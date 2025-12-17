package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.FailedCompensationEntity;
import com.hhplus.ecommerce.domain.order.FailedCompensationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * FailedCompensationJpaRepository - 보상 실패 JPA Repository
 *
 * Spring Data JPA 기반 구현
 * Thread-safe 및 다중 서버 환경 지원
 */
public interface FailedCompensationJpaRepository extends JpaRepository<FailedCompensationEntity, Long> {

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
     * 실패 시각 순으로 정렬 (오래된 것부터)
     *
     * @return PENDING 보상 실패 목록
     */
    @Query("SELECT f FROM FailedCompensationEntity f WHERE f.status = 'PENDING' ORDER BY f.failedAt ASC")
    List<FailedCompensationEntity> findAllPending();

    /**
     * 특정 Step 이름의 보상 실패 목록 조회
     *
     * @param stepName Step 이름
     * @return 보상 실패 목록
     */
    List<FailedCompensationEntity> findByStepName(String stepName);
}