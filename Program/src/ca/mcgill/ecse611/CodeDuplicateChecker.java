package ca.mcgill.ecse611;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CodeDuplicateChecker {
	private JiggleActivityReporter reporter;
	private boolean producerFinished;
	private ExecutorService executor;
	public static final File SCENARIOS_DIRECTORY = ScenarioExtractor.DESTINATION_DIRECTORY;
	public static final File CODE_DUPLICATION_XML_REPORT_FOLDER = new File(GreenfootRipper.PROJECT_DIRECTORY, "CPDReports/");
	private static final int THREAD_COUNT = 2;
	private static final int MINIMUM_TOKEN_FOR_DUPLICATION = 20;
	private ArrayBlockingQueue<File> queue;
	private File[] scenarioFolders;
	public static final String PATH_TO_PMD_BIN = "/Users/Richboy/Downloads/pmd-bin-5.5.4/bin/";
	private static final String RUN_DOT_SH = PATH_TO_PMD_BIN + "run.sh";
	
	public CodeDuplicateChecker(JiggleActivityReporter reporter){
		this.reporter = reporter;
		
		init();
		process();
		
		executor.shutdown();
		
		System.out.println("\n\nDONE!!!");
		
		if( reporter != null )
			reporter.activityFinished(getClass().getName());
	}
	
	private void init(){
		executor = Executors.newFixedThreadPool(THREAD_COUNT);
		queue = new ArrayBlockingQueue<File>(10);
		CODE_DUPLICATION_XML_REPORT_FOLDER.mkdirs();
	}
	
	private void process(){
		scenarioFolders = SCENARIOS_DIRECTORY.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() && !f.getName().startsWith(".");
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
	
	public static void main(String[] args){
		new CodeDuplicateChecker(null);
	}
	
	private class Runner implements Runnable{
		private int type;
		
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
				BufferedWriter dumpWriter = new BufferedWriter(new FileWriter(new File(GreenfootRipper.PROJECT_DIRECTORY, "commands.txt")));
				dumpWriter.write("#!/bin/bash");
				dumpWriter.newLine();
				
				while(true){
					File folder = queue.poll();
					
					if( folder == null && producerFinished )
						break;
					
					if( folder == null )
						continue;
					
					Process p = null;
					
					try {
						String command = RUN_DOT_SH + " cpd --minimum-tokens "+ MINIMUM_TOKEN_FOR_DUPLICATION +" --files " + folder.getAbsolutePath() + " --encoding ISO-8859-1 --format xml --failOnViolation false --skip-lexical-errors";
						String printCommand = command + " > " + new File(CODE_DUPLICATION_XML_REPORT_FOLDER, folder.getName() + ".xml").getAbsolutePath();
						dumpWriter.write(printCommand);
						dumpWriter.newLine();
						if( true )
							continue;
						
						p = Runtime.getRuntime().exec(command);
						int status = p.waitFor();
						
						if( status == 0 ){
							try( BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream())); 
									BufferedWriter writer = new BufferedWriter(new FileWriter(new File(CODE_DUPLICATION_XML_REPORT_FOLDER, folder.getName() + ".xml"))) ){
								String line;
								
								while( (line = reader.readLine()) != null ){
									writer.write(line);
									writer.newLine();
								}
							}
							catch(Exception e){
								e.printStackTrace();
							}
						}
						else{
							System.err.println(command + " returned Error Code: " + status);
						}
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					finally{
						if( p != null )
							p.destroy();
						p = null;
						//System.out.println(Runtime.getRuntime().freeMemory());
					}
				}
				dumpWriter.close();
				}catch(Exception e){}
			}
		}
	}
}
