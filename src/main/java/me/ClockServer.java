package me;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP server replying the current date after a sleep period
 * The count of ongoing requests is also returned with the thread name handling the response
 */
public class ClockServer {

    private static final Logger log = LogManager.getLogger(ClockServer.class);

    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws IOException {
        new ClockServer().runServer();
    }

    public void runServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(httpExecutor); // creates a default executor
        server.start();
        log.info("Server started...");
    }

    static class MyHandler implements HttpHandler {

        private final AtomicLong activeQueriesCount = new AtomicLong();

        @Override
        public void handle(HttpExchange exchange) {
            long ongoingQueries = activeQueriesCount.incrementAndGet();
            Instant instant = Clock.systemDefaultZone().instant();
            String response = instant.toString() + " " + Thread.currentThread().getName() + " [" + ongoingQueries + "]";
            log.info("GET [" + ongoingQueries + "]");
            try {
                Thread.sleep(1000);
                log.info("End of sleep");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                log.warn(e);
            } finally {
                activeQueriesCount.decrementAndGet();
            }
            log.info(" => " + response);
        }
    }
}
