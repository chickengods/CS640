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

			long bytesReceived = 0;  // the number of bytes received from the client
			long bytes = 0;
			byte[] input = new byte[1000];
			long timePassed;  // will represent milliseconds passed while reading from client
			
			timePassed = System.currentTimeMillis();
			while ((bytes = in.read(input)) != -1)
			{
				//TODO I don't know much about networking but
				// wouldn't we count the amount of bytes received
				// then adding that to the total
				// amountRead = in.read(input)
				// bytesReceived += amountRead?
				// I could be wrong though

				bytesReceived += bytes;
			}
			
			timePassed = System.currentTimeMillis() - timePassed;
			clientSocket.close();
			serverSocket.close();
			long megabitsReceived = (bytesReceived / 1000) * 8;
			double readRate = megabitsReceived / (timePassed * 1000);  // find read rate in megabits/second
			System.out.println("received=" + kilobytesReceived + " KB rate=" + readRate + " Mbps");
			
		} catch (IOException e) {

		}
	}
	
}
