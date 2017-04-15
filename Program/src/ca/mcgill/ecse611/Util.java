package ca.mcgill.ecse611;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class Util {
	public static String SQLitePath = "";
	public static final String DRIVER = "org.sqlite.JDBC";
	public static String DB_URL = "jdbc:sqlite:";
	public static final String DB_USERNAME = "";
	public static final String DB_PASSWORD = "";
	public static FileWriter writer;
	public static DatabaseConnection con, mcon;
	
	public static final String MYSQL_DRIVER = "com.mysql.jdbc.Driver";
	public static final String MYSQL_DB_URL_PREFIX = "jdbc:mysql://";
	public static String MYSQL_DB_HOST = "127.0.0.1";//"192.168.0.6";//"localhost";
	public static String MYSQL_DB_PORT = "3306";//"8592";// "3306";
	public static String MYSQL_DB_NAME = "ecse611";
	public static final String MYSQL_DB_USERNAME = "root";//"ratelsoft";
	public static final String MYSQL_DB_PASSWORD = "";//"apps";
	public static final String MYSQL_DB_URL = MYSQL_DB_URL_PREFIX + "address=(protocol=tcp)(host="+MYSQL_DB_HOST+")(port="+MYSQL_DB_PORT+")(user="+MYSQL_DB_USERNAME+")" + "/" + MYSQL_DB_NAME;
	
	
	public static final String POSTGRES_DRIVER = "org.postgresql.Driver";
	public static final String POSTGRES_DB_URL_PREFIX = "jdbc:postgresql://";//jdbc:postgresql://host:port/database
	public static String POSTGRES_DB_HOST = "localhost";
	public static String POSTGRES_DB_PORT = "5432";
	public static String POSTGRES_DB_NAME = "ecse611";
	public static final String POSTGRES_DB_USERNAME = "postgres";
	public static final String POSTGRES_DB_PASSWORD = "root";
	public static final String POSTGRES_DB_URL = POSTGRES_DB_URL_PREFIX + POSTGRES_DB_HOST + ":" + POSTGRES_DB_PORT + "/" + POSTGRES_DB_NAME + "?user=" + POSTGRES_DB_USERNAME + "&password=" + POSTGRES_DB_PASSWORD + "";
	
	static{
		DatabaseConnection con = new DatabaseConnection() {
			@Override
			public String getDriver() {
				return POSTGRES_DRIVER;
			}
			@Override
			public String getURL() {
				return POSTGRES_DB_URL;
			}
			@Override
			public boolean isTypeForwardOnly() {
				return false;
			}
		};
		Util.con = con;
		
		DatabaseConnection mcon = new DatabaseConnection() {
			@Override
			public String getDriver() {
				return MYSQL_DRIVER;
			}
			@Override
			public String getURL() {
				return MYSQL_DB_URL;
			}
			@Override
			public boolean isTypeForwardOnly() {
				return false;
			}
		};
		Util.mcon = mcon;
	}
	
	public static class ErrorContext{
		private boolean isSuccessful;
		private String message;
		private String title;
		
		public ErrorContext(boolean state, String mess){
			this(state, mess, null);
		}
		public ErrorContext(boolean state, String mess, String title){
			isSuccessful = state;
			message = mess;
			this.title = title;
		}
		public boolean isSuccessful(){
			return isSuccessful;
		}
		public boolean hasTitle(){
			return title != null;
		}
		public String getMessage(){
			return message;
		}
		public String getTitle(){
			return title;
		}
	}
	
	public static class SimpleMap<E, T>{
        private E object1;
        private T object2;
        private ArrayList properties;

        public SimpleMap(E object1, T object2){
            this.object1 = object1;
            this.object2 = object2;
            properties = new ArrayList();
        }
        public E getFirstElement(){
            return object1;
        }
        public T getSecondElement(){
            return object2;
        }
        public void setFirstElement(E object1){
            this.object1 = object1;
        }
        public void setSecondElement(T object2){
            this.object2 = object2;
        }
        public void addProperty(Object o){
            properties.add(o);
        }
        public Object getProperty(int index){
            if( properties.size() > index )
                return properties.get(index);
            else
                return null;
        }
        public boolean equals(Object o){
            if( o instanceof SimpleMap ){
                SimpleMap obj = (SimpleMap) o;
                return object1.equals(obj.getFirstElement()) && object2.equals(obj.getSecondElement());
            }
            return false;
        }
        public String toString(){
            if( object2 instanceof String )
                return object2.toString();
            else if( object1 instanceof String )
                return object1.toString();

            return object2.toString();
        }
    }
	
	public static String generateUniqueID(){
		return generateUniqueID(10);
	}
	public static String generateUniqueID( int length ){
		String id = "";
		String charOptions = "abcdefghijklmnopqrstuvwxyz0123456789";
		Random rand = new Random();
		
		for( int i = 0 ; i < length; i++){
			int index = rand.nextInt(charOptions.length());
			int caseRand = rand.nextInt(1);
			
			if( caseRand == 0 )//lowercase
				id += charOptions.substring(index, index + 1);
			else//uppercase
				id += charOptions.substring(index, index + 1).toUpperCase();
		}
				
		return id;
	}
	public static int generateINTCode(){
		Random rand = new Random();
		int codeLength = rand.nextInt(6) + 4;
		
		return generateINTCode(codeLength);
	}
	public static int generateINTCode( int length ){
		String code = "";
		String charOptions = "123456789";
		Random rand = new Random();
		
		for( int i = 0 ; i < length; i++){
			int index = rand.nextInt(charOptions.length());
			
			code += charOptions.substring(index, index + 1);
		}
		
		return Integer.parseInt(code);
	}
	
	public static void routeOutput(String text){
		routeOutput(text, false);
	}
	
	public static void routeOutput(String text, boolean isError){
		if( isError )
			System.err.print(text);
		else
			System.out.print(text);
		try{
			writer.write(text);
			writer.flush();
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public static void deleteFileAndDirectory(File directory){
		if( directory == null )
			return;
		
		if( !directory.isDirectory() ){
			directory.delete();
			return;
		}
		File[] files = directory.listFiles();
		for( File f : files ){
			if( f.isDirectory() )
				deleteFileAndDirectory(f);
			else
				f.delete();
		}
		directory.delete();
	}
	
	public static boolean extractZipTo(File zipFile, File folder, boolean deleteOnClose){
		try{
			ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
			byte[] buffer = new byte[1024];
			
			ZipEntry ze;
			while( (ze = zis.getNextEntry()) != null ){
				if( ze.isDirectory() ){
					File d = new File(folder, Util.isWindows() ? ze.getName() : ze.getName().replaceAll("\\\\", "/"));
					d.mkdirs();
				}
				else{
					File f = new File(folder, Util.isWindows() ? ze.getName() : ze.getName().replaceAll("\\\\", "/"));
					f.getParentFile().mkdirs();
					
					f.createNewFile();
					
					FileOutputStream fos = new FileOutputStream(f);
					int len;
					while( (len = zis.read(buffer)) > 0 ){
						fos.write(buffer, 0, len);
					}
					fos.close();
				}
				zis.closeEntry();
			}
			
			zis.close();
			
			boolean closed = false;
			if( deleteOnClose )
				closed = zipFile.delete();
			
			if( deleteOnClose && !closed )
				zipFile.deleteOnExit();
			
			return true;
		}
		catch(Exception e){
			boolean closed = false;
			if( deleteOnClose )
				closed = zipFile.delete();
			
			if( deleteOnClose && !closed )
				zipFile.deleteOnExit();
			
			e.printStackTrace();
			return false;
		}
	}
	
	public static boolean isWindows(){
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}
	
	public static boolean pingURL(String url, int timeout) {
	    url = url.replaceFirst("^https", "http"); // Otherwise an exception may be thrown on invalid SSL certificates.
	
	    try {
	        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
	        connection.setConnectTimeout(timeout);
	        connection.setReadTimeout(timeout);
	        connection.setRequestMethod("HEAD");
	        int responseCode = connection.getResponseCode();
	        return (200 <= responseCode && responseCode <= 399);
	    } catch (IOException exception) {
	        return false;
	    }
	}
}
