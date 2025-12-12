package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.ExecutedChildTransaction;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.domain.order.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * ExecutedChildTransaction JPA Repository
 * Spring Data JPA 기반 데이터 접근 인터페이스
 */
public interface ExecutedChildTransactionJpaRepository extends JpaRepository<ExecutedChildTransaction, Long> {

    /**
     * 멱등성 토큰으로 조회 (핵심 메서드)
     */
    Optional<ExecutedChildTransaction> findByIdempotencyToken(String idempotencyToken);

    /**
     * 멱등성 토큰으로 조회 (비관적 락)
     * SELECT ... FOR UPDATE 쿼리 사용
     *
     * 동시성 제어:
     * - 동일 토큰으로 동시 요청 시 첫 번째만 락 획득
     * - 두 번째 요청은 첫 번째 트랜잭션 완료까지 대기
     * - 중복 실행 완전 차단
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ExecutedChildTransaction e WHERE e.idempotencyToken = :token")
    Optional<ExecutedChildTransaction> findByIdempotencyTokenForUpdate(@Param("token") String idempotencyToken);

    /**
     * 주문 ID로 모든 실행 기록 조회
     */
    List<ExecutedChildTransaction> findByOrderId(Long orderId);

    /**
     * 주문 ID와 상태로 실행 기록 조회
     */
    List<ExecutedChildTransaction> findByOrderIdAndStatus(Long orderId, ExecutionStatus status);

    /**
     * 주문 ID와 TX 타입으로 실행 기록 조회
     */
    List<ExecutedChildTransaction> findByOrderIdAndTxType(Long orderId, ChildTxType txType);

    /**
     * 주문 ID, TX 타입, 상태로 실행 기록 조회
     */
    Optional<ExecutedChildTransaction> findByOrderIdAndTxTypeAndStatus(
            Long orderId, ChildTxType txType, ExecutionStatus status);

    /**
     * 상태별 모든 실행 기록 조회
     */
    List<ExecutedChildTransaction> findByStatus(ExecutionStatus status);

    /**
     * 주문 ID와 상태별 실행 개수 조회
     */
    long countByOrderIdAndStatus(Long orderId, ExecutionStatus status);

}
