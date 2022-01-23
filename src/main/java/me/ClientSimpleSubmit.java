package me;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;

/**
 * Http client sending a request by submitting Callable
 */
public class ClientSimpleSubmit {
    private static final Logger log = LogManager.getLogger(ClientSimpleSubmit.class);

    ExecutorService threadPool = Executors.newFixedThreadPool(5);

    public static void main(String[] args) {
        System.out.println("Start tasks...");

        ClientSimpleSubmit clientSimpleSubmit = new ClientSimpleSubmit();
        clientSimpleSubmit.startTasks();
    }

    private void startTasks() {
        Future<String> future = threadPool.submit(new MyTask());
        log.info("Task submitted...");
        try {
            log.info("Result of future => [" + future.get() + "]");
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    static class MyTask implements Callable<String> {
        @Override
        public String call() throws Exception {
            log.info("GET on 8000");
            URL url = new URL("http://localhost:8000");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            log.info("ResponseCode: " + con.getResponseCode());

            return content.toString();
        }
    }
}
