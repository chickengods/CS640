import java.security.Signature;

public class main {
    public static boolean port_bound_check(int port){
        return 1024 <= port && port <= 65535;
    }

    public static void main(String[] args) {
        System.out.println("Code is starting");

        String program_mode = "";

        int host_sever = -1;
        int server_port = -1;
        int time = -1;

        //TODO what if the host and server ports are the same
        for (int i = 0; i < 2; i++){
            if (i == 0 && args[i].equals("-c")){ //check for client
                program_mode = "client";
                if (args.length != 7){ //check args length
                    //args length too long or too short -> throw error
                    //TODO --> add error code
                }
                continue;
            }
            else if (i == 0 && args[i].equals("-s")){ //check for sever
                program_mode = "sever";
                if (args.length != 3){ //check args length
                    //args length too long or too short --> throw error
                    //TODO --> add error code
                }
                continue;
            }
            else{ //not client or sever throw error
                //TODO --> add error code
            }
            if (program_mode.equals("client")){ //client mode parse args
                //flag check
                if (!args[1].equals("-h") && !args[3].equals("-p") && !args[5].equals("-t")){
                    //flags are wrong --> error
                    //TODO --> add erro code
                }
                //try to parse inputs into ints if fails then bad input
                try {
                    host_sever = Integer.parseInt(args[2]);
                    server_port = Integer.parseInt(args[4]);
                    time = Integer.parseInt(args[6]);
                }
                catch (NumberFormatException e){
                    System.out.println("Error: server ports and time must be ints");
                }
                //check input number bounds
                if (!port_bound_check(host_sever) || !port_bound_check(server_port) || time < 0){
                    //inputs out of bounds error
                    //TODO --> add error code
                }
            }
            else if (program_mode.equals("sever")) { //sever mode parse args
                if (!args[1].equals("-p")){
                    //flags are wrong --> error
                    //TODO --> add erro code
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
                    //TODO --> add error code
                }
            }
            else{ //not client or sever throw error
                //TODO --> add error code
            }

        }
    }
}
