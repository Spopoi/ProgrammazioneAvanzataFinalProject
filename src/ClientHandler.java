import java.io.*;
import java.net.Socket;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


public class ClientHandler extends Thread {

    private final Socket socket;
    private final Server server;
    private long startTime;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    public void run() {

        System.out.printf("[%1$tY-%1$tm-%1$td %1$tT] Connection from %2$s.%n", System.currentTimeMillis(), socket.getInetAddress());

        try (socket;
             BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            while (true) {
                String command = br.readLine();
                startTime = System.currentTimeMillis();
                if (command == null) {
                    System.err.println("Client abruptly closed connection");
                    break;
                }
                if (command.equals("BYE")) {
                    System.out.println("Closing connection.. Bye!");
                    break;
                }
                bw.write(processResponse(command) + System.lineSeparator());
                bw.flush();
            }
            System.out.printf("[%1$tY-%1$tm-%1$td %1$tT] Disconnection of %2$s.%n", System.currentTimeMillis(), socket.getInetAddress());
        } catch (
                IOException e) {
            System.err.printf("IO error: %s", e);
        }
    }

    protected String processResponse(String input) {

        input = input.replaceAll(" ", "");
        switch (input) { //managing STAT_REQUEST
            case "STAT_REQS":
                return makeResponse(String.valueOf(server.getOkResponses()));

            case "STAT_AVG_TIME":
                return makeResponse(String.valueOf(server.getAvgTime()));

            case "STAT_MAX_TIME":
                return makeResponse(String.valueOf(server.getMaxTime()));
        }

        ExpressionServer expressionServer = new ExpressionServer(input);
        Future<String> futureResult;
        synchronized (server.executorService) {
            futureResult = server.executorService.submit(expressionServer);
        }
        try {
            return makeResponse(futureResult.get());
        } catch (ExecutionException | InterruptedException e) {
            return makeResponse("Computation request failed: "+ e.getMessage());
        }
    }

    public String makeResponse(String mex) {

        double time = (System.currentTimeMillis() - startTime) / 1000.0;

        try {
            double value = Double.parseDouble(mex);
            NumberFormat nf = new DecimalFormat("0.000000");
            NumberFormat tf = new DecimalFormat("0.000");
            mex = "OK;" + tf.format(time) + ";" + nf.format(value);
            if(time > server.getMaxTime()) server.setMaxTime(time);
            server.setAvgTime((server.getAvgTime() * server.getOkResponses() + time) / (server.getOkResponses() +1) );
            server.setOkResponses(server.getOkResponses() + 1);

        } catch (NumberFormatException e) {
            mex = "ERR;"+ mex;
            System.err.println(mex);
        }
        return mex;
    }
}

