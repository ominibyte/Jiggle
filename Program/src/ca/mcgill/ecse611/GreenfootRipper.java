package ca.mcgill.ecse611;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.mcgill.ecse611.Processor.RequestPack;
import ca.mcgill.ecse611.ServerPostingManager.ServerContentType;
import ca.mcgill.ecse611.Util.ErrorContext;

public class GreenfootRipper extends AbstractDownloadManager<String> implements Handler, DownloadListener{
	private String urlPrefix = "http://www.greenfoot.org/tags/with-source";
	private LinkedHashSet<String> scenarios;
	private LinkedList<DownloadContext<String>> queue, queueCopy;
	private DownloadContext<String> current;
	private ExecutorService executor;
	public static final File PROJECT_DIRECTORY = new File("/Users/Richboy/Documents/ECSE611/Project/");
	public static final File SAVE_DIRECTORY = new File(PROJECT_DIRECTORY, "ProjectFiles/");
	public static final File LOG_FILE = new File(PROJECT_DIRECTORY, "GreenfootRipperLog.txt");
	private Set<String> failedURLs;
	private int totalFoundScenarios;
	public static final int LAST_PAGE_NUMBER = 689;
	private JiggleActivityReporter reporter;

	public GreenfootRipper(JiggleActivityReporter reporter) throws IOException{
		this.reporter = reporter;
		
		ServerPostingManager.TEMP_DOWNLOAD_DIRECTORY = Files.createTempDirectory("GF").toFile();
		Processor processor = new Processor(this);
		scenarios = new LinkedHashSet<>();
		
		for(int i = 1; i < LAST_PAGE_NUMBER; i++){
			processor.addEntry(new RequestPack(i + "", urlPrefix, "sort_by=&page=" + i, ServerContentType.TEXT_HTML, true));
		}
		
		SAVE_DIRECTORY.mkdirs();
		
		LOG_FILE.delete();
		LOG_FILE.createNewFile();
		
		Util.writer = new FileWriter(LOG_FILE);
		
		Util.routeOutput("Getting Scenario URLs...\n");
		
		processor.start();
		processor.shutdown();
		
		Util.routeOutput("Total Scenarios found: " + scenarios.size() + "\n");
		totalFoundScenarios = scenarios.size();
		
		failedURLs = new HashSet<>();
		executor = Executors.newFixedThreadPool(2);
		queue = new LinkedList<>();
		queueCopy = new LinkedList<>();
		
		for(String scenario : scenarios)
			addToQueue(new DownloadContext<>(scenario, scenario + "/get_gfar", 1));
		
		
		Util.routeOutput("\nDownloading Scenarios...\n");
		
		downloadNext();
	}
	
	public static void main(String[] args) throws IOException{
		new GreenfootRipper(null);
	}
	
	public void onSuccess(String id, String content){
		Pattern pattern = Pattern.compile("/scenarios/\\d+");
		Matcher matcher = pattern.matcher(content);
		String match;
		
		while(matcher.find()){
			match = matcher.group();
			scenarios.add("http://www.greenfoot.org" + match);
		}
	}
	
	public void onError(String id, String errorMessage){
		System.err.println(errorMessage);
	}

	@Override
	public void onFinish(long downloaded, String url, File savedFilePath) {
		String filename = url.replace("/get_gfar", "");
		filename = filename.substring(filename.lastIndexOf("/") + 1) + ".gfar";
		//Util.routeOutput("Downloaded " + savedFilePath.getName() + " from: " + url + "\n");
		
		try{
			Files.move(savedFilePath.toPath(), new File(SAVE_DIRECTORY + File.separator + filename).toPath(), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
		}catch(IOException e){
			e.printStackTrace();
		}
		
		downloadNext();
	}

	@Override
	public void onStart(long total) {
	}

	@Override
	public void publish(long downloaded) {
	}

	@Override
	public void onCancel(long downloaded, String url, File savedFilePath) {
	}
	
	@Override
	public void onFailure(String url){
		failedURLs.add(url);
		Util.routeOutput("\nError Discovered for URL: " + url, true);
		
		downloadNext();
	}

	@Override
	public void retriveMessage(ErrorContext context) {
	}

	@Override
	public void interruptPosting(String text) {
		Util.routeOutput(text + "\n", true);
		downloadNext();
	}

	@Override
	public void addToQueue(
			ca.mcgill.ecse611.AbstractDownloadManager.DownloadContext<String> resourceContext) {
		queue.add(resourceContext);
		queueCopy.add(resourceContext);
	}

	@Override
	public void downloadNext() {
		if( !queue.isEmpty() ){
			current = queue.poll();
			
			ServerPostingManager spm = new ServerPostingManager(this);
			spm.setAsDownload(this);
			spm.setTargetURL(current.getDownloadURL());
			spm.setURLParameters("");
			spm.setAsGetRequest();
			
			executor.execute(spm);
		}
		else
			queueFinished();
	}

	@Override
	public void queueFinished() {
		ServerPostingManager.TEMP_DOWNLOAD_DIRECTORY.delete();
		
		if( failedURLs.size() > 0 )
			Util.routeOutput("\nListing failed URLs...\n\n");
		
		for(String failed : failedURLs){
			Util.routeOutput(failed + "\n");
		}
		
		
		Util.routeOutput("Total Scenarios Found: " + totalFoundScenarios + "\n");
		Util.routeOutput("Total Failed: " + failedURLs.size() + "\n");
		Util.routeOutput("Temp Directory Location: " + ServerPostingManager.TEMP_DOWNLOAD_DIRECTORY.toString() + "\n");
		
		Util.routeOutput("\n\nAll Done!!!\n");
		
		try{
			if( Util.writer != null )
				Util.writer.close();
		}catch(Exception e){}
		
		executor.shutdown();
		
		if( reporter != null )
			reporter.activityFinished(getClass().getName());
	}

	@Override
	public void cancellAll() {
	}

	@Override
	public void removeFromQueue(
			ca.mcgill.ecse611.AbstractDownloadManager.DownloadContext<String> resourceContext) {
	}
}