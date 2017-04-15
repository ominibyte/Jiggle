package ca.mcgill.ecse611;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Query {
	private boolean isConnected;
	private boolean isFullyConnected;
	private PreparedStatement ps;
	private Exception exception;
	private Connection con;
	
	public Query( String queryString, DatabaseConnection connection, boolean continueStatus ){
		isFullyConnected  = false;
		isConnected = false;
		try{
			try{
				Class.forName(connection.getDriver()).newInstance();
			}
			catch(Exception e){
				Class.forName(connection.getDriver());
			}
			//con = DriverManager.getConnection(connection.getURL(), connection.getUsername(), connection.getPassword());
			con = DriverManager.getConnection(connection.getURL());
			
			isConnected = true;
			
			if( continueStatus )
				nextPhase( queryString, connection );
		}
		catch(Exception e){
			isConnected = false;
			exception = e;
		}
	}
	
	public Query( String queryString, DatabaseConnection connection ){
		this(queryString, connection, true);
	}
	
	public Query( String queryString ){
		this(queryString, Util.con, true);
	}
	
	public void nextPhase( String queryString, DatabaseConnection connection ){
		try{
			ps = con.prepareStatement(queryString, !connection.isTypeForwardOnly() ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			isFullyConnected = true;
		}
		catch( Exception e ){
			exception = e;
			isFullyConnected = false;
		}
	}
		
	public boolean isConnected(){
		return isConnected;
	}
	
	public boolean isFullyConnected(){
		return isFullyConnected;
	}
	
	public PreparedStatement getPS(){
		return ps;
	}
	
	public Exception getException(){
		return exception;
	}
	
	public Connection getCon(){
		return con;
	}
	
	public void disconnect(){
		try{
			ps.close();
		}
		catch(Exception e){}
		try{
			con.close();
		}
		catch(Exception e){}
	}
	
}
