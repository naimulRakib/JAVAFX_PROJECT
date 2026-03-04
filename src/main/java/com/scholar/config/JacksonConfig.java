package com.scholar.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * JacksonConfig — registers JavaTimeModule on Spring Boot's global ObjectMapper.
 *
 * Without this, any @RequestBody or @ResponseBody that contains a Java 8
 * date/time type (Instant, LocalDate, ZonedDateTime, etc.) throws:
 *
 *   HttpMessageNotReadableException: JSON parse error:
 *   Cannot deserialize value of type `java.time.Instant` from String "..."
 *
 * This bean is @Primary so Spring uses it everywhere (controllers, RestTemplate,
 * WebClient) instead of the default auto-configured one.
 *
 * Put this file in:
 *   src/main/java/com/scholar/config/JacksonConfig.java
 *
 * Also add this dependency to pom.xml if not already present:
 *   <dependency>
 *     <groupId>com.fasterxml.jackson.datatype</groupId>
 *     <artifactId>jackson-datatype-jsr310</artifactId>
 *   </dependency>
 * (Spring Boot manages the version automatically via spring-boot-dependencies BOM.)
 */
@Configuration
public class JacksonConfig {

    @Bean
    // NOTE: Do NOT add @Primary here — it would override StringHttpMessageConverter
    // and cause Jackson to double-encode ResponseEntity<String> responses (HTTP 500)
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                // Handle java.time.* types (Instant, LocalDate, ZonedDateTime, …)
                .registerModule(new JavaTimeModule())

                // Write dates as ISO-8601 strings, NOT as numeric timestamps
                // e.g. "2026-02-27T09:00:00Z"  instead of  1740647022000
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

                // Don't fail if the JSON contains fields the model doesn't have
                // (useful when Supabase returns extra columns like created_at)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}