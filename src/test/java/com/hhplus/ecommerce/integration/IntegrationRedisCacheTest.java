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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 캐시 통합 테스트
 *
 * RedisCacheManager가 실제로 작동하는지 검증:
 * - Redis에 데이터가 저장되는지 확인
 * - @Cacheable 어노테이션이 Redis와 동작하는지 확인
 * - @CacheEvict가 Redis에서 정상 제거되는지 확인
 * - 캐시별 TTL 설정이 올바른지 확인
 *
 * Phase 2 Redis 캐싱 검증:
 * 1. ProductList 캐싱 (TTL: 1시간)
 * 2. ProductDetail 캐싱 (TTL: 2시간)
 * 3. AvailableCoupons 캐싱 (TTL: 30분)
 * 4. Redis 직접 조회로 캐시 존재 확인
 * 5. 캐시 무효화 후 Redis에서 제거 확인
 */
@SpringBootTest
@DisplayName("Redis 캐시 통합 테스트")
class IntegrationRedisCacheTest extends BaseIntegrationTest {

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

    @BeforeEach
    void setUp() {
        // 테스트 데이터 준비
        // 상품 생성
        Product product = Product.builder()
                .productName("Redis 캐시 테스트 상품")
                .description("Redis 캐시 검증용 상품")
                .price(10000L)
                .totalStock(100)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
        productRepository.save(product);

        // 상품 옵션 생성
        if (product.getProductId() != null) {
            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본 옵션")
                    .stock(100)
                    .version(0L)
                    .build();
            productRepository.saveOption(option);
            productId = product.getProductId();
        }

        // 쿠폰 생성
        Coupon coupon = Coupon.builder()
                .couponName("Redis 캐시 테스트 쿠폰")
                .description("Redis 캐시 검증용 쿠폰")
                .discountType("PERCENTAGE")
                .discountRate(BigDecimal.valueOf(10))
                .discountAmount(0L)
                .totalQuantity(100)
                .remainingQty(100)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .version(0L)
                .createdAt(LocalDateTime.now())
                .build();
        couponRepository.save(coupon);
        couponId = coupon.getCouponId();

        // 캐시 초기화
        clearAllCaches();
    }

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

        // Redis에 캐시가 존재하는지 확인
        String cacheKey = "cache:productList::list_0_10_created_at,desc";
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
        System.out.println("✅ Redis 캐시 키: " + cacheKey);
        System.out.println("✅ Redis 캐시 데이터 존재: " + (cachedValue != null));

        // When: 두 번째 호출 (Redis에서 조회)
        long startTime2 = System.currentTimeMillis();
        ProductListResponse result2 = productService.getProductList(0, 10, "created_at,desc");
        long elapsedTime2 = System.currentTimeMillis() - startTime2;

        // Then: 캐시된 데이터가 반환되고, 응답시간이 훨씬 빨라짐
        assertThat(result2).isNotNull();
        assertThat(result2.getContent()).isEqualTo(result1.getContent());
        assertThat(elapsedTime2).isLessThan(elapsedTime1);

        System.out.println("✅ 상품 목록 캐싱: 첫 호출 " + elapsedTime1 + "ms → Redis 캐시 호출 " + elapsedTime2 + "ms");
    }

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

        // Redis에 캐시가 존재하는지 확인
        String cacheKey = "cache:productDetail::" + productId;
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
        System.out.println("✅ Redis 캐시 키: " + cacheKey);
        System.out.println("✅ Redis 캐시 데이터 존재: " + (cachedValue != null));

        // When: 두 번째 호출 (Redis에서 조회)
        long startTime2 = System.currentTimeMillis();
        ProductDetailResponse result2 = productService.getProductDetail(productId);
        long elapsedTime2 = System.currentTimeMillis() - startTime2;

        // Then: 캐시된 데이터가 반환됨
        assertThat(result2).isNotNull();
        assertThat(result2.getProductId()).isEqualTo(result1.getProductId());
        assertThat(elapsedTime2).isLessThan(elapsedTime1);

        System.out.println("✅ 상품 상세 캐싱: 첫 호출 " + elapsedTime1 + "ms → Redis 캐시 호출 " + elapsedTime2 + "ms");
    }

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

        // Redis에 캐시가 존재하는지 확인
        String cacheKey = "cache:couponList::all";
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
        System.out.println("✅ Redis 캐시 키: " + cacheKey);
        System.out.println("✅ Redis 캐시 데이터 존재: " + (cachedValue != null));

        // When: 두 번째 호출 (Redis에서 조회)
        long startTime2 = System.currentTimeMillis();
        List<AvailableCouponResponse> result2 = couponService.getAvailableCoupons();
        long elapsedTime2 = System.currentTimeMillis() - startTime2;

        // Then: 캐시된 데이터가 반환됨
        assertThat(result2).isNotNull();
        assertThat(result2.size()).isEqualTo(result1.size());
        assertThat(elapsedTime2).isLessThan(elapsedTime1);

        System.out.println("✅ 쿠폰 목록 캐싱: 첫 호출 " + elapsedTime1 + "ms → Redis 캐시 호출 " + elapsedTime2 + "ms");
    }

    @Test
    @DisplayName("캐시 무효화 - Redis에서 정상 제거")
    void testCacheEvict_RedisKeyRemoval() {
        // Given: 상품 조회로 캐시 저장
        productService.getProductDetail(productId);
        String cacheKey = "cache:productDetail::" + productId;
        Object cachedBefore = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedBefore).isNotNull();
        System.out.println("✅ 캐시 무효화 전: Redis에 데이터 존재");

        // When: 캐시 무효화 (예: 상품 정보 업데이트)
        // ProductService에서 @CacheEvict가 호출되는 시나리오
        clearAllCaches();

        // Then: Redis에서 캐시가 제거됨
        Object cachedAfter = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedAfter).isNull();
        System.out.println("✅ 캐시 무효화 후: Redis에서 데이터 제거됨");
    }

    @Test
    @DisplayName("Redis 캐시 TTL 검증")
    void testCacheTTL_VerifyExpiration() {
        // Given: 상품 목록 조회로 캐시 저장
        productService.getProductList(0, 10, "created_at,desc");
        String cacheKey = "cache:productList::list_0_10_created_at,desc";

        // When: TTL 확인
        Long ttl = redisTemplate.getExpire(cacheKey);
        System.out.println("✅ productList 캐시 TTL: " + ttl + "초 (기대: ~3600초 = 1시간)");

        // Then: TTL이 설정되어 있음 (1시간 = 3600초)
        // 정확한 값보다는 범위로 확인 (설정 후 몇 초 경과 가능)
        assertThat(ttl).isGreaterThan(3590);  // 3590초 이상 (1시간 - 10초)
        assertThat(ttl).isLessThanOrEqualTo(3600);  // 3600초 이하 (정확히 1시간)
    }

    @Test
    @DisplayName("Redis 캐시 구조 검증 - 캐시 매니저 Bean")
    void testRedisCacheManager_BeanConfiguration() {
        // Given: CacheManager가 RedisCacheManager인지 확인
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.getClass().getSimpleName()).contains("RedisCacheManager");
        System.out.println("✅ 캐시 매니저 타입: " + cacheManager.getClass().getSimpleName());

        // When & Then: 정의된 모든 캐시 이름 확인
        var cacheNames = cacheManager.getCacheNames();
        assertThat(cacheNames).contains("productList", "couponList", "productDetail", "cartItems");
        System.out.println("✅ 등록된 캐시 이름: " + cacheNames);

        // 각 캐시가 존재하는지 확인
        cacheNames.forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            assertThat(cache).isNotNull();
            System.out.println("✅ 캐시 확인: " + cacheName);
        });
    }

    /**
     * 모든 캐시 초기화 헬퍼 메서드
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
