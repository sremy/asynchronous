package me;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Http client sending requests asynchronously by 3 parallel tasks limited by semaphore
 */
public class ClientCompletableSeveralTasksSemaphore {
    private static final Logger log = LogManager.getLogger(ClientCompletableSeveralTasksSemaphore.class);

    ExecutorService threadPool = Executors.newFixedThreadPool(5);
    ExecutorService specialThreadPool = Executors.newFixedThreadPool(2);
    Semaphore semaphore = new Semaphore(20);

    public static void main(String[] args) {
        System.out.println("Start tasks...");

        ClientCompletableSeveralTasksSemaphore clientApp = new ClientCompletableSeveralTasksSemaphore();
        clientApp.startTasks();
    }

    private void startTasks() {

        int taskCount = 20;
        List<CompletableFuture<String>> futureList = new ArrayList<>(taskCount);
        for (int taskId = 0; taskId < taskCount; taskId++) {
            CompletableFuture<String> completableFuture = runFutureTask(taskId);
            futureList.add(completableFuture);
        }
        log.info("Tasks submitted...");

        CompletableFuture<Void> finalCompletableFuture = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[taskCount]));
        List<String> mergedResult = finalCompletableFuture.thenApply(Void -> futureList.stream().map(CompletableFuture::join).collect(Collectors.toList())).join();
        log.info("## Global result: " + mergedResult);

        threadPool.shutdown();
        specialThreadPool.shutdown();
        try {
            threadPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private CompletableFuture<String> runFutureTask(int taskId) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        CompletableFuture<String> completableFuture =
                CompletableFuture.supplyAsync(() -> new MyTask(taskId).call(), threadPool)
                .handle((s, t) -> t != null ? "Other Exception" : s);
//        sleep();
        log.info("Task " + taskId + ") First step created, callable started.");

        completableFuture = completableFuture.thenApply(ClientCompletableSeveralTasksSemaphore::secondStep); // same thread than previous step: threadPool
        completableFuture = completableFuture.thenApplyAsync(ClientCompletableSeveralTasksSemaphore::thirdStep); // ForkJoinPool.commonPool()
        completableFuture = completableFuture.thenApplyAsync(ClientCompletableSeveralTasksSemaphore::fourthStep, specialThreadPool); // executor provided
        completableFuture = completableFuture.thenComposeAsync(ClientCompletableSeveralTasksSemaphore::lastCompose); // ForkJoinPool.commonPool()
        completableFuture = completableFuture.whenCompleteAsync((result, t) -> printTaskResult(result), specialThreadPool);
        return completableFuture;
    }

    private void printTaskResult(String result) {
        log.info("# Result of future => [" + result + "]");
        semaphore.release();
    }

    private void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static String secondStep(String in) {
//        log.info("Second step called [" + in + "]");
        return "> " + in + " <";
    }

    private static String thirdStep(String in) {
//        log.info("Third step called [" + in + "]");
        return "( " + in + " )";
    }

    private static String fourthStep(String in) {
//        log.info("Third step called [" + in + "]");
        return "{ " + in + " }";
    }

    private static CompletableFuture<String> lastCompose(String s) {
//        log.info("last ComposeFuture: " + s);
        return CompletableFuture.supplyAsync(() -> "# " + s + " #");
    }

    static class MyTask implements Callable<String> {
        private final int taskId;

        public MyTask(int taskId) {
            this.taskId = taskId;
        }

        @Override
        public String call() {
            log.info("Task {}) GET on 8000", taskId);
            try {
                URL url = new URL("http://localhost:8000");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");

                // The connection is opened on con.connect() or con.getInputStream() or con.getResponseCode()
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();

//            log.info("responseCode: " + con.getResponseCode());

                return content.toString();
            } catch (IOException e) {
                e.printStackTrace();
                return "Exception " + e;
            }
        }
    }
}
