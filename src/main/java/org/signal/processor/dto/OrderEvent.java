package org.signal.processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Example business event for e-commerce orders.
 */
public class OrderEvent {

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("eventType")
    private OrderEventType eventType;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("metadata")
    private OrderMetadata metadata;

    // Constructors
    public OrderEvent() {
    }

    public OrderEvent(String orderId, String customerId, BigDecimal amount, OrderEventType eventType) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
        this.metadata = new OrderMetadata();
    }

    // Getters and setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public OrderEventType getEventType() {
        return eventType;
    }

    public void setEventType(OrderEventType eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public OrderMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(OrderMetadata metadata) {
        this.metadata = metadata;
    }

    public enum OrderEventType {
        CREATED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    }

    public static class OrderMetadata {
        @JsonProperty("source")
        private String source = "signal-processor";

        @JsonProperty("version")
        private String version = "1.0";

        // Getters and setters
        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    @Override
    public String toString() {
        return "OrderEvent{" +
                "orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", amount=" + amount +
                ", eventType=" + eventType +
                ", timestamp=" + timestamp +
                '}';
    }
}
