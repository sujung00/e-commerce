package com.hhplus.ecommerce.application.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CouponRequest - 쿠폰 발급 요청 데이터
 *
 * 역할:
 * - Redis 큐에 저장되는 요청 데이터
 * - JSON 직렬화/역렬화를 위한 구조
 * - 요청 상태 추적 (PENDING, COMPLETED, FAILED, RETRY)
 *
 * 흐름:
 * 1. Controller → enqueueCouponRequest() → CouponRequest 생성
 * 2. Redis 큐에 JSON으로 저장
 * 3. BackgroundWorker → Redis 큐에서 꺼내기
 * 4. JSON → CouponRequest 역직렬화
 * 5. DB 처리 후 상태 업데이트
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRequest {

    /**
     * 요청의 고유 ID
     * - UUID로 생성
     * - 클라이언트가 폴링할 때 사용
     * - 멱등성 보장용
     */
    private String requestId;

    /**
     * 쿠폰을 요청한 사용자 ID
     */
    private Long userId;

    /**
     * 발급받을 쿠폰 ID
     */
    private Long couponId;

    /**
     * 요청 시간 (timestamp)
     * - System.currentTimeMillis()로 설정
     * - 큐 대기 시간 계산용
     */
    private Long timestamp;

    /**
     * 요청 상태
     * - PENDING: 처리 대기 중
     * - COMPLETED: 발급 완료
     * - FAILED: 발급 불가 (비즈니스 로직 실패)
     * - RETRY: 시스템 오류로 재시도 대기
     */
    private String status;

    /**
     * 에러 메시지 (선택사항)
     * - status가 FAILED 또는 RETRY일 때만 설정
     */
    private String errorMessage;

    /**
     * 큐에서의 위치 (선택사항)
     * - 성능을 위해 계산하지 않음 (선택사항)
     */
    @Builder.Default
    private Integer queuePosition = null;

    /**
     * 재시도 횟수
     * - 0: 첫 시도
     * - 1, 2, 3: 재시도
     * - MAX_RETRIES(3)를 초과하면 DLQ로 이동
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 생성 시간 기록
     */
    public static CouponRequest of(Long userId, Long couponId) {
        return CouponRequest.builder()
            .requestId(java.util.UUID.randomUUID().toString())
            .userId(userId)
            .couponId(couponId)
            .timestamp(System.currentTimeMillis())
            .status("PENDING")
            .build();
    }

    /**
     * 상태 업데이트
     */
    public void markCompleted() {
        this.status = "COMPLETED";
        this.errorMessage = null;
    }

    public void markFailed(String reason) {
        this.status = "FAILED";
        this.errorMessage = reason;
    }

    public void markRetry(String reason) {
        this.status = "RETRY";
        this.errorMessage = reason;
    }

    /**
     * 재시도 횟수 증가
     */
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    /**
     * 재시도 가능 여부 확인
     * @param maxRetries 최대 재시도 횟수
     * @return true면 재시도 가능, false면 DLQ로 이동해야 함
     */
    public boolean isRetryable(int maxRetries) {
        return this.retryCount < maxRetries;
    }

    /**
     * 대기 시간 계산 (밀리초)
     */
    public long getWaitingTimeMs() {
        return System.currentTimeMillis() - this.timestamp;
    }

    @Override
    public String toString() {
        return String.format(
            "CouponRequest(requestId=%s, userId=%d, couponId=%d, status=%s, waiting=%dms)",
            requestId, userId, couponId, status, getWaitingTimeMs()
        );
    }
}
