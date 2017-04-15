package ca.mcgill.ecse611;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;

import org.json.me.JSONArray;
import org.json.me.JSONObject;

import ca.mcgill.ecse611.ServerPostingManager.ServerContentType;
import ca.mcgill.ecse611.Util.ErrorContext;

public class Starter {
	private static String server = "https://bugreports.qt.io/rest/api/2/search";
	private static String params = "jql=project+%3D+Qt&maxResults=1000&startAt=0";
	private static String filePath = "/Users/Richboy/Documents/ECSE611/issues.csv";
	
	Starter(){
		ServerPostingManager spm = new ServerPostingManager(new ServerPostingInterface() {
			
			@Override
			public void retriveMessage(ErrorContext context) {
				try{
					JSONObject object = new JSONObject(context.getMessage());
					
					processResponse(object);
					
					System.out.println("Done!!!");
				}
				catch(Exception e){
					e.printStackTrace();
				}
			}
			
			@Override
			public void interruptPosting(String text) {
				System.err.println(text);
			}
		});
		spm.setAsGetRequest();
		spm.setTargetURL(server);
		spm.setURLParameters(params);
		spm.setTimeout(60, true);
		spm.setRetriesCount(3);
		spm.setContentType(ServerContentType.JSON);
		spm.run();
	}
	
	private void processResponse(JSONObject responseObject) throws Exception{
		//create file
		File f = new File(filePath);
		if( f.exists() )
			f.delete();
		f.createNewFile();
		
		try( BufferedWriter writer = new BufferedWriter(new FileWriter(f)) ){
			//write file header
			writer.write("key;type;reporter;date");
			writer.newLine();
			
			JSONArray issues = responseObject.getJSONArray("issues");
			JSONObject issue;
			for(int i = 0; i < issues.length(); i++){
				issue = issues.getJSONObject(i);
				
				writer.write( issue.getString("key") 
						+ ";" + issue.getJSONObject("fields").getJSONObject("issuetype").getString("name")
						+ ";" + issue.getJSONObject("fields").getJSONObject("reporter").getString("name")
						+ ";" + convertToTimestamp(issue.getJSONObject("fields").getString("created")) );
				writer.newLine();
			}
		}
		finally{
			
		}
	}
	
	public static void main(String[] args){
		if( args.length == 2 ){
			server = args[0];
			params = args[1];
		}
		
		new Starter();
	}
	
	private long convertToTimestamp(String unixTime) throws Exception{
		//Sample Date: 2017-01-10T22:50:23.000+0000
		return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(unixTime).getTime() / 1000;
	}
}
