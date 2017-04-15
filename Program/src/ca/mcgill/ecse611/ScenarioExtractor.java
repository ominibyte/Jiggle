package ca.mcgill.ecse611;

import java.io.File;
import java.io.FilenameFilter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//Extract All Scenarios to their respective folders
public class ScenarioExtractor {
	private JiggleActivityReporter reporter;
	private static final File SOURCE_DIRECTORY = GreenfootRipper.SAVE_DIRECTORY;//new File("/Users/Richboy/Documents/ECSE611/ProjectFiles Downloaded/");
	public static final File DESTINATION_DIRECTORY = new File(GreenfootRipper.PROJECT_DIRECTORY, "ExtractedProjectFiles/");
	private ExecutorService executor;
	private ArrayBlockingQueue<File> queue;
	private File[] scenarios;
	private boolean producerFinished;
	
	public ScenarioExtractor(JiggleActivityReporter reporter){
		this.reporter = reporter;
		
		init();
		process();
		
		scenarios = null;
		executor.shutdown();
		
		System.out.println("\n\nDONE!!!");
		
		if( reporter != null )
			reporter.activityFinished(getClass().getName());
	}
	
	private void init(){
		executor = Executors.newFixedThreadPool(4);
		queue = new ArrayBlockingQueue<File>(10);
		DESTINATION_DIRECTORY.mkdirs();
	}
	
	private void process(){
		scenarios = SOURCE_DIRECTORY.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return !name.toLowerCase().endsWith(".gfar");
			}
		});
		

		Future<String>[] futures = new Future[4];
		
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
		new ScenarioExtractor(null);
	}
	
	private class Runner implements Runnable{
		private int type;
		
		public Runner(int type){
			this.type = type;
		}
		
		public void run(){
			if( type == 0 ){//producer
				for(File scenario : scenarios){
					try{
						queue.put(scenario);
					}catch(Exception e){}
				}
				
				producerFinished = true;
			}
			else{//consumer
				while(true){
					File scenario = queue.poll();
					
					if( scenario == null && producerFinished )
						break;
					
					if( scenario == null )
						continue;
					
					File destination = new File(DESTINATION_DIRECTORY, scenario.getName().substring(0, scenario.getName().indexOf(".")));
					
					Util.extractZipTo(scenario, destination, false);
					
					//delete all files that are not needed from the extracted data
					removeGabbage(destination);
				}
			}
		}
		
		private void removeGabbage(File destination){
			//may not do anything here cause there may be some other analysis to be done on the file contents
		}
	}
}
