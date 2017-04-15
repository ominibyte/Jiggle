package ca.mcgill.ecse611;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * This file generates the file the will be used by the SLOC count tool to count the lines of code
 * for each file in each scenario
 * @author Richboy
 *
 */

public class SLOCCountPerFile {
	private JiggleActivityReporter reporter;
	private boolean producerFinished;
	private ExecutorService executor;
	private static final int THREAD_COUNT = 2;
	private ArrayBlockingQueue<String> queue;
	private File[] scenarioFolders;
	public static final File SCENARIOS_DIRECTORY = ScenarioExtractor.DESTINATION_DIRECTORY;
	public static final File SLOC_FOLDER = new File(GreenfootRipper.PROJECT_DIRECTORY, "SLOCFile/");
	public static final String PATH_TO_SLOC_COUNT = "/Users/Richboy/Downloads/sloccount-2.26/";
	private static final String SLOC_COUNT = PATH_TO_SLOC_COUNT + "sloccount";
	
	public SLOCCountPerFile(JiggleActivityReporter reporter){
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
		queue = new ArrayBlockingQueue<>(10);
		SLOC_FOLDER.mkdirs();
	}
	
	private void process(){
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
		new SLOCCountPerFile(null);
	}
	
	private class Runner implements Runnable{
		private int type;
		
		public Runner(int type){
			this.type = type;
		}
		
		public void run(){
			if( type == 0 ){//producer
				Query q = new Query("SELECT file FROM project_per_file_metrics", Util.mcon);
				if( q.isFullyConnected() ){
					try{
						ResultSet rs = q.getPS().executeQuery();
						while(rs.next()){
							try{
								queue.put(rs.getString("file"));
							}catch(Exception e){}
						}
						rs.close();
					}
					catch(Exception e){
						e.printStackTrace();
					}
				}
				else
					q.getException().printStackTrace();
				
				q.disconnect();
				
				producerFinished = true;
			}
			else{//consumer
				try{
					BufferedWriter dumpWriter = new BufferedWriter(new FileWriter(new File(GreenfootRipper.PROJECT_DIRECTORY, "sloccount_file.command")));
					dumpWriter.write("#!/bin/bash");
					dumpWriter.newLine();
					String newFile, tempFile;
					int index, lastIndex;
					
					while(true){
						String file = queue.poll();
						
						if( file == null && producerFinished )
							break;
						
						if( file == null )
							continue;
						
						System.out.println(Thread.currentThread().getName() + " is working on " + file);
						
						try {
							if (file.contains("(") || file.contains(")") ){
								tempFile = file.replace("(", "_").replace(")", "_").replace("ExtractedProjectFiles", "TempProjectFiles");
								new File(tempFile).getParentFile().mkdirs();
								
								Files.copy(new File(file).toPath(), new File(tempFile).toPath(), StandardCopyOption.REPLACE_EXISTING);
								file = tempFile;
							}
							
							newFile = file.replace("ExtractedProjectFiles", "SLOCFile").replace(".java", ".txt");
							index = newFile.indexOf("SLOCFile") + "SLOCFile".length();
							index = newFile.indexOf("/", index + 2);
							lastIndex = newFile.lastIndexOf("/");
							newFile = newFile.substring(0, index) + newFile.substring(lastIndex);
							
							new File(newFile).getParentFile().mkdirs();
							 
							String command = SLOC_COUNT + " --duplicates '" + file + "'";
							command += " > '" + newFile + "'";

							dumpWriter.write(command);
							dumpWriter.newLine();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					
					dumpWriter.close();
				}catch(Exception e){}
			}
		}
	}
}
