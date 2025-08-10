# Message Subscriber System

This document explains how to use the comprehensive message subscriber system that receives and processes messages published by your AMQP messaging services.

## Overview

The `MessageSubscriberService` provides:

- **Message receipt confirmation** - Know that your messages were actually sent and received
- **Additional processing logic** - Perform business logic when messages are received
- **Statistics tracking** - Monitor message flow per topic
- **Type-safe handling** - Jackson deserialization of JSON objects

## How It Works

When you publish messages using `DynamicAmqpMessagingService`, `HighPerformanceAmqpMessagingService`, or `OrderEventService`, the subscribers automatically receive them and:

1. **Log receipt** with timestamps and counts
2. **Deserialize JSON** back to typed objects (where applicable)
3. **Execute additional processing logic** (your custom business logic)
4. **Track statistics** for monitoring

## Configured Subscribers

The following subscribers are configured and ready to receive messages:

### Jackson Message Subscribers

- **`test-jackson-topic`** - Receives TestMessage objects from `/send-jackson` endpoint
- **`test-jackson-confirmed-topic`** - Receives confirmed delivery messages from `/send-jackson-confirmed`
- **`customer-notifications`** - Receives customer notification TestMessages

### Business Event Subscribers

- **`user-logins`** - Receives user login events
- **`user-purchases`** - Receives user purchase events
- **`support-requests`** - Receives support request events
- **`user-activities`** - Receives general user activity events
- **`order-events`** - Receives order processing events

## Adding Custom Processing Logic

To add your own business logic when messages are received, modify the processing methods in `MessageSubscriberService`:

### Example: Processing Customer Notifications

```java
private void processCustomerNotification(TestMessage notification, String topic) {
    logger.debug("Processing customer notification: {}", notification.getId());

    // Your custom logic here:
    // - Update database with delivery confirmation
    // - Send acknowledgment to external system
    // - Trigger follow-up actions
    // - Update customer engagement metrics

    if (notification.getPriority() > 7) {
        // Handle urgent notifications differently
        handleUrgentNotification(notification);
    }

    logger.info("🔔 Customer notification processed: {}", notification.getId());
}
```

### Example: Processing Order Events

```java
private void processOrderEvent(String orderEvent, String topic) {
    // Example order processing logic:
    // - Update order status in database
    // - Calculate inventory impact
    // - Trigger fulfillment process
    // - Send confirmation email

    logger.debug("Processing order event from: {}", topic);
}
```

## Monitoring Subscribers

### Get Subscriber Statistics

Access comprehensive statistics about received messages:

```http
GET http://localhost:8081/amqp-dynamic/subscriber-stats
```

Response includes:

- Total messages received across all topics
- Message counts per topic
- Last message received per topic
- Timestamps of last messages

### Example Response

```json
{
  "totalMessagesReceived": 45,
  "messageCountsByTopic": {
    "test-jackson-topic": 12,
    "customer-notifications": 8,
    "user-logins": 15,
    "order-events": 10
  },
  "lastMessagePerTopic": {
    "test-jackson-topic": "{\"id\":\"test-123\",\"content\":\"Hello\",\"timestamp\":1691234567890,\"priority\":1}",
    "customer-notifications": "{\"id\":\"notif-456\",\"content\":\"Your order shipped\",\"timestamp\":1691234567891,\"priority\":5}"
  },
  "lastTimestampPerTopic": {
    "test-jackson-topic": "2024-08-10T14:30:15.123",
    "customer-notifications": "2024-08-10T14:32:22.456"
  }
}
```

## Testing Message Flow

### 1. Send a Message

```bash
curl -X POST http://localhost:8081/amqp-dynamic/send-jackson \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-123",
    "content": "Hello World",
    "timestamp": 1691234567890,
    "priority": 1
  }'
```

### 2. Check Subscriber Logs

Look for log entries like:

```
📨 [2024-08-10T14:30:15.123] RECEIVED Jackson message #1 from test-jackson-topic: TestMessage{id='test-123', content='Hello World', timestamp=1691234567890, priority=1}
```

### 3. Verify Statistics

```bash
curl http://localhost:8081/amqp-dynamic/subscriber-stats
```

## Configuration

### AMQP Channel Configuration

Subscribers are configured in `application.properties`:

```properties
# Jackson message consumers
mp.messaging.incoming.jackson-topic-consumer.connector=smallrye-amqp
mp.messaging.incoming.jackson-topic-consumer.address=test-jackson-topic
mp.messaging.incoming.jackson-topic-consumer.durable=false

# Customer notifications
mp.messaging.incoming.customer-notifications-consumer.connector=smallrye-amqp
mp.messaging.incoming.customer-notifications-consumer.address=customer-notifications
mp.messaging.incoming.customer-notifications-consumer.durable=false

# Business events
mp.messaging.incoming.user-logins-consumer.connector=smallrye-amqp
mp.messaging.incoming.user-logins-consumer.address=user-logins
mp.messaging.incoming.user-logins-consumer.durable=false
```

### Adding New Subscribers

To add a new subscriber:

1. **Add channel configuration** in `application.properties`:

```properties
mp.messaging.incoming.my-new-consumer.connector=smallrye-amqp
mp.messaging.incoming.my-new-consumer.address=my-new-topic
mp.messaging.incoming.my-new-consumer.durable=false
```

2. **Add subscriber method** in `MessageSubscriberService`:

```java
@Incoming("my-new-consumer")
public void handleMyNewMessages(String rawMessage) {
    String topic = "my-new-topic";
    try {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        long count = incrementTopicCount(topic);

        logger.info("📩 [{}] RECEIVED message #{} from {}: {}",
                   timestamp, count, topic, rawMessage);

        // Your processing logic
        processMyNewMessage(rawMessage, topic);

        updateStatistics(topic, rawMessage, timestamp);

    } catch (Exception e) {
        logger.error("❌ Failed to process message from {}: {}", topic, e.getMessage());
    }
}
```

## Benefits

✅ **End-to-end verification** - Confirm messages are actually delivered  
✅ **Additional processing** - Execute business logic upon message receipt  
✅ **Monitoring & analytics** - Track message flow and performance  
✅ **Type safety** - Deserialize JSON to typed objects automatically  
✅ **Error handling** - Graceful handling of malformed messages  
✅ **Scalable design** - Easy to add new subscribers and topics

## Logging Configuration

Control subscriber logging verbosity:

```properties
# Set to DEBUG to see detailed processing logs
quarkus.log.category."org.signal.processor.subscribers.MessageSubscriberService".level=INFO

# To see all debug messages:
quarkus.log.category."org.signal.processor.subscribers.MessageSubscriberService".level=DEBUG
```

This subscriber system gives you complete visibility into your message flow and allows you to perform additional work when messages are received, ensuring your AMQP messaging system is working end-to-end!
