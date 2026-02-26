package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.application.coupon.dto.CouponRequest;
import com.hhplus.ecommerce.application.coupon.dto.CouponIssueStatusResponse;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.CouponIssueAsyncResponse;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;
import com.hhplus.ecommerce.infrastructure.constants.RetryConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CouponQueueService - Redis 기반 쿠폰 발급 큐 관리
 *
 * 책임:
 * 1. 쿠폰 발급 요청을 Redis 큐에 추가 (FIFO)
 * 2. 백그라운드 워커로 FIFO 순서대로 처리
 * 3. 요청 상태 저장 및 조회
 *
 * 아키텍처:
 * - Queue: LPUSH(요청 추가) + RPOP(순서대로 처리) = FIFO 보장
 * - State: 각 요청의 처리 상태 저장 (PENDING → COMPLETED/FAILED/RETRY)
 * - Result: 발급 완료 후 결과 저장
 *
 * 흐름:
 * 1. CouponController.issueCoupon()
 *    → enqueueCouponRequest() → requestId 반환 (< 10ms)
 * 2. 백그라운드 워커 processCouponQueue()
 *    → RPOP으로 큐에서 꺼내기 (FIFO)
 *    → CouponService.issueCouponWithLock() (DB 처리)
 *    → 상태 업데이트 (COMPLETED/FAILED/RETRY)
 * 3. CouponController.getIssueStatus()
 *    → getRequestStatus() → 상태 조회
 *
 * 성능 특징:
 * - 큐 추가: O(1) ~ 1ms
 * - 상태 저장: O(1) ~ 1ms
 * - 처리: 배치로 초당 ~100개
 * - 선착순: FIFO 보장, 공정성 보장
 */
@Service
public class CouponQueueService {

    private static final Logger log = LoggerFactory.getLogger(CouponQueueService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CouponService couponService;

    public CouponQueueService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CouponService couponService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.couponService = couponService;
    }

    /**
     * 쿠폰 발급 (비동기, FIFO 보장)
     *
     * 컨트롤러에서 이동된 비즈니스 로직:
     * 1. 기본 입력 검증
     * 2. 캐시에서 쿠폰 존재 여부 확인
     * 3. Redis 큐에 요청 저장
     * 4. 응답 DTO 생성
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 비동기 발급 응답 (requestId 포함)
     */
    public CouponIssueAsyncResponse issueCouponAsync(Long userId, Long couponId) {
        // 1. 기본 입력 검증
        if (userId == null || userId <= 0) {
            log.warn("[CouponQueueService] 잘못된 사용자 ID: userId={}", userId);
            throw new IllegalArgumentException("유효하지 않은 사용자 ID입니다");
        }
        if (couponId == null || couponId <= 0) {
            log.warn("[CouponQueueService] 잘못된 쿠폰 ID: couponId={}", couponId);
            throw new IllegalArgumentException("유효하지 않은 쿠폰 ID입니다");
        }

        // 2. 캐시에서 쿠폰 존재 여부 빠르게 확인
        AvailableCouponResponse couponFromCache = couponService.getAvailableCouponFromCache(couponId);
        if (couponFromCache == null) {
            log.warn("[CouponQueueService] 쿠폰을 찾을 수 없음: couponId={}", couponId);
            throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다");
        }

        // 3. Redis 큐에 요청 추가
        String requestId = enqueueCouponRequest(userId, couponId);

        // 4. 202 Accepted 응답 생성
        return CouponIssueAsyncResponse.builder()
                .requestId(requestId)
                .status("PENDING")
                .message("쿠폰 발급 요청이 접수되었습니다. requestId로 상태를 확인하세요.")
                .build();
    }

    /**
     * 쿠폰 발급 요청을 Redis 큐에 추가 (내부 메서드)
     *
     * 동작:
     * 1. CouponRequest 객체 생성
     * 2. JSON으로 직렬화
     * 3. Redis LPUSH로 큐에 추가
     * 4. 상태를 별도 키에 저장 (조회용)
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 요청의 고유 ID (requestId)
     */
    private String enqueueCouponRequest(Long userId, Long couponId) {
        CouponRequest request = CouponRequest.of(userId, couponId);

        try {
            String json = objectMapper.writeValueAsString(request);

            // 1. 큐에 요청 추가 (LPUSH)
            String queueKey = RedisKeyType.QUEUE_COUPON_PENDING.getKey();
            redisTemplate.opsForList().leftPush(queueKey, json);

            // 2. 상태 저장 (조회용)
            String stateKey = RedisKeyType.STATE_COUPON_REQUEST
                .buildKey(request.getRequestId());
            Duration ttl = RedisKeyType.STATE_COUPON_REQUEST.getTtl();
            redisTemplate.opsForValue().set(stateKey, json, ttl);

            log.info("[CouponQueue] 요청 추가: requestId={}, userId={}, couponId={}, queueKey={}",
                    request.getRequestId(), userId, couponId, queueKey);

            return request.getRequestId();

        } catch (Exception e) {
            log.error("[CouponQueue] 큐 추가 실패: userId={}, couponId={}", userId, couponId, e);
            throw new RuntimeException("쿠폰 발급 요청 등록 실패", e);
        }
    }

    /**
     * 백그라운드 워커: Redis 큐의 요청을 처리
     *
     * 실행:
     * - @Scheduled(fixedRate = 10)으로 10ms마다 실행
     * - 배치: 한 번에 최대 10개씩 처리
     * - RPOP으로 FIFO 순서 보장
     *
     * 처리 흐름:
     * 1. Redis 큐에서 요청 꺼내기 (RPOP)
     * 2. JSON → CouponRequest 역직렬화
     * 3. DB 처리 (CouponService.issueCouponWithLock)
     * 4. 결과 저장 및 상태 업데이트
     *
     * 에러 처리:
     * - IllegalArgumentException (쿠폰 소진, 기간 만료): FAILED로 기록
     * - Exception (시스템 오류): 재시도 큐로 이동
     */
    @Scheduled(fixedRate = 10)
    public void processCouponQueue() {
        String queueKey = RedisKeyType.QUEUE_COUPON_PENDING.getKey();
        int processedCount = 0;
        int maxBatchSize = 10;  // 한 번에 최대 10개 처리

        while (processedCount < maxBatchSize) {
            String json = redisTemplate.opsForList().rightPop(queueKey);

            if (json == null) {
                // 큐가 비었으면 대기
                break;
            }

            try {
                CouponRequest request = objectMapper.readValue(json, CouponRequest.class);

                log.debug("[Worker] 쿠폰 발급 처리 시작: requestId={}, userId={}, couponId={}",
                        request.getRequestId(), request.getUserId(), request.getCouponId());

                // DB 처리
                IssueCouponResponse response = couponService.issueCouponWithLock(
                    request.getUserId(),
                    request.getCouponId()
                );

                // 결과 저장
                saveResult(request.getRequestId(), response, "COMPLETED", null);

                log.info("[Worker] 쿠폰 발급 완료: requestId={}, couponId={}, discountAmount={}",
                        request.getRequestId(), response.getCouponId(), response.getDiscountAmount());

                processedCount++;

            } catch (IllegalArgumentException e) {
                // 비즈니스 로직 오류 (재시도 X)
                try {
                    CouponRequest request = objectMapper.readValue(json, CouponRequest.class);
                    saveResult(request.getRequestId(), null, "FAILED", e.getMessage());
                    log.warn("[Worker] 발급 불가: requestId={}, reason={}",
                        ((CouponRequest) objectMapper.readValue(json, CouponRequest.class)).getRequestId(),
                        e.getMessage());
                } catch (Exception innerE) {
                    log.error("[Worker] 요청 파싱 오류", innerE);
                }

            } catch (Exception e) {
                // 시스템 오류 (재시도 O)
                try {
                    CouponRequest request = objectMapper.readValue(json, CouponRequest.class);
                    redisTemplate.opsForList().leftPush(
                        RedisKeyType.QUEUE_COUPON_RETRY.getKey(),
                        json
                    );
                    updateStatus(request.getRequestId(), "RETRY", e.getMessage());
                    log.error("[Worker] 처리 실패, 재시도 큐로 이동: requestId={}, error={}",
                            request.getRequestId(), e.getMessage());
                } catch (Exception innerE) {
                    log.error("[Worker] 오류 처리 중 예외 발생", innerE);
                }
            }
        }

        if (processedCount > 0) {
            log.debug("[Worker] 배치 처리 완료: count={}", processedCount);
        }
    }

    /**
     * 재시도 큐 처리 (무한 반복 방지)
     * - 백그라운드에서 주기적으로 실행
     * - 시스템 오류로 재시도 중인 요청 재처리
     * - MAX_RETRIES(3)를 초과하면 DLQ로 이동
     *
     * 흐름:
     * 1. 재시도 큐에서 요청 꺼내기
     * 2. 재시도 카운트 증가
     * 3. DB 처리 시도
     * 4. 성공 → 결과 저장
     * 5. 비즈니스 오류 → FAILED로 기록
     * 6. 시스템 오류 + 재시도 가능 → 재시도 큐로 다시 추가
     * 7. 시스템 오류 + 재시도 불가능 → DLQ로 이동
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000)  // 1분마다, 30초 후 시작
    public void processRetryQueue() {
        String retryQueueKey = RedisKeyType.QUEUE_COUPON_RETRY.getKey();
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();
        int processedCount = 0;
        int maxRetries = RetryConstants.COUPON_ISSUANCE_MAX_RETRIES;

        while (processedCount < 5) {  // 재시도 큐는 한번에 5개까지만
            String json = redisTemplate.opsForList().rightPop(retryQueueKey);

            if (json == null) break;

            try {
                CouponRequest request = objectMapper.readValue(json, CouponRequest.class);

                // 재시도 횟수 증가
                request.incrementRetryCount();

                log.debug("[Retry Worker] 재시도 처리: requestId={}, userId={}, couponId={}, retryCount={}/{}",
                        request.getRequestId(), request.getUserId(), request.getCouponId(),
                        request.getRetryCount(), maxRetries);

                // DB 처리
                IssueCouponResponse response = couponService.issueCouponWithLock(
                    request.getUserId(),
                    request.getCouponId()
                );

                // 결과 저장
                saveResult(request.getRequestId(), response, "COMPLETED", null);
                log.info("[Retry Worker] 재시도 성공: requestId={}, retryCount={}",
                        request.getRequestId(), request.getRetryCount());

                processedCount++;

            } catch (IllegalArgumentException e) {
                // 비즈니스 로직 오류 (쿠폰 소진, 기간 만료 등) → 다시 실패 (재시도 안 함)
                try {
                    CouponRequest request = objectMapper.readValue(json, CouponRequest.class);
                    saveResult(request.getRequestId(), null, "FAILED", e.getMessage());
                    log.warn("[Retry Worker] 비즈니스 오류로 최종 실패: requestId={}, reason={}",
                            request.getRequestId(), e.getMessage());
                } catch (Exception innerE) {
                    log.error("[Retry Worker] 요청 파싱 오류", innerE);
                }

            } catch (Exception e) {
                // 시스템 오류 → 재시도 가능한지 확인
                try {
                    CouponRequest request = objectMapper.readValue(json, CouponRequest.class);

                    if (request.isRetryable(maxRetries)) {
                        // 재시도 가능 → 다시 재시도 큐에 추가
                        String updatedJson = objectMapper.writeValueAsString(request);
                        redisTemplate.opsForList().leftPush(retryQueueKey, updatedJson);
                        log.warn("[Retry Worker] 시스템 오류, 재시도 큐에 다시 추가: requestId={}, " +
                                 "retryCount={}/{}, error={}",
                                request.getRequestId(), request.getRetryCount(), maxRetries, e.getMessage());
                    } else {
                        // 재시도 불가능 (MAX_RETRIES 초과) → DLQ로 이동
                        String updatedJson = objectMapper.writeValueAsString(request);
                        redisTemplate.opsForList().leftPush(dlqKey, updatedJson);
                        updateStatus(request.getRequestId(), "DLQ",
                                "최대 재시도 횟수(3) 초과: " + e.getMessage());
                        log.error("[Retry Worker] 최대 재시도 횟수 초과, DLQ로 이동: requestId={}, " +
                                 "retryCount={}/{}, error={}",
                                request.getRequestId(), request.getRetryCount(), maxRetries, e.getMessage());
                    }

                } catch (Exception innerE) {
                    log.error("[Retry Worker] 오류 처리 중 예외 발생", innerE);
                }
            }
        }

        if (processedCount > 0) {
            log.info("[Retry Worker] 재시도 배치 완료: count={}", processedCount);
        }
    }

    /**
     * 발급 결과를 Redis에 저장
     *
     * @param requestId 요청 ID
     * @param response 발급 응답 (null 가능)
     * @param status 상태 (COMPLETED, FAILED, RETRY)
     * @param errorMessage 에러 메시지 (선택사항)
     */
    private void saveResult(String requestId,
                           IssueCouponResponse response,
                           String status,
                           String errorMessage) {
        try {
            // 1. 결과 저장 (발급 완료 시)
            if (response != null) {
                String resultJson = objectMapper.writeValueAsString(response);
                String resultKey = RedisKeyType.STATE_COUPON_RESULT.buildKey(requestId);
                Duration ttl = RedisKeyType.STATE_COUPON_RESULT.getTtl();
                redisTemplate.opsForValue().set(resultKey, resultJson, ttl);
            }

            // 2. 상태 업데이트
            updateStatus(requestId, status, errorMessage);

        } catch (Exception e) {
            log.error("[CouponQueue] 결과 저장 실패: requestId={}", requestId, e);
        }
    }

    /**
     * 요청 상태 업데이트
     */
    private void updateStatus(String requestId, String status, String errorMessage) {
        try {
            String stateKey = RedisKeyType.STATE_COUPON_REQUEST.buildKey(requestId);
            String currentJson = redisTemplate.opsForValue().get(stateKey);

            if (currentJson != null) {
                CouponRequest request = objectMapper.readValue(currentJson, CouponRequest.class);
                request.setStatus(status);
                request.setErrorMessage(errorMessage);

                String updatedJson = objectMapper.writeValueAsString(request);
                Duration ttl = RedisKeyType.STATE_COUPON_REQUEST.getTtl();
                redisTemplate.opsForValue().set(stateKey, updatedJson, ttl);

                log.debug("[CouponQueue] 상태 업데이트: requestId={}, status={}", requestId, status);
            }

        } catch (Exception e) {
            log.error("[CouponQueue] 상태 업데이트 실패: requestId={}", requestId, e);
        }
    }

    /**
     * 요청 상태 조회
     *
     * 컨트롤러에서 이동된 비즈니스 로직:
     * 1. 입력 검증
     * 2. Redis에서 상태 조회
     *
     * @param requestId 요청 ID
     * @return 상태 응답 (null이면 요청을 찾을 수 없음)
     */
    public CouponIssueStatusResponse getRequestStatus(String requestId) {
        // 1. 입력 검증
        if (requestId == null || requestId.trim().isEmpty()) {
            log.warn("[CouponQueueService] 잘못된 requestId: {}", requestId);
            throw new IllegalArgumentException("유효하지 않은 요청 ID입니다");
        }

        try {
            String stateJson = redisTemplate.opsForValue()
                .get(RedisKeyType.STATE_COUPON_REQUEST.buildKey(requestId));

            if (stateJson == null) {
                log.debug("[CouponQueue] 요청을 찾을 수 없음: requestId={}", requestId);
                return CouponIssueStatusResponse.notFound(requestId);
            }

            CouponRequest request = objectMapper.readValue(stateJson, CouponRequest.class);

            // 상태별 응답 구성
            if ("COMPLETED".equals(request.getStatus())) {
                String resultJson = redisTemplate.opsForValue()
                    .get(RedisKeyType.STATE_COUPON_RESULT.buildKey(requestId));

                if (resultJson != null) {
                    IssueCouponResponse result = objectMapper
                        .readValue(resultJson, IssueCouponResponse.class);
                    return CouponIssueStatusResponse.completed(requestId, result);
                } else {
                    return CouponIssueStatusResponse.completed(requestId, null);
                }

            } else if ("FAILED".equals(request.getStatus())) {
                return CouponIssueStatusResponse.failed(requestId, request.getErrorMessage());

            } else if ("RETRY".equals(request.getStatus())) {
                return CouponIssueStatusResponse.retry(requestId, request.getErrorMessage());

            } else {
                // PENDING
                return CouponIssueStatusResponse.pending(requestId);
            }

        } catch (Exception e) {
            log.error("[CouponQueue] 상태 조회 실패: requestId={}", requestId, e);
            return CouponIssueStatusResponse.error("상태 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 큐 통계 조회 (모니터링용)
     */
    public QueueStats getQueueStats() {
        try {
            Long pendingSize = redisTemplate.opsForList()
                .size(RedisKeyType.QUEUE_COUPON_PENDING.getKey());
            Long retrySize = redisTemplate.opsForList()
                .size(RedisKeyType.QUEUE_COUPON_RETRY.getKey());

            return QueueStats.builder()
                .pendingCount(pendingSize != null ? pendingSize : 0)
                .retryCount(retrySize != null ? retrySize : 0)
                .build();

        } catch (Exception e) {
            log.error("[CouponQueue] 큐 통계 조회 실패", e);
            return QueueStats.builder()
                .pendingCount(0L)
                .retryCount(0L)
                .error(e.getMessage())
                .build();
        }
    }

    /**
     * 큐 통계 DTO
     */
    @lombok.Getter
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QueueStats {
        private Long pendingCount;
        private Long retryCount;
        private String error;

        public Long getTotalCount() {
            return (pendingCount != null ? pendingCount : 0) +
                   (retryCount != null ? retryCount : 0);
        }
    }
}
