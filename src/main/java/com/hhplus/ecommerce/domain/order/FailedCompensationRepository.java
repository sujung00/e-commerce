package com.hhplus.ecommerce.domain.order;

import java.util.List;

/**
 * FailedCompensationRepository - 보상 실패 Repository 인터페이스
 *
 * 역할:
 * - 보상 실패 정보 영구 저장
 * - 상태별 조회 및 관리
 * - Thread-safe 및 다중 서버 환경 지원
 */
public interface FailedCompensationRepository {

    /**
     * 보상 실패 정보 저장
     *
     * @param entity 보상 실패 엔티티
     * @return 저장된 엔티티
     */
    FailedCompensationEntity save(FailedCompensationEntity entity);

    /**
     * 특정 주문의 보상 실패 목록 조회
     *
     * @param orderId 주문 ID
     * @return 보상 실패 목록
     */
    List<FailedCompensationEntity> findByOrderId(Long orderId);

    /**
     * 상태별 보상 실패 목록 조회
     *
     * @param status 처리 상태
     * @return 보상 실패 목록
     */
    List<FailedCompensationEntity> findByStatus(FailedCompensationStatus status);

    /**
     * 모든 PENDING 상태의 보상 실패 조회
     *
     * @return PENDING 보상 실패 목록
     */
    List<FailedCompensationEntity> findAllPending();

    /**
     * ID로 조회
     *
     * @param compensationId 보상 실패 ID
     * @return 보상 실패 엔티티 (Optional)
     */
    java.util.Optional<FailedCompensationEntity> findById(Long compensationId);
}