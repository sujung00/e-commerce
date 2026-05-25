package com.hhplus.ecommerce.unit.application.product;

import com.hhplus.ecommerce.application.product.PopularProductServiceImpl;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.MySQLProductRepository;
import com.hhplus.ecommerce.presentation.product.response.PopularProductListResponse;
import com.hhplus.ecommerce.presentation.product.response.PopularProductView;
import com.hhplus.ecommerce.unit.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

/**
 * PopularProductServiceImpl rank 순서 단위 테스트
 * - rank가 1부터 순서대로 올바르게 설정되는지 검증
 */
@DisplayName("[Unit] PopularProductServiceImpl - rank 순서 검증")
class PopularProductRankTest extends BaseUnitTest {

    @Mock
    private MySQLProductRepository productRepository;

    private PopularProductServiceImpl service;

    private Product buildProduct(long id, String name) {
        return Product.builder()
                .productId(id)
                .productName(name)
                .price(10000L)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void setupService() {
        // PopularProductServiceImpl은 ProductRepository를 받지만 내부에서
        // MySQLProductRepository로 캐스팅하므로 mock을 ProductRepository로도 등록
        service = new PopularProductServiceImpl(productRepository);
    }

    @Test
    @DisplayName("5개 상품 rank가 1,2,3,4,5 순서대로 할당된다")
    void rankAssignedInAscendingOrder() {
        setupService();

        List<Product> products = List.of(
                buildProduct(1L, "상품A"),
                buildProduct(2L, "상품B"),
                buildProduct(3L, "상품C"),
                buildProduct(4L, "상품D"),
                buildProduct(5L, "상품E")
        );

        // 주문 수량: A=500, B=400, C=300, D=200, E=100
        Map<Long, Long> orderCounts = Map.of(
                1L, 500L,
                2L, 400L,
                3L, 300L,
                4L, 200L,
                5L, 100L
        );

        given(productRepository.findProductsOrderedLast3Days()).willReturn(products);
        given(productRepository.getOrderCountsLast3Days(anyList())).willReturn(orderCounts);

        PopularProductListResponse result = service.getPopularProducts();
        List<PopularProductView> views = result.getProducts();

        assertThat(views).hasSize(5);
        // rank는 1부터 5까지 순서대로
        for (int i = 0; i < views.size(); i++) {
            assertThat(views.get(i).getRank()).isEqualTo(i + 1);
        }
        // 가장 높은 주문 수량(500)이 rank=1
        assertThat(views.get(0).getProductName()).isEqualTo("상품A");
        assertThat(views.get(0).getRank()).isEqualTo(1);
        // 가장 낮은 주문 수량(100)이 rank=5
        assertThat(views.get(4).getProductName()).isEqualTo("상품E");
        assertThat(views.get(4).getRank()).isEqualTo(5);
    }

    @Test
    @DisplayName("상품이 3개인 경우 rank가 1,2,3으로 설정된다")
    void rankAssignedFor3Products() {
        setupService();

        List<Product> products = List.of(
                buildProduct(1L, "상품X"),
                buildProduct(2L, "상품Y"),
                buildProduct(3L, "상품Z")
        );

        Map<Long, Long> orderCounts = Map.of(
                1L, 100L,
                2L, 300L,
                3L, 200L
        );

        given(productRepository.findProductsOrderedLast3Days()).willReturn(products);
        given(productRepository.getOrderCountsLast3Days(anyList())).willReturn(orderCounts);

        PopularProductListResponse result = service.getPopularProducts();
        List<PopularProductView> views = result.getProducts();

        assertThat(views).hasSize(3);
        assertThat(views.get(0).getRank()).isEqualTo(1);
        assertThat(views.get(1).getRank()).isEqualTo(2);
        assertThat(views.get(2).getRank()).isEqualTo(3);
        // 주문 수량 내림차순 정렬: 상품Y(300) > 상품Z(200) > 상품X(100)
        assertThat(views.get(0).getProductName()).isEqualTo("상품Y");
        assertThat(views.get(1).getProductName()).isEqualTo("상품Z");
        assertThat(views.get(2).getProductName()).isEqualTo("상품X");
    }

    @Test
    @DisplayName("상품이 없는 경우 빈 목록이 반환된다")
    void emptyProductList() {
        setupService();

        given(productRepository.findProductsOrderedLast3Days()).willReturn(List.of());
        given(productRepository.getOrderCountsLast3Days(anyList())).willReturn(Map.of());

        PopularProductListResponse result = service.getPopularProducts();

        assertThat(result.getProducts()).isEmpty();
    }
}
