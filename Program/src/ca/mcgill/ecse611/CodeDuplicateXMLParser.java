package ca.mcgill.ecse611;

import java.io.File;
import java.io.FilenameFilter;
import java.sql.PreparedStatement;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/*
 * This class goes through all the XML files generated from the CodeDuplicateChecker and populates 
 * the MySQL database for future queries.
 */
public class CodeDuplicateXMLParser {
	private JiggleActivityReporter reporter;
	private static final File XML_FOLDER = CodeDuplicateChecker.CODE_DUPLICATION_XML_REPORT_FOLDER;
	private Integer duplicationTableIndex = 0;
	private Integer duplicationFilesTableIndex = 0;
	
	public CodeDuplicateXMLParser(JiggleActivityReporter reporter){
		this.reporter = reporter;
		process();
		
		System.out.println("\n\nDONE!!!");
		
		if( reporter != null )
			reporter.activityFinished(getClass().getName());
	}
	
	private void process(){
		//get all xml files in the xml folder
		File[] xmlFiles = XML_FOLDER.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().trim().endsWith(".xml");
			}
		});
		
		Arrays.asList(xmlFiles).parallelStream().forEach(file -> {
			try{
				int fileID = Integer.parseInt(file.getName().substring(0, file.getName().indexOf(".")));
				
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(file);
				
				doc.getDocumentElement().normalize();
				
				if( doc.getDocumentElement().hasChildNodes() ){
					NodeList list = doc.getElementsByTagName("duplication");
					
					for (int i = 0; i < list.getLength(); i++) {
						Node node = list.item(i);
						Element element = (Element) node;
						int id;
						
						int lines = Integer.parseInt(element.getAttribute("lines"));
						int tokens = Integer.parseInt(element.getAttribute("tokens"));
						String code = element.getElementsByTagName("codefragment").item(0).getTextContent();
						
						//get the id
						synchronized(duplicationTableIndex){
							id = ++duplicationTableIndex;
						}
						
						//insert the data into the cpd_duplication table
						Query q = new Query("INSERT INTO cpd_duplication VALUES (?, ?, ?, ?, ?)", Util.mcon);
						if( q.isFullyConnected() ){
							PreparedStatement ps = q.getPS();
							ps.setInt(1, id);
							ps.setInt(2, fileID);
							ps.setInt(3, lines);
							ps.setInt(4, tokens);
							ps.setString(5, code);
							
							ps.executeUpdate();
						}
						else
							q.getException().printStackTrace();
						
						q.disconnect();
						
						q = new Query("INSERT INTO cpd_duplication_files VALUES (?, ?, ?, ?)", Util.mcon);
						PreparedStatement ps = q.getPS();
						
						NodeList fileList = element.getElementsByTagName("file");
						for(int j = 0; j < fileList.getLength(); j++){
							Element fileElement = (Element) fileList.item(j);
							int dtid;
							
							synchronized(duplicationFilesTableIndex){
								dtid = ++duplicationFilesTableIndex;
							}
							
							int line = Integer.parseInt(fileElement.getAttribute("line"));
							String path = fileElement.getAttribute("path");
							
							ps.setInt(1, dtid);
							ps.setInt(2, id);
							ps.setInt(3, line);
							ps.setString(4, path);
							
							ps.executeUpdate();
						}
						
						q.disconnect();
					}
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		});
	}
	
	public static void main(String[] args){
		new CodeDuplicateXMLParser(null);
	}
}
