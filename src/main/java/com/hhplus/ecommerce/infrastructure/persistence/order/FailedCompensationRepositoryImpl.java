package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.FailedCompensationEntity;
import com.hhplus.ecommerce.domain.order.FailedCompensationRepository;
import com.hhplus.ecommerce.domain.order.FailedCompensationStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * FailedCompensationRepositoryImpl - 보상 실패 Repository 구현체
 *
 * JPA Repository를 감싸는 어댑터 패턴
 */
@Repository
public class FailedCompensationRepositoryImpl implements FailedCompensationRepository {

    private final FailedCompensationJpaRepository jpaRepository;

    public FailedCompensationRepositoryImpl(FailedCompensationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public FailedCompensationEntity save(FailedCompensationEntity entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public List<FailedCompensationEntity> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public List<FailedCompensationEntity> findByStatus(FailedCompensationStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public List<FailedCompensationEntity> findAllPending() {
        return jpaRepository.findAllPending();
    }

    @Override
    public Optional<FailedCompensationEntity> findById(Long compensationId) {
        return jpaRepository.findById(compensationId);
    }
}