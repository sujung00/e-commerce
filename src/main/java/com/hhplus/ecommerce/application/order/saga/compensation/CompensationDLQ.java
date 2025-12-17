package com.hhplus.ecommerce.application.order.saga.compensation;

import com.hhplus.ecommerce.domain.order.FailedCompensationEntity;
import com.hhplus.ecommerce.domain.order.FailedCompensationRepository;
import com.hhplus.ecommerce.domain.order.FailedCompensationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CompensationDLQ - 보상 실패 Dead Letter Queue (DB 기반 영구 저장)
 *
 * 역할:
 * - 보상 트랜잭션 실패 시 FailedCompensation을 DB에 영구 저장
 * - 수동 재처리를 위한 인터페이스 제공
 * - 실패 이력 추적 및 조회 기능
 * - Thread-safe 및 다중 서버 환경 지원
 *
 * 개선사항:
 * - In-Memory → DB 기반 영구 저장 (서버 재시작 시에도 데이터 유지)
 * - 다중 서버 환경에서 일관된 DLQ 관리
 * - 트랜잭션 독립성 보장 (REQUIRES_NEW)
 *
 * 사용 흐름:
 * 1. OrderSagaOrchestrator에서 보상 실패 시 publish() 호출
 * 2. FailedCompensation을 DB에 저장 (PENDING 상태)
 * 3. 관리자가 getAllFailed() 조회
 * 4. 수동 재처리 후 markAsResolved() 호출 (RESOLVED 상태로 변경)
 *
 * 향후 기능:
 * - 자동 재시도 로직 (exponential backoff)
 * - 실패 통계 및 대시보드
 * - 알림 통합 (Slack, PagerDuty 등)
 */
@Slf4j
@Component
public class CompensationDLQ {

    private final FailedCompensationRepository failedCompensationRepository;

    public CompensationDLQ(FailedCompensationRepository failedCompensationRepository) {
        this.failedCompensationRepository = failedCompensationRepository;
    }

    /**
     * 보상 실패 메시지를 DLQ에 발행 (DB 저장)
     *
     * REQUIRES_NEW 전략:
     * - 보상 실패 기록은 부모 트랜잭션과 독립적으로 저장
     * - 부모 트랜잭션 롤백 시에도 DLQ 기록은 유지
     * - 보상 실패 이력을 반드시 남기기 위함
     *
     * @param failedCompensation 실패한 보상 정보
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(FailedCompensation failedCompensation) {
        Long orderId = failedCompensation.getOrderId();

        log.error("[CompensationDLQ] 보상 실패 메시지 DLQ 발행 - " +
                        "orderId={}, stepName={}, errorMessage={}",
                orderId,
                failedCompensation.getStepName(),
                failedCompensation.getErrorMessage());

        // FailedCompensation DTO → FailedCompensationEntity 변환
        FailedCompensationEntity entity = FailedCompensationEntity.builder()
                .orderId(orderId)
                .userId(failedCompensation.getUserId())
                .stepName(failedCompensation.getStepName())
                .stepOrder(failedCompensation.getStepOrder())
                .errorMessage(failedCompensation.getErrorMessage())
                .stackTrace(failedCompensation.getStackTrace())
                .failedAt(failedCompensation.getFailedAt())
                .retryCount(failedCompensation.getRetryCount())
                .status(FailedCompensationStatus.PENDING)
                .contextSnapshot(failedCompensation.getContextSnapshot())
                .createdAt(LocalDateTime.now())
                .build();

        // DB에 저장
        FailedCompensationEntity savedEntity = failedCompensationRepository.save(entity);

        log.warn("[CompensationDLQ] DLQ 저장 완료 - compensationId={}, orderId={}, stepName={}",
                savedEntity.getCompensationId(), orderId, failedCompensation.getStepName());
    }

    /**
     * 특정 주문의 실패한 보상 목록 조회
     *
     * @param orderId 주문 ID
     * @return 실패한 보상 목록
     */
    @Transactional(readOnly = true)
    public List<FailedCompensation> getFailedCompensations(Long orderId) {
        List<FailedCompensationEntity> entities = failedCompensationRepository.findByOrderId(orderId);

        log.info("[CompensationDLQ] orderId={}의 실패 보상 조회 - 총 {}개",
                orderId, entities.size());

        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 모든 PENDING 상태의 실패한 보상 조회 (관리자용)
     *
     * @return 모든 실패한 보상 목록
     */
    @Transactional(readOnly = true)
    public List<FailedCompensation> getAllFailed() {
        List<FailedCompensationEntity> entities = failedCompensationRepository.findAllPending();

        log.info("[CompensationDLQ] 전체 PENDING 실패 보상 조회 - 총 {}개", entities.size());

        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 보상 실패 해결 표시 (수동 처리 후)
     *
     * @param orderId 주문 ID
     */
    @Transactional
    public void markAsResolved(Long orderId) {
        List<FailedCompensationEntity> entities = failedCompensationRepository.findByOrderId(orderId);

        if (entities.isEmpty()) {
            log.warn("[CompensationDLQ] 해결 요청되었으나 DLQ에 없음 - orderId={}", orderId);
            return;
        }

        // 모든 PENDING 상태를 RESOLVED로 변경
        for (FailedCompensationEntity entity : entities) {
            if (entity.getStatus() == FailedCompensationStatus.PENDING) {
                entity.markAsResolved();
                failedCompensationRepository.save(entity);
            }
        }

        log.info("[CompensationDLQ] 보상 실패 해결 처리 완료 - orderId={}, 처리된 실패 Step={}개",
                orderId, entities.size());
    }

    /**
     * DLQ 크기 조회 (모니터링용)
     *
     * @return 실패한 주문 수 (PENDING 상태)
     */
    @Transactional(readOnly = true)
    public int getSize() {
        List<FailedCompensationEntity> entities = failedCompensationRepository.findAllPending();
        return entities.size();
    }

    /**
     * Entity → DTO 변환
     *
     * @param entity FailedCompensationEntity
     * @return FailedCompensation DTO
     */
    private FailedCompensation toDto(FailedCompensationEntity entity) {
        return FailedCompensation.builder()
                .orderId(entity.getOrderId())
                .userId(entity.getUserId())
                .stepName(entity.getStepName())
                .stepOrder(entity.getStepOrder())
                .errorMessage(entity.getErrorMessage())
                .stackTrace(entity.getStackTrace())
                .failedAt(entity.getFailedAt())
                .retryCount(entity.getRetryCount())
                .contextSnapshot(entity.getContextSnapshot())
                .build();
    }
}