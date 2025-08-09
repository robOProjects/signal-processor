package org.signal.processor.dto;

/**
 * Record representing a message sending request.
 */
public record DynamicAmqpRequest(String topic, String message) {
}
