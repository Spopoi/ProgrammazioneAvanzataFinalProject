import java.io.IOException;

public class main {

    public static void main(String[] args) throws IOException{

        if(args.length == 0) System.out.println("Command-line arguments empty - No portNumber");
        else {
            Server server = new Server(Integer.parseInt(args[0]));
            server.start();
            System.out.println("closing server!");
        }
    }
}