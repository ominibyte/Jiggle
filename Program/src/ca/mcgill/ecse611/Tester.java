package ca.mcgill.ecse611;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Tester {
	public static final File SCENARIOS_DIRECTORY = ScenarioExtractor.DESTINATION_DIRECTORY;
	public static final File RAW_SCENARIO_PROJECTS_FOLDER = GreenfootRipper.SAVE_DIRECTORY;
	
	public static void main(String[] args){
		//get all the projects that failed parsing
		File[] extracted = SCENARIOS_DIRECTORY.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() && !f.getName().startsWith(".");
			}
		});
		
		File[] raw = RAW_SCENARIO_PROJECTS_FOLDER.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.getName().endsWith(".gfar");
			}
		});
		
		List<String> extractedNames = Arrays.stream(extracted).map(File::getName).collect(Collectors.toList());
		List<String> rawNames = Arrays.stream(raw).map(f -> f.getName().substring(0, f.getName().indexOf("."))).collect(Collectors.toList());
		
		System.out.println(extractedNames.size() + ", " + rawNames.size());
		
		rawNames.forEach(name -> {
			if( !extractedNames.contains(name) )
				System.out.println(name);
		});
	}
}
