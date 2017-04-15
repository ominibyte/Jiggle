package ca.mcgill.ecse611;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/*
 * This class goes through all the XML files generated from the CodeSmellFinder and populates 
 * the MySQL database for future queries.
 */
public class CodeSmellXMLParser {
	private JiggleActivityReporter reporter;
	private static final File XML_FOLDER_ROOT = CodeSmellFinder.CODE_SMELLS_FOLDER;
	
	public CodeSmellXMLParser(JiggleActivityReporter reporter){
		this.reporter = reporter;
		
		process();
		
		System.out.println("\n\nDONE!!!");
		
		if( reporter != null )
			reporter.activityFinished(getClass().getName());
	}
	
	private void process(){
		//get all folders that have xml files in them
		File[] xmlFolders = XML_FOLDER_ROOT.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() && !f.getName().startsWith(".");
			}
		});
		
		//get all xml files from their various xml folders
		List<File> xmlFiles = new ArrayList<>();
		for(File folder : xmlFolders){
			xmlFiles.addAll(Arrays.asList(folder.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.toLowerCase().trim().endsWith(".xml");
				}
			})));
		}
		
		xmlFiles.parallelStream().forEach(file -> {
			try{
				int projectID = Integer.parseInt(file.getName().substring(0, file.getName().indexOf(".")));
				
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(file);
				
				doc.getDocumentElement().normalize();
				
				if( doc.getDocumentElement().hasChildNodes() ){
					NodeList fileNodeList = doc.getElementsByTagName("file");
					
					for (int i = 0; i < fileNodeList.getLength(); i++) {
						Node fileNode = fileNodeList.item(i);
						Element fileNodeElement = (Element) fileNode;
						
						String filePath = fileNodeElement.getAttribute("name");
						//String filePath = fileNodeElement.getElementsByTagName("name").item(0).getTextContent();
						
						//insert the data into the cpd_duplication table
						Query q = new Query("INSERT IGNORE INTO code_smell VALUES (NULL, ?, ?)", Util.mcon);
						if( q.isFullyConnected() ){
							PreparedStatement ps = q.getPS();
							ps.setInt(1, projectID);
							ps.setString(2, filePath);
							
							ps.executeUpdate();
						}
						else
							q.getException().printStackTrace();
						
						q.disconnect();
						
						
						NodeList violationNodeList = fileNodeElement.getElementsByTagName("violation");
						for(int j = 0; j < violationNodeList.getLength(); j++){
							Element violationNodeElement = (Element) violationNodeList.item(j);
							
							int beginline = Integer.parseInt(violationNodeElement.getAttribute("beginline"));
							int endline = Integer.parseInt(violationNodeElement.getAttribute("endline"));
							int begincolumn = Integer.parseInt(violationNodeElement.getAttribute("begincolumn"));
							int endcolumn = Integer.parseInt(violationNodeElement.getAttribute("endcolumn"));
							String rule = violationNodeElement.getAttribute("rule");
							String ruleset = violationNodeElement.getAttribute("ruleset");
							String theClass = violationNodeElement.getAttribute("class");
							String variable = violationNodeElement.hasAttribute("variable") ? violationNodeElement.getAttribute("variable") : null;
							String method = violationNodeElement.hasAttribute("method") ? violationNodeElement.getAttribute("method") : null;
							int priority = Integer.parseInt(violationNodeElement.getAttribute("priority"));
							String violationText = violationNodeElement.getTextContent();
							
							//insert the rule
							q = new Query("INSERT IGNORE INTO code_smell_rules VALUES (NULL, ?)", Util.mcon);
							if( q.isFullyConnected() ){
								PreparedStatement ps = q.getPS();
								ps.setString(1, rule);
								
								ps.executeUpdate();
							}
							else
								q.getException().printStackTrace();
							
							q.disconnect();
							
							//insert the ruleset
							q = new Query("INSERT IGNORE INTO code_smell_ruleset VALUES (NULL, ?)", Util.mcon);
							if( q.isFullyConnected() ){
								PreparedStatement ps = q.getPS();
								ps.setString(1, ruleset);
								
								ps.executeUpdate();
							}
							else
								q.getException().printStackTrace();
							
							q.disconnect();
							
							//insert the violation
							q = new Query("INSERT INTO code_smell_violation VALUES (NULL, (SELECT id FROM code_smell WHERE project_id = ? AND file = ?), ?, ?, ?, ?, (SELECT id FROM code_smell_rules WHERE rule = ?), (SELECT id FROM code_smell_ruleset WHERE ruleset = ?), ?, ?, ?, ?, ?)", Util.mcon);
							if( q.isFullyConnected() ){
								PreparedStatement ps = q.getPS();
								int pos = 0;
								ps.setInt(++pos, projectID);
								ps.setString(++pos, filePath);
								ps.setInt(++pos, beginline);
								ps.setInt(++pos, endline);
								ps.setInt(++pos, begincolumn);
								ps.setInt(++pos, endcolumn);
								ps.setString(++pos, rule);
								ps.setString(++pos, ruleset);
								ps.setString(++pos, theClass);
								ps.setInt(++pos, priority);
								ps.setString(++pos, variable);
								ps.setString(++pos, method);
								ps.setString(++pos, violationText);
								
								ps.executeUpdate();
							}
							else
								q.getException().printStackTrace();
							
							q.disconnect();
						}
					}
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		});
	}
	
	public static void main(String[] args){
		new CodeSmellXMLParser(null);
	}
}
