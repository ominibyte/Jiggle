package ca.mcgill.ecse611;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class generates the report dataset statistics and presents them in latex format
 * @author Richboy
 *
 */
public class SummaryStatisticsGenerator {
	private JiggleActivityReporter reporter;
	private List<List<StatisticsPack>> generalReport;
	private LinkedList<Number> container = new LinkedList<>();
	
	public SummaryStatisticsGenerator(JiggleActivityReporter reporter){
		this.reporter = reporter;
		generalReport = new ArrayList<>();
		
		process();
		
		generateCSVFile();
		generateLatexTable();
		
		System.out.println("\n\nDONE!!!");
		
		if( reporter != null )
			reporter.activityFinished(getClass().getName());
	}
	
	private void process(){
		processForSizes();
		processForComplexity();
		processForOOPConcepts();
	}
	
	private void generateCSVFile(){
		//TODO
	}
	
	private void generateLatexTable(){
		String latex = "\\caption{Summary Statistics of the dataset of 5,968 Greenfoot Scenarios}\n"
				+ "\\label{table:basicstats}"
				+ "\\begin{tabularx}{\\textwidth}{llrrrrrr}\n";
		latex += " & & \\textbf{mean} & \\textbf{min} & \\textbf{Q1} & \\textbf{median} & \\textbf{Q3} & \\textbf{max} \\\\ \n";
		latex += "\\hline\n";
		
		for(List<StatisticsPack> entries: generalReport){
			int count = 0;
			for( StatisticsPack pack : entries ){//this should be auto sorted by entryOrder
				latex += " " + (count == 0 ? pack.entryGroup : "") + " & "+ pack.entryName +" & "+ adjustForDisplay(pack.mean) +" & "+ adjustForDisplay(pack.min) +" & "+ adjustForDisplay(pack.q1) +" & "+ adjustForDisplay(pack.median) +" & "+ adjustForDisplay(pack.q3) +" & "+ adjustForDisplay(pack.max) +" \\\\ \n";
				count++;
			}
			latex += "\\hline\n";
		}
				
		latex += "\\end{tabularx}";
		
		try{
			Files.write(new File(GreenfootRipper.PROJECT_DIRECTORY, "statistics_table_latex.txt").toPath(), latex.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private String adjustForDisplay(double value){
		DecimalFormat formatter = new DecimalFormat("0.#");
		return formatter.format(value);
	}
	
	private void processForSizes(){
		//DONE: number of classes per scenario
		//DONE: number of methods per scenario
		//DONE: number of methods per script per project
		//DONE: number of fields per project
		//DONE: number of fields per script per project
		//DONE: number of non-empty java class files per project
		//DONE: number of java class files per project
		//DONE: package size (.gfar)
		//DONE: sloccount per scenario
		//DONE: sloccount per Java file per scenario
		
		List<StatisticsPack> entries = new ArrayList<>();
		
		int entryOrder = 1;
		String entryGroup = "Size";
		
		//number of classes per scenario
		container.clear();
		processQuery("SELECT class_count FROM project_stats WHERE class_count > 0 ORDER BY class_count ASC", "class_count");
		processEntry(entryGroup, entryOrder++, "Number of classes per Scenario", container, entries);
		
		
		//number of methods per scenario
		container.clear();
		processQuery("SELECT method_count FROM project_stats WHERE class_count > 0 AND method_count > 0 ORDER BY method_count ASC", "method_count");
		processEntry(entryGroup, entryOrder++, "Number of methods per Scenario with methods", container, entries);
		
		
		//number of methods per script per scenario
		container.clear();
		processQuery("SELECT project_per_file_metrics.method_count FROM project_per_file_metrics INNER JOIN project_stats ON project_per_file_metrics.project_id = project_stats.project_id WHERE class_count > 0 AND project_per_file_metrics.method_count > 0 ORDER BY method_count ASC", "method_count");
		processEntry(entryGroup, entryOrder++, "Number of methods per Java per Scenario with methods", container, entries);
		
		
		//number of fields per scenario
		container.clear();
		processQuery("SELECT field_count FROM project_stats WHERE class_count > 0 ORDER BY field_count ASC", "field_count");
		processEntry(entryGroup, entryOrder++, "Number of fields per Scenario", container, entries);
		
		
		//number of fields per java file per scenario
		container.clear();
		processQuery("SELECT project_per_file_metrics.field_count FROM project_per_file_metrics INNER JOIN project_stats ON project_per_file_metrics.project_id = project_stats.project_id WHERE class_count > 0 ORDER BY field_count ASC", "field_count");
		processEntry(entryGroup, entryOrder++, "Number of fields per Java file per Scenario", container, entries);
		
		
		//number of non-empty Java class files per scenario
		container.clear();
		Map<String, Integer> projectFilesMap = new HashMap<>();
		
		File[] scenarioFolders = CodeParser.SCENARIOS_DIRECTORY.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return dir.isDirectory() && !name.startsWith(".");
			}
		});
		
		for(File folder : scenarioFolders){
			int size = CodeParser.filterForJavaFiles(folder).size();
			if( size == 0 )
				continue;
			projectFilesMap.put(folder.getName(), size);
		}
		
		projectFilesMap.forEach((key, value) -> container.add(value));
		LinkedList<Number> containerCopy = new LinkedList<>();
		containerCopy.addAll(container.stream().sorted().collect(Collectors.toList()));
		
		processEntry(entryGroup, entryOrder++, "Number of non-empty Java files per Scenario", containerCopy, entries);
		
		
		//number of Java class files per scenario
		container.clear();
		scenarioFolders = new File(GreenfootRipper.PROJECT_DIRECTORY, "EmptyFiles/").listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return dir.isDirectory() && !name.startsWith(".");
			}
		});
		
		for(File folder : scenarioFolders){
			int size = CodeParser.filterForJavaFiles(folder).size();
			if( size == 0 )
				continue;
			projectFilesMap.merge(folder.getName(), size, Integer::sum);
		}
		
		projectFilesMap.forEach((key, value) -> container.add(value));
		containerCopy.clear();
		containerCopy.addAll(container.stream().sorted().collect(Collectors.toList()));
		
		processEntry(entryGroup, entryOrder++, "Number of Java class files per Scenario", containerCopy, entries);
		projectFilesMap.clear();
		containerCopy.clear();
		
		
		//package size
		container.clear();
		Query q = new Query("SELECT package_size FROM project_sizes ORDER BY package_size ASC", Util.mcon);
		if( q.isFullyConnected() ){
			try{
				ResultSet rs = q.getPS().executeQuery();
				
				while( rs.next() )
					container.add(rs.getInt("package_size") / 1024);
				
				rs.close();
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		else
			q.getException().printStackTrace();
		q.disconnect();
		
		processEntry(entryGroup, entryOrder++, "Package size per Scenario (in KB)", container, entries);
		
		
		//SLOC Count per scenario
		container.clear();
		q = new Query("SELECT sloc FROM project_sloc ORDER BY sloc ASC", Util.mcon);
		if( q.isFullyConnected() ){
			try{
				ResultSet rs = q.getPS().executeQuery();
				
				while( rs.next() )
					container.add(rs.getInt("sloc"));
				
				rs.close();
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		else
			q.getException().printStackTrace();
		q.disconnect();
		
		processEntry(entryGroup, entryOrder++, "SLOC count per scenario", container, entries);
		
		
		//SLOC Count per file per scenario
		container.clear();
		q = new Query("SELECT sloc FROM project_file_sloc ORDER BY sloc ASC", Util.mcon);
		if( q.isFullyConnected() ){
			try{
				ResultSet rs = q.getPS().executeQuery();
				
				while( rs.next() )
					container.add(rs.getInt("sloc"));
				
				rs.close();
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		else
			q.getException().printStackTrace();
		q.disconnect();
		
		processEntry(entryGroup, entryOrder++, "SLOC count per Java file per scenario", container, entries);
		
		generalReport.add(entries);
	}
	
	private void processEntry(String entryGroup, int entryOrder, String entryName, LinkedList<Number> container, List<StatisticsPack> collector){
		StatisticsPack pack = new StatisticsPack();
		pack.entryGroup = entryGroup;
		pack.entryOrder = entryOrder;
		pack.entryName = entryName;
		pack.min = container.getFirst().doubleValue();
		pack.max = container.getLast().doubleValue();
		pack.mean = container.stream().mapToInt(Number::intValue).average().getAsDouble();
		pack.median = getMedian(container);
		pack.q1 = getQ1(container);
		pack.q3 = getQ3(container);
		
		collector.add(pack);
	}
	
	private double getMedian(LinkedList<Number> container){
		return container.size() % 2 == 0 ? (container.get( container.size() / 2 ).doubleValue() + container.get( container.size() / 2 - 1 ).doubleValue()) / 2 : container.get( container.size() / 2 ).doubleValue();
	}
	
	private double getQ1(LinkedList<Number> container){
		return container.size() % 4 == 0 ? (container.get( container.size() / 4 ).doubleValue() + container.get( container.size() / 4 - 1 ).doubleValue()) / 2 : container.get( container.size() / 4 ).doubleValue();
	}
	
	private double getQ3(LinkedList<Number> container){
		if( container.size() % 4 != 0 && container.size() % 2 != 0 )	//if the splits are both odds
			return container.size() % 4 == 0 ? (container.get( container.size() / 4 + (int) Math.ceil(container.size() / 2.0) ).doubleValue() + container.get( container.size() / 4 + ((int) Math.ceil(container.size() / 2.0)) - 1 ).doubleValue()) / 2 : container.get( container.size() / 4 + (int) Math.ceil(container.size() / 2.0) ).doubleValue();
		else
			return container.size() % 4 == 0 ? (container.get( container.size() / 4 + container.size() / 2 ).doubleValue() + container.get( container.size() / 4 + container.size() / 2 - 1 ).doubleValue()) / 2 : container.get( container.size() / 4 + container.size() / 2 ).doubleValue();
	}
	
	private void processForComplexity(){
		//cyclomatic complexity per class
		//cyclomatic complexity per method per class
		
		List<StatisticsPack> entries = new ArrayList<>();
		
		int entryOrder = 1;
		String entryGroup = "Complexity";
		
		//cyclomatic complexity per class
		container.clear();
		processQuery("SELECT value FROM code_smell_violation_codesize INNER JOIN code_smell_violation ON code_smell_violation.id = code_smell_violation_codesize.fkcode_smell_violation WHERE code_smell_violation.method IS NULL AND code_smell_violation.fkrule = (SELECT id FROM code_smell_rules WHERE rule = 'CyclomaticComplexity') ORDER BY value ASC", "value");
		processEntry(entryGroup, entryOrder++, "Cyclomatic Complexity (CC) per class", container, entries);
		
		
		//cyclomatic complexity per method per class
		container.clear();
		processQuery("SELECT value FROM code_smell_violation_codesize INNER JOIN code_smell_violation ON code_smell_violation.id = code_smell_violation_codesize.fkcode_smell_violation WHERE code_smell_violation.method IS NOT NULL AND code_smell_violation.fkrule = (SELECT id FROM code_smell_rules WHERE rule = 'CyclomaticComplexity') ORDER BY value ASC", "value");
		//processQuery("SELECT value FROM code_smell_violation_codesize INNER JOIN code_smell_violation ON code_smell_violation.id = code_smell_violation_codesize.fkcode_smell_violation WHERE code_smell_violation.method IS NOT NULL AND code_smell_violation.class IS NOT NULL AND code_smell_violation.fkrule = (SELECT id FROM code_smell_rules WHERE rule = 'CyclomaticComplexity') GROUP BY code_smell_violation.class ORDER BY value ASC", "value");
		processEntry(entryGroup, entryOrder++, "Cyclomatic Complexity (CC) per method per class", container, entries);
		
		generalReport.add(entries);
	}
	
	private void processForOOPConcepts(){
		//DONE: Number of classes per scenario	*excluded
		//DONE: Number of interfaces per scenario
		//DONE: Number of abstract classes per scenario
		//DONE: Number of abstract methods per scenario
		//DONE: Number of generic classes per scenario
		//DONE: Number of generic methods per scenario
		//DONE: Number of generic interfaces per scenario
		//DONE: Number of polymorphism usages per scenario
		//DONE: Total class inheritance per scenario
		//DONE: Total interface inheritance per scenario
		
		List<StatisticsPack> entries = new ArrayList<>();
		
		int entryOrder = 1;
		String entryGroup = "OOP Concepts";
		
		//Number of classes per project	*already done for code size
//		container.clear();
//		processQuery("SELECT class_count FROM project_stats ORDER BY class_count ASC", "class_count");
//		processEntry(entryGroup, entryOrder++, "Number of Classes per scenario", container, entries);
		
		
		//Number of interfaces per project
		container.clear();
		processQuery("SELECT interface_count FROM project_stats ORDER BY interface_count ASC", "interface_count");
		processEntry(entryGroup, entryOrder++, "Number of Interfaces per Scenario", container, entries);
		
		
		//Number of abstract classes per scenario
		container.clear();
		processQuery("SELECT abs_class_count FROM project_stats ORDER BY abs_class_count ASC", "abs_class_count");
		processEntry(entryGroup, entryOrder++, "Number of Abstract Classes per Scenario", container, entries);
		
		
		//Number of abstract methods per scenario
		container.clear();
		processQuery("SELECT abs_method_count FROM project_stats ORDER BY abs_method_count ASC", "abs_method_count");
		processEntry(entryGroup, entryOrder++, "Number of Abstract Methods per Scenario", container, entries);
		
		
		//Number of generic classes per scenario
		container.clear();
		processQuery("SELECT gen_class_count FROM project_stats ORDER BY gen_class_count ASC", "gen_class_count");
		processEntry(entryGroup, entryOrder++, "Number of Generic Classes per Scenario", container, entries);
		
		
		//Number of generic methods per scenario
		container.clear();
		processQuery("SELECT gen_method_count FROM project_stats ORDER BY gen_method_count ASC", "gen_method_count");
		processEntry(entryGroup, entryOrder++, "Number of Generic Methods per Scenario", container, entries);
		
		
		//Number of generic interfaces per scenario
		container.clear();
		processQuery("SELECT gen_interface_count FROM project_stats ORDER BY gen_interface_count ASC", "gen_interface_count");
		processEntry(entryGroup, entryOrder++, "Number of Generic Interfaces per Scenario", container, entries);
		
		
		//Total class inheritance per scenario
		container.clear();
		processQuery("SELECT class_inheritance_count FROM project_stats ORDER BY class_inheritance_count ASC", "class_inheritance_count");
		processEntry(entryGroup, entryOrder++, "Total Class Inheritance per Scenario", container, entries);
		
		
		//Total interface inheritance per scenario
		container.clear();
		processQuery("SELECT interface_inheritance_count FROM project_stats ORDER BY interface_inheritance_count ASC", "interface_inheritance_count");
		processEntry(entryGroup, entryOrder++, "Total Interface Inheritance per Scenario", container, entries);
		
		
		//Total Polymorphism usages per scenario
		container.clear();
		processQuery("SELECT polymorphism_usage_count FROM project_stats ORDER BY polymorphism_usage_count ASC", "polymorphism_usage_count");
		processEntry(entryGroup, entryOrder++, "Total Polymorphism usages per Scenario", container, entries);
		
		generalReport.add(entries);
	}
	
	private void processQuery(String queryString, String key){
		Query q = new Query(queryString, Util.mcon);
		if( q.isFullyConnected() ){
			try{
				ResultSet rs = q.getPS().executeQuery();
				
				while( rs.next() )
					container.add(rs.getInt(key));
				
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
	
	
	public static void main(String[] args){
		new SummaryStatisticsGenerator(null);
	}
	
	private class StatisticsPack{
		private String entryName;
		private int entryOrder;
		private String entryGroup;
		private double mean;
		private double median;
		private double q1;
		private double q3;
		private double min;
		private double max;
	}
}
