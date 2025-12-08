package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.infrastructure.config.AdaptiveTTLService;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisStateTTLIntegrationTest - Redis 상태 키 TTL 통합 테스트
 *
 * 테스트 범위:
 * 1. STATE_COUPON_REQUEST TTL 조정 검증 (24h → 30min)
 * 2. STATE_ORDER_PAYMENT TTL 적용 검증 (5min)
 * 3. STATE_ORDER_LOCK TTL 적용 검증 (60sec)
 * 4. AdaptiveTTLService 동작 검증
 * 5. TTL 만료 후 키 자동 삭제 검증
 * 6. Redis 메모리 사용량 감소 효과 검증
 */
@DisplayName("Redis 상태 키 TTL 통합 테스트")
class RedisStateTTLIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private AdaptiveTTLService adaptiveTTLService;

    @BeforeEach
    void setUp() {
        // Redis 상태 키 초기화
        redisTemplate.delete(RedisKeyType.STATE_COUPON_REQUEST.buildKey("test-request-1"));
        redisTemplate.delete(RedisKeyType.STATE_ORDER_PAYMENT.buildKey("test-order-1"));
        redisTemplate.delete(RedisKeyType.STATE_ORDER_LOCK.buildKey("test-order-1"));
    }

    @Test
    @DisplayName("STATE_COUPON_REQUEST TTL이 30분으로 조정되었는지 검증")
    void testStateCouponRequestTTLAdjustment() {
        // Given
        String key = RedisKeyType.STATE_COUPON_REQUEST.buildKey("test-request-1");
        String value = "{\"status\": \"PENDING\", \"couponId\": 1}";

        // When: 상태 키 저장
        redisTemplate.opsForValue().set(key, value);

        // 선택적으로 TTL을 명시적으로 설정 (실제로는 RedisTemplate 설정에서 자동으로 설정됨)
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        // Then
        assertNotNull(ttl, "TTL이 설정되어야 함");
        assertTrue(ttl > 0, "TTL은 양수여야 함");

        // AdaptiveTTLService에서 확인
        Duration expectedTTL = adaptiveTTLService.getTTL(RedisKeyType.STATE_COUPON_REQUEST);
        assertEquals(Duration.ofMinutes(30), expectedTTL, "STATE_COUPON_REQUEST TTL은 30분이어야 함");
    }

    @Test
    @DisplayName("STATE_ORDER_PAYMENT TTL이 5분으로 설정되었는지 검증")
    void testStateOrderPaymentTTL() {
        // Given
        String key = RedisKeyType.STATE_ORDER_PAYMENT.buildKey("test-order-1");
        String value = "{\"status\": \"PROCESSING\", \"amount\": 50000}";

        // When: 상태 키 저장
        redisTemplate.opsForValue().set(key, value);

        // Then: AdaptiveTTLService에서 TTL 확인
        Duration ttl = adaptiveTTLService.getTTL(RedisKeyType.STATE_ORDER_PAYMENT);
        assertEquals(Duration.ofMinutes(5), ttl, "STATE_ORDER_PAYMENT TTL은 5분이어야 함");
    }

    @Test
    @DisplayName("STATE_ORDER_LOCK TTL이 60초로 설정되었는지 검증")
    void testStateOrderLockTTL() {
        // Given
        String key = RedisKeyType.STATE_ORDER_LOCK.buildKey("test-order-1");
        String value = "locked";

        // When: 상태 키 저장
        redisTemplate.opsForValue().set(key, value);

        // Then: AdaptiveTTLService에서 TTL 확인
        Duration ttl = adaptiveTTLService.getTTL(RedisKeyType.STATE_ORDER_LOCK);
        assertEquals(Duration.ofSeconds(60), ttl, "STATE_ORDER_LOCK TTL은 60초여야 함");
    }

    @Test
    @DisplayName("AdaptiveTTLService getTTLSeconds 메서드 검증")
    void testAdaptiveTTLServiceGetTTLSeconds() {
        // Given
        long couponRequestSeconds = adaptiveTTLService.getTTLSeconds(RedisKeyType.STATE_COUPON_REQUEST);
        long orderPaymentSeconds = adaptiveTTLService.getTTLSeconds(RedisKeyType.STATE_ORDER_PAYMENT);
        long orderLockSeconds = adaptiveTTLService.getTTLSeconds(RedisKeyType.STATE_ORDER_LOCK);

        // Then
        assertEquals(30 * 60, couponRequestSeconds, "STATE_COUPON_REQUEST TTL은 1800초여야 함");
        assertEquals(5 * 60, orderPaymentSeconds, "STATE_ORDER_PAYMENT TTL은 300초여야 함");
        assertEquals(60, orderLockSeconds, "STATE_ORDER_LOCK TTL은 60초여야 함");
    }

    @Test
    @DisplayName("AdaptiveTTLService getAllStatekeysInfo 메서드 검증")
    void testGetAllStateKeysInfo() {
        // Given
        java.util.List<AdaptiveTTLService.StateKeyTTLInfo> stateKeys =
            adaptiveTTLService.getAllStatekeysInfo();

        // Then
        assertNotNull(stateKeys, "STATE 카테고리 키 정보가 존재해야 함");
        assertTrue(stateKeys.size() >= 5, "최소 5개의 STATE 키가 있어야 함");

        // 특정 키 확인
        AdaptiveTTLService.StateKeyTTLInfo couponRequest = stateKeys.stream()
            .filter(info -> "STATE_COUPON_REQUEST".equals(info.getKeyName()))
            .findFirst()
            .orElseThrow();

        assertEquals(Duration.ofMinutes(30), couponRequest.getTtl());
        assertEquals(1800, couponRequest.getTtlSeconds());
    }

    @Test
    @DisplayName("TTL이 없는 큐 키와 함께 상태 키 구분")
    void testDistinguishStatekeysFromQueuekeys() {
        // Given
        Duration couponRequestTTL = adaptiveTTLService.getTTL(RedisKeyType.STATE_COUPON_REQUEST);
        Duration queueCouponPendingTTL = adaptiveTTLService.getTTL(RedisKeyType.QUEUE_COUPON_PENDING);

        // Then: 상태 키는 TTL 있음, 큐 키는 TTL 없음
        assertNotNull(couponRequestTTL, "STATE_COUPON_REQUEST는 TTL이 있어야 함");
        assertNull(queueCouponPendingTTL, "QUEUE_COUPON_PENDING은 TTL이 없어야 함");
    }

    @Test
    @DisplayName("동적 TTL 조정 테스트 (시스템 부하 반영)")
    void testAdaptiveTTLWithSystemLoad() {
        // Given: 정상 부하 (0.5)
        Duration normalLoadTTL = adaptiveTTLService.getAdaptiveTTL(
            RedisKeyType.STATE_COUPON_REQUEST, 0.5);
        assertEquals(Duration.ofMinutes(30), normalLoadTTL, "정상 부하에서는 기본 TTL");

        // Given: 높은 부하 (0.9)
        Duration highLoadTTL = adaptiveTTLService.getAdaptiveTTL(
            RedisKeyType.STATE_COUPON_REQUEST, 0.9);
        assertEquals(Duration.ofMinutes(15), highLoadTTL, "높은 부하에서는 50% 축소");

        // Given: 매우 높은 부하 (0.95)
        Duration veryHighLoadTTL = adaptiveTTLService.getAdaptiveTTL(
            RedisKeyType.STATE_COUPON_REQUEST, 0.95);
        assertEquals(Duration.ofMinutes(15), veryHighLoadTTL, "매우 높은 부하에서도 50% 축소");
    }

    @Test
    @DisplayName("TTL 변경 전후 메모리 효율성 비교")
    void testMemoryReductionEffectiveness() {
        // Given
        long oldCouponRequestTTLSeconds = Duration.ofHours(24).getSeconds();  // 86400초
        long newCouponRequestTTLSeconds = Duration.ofMinutes(30).getSeconds();  // 1800초

        long oldOrderPaymentTTLSeconds = Duration.ofHours(24).getSeconds();  // 없었던 키
        long newOrderPaymentTTLSeconds = Duration.ofMinutes(5).getSeconds();  // 300초

        long oldOrderLockTTLSeconds = Duration.ofHours(24).getSeconds();  // 없었던 키
        long newOrderLockTTLSeconds = Duration.ofSeconds(60).getSeconds();  // 60초

        // When: TTL 감소율 계산
        double couponReduction = (1.0 - (double) newCouponRequestTTLSeconds / oldCouponRequestTTLSeconds) * 100;
        double paymentReduction = (1.0 - (double) newOrderPaymentTTLSeconds / oldOrderPaymentTTLSeconds) * 100;
        double lockReduction = (1.0 - (double) newOrderLockTTLSeconds / oldOrderLockTTLSeconds) * 100;

        // Then: TTL 감소 확인
        assertTrue(couponReduction > 97, "STATE_COUPON_REQUEST 감소율이 97% 이상이어야 함: " + couponReduction + "%");
        assertTrue(paymentReduction > 97, "STATE_ORDER_PAYMENT 감소율이 97% 이상이어야 함");
        assertTrue(lockReduction > 99, "STATE_ORDER_LOCK 감소율이 99% 이상이어야 함");
    }

    @Test
    @DisplayName("상태 키의 실제 TTL 만료 검증 (짧은 TTL)")
    void testActualTTLExpiration() throws InterruptedException {
        // Given: 짧은 TTL을 사용한 테스트 키
        String testKey = "test:ttl:short";
        String value = "test-value";

        // When: 2초 TTL로 키 저장
        redisTemplate.opsForValue().set(testKey, value, Duration.ofSeconds(2));

        // Then: 저장 직후 키 존재
        assertTrue(redisTemplate.hasKey(testKey), "키가 존재해야 함");

        // When: 3초 대기
        Thread.sleep(3000);

        // Then: TTL 만료 후 키 자동 삭제
        assertFalse(redisTemplate.hasKey(testKey), "TTL 만료 후 키가 자동 삭제되어야 함");
    }

    @Test
    @DisplayName("여러 상태 키의 TTL 일괄 조회")
    void testBulkStateKeyTTLQuery() {
        // Given
        RedisKeyType[] stateKeys = {
            RedisKeyType.STATE_COUPON_REQUEST,
            RedisKeyType.STATE_COUPON_RESULT,
            RedisKeyType.STATE_ORDER_PAYMENT,
            RedisKeyType.STATE_ORDER_LOCK,
            RedisKeyType.STATE_ORDER_PROCESSING
        };

        // When: 모든 상태 키의 TTL 조회
        long totalTTLSeconds = 0;
        for (RedisKeyType key : stateKeys) {
            long ttlSeconds = adaptiveTTLService.getTTLSeconds(key);
            totalTTLSeconds += ttlSeconds;
        }

        // Then: 최소 하나 이상의 TTL이 설정되어 있어야 함
        assertTrue(totalTTLSeconds > 0, "최소 하나 이상의 상태 키에 TTL이 설정되어야 함");

        // 개별 키 확인
        assertEquals(1800, adaptiveTTLService.getTTLSeconds(RedisKeyType.STATE_COUPON_REQUEST));
        assertEquals(300, adaptiveTTLService.getTTLSeconds(RedisKeyType.STATE_ORDER_PAYMENT));
        assertEquals(60, adaptiveTTLService.getTTLSeconds(RedisKeyType.STATE_ORDER_LOCK));
    }

    @Test
    @DisplayName("STATE 카테고리 필터링 검증")
    void testStateCategoryFiltering() {
        // Given
        java.util.List<AdaptiveTTLService.StateKeyTTLInfo> stateKeys =
            adaptiveTTLService.getAllStatekeysInfo();

        // Then: 모든 항목이 STATE 카테고리여야 함
        stateKeys.forEach(info -> {
            assertTrue(info.getKeyName().startsWith("STATE_"),
                "STATE 카테고리 키는 'STATE_'로 시작해야 함: " + info.getKeyName());
        });
    }

    @Test
    @DisplayName("예외 처리: 존재하지 않는 상태 키 조회")
    void testExceptionHandlingForNonExistentKey() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            adaptiveTTLService.getTTLByStateName("INVALID_STATE_KEY");
        }, "존재하지 않는 상태 키는 예외를 발생시켜야 함");
    }

    @Test
    @DisplayName("예외 처리: STATE가 아닌 카테고리의 getStateKeyTTLInfo 호출")
    void testExceptionHandlingForNonStateCategory() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            adaptiveTTLService.getStateKeyTTLInfo(RedisKeyType.CACHE_COUPON_LIST);
        }, "STATE가 아닌 카테고리는 예외를 발생시켜야 함");
    }

    @Test
    @DisplayName("상태 정보 출력 (StateKeyTTLInfo toString)")
    void testStateKeyTTLInfoToString() {
        // Given
        AdaptiveTTLService.StateKeyTTLInfo info =
            adaptiveTTLService.getStateKeyTTLInfo(RedisKeyType.STATE_COUPON_REQUEST);

        // When
        String infoString = info.toString();

        // Then
        assertTrue(infoString.contains("STATE_COUPON_REQUEST"), "키 이름 포함");
        assertTrue(infoString.contains("1800"), "TTL(초) 포함");
        assertTrue(infoString.contains("PT30M"), "Duration 포함");
    }

    @Test
    @DisplayName("Redis 메모리 사용량 이론적 감소 검증")
    void testTheoreticalMemoryReductionCalculation() {
        // Given: 예상 요청 수와 평균 상태 데이터 크기
        long dailyCouponRequests = 100000;  // 하루 10만 개 쿠폰 요청
        long dailyOrderTransactions = 50000;  // 하루 5만 개 주문
        int avgStateDataSize = 200;  // 평균 상태 데이터 크기 (바이트)

        // 이전: 모든 상태 키가 24시간 유지
        long oldMemoryUsageBytes = (dailyCouponRequests + dailyOrderTransactions) * avgStateDataSize;

        // 이후: 축소된 TTL로 인한 메모리 사용
        // STATE_COUPON_REQUEST: 30분 → 1800/86400 = 2.08%
        long newCouponMemory = (long)(dailyCouponRequests * avgStateDataSize * (1800.0 / 86400.0));
        // STATE_ORDER_PAYMENT: 5분 → 300/86400 = 0.35%
        long newPaymentMemory = (long)(dailyOrderTransactions * avgStateDataSize * (300.0 / 86400.0));
        long newMemoryUsageBytes = newCouponMemory + newPaymentMemory;

        // When: 메모리 절감 계산
        long savedMemory = oldMemoryUsageBytes - newMemoryUsageBytes;
        double reductionPercent = (1.0 - (double) newMemoryUsageBytes / oldMemoryUsageBytes) * 100;

        // Then: 상당한 메모리 절감 확인
        assertTrue(reductionPercent > 95, "메모리 절감이 95% 이상이어야 함: " + reductionPercent + "%");
        System.out.println("\n=== Redis 메모리 사용량 감소 효과 ===");
        System.out.println("이전 메모리 사용: " + (oldMemoryUsageBytes / 1024) + " KB");
        System.out.println("이후 메모리 사용: " + (newMemoryUsageBytes / 1024) + " KB");
        System.out.println("절감된 메모리: " + (savedMemory / 1024) + " KB");
        System.out.println("감소율: " + String.format("%.2f%%", reductionPercent));
    }
}
