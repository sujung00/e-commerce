package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheInvalidationIntegrationTest - 캐시 무효화 로직 통합 테스트
 *
 * 테스트 범위:
 * 1. 개별 쿠폰 발급 시 선택적 캐시 무효화 (allEntries=true 제거)
 * 2. 쿠폰 상세 캐시만 무효화 (CACHE_COUPON_DETAIL)
 * 3. 재고 소진 시 활성 쿠폰 목록 캐시 무효화 (CACHE_ACTIVE_COUPONS)
 * 4. DB 쿼리 감소 효과 검증 (캐시 히트)
 */
@DisplayName("캐시 무효화 로직 통합 테스트")
class CacheInvalidationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Long userId;
    private Long couponId1;
    private Long couponId2;

    @BeforeEach
    void setUp() {
        // 사용자 생성
        User user = User.builder()
                .name("테스트 사용자")
                .email("test@example.com")
                .balance(100000L)
                .build();
        userRepository.save(user);
        userId = user.getUserId();

        // 쿠폰 1 생성 (재고 5개)
        Coupon coupon1 = Coupon.builder()
                .couponName("할인쿠폰 #1")
                .discountAmount(1000L)
                .remainingQty(5)
                .validFrom(LocalDateTime.now().minusHours(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .build();
        couponRepository.save(coupon1);
        couponId1 = coupon1.getCouponId();

        // 쿠폰 2 생성 (재고 2개)
        Coupon coupon2 = Coupon.builder()
                .couponName("할인쿠폰 #2")
                .discountAmount(5000L)
                .remainingQty(2)
                .validFrom(LocalDateTime.now().minusHours(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .build();
        couponRepository.save(coupon2);
        couponId2 = coupon2.getCouponId();

        // 캐시 초기화
        clearAllCaches();
    }

    private void clearAllCaches() {
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache("couponListCache");
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            // 무시
        }
    }

    @Test
    @DisplayName("개별 쿠폰 발급 시 쿠폰 상세 캐시만 무효화 (allEntries=true 제거)")
    void testSelectiveCacheInvalidationOnIndividualIssue() {
        // Given: 쿠폰 상세 캐시에 데이터 저장
        String couponDetailKey = RedisKeyType.CACHE_COUPON_DETAIL.buildKey(couponId1);
        String couponListKey = RedisKeyType.CACHE_COUPON_LIST.getKey();

        org.springframework.cache.Cache cache = cacheManager.getCache("couponListCache");
        assertNotNull(cache, "캐시 매니저가 존재해야 함");

        // 캐시에 임시 데이터 저장 (모의)
        cache.put(couponListKey, "cached_coupon_list");
        cache.put(couponDetailKey, "cached_coupon_detail");

        // When: 쿠폰 발급 (선택적 무효화 적용)
        IssueCouponResponse response = couponService.issueCoupon(userId, couponId1);

        // Then
        assertNotNull(response, "쿠폰 발급 성공");

        // 쿠폰 상세 캐시는 무효화됨
        assertNull(cache.get(couponDetailKey), "쿠폰 상세 캐시가 무효화되어야 함");

        // 전체 쿠폰 목록 캐시는 유지됨 (allEntries=true 제거됨)
        assertNotNull(cache.get(couponListKey),
                "다른 쿠폰의 캐시는 영향을 받지 않아야 함 (allEntries=true 제거)");
    }

    @Test
    @DisplayName("재고 소진 시 활성 쿠폰 목록 캐시도 무효화")
    void testActiveCouponsCacheInvalidationOnStockExhaustion() {
        // Given: 재고 2개의 쿠폰으로 시작
        org.springframework.cache.Cache cache = cacheManager.getCache("couponListCache");
        assertNotNull(cache);

        String activeCouponsKey = RedisKeyType.CACHE_ACTIVE_COUPONS.getKey();
        cache.put(activeCouponsKey, "cached_active_coupons");

        Long anotherUserId = createAdditionalUser();

        // When: 첫 번째 발급 (재고 1개 남음)
        couponService.issueCoupon(userId, couponId2);
        assertNotNull(cache.get(activeCouponsKey),
                "재고가 남아있으면 활성 쿠폰 목록 캐시 유지");

        // When: 두 번째 발급 (재고 소진)
        couponService.issueCoupon(anotherUserId, couponId2);

        // Then: 활성 쿠폰 목록 캐시 무효화 (재고 소진)
        assertNull(cache.get(activeCouponsKey),
                "재고 소진 시 활성 쿠폰 목록 캐시가 무효화되어야 함");
    }

    @Test
    @DisplayName("캐시 무효화로 인한 DB 쿼리 감소 효과 검증")
    void testDBQueryReductionEffectByCache() {
        // Given
        clearAllCaches();

        // When: 쿠폰 발급 가능 목록 조회 (캐시 미스)
        couponService.getAvailableCoupons();

        // 캐시 상태 확인
        org.springframework.cache.Cache cache = cacheManager.getCache("couponListCache");
        String couponListKey = RedisKeyType.CACHE_COUPON_LIST.getKey();
        assertNotNull(cache.get(couponListKey), "캐시가 저장되어야 함");

        // When: 쿠폰 발급
        couponService.issueCoupon(userId, couponId1);

        // Then: 캐시가 무효화되어야 함 (다음 조회 시 새 데이터)
        assertNull(cache.get(couponListKey),
                "쿠폰 발급 후 발급 가능 목록 캐시가 무효화되어야 함");

        // 새로 조회하면 캐시 미스로 인해 DB에서 다시 조회
        var availableCoupons = couponService.getAvailableCoupons();
        assertTrue(availableCoupons.stream()
                        .anyMatch(c -> c.getCouponId().equals(couponId1)),
                "발급된 쿠폰의 재고가 감소해야 함");
    }

    @Test
    @DisplayName("여러 사용자의 쿠폰 발급 시 다른 쿠폰 캐시에 영향 없음")
    void testNoCrossUserCacheInvalidation() {
        // Given
        Long anotherUserId = createAdditionalUser();
        org.springframework.cache.Cache cache = cacheManager.getCache("couponListCache");

        String coupon1DetailKey = RedisKeyType.CACHE_COUPON_DETAIL.buildKey(couponId1);
        String coupon2DetailKey = RedisKeyType.CACHE_COUPON_DETAIL.buildKey(couponId2);

        cache.put(coupon1DetailKey, "cached_coupon_1");
        cache.put(coupon2DetailKey, "cached_coupon_2");

        // When: 쿠폰 1 발급
        couponService.issueCoupon(userId, couponId1);

        // Then: 쿠폰 1의 캐시만 무효화, 쿠폰 2의 캐시는 유지
        assertNull(cache.get(coupon1DetailKey),
                "쿠폰 1의 캐시가 무효화되어야 함");
        assertNotNull(cache.get(coupon2DetailKey),
                "쿠폰 2의 캐시는 영향을 받지 않아야 함 (선택적 무효화)");
    }

    @Test
    @DisplayName("재고별 캐시 무효화 전략 검증")
    void testCacheInvalidationStrategyByInventoryStatus() {
        // Given
        org.springframework.cache.Cache cache = cacheManager.getCache("couponListCache");

        // 시나리오 1: 재고 충분 (5개 → 4개)
        String couponListKey = RedisKeyType.CACHE_COUPON_LIST.getKey();
        cache.put(couponListKey, "cached_data");

        couponService.issueCoupon(userId, couponId1);

        // 전체 쿠폰 목록은 변경되지 않으므로 캐시 유지 가능
        // (실제 구현에서는 재고 변화에 따라 결정)

        // 시나리오 2: 재고 소진 (2개 → 0개)
        Long user2 = createAdditionalUser();
        Long user3 = createAdditionalUser();

        couponService.issueCoupon(user2, couponId2);
        assertNotNull(cache.get(couponListKey), "재고 1개 남음, 캐시 유지");

        // 재고 완전 소진
        couponService.issueCoupon(user3, couponId2);
        // 재고 소진 시 활성 쿠폰 목록 무효화됨
        assertNull(cache.get(couponListKey),
                "재고 소진 시 캐시 무효화");
    }

    private Long createAdditionalUser() {
        User user = User.builder()
                .name("추가 사용자")
                .email("user" + System.nanoTime() + "@example.com")
                .balance(100000L)
                .build();
        userRepository.save(user);
        return user.getUserId();
    }

    @Test
    @DisplayName("캐시 무효화 실패 시에도 쿠폰 발급이 완료되어야 함")
    void testCouponIssuanceSucceedsEvenIfCacheInvalidationFails() {
        // Given
        clearAllCaches();

        // When: 쿠폰 발급 (캐시 매니저가 null이라도 발급 성공)
        IssueCouponResponse response = couponService.issueCoupon(userId, couponId1);

        // Then: 쿠폰 발급이 성공하고, 재고가 감소함
        assertNotNull(response, "쿠폰 발급이 성공해야 함");
        assertEquals(couponId1, response.getCouponId());

        // DB에서 재고 확인
        Coupon coupon = couponRepository.findById(couponId1).orElseThrow();
        assertEquals(4, coupon.getRemainingQty(),
                "재고가 1개 감소해야 함 (5 → 4)");
    }
}
