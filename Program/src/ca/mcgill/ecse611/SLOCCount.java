package ca.mcgill.ecse611;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SLOCCount {
	private JiggleActivityReporter reporter;
	private boolean producerFinished;
	private ExecutorService executor;
	private static final int THREAD_COUNT = 2;
	private ArrayBlockingQueue<File> queue;
	private File[] scenarioFolders;
	public static final File SCENARIOS_DIRECTORY = ScenarioExtractor.DESTINATION_DIRECTORY;
	public static final File SLOC_FOLDER = new File(GreenfootRipper.PROJECT_DIRECTORY, "SLOC/");
	public static final String PATH_TO_SLOC_COUNT = "/Users/Richboy/Downloads/sloccount-2.26/";
	private static final String SLOC_COUNT = PATH_TO_SLOC_COUNT + "sloccount";
	private String commandPath;
	
	public SLOCCount(JiggleActivityReporter reporter){
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
		SLOC_FOLDER.mkdirs();
	}
	
	private void process(){
		try{
			Process p = Runtime.getRuntime().exec("/bin/sh echo $PATH");
			p.waitFor();
			
			//commandPath = new BufferedReader(new InputStreamReader(p.getInputStream())).lines().reduce("", (a,b) -> a + "\n" + b);
		}
		catch(Exception e){
			e.printStackTrace();
		}
		//System.out.println(commandPath);
		
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
	
	public static void main(String[] args){
		new SLOCCount(null);
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
					BufferedWriter dumpWriter = new BufferedWriter(new FileWriter(new File(GreenfootRipper.PROJECT_DIRECTORY, "sloccount.command")));
					dumpWriter.write("#!/bin/bash");
					dumpWriter.newLine();
					
					while(true){
						File folder = queue.poll();
						
						if( folder == null && producerFinished )
							break;
						
						if( folder == null )
							continue;
						
						System.out.println(Thread.currentThread().getName() + " is working on " + folder.getName());
						Process p = null;
						
						try {
							String command = SLOC_COUNT + " --duplicates " + folder.getAbsolutePath();
							command += " > " + new File(SLOC_FOLDER, folder.getName() + ".txt").getAbsolutePath();

							dumpWriter.write(command);
							dumpWriter.newLine();
							if( true )
								continue;
							
							ProcessBuilder pb = new ProcessBuilder(SLOC_COUNT, folder.getAbsolutePath(), ">", new File(SLOC_FOLDER, folder.getName() + ".txt").getAbsolutePath());
							Map<String, String> env = pb.environment();
							//env.forEach((k, v) -> System.out.printf("%s=%s\n", k, v));
							//env.put("PATH", commandPath);
							pb.directory(new File(SLOC_COUNT).getParentFile());
							p = pb.start();
							int status = p.waitFor();
							
							if( status == 0 ){
//								try( BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream())); 
//									BufferedWriter writer = new BufferedWriter(new FileWriter(new File(SLOC_FOLDER, folder.getName() + ".txt"))) ){
//									String line;
//									
//									while( (line = reader.readLine()) != null ){
//										writer.write(line);
//										writer.newLine();
//									}
//								}
//								catch(Exception e){
//									e.printStackTrace();
//								}
							}
							else{
								System.err.println(command + " returned Error Code: " + status);
								System.err.println(new BufferedReader(new InputStreamReader(p.getInputStream())).lines().reduce("", (a,b) -> a + "\n" + b));
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
