package ca.mcgill.ecse611;


public interface Handler {
	public void onSuccess(String id, String content);
	public void onError(String id, String errorMessage);
}
