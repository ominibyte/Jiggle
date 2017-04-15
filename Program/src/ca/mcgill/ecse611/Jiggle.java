package ca.mcgill.ecse611;


public class Jiggle implements JiggleActivityReporter{
	
	public Jiggle() throws Exception{
		new GreenfootRipper(this);
	}
	
	public static void main(String[] args) throws Exception{
		new Jiggle();
	}

	@Override
	public void activityFinished(String id) {
		switch( id ){
			case "GreenfootRipper":
				new ScenarioExtractor(this);
				break;
			case "ScenarioExtractor":
				new CodeDuplicateChecker(this);
				break;
			case "CodeDuplicateChecker":
				new CodeDuplicateXMLParser(this);
				break;
			case "CodeDuplicateXMLParser":
				
				break;
		}
	}

}
