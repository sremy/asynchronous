package me;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;

/**
 * Http client sending requests asynchronously with a CompletableFuture
 */
public class ClientCompletableSingleTask {
    private static final Logger log = LogManager.getLogger(ClientCompletableSingleTask.class);

    ExecutorService threadPool = Executors.newFixedThreadPool(5);
    ExecutorService specialThreadPool = Executors.newFixedThreadPool(2);

    public static void main(String[] args) {
        System.out.println("Start tasks...");

        ClientCompletableSingleTask clientApp = new ClientCompletableSingleTask();
        clientApp.startTasks();
    }

    private void startTasks() {

        CompletableFuture<String> completableFuture =
                CompletableFuture.supplyAsync(() -> new MyTask().call(), threadPool)
                .handle((s, t) -> t != null ? "Other Exception" : s);
        sleep();
        log.info("First step created, callable already executed.");

        completableFuture = completableFuture.thenApply(ClientCompletableSingleTask::secondStep); // same thread than previous step: threadPool
        completableFuture = completableFuture.thenApplyAsync(ClientCompletableSingleTask::thirdStep); // ForkJoinPool.commonPool()
        completableFuture = completableFuture.thenApplyAsync(ClientCompletableSingleTask::fourthStep, specialThreadPool); // executor provided
        completableFuture = completableFuture.thenComposeAsync(ClientCompletableSingleTask::lastCompose); // ForkJoinPool.commonPool()
        completableFuture = completableFuture.whenCompleteAsync((result, t) -> printTaskResult(result), specialThreadPool);

        completableFuture.join();
        log.info("Task done.");
//        printTaskResult(completableFuture);


        threadPool.shutdown();
        specialThreadPool.shutdown();
        try {
            threadPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void printTaskResult(String result) {
        log.info("Result of future => [" + result + "]");
    }

    private void printTaskResult(CompletableFuture<String> completableFuture) {
        try {
            log.info("Result of future => [" + completableFuture.get() + "]");
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static String secondStep(String in) {
        log.info("Second step called [" + in + "]");
        return "> " + in + " <";
    }

    private static String thirdStep(String in) {
        log.info("Third step called [" + in + "]");
        return "( " + in + " )";
    }

    private static String fourthStep(String in) {
        log.info("Third step called [" + in + "]");
        return "{ " + in + " }";
    }

    private static CompletableFuture<String> lastCompose(String s) {
        log.info("last ComposeFuture: " + s);
        return CompletableFuture.supplyAsync(() -> "#" + s + "#");
    }

    static class MyTask implements Callable<String> {
        @Override
        public String call() {
            log.info("GET on 8000");
            try {
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

//            log.info("ResponseCode: " + con.getResponseCode());

                return content.toString();
            } catch (IOException e) {
                e.printStackTrace();
                return "Exception " + e;
            }
        }
    }
}
