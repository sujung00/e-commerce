package com.hhplus.ecommerce.domain.coupon.event;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CouponIssueRequest - 쿠폰 발급 요청 이벤트 (Kafka 메시지)
 *
 * 역할:
 * - Kafka Topic: coupon.issue.requests로 발행되는 메시지
 * - Producer가 생성하여 발행
 * - Consumer가 수신하여 쿠폰 발급 처리
 *
 * 파티셔닝 전략:
 * - Key: userId
 * - 같은 사용자의 요청은 항상 같은 파티션으로 전달 (순서 보장)
 * - 다른 사용자의 요청은 다른 파티션으로 분산 (병렬 처리)
 *
 * 설계 근거:
 * - userId로 파티셔닝하면 같은 사용자의 요청 순서 보장
 * - 10개 파티션 = 최대 10개 Consumer 병렬 처리
 * - 선착순 보장: 같은 쿠폰에 대한 요청이 여러 파티션으로 분산되어도
 *   Kafka의 메시지 순서(timestamp 기준)로 선착순 보장
 *
 * 메시지 크기:
 * - userId: 8 bytes
 * - couponId: 8 bytes
 * - requestId: ~36 bytes (UUID)
 * - timestamp: ~30 bytes
 * - Total: ~100 bytes/message
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponIssueRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 요청 고유 ID
     * - UUID로 생성
     * - 중복 요청 방지 및 추적용
     */
    private String requestId;

    /**
     * 사용자 ID
     * - Kafka Key로 사용 (파티셔닝)
     * - 같은 userId는 항상 같은 파티션으로 전달
     */
    private Long userId;

    /**
     * 쿠폰 ID
     * - 발급받을 쿠폰
     */
    private Long couponId;

    /**
     * 요청 생성 시각
     * - 선착순 판단 기준
     * - 메시지 순서는 Kafka Broker의 append order로 보장됨
     */
    private LocalDateTime requestedAt;

    /**
     * 재시도 횟수
     * - Consumer에서 처리 실패 시 증가
     * - 최대 재시도 횟수 초과 시 DLQ로 전송
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * CouponIssueRequest 생성 팩토리 메서드
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return CouponIssueRequest
     */
    public static CouponIssueRequest create(Long userId, Long couponId) {
        return CouponIssueRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .userId(userId)
                .couponId(couponId)
                .requestedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    /**
     * 재시도 횟수 증가
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 재시도 가능 여부 확인
     *
     * @param maxRetries 최대 재시도 횟수
     * @return 재시도 가능하면 true
     */
    public boolean isRetryable(int maxRetries) {
        return this.retryCount < maxRetries;
    }
}