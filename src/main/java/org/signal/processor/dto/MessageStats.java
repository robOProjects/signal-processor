package org.signal.processor.dto;

/**
 * Record representing message sending statistics.
 */
public record MessageStats(
        long messagesSent,
        long messagesFailed,
        String lastSuccessfulSend,
        String lastFailure,
        int cachedTopics) {

    public double getSuccessRate() {
        long total = messagesSent + messagesFailed;
        return total == 0 ? 0.0 : (double) messagesSent / total * 100.0;
    }
}
