package org.signal.processor.dto;

/**
 * DTO for batch message sending request.
 */
public record BatchRequest(String topic, String[] messages) {
}
