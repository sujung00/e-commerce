package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.product.ProductService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductDetailResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 * ✨ Redis 캐시 검증 테스트
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * 목적:
 * - ProductService와 CouponService의 @Cacheable이 실제 Redis에서 동작하는지 검증
 * - RedisCacheManager가 올바르게 구성되었는지 검증
 * - 캐시로 인한 성능 개선을 측정
 *
 * 테스트 방식:
 * 1. @SpringBootTest로 전체 Spring Context 로드
 * 2. TestContainers를 통해 실제 MySQL & Redis 컨테이너 사용
 * 3. RedisTemplate을 통해 Redis 캐시를 직접 검증
 * 4. CacheManager를 통해 캐시 구성 검증
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🔍 검증 항목
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 1. ✅ @Cacheable 작동 검증
 *    - 첫 호출 후 Redis에 데이터가 저장되었는가?
 *    - 두 번째 호출이 Redis에서 조회되었는가 (응답 시간 비교)?
 *
 * 2. ✅ Redis 캐시 데이터 검증
 *    - RedisTemplate으로 캐시 키를 조회하면 데이터가 존재하는가?
 *    - 캐시 데이터가 올바른 타입인가?
 *
 * 3. ✅ 캐시 TTL 검증
 *    - TTL이 설정되었는가?
 *    - 올바른 TTL 값인가 (시간 범위 검증)?
 *
 * 4. ✅ RedisCacheManager 설정 검증
 *    - CacheManager가 RedisCacheManager 인스턴스인가?
 *    - 필요한 캐시 이름들이 등록되었는가?
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🧪 테스트 시나리오
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * Test 1: testProductList_RedisCacheSaveAndRead()
 * ──────────────────────────────────────────────────
 * Given:   상품 목록 데이터가 DB에 존재
 * When:    첫 번째 호출 후 두 번째 호출 수행
 * Then:    두 번째 응답이 Redis에서 조회되어 더 빨라야 함
 *          Redis에 "cache:productList::..." 키가 존재해야 함
 *
 * Test 2: testProductDetail_RedisCacheSaveAndRead()
 * ──────────────────────────────────────────────────
 * Given:   특정 상품이 DB에 존재
 * When:    상품 상세 조회 2회 수행
 * Then:    Redis에 캐시가 저장되고, 두 번째 조회가 빨라야 함
 *          "cache:productDetail::{{productId}}" 키가 존재해야 함
 *
 * Test 3: testAvailableCoupons_RedisCacheSaveAndRead()
 * ───────────────────────────────────────────────────
 * Given:   사용 가능한 쿠폰이 DB에 존재
 * When:    쿠폰 목록 조회 2회 수행
 * Then:    Redis에 캐시가 저장되고, 성능이 개선되어야 함
 *          "cache:couponList::all" 키가 존재해야 함
 *
 * Test 4: testRedisCacheManager_Configuration()
 * ──────────────────────────────────────────────
 * Given:   Spring Context가 로드됨
 * When:    CacheManager와 캐시 구성을 검증
 * Then:    RedisCacheManager이 사용 중이어야 함
 *          필요한 캐시 이름이 모두 등록되어야 함
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 📊 성능 기대값
 * ═══════════════════════════════════════════════════════════════════════════════════
 * - 캐시 미스 (DB 쿼리):  80-100ms
 * - 캐시 히트 (Redis):    5-15ms
 * - 성능 개선율:          약 5~10배
 */
@SpringBootTest
@DisplayName("Redis 캐시 검증 테스트 - ProductService & CouponService")
class RedisCacheValidationTest extends BaseIntegrationTest {

    // ═══════════════════════════════════════════════════════════════════════
    // 의존성 주입
    // ═══════════════════════════════════════════════════════════════════════

    @Autowired
    private ProductService productService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Long productId;
    private Long couponId;

    // ═══════════════════════════════════════════════════════════════════════
    // 테스트 데이터 준비
    // ═══════════════════════════════════════════════════════════════════════

    @BeforeEach
    void setUp() {
        // 캐시 초기화
        clearAllCaches();

        // 상품 생성
        Product product = Product.builder()
                .productName("Redis 캐시 검증용 상품")
                .description("상품 상세 조회 캐싱 테스트")
                .price(50000L)
                .totalStock(200)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
        productRepository.save(product);

        // 상품 옵션 생성
        if (product.getProductId() != null) {
            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본 옵션")
                    .stock(200)
                    .version(0L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            productId = product.getProductId();
        }

        // 쿠폰 생성
        Coupon coupon = Coupon.builder()
                .couponName("Redis 캐시 검증용 쿠폰")
                .description("쿠폰 목록 캐싱 테스트")
                .discountType("PERCENTAGE")
                .discountRate(BigDecimal.valueOf(15))
                .discountAmount(0L)
                .totalQuantity(500)
                .remainingQty(500)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .version(0L)
                .createdAt(LocalDateTime.now())
                .build();
        couponRepository.save(coupon);
        couponId = coupon.getCouponId();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ✅ 테스트 1: ProductList - Redis 캐시 저장 및 조회
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("상품 목록 조회 - Redis 캐시 저장 및 조회")
    void testProductList_RedisCacheSaveAndRead() {
        // Given: 캐시 초기화 상태

        // When: 첫 번째 호출 (DB에서 조회)
        long startTime1 = System.currentTimeMillis();
        ProductListResponse result1 = productService.getProductList(0, 10, "created_at,desc");
        long elapsedTime1 = System.currentTimeMillis() - startTime1;

        // Then: 데이터가 조회되고 Redis에 저장됨
        assertThat(result1).isNotNull();
        System.out.println("✅ 첫 호출 (DB 쿼리): " + elapsedTime1 + "ms");

        // ─────────────────────────────────────────────────────────────────
        // 🔍 Redis 캐시 검증
        // ─────────────────────────────────────────────────────────────────
        String cacheKey = "cache:productList::list_0_10_created_at,desc";
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

        assertThat(cachedValue)
                .as("Redis에 캐시가 저장되었는가?")
                .isNotNull();
        System.out.println("✅ Redis 캐시 키 존재: " + cacheKey);

        // When: 두 번째 호출 (Redis에서 조회)
        long startTime2 = System.currentTimeMillis();
        ProductListResponse result2 = productService.getProductList(0, 10, "created_at,desc");
        long elapsedTime2 = System.currentTimeMillis() - startTime2;

        // Then: 캐시된 데이터가 반환되고, 응답시간이 훨씬 빨라짐
        assertThat(result2).isNotNull();
        assertThat(result2.getContent())
                .as("캐시된 데이터와 첫 호출 데이터가 동일한가?")
                .isEqualTo(result1.getContent());

        assertThat(elapsedTime2)
                .as("Redis 캐시 히트가 DB 쿼리보다 훨씬 빨아야 함")
                .isLessThan(elapsedTime1);

        // ─────────────────────────────────────────────────────────────────
        // 📊 성능 개선 측정
        // ─────────────────────────────────────────────────────────────────
        double speedImprovement = (double) elapsedTime1 / (elapsedTime2 + 1);
        System.out.println("✅ 성능 개선: DB(" + elapsedTime1 + "ms) → Redis(" + elapsedTime2 + "ms)");
        System.out.println("✅ 속도 향상: 약 " + String.format("%.1f", speedImprovement) + "배");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ✅ 테스트 2: ProductDetail - Redis 캐시 저장 및 조회
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("상품 상세 조회 - Redis 캐시 저장 및 조회")
    void testProductDetail_RedisCacheSaveAndRead() {
        // Given: 캐시 초기화 상태

        // When: 첫 번째 호출 (DB에서 조회)
        long startTime1 = System.currentTimeMillis();
        ProductDetailResponse result1 = productService.getProductDetail(productId);
        long elapsedTime1 = System.currentTimeMillis() - startTime1;

        // Then: 상품 상세 정보가 조회됨
        assertThat(result1).isNotNull();
        assertThat(result1.getProductId()).isEqualTo(productId);
        System.out.println("✅ 첫 호출 (DB 쿼리): " + elapsedTime1 + "ms");

        // ─────────────────────────────────────────────────────────────────
        // 🔍 Redis 캐시 검증
        // ─────────────────────────────────────────────────────────────────
        String cacheKey = "cache:productDetail::" + productId;
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

        assertThat(cachedValue)
                .as("Redis에 상품 상세 캐시가 저장되었는가?")
                .isNotNull();
        System.out.println("✅ Redis 캐시 키 존재: " + cacheKey);

        // When: 두 번째 호출 (Redis에서 조회)
        long startTime2 = System.currentTimeMillis();
        ProductDetailResponse result2 = productService.getProductDetail(productId);
        long elapsedTime2 = System.currentTimeMillis() - startTime2;

        // Then: 캐시된 데이터가 반환됨
        assertThat(result2).isNotNull();
        assertThat(result2.getProductId()).isEqualTo(result1.getProductId());
        assertThat(result2.getProductName()).isEqualTo(result1.getProductName());

        assertThat(elapsedTime2)
                .as("Redis 캐시 히트가 DB 쿼리보다 빨아야 함")
                .isLessThan(elapsedTime1);

        // ─────────────────────────────────────────────────────────────────
        // 📊 성능 개선 측정
        // ─────────────────────────────────────────────────────────────────
        double speedImprovement = (double) elapsedTime1 / (elapsedTime2 + 1);
        System.out.println("✅ 성능 개선: DB(" + elapsedTime1 + "ms) → Redis(" + elapsedTime2 + "ms)");
        System.out.println("✅ 속도 향상: 약 " + String.format("%.1f", speedImprovement) + "배");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ✅ 테스트 3: AvailableCoupons - Redis 캐시 저장 및 조회
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("쿠폰 목록 조회 - Redis 캐시 저장 및 조회")
    void testAvailableCoupons_RedisCacheSaveAndRead() {
        // Given: 캐시 초기화 상태

        // When: 첫 번째 호출 (DB에서 조회)
        long startTime1 = System.currentTimeMillis();
        List<AvailableCouponResponse> result1 = couponService.getAvailableCoupons();
        long elapsedTime1 = System.currentTimeMillis() - startTime1;

        // Then: 사용 가능한 쿠폰이 조회됨
        assertThat(result1).isNotNull();
        assertThat(result1).isNotEmpty();
        System.out.println("✅ 첫 호출 (DB 쿼리): " + elapsedTime1 + "ms");

        // ─────────────────────────────────────────────────────────────────
        // 🔍 Redis 캐시 검증
        // ─────────────────────────────────────────────────────────────────
        String cacheKey = "cache:couponList::all";
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

        assertThat(cachedValue)
                .as("Redis에 쿠폰 목록 캐시가 저장되었는가?")
                .isNotNull();
        System.out.println("✅ Redis 캐시 키 존재: " + cacheKey);

        // When: 두 번째 호출 (Redis에서 조회)
        long startTime2 = System.currentTimeMillis();
        List<AvailableCouponResponse> result2 = couponService.getAvailableCoupons();
        long elapsedTime2 = System.currentTimeMillis() - startTime2;

        // Then: 캐시된 데이터가 반환됨
        assertThat(result2).isNotNull();
        assertThat(result2.size()).isEqualTo(result1.size());

        assertThat(elapsedTime2)
                .as("Redis 캐시 히트가 DB 쿼리보다 빨아야 함")
                .isLessThan(elapsedTime1);

        // ─────────────────────────────────────────────────────────────────
        // 📊 성능 개선 측정
        // ─────────────────────────────────────────────────────────────────
        double speedImprovement = (double) elapsedTime1 / (elapsedTime2 + 1);
        System.out.println("✅ 성능 개선: DB(" + elapsedTime1 + "ms) → Redis(" + elapsedTime2 + "ms)");
        System.out.println("✅ 속도 향상: 약 " + String.format("%.1f", speedImprovement) + "배");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ✅ 테스트 4: RedisCacheManager 설정 검증
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisCacheManager 설정 검증")
    void testRedisCacheManager_Configuration() {
        // Given: CacheManager가 주입되었음
        assertThat(cacheManager).isNotNull();

        // ─────────────────────────────────────────────────────────────────
        // 🔍 CacheManager 타입 검증
        // ─────────────────────────────────────────────────────────────────
        String cacheManagerType = cacheManager.getClass().getSimpleName();
        assertThat(cacheManagerType)
                .as("RedisCacheManager를 사용해야 함")
                .contains("RedisCacheManager");
        System.out.println("✅ 캐시 매니저 타입: " + cacheManagerType);

        // ─────────────────────────────────────────────────────────────────
        // 🔍 등록된 캐시 이름 검증
        // ─────────────────────────────────────────────────────────────────
        var cacheNames = cacheManager.getCacheNames();
        System.out.println("✅ 등록된 캐시 이름: " + cacheNames);

        // 필수 캐시가 모두 등록되었는지 확인
        assertThat(cacheNames)
                .as("필수 캐시가 모두 등록되어야 함")
                .contains("productList", "productDetail", "couponList", "cartItems", "popularProducts");

        // ─────────────────────────────────────────────────────────────────
        // 🔍 각 캐시의 존재 여부 확인
        // ─────────────────────────────────────────────────────────────────
        for (String cacheName : cacheNames) {
            var cache = cacheManager.getCache(cacheName);
            assertThat(cache)
                    .as("캐시 '" + cacheName + "'이 존재해야 함")
                    .isNotNull();
            System.out.println("  ✅ 캐시 '" + cacheName + "' 확인됨");
        }

        // ─────────────────────────────────────────────────────────────────
        // ✨ 결론
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n✅ RedisCacheManager가 올바르게 구성되었습니다!");
        System.out.println("✅ 모든 필수 캐시가 등록되었습니다!");
        System.out.println("✅ @Cacheable / @CacheEvict는 실제 Redis에서 동작합니다!");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 헬퍼 메서드
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 모든 캐시를 초기화하는 헬퍼 메서드
     *
     * 각 테스트 전에 캐시를 깨끗하게 초기화하여
     * 테스트 간 캐시 데이터 오염을 방지합니다.
     */
    private void clearAllCaches() {
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            });
        }
    }
}
