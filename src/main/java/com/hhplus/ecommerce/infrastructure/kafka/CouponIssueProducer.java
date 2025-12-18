package com.hhplus.ecommerce.infrastructure.kafka;

import com.hhplus.ecommerce.domain.coupon.event.CouponIssueRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * CouponIssueProducer - Kafka 쿠폰 발급 요청 발행 서비스
 *
 * 역할:
 * - 쿠폰 발급 요청을 Kafka Topic: coupon.issue.requests로 발행
 * - userId를 Key로 사용하여 파티셔닝 (같은 사용자 요청은 같은 파티션)
 * - Producer 멱등성 보장 (enable.idempotence=true)
 *
 * 파티셔닝 전략:
 * - Key: userId (String 변환)
 * - 같은 userId는 항상 같은 파티션으로 전달
 * - Hash(userId) % PartitionCount → Partition 번호 결정
 * - 예: userId=123 → Partition 3 (고정)
 *
 * 순서 보장:
 * - 같은 사용자(userId)의 요청은 같은 파티션 → 순서 보장
 * - 다른 사용자의 요청은 다른 파티션 → 병렬 처리
 *
 * 멱등성 보장 (Producer):
 * - enable.idempotence=true (KafkaConfig 설정)
 * - Producer 재전송 시에도 중복 메시지가 Broker에 저장되지 않음
 * - acks=all, retries=Integer.MAX_VALUE, max.in.flight.requests.per.connection=5
 *
 * 처리 흐름:
 * 1. CouponController.issueCouponKafka() → sendCouponIssueRequest()
 * 2. CouponIssueRequest 생성 (userId, couponId, requestId)
 * 3. KafkaTemplate.send(topic, key=userId, value=request)
 * 4. Kafka Broker에 메시지 저장 (파티션 결정)
 * 5. Consumer가 파티션에서 메시지 poll → 처리
 *
 * 성능 목표:
 * - 응답 시간: < 10ms (비동기 발행)
 * - 처리량: 200 req/s (P=10), 10,000 req/s (P=500)
 * - 가용성: 99.9% (Kafka 클러스터 복제)
 */
@Component
public class CouponIssueProducer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueProducer.class);

    private final KafkaTemplate<String, CouponIssueRequest> kafkaTemplate;

    @Value("${kafka.topics.coupon-issue-requests}")
    private String topicName;

    public CouponIssueProducer(KafkaTemplate<String, CouponIssueRequest> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 쿠폰 발급 요청을 Kafka로 발행
     *
     * 파라미터:
     * - topic: coupon.issue.requests
     * - key: userId (String) → 파티셔닝 기준
     * - value: CouponIssueRequest (JSON 직렬화)
     *
     * 비동기 발행:
     * - send()는 CompletableFuture를 반환 (non-blocking)
     * - Controller는 즉시 202 Accepted 응답
     * - Callback으로 성공/실패 로깅
     *
     * 실패 처리:
     * - Producer 자동 재시도 (retries=Integer.MAX_VALUE)
     * - 재시도 실패 시 예외 발생 → Controller에서 처리
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return requestId (요청 추적용)
     * @throws Exception Kafka 발행 실패 시
     */
    public String sendCouponIssueRequest(Long userId, Long couponId) throws Exception {
        // 1. CouponIssueRequest 생성
        CouponIssueRequest request = CouponIssueRequest.create(userId, couponId);

        // 2. Kafka로 발행 (Key: userId, Value: request)
        // Key를 String으로 변환하여 파티셔닝
        String key = String.valueOf(userId);

        try {
            log.info("[CouponIssueProducer] 쿠폰 발급 요청 발행 시작 - " +
                    "topic={}, key={}, requestId={}, userId={}, couponId={}",
                    topicName, key, request.getRequestId(), userId, couponId);

            // 비동기 발행
            CompletableFuture<SendResult<String, CouponIssueRequest>> future =
                    kafkaTemplate.send(topicName, key, request);

            // Callback 등록 (성공/실패 로깅)
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // 발행 성공
                    int partition = result.getRecordMetadata().partition();
                    long offset = result.getRecordMetadata().offset();
                    log.info("[CouponIssueProducer] 발행 완료 - " +
                            "requestId={}, userId={}, couponId={}, partition={}, offset={}",
                            request.getRequestId(), userId, couponId, partition, offset);
                } else {
                    // 발행 실패
                    log.error("[CouponIssueProducer] 발행 실패 - " +
                            "requestId={}, userId={}, couponId={}, error={}",
                            request.getRequestId(), userId, couponId, ex.getMessage(), ex);
                }
            });

            // 3. requestId 반환 (클라이언트 추적용)
            return request.getRequestId();

        } catch (Exception e) {
            log.error("[CouponIssueProducer] 쿠폰 발급 요청 발행 실패 - " +
                    "userId={}, couponId={}, error={}",
                    userId, couponId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 파티션 번호 예측 (테스트/디버깅용)
     *
     * 실제 파티션 결정은 Kafka DefaultPartitioner가 수행
     * Hash(key) % partitionCount → partition
     *
     * @param userId 사용자 ID
     * @param partitionCount 파티션 개수
     * @return 예상 파티션 번호
     */
    public int predictPartition(Long userId, int partitionCount) {
        String key = String.valueOf(userId);
        // DefaultPartitioner는 murmur2 해시 사용
        // 여기서는 간단히 hashCode 사용 (정확하지 않을 수 있음)
        return Math.abs(key.hashCode() % partitionCount);
    }
}