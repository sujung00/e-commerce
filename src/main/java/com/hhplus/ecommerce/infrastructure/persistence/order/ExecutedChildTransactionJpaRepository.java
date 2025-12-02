package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.ExecutedChildTransaction;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.domain.order.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
