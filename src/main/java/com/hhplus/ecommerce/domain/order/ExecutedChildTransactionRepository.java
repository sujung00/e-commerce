package com.hhplus.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * ExecutedChildTransactionRepository - Executed Child TX 영속성 Port Interface
 * 멱등성 토큰 기반 재시도 안전성을 위한 저장소
 *
 * 역할:
 * - 멱등성 토큰으로 중복 실행 방지
 * - Child TX 실행 상태 추적 및 관리
 * - 재시도 로직에서 중복 실행 판단
 *
 * 핵심 개념:
 * - idempotencyToken은 UNIQUE 제약조건으로 보호됨
 * - 동일 토큰 조회 → 이미 COMPLETED면 skip, FAILED면 재실행
 * - 재시도 횟수 추적으로 문제 분석 가능
 *
 * 사용 시나리오:
 * 1. 요청 처리 시작: findByIdempotencyToken(token) → 없으면 create
 * 2. 실행 중: status = PENDING
 * 3. 성공 시: markAsCompleted(resultData)
 * 4. 재시도 요청: findByIdempotencyToken(token) → COMPLETED면 skip, FAILED면 재실행
 */
public interface ExecutedChildTransactionRepository {

    /**
     * 실행 기록 저장
     *
     * @param execution 저장할 Executed Child TX
     * @return 저장된 Execution (executionId 자동 생성)
     */
    ExecutedChildTransaction save(ExecutedChildTransaction execution);

    /**
     * 실행 ID로 조회
     *
     * @param executionId 실행 ID
     * @return 해당 Execution, 없으면 empty
     */
    Optional<ExecutedChildTransaction> findById(Long executionId);

    /**
     * 멱등성 토큰으로 조회 (핵심 메서드)
     * 동일 요청의 중복 실행 여부를 판단하는 핵심 메서드
     *
     * 사용 패턴:
     * ```
     * Optional<ExecutedChildTransaction> existing = repo.findByIdempotencyToken(token);
     * if (existing.isPresent()) {
     *     if (existing.get().getStatus() == COMPLETED) {
     *         return existing.get().getResultData(); // 중복 방지
     *     } else if (existing.get().getStatus() == FAILED) {
     *         // 재시도 로직 수행
     *         existing.get().incrementRetryCount();
     *         // Child TX 재실행
     *     }
     * } else {
     *     // 새로운 요청 - 첫 실행
     *     create(token, txType);
     * }
     * ```
     *
     * @param idempotencyToken 멱등성 토큰 (UUID 형식)
     * @return 해당 토큰의 Execution, 없으면 empty
     */
    Optional<ExecutedChildTransaction> findByIdempotencyToken(String idempotencyToken);

    /**
     * 주문별 모든 실행 기록 조회
     * 주문의 모든 Child TX 실행 히스토리
     *
     * @param orderId 주문 ID
     * @return 해당 주문의 모든 Execution 리스트
     */
    List<ExecutedChildTransaction> findByOrderId(Long orderId);

    /**
     * 주문별 특정 상태의 실행 기록 조회
     * 예: 주문의 모든 FAILED 실행을 조회 → 재시도 후보 식별
     *
     * @param orderId 주문 ID
     * @param status 조회 대상 상태 (PENDING, COMPLETED, FAILED)
     * @return 해당 조건의 Execution 리스트
     */
    List<ExecutedChildTransaction> findByOrderIdAndStatus(Long orderId, ExecutionStatus status);

    /**
     * 주문과 TX 타입으로 실행 기록 조회
     * 특정 타입의 Child TX 실행 기록만 조회
     *
     * @param orderId 주문 ID
     * @param txType Child TX 타입
     * @return 해당 조건의 Execution 리스트
     */
    List<ExecutedChildTransaction> findByOrderIdAndTxType(Long orderId, ChildTxType txType);

    /**
     * 주문, TX 타입, 상태로 조회 (단일 결과)
     * BALANCE_DEDUCT 실행이 COMPLETED 상태인지 확인할 때 사용
     *
     * @param orderId 주문 ID
     * @param txType Child TX 타입
     * @param status 실행 상태
     * @return 해당 조건의 Execution, 없으면 empty
     */
    Optional<ExecutedChildTransaction> findByOrderIdAndTxTypeAndStatus(
            Long orderId, ChildTxType txType, ExecutionStatus status);

    /**
     * 특정 상태의 모든 실행 기록 조회
     * 배치 작업에서 미처리 작업을 찾을 때 사용
     *
     * @param status 조회 대상 상태
     * @return 해당 상태의 모든 Execution 리스트
     */
    List<ExecutedChildTransaction> findByStatus(ExecutionStatus status);

    /**
     * 주문별 성공한 실행 개수
     * 주문이 완료된 Child TX가 몇 개인지 확인
     *
     * @param orderId 주문 ID
     * @return COMPLETED 상태의 Execution 개수
     */
    long countByOrderIdAndStatus(Long orderId, ExecutionStatus status);

    /**
     * 주문별 재시도가 필요한 실행 개수
     * FAILED 상태의 Execution이 몇 개인지 확인
     *
     * @param orderId 주문 ID
     * @return FAILED 상태의 Execution 개수
     */
    long countFailedByOrderId(Long orderId);

    /**
     * 모든 실행 기록 조회 (모니터링용)
     *
     * @return 모든 Execution 리스트
     */
    List<ExecutedChildTransaction> findAll();

    /**
     * 실행 기록 삭제 (테스트/관리용)
     *
     * @param execution 삭제할 Execution
     */
    void delete(ExecutedChildTransaction execution);
}
