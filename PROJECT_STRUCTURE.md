# Project Structure - DTO Organization

## Overview

All Data Transfer Objects (DTOs) have been organized into the `org.signal.processor.dto` package for better code organization and maintainability.

## Package Structure

```
src/main/java/org/signal/processor/
├── amqp/                    # AMQP messaging services
│   ├── DynamicAmqpMessagingService.java
│   └── HighPerformanceAmqpMessagingService.java
├── config/                  # Configuration classes
│   └── JacksonConfig.java
├── dto/                     # Data Transfer Objects
│   ├── BatchRequest.java
│   ├── DynamicAmqpRequest.java
│   ├── LoggingConfig.java
│   ├── MessageStats.java
│   ├── OrderEvent.java
│   ├── PrewarmRequest.java
│   └── TestMessage.java
├── resources/               # REST endpoints
│   └── DynamicAmqpResource.java
├── services/                # Business logic services
│   ├── ExampleBusinessService.java
│   ├── ExampleJsonMessagingService.java
│   └── OrderEventService.java
└── utils/                   # Utility classes
    └── TestMessageConsumer.java
```

## DTO Categories

### Message DTOs

- **`TestMessage.java`** - Simple test message with Jackson annotations
- **`OrderEvent.java`** - Business event for e-commerce orders
- **`DynamicAmqpRequest.java`** - Request object for message sending

### Configuration DTOs

- **`LoggingConfig.java`** - Configuration for logging behavior
- **`PrewarmRequest.java`** - Request for pre-warming topics
- **`BatchRequest.java`** - Request for batch message sending

### Statistics DTOs

- **`MessageStats.java`** - Message sending statistics with success rate calculation

## Benefits of DTO Package Organization

### ✅ **Clear Separation of Concerns**

- Business logic in `services/`
- Data structures in `dto/`
- REST endpoints in `resources/`
- Configuration in `config/`

### ✅ **Better Maintainability**

- Easy to find all DTOs in one place
- Clear dependencies between packages
- Reduced circular dependencies

### ✅ **Improved Reusability**

- DTOs can be shared across multiple services
- Clear API contracts between layers
- Easy to version and evolve data structures

### ✅ **Enhanced Testing**

- DTOs can be unit tested independently
- Mock objects easier to create
- Clear test data setup

## Usage Examples

### Importing DTOs

```java
import org.signal.processor.dto.TestMessage;
import org.signal.processor.dto.OrderEvent;
import org.signal.processor.dto.DynamicAmqpRequest;
```

### Using in Services

```java
@ApplicationScoped
public class OrderService {

    @Inject
    DynamicAmqpMessagingService messagingService;

    public void processOrder(String orderId) {
        OrderEvent event = new OrderEvent(orderId, customerId, amount, OrderEvent.OrderEventType.CREATED);
        messagingService.sendMessage("orders", event);  // Generic T = OrderEvent
    }
}
```

### REST Endpoint Usage

```java
@POST
@Path("/send-order")
@Consumes(MediaType.APPLICATION_JSON)
public Response sendOrder(OrderEvent orderEvent) {
    // Jackson automatically deserializes from JSON
    return messagingService.sendMessage("orders", orderEvent)
        .thenApply(result -> Response.ok().build());
}
```

## Migration Notes

- All imports updated to use `org.signal.processor.dto.*`
- No breaking changes to public APIs
- All functionality preserved
- Better type safety with generics maintained
