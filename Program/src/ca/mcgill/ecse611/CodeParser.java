package ca.mcgill.ecse611;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ca.mcgill.ecse611.Util.SimpleMap;

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
 * This class parses all files in the scenarios and collect statistics about the total number of files
 * It also moves empty project files (files without fields and methods) from the projects for SLOCCount
 * to be executed more accurately.
 * @author Richboy
 *
 */
public class CodeParser {
	private JiggleActivityReporter reporter;
	private boolean producerFinished;
	private ExecutorService executor;
	private static final int THREAD_COUNT = 4;
	private ArrayBlockingQueue<File> queue;
	private File[] scenarioFolders;
	public static final File SCENARIOS_DIRECTORY = ScenarioExtractor.DESTINATION_DIRECTORY;
	public static final File RAW_SCENARIO_PROJECTS_FOLDER = GreenfootRipper.SAVE_DIRECTORY;
	public static final File PROJECT_SIZES_FILE = new File(GreenfootRipper.PROJECT_DIRECTORY, "project_sizes.csv");
	private Integer totalEmptyFiles = 0;
	private Integer totalEmptyProjects = 0;
	private Integer totalFiles = 0;
	private Integer totalFilesPassedCompilation = 0;
	private static final String TO_REPLACE = "ExtractedProjectFiles";
	private static final String REPLACER = "EmptyFiles";
	private ConcurrentMap<String, SimpleMap<Long, Long>> projectSizesMap;
	private static final String PROJECT_EXTENSION = ".gfar";
	
	public CodeParser(JiggleActivityReporter reporter){
		this.reporter = reporter;
		projectSizesMap = new ConcurrentHashMap<>();
		
		init();
		process();
		
		executor.shutdown();
		
		System.out.println("\nTotal Empty Files: " + totalEmptyFiles);
		System.out.println("Total Empty Projects: " + totalEmptyProjects);
		System.out.println("Total Files: " + totalFiles);
		System.out.println("Total Files that passed compilation: " + totalFilesPassedCompilation);
		
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
		List<SimpleMap<String, SimpleMap<Long, Long>>> fileSizesList = new ArrayList<>();
		
		projectSizesMap.forEach((project, sizes) -> fileSizesList.add(new SimpleMap<>(project, sizes)));
		
		//descending order
		Collections.sort(fileSizesList, (p1, p2) -> {
			if( p1.getSecondElement().getFirstElement() < p2.getSecondElement().getFirstElement() )
				return 1;
			else if( p1.getSecondElement().getFirstElement() > p2.getSecondElement().getFirstElement() )
				return -1;
			return 0;
		});
		
		try( BufferedWriter writer = new BufferedWriter(new FileWriter(PROJECT_SIZES_FILE)) ){
			writer.write("Project,Folder Size,Folder Size After File Move,Package Size");
			writer.newLine();
			for( SimpleMap<String, SimpleMap<Long, Long>> entry : fileSizesList ){
				writer.write(entry.getFirstElement() + "," + 
						entry.getSecondElement().getFirstElement() + "," + 
						entry.getSecondElement().getSecondElement() + "," +
						new File(RAW_SCENARIO_PROJECTS_FOLDER, entry.getFirstElement() + PROJECT_EXTENSION).length());
				writer.newLine();
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args){
		new CodeParser(null);
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
						
						javaFiles.forEach(f -> {
							//check if this file is empty
							CompilationUnit cu = getCompilationUnit(f);
							
							synchronized(totalFiles){
								totalFiles++;
							}
							
							if(cu == null)
								return;
							
							synchronized(totalFilesPassedCompilation){
								totalFilesPassedCompilation++;
							}
							
							//we need to also check if it is the case that the fields in a class are not all private when it has no method/constructor
							//we consider such a class empty
							int fieldCount = getFieldCount(cu, false);
							int privateFieldCount = getFieldCount(cu, true);
							int methodCount = getMethodCount(cu);
							int constructorCount = getConstructorCount(cu);
							
							if( (fieldCount == 0 && methodCount == 0 && constructorCount == 0) || 
									(methodCount == 0 && constructorCount == 0 && fieldCount == privateFieldCount) ){
								emptyFilesCount++;
								emptyJavaFiles.add(f);
							}
						});
						
						
						//move all empty java files out of the projects area
						emptyJavaFiles.forEach(file -> {
							try {
								File dest = new File(file.getAbsolutePath().replace(TO_REPLACE, REPLACER));
								dest.getParentFile().mkdirs();
								Files.move(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
							} catch (Exception e) {
								e.printStackTrace();
							}
						});
						
						long sizeAfter = Files.walk(folder.toPath()).mapToLong( p -> p.toFile().length() ).sum();
						
						projectSizesMap.put(folder.getName(), new SimpleMap<>(size, sizeAfter));
						
						if( emptyFilesCount == javaFiles.size() ){//this is an empty project
							synchronized(totalEmptyFiles){
								totalEmptyFiles += emptyFilesCount;
							}
							
							synchronized(totalEmptyProjects){
								totalEmptyProjects++;
							}
							
							continue;
						}
						
						synchronized(totalEmptyFiles){
							totalEmptyFiles += emptyFilesCount;
						}
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
//	        	if( type instanceof ClassOrInterfaceDeclaration ){//ADDED:
//	        		theType = (ClassOrInterfaceDeclaration) type;
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
//		                	else{//ADDED: if its just the braces then check if this class extends another class
//		                		if( theType.getExtendedTypes().size() > 0 )
//		                			count++;
//		                	}
//		                	
//		                	System.out.println("In here");
		                }
		            }
//		        }
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
