import java.io.*;
import java.net.*;

public class Client {
    Socket socket;
    OutputStream outputStream;

    //create client
    public Client(String hostName, int portNum)throws UnknownHostException, IOException{
        socket = new Socket(hostName, portNum);
        outputStream = socket.getOutputStream();
    }

    //sends empty data to Clients port for a length of time
    public void sendData(long time) throws IOException{
        //TODO put in a try catch block over for this method

        //set vars
        time = time * 1000;
        byte[] data = new byte[1000];
        long start = System.currentTimeMillis();
        long dataSent = 0;

        //send data
        while(System.currentTimeMillis() - start < time){
            outputStream.write(data);
            dataSent += 1000;
        }

        //close steam/socket
        outputStream.close();
        socket.close();

        //print data stream results
        long kbSent = dataSent / 1000;
        long mbSent = kbSent / 1000;
        float mbSentPerSec = mbSent / (time / 1000);
        System.out.println("received=" + kbSent + " KB  Rate=" + mbSentPerSec +" Mbps");







    }
}
