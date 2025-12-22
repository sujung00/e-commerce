package com.hhplus.ecommerce.infrastructure.kafka;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.domain.coupon.event.CouponIssueRequest;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * CouponIssueConsumer - Kafka 쿠폰 발급 요청 소비 서비스
 *
 * 역할:
 * - coupon.issue.requests 토픽에서 쿠폰 발급 요청 수신
 * - 수신한 요청을 처리 (DB 쿠폰 발급)
 * - 수동 커밋으로 at-least-once 보장
 *
 * Consumer Group:
 * - group-id: ecommerce-coupon-consumer-group
 * - 동일 그룹 내 Consumer들이 파티션을 분담하여 처리
 * - 예: 10개 파티션, 10개 Consumer → 각 Consumer가 1개 파티션 담당
 *
 * Offset 관리:
 * - enable-auto-commit=false: 수동 커밋
 * - 메시지 처리 성공 후 acknowledgment.acknowledge() 호출
 * - 처리 실패 시 커밋하지 않음 → 재처리 보장 (at-least-once)
 *
 * 동시성:
 * - concurrency=10: 10개의 Consumer 스레드로 병렬 처리
 * - 각 스레드가 독립적으로 메시지 처리
 * - 10개 파티션 + 10개 Consumer = 최대 병렬도
 *
 * 처리 로직:
 * 1. Kafka에서 메시지 수신
 * 2. CouponIssueRequest로 역직렬화
 * 3. 비즈니스 로직 처리 (CouponService.issueCouponWithLock)
 * 4. 성공 시 acknowledgment.acknowledge() → Offset 커밋
 * 5. 실패 시 로깅 + 커밋하지 않음 → 다음 poll()에서 재처리
 *
 * 멱등성 보장:
 * - at-least-once 보장으로 인한 중복 메시지 처리 가능
 * - CouponService.issueCouponWithLock()에서 중복 발급 방지
 *   - UNIQUE(user_id, coupon_id) 제약
 *   - 중복 시 IllegalArgumentException 발생
 *
 * 성능 목표:
 * - 처리량: 200 req/s (P=10), 10,000 req/s (P=500)
 * - Consumer 개수 = 파티션 개수 (최대 병렬도)
 * - 각 Consumer: ~20 req/s (DB 처리 시간 ~50ms)
 */
@Service
public class CouponIssueConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);

    private final CouponService couponService;

    public CouponIssueConsumer(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * 쿠폰 발급 요청 리스너
     *
     * @KafkaListener 설정:
     * - topics: coupon.issue.requests (application.yml의 kafka.topics.coupon-issue-requests)
     * - groupId: ecommerce-coupon-consumer-group
     * - containerFactory: kafkaListenerContainerFactory (KafkaConfig 참조)
     *
     * 파라미터:
     * - @Payload: 메시지 본문 (CouponIssueRequest)
     * - @Header(PARTITION): 메시지가 속한 파티션 번호
     * - @Header(OFFSET): 메시지 Offset
     * - acknowledgment: 수동 커밋용 객체
     *
     * 처리 흐름 (멱등성 보장):
     * 1. Kafka에서 메시지 poll
     * 2. JSON → CouponIssueRequest 역직렬화
     * 3. handleCouponIssue() 호출
     *    - CouponService.issueCouponWithLock() 호출 (멱등성 보장)
     *    - UNIQUE constraint (user_id, coupon_id)로 중복 발급 방지
     * 4. 처리 성공 → acknowledgment.acknowledge()
     * 5. 중복 발급 (IllegalArgumentException) → acknowledgment.acknowledge() (재처리 불필요)
     * 6. 실제 실패 → 커밋하지 않음 → 재처리
     *
     * 멱등성 보장:
     * - Layer 1: Producer 멱등성 (enable.idempotence=true)
     * - Layer 2: at-least-once (수동 커밋)
     * - Layer 3: DB Unique Constraint (user_id, coupon_id) ← 이 레이어
     *
     * @param request 쿠폰 발급 요청
     * @param partition 파티션 번호
     * @param offset Offset
     * @param acknowledgment 수동 커밋 객체
     */
    @KafkaListener(
        topics = "${kafka.topics.coupon-issue-requests}",
        groupId = "${kafka.consumer.coupon-group-id}",
        containerFactory = "couponKafkaListenerContainerFactory"
    )
    public void listen(
            @Payload CouponIssueRequest request,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[CouponIssueConsumer] Kafka 메시지 수신 - " +
                "partition={}, offset={}, requestId={}, userId={}, couponId={}",
                partition, offset, request.getRequestId(), request.getUserId(), request.getCouponId());

        try {
            // 비즈니스 로직 처리 (멱등성 보장)
            handleCouponIssue(request);

            // 처리 성공 → Offset 커밋
            acknowledgment.acknowledge();
            log.info("[CouponIssueConsumer] Offset 커밋 완료 - " +
                    "partition={}, offset={}, requestId={}, userId={}, couponId={}",
                    partition, offset, request.getRequestId(), request.getUserId(), request.getCouponId());

        } catch (IllegalArgumentException e) {
            // ===== 비즈니스 로직 오류 처리 =====
            // 중복 발급, 쿠폰 소진, 유효기간 만료 등
            // 재시도해도 성공할 수 없는 경우 → Offset 커밋하고 skip
            log.warn("[CouponIssueConsumer] 비즈니스 오류 (재시도 불필요) - " +
                    "partition={}, offset={}, requestId={}, userId={}, couponId={}, 오류: {}",
                    partition, offset, request.getRequestId(), request.getUserId(),
                    request.getCouponId(), e.getMessage());

            // Offset 커밋 (재시도 불필요)
            acknowledgment.acknowledge();
            log.info("[CouponIssueConsumer] 비즈니스 오류 Offset 커밋 완료 - " +
                    "partition={}, offset={}, requestId={}",
                    partition, offset, request.getRequestId());

        } catch (Exception e) {
            // ===== 실제 에러 처리 =====
            // 처리 실패 → 커밋하지 않음 → 다음 poll()에서 재처리
            // 예: DB 연결 실패, 네트워크 오류, 타임아웃 등
            log.error("[CouponIssueConsumer] 메시지 처리 실패 (재처리 예정) - " +
                    "partition={}, offset={}, requestId={}, userId={}, couponId={}, error={}",
                    partition, offset, request.getRequestId(), request.getUserId(),
                    request.getCouponId(), e.getMessage(), e);

            // Offset 커밋하지 않음 → 다음 poll()에서 같은 메시지 재처리
            // 옵션:
            // - 현재: 예외 전파하지 않고 로깅만 (재처리 보장)
            // - 대안 1: 예외 전파하여 Spring Kafka ErrorHandler로 위임
            // - 대안 2: 재시도 횟수 초과 시 DLQ (Dead Letter Queue)로 전송
        }
    }

    /**
     * 쿠폰 발급 처리 로직 (멱등성 보장)
     *
     * 처리 순서:
     * 1. CouponService.issueCouponWithLock() 호출
     *    - DB 비관적 락 (SELECT FOR UPDATE)
     *    - UNIQUE constraint (user_id, coupon_id)로 중복 발급 방지
     *    - 중복 시 IllegalArgumentException 발생 → 상위에서 catch
     * 2. 처리 성공 시 로깅
     *
     * 멱등성 보장:
     * - UNIQUE constraint (user_id, coupon_id)로 중복 INSERT 방지
     * - at-least-once 보장으로 인한 재처리 시나리오:
     *   1. Consumer 재시작 후 미커밋 Offset 재처리
     *      - 이전 처리가 성공했지만 Offset 커밋 전 서버 종료
     *      - 재시작 후 같은 메시지 재처리
     *      - CouponService.issueCouponWithLock() → IllegalArgumentException
     *      - 상위에서 catch → Offset 커밋
     *   2. Kafka Rebalancing 중 중복 수신
     *      - Consumer Group 내 Consumer 추가/제거 시 Rebalancing
     *      - 일시적으로 같은 메시지를 여러 Consumer가 처리
     *      - 첫 처리: 성공 → 쿠폰 발급
     *      - 중복 처리: UNIQUE constraint 위반 → skip
     *
     * 실제 구현:
     * - CouponService.issueCouponWithLock()는 이미 멱등성 보장
     * - UNIQUE(user_id, coupon_id) 제약으로 중복 발급 방지
     * - 중복 시 IllegalArgumentException 발생
     *
     * @param request 쿠폰 발급 요청
     * @throws IllegalArgumentException 중복 발급, 쿠폰 소진, 유효기간 만료 등
     * @throws Exception 처리 실패 시 (DB 오류, 네트워크 오류 등)
     */
    private void handleCouponIssue(CouponIssueRequest request) throws Exception {
        log.info("[CouponIssueConsumer] >>> 쿠폰 발급 처리 시작 <<<");
        log.info("[CouponIssueConsumer]     - requestId: {}", request.getRequestId());
        log.info("[CouponIssueConsumer]     - userId: {}", request.getUserId());
        log.info("[CouponIssueConsumer]     - couponId: {}", request.getCouponId());
        log.info("[CouponIssueConsumer]     - requestedAt: {}", request.getRequestedAt());

        // CouponService.issueCouponWithLock() 호출 (멱등성 보장)
        // - DB 비관적 락 (SELECT FOR UPDATE)
        // - UNIQUE constraint (user_id, coupon_id)로 중복 발급 방지
        // - 중복 시 IllegalArgumentException: "이 쿠폰은 이미 발급받으셨습니다"
        IssueCouponResponse response = couponService.issueCouponWithLock(
                request.getUserId(),
                request.getCouponId()
        );

        log.info("[CouponIssueConsumer] 쿠폰 발급 완료 - " +
                "requestId={}, userId={}, couponId={}, userCouponId={}, discountAmount={}",
                request.getRequestId(), request.getUserId(), request.getCouponId(),
                response.getUserCouponId(), response.getDiscountAmount());

        log.info("[CouponIssueConsumer] >>> 쿠폰 발급 처리 완료 <<<");

        // 실패 테스트용 시뮬레이션 (선택적):
        // if (request.getUserId() % 10 == 0) {
        //     throw new Exception("DB connection timeout");
        // }
    }
}