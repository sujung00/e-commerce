package com.hhplus.ecommerce.domain.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Outbox {

    private Long messageId;           // PK: 메시지 고유 식별자
    private Long orderId;             // FK: 주문 ID (NOT NULL)
    private Long userId;              // 사용자 ID (이벤트 추적용)
    private String messageType;       // 이벤트 타입: ORDER_COMPLETED, SHIPPING_REQUEST 등
    private String status;            // PENDING | SENT | FAILED
    private Integer retryCount;       // 재시도 횟수 (기본값: 0)
    private LocalDateTime lastAttempt; // 마지막 시도 시간 (nullable)
    private LocalDateTime sentAt;     // 전송 완료 시간 (nullable)
    private LocalDateTime createdAt;  // 생성 시각

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
}
