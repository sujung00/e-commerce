package com.hhplus.ecommerce.domain.order;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Outbox - 외부 시스템 전송 메시지 (Domain 계층)
 *
 * 역할:
 * - 주문 완료 후 외부 시스템(배송, 결제 등)으로 전송할 메시지를 임시 저장
 * - 비동기 처리를 위한 메시지 큐 역할 (Outbox 패턴)
 *
 * 특징:
 * - 트랜잭션 2단계 내에서 주문과 함께 저장되므로 원자성 보장
 * - 별도 배치 프로세스가 PENDING 상태의 메시지를 외부 시스템에 전송
 * - 전송 실패 시 재시도 가능
 *
 * 흐름:
 * 1. 주문 생성 시 Outbox 기록 (status='PENDING')
 * 2. 배치 프로세스가 PENDING 상태 확인
 * 3. 외부 시스템에 전송 시도
 * 4. 성공 → status='SENT', 실패 → status='FAILED' (재시도 예정)
 *
 * 구현 참고:
 * - order_id는 NOT NULL (주문과의 관계 필수)
 * - message_type은 이벤트 타입 (ORDER_COMPLETED, SHIPPING_REQUEST 등)
 * - status는 PENDING | SENT | FAILED 중 하나
 * - retry_count는 재시도 횟수 추적
 */
@Entity
@Table(name = "outbox")
@AllArgsConstructor
@Builder
@Getter
@Setter
@NoArgsConstructor
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "last_attempt")
    private LocalDateTime lastAttempt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Outbox 메시지 생성 (2단계 트랜잭션 내)
     *
     * 호출 시점: OrderTransactionService.executeTransactionalOrder() 내
     * 특징: 트랜잭션과 함께 원자적으로 처리됨
     */
    public static Outbox createOutbox(Long orderId, Long userId, String messageType) {
        return Outbox.builder()
                .orderId(orderId)
                .userId(userId)
                .messageType(messageType)
                .status("PENDING")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Outbox 메시지 생성 (payload 포함)
     *
     * 호출 시점: OutboxEventPublisher.publishWithOutbox() 내
     * 특징: 외부 시스템 전송 데이터를 JSON 형태로 저장
     */
    public static Outbox createOutboxWithPayload(Long orderId, Long userId, String messageType, String payload) {
        return Outbox.builder()
                .orderId(orderId)
                .userId(userId)
                .messageType(messageType)
                .payload(payload)
                .status("PENDING")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 전송 완료 표시
     */
    public void markAsSent() {
        this.status = "SENT";
        this.sentAt = LocalDateTime.now();
    }

    /**
     * 전송 실패 표시
     */
    public void markAsFailed() {
        this.status = "FAILED";
        this.lastAttempt = LocalDateTime.now();
        this.retryCount++;
    }

    /**
     * 전송 재시도 대기 상태로 변경
     */
    public void resetForRetry() {
        this.status = "PENDING";
        this.lastAttempt = LocalDateTime.now();
    }

    /**
     * 최대 재시도 초과 여부 확인
     *
     * @param maxRetries 최대 재시도 횟수
     * @return true: 최대 재시도 초과, false: 재시도 가능
     */
    public boolean shouldAbandoned(int maxRetries) {
        return this.retryCount >= maxRetries;
    }
}
