package com.hhplus.ecommerce.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson ObjectMapper 설정
 *
 * 목적:
 * - Java 8 Date/Time API (Instant, LocalDateTime 등) 직렬화/역직렬화 지원
 * - ErrorResponse의 timestamp(Instant) 필드를 ISO-8601 형식으로 JSON 변환
 *
 * 설정 내용:
 * - JavaTimeModule 등록 (jackson-datatype-jsr310)
 * - Instant를 타임스탬프 대신 ISO-8601 문자열로 직렬화 (write-dates-as-timestamps: false)
 * - 알 수 없는 JSON 속성 무시 (fail-on-unknown-properties: false)
 */
@Configuration
public class JacksonConfig {

    /**
     * ObjectMapper 빈 등록
     *
     * @Primary: Spring Boot의 기본 ObjectMapper를 대체
     * @return 설정된 ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder
                .createXmlMapper(false)
                .build();

        // JavaTimeModule 등록 (Instant, LocalDateTime 등 지원)
        objectMapper.registerModule(new JavaTimeModule());

        // Instant를 ISO-8601 문자열로 직렬화 (예: "2025-01-15T10:30:00Z")
        // false: 타임스탬프 숫자 대신 문자열 사용
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 알 수 없는 JSON 속성 무시 (클라이언트가 추가 필드를 보내도 에러 안 남)
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return objectMapper;
    }
}