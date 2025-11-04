package com.hhplus.ecommerce.domain;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Outbox 도메인 엔티티
 * 외부 시스템 전송 메시지 큐 (신뢰성 보장)
 * message_type: SHIPPING_REQUEST | PAYMENT_NOTIFICATION | ...
 * status: PENDING | SENT | FAILED
 * payload는 JSON 형식의 전송 데이터
 * retry_count와 last_attempt는 재시도 전략 관리용
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Outbox {
    private Long messageId;
    private Long orderId;
    private String messageType;
    private String status;
    private String payload;
    private Integer retryCount;
    private LocalDateTime lastAttempt;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}