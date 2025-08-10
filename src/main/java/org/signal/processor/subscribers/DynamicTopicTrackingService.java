package org.signal.processor.subscribers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Dynamic topic tracking service that monitors dynamic topics created on the
 * fly.
 * <p>
 * This service works by: 1. Tracking when messages are sent to dynamic topics
 * 2. Maintaining statistics for all dynamic topic activity 3. Integrating with
 * your DynamicAmqpMessagingService 4. Processing all dynamic topics uniformly
 * </p>
 * 
 * Call trackTopicUsage() from your messaging service when sending to dynamic
 * topics.
 */
@ApplicationScoped
public class DynamicTopicTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicTopicTrackingService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Track known dynamic topics
    private final ConcurrentHashMap<String, Boolean> dynamicTopics = new ConcurrentHashMap<>();

    // Statistics tracking
    private final AtomicLong totalDynamicMessages = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> topicMessageCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastMessagePerTopic = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastSentTimestamp = new ConcurrentHashMap<>();

    /**
     * Call this method when sending a message to a dynamic topic. This tracks the
     * topic usage and enables monitoring - now reactive!
     */
    public Uni<Void> trackTopicUsage(String topicName, String message) {
        return Uni.createFrom().item(() -> {
            String timestamp = LocalDateTime.now().format(FORMATTER);

            // Track new dynamic topics
            if (!dynamicTopics.containsKey(topicName)) {
                dynamicTopics.put(topicName, true);
                logger.info("🆕 New dynamic topic discovered: {}", topicName);
            }

            // Update statistics
            long count = topicMessageCounts.computeIfAbsent(topicName, k -> new AtomicLong(0)).incrementAndGet();
            totalDynamicMessages.incrementAndGet();
            lastMessagePerTopic.put(topicName, message);
            lastSentTimestamp.put(topicName, timestamp);

            logger.debug("📊 Dynamic topic '{}' message #{}: {}", topicName, count, message);

            return null;
        }).chain(ignored -> processDynamicTopicMessage(topicName, message));
    }

    /**
     * Processes messages sent to dynamic topics reactively.
     */
    private Uni<Void> processDynamicTopicMessage(String topicName, String message) {
        return Uni.createFrom().item(() -> {
            logger.info("🔀 SENT to dynamic topic {}: {}", topicName, message);

            // Add any additional logic you want to perform when messages are sent
            return null;
        });
    }

    // === PUBLIC API METHODS ===

    /**
     * Gets all tracked dynamic topics.
     */
    public ConcurrentHashMap<String, Boolean> getTrackedTopics() {
        return new ConcurrentHashMap<>(dynamicTopics);
    }

    /**
     * Gets statistics for dynamic topic usage.
     */
    public DynamicTopicStats getDynamicTopicStats() {
        ConcurrentHashMap<String, Long> counts = new ConcurrentHashMap<>();
        topicMessageCounts.forEach((topic, counter) -> counts.put(topic, counter.get()));

        return new DynamicTopicStats(totalDynamicMessages.get(), counts, new ConcurrentHashMap<>(lastMessagePerTopic),
                new ConcurrentHashMap<>(lastSentTimestamp), new ConcurrentHashMap<>(dynamicTopics));
    }

    /**
     * Checks if a topic is being tracked.
     */
    public boolean isTopicTracked(String topicName) {
        return dynamicTopics.containsKey(topicName);
    }

    /**
     * Record containing dynamic topic statistics.
     */
    public record DynamicTopicStats(long totalDynamicMessages, ConcurrentHashMap<String, Long> topicMessageCounts,
            ConcurrentHashMap<String, String> lastMessagePerTopic, ConcurrentHashMap<String, String> lastSentTimestamp,
            ConcurrentHashMap<String, Boolean> trackedTopics) {
    }
}
