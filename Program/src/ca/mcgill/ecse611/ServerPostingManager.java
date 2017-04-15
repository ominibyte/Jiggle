package ca.mcgill.ecse611;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.Timer;

public class ServerPostingManager implements Runnable{
	private int tries;
	private int timeout;
	private String urlParameters;
	private String targetURL;
	private boolean shouldStop, shouldCancel;
	private ServerPostingInterface spi;
	private Thread timeoutThread;
	private boolean isGet;
	private int SLEEP_INTERVAL = 1000;
	private ServerContentType contentType;
	private boolean isDownload;
	private DownloadListener downloadListener;
	public static File TEMP_DOWNLOAD_DIRECTORY;
	private Timeout timer;
	private boolean shouldGenerateRandomDownloadFileName;
	
	public ServerPostingManager(ServerPostingInterface spi){
		tries = 1;
		timeout = 20 * 1000;//20 seconds
		urlParameters = null;
		targetURL = null;
		timeoutThread = null;
		isGet = false;
		isDownload = false;
		this.spi = spi;
	}
	
	public void setRetriesCount(int count){
		tries = count;
	}
	
	public void stopTimer(){
		if( timeoutThread != null )
			timeoutThread.interrupt();
	}
	
	public void setTimeout(int time, boolean inSeconds){
		if( inSeconds )
			timeout = time * 1000;
		else
			timeout = time;
	}
	
	public void setAsDownload(DownloadListener listener){
		downloadListener = listener;
		isDownload = true;
	}
	
	public void cancelDownload(){
		shouldCancel = true;
	}
	
	public void setURLParameters(String parameter){
		urlParameters = parameter;
	}
	
	public void setTargetURL(String url){
		targetURL = url;
	}
	
	public void setAsGetRequest(){
		isGet = true;
	}
	
	public void setContentType(ServerContentType contentType){
		this.contentType = contentType;
	}
	
	public void setShouldGenerateRandomDownloadFileName(){
		shouldGenerateRandomDownloadFileName = true;
	}
	
	public void run(){
		if( timeout != 0 ){
			timer = new Timeout(timeout);
			timeoutThread = new Thread( timer );
			timeoutThread.start();
		}
		
		try{
			if( spi == null )
				executePost(targetURL, urlParameters);
			else
				spi.retriveMessage(executePost(targetURL, urlParameters));
		}
		finally{
			if( timer != null )
				timer.stop();
		}
	}
	
	public boolean isURLLinkAlive(String targetURL){
		URL url;
		HttpURLConnection connection = null;
		int attempt = 0;
		boolean isHTTPS = targetURL.trim().toLowerCase().startsWith("https");
		
		do{
			try{
				if( attempt > 0 )
					Thread.sleep(150);
				if( isHTTPS )
					url = new URL(null, targetURL, new sun.net.www.protocol.https.Handler());
				else
					url = new URL(targetURL);
				connection = targetURL.trim().toLowerCase().startsWith("https") ?  (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
			
				connection.setRequestMethod("GET");
				connection.setRequestProperty("Content-Type", contentType == null ? ServerContentType.DEFAULT.contentTypeString : contentType.contentTypeString);
				connection.setRequestProperty ( "User-agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36");
				connection.setRequestProperty("Content-Language", "en-US");
				connection.setUseCaches(false);
				connection.setConnectTimeout(10000);//10 seconds
				
				return connection.getResponseCode() == 200;
			}
			catch(Exception e){
				attempt++;
			}
			finally{
				if (connection != null) {
					connection.disconnect();
				}
			}
		}while( attempt < 3 );
		
		return false;
	}
	
	private String[] partitionHTTPSURL(String url){
		String site = url.substring("https://".length());
		
		String[] parts = new String[3];
		parts[0] = "https";
		if( site.indexOf("/") != -1 ){
			parts[1] = site.toLowerCase().startsWith("www") ? site.substring("www".length(), site.indexOf("/")) : site.substring(0, site.indexOf("/"));
			parts[2] = site.substring(site.indexOf("/"));
		}
		else if( site.indexOf("?") != -1 ){
			parts[1] = site.toLowerCase().startsWith("www") ? site.substring("www".length(), site.indexOf("?")) : site.substring(0, site.indexOf("?"));
			parts[2] = site.substring(site.indexOf("?"));
		}
		else{
			parts[1] = site.toLowerCase().startsWith("www") ? site.substring("www".length()) : site.substring(0);
			parts[2] = "";
		}
		
		System.out.printf("%s - %s - %s\n", parts[0], parts[1], parts[2]);
		
		return parts;
	}
	
	private Util.ErrorContext executePost(String targetURL, String urlParameters) {
		if( targetURL == null || urlParameters == null )
			return new Util.ErrorContext(false, "Insufficient Request Data.\n" + ( targetURL == null ? "Target URL is empty." : "POST parameter is empty."));
		
		int tried = 0;
		File tempFileLocation = null;
		
		do{
			URL url;
			HttpURLConnection connection = null;
			boolean isHTTPS = targetURL.trim().toLowerCase().startsWith("https");
			
			try {
				if( shouldStop )
					return new Util.ErrorContext(false, "The connection timed out. Please check your internet connection.");
				
				if( isHTTPS && isDownload ){
//					System.setProperty("java.protocol.handler.pkgs", new sun.net.www.protocol.https.Handler());
//					Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
					
//					// Create a new trust manager that trust all certificates
//					TrustManager[] trustAllCerts = new TrustManager[]{
//					    new X509TrustManager() {
//					        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
//					            return null;
//					        }
//					        public void checkClientTrusted(
//					            java.security.cert.X509Certificate[] certs, String authType) {
//					        }
//					        public void checkServerTrusted(
//					            java.security.cert.X509Certificate[] certs, String authType) {
//					        }
//					    }
//					};
//
//					// Activate the new trust manager
//					try {
//					    SSLContext sc = SSLContext.getInstance("SSL");
//					    sc.init(null, trustAllCerts, new java.security.SecureRandom());
//					    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
//					} catch (Exception e) {
//					}
				}
				
				try{
					// Create connection
					if( isHTTPS /*&& isDownload*/ ){
						//String[] urlParts = partitionHTTPSURL(targetURL + ( isGet && !urlParameters.trim().isEmpty() ? "?" + urlParameters : "" ));
						//url = new URL(urlParts[0], urlParts[1], 443, urlParts[2], new sun.net.www.protocol.https.Handler());
						url = new URL(null, targetURL + ( isGet && !urlParameters.trim().isEmpty() ? "?" + urlParameters : "" ), new sun.net.www.protocol.https.Handler());
					}
					else
						url = new URL(targetURL + ( isGet && !urlParameters.trim().isEmpty() ? "?" + urlParameters : "" ));
					connection = isHTTPS ?  (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
				}
				catch( Exception e ){
					if( isDownload )
						downloadListener.onFailure(targetURL);
					e.printStackTrace();
					return new Util.ErrorContext(false, "The specified URL was incorrectly formed or unable to locate specified URL:\n" + e.getMessage());
				}
				connection.setRequestMethod( isGet ? "GET" : "POST" );
				connection.setRequestProperty("Content-Type", contentType == null ? ServerContentType.DEFAULT.contentTypeString : contentType.contentTypeString);
				connection.setRequestProperty ( "User-agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36");
				
				connection.setRequestProperty("Content-Length",
						"" + Integer.toString(urlParameters.getBytes().length));
				connection.setRequestProperty("Content-Language", "en-US");
	
				connection.setUseCaches(false);
				connection.setDoInput(true);
				connection.setDoOutput(true);
	
				// Send request if not GET posting
				if( !isGet ){
					DataOutputStream wr = new DataOutputStream(
							connection.getOutputStream());
					wr.writeBytes(urlParameters);
					wr.flush();
					wr.close();
				}
				
				if( shouldStop )
					return new Util.ErrorContext(false, "The connection timed out. Please check your internet connection.");
				
				// Get Response
				InputStream is = connection.getInputStream();
	
				if( !isDownload ){
					BufferedReader rd = new BufferedReader(new InputStreamReader(is));
					String line;
					StringBuffer response = new StringBuffer();
					while ((line = rd.readLine()) != null) {
						response.append(line);
						response.append(System.getProperty("line.separator"));
					}
					rd.close();
					
					return new Util.ErrorContext(true, response.toString());
				}
				else{//download
					//get server file name
					String filename = null;
					String contentDisposition = connection.getHeaderField("Content-Disposition");
					if( contentDisposition == null ){
						filename = "unknown.file";
					}
					else{
						String[] parts = contentDisposition.split("=");
						if( parts.length > 1 ){
							if( shouldGenerateRandomDownloadFileName ){
								filename = Util.generateUniqueID() + "_" + System.currentTimeMillis() + 
										(parts[1].contains(".") ? parts[1].substring(parts[1].lastIndexOf(".")).trim() : ".file");
							}
							else{
								filename = parts[1].trim().replaceAll("[\\/)(]", "-");
								filename = filename.trim().replaceAll("[\"'\n]", "");
							}
						}
						else
							filename = "unknown.file";
					}
					
					if( filename.equals("unknown.file") && (!targetURL.endsWith(".php") && targetURL.indexOf("?") == -1) ){
						filename = URLDecoder.decode(targetURL.substring(targetURL.lastIndexOf("/") + 1), "UTF8");
					}
					
					
					tempFileLocation = new File(TEMP_DOWNLOAD_DIRECTORY.toString() + File.separator + filename);
					if( tempFileLocation.exists() )
						tempFileLocation.delete();
					tempFileLocation.getParentFile().mkdirs();
					
					boolean created = tempFileLocation.createNewFile();
					if( !created ){
						tempFileLocation = new File(TEMP_DOWNLOAD_DIRECTORY.toString() + File.separator + Util.generateUniqueID() + filename.substring(filename.indexOf(".")));
						if( tempFileLocation.exists() )
							tempFileLocation.delete();
						tempFileLocation.getParentFile().mkdirs();
						tempFileLocation.createNewFile();
					}
					
					
					long fileSize = connection.getContentLength();
					
					FileOutputStream fos = new FileOutputStream(tempFileLocation);
					long downloaded = 0;
					int read;
					byte[] buffer = new byte[1024];//create a 1KB buffer					
					
					downloadListener.onStart(fileSize > 0 ? fileSize : 0);
					while( (read = is.read(buffer)) > 0 ){
						try{
							fos.write(buffer, 0, read);
							//fos.flush();
							downloaded += read;
							downloadListener.publish(downloaded);
							
							if( shouldCancel ){
								break;
							}
						}
						catch(Exception e){
							e.printStackTrace();
							return new Util.ErrorContext(false, e.getMessage());
						}
					}
					fos.flush();
					fos.close();
					
					
					try{
						is.close();
					}
					catch(Exception e){}
					
					if( !shouldCancel && (fileSize <= 0 && downloaded > 0 || downloaded == fileSize) )
						downloadListener.onFinish(downloaded, targetURL, tempFileLocation);
					else
						downloadListener.onCancel(downloaded, targetURL, tempFileLocation);
					
					return new Util.ErrorContext(true, "Download Completed sucessfully");
				}
			} catch (Exception e) {
				try{
					if( isDownload && tempFileLocation != null && tempFileLocation.exists() )
						tempFileLocation.delete();
					
					Thread.sleep(SLEEP_INTERVAL + ( SLEEP_INTERVAL / 2 * tried ));//let the sleep time be decaying
				}
				catch(Exception e1){}
			} finally {
				if (connection != null) {
					connection.disconnect();
				}
				
				tried++;
			}
		}while( tried < tries );
		
		if( isDownload )
			downloadListener.onFailure(targetURL);
		
		return new Util.ErrorContext(false, "Unable to connect to server after " + tries + " attempt(s). Resource may be unreachable.\nPlease make sure that your internet connection is on and stable and try again later.\n\nIf you're sure your internet is working, check that access is not being blocked by firewall/antivirus, and then try again");
	}
	
	private class Timeout implements Runnable, ActionListener{
		private int timeGone;
		private int timeout;
		private Timer timer;
		
		public Timeout(int timeout){
			timeGone = 0;
			this.timeout = timeout;
			
			timer = new Timer(timeout, this);
		}
		public void run(){
			timer.start();
		}
		public void stop(){
			timer.stop();
		}
		public void actionPerformed(ActionEvent event){
			if( timeGone >= timeout ){
				shouldStop = true;
				if( !isDownload && spi != null )
					spi.interruptPosting("Process timed out. Please check your internet connection.");
				timer.stop();
			}
		}
	}
	
	public enum ServerContentType{
		DEFAULT("application/x-www-form-urlencoded"),
		TEXT_HTML("text/html; charset=utf-8"),
		TEXT_PLAIN("text/plain; charset=utf-8"),
		JSON("application/json");
		
		private String contentTypeString;
		
		ServerContentType(String contentTypeString){
			this.contentTypeString = contentTypeString;
		}
	}
}
