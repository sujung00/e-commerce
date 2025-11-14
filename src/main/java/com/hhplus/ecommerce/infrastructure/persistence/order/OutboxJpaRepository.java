package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Outbox JPA Repository
 * Spring Data JPA를 통한 Outbox 엔티티 영구 저장소
 */
public interface OutboxJpaRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByOrderId(Long orderId);

    List<Outbox> findByStatus(String status);
}
