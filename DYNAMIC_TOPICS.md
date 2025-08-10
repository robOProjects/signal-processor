# Dynamic Topic Handling System

This document explains how the system handles both **static predefined topics** and **dynamic topics created on the fly**.

## Overview

Your messaging system now supports two types of topic handling:

### 1. **Static Topics** (via MessageSubscriberService)

- Predefined topics with dedicated `@Incoming` subscribers
- Type-safe JSON deserialization
- Specific business logic per topic
- Examples: `test-jackson-topic`, `customer-notifications`, `user-logins`

### 2. **Dynamic Topics** (via DynamicTopicTrackingService)

- Topics created on demand with any name
- Pattern-based processing and monitoring
- Automatic tracking when messages are sent
- Examples: `order-12345`, `user-session-abc`, `temp-queue-xyz`

## How Dynamic Topics Work

When you send a message to **any topic name** using `DynamicAmqpMessagingService`, the system:

1. **Automatically tracks** the new topic
2. **Identifies the pattern** (ORDER, USER, NOTIFICATION, etc.)
3. **Processes accordingly** based on the pattern
4. **Maintains statistics** for monitoring
5. **Logs activity** for visibility

## Dynamic Topic Patterns

The system automatically recognizes these patterns:

| Pattern          | Topic Examples                       | Processing Logic                                 |
| ---------------- | ------------------------------------ | ------------------------------------------------ |
| **ORDER**        | `order-12345`, `order-abc`           | Order lifecycle tracking, fulfillment monitoring |
| **USER**         | `user-789`, `user-session-xyz`       | User activity tracking, session management       |
| **NOTIFICATION** | `notification-456`, `notif-urgent`   | Notification delivery tracking, metrics          |
| **TEMPORARY**    | `temp-123`, `temp-batch-processing`  | Short-lived topics, auto-cleanup                 |
| **SESSION**      | `session-abc123`, `session-user-789` | Session lifecycle management                     |
| **BATCH**        | `batch-import`, `batch-process-456`  | Batch operation monitoring                       |
| **TEST**         | `test-anything`, `my-test-topic`     | Enhanced logging for testing                     |
| **ID_BASED**     | `anything-123`, `entity-456`         | Generic ID-based processing                      |
| **GENERIC**      | `my-custom-topic`, `special-queue`   | Generic dynamic topic handling                   |

## Using Dynamic Topics

### Send to Any Topic Name

```bash
# Send to an order-specific topic
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "order-12345",
    "message": "Order status updated to shipped"
  }'

# Send to a user session topic
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "user-session-abc123",
    "message": "User logged in from new device"
  }'

# Send to a temporary processing topic
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "temp-batch-processing-xyz",
    "message": "Batch processing started"
  }'
```

### Monitor Dynamic Topics

Check what dynamic topics have been used:

```bash
curl http://localhost:8081/amqp-dynamic/dynamic-topic-stats
```

### Example Response

```json
{
  "totalDynamicMessages": 25,
  "topicMessageCounts": {
    "order-12345": 5,
    "user-session-abc123": 3,
    "temp-batch-xyz": 2,
    "notification-urgent-456": 1
  },
  "lastMessagePerTopic": {
    "order-12345": "Order status updated to shipped",
    "user-session-abc123": "User logged in from new device"
  },
  "lastSentTimestamp": {
    "order-12345": "2024-08-10T15:30:22.123",
    "user-session-abc123": "2024-08-10T15:32:15.456"
  },
  "topicPatterns": {
    "order-12345": "ORDER",
    "user-session-abc123": "SESSION",
    "temp-batch-xyz": "TEMPORARY",
    "notification-urgent-456": "NOTIFICATION"
  }
}
```

## Pattern-Based Processing

Each dynamic topic pattern triggers specific processing logic:

### ORDER Topics

```java
// Triggered for topics like "order-12345"
private void processOrderTopic(String topicName, String message) {
    String orderId = extractIdFromTopic(topicName, "order-");
    logger.info("📦 SENT to order topic {} (order: {}): {}", topicName, orderId, message);

    // Your order-specific logic:
    // - Set up monitoring for order status updates
    // - Configure notifications for this order
    // - Track order lifecycle
}
```

### USER Topics

```java
// Triggered for topics like "user-789", "user-session-abc"
private void processUserTopic(String topicName, String message) {
    String userId = extractIdFromTopic(topicName, "user-");
    logger.info("👤 SENT to user topic {} (user: {}): {}", topicName, userId, message);

    // Your user-specific logic:
    // - Track user-specific events
    // - Set up personalized monitoring
    // - Configure user session tracking
}
```

### TEMPORARY Topics

```java
// Triggered for topics like "temp-123", "temp-processing"
private void processTempTopic(String topicName, String message) {
    logger.info("⏱️ SENT to temporary topic {}: {}", topicName, message);

    // Temporary topics are marked for cleanup
    markForCleanup(topicName, 300000); // 5 minutes
}
```

## Integration with Your Code

The dynamic topic tracking is **automatically integrated** with your `DynamicAmqpMessagingService`. Every time you send a message to any topic, it gets tracked:

```java
// In DynamicAmqpMessagingService.sendMessage()
dynamicTopicTracker.trackTopicUsage(topic, message);
```

This means **zero additional code** is needed - just send messages to any topic name and they'll be tracked automatically!

## Complete Topic Monitoring

You now have **three statistics endpoints**:

### 1. Static Topic Subscribers

```bash
curl http://localhost:8081/amqp-dynamic/subscriber-stats
```

Shows messages **received** by static topic subscribers.

### 2. Dynamic Topic Tracking

```bash
curl http://localhost:8081/amqp-dynamic/dynamic-topic-stats
```

Shows messages **sent** to dynamic topics with pattern analysis.

### 3. Original Consumer Stats

```bash
curl http://localhost:8081/amqp-dynamic/consumer-stats
```

Shows basic test consumer statistics.

## Examples of Dynamic Topic Usage

### E-commerce Order Processing

```bash
# Order created
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -d '{"topic": "order-67890", "message": "Order created"}'

# Order updated
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -d '{"topic": "order-67890", "message": "Order payment confirmed"}'

# Order shipped
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -d '{"topic": "order-67890", "message": "Order shipped"}'
```

### User Session Management

```bash
# User login
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -d '{"topic": "user-session-user123", "message": "User logged in"}'

# Session activity
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -d '{"topic": "user-session-user123", "message": "Page viewed: /products"}'
```

### Temporary Processing

```bash
# Batch job
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -d '{"topic": "temp-batch-import-20240810", "message": "Starting batch import"}'

# Processing updates
curl -X POST http://localhost:8081/amqp-dynamic/send \
  -d '{"topic": "temp-batch-import-20240810", "message": "50% complete"}'
```

## Benefits

✅ **Complete Flexibility** - Send to any topic name without configuration  
✅ **Automatic Tracking** - All dynamic topics are monitored automatically  
✅ **Pattern Recognition** - Smart processing based on topic naming patterns  
✅ **Zero Configuration** - No need to predefined topics in application.properties  
✅ **Full Visibility** - Statistics and monitoring for all topic usage  
✅ **Business Logic** - Custom processing per topic pattern  
✅ **Scalable Design** - Handles unlimited dynamic topic names

This gives you the best of both worlds: **structured processing** for known topics and **complete flexibility** for dynamic topics created on the fly!

## Logging

Watch the logs to see dynamic topic processing:

```log
🆕 New dynamic topic discovered: order-12345 (pattern: ORDER)
📦 SENT to order topic order-12345 (order: 12345): Order status updated
👤 SENT to user topic user-session-abc (user: session-abc): User activity detected
⏱️ SENT to temporary topic temp-processing-xyz: Batch job started
🧹 Marked topic temp-processing-xyz for cleanup in 300000 ms
```

You now have a complete solution that handles **any topic name** you can think of!
