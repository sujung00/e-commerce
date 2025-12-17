package com.hhplus.ecommerce.domain.order;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * FailedCompensationEntity - 보상 실패 영구 저장 엔티티
 *
 * 역할:
 * - 보상 실패 정보를 DB에 영구 저장
 * - In-Memory 방식의 단점 해결 (서버 재시작 시 데이터 유실 방지)
 * - 다중 서버 환경에서 일관된 DLQ 관리
 * - 재처리 및 모니터링 지원
 *
 * 저장 정보:
 * - orderId: 주문 ID (식별자)
 * - userId: 사용자 ID
 * - stepName: 실패한 Step 이름
 * - stepOrder: Step 실행 순서
 * - errorMessage: 실패 원인
 * - stackTrace: 예외 스택 트레이스
 * - failedAt: 실패 시각
 * - resolvedAt: 해결 시각
 * - retryCount: 재시도 횟수
 * - status: 처리 상태 (PENDING, RESOLVED, ABANDONED)
 * - contextSnapshot: Saga 컨텍스트 스냅샷
 */
@Entity
@Table(name = "failed_compensations",
        indexes = {
                @Index(name = "idx_order_id", columnList = "order_id"),
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_failed_at", columnList = "failed_at"),
                @Index(name = "idx_step_name", columnList = "step_name")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedCompensationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "compensation_id")
    private Long compensationId;

    /**
     * 주문 ID
     */
    @Column(name = "order_id")
    private Long orderId;

    /**
     * 사용자 ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 실패한 Step 이름 (예: DeductInventoryStep)
     */
    @Column(name = "step_name", nullable = false, length = 100)
    private String stepName;

    /**
     * Step 실행 순서 (1~4)
     */
    @Column(name = "step_order")
    private Integer stepOrder;

    /**
     * 실패 원인 메시지
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 예외 스택 트레이스 (디버깅용)
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    /**
     * 실패 시각
     */
    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    /**
     * 해결 시각 (수동 처리 완료 시)
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * 재시도 횟수
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 처리 상태
     * PENDING: 대기 중 (미처리)
     * RESOLVED: 해결됨 (수동 처리 완료)
     * ABANDONED: 폐기됨 (재처리 불가)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FailedCompensationStatus status = FailedCompensationStatus.PENDING;

    /**
     * Saga Context 스냅샷 (JSON 형식)
     */
    @Column(name = "context_snapshot", columnDefinition = "TEXT")
    private String contextSnapshot;

    /**
     * 생성 시각
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 수정 시각
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 해결 표시 (수동 처리 후)
     */
    public void markAsResolved() {
        this.status = FailedCompensationStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재시도 횟수 증가
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 폐기 표시
     */
    public void markAsAbandoned() {
        this.status = FailedCompensationStatus.ABANDONED;
        this.updatedAt = LocalDateTime.now();
    }
}