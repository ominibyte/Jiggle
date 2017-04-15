package ca.mcgill.ecse611;

public abstract class AbstractDownloadManager<E> implements DownloadListener, ServerPostingInterface{
	
	public abstract void addToQueue(DownloadContext<E> resourceContext);
	public abstract void downloadNext();
	public abstract void queueFinished();
	public abstract void cancellAll();
	public abstract void removeFromQueue(DownloadContext<E> resourceContext);//handles both currently downloading and queue removal
	
	public static class DownloadContext<E> implements Comparable<DownloadContext<Integer>>{
		private E objectInfo;
		private String url;
		private int priority;
		
		public DownloadContext(E objectInfo, String url, int priority){
			this.objectInfo = objectInfo;
			this.url = url;
			this.priority = priority;
		}
		public String getDownloadURL(){
			return url;
		}
		public E getObjectContext(){
			return objectInfo;
		}
		public int getPriority(){
			return priority;
		}
		@Override
		public int compareTo(DownloadContext<Integer> another) {
			if( another.getPriority() > getPriority() )
				return 1;
			else if( getPriority() > another.getPriority() )
				return -11;
				
			return 0;
		}
	}
}
