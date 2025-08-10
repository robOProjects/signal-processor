package org.signal.processor.services;

import java.util.concurrent.CompletionStage;

import org.signal.processor.amqp.DynamicAmqpMessagingService;
import org.signal.processor.dto.DynamicAmqpRequest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Example service showing how to inject and use the DynamicAmqpMessagingService
 * in your own business logic classes.
 * <p>
 * This demonstrates the various ways you can use the messaging service: -
 * Asynchronous sending with CompletionStage - Synchronous sending - Using
 * request objects
 * </p>
 */
@ApplicationScoped
public class ExampleBusinessService {

    @Inject
    private DynamicAmqpMessagingService messagingService;

    /**
     * Example non-blocking method showing asynchronous message sending.
     */
    public void processOrderAsync(String orderId, String customerDetails) {
        String topic = "order-processing";
        String message = String.format("Processing order %s for customer: %s", orderId, customerDetails);

        messagingService.sendMessage(topic, message).thenAccept(result -> {
            System.out.println("Order processing message sent successfully for order: " + orderId);
            // Continue with other processing...
        }).exceptionally(throwable -> {
            System.err.println("Failed to send order processing message: " + throwable.getMessage());
            // Handle the error - maybe retry, log, or alert
            return null;
        });
    }

    /**
     * Example method showing synchronous message sending.
     */
    public void processOrderSync(String orderId, String customerDetails) {
        String topic = "order-processing";
        String message = String.format("Processing order %s for customer: %s", orderId, customerDetails);

        try {
            messagingService.sendMessageSync(topic, message);
            System.out.println("Order processing message sent successfully for order: " + orderId);
            // Continue with other processing...
        }
        catch (Exception e) {
            System.err.println("Failed to send order processing message: " + e.getMessage());
            // Handle the error
            throw new RuntimeException("Order processing failed", e);
        }
    }

    /**
     * Example method showing how to use request objects.
     */
    public CompletionStage<Void> sendNotification(String userId, String notificationText) {
        var request = new DynamicAmqpRequest("user-notifications-" + userId, notificationText);

        return messagingService.sendMessage(request);
    }

    /**
     * Example of sending multiple messages to different topics.
     */
    public void broadcastEvent(String eventType, String eventData) {
        // Send to different topics based on event type
        String[] topics = { "audit-log", "event-stream-" + eventType, "analytics-events" };

        for (String topic : topics) {
            messagingService.sendMessage(topic, eventData)
                    .thenAccept(result -> System.out.println("Event sent to " + topic)).exceptionally(throwable -> {
                        System.err.println("Failed to send event to " + topic + ": " + throwable.getMessage());
                        return null;
                    });
        }
    }

    /**
     * Example of conditional message sending based on business logic.
     */
    public void handleUserAction(String userId, String action, String details) {
        // Different topics based on action type
        String topic = switch (action.toLowerCase()) {
        case "login" -> "user-logins";
        case "purchase" -> "user-purchases";
        case "support" -> "support-requests";
        default -> "user-activities";
        };

        String message = String.format("User %s performed action: %s - %s", userId, action, details);

        messagingService.sendMessage(topic, message)
                .thenAccept(result -> System.out.println("User action logged to " + topic)).exceptionally(throwable -> {
                    System.err.println("Failed to log user action: " + throwable.getMessage());
                    return null;
                });
    }
}
