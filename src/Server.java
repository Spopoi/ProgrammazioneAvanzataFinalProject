import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
/**
 * @author davide
 */
public class Server {

    private final int port;
    public final ExecutorService executorService;
    private int okResponses;
    private double avgTime;
    private double maxTime;

    //constructor
    public Server(int port) {

        this.port = port;
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    }

    public void start() throws IOException {
        System.out.println("benvenuto nel panna server");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(socket, this);
                    clientHandler.start();

                } catch (IOException e) {
                    System.err.printf("Cannot accept connection due to %s", e);
                }
            }
        } finally {
            executorService.shutdown();
        }
    }

    public synchronized int getOkResponses(){
        return okResponses;
    }

    public synchronized void setOkResponses(int value){
        okResponses = value;
    }

    public synchronized double getMaxTime(){
        return maxTime;
    }

    public synchronized void setMaxTime(double value){
        maxTime = value;
    }

    public synchronized double getAvgTime(){
        return avgTime;
    }

    public synchronized void setAvgTime(double value){
        avgTime = value;
    }
}