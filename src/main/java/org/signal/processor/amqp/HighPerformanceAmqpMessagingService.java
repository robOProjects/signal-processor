package org.signal.processor.amqp;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.reactive.messaging.amqp.OutgoingAmqpMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * High-performance AMQP messaging service optimized for high bandwidth
 * scenarios.
 * <p>
 * This service is designed to handle thousands of messages per second with
 * minimal overhead.
 * Key optimizations:
 * - Metadata caching to avoid object creation
 * - LongAdder for better concurrent performance than AtomicLong
 * - Conditional logging to reduce I/O overhead
 * - Pre-validated topics to skip validation on hot paths
 * </p>
 */
@ApplicationScoped
public class HighPerformanceAmqpMessagingService {

    private static final Logger logger = LoggerFactory.getLogger(HighPerformanceAmqpMessagingService.class);

    @Inject
    @Channel("dynamic-sender")
    private Emitter<String> emitter;

    // High-performance counters (better than AtomicLong for high contention)
    private final LongAdder messagesSentCount = new LongAdder();
    private final LongAdder messagesFailedCount = new LongAdder();

    // Metadata cache to avoid creating new objects for each message
    private final ConcurrentHashMap<String, Metadata> metadataCache = new ConcurrentHashMap<>();

    // Configuration for performance tuning
    private volatile boolean enableDetailedLogging = false;
    private volatile int logEveryNthMessage = 1000; // Log every 1000th message by default

    /**
     * High-performance message sending with minimal overhead.
     * Optimized for throughput over individual message tracking.
     */
    public void sendMessageFast(String topic, String message) {
        try {
            // Get cached metadata or create new one
            Metadata metadata = metadataCache.computeIfAbsent(topic,
                    t -> Metadata.of(OutgoingAmqpMetadata.builder().withAddress(t).build()));

            // Create message with cached metadata
            Message<String> amqpMessage = Message.of(message).withMetadata(metadata);

            // Send the message - fire-and-forget
            emitter.send(amqpMessage);

            // Increment counter (LongAdder is more efficient under high contention)
            long count = messagesSentCount.sumThenReset();
            messagesSentCount.increment();

            // Conditional logging to reduce I/O overhead
            if (enableDetailedLogging && (count % logEveryNthMessage == 0)) {
                logger.info("📊 Sent {} messages (current topic: {})", count, topic);
            }

        } catch (Exception e) {
            messagesFailedCount.increment();
            if (enableDetailedLogging) {
                logger.error("❌ Failed to send message to {}: {}", topic, e.getMessage());
            }
        }
    }

    /**
     * Batch message sending for maximum throughput.
     * Sends multiple messages to the same topic efficiently.
     */
    public void sendMessageBatch(String topic, String[] messages) {
        if (messages == null || messages.length == 0) {
            return;
        }

        try {
            // Get cached metadata once for the entire batch
            Metadata metadata = metadataCache.computeIfAbsent(topic,
                    t -> Metadata.of(OutgoingAmqpMetadata.builder().withAddress(t).build()));

            // Send all messages in batch
            for (String message : messages) {
                Message<String> amqpMessage = Message.of(message).withMetadata(metadata);
                emitter.send(amqpMessage);
                messagesSentCount.increment();
            }

            if (enableDetailedLogging) {
                logger.info("📦 Batch sent {} messages to topic: {}", messages.length, topic);
            }

        } catch (Exception e) {
            messagesFailedCount.add(messages.length);
            if (enableDetailedLogging) {
                logger.error("❌ Failed to send batch to {}: {}", topic, e.getMessage());
            }
        }
    }

    /**
     * Async batch sending with CompletionStage for non-blocking operations.
     */
    public CompletionStage<Void> sendMessageBatchAsync(String topic, String[] messages) {
        return CompletableFuture.runAsync(() -> sendMessageBatch(topic, messages));
    }

    /**
     * Pre-warms the metadata cache for known topics to avoid first-access overhead.
     */
    public void preWarmTopics(String... topics) {
        for (String topic : topics) {
            metadataCache.computeIfAbsent(topic,
                    t -> Metadata.of(OutgoingAmqpMetadata.builder().withAddress(t).build()));
        }
        logger.info("🔥 Pre-warmed {} topics in metadata cache", topics.length);
    }

    /**
     * Clears the metadata cache to free memory if needed.
     */
    public void clearMetadataCache() {
        int size = metadataCache.size();
        metadataCache.clear();
        logger.info("🧹 Cleared metadata cache ({} entries)", size);
    }

    /**
     * Gets high-performance statistics.
     */
    public HighPerfStats getStats() {
        return new HighPerfStats(
                messagesSentCount.sum(),
                messagesFailedCount.sum(),
                metadataCache.size(),
                enableDetailedLogging,
                logEveryNthMessage);
    }

    /**
     * Configures performance settings.
     */
    public void configurePerformance(boolean enableLogging, int logInterval) {
        this.enableDetailedLogging = enableLogging;
        this.logEveryNthMessage = logInterval;
        logger.info("⚙️ Performance configured: logging={}, interval={}",
                enableLogging, logInterval);
    }

    /**
     * Checks if the emitter can handle more messages (backpressure check).
     */
    public boolean canSendMore() {
        try {
            return emitter.hasRequests() && !emitter.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Performance statistics record.
     */
    public record HighPerfStats(
            long totalMessagesSent,
            long totalMessagesFailed,
            int cachedTopics,
            boolean detailedLoggingEnabled,
            int logInterval) {
        public double getSuccessRate() {
            long total = totalMessagesSent + totalMessagesFailed;
            return total == 0 ? 100.0 : (double) totalMessagesSent / total * 100.0;
        }
    }
}
