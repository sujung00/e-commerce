package com.hhplus.ecommerce.presentation.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API 에러 응답 DTO
 */
@Getter
@Setter
public class ErrorResponse {
    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    @JsonProperty("request_id")
    private String requestId;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
        this.requestId = "req-" + UUID.randomUUID().toString().substring(0, 20);
    }

    public ErrorResponse(String errorCode, String errorMessage) {
        this();
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

}
