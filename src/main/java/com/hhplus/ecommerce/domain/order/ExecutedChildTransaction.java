package com.hhplus.ecommerce.domain.order;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Executed Child Transaction (Idempotency 토큰 기반 재시도 안전성)
 *
 * 역할:
 * - 각 Child TX 실행 상태를 추적하여 멱등성 보장
 * - 동일 idempotencyToken으로의 재시도 시 중복 실행 방지
 * - 클라이언트 요청 레벨의 재시도 안전성 보증
 *
 * 특징:
 * - idempotencyToken은 UNIQUE 제약조건으로 보호됨
 * - 같은 토큰 = 같은 요청 = 한 번만 실행
 * - 재시도 횟수 추적으로 재시도 패턴 분석 가능
 *
 * 라이프사이클:
 * PENDING (실행 시작) → COMPLETED (성공) 또는 FAILED (실패)
 *                    ↓
 *                  PENDING → COMPLETED (재시도 성공)
 *
 * 사용 시나리오:
 * 1. 첫 시도: 토큰 생성 → ExecutedTransaction 저장 (PENDING) → Child TX 실행 → COMPLETED
 * 2. 재시도: 토큰 조회 → 이미 COMPLETED → skip (중복 방지) ✅
 * 3. 재시도 실패: 토큰 조회 → FAILED 상태 → 재시도 가능, 재실행 수행
 *
 * ✅ 개선사항:
 * - 멱등성 토큰으로 완전한 재시도 안전성 보증
 * - 클라이언트-서버 간 통신 신뢰성 강화
 * - 재시도 패턴 모니터링 가능
 */
@Entity
@Table(name = "executed_child_transactions",
        indexes = {
                @Index(name = "idx_idempotency_token", columnList = "idempotency_token", unique = true),
                @Index(name = "idx_order_id", columnList = "order_id"),
                @Index(name = "idx_status", columnList = "status")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutedChildTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long executionId;

    /**
     * 주문 ID
     * 같은 주문의 모든 Child TX 추적용
     */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /**
     * 멱등성 토큰 (Idempotency Token)
     * 클라이언트가 생성하는 UUID
     * UNIQUE 제약조건으로 동일 토큰의 중복 실행 방지
     *
     * 형식: UUID 예) "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
     * 생성 방식:
     * - 클라이언트 생성 (권장): 네트워크 재시도 시에도 동일 토큰 사용
     * - 서버 생성: UUID.randomUUID().toString()
     */
    @Column(name = "idempotency_token", nullable = false, length = 36, unique = true)
    private String idempotencyToken;

    /**
     * Child TX 타입
     * BALANCE_DEDUCT, COUPON_ISSUE, INVENTORY_DEDUCT 등
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false)
    private ChildTxType txType;

    /**
     * 실행 상태
     * PENDING: 아직 실행 안 됨 (초기 상태)
     * COMPLETED: 성공 (중복 실행 방지 - skip)
     * FAILED: 실패 (재시도 가능 - 재실행 수행)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExecutionStatus status;

    /**
     * Child TX 결과 데이터 (JSON)
     * 성공/실패 상관없이 결과 저장
     * 예: {"balanceAfter": 50000, "deductedAmount": 10000}
     *    {"couponId": 5, "couponStatus": "USED", "error": null}
     */
    @Column(name = "result_data", columnDefinition = "TEXT")
    private String resultData;

    /**
     * 재시도 횟수
     * 클라이언트의 재시도 시도 횟수 추적
     * 모니터링 및 분석용
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 타임스탬프
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /**
     * 팩토리 메서드: 신규 실행 기록 생성
     * 토큰 기반으로 새로운 실행 기록 시작
     */
    public static ExecutedChildTransaction create(
            Long orderId,
            String idempotencyToken,
            ChildTxType txType) {
        return ExecutedChildTransaction.builder()
                .orderId(orderId)
                .idempotencyToken(idempotencyToken)
                .txType(txType)
                .status(ExecutionStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 실행 완료 표기
     * Child TX 성공 시 호출
     */
    public void markAsCompleted(String resultData) {
        this.status = ExecutionStatus.COMPLETED;
        this.resultData = resultData;
        this.executedAt = LocalDateTime.now();
    }

    /**
     * 실행 실패 표기
     * Child TX 실패 시 호출
     */
    public void markAsFailed(String resultData) {
        this.status = ExecutionStatus.FAILED;
        this.resultData = resultData;
        this.executedAt = LocalDateTime.now();
    }

    /**
     * 재시도 카운트 증가
     * 클라이언트가 재시도할 때마다 호출
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 재시도 가능 여부 판단
     * COMPLETED 상태는 재시도 불가 (멱등성 보장)
     * PENDING, FAILED 상태는 재시도 가능
     */
    public boolean isRetryable() {
        return !ExecutionStatus.COMPLETED.equals(this.status);
    }
}
