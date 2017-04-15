package ca.mcgill.ecse611;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;

/**
 * This class extracts a few more "missed" metrics from the dataset
 * @author Richboy
 *
 */
public class AdditionalMetricExtractor {
	private JiggleActivityReporter reporter;
	private Map<String, Integer> projectFilesMap = new HashMap<>();
	private PreparedStatement ps;
	
	public static final File SCENARIOS_DIRECTORY = ScenarioExtractor.DESTINATION_DIRECTORY;
	public static final File FAILED_COMPILATIONS_FILE = new File(GreenfootRipper.PROJECT_DIRECTORY, "failed_file_compilations.csv");
	private boolean producerFinished;
	private ExecutorService executor;
	private static final int THREAD_COUNT = 4;
	private ArrayBlockingQueue<File> queue;
	private File[] scenarioFolders;
	private ArrayList<File> failedCompilations;
	
	public AdditionalMetricExtractor(JiggleActivityReporter reporter){
		this.reporter = reporter;
		failedCompilations = new ArrayList<>();
		
		init();
		process();
		
		executor.shutdown();
		
		writeToFile();
		
		System.out.println("\n\nDONE!!!");
		
		if( reporter != null )
			reporter.activityFinished(getClass().getName());
	}
	
	private void init(){
		executor = Executors.newFixedThreadPool(THREAD_COUNT);
		queue = new ArrayBlockingQueue<File>(10);
	}
	
	private void process(){
		doGenerateNonEmptyClassFilesPerScenario();
		doGenerateClassFilesPerScenario();
		projectFilesMap.clear();
		
		scenarioFolders = SCENARIOS_DIRECTORY.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return dir.isDirectory() && !name.startsWith(".");
			}
		});
		
		Arrays.sort(scenarioFolders, (a, b) -> {
			int n1 = Integer.parseInt(a.getName());
			int n2 = Integer.parseInt(b.getName());
			
			if( n1 > n2 )
				return 1;
			else if( n2 > n1 )
				return -1;
			return 0;
		});

		Future<String>[] futures = new Future[THREAD_COUNT];
		
		futures[0] = executor.submit(new Runner(0), "");//producer
		for( int i = 1; i < futures.length; i++ ){
			futures[i] = executor.submit(new Runner(i), "");
		}
		
		for( int i = 1; i < futures.length; i++ ){
			try{
				futures[i].get();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	private void writeToFile(){
		try( BufferedWriter writer = new BufferedWriter(new FileWriter(FAILED_COMPILATIONS_FILE)) ){
			for( File file : failedCompilations ){
				writer.write(file.getAbsolutePath());
				writer.newLine();
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private void doGenerateNonEmptyClassFilesPerScenario(){
		//number of non-empty Java class files per scenario
		File[] scenarioFolders = CodeParser.SCENARIOS_DIRECTORY.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return dir.isDirectory() && !name.startsWith(".");
			}
		});
		
		for(File folder : scenarioFolders){
			projectFilesMap.put(folder.getName(), CodeParser.filterForJavaFiles(folder).size());
		}
		
		Query q = new Query("INSERT IGNORE INTO project_no_empty_files_count VALUES (?, ?)", Util.mcon);
		ps = q.getPS();
		
		projectFilesMap.forEach(this::insert);
		
		q.disconnect();
	}
	
	private void insert(String key, Integer value){
		try{
			ps.setInt(1, Integer.parseInt(key));
			ps.setInt(2, value);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	//This class uses part of the information obtained from the non-empty version method
	private void doGenerateClassFilesPerScenario(){
		//number of Java class files per scenario
		File[] scenarioFolders = new File(GreenfootRipper.PROJECT_DIRECTORY, "EmptyFiles/").listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return dir.isDirectory() && !name.startsWith(".");
			}
		});
		
		for(File folder : scenarioFolders){
			projectFilesMap.merge(folder.getName(), CodeParser.filterForJavaFiles(folder).size(), Integer::sum);
		}
		
		Query q = new Query("INSERT IGNORE INTO project_total_files_count VALUES (?, ?)", Util.mcon);
		ps = q.getPS();
		
		projectFilesMap.forEach(this::insert);
		
		q.disconnect();
	}
	
	
	public static void main(String[] args){
		new AdditionalMetricExtractor(null);
	}
	
	public static List<File> filterForJavaFiles(File directory){
		List<File> files = new ArrayList<>();
		
		File[] selectedFiles = directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if( file.isDirectory() || file.getName().trim().toLowerCase().endsWith(".java") )
					return true;
				return false;
			}
		});
		
		for(File file : selectedFiles){
			if( file.isDirectory() )
				files.addAll(filterForJavaFiles(file));
			else
				files.add(file);
		}
		
		return files;
	}
	
	private class Runner implements Runnable{
		private int type;
		private int emptyFilesCount;
		private List<File> emptyJavaFiles;//files that have got no field or method
		
		public Runner(int type){
			this.type = type;
			emptyJavaFiles = new ArrayList<>();
		}
		
		public void run(){
			if( type == 0 ){//producer
				for(File folder : scenarioFolders){
					try{
						queue.put(folder);
					}catch(Exception e){}
				}
				
				producerFinished = true;
			}
			else{//consumer
				try{
					while(true){
						emptyFilesCount = 0;
						emptyJavaFiles.clear();
						File folder = queue.poll();
						
						if( folder == null && producerFinished )
							break;
						
						if( folder == null )
							continue;
						
						//get a list of all the java files
						List<File> javaFiles = filterForJavaFiles(folder);
						
						if( javaFiles.isEmpty() )
							continue;
						
						System.out.println(Thread.currentThread().getName() + " is working on " + folder.getName());
						
						long size = Files.walk(folder.toPath()).mapToLong( p -> p.toFile().length() ).sum();
						
						Query q = new Query("INSERT INTO project_per_file_metrics VALUES (NULL, ?, ?, ?, ?, ?, ?)", Util.mcon);
						PreparedStatement ps = q.getPS();
						
						javaFiles.forEach(f -> {
							//check if this file is empty
							CompilationUnit cu = getCompilationUnit(f);
							
							if(cu == null){
								failedCompilations.add(f);
								return;
							}
							
							
							//we need to also check if it is the case that the fields in a class are not all private when it has no method/constructor
							//we consider such a class empty
							int fieldCount = getFieldCount(cu, false);
							int privateFieldCount = getFieldCount(cu, true);
							int methodCount = getMethodCount(cu);
							int constructorCount = getConstructorCount(cu);
							
							try{
								ps.setInt(1, Integer.parseInt(folder.getName()));
								ps.setString(2, f.getAbsolutePath());
								ps.setInt(3, methodCount);
								ps.setInt(4, fieldCount);
								ps.setInt(5, privateFieldCount);
								ps.setInt(6, constructorCount);
								ps.executeUpdate();
							}
							catch(Exception e){
								e.printStackTrace();
							}
						});
						
						q.disconnect();
					}
				}catch(Exception e){}
			}
		}
		
		private CompilationUnit getCompilationUnit(File f){
			CompilationUnit cu = null;
			
			try(FileInputStream fis = new FileInputStream(f)){
				cu = JavaParser.parse(fis, Charset.forName("ISO-8859-1"));
			}
			catch(Exception e){
				//e.printStackTrace();
			}
			
			return cu;
		}
		
		private int getMethodCount(CompilationUnit cu){
			int count = 0;
			
			NodeList<TypeDeclaration<?>> types = cu.getTypes();
	        for (TypeDeclaration<?> type : types) {
	            // Go through all fields, methods, etc. in this type
	            NodeList<BodyDeclaration<?>> members = type.getMembers();
	            for (BodyDeclaration<?> member : members) {
	                if (member instanceof MethodDeclaration) {
	                	//ignore empty methods
	                	MethodDeclaration md = (MethodDeclaration) member;
	                	md.getAllContainedComments().forEach(Comment::remove);
	                	md.getOrphanComments().forEach(md::removeOrphanComment);
	                	
	                	String body = md.getBody().toString().replaceAll("\\s", "").trim();
	                	
	                	if( body.length() > 2 )	//if its not just the braces in the method
	                		count++;
	                }
	            }
	        }
			
			return count;
		}
		
		private int getConstructorCount(CompilationUnit cu){
			int count = 0;
			
			NodeList<TypeDeclaration<?>> types = cu.getTypes();
			ClassOrInterfaceDeclaration theType = null;
	        for (TypeDeclaration<?> type : types) {
		            // Go through all fields, methods, etc. in this type
		            NodeList<BodyDeclaration<?>> members = type.getMembers();
		            for (BodyDeclaration<?> member : members) {
		                if (member instanceof ConstructorDeclaration) {
		                	//ignore empty constructor
		                	ConstructorDeclaration cd = (ConstructorDeclaration) member;
		                	cd.getAllContainedComments().forEach(Comment::remove);
		                	cd.getOrphanComments().forEach(cd::removeOrphanComment);
		                	
		                	String body = cd.getBody().toString().replaceAll("\\s", "").trim();
		                	
		                	if( body.length() > 2 )	//if its not just the braces in the constructor
		                		count++;
		                }
		            }
	        }
			
			return count;
		}
		
		private int getFieldCount(CompilationUnit cu, boolean forPrivateFields){
			int count = 0;
			
			NodeList<TypeDeclaration<?>> types = cu.getTypes();
	        for (TypeDeclaration<?> type : types) {
	            // Go through all fields, methods, etc. in this type
	            NodeList<BodyDeclaration<?>> members = type.getMembers();
	            for (BodyDeclaration<?> member : members) {
	                if (member instanceof FieldDeclaration) {
	                	if( !forPrivateFields )
	                		count++;
	                	else{
	                		FieldDeclaration fd = (FieldDeclaration) member;
	                		if( fd.isPrivate() )
	                			count++;
	                	}
	                }
	            }
	        }
			
			return count;
		}
	}
}
