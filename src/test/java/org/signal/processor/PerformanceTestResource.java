package org.signal.processor;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.signal.processor.amqp.DynamicAmqpMessagingService;
import org.signal.processor.amqp.HighPerformanceAmqpMessagingService;

/**
 * REST endpoints for testing high-performance messaging capabilities.
 */
@Path("/amqp-perf")
public class PerformanceTestResource {

    @Inject
    private HighPerformanceAmqpMessagingService highPerfService;

    @Inject
    private DynamicAmqpMessagingService standardService;

    /**
     * Sends a single message using the high-performance service.
     */
    @POST
    @Path("/send-fast")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sendFast(MessageRequest request) {
        long startTime = System.nanoTime();

        highPerfService.sendMessageFast(request.topic(), request.message());

        long duration = System.nanoTime() - startTime;

        return Response.ok()
                .entity(String.format("Message sent in %d ns (%.3f μs)", duration, duration / 1000.0))
                .build();
    }

    /**
     * Sends a batch of messages for throughput testing.
     */
    @POST
    @Path("/send-batch")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sendBatch(BatchRequest request) {
        long startTime = System.nanoTime();

        highPerfService.sendMessageBatch(request.topic(), request.messages());

        long duration = System.nanoTime() - startTime;
        double messagesPerSecond = request.messages().length / (duration / 1_000_000_000.0);

        return Response.ok()
                .entity(String.format("Batch of %d messages sent in %.3f ms (%.0f msgs/sec)",
                        request.messages().length, duration / 1_000_000.0, messagesPerSecond))
                .build();
    }

    /**
     * Benchmark comparison between standard and high-performance services.
     */
    @POST
    @Path("/benchmark")
    @Consumes(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> benchmark(BenchmarkRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            String topic = "benchmark-topic";
            String message = "Benchmark message " + System.currentTimeMillis();

            // Warm up
            for (int i = 0; i < 100; i++) {
                highPerfService.sendMessageFast(topic, message + "-warmup-" + i);
            }

            // Benchmark standard service
            long standardStart = System.nanoTime();
            for (int i = 0; i < request.messageCount(); i++) {
                standardService.sendMessage(topic, message + "-std-" + i);
            }
            long standardDuration = System.nanoTime() - standardStart;

            // Benchmark high-performance service
            long highPerfStart = System.nanoTime();
            for (int i = 0; i < request.messageCount(); i++) {
                highPerfService.sendMessageFast(topic, message + "-hp-" + i);
            }
            long highPerfDuration = System.nanoTime() - highPerfStart;

            // Calculate results
            double standardMsgsPerSec = request.messageCount() / (standardDuration / 1_000_000_000.0);
            double highPerfMsgsPerSec = request.messageCount() / (highPerfDuration / 1_000_000_000.0);
            double improvement = (highPerfMsgsPerSec / standardMsgsPerSec - 1) * 100;

            String result = String.format(
                    "Benchmark Results (%d messages):\n" +
                            "Standard Service: %.3f ms (%.0f msgs/sec)\n" +
                            "High-Perf Service: %.3f ms (%.0f msgs/sec)\n" +
                            "Performance Improvement: %.1f%%",
                    request.messageCount(),
                    standardDuration / 1_000_000.0, standardMsgsPerSec,
                    highPerfDuration / 1_000_000.0, highPerfMsgsPerSec,
                    improvement);

            return Response.ok(result).build();
        });
    }

    /**
     * Pre-warms topics for better performance.
     */
    @POST
    @Path("/prewarm")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response prewarmTopics(PrewarmRequest request) {
        long startTime = System.nanoTime();

        highPerfService.preWarmTopics(request.topics());

        long duration = System.nanoTime() - startTime;

        return Response.ok()
                .entity(String.format("Pre-warmed %d topics in %.3f ms",
                        request.topics().length, duration / 1_000_000.0))
                .build();
    }

    /**
     * Gets performance statistics.
     */
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPerfStats() {
        var stats = highPerfService.getStats();

        String statsJson = String.format(
                "{\"totalMessagesSent\":%d,\"totalMessagesFailed\":%d," +
                        "\"successRate\":%.2f,\"cachedTopics\":%d," +
                        "\"detailedLoggingEnabled\":%b,\"logInterval\":%d}",
                stats.totalMessagesSent(),
                stats.totalMessagesFailed(),
                stats.getSuccessRate(),
                stats.cachedTopics(),
                stats.detailedLoggingEnabled(),
                stats.logInterval());

        return Response.ok(statsJson).build();
    }

    /**
     * Configures performance settings.
     */
    @POST
    @Path("/configure")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response configurePerformance(PerformanceConfig config) {
        highPerfService.configurePerformance(config.enableLogging(), config.logInterval());
        return Response.ok("Performance configuration updated").build();
    }

    /**
     * Load test endpoint - sends messages continuously.
     */
    @POST
    @Path("/load-test")
    @Consumes(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> loadTest(LoadTestRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            long messagesSent = 0;

            System.out.printf("🚀 Starting load test: %d msg/sec for %d seconds%n",
                    request.messagesPerSecond(), request.durationSeconds());

            try {
                long intervalMs = 1000 / request.messagesPerSecond();
                long endTime = startTime + (request.durationSeconds() * 1000L);

                while (System.currentTimeMillis() < endTime) {
                    if (highPerfService.canSendMore()) {
                        highPerfService.sendMessageFast(
                                request.topic(),
                                "Load test message " + messagesSent + " at " + System.currentTimeMillis());
                        messagesSent++;

                        if (intervalMs > 0) {
                            Thread.sleep(intervalMs);
                        }
                    } else {
                        System.out.println("⚠️ Backpressure detected, slowing down...");
                        Thread.sleep(10);
                    }
                }

                long actualDuration = System.currentTimeMillis() - startTime;
                double actualRate = messagesSent / (actualDuration / 1000.0);

                String result = String.format(
                        "Load test completed:\n" +
                                "Messages sent: %d\n" +
                                "Duration: %d ms\n" +
                                "Actual rate: %.1f msgs/sec\n" +
                                "Target rate: %d msgs/sec",
                        messagesSent, actualDuration, actualRate, request.messagesPerSecond());

                return Response.ok(result).build();

            } catch (Exception e) {
                return Response.serverError()
                        .entity("Load test failed: " + e.getMessage())
                        .build();
            }
        });
    }

    // Request DTOs
    public record MessageRequest(String topic, String message) {
    }

    public record BatchRequest(String topic, String[] messages) {
    }

    public record BenchmarkRequest(int messageCount) {
    }

    public record PrewarmRequest(String[] topics) {
    }

    public record PerformanceConfig(boolean enableLogging, int logInterval) {
    }

    public record LoadTestRequest(String topic, int messagesPerSecond, int durationSeconds) {
    }
}
