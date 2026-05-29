package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.product.ProductService;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.presentation.product.response.ProductDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🔴 Redis 캐시 TTL 검증 테스트
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * 목적:
 * - 캐시의 TTL(Time-To-Live)이 올바르게 설정되었는지 검증
 * - 각 캐시별로 다른 TTL이 적절히 작동하는지 확인
 * - Redis에서 캐시가 자동으로 만료되는지 검증
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 📋 검증 항목
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * 캐시별 TTL 설정 (CacheConfig.java):
 * - productList:      1시간 (3600초)
 * - productDetail:    2시간 (7200초)
 * - couponList:       30분 (1800초)
 * - cartItems:        30분 (1800초)
 * - popularProducts:  1시간 (3600초)
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🧪 테스트 시나리오
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * Test 1: testProductDetail_TTL_Verification()
 * ──────────────────────────────────────────────
 * Given:   상품 상세 조회로 Redis 캐시 저장 (TTL: 2시간)
 * When:    RedisTemplate으로 TTL을 조회
 * Then:    TTL이 2시간(7200초) 범위 내에 설정되었는가?
 *
 * Expected: TTL >= 7190초 && TTL <= 7200초
 * (10초 오차범위: 쿼리 실행 시간 고려)
 *
 * Test 2: testProductList_TTL_Verification()
 * ───────────────────────────────────────────
 * Given:   상품 목록 조회로 Redis 캐시 저장 (TTL: 1시간)
 * When:    RedisTemplate으로 TTL을 조회
 * Then:    TTL이 1시간(3600초) 범위 내에 설정되었는가?
 *
 * Expected: TTL >= 3590초 && TTL <= 3600초
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 💡 Redis TTL 검증 방법
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * Redis에서 TTL을 확인하려면 EXPIRE 커맨드를 사용합니다:
 *
 * ```bash
 * # Redis CLI에서 직접 확인
 * redis-cli
 * > TTL cache:productDetail::1
 * (integer) 7195
 *
 * # Spring RedisTemplate으로 확인
 * Long ttl = redisTemplate.getExpire(cacheKey);
 * // ttl: 남은 시간 (초)
 * // -1: TTL이 설정되지 않음
 * // -2: 키가 존재하지 않음
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 📊 TTL 범위 검증 로직
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * TTL 설정 후 즉시 조회하면:
 * - 이상적: 설정값 (예: 3600초)
 * - 현실적: 설정값 - 쿼리 실행 시간 (예: 3597-3599초)
 *
 * 따라서 범위 검증이 필요합니다:
 * - 최소값: TTL 설정값 - 10초 (쿼리 지연 허용)
 * - 최대값: TTL 설정값 (정확한 값)
 *
 * ```java
 * Long ttl = redisTemplate.getExpire(cacheKey);
 * assertThat(ttl).isGreaterThan(3590);        // 1시간 - 10초
 * assertThat(ttl).isLessThanOrEqualTo(3600);  // 1시간
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🔧 테스트 이점
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 1. 캐시 설정 검증: CacheConfig.java의 TTL 설정이 올바른지 확인
 * 2. Redis 연결 확인: RedisTemplate이 실제 Redis와 통신하는지 확인
 * 3. 성능 예측: TTL을 알면 캐시 갱신 빈도를 예측할 수 있음
 * 4. 메모리 관리: TTL이 너무 길면 메모리 낭비, 너무 짧으면 캐시 효율 저하
 */
@SpringBootTest
@DisplayName("Redis 캐시 TTL 검증 테스트")
class RedisCacheTTLTest extends BaseIntegrationTest {

    // ═══════════════════════════════════════════════════════════════════════
    // 의존성 주입
    // ═══════════════════════════════════════════════════════════════════════

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Long productId;

    // ═══════════════════════════════════════════════════════════════════════
    // 테스트 데이터 준비
    // ═══════════════════════════════════════════════════════════════════════

    @BeforeEach
    void setUp() {
        // Redis 전체 초기화 (테스트 간 상태 오염 방지)
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        // 캐시 초기화
        clearAllCaches();

        // 상품 생성
        Product product = Product.builder()
                .productName("TTL 검증용 상품")
                .description("캐시 TTL 검증 테스트")
                .price(75000L)
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
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            productId = product.getProductId();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ✅ 테스트 1: ProductDetail - TTL 검증 (2시간)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("상품 상세 조회 - ProductDetail 캐시 TTL 검증 (2시간)")
    void testProductDetail_TTL_Verification() {
        // ─────────────────────────────────────────────────────────────────
        // Step 1: 캐시 저장
        // ─────────────────────────────────────────────────────────────────
        // Given: 상품 상세 조회를 통해 Redis 캐시 저장
        ProductDetailResponse result = productService.getProductDetail(productId);

        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(productId);
        System.out.println("✅ 상품 상세 조회 완료: " + productId);

        // ─────────────────────────────────────────────────────────────────
        // Step 2: Redis 캐시 확인
        // ─────────────────────────────────────────────────────────────────
        // When: Redis에서 캐시 키를 확인
        String cacheKey = "productDetail::" + productId;
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

        assertThat(cachedValue)
                .as("Redis에 캐시가 저장되었는가?")
                .isNotNull();
        System.out.println("✅ Redis 캐시 존재: " + cacheKey);

        // ─────────────────────────────────────────────────────────────────
        // Step 3: TTL 검증
        // ─────────────────────────────────────────────────────────────────
        // When: RedisTemplate으로 TTL 조회
        Long ttl = redisTemplate.getExpire(cacheKey);

        // ─────────────────────────────────────────────────────────────────
        // 📊 TTL 검증 로직
        // ─────────────────────────────────────────────────────────────────
        // productDetail 캐시의 TTL은 2시간 = 7200초
        // 쿼리 실행 시간(~10초)를 고려한 범위 검증

        System.out.println("\n📊 TTL 검증 결과:");
        System.out.println("   캐시 키: " + cacheKey);
        System.out.println("   현재 TTL: " + ttl + "초");
        System.out.println("   기대값: 7200초 (2시간)");

        assertThat(ttl)
                .as("TTL이 설정되어야 함 (음수가 아님)")
                .isGreaterThan(0);

        assertThat(ttl)
                .as("TTL이 2시간(7200초) 범위 내에 있어야 함 (최소: 7190초)")
                .isGreaterThan(7190);

        assertThat(ttl)
                .as("TTL이 설정값 이하여야 함 (최대: 7200초)")
                .isLessThanOrEqualTo(7200);

        // ─────────────────────────────────────────────────────────────────
        // ✨ 결론
        // ─────────────────────────────────────────────────────────────────
        System.out.println("✅ ProductDetail 캐시의 TTL이 올바르게 설정되었습니다!");
        System.out.println("✅ 캐시는 약 " + ttl + "초 후에 자동으로 만료됩니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ✅ 테스트 2: ProductList - TTL 검증 (1시간)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("상품 목록 조회 - ProductList 캐시 TTL 검증 (1시간)")
    void testProductList_TTL_Verification() {
        // ─────────────────────────────────────────────────────────────────
        // Step 1: 캐시 저장
        // ─────────────────────────────────────────────────────────────────
        // Given: 상품 목록 조회를 통해 Redis 캐시 저장
        var result = productService.getProductList(0, 10, "created_at,desc");

        assertThat(result).isNotNull();
        System.out.println("✅ 상품 목록 조회 완료");

        // ─────────────────────────────────────────────────────────────────
        // Step 2: Redis 캐시 확인
        // ─────────────────────────────────────────────────────────────────
        // When: Redis에서 캐시 키를 확인
        String cacheKey = "productList::list_0_10_created_at,desc";
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

        assertThat(cachedValue)
                .as("Redis에 캐시가 저장되었는가?")
                .isNotNull();
        System.out.println("✅ Redis 캐시 존재: " + cacheKey);

        // ─────────────────────────────────────────────────────────────────
        // Step 3: TTL 검증
        // ─────────────────────────────────────────────────────────────────
        // When: RedisTemplate으로 TTL 조회
        Long ttl = redisTemplate.getExpire(cacheKey);

        // ─────────────────────────────────────────────────────────────────
        // 📊 TTL 검증 로직
        // ─────────────────────────────────────────────────────────────────
        // productList 캐시의 TTL은 1시간 = 3600초

        System.out.println("\n📊 TTL 검증 결과:");
        System.out.println("   캐시 키: " + cacheKey);
        System.out.println("   현재 TTL: " + ttl + "초");
        System.out.println("   기대값: 3600초 (1시간)");

        assertThat(ttl)
                .as("TTL이 설정되어야 함 (음수가 아님)")
                .isGreaterThan(0);

        assertThat(ttl)
                .as("TTL이 1시간(3600초) 범위 내에 있어야 함 (최소: 3590초)")
                .isGreaterThan(3590);

        assertThat(ttl)
                .as("TTL이 설정값 이하여야 함 (최대: 3600초)")
                .isLessThanOrEqualTo(3600);

        // ─────────────────────────────────────────────────────────────────
        // ✨ 결론
        // ─────────────────────────────────────────────────────────────────
        System.out.println("✅ ProductList 캐시의 TTL이 올바르게 설정되었습니다!");
        System.out.println("✅ 캐시는 약 " + ttl + "초 후에 자동으로 만료됩니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ✅ 테스트 3: TTL이 설정되지 않은 캐시 오류 검증
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("캐시 TTL 설정 실패 - 오류 검증")
    void testCacheTTL_NotSet_ErrorDetection() {
        // ─────────────────────────────────────────────────────────────────
        // Given: 캐시가 저장됨
        // ─────────────────────────────────────────────────────────────────
        productService.getProductDetail(productId);

        // ─────────────────────────────────────────────────────────────────
        // When & Then: TTL이 설정된 캐시 검증
        // ─────────────────────────────────────────────────────────────────
        String cacheKey = "productDetail::" + productId;
        Long ttl = redisTemplate.getExpire(cacheKey);

        System.out.println("\n🔍 TTL 설정 상태 확인:");
        System.out.println("   TTL 값: " + ttl);
        System.out.println("   -1 = TTL 설정 안됨");
        System.out.println("   -2 = 캐시 키가 없음");
        System.out.println("   양수 = TTL 설정됨 (추천)");

        // TTL이 설정되어야 함 (-1은 TTL이 없는 상태)
        assertThat(ttl)
                .as("TTL이 설정되어야 함. -1은 TTL이 설정되지 않음을 의미함")
                .isGreaterThan(-1);

        // TTL이 0이 아님 (캐시가 즉시 만료되지 않음)
        assertThat(ttl)
                .as("TTL이 0보다 커야 함 (캐시가 즉시 만료되면 안됨)")
                .isGreaterThan(0);

        System.out.println("✅ TTL이 올바르게 설정되었습니다!");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 헬퍼 메서드
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 모든 캐시를 초기화하는 헬퍼 메서드
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
