package ca.mcgill.ecse611;

public abstract class DatabaseConnection {
	protected String DRIVER;
	protected String DB_URL;
	protected String DB_USERNAME;
	protected String DB_PASSWORD;
	
	public static DatabaseConnection getConnection(String driver, String url, String username, String password) throws InsufficientDataException {
		return null;
	}
	public String getDriver(){
		return DRIVER;
	}
	public String getURL(){
		return DB_URL;
	}
	public String getUsername(){
		return DB_USERNAME;
	}
	public String getPassword(){
		return DB_PASSWORD;
	}
	public abstract boolean isTypeForwardOnly();
}
