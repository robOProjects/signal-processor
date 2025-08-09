package org.signal.processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Example message class for JSON serialization testing.
 */
public class TestMessage {

    @JsonProperty("id")
    private String id;

    @JsonProperty("content")
    private String content;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("priority")
    private int priority;

    // Default constructor for Jackson
    public TestMessage() {
    }

    public TestMessage(String id, String content, long timestamp, int priority) {
        this.id = id;
        this.content = content;
        this.timestamp = timestamp;
        this.priority = priority;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "TestMessage{" +
                "id='" + id + '\'' +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", priority=" + priority +
                '}';
    }
}
