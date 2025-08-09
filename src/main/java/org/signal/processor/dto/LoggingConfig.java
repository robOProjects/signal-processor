package org.signal.processor.dto;

/**
 * DTO for configuring logging behavior.
 */
public record LoggingConfig(boolean enableVerbose, int logInterval) {
}
