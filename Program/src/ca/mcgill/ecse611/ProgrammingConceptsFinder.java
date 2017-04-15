package ca.mcgill.ecse611;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * This class finds several programming and OOP concepts used in the scenarios
 * NOTE: This class would be run after CodeParser and thus there is no need to check for empty class files
 * @author Richboy
 *
 */
public class ProgrammingConceptsFinder {
	private JiggleActivityReporter reporter;
	private boolean producerFinished;
	private ExecutorService executor;
	private static final int THREAD_COUNT = 4;
	private ArrayBlockingQueue<File> queue;
	private File[] scenarioFolders;
	public static final File SCENARIOS_DIRECTORY = ScenarioExtractor.DESTINATION_DIRECTORY;
	private ConcurrentMap<String, Statistics> projectStatisticsMap;
	public static final File PROJECT_STATS_FILE = new File(GreenfootRipper.PROJECT_DIRECTORY, "project_stats.csv");
	
	public ProgrammingConceptsFinder(JiggleActivityReporter reporter){
		this.reporter = reporter;
		projectStatisticsMap = new ConcurrentHashMap<>();
		
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
		try( BufferedWriter writer = new BufferedWriter(new FileWriter(PROJECT_STATS_FILE)) ){
			writer.write("Project,Methods,Fields,Abstract Classes,Abstract Methods,Generic Classes,Generic Methods,Generic Interfaces,Classes,Interfaces,Uses Class Inheritance,Uses Interface Inheritance,Total Polymorphism Usages");
			writer.newLine();
			projectStatisticsMap.forEach((project, stats) -> {
				try{
					writer.write(project + "," + 
							stats.totalMethodCount + "," + 
							stats.totalFieldCount + "," + 
							stats.totalAbstractClasses + "," + 
							stats.totalAbstractMethods + "," + 
							stats.totalGenericClasses + "," + 
							stats.totalGenericMethods + "," + 
							stats.totalGenericInterfaces + "," + 
							stats.totalClasses + "," + 
							stats.totalInterfaces + "," + 
							stats.totalClassInheritance + "," + 
							stats.totalInterfaceInheritance + "," + 
							stats.totalPolymorphismUsages
					);
					writer.newLine();
				}
				catch(Exception e){
				}
			});
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args){
		new ProgrammingConceptsFinder(null);
	}
	
	private List<File> filterForJavaFiles(File directory){
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
	
	private List<File> findFile(File directory, String f){
		List<File> files = new ArrayList<>();
		
		File[] selectedFiles = directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if( file.isDirectory() || file.getName().trim().equalsIgnoreCase(f) )
					return true;
				return false;
			}
		});
		
		for(File file : selectedFiles){
			if( file.isDirectory() )
				files.addAll(findFile(file, f));
			else
				files.add(file);
		}
		
		return files;
	}
	
	private class Runner implements Runnable{
		private int type;
		private Statistics stats;
		private File rootDirectory;//root directory where the project.greenfoot file is located
		private File currentFile;
		
		public Runner(int type){
			this.type = type;
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
						stats = new Statistics();
						
						File folder = queue.poll();
						
						if( folder == null && producerFinished )
							break;
						
						if( folder == null )
							continue;
						
						//get a list of all the java files
						List<File> javaFiles = filterForJavaFiles(folder);
						
						if( javaFiles.isEmpty() )
							continue;
						
						rootDirectory = findFile(folder, "project.greenfoot").get(0).getParentFile();
						
						//System.out.println(Thread.currentThread().getName() + " is working on " + folder.getName());
						
						javaFiles.forEach(f -> {
							currentFile = f;
							//check if this file is empty
							CompilationUnit cu = getCompilationUnit(f);
							
							if(cu == null)
								return;
							
							stats.totalMethodCount += getMethodCount(cu);
							stats.totalFieldCount += getFieldCount(cu);
							new TheVisitor().visit(cu, null);
						});
						
						projectStatisticsMap.put(folder.getName(), stats);
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
		
		private class TheVisitor extends VoidVisitorAdapter<Object>{
			@Override
			public void visit(MethodCallExpr n, Object arg) {//TODO not working...not being used
				//lets check if we have a recursive method
				
				try{
					MethodDeclaration decl = null;
					
					Node node = n.getParentNode().get();
					if( node instanceof MethodDeclaration ){
						decl = (MethodDeclaration) node;
					}
					
					//iteratively find the method where this method call expression is defined
					while( !node.getMetaModel().isRootNode() ){
						node = node.getParentNode().get();
						
						if( node instanceof MethodDeclaration ){
							decl = (MethodDeclaration) node;
							break;
						}
					}
					
					if( decl != null ){//if we found the parent method
						if( !n.getScope().isPresent()	//if the method call is to a method within the same class
								&& n.getArguments().size() > 0 	//if the method call has an argument
								&& decl.getParameters().size() == n.getArguments().size() //if the argument sizes are same
								&& decl.getName().toString().equals(n.getName().toString()) ){	//if the method names are same
							//TODO the call could be scoped to some other class
							
							//System.out.println(n.getArguments().size() + " --- " + n.getArguments().stream().map(t -> t.toString()).reduce("", (a,b) -> a + ","+ b));
							
							//System.out.println(currentFile.getAbsolutePath() + " ----- " + n);
						}
					}
				}
				catch(Exception | Error e){}
				
				super.visit(n, arg);
			}
			
			@Override
			public void visit(VariableDeclarationExpr n, Object arg) {
				//to detect polymorphism
				if( n.toString().contains("new ") ){
					String leftType = "";
					Node newNode = null;
					
					//when we do this iteration, the last item always gives the declaration Data Type on the left
					for(Node node : n.getChildNodes()){
						for(Node n1 : node.getChildNodes()){
							leftType = n1.toString().trim();
							if( n1.toString().trim().startsWith("new ") )
								newNode = n1;
						}
					}
					
					if( newNode != null ){
						String rightType = newNode.toString().substring(newNode.toString().indexOf("new ") + "new ".length()).split("(\\[|\\(|\\s|\\<)")[0].trim();
						
						//remove the generic and array expressions from the left type
						if( leftType.contains("<") )
							leftType = leftType.substring(0, leftType.indexOf("<")).trim();
						if( leftType.contains("[") )
							leftType = leftType.substring(0, leftType.indexOf("[")).trim();
						
						if( !leftType.equals(rightType) ){//then there is polymorphism being used
							stats.totalPolymorphismUsages++;
							//System.out.println(leftType + " - " + rightType + " - " + currentFile.toString());
						}
					}
				}
				
				super.visit(n, arg);
			}
			
			
			@Override
			public void visit(MethodDeclaration n, Object arg) {
				if( n.isAbstract() )
					stats.totalAbstractMethods++;
				if( n.isGeneric() )
					stats.totalGenericMethods++;
				
				super.visit(n, arg);
			}
			
			@Override
			public void visit(ClassOrInterfaceDeclaration n, Object arg) {
				if( n.isInterface() )
					stats.totalInterfaces++;
				else
					stats.totalClasses++;
				
				if( !n.isInterface() && n.isAbstract() )
					stats.totalAbstractClasses++;
				if( n.isGeneric() ){
					if( n.isInterface() )
						stats.totalGenericInterfaces++;
					else
						stats.totalGenericClasses++;
				}
				
				if( n.isInterface() ){
					if( n.getExtendedTypes().size() > 0 || n.getImplementedTypes().size() > 0 )
						stats.totalInterfaceInheritance += n.getExtendedTypes().size() + n.getImplementedTypes().size();
				}
				else{
					if( n.getExtendedTypes().size() > 0 ){
						//check that this extends is not making use of Actor and World
						int count = 0;
						
						for(ClassOrInterfaceType type : n.getExtendedTypes()){
							if( !type.getName().toString().equals("Actor") && !type.getName().toString().equals("World")
									&& !type.getName().toString().equals("greenfoot.Actor") && !type.getName().toString().equals("greenfoot.World") ){
								count++;
							}
						}
						
						if( count > 0 )
							stats.totalClassInheritance += count;
					}
					if( n.getImplementedTypes().size() > 0 )
						stats.totalInterfaceInheritance += n.getImplementedTypes().size();
				}
				
				super.visit(n, arg);
			}
		}
		
		private int getMethodCount(CompilationUnit cu){
			int count = 0;
			
			NodeList<TypeDeclaration<?>> types = cu.getTypes();
	        for (TypeDeclaration<?> type : types) {
	            // Go through all fields, methods, etc. in this type
	            NodeList<BodyDeclaration<?>> members = type.getMembers();
	            for (BodyDeclaration<?> member : members) {
	                if (member instanceof MethodDeclaration) {
	                    count++;
	                }
	            }
	        }
			
			return count;
		}
		
		private int getFieldCount(CompilationUnit cu){
			int count = 0;
			
			NodeList<TypeDeclaration<?>> types = cu.getTypes();
	        for (TypeDeclaration<?> type : types) {
	            // Go through all fields, methods, etc. in this type
	            NodeList<BodyDeclaration<?>> members = type.getMembers();
	            for (BodyDeclaration<?> member : members) {
	                if (member instanceof FieldDeclaration) {
	                    count++;
	                }
	            }
	        }
			
			return count;
		}
	}
	
	private class Statistics{
		private int totalMethodCount;				//done
		private int totalFieldCount;				//done
		private int totalRecursiveMethods;
		private int totalAbstractClasses;			//done
		private int totalAbstractMethods;			//done
		private int totalGenericClasses;			//done
		private int totalGenericInterfaces;			//done
		private int totalGenericMethods;			//done
		private int totalInterfaces;				//done
		private int totalClasses;					//done
		private int totalPolymorphismUsages;		//done
		private int totalClassInheritance;		//DONE: if a class extends another class
		private int totalInterfaceInheritance;	//DONE: if a class or interface implements/extends another interface
	}
}
