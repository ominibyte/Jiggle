package ca.mcgill.ecse611;

import java.io.File;

public interface DownloadListener {
	public void onFailure(String url);
	public void onFinish(long downloaded, String url, File savedFilePath);//the savedFilePath is the temporary directory the file was saved to with the complete name of the path and file where the file will be saved e.g C://.temp/app.exe
	public void onStart(long total);
	public void publish(long downloaded);//does NOT fire when download was cancelled
	public void onCancel(long downloaded, String url, File savedFilePath);
}
