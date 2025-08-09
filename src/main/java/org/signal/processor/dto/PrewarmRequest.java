package org.signal.processor.dto;

/**
 * DTO for pre-warming topic request.
 */
public record PrewarmRequest(String[] topics) {
}
