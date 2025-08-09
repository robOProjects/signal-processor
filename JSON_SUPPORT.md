# JSON Support in DynamicAmqpMessagingService

## Overview

The `DynamicAmqpMessagingService` now supports sending JSON messages in addition to plain strings, providing both **Jackson** (modern) and **Jakarta JSON** (legacy) compatibility.

## Supported Message Types

### 1. String Messages (Original)

```java
messagingService.sendMessage("topic", "Hello World!");
```

### 2. Jackson Objects (Recommended for new code)

```java
TestMessage msg = new TestMessage("id", "content", timestamp, priority);
messagingService.sendMessage("topic", msg);  // Auto-serialized to JSON
```

### 3. Jakarta JsonObject (Legacy support)

```java
JsonObject json = Json.createObjectBuilder()
    .add("id", "test")
    .add("content", "Hello")
    .build();
messagingService.sendMessage("topic", json);
```

## Available Methods

All message sending methods now have JSON overloads:

### Fire-and-Forget (Async)

- `sendMessage(String topic, String message)`
- `sendMessage(String topic, Object object)` - Jackson serialization
- `sendMessage(String topic, JsonObject jsonObject)` - Jakarta JSON

### With Delivery Confirmation

- `sendMessageWithConfirmation(String topic, String message)`
- `sendMessageWithConfirmation(String topic, Object object)`
- `sendMessageWithConfirmation(String topic, JsonObject jsonObject)`

### Synchronous (Blocks until delivered)

- `sendMessageSync(String topic, String message)`
- `sendMessageSync(String topic, Object object)`
- `sendMessageSync(String topic, JsonObject jsonObject)`

### Synchronous Fire-and-Forget (Blocks until queued)

- `sendMessageSyncFireAndForget(String topic, String message)`
- `sendMessageSyncFireAndForget(String topic, Object object)`
- `sendMessageSyncFireAndForget(String topic, JsonObject jsonObject)`

## Dependencies Added

```gradle
implementation 'jakarta.json:jakarta.json-api'
implementation 'org.eclipse.parsson:parsson' // Jakarta JSON implementation
// Jackson support already included via 'io.quarkus:quarkus-rest-jackson'
```

## Test Endpoints

New REST endpoints for testing JSON functionality:

- `POST /amqp-dynamic/send-jackson` - Send Jackson object
- `POST /amqp-dynamic/send-jakarta-json` - Send Jakarta JsonObject
- `POST /amqp-dynamic/send-jackson-confirmed` - Send with confirmation
- `POST /amqp-dynamic/send-jackson-sync` - Send synchronously
- `GET /amqp-dynamic/json-examples` - Documentation

## Example Usage

See `ExampleJsonMessagingService` for complete business logic examples including:

- User notifications with Jackson POJOs
- Legacy alerts with Jakarta JSON
- Critical updates with synchronous delivery
- Batch processing patterns

## Error Handling

JSON serialization errors are:

- Logged with SLF4J
- Wrapped in `RuntimeException`
- Returned as failed `CompletableFuture` for async methods
- Thrown immediately for sync methods

## Best Practices

1. **Use Jackson for new code** - Better type safety and performance
2. **Use Jakarta JSON for legacy compatibility** - When integrating existing code
3. **Choose appropriate delivery method**:
   - `sendMessage()` - Fire-and-forget, highest throughput
   - `sendMessageWithConfirmation()` - Reliable delivery
   - `sendMessageSync()` - Critical messages requiring immediate confirmation
4. **Handle serialization errors** - Always wrap in try-catch for business logic
