package com.hhplus.ecommerce.infrastructure.kafka;

import com.hhplus.ecommerce.domain.coupon.event.CouponIssueRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CouponIssueProducer - Kafka 쿠폰 발급 요청 발행 서비스
 *
 * 역할:
 * - 쿠폰 발급 요청을 Kafka Topic: coupon.issue.requests로 발행
 * - couponId를 Key로 사용하여 파티셔닝 (같은 쿠폰 요청은 같은 파티션)
 * - Producer 멱등성 보장 (enable.idempotence=true)
 *
 * 파티셔닝 전략 (✅ 개선: userId → couponId):
 * - Key: couponId (String 변환)
 * - 같은 couponId는 항상 같은 파티션으로 전달
 * - Hash(couponId) % PartitionCount → Partition 번호 결정
 * - 예: couponId=1 → Partition 1 (고정)
 *
 * 순서 보장 (✅ 선착순 쿠폰 발급):
 * - 같은 쿠폰(couponId)에 대한 모든 요청은 같은 파티션 → 순서 보장
 * - 해당 파티션의 Consumer가 순서대로 처리 → 선착순 보장
 * - 서로 다른 쿠폰의 요청은 다른 파티션 → 병렬 처리
 * - 예: couponId=1 요청 100개 → Partition 1에서 순서대로 처리
 *
 * ⚠️ 파티셔닝 키 선정 이유:
 * - 선착순 쿠폰 발급 시 같은 쿠폰에 대한 요청 순서가 중요
 * - DB에서 couponId로 비관적 락 획득 (SELECT ... FOR UPDATE)
 * - 같은 쿠폰 요청은 순서대로 처리되어야 재고 감소 순서 보장
 * - userId 파티셔닝 시 같은 쿠폰 내 순서 보장 안 됨 (선착순 불가능)
 *
 * 멱등성 보장 (Producer):
 * - enable.idempotence=true (KafkaConfig 설정)
 * - Producer 재전송 시에도 중복 메시지가 Broker에 저장되지 않음
 * - acks=all, retries=Integer.MAX_VALUE, max.in.flight.requests.per.connection=5
 *
 * 처리 흐름:
 * 1. CouponController.issueCouponKafka() → sendCouponIssueRequest()
 * 2. CouponIssueRequest 생성 (userId, couponId, requestId)
 * 3. KafkaTemplate.send(topic, key=couponId, value=request)
 * 4. Kafka Broker에 메시지 저장 (파티션 결정)
 * 5. Consumer가 파티션에서 메시지 poll → 순서대로 처리
 *
 * 성능 목표:
 * - 응답 시간: < 5초 (동기 대기, Kafka 발행 완료 확인)
 * - 처리량: 쿠폰당 순차 처리 (선착순 보장)
 * - 가용성: 99.9% (Kafka 클러스터 복제)
 */
@Component
public class CouponIssueProducer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueProducer.class);

    private final KafkaTemplate<String, CouponIssueRequest> kafkaTemplate;

    @Value("${spring.kafka.topics.coupon-issue-requests}")
    private String topicName;

    public CouponIssueProducer(KafkaTemplate<String, CouponIssueRequest> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 쿠폰 발급 요청을 Kafka로 발행
     *
     * 파라미터:
     * - topic: coupon.issue.requests
     * - key: couponId (String) → 파티셔닝 기준 (✅ 개선: userId → couponId)
     * - value: CouponIssueRequest (JSON 직렬화)
     *
     * 파티셔닝 전략 (✅ 선착순 쿠폰 발급):
     * - key를 couponId로 설정하여 같은 쿠폰 요청은 같은 파티션으로 이동
     * - 해당 파티션의 Consumer가 순서대로 처리 → 선착순 보장
     * - 예: couponId=1 요청 100개 → 모두 같은 파티션 → 순서대로 처리
     *
     * 동기 발행 (개선):
     * - send()는 CompletableFuture를 반환
     * - .get(5, TimeUnit.SECONDS)로 동기 대기 (최대 5초)
     * - 발행 성공 확인 후 requestId 반환
     * - 발행 실패 시 예외를 Controller로 전파
     *
     * 실패 처리:
     * - Producer 자동 재시도 (retries=Integer.MAX_VALUE)
     * - 타임아웃 시 TimeoutException → RuntimeException 변환
     * - 재시도 실패 시 예외 발생 → Controller에서 5xx 응답
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID (파티셔닝 키로 사용)
     * @return requestId (요청 추적용)
     * @throws RuntimeException Kafka 발행 실패 또는 타임아웃 시
     */
    public String sendCouponIssueRequest(Long userId, Long couponId) {
        // 1. CouponIssueRequest 생성
        CouponIssueRequest request = CouponIssueRequest.create(userId, couponId);

        // 2. Kafka로 발행 (Key: couponId, Value: request)
        // ✅ 파티셔닝 키: couponId (선착순 쿠폰 발급 순서 보장)
        // - 같은 쿠폰에 대한 모든 요청이 같은 파티션으로 이동
        // - 해당 파티션의 Consumer가 순서대로 처리 → 선착순 보장
        String key = String.valueOf(couponId);

        log.info("[CouponIssueProducer] 쿠폰 발급 요청 발행 시작 - " +
                "topic={}, key={} (couponId), requestId={}, userId={}, couponId={}",
                topicName, key, request.getRequestId(), userId, couponId);

        try {
            // 동기 발행 - 최대 5초 대기
            CompletableFuture<SendResult<String, CouponIssueRequest>> future =
                    kafkaTemplate.send(topicName, key, request);

            // CompletableFuture 결과를 동기적으로 대기
            SendResult<String, CouponIssueRequest> result = future.get(5, TimeUnit.SECONDS);

            // 발행 성공 - 파티션 및 오프셋 정보 로깅
            int partition = result.getRecordMetadata().partition();
            long offset = result.getRecordMetadata().offset();
            log.info("[CouponIssueProducer] 발행 완료 - " +
                    "requestId={}, userId={}, couponId={}, partition={}, offset={}",
                    request.getRequestId(), userId, couponId, partition, offset);

            // 3. requestId 반환 (클라이언트 추적용)
            return request.getRequestId();

        } catch (TimeoutException e) {
            // Kafka 발행 타임아웃 (5초 내 응답 없음)
            String errorMsg = String.format("Kafka 발행 타임아웃 (5초 초과) - requestId=%s, userId=%s, couponId=%s",
                    request.getRequestId(), userId, couponId);
            log.error("[CouponIssueProducer] {}", errorMsg, e);
            throw new RuntimeException(errorMsg, e);

        } catch (InterruptedException e) {
            // 스레드 인터럽트
            Thread.currentThread().interrupt();
            String errorMsg = String.format("Kafka 발행 중 인터럽트 발생 - requestId=%s, userId=%s, couponId=%s",
                    request.getRequestId(), userId, couponId);
            log.error("[CouponIssueProducer] {}", errorMsg, e);
            throw new RuntimeException(errorMsg, e);

        } catch (Exception e) {
            // Kafka 발행 실패 (네트워크 오류, 브로커 다운 등)
            String errorMsg = String.format("Kafka 발행 실패 - requestId=%s, userId=%s, couponId=%s, error=%s",
                    request.getRequestId(), userId, couponId, e.getMessage());
            log.error("[CouponIssueProducer] {}", errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * 파티션 번호 예측 (테스트/디버깅용)
     *
     * 실제 파티션 결정은 Kafka DefaultPartitioner가 수행
     * Hash(key) % partitionCount → partition
     *
     * ✅ 파티셔닝 키: couponId (userId에서 변경)
     *
     * @param couponId 쿠폰 ID (파티셔닝 키)
     * @param partitionCount 파티션 개수
     * @return 예상 파티션 번호
     */
    public int predictPartition(Long couponId, int partitionCount) {
        String key = String.valueOf(couponId);
        // DefaultPartitioner는 murmur2 해시 사용
        // 여기서는 간단히 hashCode 사용 (정확하지 않을 수 있음)
        return Math.abs(key.hashCode() % partitionCount);
    }
}