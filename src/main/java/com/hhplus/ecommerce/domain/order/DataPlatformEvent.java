package com.hhplus.ecommerce.domain.order;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DataPlatformEvent - 데이터 플랫폼 전송 이력 (Domain 계층)
 *
 * 역할:
 * - Kafka Consumer에서 처리한 주문 완료 이벤트 이력 저장
 * - 중복 처리 방지 (멱등성 보장)
 * - at-least-once 보장 시 중복 메시지 필터링
 *
 * 중복 처리 방지 전략:
 * - UNIQUE constraint: (order_id, event_type)
 * - 같은 orderId + event_type 조합으로 INSERT 시 DuplicateKeyException 발생
 * - Consumer에서 DuplicateKeyException catch → 중복 메시지로 판단 → acknowledge()
 *
 * 흐름:
 * 1. Consumer가 Kafka에서 OrderCompletedEvent 수신
 * 2. 외부 데이터 플랫폼으로 전송
 * 3. DataPlatformEvent 저장 시도
 *    - 성공: 첫 처리 → Offset 커밋
 *    - DuplicateKeyException: 중복 처리 → Offset 커밋
 *    - 기타 예외: 재처리 필요 → Offset 커밋하지 않음
 */
@Entity
@Table(
    name = "data_platform_events",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "idx_data_platform_events_order_event",
            columnNames = {"order_id", "event_type"}
        )
    },
    indexes = {
        @Index(name = "idx_data_platform_events_created_at", columnList = "created_at")
    }
)
@AllArgsConstructor
@Builder
@Getter
@Setter
@NoArgsConstructor
public class DataPlatformEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    /**
     * 주문 ID
     * - UNIQUE constraint의 일부
     * - 같은 orderId + event_type 조합은 1번만 저장 가능
     */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /**
     * 이벤트 타입
     * - UNIQUE constraint의 일부
     * - 예: "ORDER_COMPLETED", "ORDER_CANCELLED" 등
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * 사용자 ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 주문 금액
     */
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    /**
     * 이벤트 발생 시각
     * - OrderCompletedEvent의 occurredAt
     */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /**
     * 처리 완료 시각
     * - Consumer에서 처리 완료 시점
     */
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    /**
     * 레코드 생성 시각
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * DataPlatformEvent 생성 (Consumer에서 호출)
     *
     * 호출 시점: OrderEventConsumer.handleOrderCompleted() 내
     * 특징: UNIQUE constraint로 중복 방지
     *
     * @param orderId 주문 ID
     * @param eventType 이벤트 타입
     * @param userId 사용자 ID
     * @param totalAmount 주문 금액
     * @param occurredAt 이벤트 발생 시각
     * @return DataPlatformEvent
     */
    public static DataPlatformEvent create(
            Long orderId,
            String eventType,
            Long userId,
            Long totalAmount,
            LocalDateTime occurredAt) {
        return DataPlatformEvent.builder()
                .orderId(orderId)
                .eventType(eventType)
                .userId(userId)
                .totalAmount(totalAmount)
                .occurredAt(occurredAt)
                .processedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }
}