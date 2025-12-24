package com.hhplus.ecommerce.domain.order;

import java.time.LocalDateTime;
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
 * - findByStatusIn(): 여러 상태 조회 (PENDING, FAILED 등)
 * - findStuckPublishingMessages(): PUBLISHING 상태가 오래된 메시지 조회 (타임아웃)
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
     * 여러 상태의 Outbox 메시지 조회 (배치 프로세스용)
     *
     * 사용 사례:
     * - PENDING과 FAILED 상태의 메시지만 조회하여 재시도
     * - PUBLISHING 상태는 제외하여 중복 발행 방지
     *
     * @param statuses 메시지 상태 목록 (예: ["PENDING", "FAILED"])
     * @return 해당 상태들의 모든 Outbox 메시지 리스트
     */
    List<Outbox> findByStatusIn(List<String> statuses);

    /**
     * PUBLISHING 상태가 오래된 메시지 조회 (타임아웃 처리)
     *
     * 사용 사례:
     * - PUBLISHING 상태에서 멈춰있는 메시지 감지 (서버 재시작, 장애 등)
     * - 일정 시간(예: 5분) 이상 PUBLISHING 상태인 메시지를 FAILED로 변경하여 재시도
     *
     * @param threshold PUBLISHING 상태 타임아웃 기준 시간
     * @return 타임아웃된 PUBLISHING 메시지 리스트
     */
    List<Outbox> findStuckPublishingMessages(LocalDateTime threshold);

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
