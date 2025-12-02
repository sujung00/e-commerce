package com.hhplus.ecommerce.domain.order;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Child Transaction Event (Outbox 패턴)
 *
 * 역할:
 * - 모든 Child TX 실행 기록 저장
 * - Parent TX 실패 시 보상 로직 추적용
 * - 이벤트 기반 비동기 처리 지원
 *
 * 특징:
 * - 자체 TX에서 저장됨 (Parent TX 실패와 무관)
 * - 보상 로직이 이 이벤트를 조회하여 동작
 *
 * 라이프사이클:
 * PENDING (생성) → COMPLETED (성공) → COMPENSATED (보상됨)
 *                  ↓
 *                 FAILED → COMPENSATED (필요시)
 *
 * ✅ 개선사항:
 * - Parent TX 실패 시 이미 커밋된 Child TX 자동 보상
 * - Outbox 패턴으로 일관성 보장
 * - 이벤트 데이터 JSON 저장으로 유연성 확보
 */
@Entity
@Table(name = "child_transaction_events",
        indexes = {
                @Index(name = "idx_order_id", columnList = "order_id"),
                @Index(name = "idx_status", columnList = "status")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChildTransactionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    /**
     * 부모 주문 ID
     * 같은 주문의 모든 Child TX를 추적하기 위함
     */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /**
     * 사용자 ID
     * 보상 로직 실행 시 필요 (환불 등)
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Child TX 타입
     * BALANCE_DEDUCT: 사용자 잔액 차감
     * COUPON_ISSUE: 쿠폰 발급
     * INVENTORY_DEDUCT: 재고 차감 (향후 추가)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false)
    private ChildTxType txType;

    /**
     * 이벤트 상태
     * PENDING: 실행 대기
     * COMPLETED: 성공 (보상 가능)
     * FAILED: 실패 (보상 불필요)
     * COMPENSATED: 보상됨 (복구 완료)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status;

    /**
     * 이벤트 데이터 (JSON)
     * 보상 로직에 필요한 정보를 저장
     * 예: {"amount": 10000, "couponId": 5, ...}
     */
    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;

    /**
     * Parent Transaction ID (추적용)
     * 향후 분산 트랜잭션 추적에 사용 가능
     */
    @Column(name = "parent_transaction_id")
    private Long parentTransactionId;

    /**
     * 타임스탬프
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "compensated_at")
    private LocalDateTime compensatedAt;

    /**
     * 보상 로직 실행 시간
     * 보상 로직이 이 이벤트를 언제 처리했는지 추적
     */
    @Column(name = "compensation_executed_at")
    private LocalDateTime compensationExecutedAt;

    /**
     * 팩토리 메서드: 신규 이벤트 생성
     */
    public static ChildTransactionEvent create(
            Long orderId,
            Long userId,
            ChildTxType txType,
            String eventData) {
        return ChildTransactionEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .txType(txType)
                .status(EventStatus.COMPLETED)
                .eventData(eventData)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 보상됨 표기
     */
    public void markAsCompensated() {
        this.status = EventStatus.COMPENSATED;
        this.compensatedAt = LocalDateTime.now();
    }
}
