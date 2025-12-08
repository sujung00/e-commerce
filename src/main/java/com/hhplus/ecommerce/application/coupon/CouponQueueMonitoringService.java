package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.application.coupon.dto.CouponRequest;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;
import com.hhplus.ecommerce.infrastructure.constants.RetryConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CouponQueueMonitoringService - 큐 모니터링 및 DLQ 관리
 *
 * 책임:
 * 1. DLQ(Dead Letter Queue) 아이템 조회
 * 2. DLQ 아이템 상태 확인
 * 3. DLQ 아이템의 수동 재처리
 * 4. 큐 통계 및 상태 모니터링
 *
 * 사용 사례:
 * - 관리자: DLQ 아이템을 조회하여 실패 원인 파악
 * - 운영팀: 재시도 가능한 항목을 수동으로 다시 처리
 * - 모니터링: 시스템 상태 점검 (DLQ 크기 등)
 */
@Service
public class CouponQueueMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(CouponQueueMonitoringService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CouponService couponService;
    private final CouponQueueService couponQueueService;

    public CouponQueueMonitoringService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CouponService couponService,
            CouponQueueService couponQueueService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.couponService = couponService;
        this.couponQueueService = couponQueueService;
    }

    /**
     * DLQ의 모든 아이템 조회
     *
     * @return DLQ에 있는 모든 요청 목록
     */
    public List<DLQItem> getAllDLQItems() {
        List<DLQItem> items = new ArrayList<>();
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();

        try {
            // DLQ의 모든 아이템 조회 (LRANGE로 읽기만 함, 제거 안 함)
            List<String> jsonList = redisTemplate.opsForList().range(dlqKey, 0, -1);

            if (jsonList == null || jsonList.isEmpty()) {
                log.info("[DLQ Monitor] DLQ가 비어있음");
                return items;
            }

            for (int i = 0; i < jsonList.size(); i++) {
                try {
                    String json = jsonList.get(i);
                    CouponRequest request = objectMapper.readValue(json, CouponRequest.class);

                    DLQItem item = DLQItem.builder()
                            .index(i)
                            .requestId(request.getRequestId())
                            .userId(request.getUserId())
                            .couponId(request.getCouponId())
                            .retryCount(request.getRetryCount())
                            .maxRetries(RetryConstants.COUPON_ISSUANCE_MAX_RETRIES)
                            .status(request.getStatus())
                            .errorMessage(request.getErrorMessage())
                            .waitingTimeMs(request.getWaitingTimeMs())
                            .rawJson(json)
                            .build();

                    items.add(item);

                } catch (Exception e) {
                    log.warn("[DLQ Monitor] DLQ 아이템 파싱 오류 (인덱스={})", i, e);
                }
            }

            log.info("[DLQ Monitor] DLQ 조회 완료: {}개", items.size());

        } catch (Exception e) {
            log.error("[DLQ Monitor] DLQ 조회 실패", e);
        }

        return items;
    }

    /**
     * DLQ에 있는 특정 요청 조회
     *
     * @param requestId 요청 ID
     * @return DLQ 아이템 (없으면 Optional.empty())
     */
    public Optional<DLQItem> getDLQItemByRequestId(String requestId) {
        List<DLQItem> items = getAllDLQItems();
        return items.stream()
                .filter(item -> item.getRequestId().equals(requestId))
                .findFirst();
    }

    /**
     * DLQ 아이템을 재시도 큐로 이동 (수동 재처리)
     *
     * @param requestId 요청 ID
     * @return 성공 여부
     */
    public boolean moveToRetryQueue(String requestId) {
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();
        String retryQueueKey = RedisKeyType.QUEUE_COUPON_RETRY.getKey();

        try {
            // DLQ에서 해당 요청 찾기
            List<String> jsonList = redisTemplate.opsForList().range(dlqKey, 0, -1);

            if (jsonList == null) {
                log.warn("[DLQ Monitor] DLQ가 비어있음");
                return false;
            }

            int index = -1;
            String targetJson = null;

            for (int i = 0; i < jsonList.size(); i++) {
                try {
                    String json = jsonList.get(i);
                    CouponRequest request = objectMapper.readValue(json, CouponRequest.class);
                    if (request.getRequestId().equals(requestId)) {
                        index = i;
                        targetJson = json;
                        break;
                    }
                } catch (Exception e) {
                    log.debug("[DLQ Monitor] JSON 파싱 오류 (인덱스={})", i);
                }
            }

            if (index == -1 || targetJson == null) {
                log.warn("[DLQ Monitor] DLQ에서 요청을 찾을 수 없음: requestId={}", requestId);
                return false;
            }

            // DLQ에서 제거
            redisTemplate.opsForList().remove(dlqKey, 1, targetJson);

            // 재시도 큐에 추가 (재시도 카운트는 0으로 리셋)
            try {
                CouponRequest request = objectMapper.readValue(targetJson, CouponRequest.class);
                request.setRetryCount(0);  // 수동 재처리는 카운트 리셋
                request.setStatus("RETRY");
                String updatedJson = objectMapper.writeValueAsString(request);
                redisTemplate.opsForList().leftPush(retryQueueKey, updatedJson);

                log.info("[DLQ Monitor] DLQ 아이템을 재시도 큐로 이동: requestId={}", requestId);
                return true;

            } catch (Exception e) {
                log.error("[DLQ Monitor] 재시도 큐에 추가하던 중 오류 발생", e);
                // 원래 아이템을 DLQ에 다시 추가
                redisTemplate.opsForList().leftPush(dlqKey, targetJson);
                return false;
            }

        } catch (Exception e) {
            log.error("[DLQ Monitor] DLQ 아이템 이동 실패: requestId={}", requestId, e);
            return false;
        }
    }

    /**
     * DLQ 아이템 삭제 (처리 불가능한 경우)
     *
     * @param requestId 요청 ID
     * @return 성공 여부
     */
    public boolean removeDLQItem(String requestId) {
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();

        try {
            List<String> jsonList = redisTemplate.opsForList().range(dlqKey, 0, -1);

            if (jsonList == null) {
                return false;
            }

            for (int i = 0; i < jsonList.size(); i++) {
                try {
                    String json = jsonList.get(i);
                    CouponRequest request = objectMapper.readValue(json, CouponRequest.class);
                    if (request.getRequestId().equals(requestId)) {
                        redisTemplate.opsForList().remove(dlqKey, 1, json);
                        log.info("[DLQ Monitor] DLQ 아이템 삭제: requestId={}", requestId);
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("[DLQ Monitor] JSON 파싱 오류 (인덱스={})", i);
                }
            }

            log.warn("[DLQ Monitor] DLQ에서 요청을 찾을 수 없음: requestId={}", requestId);
            return false;

        } catch (Exception e) {
            log.error("[DLQ Monitor] DLQ 아이템 삭제 실패: requestId={}", requestId, e);
            return false;
        }
    }

    /**
     * 큐 상태 조회 (모니터링용)
     *
     * @return 큐 상태 정보
     */
    public QueueStatusInfo getQueueStatus() {
        try {
            String pendingKey = RedisKeyType.QUEUE_COUPON_PENDING.getKey();
            String retryKey = RedisKeyType.QUEUE_COUPON_RETRY.getKey();
            String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();

            Long pendingCount = redisTemplate.opsForList().size(pendingKey);
            Long retryCount = redisTemplate.opsForList().size(retryKey);
            Long dlqCount = redisTemplate.opsForList().size(dlqKey);

            return QueueStatusInfo.builder()
                    .pendingCount(pendingCount != null ? pendingCount : 0)
                    .retryCount(retryCount != null ? retryCount : 0)
                    .dlqCount(dlqCount != null ? dlqCount : 0)
                    .totalCount((pendingCount != null ? pendingCount : 0) +
                               (retryCount != null ? retryCount : 0) +
                               (dlqCount != null ? dlqCount : 0))
                    .build();

        } catch (Exception e) {
            log.error("[DLQ Monitor] 큐 상태 조회 실패", e);
            return QueueStatusInfo.builder()
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * DLQ 아이템 DTO
     */
    @lombok.Getter
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DLQItem {
        private Integer index;              // DLQ 내 인덱스
        private String requestId;           // 요청 ID
        private Long userId;                // 사용자 ID
        private Long couponId;              // 쿠폰 ID
        private Integer retryCount;         // 재시도 횟수
        private Integer maxRetries;         // 최대 재시도 횟수
        private String status;              // 상태
        private String errorMessage;        // 에러 메시지
        private Long waitingTimeMs;         // 대기 시간 (ms)
        private String rawJson;             // 원본 JSON

        @Override
        public String toString() {
            return String.format(
                    "DLQItem(index=%d, requestId=%s, userId=%d, couponId=%d, retryCount=%d/%d, " +
                    "status=%s, waitingTime=%dms, error=%s)",
                    index, requestId, userId, couponId, retryCount, maxRetries,
                    status, waitingTimeMs, errorMessage);
        }
    }

    /**
     * 큐 상태 정보 DTO
     */
    @lombok.Getter
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QueueStatusInfo {
        private Long pendingCount;          // 처리 대기 중인 요청
        private Long retryCount;            // 재시도 대기 중인 요청
        private Long dlqCount;              // DLQ에 있는 요청
        private Long totalCount;            // 전체 요청
        private String error;               // 에러 메시지

        public boolean isHealthy() {
            return error == null && dlqCount <= 10;  // DLQ가 10개 이하이면 정상
        }

        @Override
        public String toString() {
            return String.format(
                    "QueueStatus(pending=%d, retry=%d, dlq=%d, total=%d, healthy=%s)",
                    pendingCount, retryCount, dlqCount, totalCount, isHealthy());
        }
    }
}
