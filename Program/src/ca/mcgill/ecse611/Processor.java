package ca.mcgill.ecse611;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import ca.mcgill.ecse611.ServerPostingManager.ServerContentType;
import ca.mcgill.ecse611.Util.ErrorContext;

public class Processor {
	private Handler handler;
	private LinkedList<RequestPack> queue;
	private boolean hasStarted;
	private Condition waitCondition;
	private ReentrantLock lock;
	private ExecutorService executor;
	
	public Processor(Handler handler){
		this.handler = handler;
		executor = Executors.newFixedThreadPool(2);
		
		queue = new LinkedList<>();
		lock = new ReentrantLock();
		waitCondition = lock.newCondition();
	}
	
	public void addEntry(RequestPack pack){
		synchronized(queue){
			queue.add(pack);
		}
	}
	
	private RequestPack getRequestPack(){
		synchronized(queue){
			if( queue.isEmpty() )
				return null;
			
			return queue.poll();
		}
	}
	
	public void start(){
		if( hasStarted )
			return;
		
		hasStarted = true;
		
		Runner runner;
		
		while( true ){
			RequestPack pack = getRequestPack();
			if( pack == null )
				return;
			
			runner = new Runner(pack);
			
			lock.lock();
			
			executor.execute(runner);
			
			try{
				waitCondition.await();
			}catch(InterruptedException e){}
			finally{
				lock.unlock();
			}
			
			if( runner.wasSuccessful )
				handler.onSuccess(pack.id, runner.content);
			else
				handler.onError(pack.id, runner.content);
		}
	}
	
	public void shutdown(){
		executor.shutdown();
	}
	
	private class Runner implements Runnable{
		private RequestPack pack;
		private boolean wasSuccessful;
		private String content;
		
		public Runner(RequestPack pack){
			this.pack = pack;
		}
		
		public void run(){
			lock.lock();
			
			ServerPostingManager spm = new ServerPostingManager(new ServerPostingInterface() {
				
				@Override
				public void retriveMessage(ErrorContext context) {
					try{
						wasSuccessful = true;
						content = context.getMessage();
						
						waitCondition.signal();
						lock.unlock();
					}
					catch(Exception e){
						wasSuccessful = false;
						content = e.getMessage();
						//e.printStackTrace();
						
						waitCondition.signal();
						lock.unlock();
					}
				}
				
				@Override
				public void interruptPosting(String text) {
					wasSuccessful = false;
					content = text;
					//System.err.println(text);
					
					waitCondition.signal();
					lock.unlock();
				}
			});
			
			if( pack.isGetRequest )
				spm.setAsGetRequest();
			spm.setTargetURL(pack.targetURL);
			spm.setURLParameters(pack.params);
			spm.setTimeout(60, true);
			spm.setRetriesCount(3);
			spm.setContentType(pack.contentType);
			spm.run();
		}
	}
	
	public static class RequestPack{
		private String id;
		private String targetURL;
		private String params;
		private ServerContentType contentType;
		private boolean isGetRequest;
		
		public RequestPack(String id, String targetURL, String params, ServerContentType contentType, boolean isGetRequest){
			this.id = id;
			this.targetURL = targetURL;
			this.params = params;
			this.contentType = contentType;
			this.isGetRequest = isGetRequest;
		}
	}
}
