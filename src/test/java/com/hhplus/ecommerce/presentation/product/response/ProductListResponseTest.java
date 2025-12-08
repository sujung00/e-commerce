package com.hhplus.ecommerce.presentation.product.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProductListResponse 단위 테스트")
class ProductListResponseTest {

    @Test
    @DisplayName("ProductListResponse 빌더를 통한 생성")
    void testProductListResponseBuilder() {
        LocalDateTime now = LocalDateTime.now();
        ProductResponse product = ProductResponse.builder()
                .productId(1L)
                .productName("상품1")
                .price(50000L)
                .build();

        ProductListResponse response = ProductListResponse.builder()
                .content(Arrays.asList(product))
                .totalElements(10L)
                .totalPages(2L)
                .currentPage(0)
                .size(5)
                .build();

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(10L);
        assertThat(response.getTotalPages()).isEqualTo(2L);
        assertThat(response.getCurrentPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("ProductListResponse 기본 생성자")
    void testProductListResponseNoArgsConstructor() {
        ProductListResponse response = new ProductListResponse();

        assertThat(response.getContent()).isNull();
        assertThat(response.getTotalElements()).isNull();
        assertThat(response.getTotalPages()).isNull();
    }

    @Test
    @DisplayName("ProductListResponse 전체 생성자")
    void testProductListResponseAllArgsConstructor() {
        List<ProductResponse> products = new ArrayList<>();
        ProductResponse product = ProductResponse.builder().productId(1L).build();
        products.add(product);

        ProductListResponse response = new ProductListResponse(
                products, 10L, 2L, 0, 5
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(10L);
        assertThat(response.getTotalPages()).isEqualTo(2L);
        assertThat(response.getCurrentPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("ProductListResponse getter/setter 테스트")
    void testProductListResponseGetterSetter() {
        ProductListResponse response = new ProductListResponse();
        List<ProductResponse> products = Arrays.asList(
                ProductResponse.builder().productId(1L).build(),
                ProductResponse.builder().productId(2L).build()
        );

        response.setContent(products);
        response.setTotalElements(20L);
        response.setTotalPages(4L);
        response.setCurrentPage(1);
        response.setSize(5);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(20L);
        assertThat(response.getTotalPages()).isEqualTo(4L);
        assertThat(response.getCurrentPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("ProductListResponse 빈 상품 목록")
    void testProductListResponseEmptyContent() {
        ProductListResponse response = ProductListResponse.builder()
                .content(new ArrayList<>())
                .totalElements(0L)
                .totalPages(0L)
                .currentPage(0)
                .size(5)
                .build();

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ProductListResponse 여러 상품")
    void testProductListResponseMultipleProducts() {
        List<ProductResponse> products = Arrays.asList(
                ProductResponse.builder().productId(1L).productName("상품1").build(),
                ProductResponse.builder().productId(2L).productName("상품2").build(),
                ProductResponse.builder().productId(3L).productName("상품3").build()
        );

        ProductListResponse response = ProductListResponse.builder()
                .content(products)
                .totalElements(3L)
                .totalPages(1L)
                .currentPage(0)
                .size(10)
                .build();

        assertThat(response.getContent()).hasSize(3);
        assertThat(response.getContent().get(0).getProductId()).isEqualTo(1L);
        assertThat(response.getContent().get(1).getProductId()).isEqualTo(2L);
        assertThat(response.getContent().get(2).getProductId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("ProductListResponse 페이지네이션 정보")
    void testProductListResponsePagination() {
        ProductListResponse response = ProductListResponse.builder()
                .content(new ArrayList<>())
                .totalElements(100L)
                .totalPages(10L)
                .currentPage(5)
                .size(10)
                .build();

        assertThat(response.getTotalElements()).isEqualTo(100L);
        assertThat(response.getTotalPages()).isEqualTo(10L);
        assertThat(response.getCurrentPage()).isEqualTo(5);
        assertThat(response.getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("ProductListResponse 마지막 페이지")
    void testProductListResponseLastPage() {
        ProductListResponse response = ProductListResponse.builder()
                .content(new ArrayList<>())
                .totalElements(25L)
                .totalPages(3L)
                .currentPage(2)
                .size(10)
                .build();

        assertThat(response.getCurrentPage()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(3L);
    }

    @Test
    @DisplayName("ProductListResponse null 필드")
    void testProductListResponseNullFields() {
        ProductListResponse response = ProductListResponse.builder().build();

        assertThat(response.getContent()).isNull();
        assertThat(response.getTotalElements()).isNull();
        assertThat(response.getTotalPages()).isNull();
        assertThat(response.getCurrentPage()).isNull();
        assertThat(response.getSize()).isNull();
    }

    @Test
    @DisplayName("ProductListResponse 큰 페이지 크기")
    void testProductListResponseLargePageSize() {
        ProductListResponse response = ProductListResponse.builder()
                .content(new ArrayList<>())
                .totalElements(Long.MAX_VALUE)
                .totalPages(Long.MAX_VALUE)
                .currentPage(Integer.MAX_VALUE)
                .size(Integer.MAX_VALUE)
                .build();

        assertThat(response.getTotalElements()).isEqualTo(Long.MAX_VALUE);
        assertThat(response.getCurrentPage()).isEqualTo(Integer.MAX_VALUE);
    }
}
