package com.hhplus.ecommerce.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * DataPlatformEventRepository - 데이터 플랫폼 이벤트 저장소
 *
 * 역할:
 * - 데이터 플랫폼 전송 이력 저장 및 조회
 * - 중복 처리 방지를 위한 조회 메서드 제공
 *
 * 중복 체크:
 * - findByOrderIdAndEventType()로 이미 처리된 이벤트인지 확인 가능
 * - 하지만 UNIQUE constraint로 충분하므로 직접 INSERT 시도하는 것이 더 효율적
 */
@Repository
public interface DataPlatformEventRepository extends JpaRepository<DataPlatformEvent, Long> {

    /**
     * 주문 ID와 이벤트 타입으로 이벤트 조회
     *
     * 사용 시나리오:
     * - 중복 처리 여부 사전 확인 (선택적)
     * - INSERT 전에 중복 체크하려면 사용 가능
     * - 하지만 일반적으로는 INSERT 시도 후 DuplicateKeyException 처리가 더 효율적
     *
     * @param orderId 주문 ID
     * @param eventType 이벤트 타입
     * @return Optional<DataPlatformEvent>
     */
    Optional<DataPlatformEvent> findByOrderIdAndEventType(Long orderId, String eventType);
}