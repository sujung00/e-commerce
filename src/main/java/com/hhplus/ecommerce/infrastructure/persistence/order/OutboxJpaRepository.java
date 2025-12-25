package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox JPA Repository
 * Spring Data JPA를 통한 Outbox 엔티티 영구 저장소
 */
public interface OutboxJpaRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByOrderId(Long orderId);

    List<Outbox> findByStatus(String status);

    /**
     * 여러 상태의 메시지 조회
     *
     * @param statuses 조회할 상태 목록
     * @return 해당 상태들의 메시지 리스트
     */
    List<Outbox> findByStatusIn(List<String> statuses);

    /**
     * PUBLISHING 상태가 오래된 메시지 조회 (타임아웃)
     *
     * @param threshold 타임아웃 기준 시간
     * @return 타임아웃된 PUBLISHING 메시지 리스트
     */
    @Query("SELECT o FROM Outbox o WHERE o.status = 'PUBLISHING' AND o.lastAttempt < :threshold")
    List<Outbox> findStuckPublishingMessages(@Param("threshold") LocalDateTime threshold);
}
