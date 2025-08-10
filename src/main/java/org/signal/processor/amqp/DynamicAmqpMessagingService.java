package org.signal.processor.amqp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.signal.processor.dto.DynamicAmqpRequest;
import org.signal.processor.dto.MessageStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.signal.processor.subscribers.DynamicTopicTrackingService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.reactive.messaging.amqp.OutgoingAmqpMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;

/**
 * Service for sending messages to dynamic AMQP topics.
 * <p>
 * This service provides a programmatic API for sending messages to dynamically
 * specified AMQP topics using the SmallRye Reactive Messaging API. It can be
 * injected into any CDI-managed class and used for asynchronous message
 * sending.
 * </p>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * {@code
 * @Inject
 * DynamicAmqpMessagingService messagingService;
 * 
 * // Send a message
 * messagingService.sendMessage("my-topic", "Hello World!")
 *         .thenAccept(result -> logger.info("Message sent successfully")).exceptionally(throwable -> {
 *             logger.error("Failed to send message: {}", throwable.getMessage());
 *             return null;
 *         });
 * }
 * </pre>
 * </p>
 */
@ApplicationScoped
public class DynamicAmqpMessagingService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicAmqpMessagingService.class);

    @Inject
    @Channel("dynamic-sender")
    /**
     * Emitter instance used to send messages of type {@code String} to an AMQP
     * messaging channel. It is not sending messages directly but rather publishing
     * them to a channel for processing. A channel is a logical stream of messages
     * that can be processed independently. The broker is responsible for routing
     * messages to the appropriate consumers based on the topic and other metadata.
     */
    private Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DynamicTopicTrackingService dynamicTopicTracker;

    // Message tracking
    private final AtomicLong messagesSentCount = new AtomicLong(0);
    private final AtomicLong messagesFailedCount = new AtomicLong(0);
    private volatile String lastSuccessfulSend = "Never";
    private volatile String lastFailure = "None";

    // Cache metadata objects to avoid repeated creation
    private final ConcurrentHashMap<String, Metadata> metadataCache = new ConcurrentHashMap<>();

    // Thread-local formatter to avoid synchronization overhead
    private static final ThreadLocal<DateTimeFormatter> FORMATTER = ThreadLocal
            .withInitial(() -> DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    /**
     * Gets statistics about message sending.
     */
    public MessageStats getMessageStats() {
        return new MessageStats(messagesSentCount.get(), messagesFailedCount.get(), lastSuccessfulSend, lastFailure,
                metadataCache.size());
    }

    /**
     * Pre-warms the metadata cache for frequently used topics. Call this method
     * with your common topics to improve performance.
     */
    public void preWarmTopics(String... topics) {
        for (String topic : topics) {
            metadataCache.computeIfAbsent(topic,
                    t -> Metadata.of(OutgoingAmqpMetadata.builder().withAddress(t).withDurable(true).build()));
        }
        logger.info("🔥 Pre-warmed {} topics in cache", topics.length);
    }

    /**
     * Checks if the emitter is ready to send messages. Note: This doesn't guarantee
     * the broker connection is healthy, but indicates if the emitter is in a failed
     * state.
     */
    public boolean isEmitterReady() {
        try {
            return !emitter.isCancelled() && !emitter.hasRequests();
        }
        catch (Exception e) {
            logger.warn("Error checking emitter status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Performs a simple health check by attempting to send a test message. This is
     * the most reliable way to verify end-to-end connectivity.
     */
    public CompletionStage<Boolean> performHealthCheck() {
        String testTopic = "health-check-" + System.currentTimeMillis();
        String testMessage = "Health check at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return sendMessage(testTopic, testMessage).thenApply(result -> {
            logger.info("Health check successful - AMQP connection is working");
            return true;
        }).exceptionally(throwable -> {
            logger.error("Health check failed: {}", throwable.getMessage());
            return false;
        });
    }

    /**
     * Sends a message to the specified AMQP topic with acknowledgment handling.
     * <p>
     * IMPORTANT: The emitter.send() is fire-and-forget. This method returns
     * immediately after queuing the message, NOT after successful broker delivery.
     * For real delivery confirmation, use sendMessageWithConfirmation() instead.
     * </p>
     * <p>
     * DURABILITY: Dynamic topics are automatically created as DURABLE, meaning they
     * will survive broker restarts and messages will be persisted to disk.
     * </p>
     * 
     * @param topic   the name of the topic to send the message to
     * @param message the message content to send
     * @return a CompletionStage that completes when the message is QUEUED (not
     *         delivered)
     * @throws IllegalArgumentException if topic or message is null or empty
     */
    public CompletionStage<Void> sendMessage(String topic, String message) {
        if (topic == null || topic.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Topic cannot be null or empty"));
        }
        if (message == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message cannot be null"));
        }

        // Chain reactive operations: track first, then send
        return dynamicTopicTracker.trackTopicUsage(topic, message).subscribeAsCompletionStage().thenCompose(ignored -> {
            try {
                // Get cached metadata or create new one (performance optimization)
                Metadata metadata = metadataCache.computeIfAbsent(topic,
                        t -> Metadata.of(OutgoingAmqpMetadata.builder().withAddress(t).withDurable(true).build()));

                // Create message with cached metadata
                Message<String> amqpMessage = Message.of(message).withMetadata(metadata);

                // Send the message - emitter.send() is fire-and-forget
                emitter.send(amqpMessage);

                // Update tracking
                long count = messagesSentCount.incrementAndGet();
                String timestamp = LocalDateTime.now().format(FORMATTER.get());
                lastSuccessfulSend = timestamp;

                // Debug logging (controlled by logging framework configuration)
                logger.debug("[{}] Message QUEUED for topic: {} (Total queued: {}) - DELIVERY NOT CONFIRMED", timestamp,
                        topic, count);

                // WARNING: This only means the message was queued, not delivered!
                return CompletableFuture.completedFuture(null);
            }
            catch (Exception e) {
                String timestamp = LocalDateTime.now().format(FORMATTER.get());
                messagesFailedCount.incrementAndGet();
                lastFailure = timestamp + " - " + e.getMessage();

                // Error messages should always be logged, regardless of enableVerboseLogging
                logger.error("[{}] Failed to queue message: {} (Total failed: {})", timestamp, e.getMessage(),
                        messagesFailedCount.get());
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    /**
     * Sends a Jackson-serializable object as JSON to the specified topic
     * (fire-and-forget).
     * <p>
     * This method converts the object to JSON using Jackson ObjectMapper and then
     * delegates to the string-based sendMessage method.
     * </p>
     * 
     * @param <T>    the type of object to serialize
     * @param topic  the name of the topic to send the message to
     * @param object the object to serialize and send as JSON
     * @return a CompletionStage that completes when the message is QUEUED (not
     *         delivered)
     * @throws IllegalArgumentException if topic is null/empty or object is null
     * @throws RuntimeException         if JSON serialization fails
     */
    public <T> CompletionStage<Void> sendMessage(String topic, T object) {
        if (object == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Object cannot be null"));
        }

        try {
            String jsonMessage = objectMapper.writeValueAsString(object);
            return sendMessage(topic, jsonMessage);
        }
        catch (JsonProcessingException e) {
            logger.error("Failed to serialize object to JSON for topic: {}", topic, e);
            return CompletableFuture.failedFuture(new RuntimeException("JSON serialization failed", e));
        }
    }

    /**
     * Sends a Jakarta JsonObject to the specified topic (fire-and-forget).
     * <p>
     * This method converts the Jakarta JsonObject to string and delegates to the
     * string-based sendMessage method. Supports code using Jakarta JSON.
     * </p>
     * 
     * @param topic      the name of the topic to send the message to
     * @param jsonObject the Jakarta JsonObject to send
     * @return a CompletionStage that completes when the message is QUEUED (not
     *         delivered)
     * @throws IllegalArgumentException if topic is null/empty or jsonObject is null
     */
    public CompletionStage<Void> sendMessage(String topic, JsonObject jsonObject) {
        if (jsonObject == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("JsonObject cannot be null"));
        }

        return sendMessage(topic, jsonObject.toString());
    }

    /**
     * Sends a message with acknowledgment tracking using Message acknowledgment.
     * This provides better feedback about delivery status. Uses cached metadata for
     * better performance.
     */
    public CompletionStage<Void> sendMessageWithConfirmation(String topic, String message) {
        if (topic == null || topic.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Topic cannot be null or empty"));
        }
        if (message == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message cannot be null"));
        }

        try {
            String timestamp = LocalDateTime.now().format(FORMATTER.get());
            logger.debug("[{}] Sending message with confirmation to topic: {}", timestamp, topic);

            CompletableFuture<Void> deliveryFuture = new CompletableFuture<>();

            // Get cached metadata or create new one
            Metadata baseMetadata = metadataCache.computeIfAbsent(topic,
                    t -> Metadata.of(OutgoingAmqpMetadata.builder().withAddress(t).withDurable(true).build()));

            // Create message with acknowledgment handling
            Message<String> amqpMessage = Message.of(message).withMetadata(baseMetadata).withAck(() -> {
                logger.debug("✅ Message CONFIRMED delivered to topic: {}", topic);
                deliveryFuture.complete(null);
                return CompletableFuture.completedFuture(null);
            })
                    // BE CAREFUL: we have seen this cause message broker issues but it is the
                    // proper way to handle acknowledgments
                    .withNack(reason -> {
                        String error = "Message delivery FAILED for topic: " + topic + ", reason: "
                                + reason.getMessage();
                        // Error messages should always be logged
                        logger.error("❌ {}", error);
                        deliveryFuture.completeExceptionally(new RuntimeException(error, reason));
                        return CompletableFuture.completedFuture(null);
                    });

            emitter.send(amqpMessage);

            // Set a timeout for the delivery confirmation
            CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> {
                if (!deliveryFuture.isDone()) {
                    deliveryFuture.completeExceptionally(
                            new RuntimeException("Message delivery timeout for topic: " + topic));
                }
            });

            return deliveryFuture;
        }
        catch (Exception e) {
            String timestamp = LocalDateTime.now().format(FORMATTER.get());
            messagesFailedCount.incrementAndGet();
            lastFailure = timestamp + " - " + e.getMessage();

            // Error messages should always be logged
            logger.error("[{}] Failed to send message with confirmation: {}", timestamp, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Sends a Jackson-serializable object as JSON with delivery confirmation.
     * <p>
     * This method converts the object to JSON using Jackson ObjectMapper and then
     * delegates to the string-based sendMessageWithConfirmation method.
     * </p>
     * 
     * @param <T>    the type of object to serialize
     * @param topic  the name of the topic to send the message to
     * @param object the object to serialize and send as JSON
     * @return a CompletionStage that completes when delivery is confirmed
     * @throws IllegalArgumentException if topic is null/empty or object is null
     * @throws RuntimeException         if JSON serialization fails
     */
    public <T> CompletionStage<Void> sendMessageWithConfirmation(String topic, T object) {
        if (object == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Object cannot be null"));
        }

        try {
            String jsonMessage = objectMapper.writeValueAsString(object);
            return sendMessageWithConfirmation(topic, jsonMessage);
        }
        catch (JsonProcessingException e) {
            logger.error("Failed to serialize object to JSON for topic: {}", topic, e);
            return CompletableFuture.failedFuture(new RuntimeException("JSON serialization failed", e));
        }
    }

    /**
     * Sends a Jakarta JsonObject with delivery confirmation.
     * <p>
     * This method converts the Jakarta JsonObject to string and delegates to the
     * string-based sendMessageWithConfirmation method. Supports legacy code using
     * Jakarta JSON.
     * </p>
     * 
     * @param topic      the name of the topic to send the message to
     * @param jsonObject the Jakarta JsonObject to send
     * @return a CompletionStage that completes when delivery is confirmed
     * @throws IllegalArgumentException if topic is null/empty or jsonObject is null
     */
    public CompletionStage<Void> sendMessageWithConfirmation(String topic, JsonObject jsonObject) {
        if (jsonObject == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("JsonObject cannot be null"));
        }

        return sendMessageWithConfirmation(topic, jsonObject.toString());
    }

    /**
     * Sends a message to the specified AMQP topic synchronously.
     * <p>
     * IMPORTANT: This method uses sendMessageWithConfirmation() internally to wait
     * for actual delivery confirmation, not just queuing. Use
     * {@link #sendMessage(String, String)} for fire-and-forget behavior.
     * </p>
     *
     * @param topic   the name of the topic to send the message to
     * @param message the message content to send
     * @throws RuntimeException if the message cannot be sent or delivery fails
     */
    public void sendMessageSync(String topic, String message) {
        try {
            // Use the confirmation method to get real delivery feedback
            sendMessageWithConfirmation(topic, message).toCompletableFuture().join();
            logger.debug("Message synchronously delivered to topic: {}", topic);
        }
        catch (Exception e) {
            logger.error("Failed to send message synchronously to topic {}: {}", topic, e.getMessage());
            throw new RuntimeException("Failed to send message synchronously to topic: " + topic, e);
        }
    }

    /**
     * Sends a Jackson-serializable object as JSON synchronously with delivery
     * confirmation.
     * <p>
     * This method converts the object to JSON and waits for delivery confirmation.
     * </p>
     * 
     * @param <T>    the type of object to serialize
     * @param topic  the name of the topic to send the message to
     * @param object the object to serialize and send as JSON
     * @throws IllegalArgumentException if topic is null/empty or object is null
     * @throws RuntimeException         if JSON serialization fails or delivery
     *                                  fails
     */
    public <T> void sendMessageSync(String topic, T object) {
        if (object == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }

        try {
            String jsonMessage = objectMapper.writeValueAsString(object);
            sendMessageSync(topic, jsonMessage);
        }
        catch (JsonProcessingException e) {
            logger.error("Failed to serialize object to JSON for topic: {}", topic, e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * Sends a Jakarta JsonObject synchronously with delivery confirmation.
     * <p>
     * This method converts the Jakarta JsonObject to string and waits for delivery
     * confirmation. Supports legacy code using Jakarta JSON.
     * </p>
     * 
     * @param topic      the name of the topic to send the message to
     * @param jsonObject the Jakarta JsonObject to send
     * @throws IllegalArgumentException if topic is null/empty or jsonObject is null
     * @throws RuntimeException         if delivery fails
     */
    public void sendMessageSync(String topic, JsonObject jsonObject) {
        if (jsonObject == null) {
            throw new IllegalArgumentException("JsonObject cannot be null");
        }

        sendMessageSync(topic, jsonObject.toString());
    }

    /**
     * Sends a message to the specified AMQP topic synchronously with
     * fire-and-forget behavior.
     * <p>
     * This method only waits for the message to be queued locally, not for broker
     * delivery. Use this when you want synchronous API but don't need delivery
     * confirmation.
     * </p>
     *
     * @param topic   the name of the topic to send the message to
     * @param message the message content to send
     * @throws RuntimeException if the message cannot be queued
     */
    public void sendMessageSyncFireAndForget(String topic, String message) {
        try {
            // This completes immediately after queuing
            sendMessage(topic, message).toCompletableFuture().join();
            logger.debug("Message queued for topic: {}", topic);
        }
        catch (Exception e) {
            logger.error("Failed to queue message for topic {}: {}", topic, e.getMessage());
            throw new RuntimeException("Failed to queue message for topic: " + topic, e);
        }
    }

    /**
     * Sends a Jackson-serializable object as JSON synchronously (fire-and-forget).
     * <p>
     * This method converts the object to JSON and waits only for local queuing, not
     * for broker delivery confirmation.
     * </p>
     * 
     * @param <T>    the type of object to serialize
     * @param topic  the name of the topic to send the message to
     * @param object the object to serialize and send as JSON
     * @throws IllegalArgumentException if topic is null/empty or object is null
     * @throws RuntimeException         if JSON serialization fails or queuing fails
     */
    public <T> void sendMessageSyncFireAndForget(String topic, T object) {
        if (object == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }

        try {
            String jsonMessage = objectMapper.writeValueAsString(object);
            sendMessageSyncFireAndForget(topic, jsonMessage);
        }
        catch (JsonProcessingException e) {
            logger.error("Failed to serialize object to JSON for topic: {}", topic, e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * Sends a Jakarta JsonObject synchronously (fire-and-forget).
     * <p>
     * This method converts the Jakarta JsonObject to string and waits only for
     * local queuing, not for broker delivery confirmation. Supports legacy code
     * using Jakarta JSON.
     * </p>
     * 
     * @param topic      the name of the topic to send the message to
     * @param jsonObject the Jakarta JsonObject to send
     * @throws IllegalArgumentException if topic is null/empty or jsonObject is null
     * @throws RuntimeException         if queuing fails
     */
    public void sendMessageSyncFireAndForget(String topic, JsonObject jsonObject) {
        if (jsonObject == null) {
            throw new IllegalArgumentException("JsonObject cannot be null");
        }

        sendMessageSyncFireAndForget(topic, jsonObject.toString());
    }

    /**
     * Sends a message using a request object.
     * <p>
     * Convenience method that accepts a {@link DynamicAmqpRequest} object. Uses
     * fire-and-forget behavior (returns immediately after queuing).
     * </p>
     *
     * @param request the request containing topic and message
     * @return a CompletionStage that completes when the message is QUEUED
     */
    public CompletionStage<Void> sendMessage(DynamicAmqpRequest request) {
        return sendMessage(request.topic(), request.message());
    }

    /**
     * Gets a summary of all available sending methods and their behaviors. Useful
     * for understanding which method to use in different scenarios.
     */
    public String getSendingMethodsSummary() {
        return """
                📨 AMQP Message Sending Methods Available:

                1. sendMessage(topic, message) - FIRE-AND-FORGET
                   ✅ Returns immediately after queuing
                   ❌ No delivery confirmation
                   🎯 Use for: High throughput, non-critical messages

                2. sendMessageWithConfirmation(topic, message) - WITH DELIVERY CONFIRMATION
                   ⏳ Waits for broker acknowledgment (up to 30s timeout)
                   ✅ Real delivery confirmation
                   🎯 Use for: Critical messages, when you need delivery guarantee

                3. sendMessageSync(topic, message) - SYNCHRONOUS WITH CONFIRMATION
                   ⏳ Blocks until delivery is confirmed
                   ✅ Real delivery confirmation
                   🎯 Use for: Traditional synchronous APIs, critical messages

                4. sendMessageSyncFireAndForget(topic, message) - SYNCHRONOUS QUEUING ONLY
                   ⏳ Blocks until queued (immediate)
                   ❌ No delivery confirmation
                   🎯 Use for: Synchronous APIs where queuing is sufficient

                5. sendMessageBatch(topic, messages[]) - BATCH FIRE-AND-FORGET
                   ✅ Efficient for multiple messages to same topic
                   ❌ No delivery confirmation
                   🎯 Use for: Bulk operations, high throughput
                """;
    }

    /**
     * Sends multiple messages to the same topic efficiently. Uses cached metadata
     * for better performance.
     * 
     * @param topic    the target topic
     * @param messages array of messages to send
     * @return CompletionStage that completes when all messages are queued
     */
    public CompletionStage<Void> sendMessageBatch(String topic, String[] messages) {
        if (topic == null || topic.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Topic cannot be null or empty"));
        }
        if (messages == null || messages.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            // Get cached metadata once for the entire batch
            Metadata metadata = metadataCache.computeIfAbsent(topic,
                    t -> Metadata.of(OutgoingAmqpMetadata.builder().withAddress(t).withDurable(true).build()));

            // Send all messages in batch
            for (String message : messages) {
                if (message != null) {
                    Message<String> amqpMessage = Message.of(message).withMetadata(metadata);
                    emitter.send(amqpMessage);
                    messagesSentCount.incrementAndGet();
                }
            }

            String timestamp = LocalDateTime.now().format(FORMATTER.get());
            lastSuccessfulSend = timestamp;

            logger.debug("[{}] Batch sent {} messages to topic: {} (Total: {})", timestamp, messages.length, topic,
                    messagesSentCount.get());

            return CompletableFuture.completedFuture(null);

        }
        catch (Exception e) {
            String timestamp = LocalDateTime.now().format(FORMATTER.get());
            messagesFailedCount.addAndGet(messages.length);
            lastFailure = timestamp + " - " + e.getMessage();

            // Error messages should always be logged
            logger.error("[{}] Failed to send batch: {}", timestamp, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Clears the metadata cache to free memory if needed. Useful for long-running
     * applications with many dynamic topics.
     */
    public void clearMetadataCache() {
        int size = metadataCache.size();
        metadataCache.clear();
        logger.info("🧹 Cleared metadata cache ({} entries)", size);
    }
}
