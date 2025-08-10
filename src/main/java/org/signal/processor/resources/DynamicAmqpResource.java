package org.signal.processor.resources;

import java.util.concurrent.CompletionStage;

import org.signal.processor.amqp.DynamicAmqpMessagingService;
import org.signal.processor.dto.BatchRequest;
import org.signal.processor.dto.DynamicAmqpRequest;
import org.signal.processor.dto.PrewarmRequest;
import org.signal.processor.dto.TestMessage;
import org.signal.processor.utils.TestMessageConsumer;
import org.signal.processor.subscribers.MessageSubscriberService;
import org.signal.processor.subscribers.DynamicTopicTrackingService;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for dynamic AMQP messaging operations.
 * <p>
 * Provides endpoints for sending messages to dynamically specified AMQP topics,
 * performing health checks, retrieving messaging and consumer statistics,
 * pre-warming topics, and sending message batches.
 * </p>
 * <ul>
 * <li><b>/amqp-dynamic/test</b>: Simple test endpoint to verify service
 * availability.</li>
 * <li><b>/amqp-dynamic/health</b>: Checks AMQP connectivity and returns health
 * status.</li>
 * <li><b>/amqp-dynamic/stats</b>: Returns statistics about sent messages and
 * emitter state.</li>
 * <li><b>/amqp-dynamic/consumer-stats</b>: Returns statistics about received
 * messages.</li>
 * <li><b>/amqp-dynamic/prewarm-topics</b>: Pre-warms AMQP topics for
 * performance optimization.</li>
 * <li><b>/amqp-dynamic/send</b>: Sends a message to a specified topic
 * (fire-and-forget).</li>
 * <li><b>/amqp-dynamic/send-confirmed</b>: Sends a message and waits for
 * delivery confirmation.</li>
 * <li><b>/amqp-dynamic/send-batch</b>: Sends a batch of messages to a specified
 * topic.</li>
 * </ul>
 * <p>
 * Uses {@link DynamicAmqpMessagingService} for messaging operations and
 * {@link TestMessageConsumer} for consumer statistics.
 * </p>
 */
@Path("/amqp-dynamic")
public class DynamicAmqpResource {

    @Inject
    private DynamicAmqpMessagingService messagingService;

    @Inject
    private TestMessageConsumer testConsumer;

    @Inject
    private MessageSubscriberService subscriberService;

    @Inject
    private DynamicTopicTrackingService dynamicTopicTracker;

    /**
     * Test endpoint to verify that the service is operational.
     *
     * @return a Response indicating the endpoint is working
     */
    @GET
    @Path("/test")
    public Response test() {
        return Response.ok("Endpoint is working!").build();
    }

    /**
     * Health check endpoint that tests actual AMQP connectivity.
     *
     * @return a Response indicating AMQP health status
     */
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> healthCheck() {
        return messagingService.performHealthCheck().thenApply(isHealthy -> {
            if (isHealthy) {
                return Response.ok().entity("{\"status\":\"UP\",\"amqp\":\"connected\"}").build();
            }
            else {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity("{\"status\":\"DOWN\",\"amqp\":\"disconnected\"}").build();
            }
        });
    }

    /**
     * Statistics endpoint showing message sending metrics.
     *
     * @return a Response with message statistics
     */
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStats() {
        var stats = messagingService.getMessageStats();
        boolean emitterReady = messagingService.isEmitterReady();

        String statsJson = String.format("{\"messagesSent\":%d,\"messagesFailed\":%d,\"successRate\":%.2f,"
                + "\"lastSuccessfulSend\":\"%s\",\"lastFailure\":\"%s\",\"emitterReady\":%b," + "\"cachedTopics\":%d}",
                stats.messagesSent(), stats.messagesFailed(), stats.getSuccessRate(), stats.lastSuccessfulSend(),
                stats.lastFailure(), emitterReady, stats.cachedTopics());

        return Response.ok(statsJson).build();
    }

    /**
     * Pre-warms topics for better performance.
     */
    @POST
    @Path("/prewarm-topics")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response prewarmTopics(PrewarmRequest request) {
        messagingService.preWarmTopics(request.topics());
        return Response.ok("Topics pre-warmed successfully").build();
    }

    /**
     * Sends multiple messages to the same topic efficiently.
     */
    @POST
    @Path("/send-batch")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sendBatch(BatchRequest request) {
        try {
            messagingService.sendMessageBatch(request.topic(), request.messages()).toCompletableFuture().join();
            return Response.accepted().entity(
                    String.format("Batch of %d messages sent to topic: %s", request.messages().length, request.topic()))
                    .build();
        }
        catch (Exception e) {
            System.err.println("Failed to send batch: " + e.getMessage());
            return Response.serverError().entity("Failed to send batch: " + e.getMessage()).build();
        }
    }

    /**
     * Consumer statistics endpoint showing received message metrics.
     *
     * @return a Response with consumer statistics
     */
    @GET
    @Path("/consumer-stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConsumerStats() {
        var stats = testConsumer.getConsumerStats();

        String statsJson = String.format(
                "{\"messagesReceived\":%d,\"lastMessageReceived\":\"%s\","
                        + "\"lastMessageTimestamp\":\"%s\",\"topicCounts\":%s}",
                stats.messagesReceived(), stats.lastMessageReceived().replace("\"", "\\\""),
                stats.lastMessageTimestamp(), stats.topicCounts().toString());

        return Response.ok(statsJson).build();
    }

    /**
     * Comprehensive subscriber statistics endpoint showing all received message
     * metrics. This includes all topics being monitored by the
     * MessageSubscriberService.
     *
     * @return a Response with comprehensive subscriber statistics
     */
    @GET
    @Path("/subscriber-stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSubscriberStats() {
        var stats = subscriberService.getSubscriberStats();

        // Build a comprehensive JSON response
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"totalMessagesReceived\":").append(stats.totalMessagesReceived()).append(",");
        json.append("\"messageCountsByTopic\":{");

        boolean first = true;
        for (var entry : stats.messageCountsByTopic().entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("},");

        json.append("\"lastMessagePerTopic\":{");
        first = true;
        for (var entry : stats.lastMessagePerTopic().entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue().replace("\"", "\\\""))
                    .append("\"");
            first = false;
        }
        json.append("},");

        json.append("\"lastTimestampPerTopic\":{");
        first = true;
        for (var entry : stats.lastTimestampPerTopic().entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("}");
        json.append("}");

        return Response.ok(json.toString()).build();
    }

    /**
     * Dynamic topic statistics endpoint showing usage of topics created on the fly.
     * This tracks messages sent to any topic name dynamically.
     *
     * @return a Response with dynamic topic statistics
     */
    @GET
    @Path("/dynamic-topic-stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDynamicTopicStats() {
        var stats = dynamicTopicTracker.getDynamicTopicStats();

        // Build a comprehensive JSON response for dynamic topics
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"totalDynamicMessages\":").append(stats.totalDynamicMessages()).append(",");

        json.append("\"topicMessageCounts\":{");
        boolean first = true;
        for (var entry : stats.topicMessageCounts().entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("},");

        json.append("\"lastMessagePerTopic\":{");
        first = true;
        for (var entry : stats.lastMessagePerTopic().entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue().replace("\"", "\\\""))
                    .append("\"");
            first = false;
        }
        json.append("},");

        json.append("\"lastSentTimestamp\":{");
        first = true;
        for (var entry : stats.lastSentTimestamp().entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("},");

        json.append("\"trackedTopics\":{");
        first = true;
        for (var entry : stats.trackedTopics().entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        json.append("}");

        return Response.ok(json.toString()).build();
    }

    /**
     * Handles HTTP POST requests to send a message to a dynamically specified AMQP
     * topic.
     * <p>
     * Expects a {@link DynamicAmqpRequest} containing the target topic and message
     * payload. The message is sent to the specified topic using the injected
     * messaging service.
     * </p>
     *
     * @param request the request containing the topic and message to send
     * @return a {@link Response} indicating whether the message was accepted or if
     *         an error occurred
     */
    @POST
    @Path("/send")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sendToDynamicTopic(DynamicAmqpRequest request) {
        try {
            messagingService.sendMessageSync(request.topic(), request.message());
            return Response.accepted()
                    .entity("Message QUEUED to topic: " + request.topic() + " (delivery not confirmed)").build();
        }
        catch (Exception e) {
            System.err.println("Failed to send message: " + e.getMessage());
            e.printStackTrace();
            return Response.serverError().entity("Failed to send message: " + e.getMessage()).build();
        }
    }

    /**
     * Sends a message with delivery confirmation. This endpoint waits for actual
     * delivery confirmation from the broker.
     */
    @POST
    @Path("/send-confirmed")
    @Consumes(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> sendToDynamicTopicConfirmed(DynamicAmqpRequest request) {
        return messagingService.sendMessageWithConfirmation(request.topic(), request.message()).thenApply(
                result -> Response.ok().entity("Message CONFIRMED delivered to topic: " + request.topic()).build())
                .exceptionally(throwable -> {
                    System.err.println("Failed to deliver message: " + throwable.getMessage());
                    return Response.serverError().entity("Failed to deliver message: " + throwable.getMessage())
                            .build();
                });
    }

    // JSON Testing Endpoints

    /**
     * Tests sending a Jackson-serializable object as JSON.
     */
    @POST
    @Path("/send-jackson")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> sendJacksonObject(TestMessage message) {
        return messagingService.sendMessage("test-jackson-topic", message).thenApply(
                unused -> Response.ok().entity("Jackson message sent successfully: " + message.toString()).build())
                .exceptionally(throwable -> Response.serverError()
                        .entity("Failed to send Jackson message: " + throwable.getMessage()).build());
    }

    /**
     * Tests sending a Jakarta JsonObject (legacy support).
     */
    @POST
    @Path("/send-jakarta-json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendJakartaJsonObject() {
        JsonObject jsonObject = Json.createObjectBuilder().add("id", "jakarta-test-" + System.currentTimeMillis())
                .add("content", "This is a Jakarta JsonObject test message")
                .add("timestamp", System.currentTimeMillis()).add("priority", 1).build();

        try {
            messagingService.sendMessage("test-jakarta-topic", jsonObject);
            return Response.ok().entity("Jakarta JsonObject sent successfully: " + jsonObject.toString()).build();
        }
        catch (Exception e) {
            return Response.serverError().entity("Failed to send Jakarta JsonObject: " + e.getMessage()).build();
        }
    }

    /**
     * Tests sending with delivery confirmation using Jackson.
     */
    @POST
    @Path("/send-jackson-confirmed")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> sendJacksonWithConfirmation(TestMessage message) {
        return messagingService.sendMessageWithConfirmation("test-jackson-confirmed-topic", message).thenApply(
                unused -> Response.ok().entity("Jackson message confirmed delivered: " + message.toString()).build())
                .exceptionally(throwable -> Response.serverError()
                        .entity("Failed to deliver Jackson message: " + throwable.getMessage()).build());
    }

    /**
     * Tests synchronous sending with Jackson.
     */
    @POST
    @Path("/send-jackson-sync")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendJacksonSync(TestMessage message) {
        try {
            messagingService.sendMessageSync("test-jackson-sync-topic", message);
            return Response.ok().entity("Jackson message synchronously delivered: " + message.toString()).build();
        }
        catch (Exception e) {
            return Response.serverError().entity("Failed to synchronously send Jackson message: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Demo endpoint that shows different JSON approaches and logging configuration.
     */
    @GET
    @Path("/json-examples")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJsonExamples() {
        String examples = """
                Available JSON endpoints:

                1. POST /amqp-dynamic/send-jackson
                   - Send Jackson object (fire-and-forget)
                   - Body: {"id": "test", "content": "Hello", "timestamp": 123456, "priority": 1}

                2. POST /amqp-dynamic/send-jakarta-json
                   - Send Jakarta JsonObject (legacy support)
                   - No body required - creates test object

                3. POST /amqp-dynamic/send-jackson-confirmed
                   - Send Jackson object with delivery confirmation
                   - Body: {"id": "test", "content": "Hello", "timestamp": 123456, "priority": 1}

                4. POST /amqp-dynamic/send-jackson-sync
                   - Send Jackson object synchronously
                   - Body: {"id": "test", "content": "Hello", "timestamp": 123456, "priority": 1}

                All methods support both String and JSON payloads for maximum flexibility.

                DEBUG LOGGING CONFIGURATION:
                To see debug messages, set in application.properties:
                quarkus.log.category."org.signal.processor.amqp.DynamicAmqpMessagingService".level=DEBUG
                """;

        return Response.ok().entity(examples).build();
    }
}
