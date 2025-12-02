package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.ChildTransactionEvent;
import com.hhplus.ecommerce.domain.order.ChildTransactionEventRepository;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.domain.order.EventStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * MySQL 기반 ChildTransactionEvent Repository 구현
 * Spring Data JPA를 사용한 영구 저장소
 *
 * Port(ChildTransactionEventRepository) 인터페이스를 구현하면서 JpaRepository 기능 제공
 */
@Repository
@Primary
public class MySQLChildTransactionEventRepository implements ChildTransactionEventRepository {

    private final ChildTransactionEventJpaRepository jpaRepository;

    public MySQLChildTransactionEventRepository(ChildTransactionEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ChildTransactionEvent save(ChildTransactionEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<ChildTransactionEvent> findById(Long eventId) {
        return jpaRepository.findById(eventId);
    }

    @Override
    public List<ChildTransactionEvent> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public List<ChildTransactionEvent> findByOrderIdAndStatus(Long orderId, EventStatus status) {
        return jpaRepository.findByOrderIdAndStatus(orderId, status);
    }

    @Override
    public List<ChildTransactionEvent> findByOrderIdAndStatusNot(Long orderId, EventStatus status) {
        return jpaRepository.findByOrderIdAndStatusNot(orderId, status);
    }

    @Override
    public List<ChildTransactionEvent> findByOrderIdAndTxType(Long orderId, ChildTxType txType) {
        return jpaRepository.findByOrderIdAndTxType(orderId, txType);
    }

    @Override
    public Optional<ChildTransactionEvent> findByOrderIdAndTxTypeAndStatus(
            Long orderId, ChildTxType txType, EventStatus status) {
        return jpaRepository.findByOrderIdAndTxTypeAndStatus(orderId, txType, status);
    }

    @Override
    public List<ChildTransactionEvent> findByStatus(EventStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public boolean isAllCompensated(Long orderId) {
        // 모든 이벤트가 COMPENSATED 상태인지 확인
        // COMPENSATED가 아닌 상태의 이벤트가 하나도 없으면 true
        return !jpaRepository.existsByOrderIdAndStatusNot(orderId, EventStatus.COMPENSATED);
    }

    @Override
    public long countPendingCompensation(Long orderId) {
        // COMPLETED 또는 FAILED 상태 중 아직 COMPENSATED되지 않은 것의 개수
        // JPA에서는 직접 구현할 수 없으므로, 전체를 조회한 후 필터링
        List<ChildTransactionEvent> events = jpaRepository.findByOrderId(orderId);
        return events.stream()
                .filter(event -> (event.getStatus() == EventStatus.COMPLETED ||
                                event.getStatus() == EventStatus.FAILED) &&
                               event.getStatus() != EventStatus.COMPENSATED)
                .count();
    }

    @Override
    public List<ChildTransactionEvent> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void delete(ChildTransactionEvent event) {
        jpaRepository.delete(event);
    }
}
