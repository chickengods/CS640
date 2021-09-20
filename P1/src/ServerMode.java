import java.io.*;
import java.net.*;
import java.util.Date;

public class ServerMode {
	
	private int port;
	public ServerMode(int port)
	{
		this.port = port;
	}

	public void Invoke() {
		try (ServerSocket serverSocket = new ServerSocket(this.port);
				Socket clientSocket = serverSocket.accept();
				InputStream in = clientSocket.getInputStream(); ) {

			int bytesReceived = 0;  // the number of bytes received from the client
			byte[] input = new byte[1000];
			long timePassed;  // will represent milliseconds passed while reading from client
			Date timer = new Date();
			
			timePassed = timer.getTime();
			while ((in.read(input)) > 0)
			{
				bytesReceived += 1000;
			}
			
			timePassed = timer.getTime() - timePassed;
			int kilobytesReceived = bytesReceived / 1000;
			double readRate = (kilobytesReceived / 125) / (timePassed / 1000);  // find read rate in megabits/second
			System.out.println("received=" + kilobytesReceived + " KB rate=" + readRate + " Mbps");
			
		} catch (IOException e) {

		}
	}
	
}
