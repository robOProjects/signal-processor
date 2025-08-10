package org.signal.processor.utils;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple message consumer for verification and testing purposes.
 * <p>
 * This consumer listens to a test topic and logs received messages, providing
 * concrete evidence that the AMQP messaging is working end-to-end.
 * </p>
 * <p>
 * Note: This is primarily for testing. In production, you would have dedicated
 * consumers for specific business logic.
 * </p>
 */
@ApplicationScoped
public class TestMessageConsumer {

    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> topicCounts = new ConcurrentHashMap<>();
    private volatile String lastMessageReceived = "None";
    private volatile String lastMessageTimestamp = "Never";

    /**
     * Consumes messages from the test-topic for verification. This proves that
     * messages are successfully reaching the broker.
     */
    @Incoming("test-consumer")
    public void consumeTestMessage(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long count = messagesReceived.incrementAndGet();

        lastMessageReceived = message;
        lastMessageTimestamp = timestamp;

        System.out.println("✅ [" + timestamp + "] RECEIVED MESSAGE #" + count + ": " + message);

        // Track messages per topic (if possible to extract from message)
        // For now, just increment a general counter
        topicCounts.merge("test-topic", 1L, Long::sum);
    }

    /**
     * Gets statistics about received messages.
     */
    public ConsumerStats getConsumerStats() {
        return new ConsumerStats(messagesReceived.get(), lastMessageReceived, lastMessageTimestamp,
                new ConcurrentHashMap<>(topicCounts));
    }

    /**
     * Record representing consumer statistics.
     */
    public record ConsumerStats(long messagesReceived, String lastMessageReceived, String lastMessageTimestamp,
            ConcurrentHashMap<String, Long> topicCounts) {
    }
}
