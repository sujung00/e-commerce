package com.hhplus.ecommerce.presentation.product.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProductResponse 단위 테스트")
class ProductResponseTest {

    @Test
    @DisplayName("ProductResponse 빌더를 통한 생성")
    void testProductResponseBuilder() {
        LocalDateTime now = LocalDateTime.now();
        ProductResponse response = ProductResponse.builder()
                .productId(1L)
                .productName("테스트 상품")
                .description("테스트 설명")
                .price(50000L)
                .totalStock(100)
                .status("ACTIVE")
                .createdAt(now)
                .build();

        assertThat(response.getProductId()).isEqualTo(1L);
        assertThat(response.getProductName()).isEqualTo("테스트 상품");
        assertThat(response.getDescription()).isEqualTo("테스트 설명");
        assertThat(response.getPrice()).isEqualTo(50000L);
        assertThat(response.getTotalStock()).isEqualTo(100);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("ProductResponse 기본 생성자")
    void testProductResponseNoArgsConstructor() {
        ProductResponse response = new ProductResponse();

        assertThat(response.getProductId()).isNull();
        assertThat(response.getProductName()).isNull();
        assertThat(response.getDescription()).isNull();
    }

    @Test
    @DisplayName("ProductResponse 전체 생성자")
    void testProductResponseAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        ProductResponse response = new ProductResponse(
                1L, "테스트", "설명", 50000L, 100, "ACTIVE", now
        );

        assertThat(response.getProductId()).isEqualTo(1L);
        assertThat(response.getProductName()).isEqualTo("테스트");
        assertThat(response.getPrice()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("ProductResponse getter/setter 테스트")
    void testProductResponseGetterSetter() {
        ProductResponse response = new ProductResponse();
        LocalDateTime now = LocalDateTime.now();

        response.setProductId(1L);
        response.setProductName("상품명");
        response.setDescription("설명");
        response.setPrice(100000L);
        response.setTotalStock(50);
        response.setStatus("INACTIVE");
        response.setCreatedAt(now);

        assertThat(response.getProductId()).isEqualTo(1L);
        assertThat(response.getProductName()).isEqualTo("상품명");
        assertThat(response.getPrice()).isEqualTo(100000L);
        assertThat(response.getTotalStock()).isEqualTo(50);
        assertThat(response.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("ProductResponse null 필드 처리")
    void testProductResponseNullFields() {
        ProductResponse response = ProductResponse.builder().build();

        assertThat(response.getProductId()).isNull();
        assertThat(response.getProductName()).isNull();
        assertThat(response.getDescription()).isNull();
        assertThat(response.getPrice()).isNull();
        assertThat(response.getTotalStock()).isNull();
        assertThat(response.getStatus()).isNull();
        assertThat(response.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("ProductResponse 다양한 상태값")
    void testProductResponseDifferentStatus() {
        String[] statuses = {"ACTIVE", "INACTIVE", "DELETED", "OUT_OF_STOCK"};

        for (String status : statuses) {
            ProductResponse response = ProductResponse.builder()
                    .status(status)
                    .build();
            assertThat(response.getStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("ProductResponse 가격과 재고 경계값")
    void testProductResponseBoundaryValues() {
        ProductResponse response = ProductResponse.builder()
                .productId(Long.MAX_VALUE)
                .price(Long.MAX_VALUE)
                .totalStock(Integer.MAX_VALUE)
                .build();

        assertThat(response.getProductId()).isEqualTo(Long.MAX_VALUE);
        assertThat(response.getPrice()).isEqualTo(Long.MAX_VALUE);
        assertThat(response.getTotalStock()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("ProductResponse 0 가격 상품")
    void testProductResponseZeroPrice() {
        ProductResponse response = ProductResponse.builder()
                .productId(1L)
                .price(0L)
                .totalStock(0)
                .build();

        assertThat(response.getPrice()).isEqualTo(0L);
        assertThat(response.getTotalStock()).isEqualTo(0);
    }
}
