package org.signal.processor.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.signal.processor.amqp.DynamicAmqpMessagingService;
import org.signal.processor.dto.TestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example business service showing how to use both Jackson and Jakarta JSON
 * with the DynamicAmqpMessagingService.
 */
@ApplicationScoped
public class ExampleJsonMessagingService {

    private static final Logger logger = LoggerFactory.getLogger(ExampleJsonMessagingService.class);

    @Inject
    DynamicAmqpMessagingService messagingService;

    /**
     * Modern approach using Jackson with POJOs. Recommended for new code.
     */
    public void sendUserNotification(String userId, String message) {
        TestMessage notification = new TestMessage("user-" + userId, message, System.currentTimeMillis(), 1);

        try {
            // Fire-and-forget with JSON serialization
            messagingService.sendMessage("user-notifications", notification);
            logger.info("User notification queued for user: {}", userId);
        }
        catch (Exception e) {
            logger.error("Failed to send user notification for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Approach using Jakarta JsonObject. Use this when integrating with existing
     * Jakarta JSON code.
     */
    public void sendLegacyAlert(String alertType, String description) {
        JsonObject alert = Json.createObjectBuilder().add("alertType", alertType).add("description", description)
                .add("timestamp", System.currentTimeMillis()).add("severity", "HIGH").build();

        try {
            // With delivery confirmation
            messagingService.sendMessageWithConfirmation("system-alerts", alert).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to deliver alert {}: {}", alertType, throwable.getMessage());
                }
                else {
                    logger.info("Alert delivered successfully: {}", alertType);
                }
            });
        }
        catch (Exception e) {
            logger.error("Failed to send alert {}: {}", alertType, e.getMessage());
        }
    }

    /**
     * Synchronous delivery for critical messages.
     */
    public void sendCriticalUpdate(String updateId, Object updateData) {
        try {
            // Synchronous delivery with Jackson serialization
            messagingService.sendMessageSync("critical-updates", updateData);
            logger.info("Critical update delivered synchronously: {}", updateId);
        }
        catch (Exception e) {
            logger.error("CRITICAL: Failed to deliver update {}: {}", updateId, e.getMessage());
            throw new RuntimeException("Critical update delivery failed", e);
        }
    }

    /**
     * Batch processing example.
     */
    public void sendBatchNotifications(String[] userIds, String message) {
        for (String userId : userIds) {
            TestMessage notification = new TestMessage("batch-" + userId, message, System.currentTimeMillis(), 2);

            // Fire-and-forget for batch processing
            messagingService.sendMessage("batch-notifications", notification).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.warn("Failed to queue notification for user {}: {}", userId, throwable.getMessage());
                }
            });
        }

        logger.info("Queued {} batch notifications", userIds.length);
    }
}
