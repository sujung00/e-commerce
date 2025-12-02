package com.hhplus.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * ChildTransactionEventRepository - Child TX Event 영속성 Port Interface
 * Outbox 패턴의 이벤트 저장소 역할
 *
 * 역할:
 * - Child TX 실행 기록 저장/조회
 * - 보상 대상 이벤트 식별 및 조회
 * - 이벤트 상태 관리 및 추적
 *
 * 사용 시나리오:
 * 1. Child TX 실행 후 Event 저장: save(event)
 * 2. Parent TX 실패 시 보상 대상 조회: findByOrderIdAndStatusNot(orderId, COMPENSATED)
 * 3. Event 상태 업데이트: save(updatedEvent)
 * 4. 보상 완료 이벤트 확인: findByOrderIdAndStatus(orderId, COMPENSATED)
 */
public interface ChildTransactionEventRepository {

    /**
     * 이벤트 저장
     *
     * @param event 저장할 Child TX Event
     * @return 저장된 Event (eventId 자동 생성)
     */
    ChildTransactionEvent save(ChildTransactionEvent event);

    /**
     * 이벤트 ID로 조회
     *
     * @param eventId 이벤트 ID
     * @return 해당 Event, 없으면 empty
     */
    Optional<ChildTransactionEvent> findById(Long eventId);

    /**
     * 주문별 모든 이벤트 조회
     * 주문의 모든 Child TX 실행 히스토리 조회
     *
     * @param orderId 주문 ID
     * @return 해당 주문의 모든 Event 리스트
     */
    List<ChildTransactionEvent> findByOrderId(Long orderId);

    /**
     * 주문별 특정 상태의 이벤트 조회
     * 예: 보상이 필요한 이벤트 조회 (COMPLETED 상태)
     *
     * @param orderId 주문 ID
     * @param status 조회 대상 상태 (PENDING, COMPLETED, FAILED, COMPENSATED)
     * @return 해당 조건의 Event 리스트
     */
    List<ChildTransactionEvent> findByOrderIdAndStatus(Long orderId, EventStatus status);

    /**
     * 주문별 특정 상태가 아닌 이벤트 조회
     * 보상 로직에서 아직 보상되지 않은 이벤트를 찾을 때 사용
     *
     * 사용 예:
     * - findByOrderIdAndStatusNot(orderId, COMPENSATED)
     *   → PENDING, COMPLETED, FAILED 상태의 이벤트 조회
     *
     * @param orderId 주문 ID
     * @param status 제외할 상태
     * @return 해당 조건의 Event 리스트
     */
    List<ChildTransactionEvent> findByOrderIdAndStatusNot(Long orderId, EventStatus status);

    /**
     * 주문과 TX 타입으로 이벤트 조회
     * 특정 타입의 Child TX (예: BALANCE_DEDUCT만)를 찾을 때 사용
     *
     * @param orderId 주문 ID
     * @param txType Child TX 타입
     * @return 해당 조건의 Event 리스트
     */
    List<ChildTransactionEvent> findByOrderIdAndTxType(Long orderId, ChildTxType txType);

    /**
     * 주문과 상태로 이벤트 조회 (제약조건: 단일 Event 반환)
     * COMPLETED 상태의 BALANCE_DEDUCT 이벤트를 조회할 때 사용
     *
     * @param orderId 주문 ID
     * @param txType Child TX 타입
     * @param status 이벤트 상태
     * @return 해당 조건의 Event (없으면 empty)
     */
    Optional<ChildTransactionEvent> findByOrderIdAndTxTypeAndStatus(
            Long orderId, ChildTxType txType, EventStatus status);

    /**
     * 특정 상태의 모든 이벤트 조회
     * 배치 작업에서 미처리 이벤트를 조회할 때 사용
     *
     * 사용 예:
     * - findByStatus(COMPLETED) → 보상 대기 중인 이벤트
     * - findByStatus(PENDING) → 아직 처리되지 않은 이벤트
     *
     * @param status 조회 대상 상태
     * @return 해당 상태의 모든 Event 리스트
     */
    List<ChildTransactionEvent> findByStatus(EventStatus status);

    /**
     * 주문별 보상 완료 여부 확인
     * Parent TX 보상이 완료되었는지 확인
     *
     * @param orderId 주문 ID
     * @return true: 모든 이벤트가 COMPENSATED 상태, false: 아직 보상 중
     */
    boolean isAllCompensated(Long orderId);

    /**
     * 주문별 미처리 이벤트 개수 조회
     * COMPLETED 또는 FAILED 상태의 이벤트 중 아직 COMPENSATED되지 않은 것의 개수
     *
     * @param orderId 주문 ID
     * @return 미처리 이벤트 개수
     */
    long countPendingCompensation(Long orderId);

    /**
     * 모든 이벤트 조회 (모니터링용)
     *
     * @return 모든 Event 리스트
     */
    List<ChildTransactionEvent> findAll();

    /**
     * 이벤트 삭제 (테스트/관리용)
     *
     * @param event 삭제할 Event
     */
    void delete(ChildTransactionEvent event);
}
