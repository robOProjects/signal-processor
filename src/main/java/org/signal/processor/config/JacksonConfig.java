package org.signal.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Jackson ObjectMapper configuration for JSON serialization.
 */
@ApplicationScoped
public class JacksonConfig {

    @Produces
    @Singleton
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Add Java 8 time module for LocalDateTime, etc.
        mapper.registerModule(new JavaTimeModule());

        // Don't write dates as timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Ignore unknown properties when deserializing
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }
}
