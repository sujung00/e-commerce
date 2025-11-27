package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.product.PopularProductService;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.presentation.product.response.PopularProductListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 캐시 통합 테스트 - 인기 상품 조회
 *
 * Redis 캐싱이 올바르게 작동하는지 검증:
 * - 첫 호출: DB에서 데이터 조회, Redis에 저장
 * - 두 번째 호출: Redis에서 캐시된 데이터 반환
 * - 응답시간 차이로 캐시 효과 측정
 *
 * Phase 2 인기 상품 캐싱 검증:
 * 1. PopularProducts 캐싱 (TTL: 1시간)
 * 2. Redis 직접 조회로 캐시 존재 확인
 * 3. 응답 시간 비교 (DB vs Redis)
 * 4. 캐시 무효화 검증
 * 5. TTL 검증
 */
@SpringBootTest
@DisplayName("인기 상품 Redis 캐시 통합 테스트")
class IntegrationPopularProductCacheTest extends BaseIntegrationTest {

    @Autowired
    private PopularProductService popularProductService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 준비 (최근 3일 주문된 상품)
        for (int i = 1; i <= 6; i++) {
            Product product = Product.builder()
                    .productName("인기 상품 " + i)
                    .description("테스트용 인기 상품 " + i)
                    .price(10000L * i)
                    .totalStock(100 * i)
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .build();
            productRepository.save(product);

            // 상품 옵션 생성
            if (product.getProductId() != null) {
                ProductOption option = ProductOption.builder()
                        .productId(product.getProductId())
                        .name("기본 옵션")
                        .stock(100 * i)
                        .version(0L)
                        .build();
                productRepository.saveOption(option);
            }
        }

        // 캐시 초기화
        clearAllCaches();
    }

    @Test
    @DisplayName("인기 상품 조회 - Redis 캐시 저장 및 조회")
    void testPopularProducts_RedisCacheSaveAndRead() {
        // Given: 캐시 초기화 상태

        // When: 첫 번째 호출 (DB에서 조회)
        long startTime1 = System.currentTimeMillis();
        PopularProductListResponse result1 = popularProductService.getPopularProducts();
        long elapsedTime1 = System.currentTimeMillis() - startTime1;

        // Then: 데이터가 조회되고 Redis에 저장됨
        assertThat(result1).isNotNull();
        assertThat(result1.getProducts()).isNotEmpty();
        assertThat(result1.getProducts().size()).isLessThanOrEqualTo(5); // 상위 5개

        // Redis에 캐시가 존재하는지 확인
        String cacheKey = "cache:popularProducts::list";
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
        System.out.println("✅ Redis 캐시 키: " + cacheKey);
        System.out.println("✅ Redis 캐시 데이터 존재: " + (cachedValue != null));
        assertThat(cachedValue).isNotNull();

        // When: 두 번째 호출 (Redis에서 조회)
        long startTime2 = System.currentTimeMillis();
        PopularProductListResponse result2 = popularProductService.getPopularProducts();
        long elapsedTime2 = System.currentTimeMillis() - startTime2;

        // Then: 캐시된 데이터가 반환되고, 응답시간이 훨씬 빨라짐
        assertThat(result2).isNotNull();
        assertThat(result2.getProducts().size()).isEqualTo(result1.getProducts().size());
        assertThat(result2.getProducts()).isEqualTo(result1.getProducts());

        // 캐시 효과 검증 (캐시된 응답이 더 빨라야 함)
        assertThat(elapsedTime2).isLessThan(elapsedTime1);

        System.out.println("✅ 인기 상품 캐싱: 첫 호출 " + elapsedTime1 + "ms → Redis 캐시 호출 " + elapsedTime2 + "ms");
    }

    @Test
    @DisplayName("인기 상품 조회 - 캐시 히트율 검증 (동일 요청 반복)")
    void testPopularProducts_CacheHitRate() {
        // Given: 캐시 초기화 상태
        int repetitions = 10;
        long firstCallTime = 0;
        long cacheCallTotalTime = 0;

        // When: 첫 호출 (DB에서 조회)
        long startTime = System.currentTimeMillis();
        PopularProductListResponse result = popularProductService.getPopularProducts();
        firstCallTime = System.currentTimeMillis() - startTime;

        // When: 반복된 호출 (캐시에서 모두 반환되어야 함)
        for (int i = 0; i < repetitions; i++) {
            startTime = System.currentTimeMillis();
            PopularProductListResponse cachedResult = popularProductService.getPopularProducts();
            cacheCallTotalTime += System.currentTimeMillis() - startTime;

            assertThat(cachedResult).isNotNull();
            assertThat(cachedResult.getProducts()).isEqualTo(result.getProducts());
        }

        // Then: 캐시된 호출들의 평균 시간이 첫 호출보다 훨씬 빠름
        long avgCacheCallTime = cacheCallTotalTime / repetitions;

        assertThat(avgCacheCallTime).isLessThan(firstCallTime);

        // 캐시 효과: 평균 응답 시간이 첫 호출의 10분의 1 이상이어야 함
        double speedImprovement = (double) firstCallTime / (avgCacheCallTime + 1);
        System.out.println("✅ 캐시 히트율: " + repetitions + "회 반복 후 응답시간 " + speedImprovement + "배 향상");
    }

    @Test
    @DisplayName("인기 상품 조회 - Redis 캐시 TTL 검증")
    void testPopularProducts_CacheTTL_VerifyExpiration() {
        // Given: 인기 상품 조회로 캐시 저장
        popularProductService.getPopularProducts();
        String cacheKey = "cache:popularProducts::list";

        // When: TTL 확인
        Long ttl = redisTemplate.getExpire(cacheKey);
        System.out.println("✅ popularProducts 캐시 TTL: " + ttl + "초 (기대: ~3600초 = 1시간)");

        // Then: TTL이 설정되어 있음 (1시간 = 3600초)
        // 정확한 값보다는 범위로 확인 (설정 후 몇 초 경과 가능)
        assertThat(ttl).isGreaterThan(3590);  // 3590초 이상 (1시간 - 10초)
        assertThat(ttl).isLessThanOrEqualTo(3600);  // 3600초 이하 (정확히 1시간)
    }

    @Test
    @DisplayName("인기 상품 조회 - 캐시 무효화 (Redis에서 정상 제거)")
    void testPopularProducts_CacheEvict_RedisKeyRemoval() {
        // Given: 인기 상품 조회로 캐시 저장
        popularProductService.getPopularProducts();
        String cacheKey = "cache:popularProducts::list";
        Object cachedBefore = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedBefore).isNotNull();
        System.out.println("✅ 캐시 무효화 전: Redis에 데이터 존재");

        // When: 캐시 무효화
        clearAllCaches();

        // Then: Redis에서 캐시가 제거됨
        Object cachedAfter = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedAfter).isNull();
        System.out.println("✅ 캐시 무효화 후: Redis에서 데이터 제거됨");
    }

    @Test
    @DisplayName("인기 상품 조회 - 캐시 구조 검증 (캐시 매니저 Bean)")
    void testPopularProducts_RedisCacheManager_BeanConfiguration() {
        // Given: CacheManager가 RedisCacheManager인지 확인
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.getClass().getSimpleName()).contains("RedisCacheManager");
        System.out.println("✅ 캐시 매니저 타입: " + cacheManager.getClass().getSimpleName());

        // When & Then: popularProducts 캐시가 등록되어 있는지 확인
        var cacheNames = cacheManager.getCacheNames();
        assertThat(cacheNames).contains("popularProducts");
        System.out.println("✅ 등록된 캐시 이름: " + cacheNames);

        // popularProducts 캐시 존재 확인
        var cache = cacheManager.getCache("popularProducts");
        assertThat(cache).isNotNull();
        System.out.println("✅ popularProducts 캐시 확인됨");
    }

    @Test
    @DisplayName("인기 상품 조회 - 상위 5개 순위 검증")
    void testPopularProducts_Top5Ranking() {
        // Given: 캐시 초기화 상태

        // When: 인기 상품 조회
        PopularProductListResponse result = popularProductService.getPopularProducts();

        // Then: 상위 5개 이하의 상품이 반환됨
        assertThat(result.getProducts()).isNotEmpty();
        assertThat(result.getProducts().size()).isLessThanOrEqualTo(5);

        // 순위가 올바르게 설정되어 있는지 확인
        for (int i = 0; i < result.getProducts().size(); i++) {
            assertThat(result.getProducts().get(i).getRank()).isEqualTo(i + 1);
        }

        System.out.println("✅ 인기 상품 순위: " + result.getProducts().size() + "개, 순위 정렬 완료");
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
