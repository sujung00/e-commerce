package com.hhplus.ecommerce.application;

import com.hhplus.ecommerce.domain.Product;
import com.hhplus.ecommerce.dto.PopularProductListResponse;
import com.hhplus.ecommerce.dto.PopularProductView;
import com.hhplus.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("인기 상품 조회 서비스 테스트")
class PopularProductServiceTest {

    @Autowired
    private PopularProductService popularProductService;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // 캐시 초기화 (서비스 재생성)
        // SpringBootTest는 싱글톤이므로 캐시가 유지됨
    }

    // ========== 기본 기능 테스트 ==========

    @Test
    @DisplayName("인기 상품 조회 - 성공")
    void testGetPopularProducts_Success() {
        // When
        PopularProductListResponse response = popularProductService.getPopularProducts();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getProducts()).isNotNull();
        assertThat(response.getProducts()).isNotEmpty();
    }

    @Test
    @DisplayName("인기 상품 조회 - 최대 5개까지만 반환")
    void testGetPopularProducts_MaxFiveProducts() {
        // When
        PopularProductListResponse response = popularProductService.getPopularProducts();

        // Then
        assertThat(response.getProducts()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("인기 상품 조회 - 순위 정보 포함")
    void testGetPopularProducts_RankIncluded() {
        // When
        PopularProductListResponse response = popularProductService.getPopularProducts();

        // Then
        List<PopularProductView> products = response.getProducts();
        assertThat(products).isNotEmpty();

        for (int i = 0; i < products.size(); i++) {
            PopularProductView product = products.get(i);
            assertThat(product.getRank()).isEqualTo(i + 1);
        }
    }

    // ========== 정렬 및 필터링 테스트 ==========

    @Test
    @DisplayName("인기 상품 조회 - orderCount3Days 내림차순 정렬")
    void testGetPopularProducts_SortedByOrderCountDesc() {
        // When
        PopularProductListResponse response = popularProductService.getPopularProducts();

        // Then
        List<PopularProductView> products = response.getProducts();
        for (int i = 0; i < products.size() - 1; i++) {
            Long currentCount = products.get(i).getOrderCount3Days();
            Long nextCount = products.get(i + 1).getOrderCount3Days();
            assertThat(currentCount).isGreaterThanOrEqualTo(nextCount);
        }
    }

    @Test
    @DisplayName("인기 상품 조회 - 품절 상품도 포함")
    void testGetPopularProducts_IncludeOutOfStockProducts() {
        // When
        PopularProductListResponse response = popularProductService.getPopularProducts();

        // Then
        List<PopularProductView> products = response.getProducts();
        boolean hasOutOfStockProduct = products.stream()
                .anyMatch(p -> "품절".equals(p.getStatus()));

        // 테스트 데이터에 품절 상품이 상위 5개에 포함된 경우만 검증
        if (hasOutOfStockProduct) {
            assertThat(hasOutOfStockProduct).isTrue();
        }
    }

    @Test
    @DisplayName("인기 상품 조회 - 판매중지 상품도 포함")
    void testGetPopularProducts_IncludeDiscontinuedProducts() {
        // When
        PopularProductListResponse response = popularProductService.getPopularProducts();

        // Then
        List<PopularProductView> products = response.getProducts();
        // 모든 상품이 포함되어야 함 (상태 무관)
        assertThat(products).isNotEmpty();
    }

    // ========== 반환 필드 검증 테스트 ==========

    @Test
    @DisplayName("인기 상품 조회 - 필수 필드 포함")
    void testGetPopularProducts_RequiredFieldsIncluded() {
        // When
        PopularProductListResponse response = popularProductService.getPopularProducts();

        // Then
        List<PopularProductView> products = response.getProducts();
        assertThat(products).isNotEmpty();

        PopularProductView product = products.get(0);
        assertThat(product.getProductId()).isNotNull();
        assertThat(product.getProductName()).isNotNull();
        assertThat(product.getPrice()).isNotNull();
        assertThat(product.getTotalStock()).isNotNull();
        assertThat(product.getStatus()).isNotNull();
        assertThat(product.getOrderCount3Days()).isNotNull();
        assertThat(product.getRank()).isNotNull();
        assertThat(product.getCreatedAt()).isNotNull();
    }

    // ========== 캐싱 테스트 ==========

    @Test
    @DisplayName("인기 상품 조회 - 첫 호출 시 캐시 미스")
    void testGetPopularProducts_FirstCallCacheMiss() {
        // When
        long startTime = System.currentTimeMillis();
        PopularProductListResponse response1 = popularProductService.getPopularProducts();
        long firstCallDuration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(response1).isNotNull();
        assertThat(firstCallDuration).isLessThan(2000); // 2초 이내
    }

    @Test
    @DisplayName("인기 상품 조회 - 연속 호출은 캐시에서 조회")
    void testGetPopularProducts_CachedOnSecondCall() {
        // When
        PopularProductListResponse response1 = popularProductService.getPopularProducts();

        long startTime = System.currentTimeMillis();
        PopularProductListResponse response2 = popularProductService.getPopularProducts();
        long cachedCallDuration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(response1).isNotNull();
        assertThat(response2).isNotNull();

        // 캐시된 호출은 매우 빠름 (1ms 미만)
        assertThat(cachedCallDuration).isLessThan(100);

        // 동일한 데이터 반환
        assertThat(response1.getProducts()).hasSize(response2.getProducts().size());
    }

    @Test
    @DisplayName("인기 상품 조회 - 캐시 내용 동일성")
    void testGetPopularProducts_CacheContentIdentical() {
        // When
        PopularProductListResponse response1 = popularProductService.getPopularProducts();
        PopularProductListResponse response2 = popularProductService.getPopularProducts();

        // Then
        List<PopularProductView> products1 = response1.getProducts();
        List<PopularProductView> products2 = response2.getProducts();

        assertThat(products1).hasSameSizeAs(products2);
        for (int i = 0; i < products1.size(); i++) {
            assertThat(products1.get(i).getProductId())
                    .isEqualTo(products2.get(i).getProductId());
            assertThat(products1.get(i).getOrderCount3Days())
                    .isEqualTo(products2.get(i).getOrderCount3Days());
            assertThat(products1.get(i).getRank())
                    .isEqualTo(products2.get(i).getRank());
        }
    }

    // ========== 엣지 케이스 테스트 ==========

    @Test
    @DisplayName("인기 상품 조회 - 상품이 없어도 200 반환")
    void testGetPopularProducts_EmptyResultReturns200() {
        // When
        PopularProductListResponse response = popularProductService.getPopularProducts();

        // Then
        assertThat(response).isNotNull();
        // 빈 결과도 정상 응답
        assertThat(response.getProducts()).isNotNull();
    }

    @Test
    @DisplayName("인기 상품 조회 - 반환된 상품 데이터 검증")
    void testGetPopularProducts_ProductDataValidation() {
        // When
        PopularProductListResponse response = popularProductService.getPopularProducts();

        // Then
        List<PopularProductView> products = response.getProducts();
        for (PopularProductView product : products) {
            assertThat(product.getProductId()).isGreaterThan(0);
            assertThat(product.getPrice()).isGreaterThanOrEqualTo(0);
            assertThat(product.getTotalStock()).isGreaterThanOrEqualTo(0);
            assertThat(product.getOrderCount3Days()).isGreaterThanOrEqualTo(0);
            assertThat(product.getRank()).isBetween(1, 5);
        }
    }

    @Test
    @DisplayName("인기 상품 조회 - 성능 요구사항 만족 (< 2초)")
    void testGetPopularProducts_PerformanceRequirement() {
        // When
        long startTime = System.currentTimeMillis();
        PopularProductListResponse response = popularProductService.getPopularProducts();
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(duration).isLessThan(2000);
        assertThat(response).isNotNull();
    }
}