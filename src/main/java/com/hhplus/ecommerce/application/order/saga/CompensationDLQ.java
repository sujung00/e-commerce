package com.hhplus.ecommerce.application.order.saga;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import java.util.ArrayList;

/**
 * CompensationDLQ - 보상 실패 Dead Letter Queue
 *
 * 역할:
 * - 보상 트랜잭션 실패 시 FailedCompensation을 임시 저장
 * - 수동 재처리를 위한 인터페이스 제공
 * - 실패 이력 추적 및 조회 기능
 *
 * 현재 구현:
 * - In-Memory ConcurrentHashMap 기반 (단일 서버 환경)
 * - 로깅을 통한 실패 알림
 *
 * 프로덕션 확장 방안:
 * - Kafka DLQ Topic: 분산 환경에서 메시지 유실 방지
 * - Database 저장: failed_compensations 테이블로 영구 보관
 * - Redis Queue: 빠른 조회와 TTL 기반 자동 삭제
 * - SQS/RabbitMQ DLQ: 클라우드 메시징 서비스
 *
 * 사용 흐름:
 * 1. OrderSagaOrchestrator에서 보상 실패 시 publish() 호출
 * 2. FailedCompensation을 DLQ에 저장
 * 3. 관리자가 getAllFailed() 조회
 * 4. 수동 재처리 후 markAsResolved() 호출
 *
 * 향후 기능:
 * - 자동 재시도 로직 (exponential backoff)
 * - 실패 통계 및 대시보드
 * - 알림 통합 (Slack, PagerDuty 등)
 */
@Slf4j
@Component
public class CompensationDLQ {

    /**
     * In-Memory DLQ Storage
     * Key: orderId
     * Value: List of FailedCompensation (하나의 주문에서 여러 Step 실패 가능)
     */
    private final ConcurrentMap<Long, List<FailedCompensation>> dlqStorage = new ConcurrentHashMap<>();

    /**
     * 보상 실패 메시지를 DLQ에 발행
     *
     * @param failedCompensation 실패한 보상 정보
     */
    public void publish(FailedCompensation failedCompensation) {
        Long orderId = failedCompensation.getOrderId();

        log.error("[CompensationDLQ] 보상 실패 메시지 DLQ 발행 - " +
                        "orderId={}, stepName={}, errorMessage={}",
                orderId,
                failedCompensation.getStepName(),
                failedCompensation.getErrorMessage());

        // DLQ에 저장 (동일 orderId에 대해 여러 실패 가능)
        dlqStorage.computeIfAbsent(orderId, k -> new ArrayList<>())
                .add(failedCompensation);

        log.warn("[CompensationDLQ] DLQ 저장 완료 - orderId={}, 총 실패 Step={}개",
                orderId,
                dlqStorage.get(orderId).size());

        // TODO: 프로덕션 구현
        // 1. Kafka DLQ Topic 발행
        // kafkaTemplate.send("compensation-dlq", failedCompensation);
        //
        // 2. Database 저장
        // failedCompensationRepository.save(toEntity(failedCompensation));
        //
        // 3. 외부 알림 시스템 연동
        // slackNotifier.sendAlert("보상 실패 발생: orderId=" + orderId);
    }

    /**
     * 특정 주문의 실패한 보상 목록 조회
     *
     * @param orderId 주문 ID
     * @return 실패한 보상 목록
     */
    public List<FailedCompensation> getFailedCompensations(Long orderId) {
        return dlqStorage.getOrDefault(orderId, new ArrayList<>());
    }

    /**
     * 모든 실패한 보상 조회 (관리자용)
     *
     * @return 모든 실패한 보상 목록
     */
    public List<FailedCompensation> getAllFailed() {
        List<FailedCompensation> allFailed = new ArrayList<>();
        dlqStorage.values().forEach(allFailed::addAll);

        log.info("[CompensationDLQ] 전체 실패 보상 조회 - 총 {}개 주문, {}개 실패 Step",
                dlqStorage.size(), allFailed.size());

        return allFailed;
    }

    /**
     * 보상 실패 해결 표시 (수동 처리 후)
     *
     * @param orderId 주문 ID
     */
    public void markAsResolved(Long orderId) {
        List<FailedCompensation> removed = dlqStorage.remove(orderId);

        if (removed != null) {
            log.info("[CompensationDLQ] 보상 실패 해결 처리 완료 - orderId={}, 제거된 실패 Step={}개",
                    orderId, removed.size());
        } else {
            log.warn("[CompensationDLQ] 해결 요청되었으나 DLQ에 없음 - orderId={}", orderId);
        }

        // TODO: 프로덕션 구현
        // failedCompensationRepository.markAsResolved(orderId);
    }

    /**
     * DLQ 크기 조회 (모니터링용)
     *
     * @return 실패한 주문 수
     */
    public int getSize() {
        return dlqStorage.size();
    }

    /**
     * DLQ 전체 삭제 (테스트용)
     */
    public void clear() {
        int size = dlqStorage.size();
        dlqStorage.clear();
        log.warn("[CompensationDLQ] DLQ 전체 삭제 - 삭제된 항목: {}개", size);
    }
}