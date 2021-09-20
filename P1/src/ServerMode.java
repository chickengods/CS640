import java.io.*;
import java.net.*;
import java.util.Date;

public class ServerMode {

	public static void Invoke(int port) {
		try (ServerSocket serverSocket = new ServerSocket(port);
				Socket clientSocket = serverSocket.accept();
				PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
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
			serverSocket.close();  // FIXME not sure
			clientSocket.close();  // FIXME not sure
			
			timePassed = timer.getTime() - timePassed;
			int kilobytesReceived = bytesReceived / 1000;
			double readRate = (kilobytesReceived / 125) / timePassed;  // find read rate in megabits/second
			
		} catch (IOException e) {

		}
	}
	
}
