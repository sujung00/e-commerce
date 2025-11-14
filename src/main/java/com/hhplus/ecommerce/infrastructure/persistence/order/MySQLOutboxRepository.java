package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * MySQL 기반 Outbox Repository 구현
 * Spring Data JPA를 사용한 영구 저장소
 *
 * Port(OutboxRepository) 인터페이스를 구현하면서 JpaRepository 기능 제공
 */
@Repository
@Primary
@Transactional
public class MySQLOutboxRepository implements OutboxRepository {

    private final OutboxJpaRepository outboxJpaRepository;

    public MySQLOutboxRepository(OutboxJpaRepository outboxJpaRepository) {
        this.outboxJpaRepository = outboxJpaRepository;
    }

    @Override
    public Outbox save(Outbox outbox) {
        return outboxJpaRepository.save(outbox);
    }

    @Override
    public Optional<Outbox> findById(Long messageId) {
        return outboxJpaRepository.findById(messageId);
    }

    @Override
    public List<Outbox> findByOrderId(Long orderId) {
        return outboxJpaRepository.findByOrderId(orderId);
    }

    @Override
    public List<Outbox> findAllByStatus(String status) {
        return outboxJpaRepository.findByStatus(status);
    }

    @Override
    public Outbox update(Outbox outbox) {
        return outboxJpaRepository.save(outbox);
    }

    @Override
    public List<Outbox> findAll() {
        return outboxJpaRepository.findAll();
    }
}
