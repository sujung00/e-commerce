package com.hhplus.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * OutboxRepository - Outbox 저장소 Port Interface (Domain 계층)
 *
 * 역할:
 * - Outbox 메시지의 영속성을 추상화하는 Port 인터페이스
 * - 외부 시스템 전송 메시지 관리
 *
 * 구현체:
 * - InMemoryOutboxRepository (현재, Infrastructure 계층)
 * - 향후 RealOutboxRepository (MySQL + JPA)
 *
 * 메서드:
 * - save(): Outbox 저장 (2단계 트랜잭션 내)
 * - findById(): 메시지 조회
 * - findByOrderId(): 주문별 메시지 조회
 * - findAllByStatus(): 상태별 메시지 조회 (배치 프로세스용)
 * - update(): 메시지 상태 업데이트 (재시도, 전송 완료 등)
 */
public interface OutboxRepository {

    /**
     * Outbox 메시지 저장 (트랜잭션 내)
     *
     * @param outbox 저장할 Outbox 메시지
     * @return 저장된 Outbox (messageId 포함)
     */
    Outbox save(Outbox outbox);

    /**
     * 메시지 ID로 Outbox 조회
     *
     * @param messageId 메시지 ID
     * @return 조회된 Outbox (존재하지 않으면 Optional.empty())
     */
    Optional<Outbox> findById(Long messageId);

    /**
     * 주문 ID로 Outbox 메시지 조회
     *
     * @param orderId 주문 ID
     * @return 주문에 속한 모든 Outbox 메시지 리스트
     */
    List<Outbox> findByOrderId(Long orderId);

    /**
     * 상태별 Outbox 메시지 조회 (배치 프로세스용)
     *
     * @param status 메시지 상태 (PENDING, SENT, FAILED)
     * @return 해당 상태의 모든 Outbox 메시지 리스트
     */
    List<Outbox> findAllByStatus(String status);

    /**
     * Outbox 메시지 상태 업데이트
     *
     * @param outbox 업데이트할 Outbox 메시지
     * @return 업데이트된 Outbox
     */
    Outbox update(Outbox outbox);

    /**
     * 모든 Outbox 메시지 조회 (테스트, 관리 용도)
     *
     * @return 모든 Outbox 메시지 리스트
     */
    List<Outbox> findAll();
}
