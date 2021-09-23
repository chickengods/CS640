

public class main {
    public static boolean port_bound_check(int port){
        return 1024 <= port && port <= 65535;
    }

    public static void main(String[] args) throws Exception{
        System.out.println("Code is starting");

        String program_mode = "";

        String host_sever = "";
        int server_port = -1;
        long time = -1;


        for (int i = 0; i < 2; i++){
            if (i == 0 && args[i].equals("-c")){ //check for client
                program_mode = "client";
                if (args.length != 7){ //check args length
                    //args length too long or too short -> throw error
                    throw new Exception("Error: invalid arguments");
                }
                continue;
            }
            else if (i == 0 && args[i].equals("-s")){ //check for sever
                program_mode = "sever";
                if (args.length != 3){ //check args length
                    //args length too long or too short --> throw error
                    throw new Exception("Error: invalid arguments");
                }
                continue;
            }
            else if (i == 0){ //not client or sever throw error
                throw new Exception("Error: invalid arguments");
            }

            if (program_mode.equals("client")){ //client mode parse args
                //flag check
                if (!args[1].equals("-h") && !args[3].equals("-p") && !args[5].equals("-t")){
                    //flags are wrong --> error
                    throw new Exception("Error: invalid arguments");
                }
                //try to parse inputs into ints if fails then bad input
                try {
                    host_sever = args[2];
                    server_port = Integer.parseInt(args[4]);
                    time = Integer.parseInt(args[6]);
                }
                catch (NumberFormatException e){
                    System.out.println("Error: server ports and time must be ints");
                }
                //check input number bounds
                if (!port_bound_check(server_port)){
                    //inputs out of bounds error
                    throw new Exception("Error: port number must be in the range 1024 to 65535");
                }
                if (time < 0){
                    throw new Exception("Error: invalid arguments");
                }
            }
            else if (program_mode.equals("sever")) { //sever mode parse args
                if (!args[1].equals("-p")){
                    //flags are wrong --> error
                    throw new Exception("Error: invalid arguments");
                }
                try {
                    server_port = Integer.parseInt(args[2]);
                }
                catch (NumberFormatException e){
                    System.out.println("Error: server ports and time must be ints");
                }
                //check input number bounds
                if (!port_bound_check(server_port)){
                    //inputs out of bounds error
                    throw new Exception("Error: port number must be in the range 1024 to 65535");
                }
            }
            else{ //not client or sever throw error
                throw new Exception("Error: invalid arguments");
            }

        }

        //TODO -> add try catch block here
        if (program_mode.equals("client")){
            Client client = new Client(host_sever, server_port);
            client.sendData(time);
        }
        else if (program_mode.equals("sever")){
            ServerMode sever = new ServerMode(server_port);
            sever.Invoke();
        }
        else{
            throw new Exception("Error: invalid arguments");
        }
    }
}
