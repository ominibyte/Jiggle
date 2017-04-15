package ca.mcgill.ecse611;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class goes through the populated code smells in the MySQL Database and extracts
 * the code size parameters from the violation text for the 'Code Size' ruleset.
 * It stores the output in another table (code_smell_violation_codesize) and creates a link to the original table.
 * @author Richboy
 *
 */
public class CodeSizeDBMetricsExtractor {
	private JiggleActivityReporter reporter;
	private Query query;
	private int added;
	private ArrayList<Integer> params;
	
	public CodeSizeDBMetricsExtractor(JiggleActivityReporter reporter){
		this.reporter = reporter;
		params = new ArrayList<>();
		
		query = new Query("INSERT INTO code_smell_violation_codesize VALUES (NULL, ?, ?, ?)", Util.mcon);
		
		process();
		
		query.disconnect();
		
		System.out.println("\n\nDONE!!!");
		
		if( reporter != null )
			reporter.activityFinished(getClass().getName());
	}
	
	private void process(){
		added = 0;
		
		Query q = new Query("SELECT * FROM code_smell_violation WHERE fkrule_set = (SELECT id FROM code_smell_ruleset WHERE ruleset = 'Code Size')", Util.mcon);
		if( q.isFullyConnected() ){
			try{
				ResultSet rs = q.getPS().executeQuery();
				
				while(rs.next()){
					extractToSQL(rs.getInt("id"), rs.getString("violation_text"));
					
					if( added % 50 == 0 )
						query.getPS().executeBatch();
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
		params.clear();
		
		Pattern p = Pattern.compile("\\s\\d+");//PMD adds a space before the number. This will separate it from classes/methods with numbers
		Matcher m = p.matcher(violationText);
		
		while(m.find())
			params.add(Integer.parseInt(m.group().trim()));
		
		if( !params.isEmpty() ){
			if( params.size() > 2 )
				System.out.println(violationText);
			
			Integer value = params.get(0);
			Integer max = params.size() > 1 ? params.get(1) : null;
			
			query.getPS().setInt(1, rowID);
			query.getPS().setInt(2, value);
			query.getPS().setObject(3, max);
			
			query.getPS().addBatch();
			
			added++;//if we found something
		}
	}
	
	public static void main(String[] args){
		new CodeSizeDBMetricsExtractor(null);
	}
}
