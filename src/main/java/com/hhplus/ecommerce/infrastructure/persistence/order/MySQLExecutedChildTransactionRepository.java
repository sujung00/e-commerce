package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.ExecutedChildTransaction;
import com.hhplus.ecommerce.domain.order.ExecutedChildTransactionRepository;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.domain.order.ExecutionStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * MySQL 기반 ExecutedChildTransaction Repository 구현
 * Spring Data JPA를 사용한 영구 저장소
 *
 * Port(ExecutedChildTransactionRepository) 인터페이스를 구현하면서 JpaRepository 기능 제공
 */
@Repository
@Primary
public class MySQLExecutedChildTransactionRepository implements ExecutedChildTransactionRepository {

    private final ExecutedChildTransactionJpaRepository jpaRepository;

    public MySQLExecutedChildTransactionRepository(ExecutedChildTransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ExecutedChildTransaction save(ExecutedChildTransaction execution) {
        return jpaRepository.save(execution);
    }

    @Override
    public Optional<ExecutedChildTransaction> findById(Long executionId) {
        return jpaRepository.findById(executionId);
    }

    @Override
    public Optional<ExecutedChildTransaction> findByIdempotencyToken(String idempotencyToken) {
        return jpaRepository.findByIdempotencyToken(idempotencyToken);
    }

    @Override
    public Optional<ExecutedChildTransaction> findByIdempotencyTokenForUpdate(String idempotencyToken) {
        return jpaRepository.findByIdempotencyTokenForUpdate(idempotencyToken);
    }

    @Override
    public List<ExecutedChildTransaction> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public List<ExecutedChildTransaction> findByOrderIdAndStatus(Long orderId, ExecutionStatus status) {
        return jpaRepository.findByOrderIdAndStatus(orderId, status);
    }

    @Override
    public List<ExecutedChildTransaction> findByOrderIdAndTxType(Long orderId, ChildTxType txType) {
        return jpaRepository.findByOrderIdAndTxType(orderId, txType);
    }

    @Override
    public Optional<ExecutedChildTransaction> findByOrderIdAndTxTypeAndStatus(
            Long orderId, ChildTxType txType, ExecutionStatus status) {
        return jpaRepository.findByOrderIdAndTxTypeAndStatus(orderId, txType, status);
    }

    @Override
    public List<ExecutedChildTransaction> findByStatus(ExecutionStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public long countByOrderIdAndStatus(Long orderId, ExecutionStatus status) {
        return jpaRepository.countByOrderIdAndStatus(orderId, status);
    }

    @Override
    public long countFailedByOrderId(Long orderId) {
        return jpaRepository.countByOrderIdAndStatus(orderId, ExecutionStatus.FAILED);
    }

    @Override
    public List<ExecutedChildTransaction> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void delete(ExecutedChildTransaction execution) {
        jpaRepository.delete(execution);
    }
}
