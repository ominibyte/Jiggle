package ca.mcgill.ecse611;

public interface ServerPostingInterface {
	public void retriveMessage(Util.ErrorContext context);
	public void interruptPosting(String text);
}
