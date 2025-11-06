package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * InMemoryOutboxRepository - InMemory 구현 (Infrastructure 계층)
 *
 * 역할:
 * - ConcurrentHashMap 기반 Outbox 메시지 저장소
 * - 2단계 트랜잭션 내에서 메시지 저장
 * - 배치 프로세스가 PENDING 상태 메시지를 조회
 *
 * 특징:
 * - 스레드 안전성: ConcurrentHashMap 사용
 * - messageId 자동 증가 (5001L부터 시작)
 * - 단순 상태 추적 (PENDING → SENT/FAILED)
 *
 * 마이그레이션:
 * - MySQL: outbox 테이블 → @Entity로 변환
 * - JPA: ConcurrentHashMap → JpaRepository
 * - 비즈니스 로직은 변경 없음
 *
 * 참고: Domain 계층의 OutboxRepository는 인터페이스 (Port)
 * 이 클래스는 그 인터페이스의 구현체 (Adapter)
 */
@Repository
public class InMemoryOutboxRepository implements OutboxRepository {

    private final ConcurrentHashMap<Long, Outbox> outboxStore = new ConcurrentHashMap<>();
    private Long outboxIdSequence = 5001L;  // messageId 시작값

    /**
     * Outbox 메시지 저장
     *
     * @param outbox 저장할 메시지
     * @return 저장된 메시지 (messageId 포함)
     */
    @Override
    public Outbox save(Outbox outbox) {
        Long messageId;
        synchronized (this) {
            messageId = outboxIdSequence++;
        }
        outbox.setMessageId(messageId);
        outboxStore.put(messageId, outbox);
        return outbox;
    }

    /**
     * 메시지 ID로 조회
     *
     * @param messageId 메시지 ID
     * @return 조회된 메시지 (없으면 Optional.empty())
     */
    @Override
    public Optional<Outbox> findById(Long messageId) {
        return Optional.ofNullable(outboxStore.get(messageId));
    }

    /**
     * 주문 ID로 메시지 조회
     *
     * @param orderId 주문 ID
     * @return 주문에 속한 모든 메시지 리스트
     */
    @Override
    public List<Outbox> findByOrderId(Long orderId) {
        return outboxStore.values().stream()
                .filter(outbox -> outbox.getOrderId().equals(orderId))
                .collect(Collectors.toList());
    }

    /**
     * 상태별 메시지 조회 (배치 프로세스용)
     *
     * @param status 메시지 상태 (PENDING, SENT, FAILED)
     * @return 해당 상태의 모든 메시지 리스트
     */
    @Override
    public List<Outbox> findAllByStatus(String status) {
        return outboxStore.values().stream()
                .filter(outbox -> outbox.getStatus().equals(status))
                .collect(Collectors.toList());
    }

    /**
     * 메시지 상태 업데이트
     *
     * @param outbox 업데이트할 메시지
     * @return 업데이트된 메시지
     */
    @Override
    public Outbox update(Outbox outbox) {
        outboxStore.put(outbox.getMessageId(), outbox);
        return outbox;
    }

    /**
     * 모든 메시지 조회 (테스트, 관리 용도)
     *
     * @return 모든 메시지 리스트
     */
    @Override
    public List<Outbox> findAll() {
        return List.copyOf(outboxStore.values());
    }
}
