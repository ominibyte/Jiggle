package ca.mcgill.ecse611;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * This class goes through the populated code smells in the MySQL Database and extracts
 * the God class parameters from the violation text for the GodClass rule.
 * It stores the output in another table (code_smell_violation_godclass) and creates a link to the original table.
 * @author Richboy
 *
 */
public class GodClassDBMetricsExtractor {
	private JiggleActivityReporter reporter;
	private Query query;
	
	public GodClassDBMetricsExtractor(JiggleActivityReporter reporter){
		this.reporter = reporter;
		
		query = new Query("INSERT INTO code_smell_violation_godclass VALUES (NULL, ?, ?, ?, ?)", Util.mcon);
		
		process();
		
		query.disconnect();
		
		System.out.println("\n\nDONE!!!");
		
		if( reporter != null )
			reporter.activityFinished(getClass().getName());
	}
	
	private void process(){
		int added = 0;
		
		Query q = new Query("SELECT * FROM code_smell_violation WHERE fkrule = (SELECT id FROM code_smell_rules WHERE rule = 'GodClass')", Util.mcon);
		if( q.isFullyConnected() ){
			try{
				ResultSet rs = q.getPS().executeQuery();
				
				while(rs.next()){
					extractToSQL(rs.getInt("id"), rs.getString("violation_text"));
					added++;
					
					if( added % 50 == 0 ){
						query.getPS().executeBatch();
						added = 0;
					}
				}
				
				if( added > 0 )
					query.getPS().executeBatch();
				
				rs.close();
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		else
			q.getException().printStackTrace();
		
		q.disconnect();
	}
	
	private void extractToSQL(int rowID, String violationText) throws SQLException{
		String interest = violationText.substring(violationText.indexOf("(") + 1, violationText.indexOf(")"));
		String[] parts = interest.split(",");
		String wmd = parts[0].split("=")[1].trim();
		String atfd = parts[1].split("=")[1].trim();
		String tcc = parts[2].split("=")[1].trim();
		
		query.getPS().setInt(1, rowID);
		query.getPS().setInt(2, Integer.parseInt(wmd));
		query.getPS().setInt(3, Integer.parseInt(atfd));
		query.getPS().setFloat(4, Float.parseFloat(tcc));
		
		query.getPS().addBatch();
	}
	
	public static void main(String[] args){
		new GodClassDBMetricsExtractor(null);
	}
}
