package org.signal.processor.subscribers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.signal.processor.dto.TestMessage;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Message subscriber service that handles incoming messages from various AMQP
 * topics.
 */
@ApplicationScoped
public class MessageSubscriberService {

    private static final Logger logger = LoggerFactory.getLogger(MessageSubscriberService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Inject
    ObjectMapper objectMapper;

    // Statistics tracking
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> topicCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastMessagePerTopic = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastTimestampPerTopic = new ConcurrentHashMap<>();

    /**
     * Subscribes to Jackson-serialized TestMessage objects.
     */
    @Incoming("jackson-topic-consumer")
    public void handleJacksonMessages(String rawMessage) {
        String topic = "test-jackson-topic";
        try {
            TestMessage message = objectMapper.readValue(rawMessage, TestMessage.class);

            String timestamp = LocalDateTime.now().format(FORMATTER);
            long count = incrementTopicCount(topic);

            logger.info("📨 [{}] RECEIVED Jackson message #{} from {}: {}",
                    timestamp, count, topic, message);

            processTestMessage(message, topic);
            updateStatistics(topic, rawMessage, timestamp);

        } catch (Exception e) {
            logger.error("❌ Failed to process Jackson message from {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Subscribes to confirmed delivery Jackson messages.
     */
    @Incoming("jackson-confirmed-consumer")
    public void handleJacksonConfirmedMessages(String rawMessage) {
        String topic = "test-jackson-confirmed-topic";
        try {
            TestMessage message = objectMapper.readValue(rawMessage, TestMessage.class);

            String timestamp = LocalDateTime.now().format(FORMATTER);
            long count = incrementTopicCount(topic);

            logger.info("✅ [{}] RECEIVED CONFIRMED Jackson message #{} from {}: {}",
                    timestamp, count, topic, message);

            processConfirmedMessage(message, topic);
            updateStatistics(topic, rawMessage, timestamp);

        } catch (Exception e) {
            logger.error("❌ Failed to process confirmed Jackson message from {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Subscribes to customer notifications.
     */
    @Incoming("customer-notifications-consumer")
    public void handleCustomerNotifications(String rawMessage) {
        String topic = "customer-notifications";
        try {
            TestMessage notification = objectMapper.readValue(rawMessage, TestMessage.class);

            String timestamp = LocalDateTime.now().format(FORMATTER);
            long count = incrementTopicCount(topic);

            logger.info("🔔 [{}] RECEIVED customer notification #{} from {}: {}",
                    timestamp, count, topic, notification);

            processCustomerNotification(notification, topic);
            updateStatistics(topic, rawMessage, timestamp);

        } catch (Exception e) {
            logger.error("❌ Failed to process customer notification from {}: {}", topic, e.getMessage());
        }
    }

    private void processTestMessage(TestMessage message, String topic) {
        logger.debug("Processing TestMessage - ID: {}, Priority: {}", message.getId(), message.getPriority());

        if (message.getPriority() > 5) {
            logger.warn("🚨 High priority message received: {}", message.getId());
        }

        long messageAge = System.currentTimeMillis() - message.getTimestamp();
        if (messageAge > 30000) {
            logger.warn("⏰ Message is {} ms old: {}", messageAge, message.getId());
        }
    }

    private void processConfirmedMessage(TestMessage message, String topic) {
        logger.debug("Processing confirmed message: {}", message.getId());
        logger.info("✅ Confirmed message processing completed for: {}", message.getId());
    }

    private void processCustomerNotification(TestMessage notification, String topic) {
        logger.debug("Processing customer notification: {}", notification.getId());
        logger.info("🔔 Customer notification processed: {}", notification.getId());
    }

    private long incrementTopicCount(String topic) {
        totalMessagesReceived.incrementAndGet();
        return topicCounts.computeIfAbsent(topic, k -> new AtomicLong(0)).incrementAndGet();
    }

    private void updateStatistics(String topic, String message, String timestamp) {
        lastMessagePerTopic.put(topic, message);
        lastTimestampPerTopic.put(topic, timestamp);
    }

    /**
     * Gets comprehensive statistics about all received messages.
     */
    public SubscriberStats getSubscriberStats() {
        ConcurrentHashMap<String, Long> counts = new ConcurrentHashMap<>();
        topicCounts.forEach((topic, counter) -> counts.put(topic, counter.get()));

        return new SubscriberStats(
                totalMessagesReceived.get(),
                counts,
                new ConcurrentHashMap<>(lastMessagePerTopic),
                new ConcurrentHashMap<>(lastTimestampPerTopic));
    }

    /**
     * Record containing subscriber statistics.
     */
    public record SubscriberStats(
            long totalMessagesReceived,
            ConcurrentHashMap<String, Long> messageCountsByTopic,
            ConcurrentHashMap<String, String> lastMessagePerTopic,
            ConcurrentHashMap<String, String> lastTimestampPerTopic) {
    }
}
