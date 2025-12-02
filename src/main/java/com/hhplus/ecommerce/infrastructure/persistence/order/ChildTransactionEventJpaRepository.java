package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.ChildTransactionEvent;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.domain.order.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * ChildTransactionEvent JPA Repository
 * Spring Data JPA 기반 데이터 접근 인터페이스
 */
public interface ChildTransactionEventJpaRepository extends JpaRepository<ChildTransactionEvent, Long> {

    /**
     * 주문 ID로 모든 이벤트 조회
     */
    List<ChildTransactionEvent> findByOrderId(Long orderId);

    /**
     * 주문 ID와 상태로 이벤트 조회
     */
    List<ChildTransactionEvent> findByOrderIdAndStatus(Long orderId, EventStatus status);

    /**
     * 주문 ID와 상태가 아닌 이벤트 조회
     */
    List<ChildTransactionEvent> findByOrderIdAndStatusNot(Long orderId, EventStatus status);

    /**
     * 주문 ID와 TX 타입으로 이벤트 조회
     */
    List<ChildTransactionEvent> findByOrderIdAndTxType(Long orderId, ChildTxType txType);

    /**
     * 주문 ID, TX 타입, 상태로 이벤트 조회
     */
    Optional<ChildTransactionEvent> findByOrderIdAndTxTypeAndStatus(
            Long orderId, ChildTxType txType, EventStatus status);

    /**
     * 상태별 모든 이벤트 조회
     */
    List<ChildTransactionEvent> findByStatus(EventStatus status);

    /**
     * 주문 ID로 모든 이벤트가 COMPENSATED 상태인지 확인
     */
    boolean existsByOrderIdAndStatusNot(Long orderId, EventStatus status);

    /**
     * 주문 ID로 미처리 이벤트 개수 조회
     */
    long countByOrderIdAndStatusNotAndStatusNot(Long orderId, EventStatus status1, EventStatus status2);
}
