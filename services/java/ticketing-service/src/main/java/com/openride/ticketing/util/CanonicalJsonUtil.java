package com.openride.ticketing.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical JSON generator for consistent hashing.
 * 
 * Ensures that the same data always produces the same JSON string
 * by sorting keys alphabetically and using consistent formatting.
 * 
 * This is critical for cryptographic operations where even minor
 * differences in formatting would produce different hashes.
 */
@Slf4j
public class CanonicalJsonUtil {

    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false); // No whitespace
    }

    /**
     * Convert object to canonical JSON string.
     * 
     * @param object the object to serialize
     * @return canonical JSON string
     * @throws RuntimeException if serialization fails
     */
    public static String toCanonicalJson(Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }

        try {
            // Convert to map to ensure key ordering
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.convertValue(object, Map.class);
            TreeMap<String, Object> sortedMap = new TreeMap<>(map);
            
            return mapper.writeValueAsString(sortedMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to generate canonical JSON", e);
            throw new RuntimeException("Canonical JSON generation failed", e);
        }
    }

    /**
     * Parse canonical JSON string to object.
     * 
     * @param json the JSON string
     * @param valueType the target class
     * @param <T> the type parameter
     * @return the deserialized object
     * @throws RuntimeException if parsing fails
     */
    public static <T> T fromCanonicalJson(String json, Class<T> valueType) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }

        try {
            return mapper.readValue(json, valueType);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse canonical JSON", e);
            throw new RuntimeException("Canonical JSON parsing failed", e);
        }
    }
}
