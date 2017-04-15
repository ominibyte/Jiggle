package ca.mcgill.ecse611;

public class InsufficientDataException extends RuntimeException {
	
	private static final long serialVersionUID = 2119198784105611379L;
	private String message;
	
	public InsufficientDataException(String message) {
		super(message);
	}
	
	public String getMessage(){
		return message;
	}
	
}
