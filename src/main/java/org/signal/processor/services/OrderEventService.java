package org.signal.processor.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.signal.processor.amqp.DynamicAmqpMessagingService;
import org.signal.processor.dto.OrderEvent;
import org.signal.processor.dto.TestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Real-world example showing how to use Jackson with
 * DynamicAmqpMessagingService.
 */
@ApplicationScoped
public class OrderEventService {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventService.class);

    @Inject
    DynamicAmqpMessagingService messagingService;

    /**
     * Fire-and-forget order event (high throughput).
     */
    public CompletionStage<Void> publishOrderEvent(String orderId, String customerId, BigDecimal amount,
            OrderEvent.OrderEventType eventType) {
        // Create strongly-typed event object
        OrderEvent event = new OrderEvent(orderId, customerId, amount, eventType);

        // Jackson automatically serializes to JSON - type-safe!
        return messagingService.sendMessage("order-events", event) // T = OrderEvent
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to publish order event {}: {}", orderId, throwable.getMessage());
                    } else {
                        logger.debug("Order event published: {}", event);
                    }
                });
    }

    /**
     * Critical order confirmation with delivery guarantee.
     */
    public CompletionStage<Void> confirmOrderWithGuarantee(String orderId, String customerId, BigDecimal amount) {
        OrderEvent event = new OrderEvent(orderId, customerId, amount, OrderEvent.OrderEventType.CONFIRMED);

        // Use confirmation for critical events
        return messagingService.sendMessageWithConfirmation("order-confirmations", event)
                .thenRun(() -> logger.info("✅ Order confirmation delivered: {}", orderId))
                .exceptionally(throwable -> {
                    logger.error("❌ CRITICAL: Failed to confirm order {}: {}", orderId, throwable.getMessage());
                    // Could trigger fallback logic here
                    return null;
                });
    }

    /**
     * Synchronous payment processing (blocks until delivered).
     */
    public void processPaymentSync(String orderId, BigDecimal amount) {
        Map<String, Object> paymentData = Map.of(
                "orderId", orderId,
                "amount", amount,
                "timestamp", LocalDateTime.now().toString(),
                "processor", "stripe");

        try {
            // Synchronous delivery for payment events
            messagingService.sendMessageSync("payments", paymentData); // T = Map<String,Object>
            logger.info("💳 Payment processed synchronously: {} for ${}", orderId, amount);
        } catch (RuntimeException e) {
            logger.error("💳 PAYMENT FAILED for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Payment processing failed", e);
        }
    }

    /**
     * Batch notification example.
     */
    public void sendCustomerNotifications(String[] customerIds, String message) {
        for (String customerId : customerIds) {
            TestMessage notification = new TestMessage(
                    "notif-" + customerId,
                    message,
                    System.currentTimeMillis(),
                    1);

            // Fire-and-forget for notifications
            messagingService.sendMessage("customer-notifications", notification) // T = TestMessage
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            logger.warn("Failed to send notification to {}: {}", customerId, throwable.getMessage());
                        }
                    });
        }

        logger.info("📧 Queued {} customer notifications", customerIds.length);
    }

    /**
     * Analytics event with custom serialization.
     */
    public void trackAnalyticsEvent(String eventName, Map<String, Object> properties) {
        Map<String, Object> analyticsEvent = Map.of(
                "event", eventName,
                "properties", properties,
                "timestamp", System.currentTimeMillis(),
                "sessionId", generateSessionId());

        // Analytics can be fire-and-forget
        messagingService.sendMessage("analytics", analyticsEvent) // T = Map<String,Object>
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.debug("Analytics event failed (non-critical): {}", throwable.getMessage());
                    }
                });
    }

    private String generateSessionId() {
        return "session-" + System.currentTimeMillis();
    }
}
