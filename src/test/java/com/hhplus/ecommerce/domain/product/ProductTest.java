package com.hhplus.ecommerce.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Product 도메인 엔티티 단위 테스트
 * - 상품 생성 및 기본 정보 관리
 * - 옵션(ProductOption) 목록 관리
 * - 재고 상태 및 판매 상태 관리
 * - 타임스탐프 추적
 */
@DisplayName("Product 도메인 엔티티 테스트")
class ProductTest {

    private static final Long TEST_PRODUCT_ID = 100L;
    private static final String TEST_PRODUCT_NAME = "프리미엄 상품";
    private static final String TEST_DESCRIPTION = "고급 상품입니다";
    private static final Long TEST_PRICE = 50000L;
    private static final Integer TEST_TOTAL_STOCK = 100;
    private static final String TEST_STATUS = "판매중";

    // ========== Product 생성 ==========

    @Test
    @DisplayName("Product 생성 - 성공")
    void testProductCreation_Success() {
        // When
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName(TEST_PRODUCT_NAME)
                .description(TEST_DESCRIPTION)
                .price(TEST_PRICE)
                .totalStock(TEST_TOTAL_STOCK)
                .status(TEST_STATUS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertNotNull(product);
        assertEquals(TEST_PRODUCT_ID, product.getProductId());
        assertEquals(TEST_PRODUCT_NAME, product.getProductName());
        assertEquals(TEST_DESCRIPTION, product.getDescription());
        assertEquals(TEST_PRICE, product.getPrice());
        assertEquals(TEST_TOTAL_STOCK, product.getTotalStock());
        assertEquals(TEST_STATUS, product.getStatus());
    }

    @Test
    @DisplayName("Product 생성 - 옵션 없이 생성")
    void testProductCreation_EmptyOptions() {
        // When
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName(TEST_PRODUCT_NAME)
                .price(TEST_PRICE)
                .build();

        // Then
        assertNotNull(product.getOptions());
        assertTrue(product.getOptions().isEmpty());
    }

    @Test
    @DisplayName("Product 생성 - 옵션 포함")
    void testProductCreation_WithOptions() {
        // When
        List<ProductOption> options = new ArrayList<>();
        options.add(ProductOption.builder()
                .optionId(1L)
                .productId(TEST_PRODUCT_ID)
                .name("사이즈 S")
                .stock(30)
                .version(1L)
                .build());
        options.add(ProductOption.builder()
                .optionId(2L)
                .productId(TEST_PRODUCT_ID)
                .name("사이즈 M")
                .stock(40)
                .version(1L)
                .build());

        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName(TEST_PRODUCT_NAME)
                .price(TEST_PRICE)
                .options(options)
                .build();

        // Then
        assertEquals(2, product.getOptions().size());
        assertEquals("사이즈 S", product.getOptions().get(0).getName());
        assertEquals("사이즈 M", product.getOptions().get(1).getName());
    }

    // ========== Product 정보 조회 ==========

    @Test
    @DisplayName("Product 조회 - 상품명 확인")
    void testProductRetrieve_ProductName() {
        // When
        Product product = Product.builder()
                .productName(TEST_PRODUCT_NAME)
                .build();

        // Then
        assertEquals(TEST_PRODUCT_NAME, product.getProductName());
    }

    @Test
    @DisplayName("Product 조회 - 가격 확인")
    void testProductRetrieve_Price() {
        // When
        Product product = Product.builder()
                .price(TEST_PRICE)
                .build();

        // Then
        assertEquals(TEST_PRICE, product.getPrice());
    }

    @Test
    @DisplayName("Product 조회 - 재고 확인")
    void testProductRetrieve_TotalStock() {
        // When
        Product product = Product.builder()
                .totalStock(TEST_TOTAL_STOCK)
                .build();

        // Then
        assertEquals(TEST_TOTAL_STOCK, product.getTotalStock());
    }

    @Test
    @DisplayName("Product 조회 - 상태 확인")
    void testProductRetrieve_Status() {
        // When
        Product product = Product.builder()
                .status(TEST_STATUS)
                .build();

        // Then
        assertEquals(TEST_STATUS, product.getStatus());
    }

    // ========== Product 정보 변경 ==========

    @Test
    @DisplayName("Product 정보 변경 - 상품명 변경")
    void testProductUpdate_ProductName() {
        // Given
        Product product = Product.builder()
                .productName("기존 상품명")
                .build();

        // When - Builder로 변경된 객체 생성
        Product updatedProduct = Product.builder()
                .productName("변경된 상품명")
                .build();

        // Then
        assertEquals("변경된 상품명", updatedProduct.getProductName());
    }

    @Test
    @DisplayName("Product 정보 변경 - 가격 변경")
    void testProductUpdate_Price() {
        // Given
        Product product = Product.builder()
                .price(50000L)
                .build();

        // When - Builder로 변경된 객체 생성
        Product updatedProduct = Product.builder()
                .price(60000L)
                .build();

        // Then
        assertEquals(60000L, updatedProduct.getPrice());
    }

    @Test
    @DisplayName("Product 정보 변경 - 재고 변경")
    void testProductUpdate_TotalStock() {
        // Given
        Product product = Product.builder()
                .totalStock(100)
                .build();

        // When - Builder로 변경된 객체 생성
        Product updatedProduct = Product.builder()
                .totalStock(150)
                .build();

        // Then
        assertEquals(150, updatedProduct.getTotalStock());
    }

    @Test
    @DisplayName("Product 정보 변경 - 상태 변경")
    void testProductUpdate_Status() {
        // Given
        Product product = Product.builder()
                .status("판매중")
                .build();

        // When - Builder로 변경된 객체 생성
        Product updatedProduct = Product.builder()
                .status("품절")
                .build();

        // Then
        assertEquals("품절", updatedProduct.getStatus());
    }

    @Test
    @DisplayName("Product 정보 변경 - 옵션 추가")
    void testProductUpdate_AddOption() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .build();

        // When
        ProductOption option = ProductOption.builder()
                .optionId(1L)
                .productId(TEST_PRODUCT_ID)
                .name("색상: 빨강")
                .stock(50)
                .version(1L)
                .build();
        product.getOptions().add(option);

        // Then
        assertEquals(1, product.getOptions().size());
        assertEquals("색상: 빨강", product.getOptions().get(0).getName());
    }

    // ========== 옵션 관리 ==========

    @Test
    @DisplayName("옵션 관리 - 여러 옵션 추가")
    void testOptionManagement_AddMultipleOptions() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName(TEST_PRODUCT_NAME)
                .build();

        // When
        for (int i = 1; i <= 5; i++) {
            ProductOption option = ProductOption.builder()
                    .optionId((long) i)
                    .productId(TEST_PRODUCT_ID)
                    .name("옵션 " + i)
                    .stock(10 * i)
                    .version(1L)
                    .build();
            product.getOptions().add(option);
        }

        // Then
        assertEquals(5, product.getOptions().size());
        assertEquals("옵션 1", product.getOptions().get(0).getName());
        assertEquals("옵션 5", product.getOptions().get(4).getName());
    }

    @Test
    @DisplayName("옵션 관리 - 옵션 제거")
    void testOptionManagement_RemoveOption() {
        // Given
        List<ProductOption> options = new ArrayList<>();
        options.add(ProductOption.builder()
                .optionId(1L)
                .productId(TEST_PRODUCT_ID)
                .name("옵션 1")
                .build());
        options.add(ProductOption.builder()
                .optionId(2L)
                .productId(TEST_PRODUCT_ID)
                .name("옵션 2")
                .build());

        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .options(options)
                .build();

        // When
        product.getOptions().remove(0);

        // Then
        assertEquals(1, product.getOptions().size());
        assertEquals("옵션 2", product.getOptions().get(0).getName());
    }

    // ========== 타임스탐프 ==========

    @Test
    @DisplayName("타임스탐프 - createdAt 설정")
    void testTimestamp_CreatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Product product = Product.builder()
                .createdAt(now)
                .build();

        // Then
        assertNotNull(product.getCreatedAt());
        assertEquals(now, product.getCreatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - updatedAt 설정")
    void testTimestamp_UpdatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Product product = Product.builder()
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(product.getUpdatedAt());
        assertEquals(now, product.getUpdatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - 변경")
    void testTimestamp_Update() {
        // Given
        LocalDateTime originalTime = LocalDateTime.now();
        Product product = Product.builder()
                .createdAt(originalTime)
                .updatedAt(originalTime)
                .build();

        // When - Builder로 변경된 객체 생성
        LocalDateTime newTime = originalTime.plusHours(1);
        Product updatedProduct = Product.builder()
                .createdAt(originalTime)
                .updatedAt(newTime)
                .build();

        // Then
        assertEquals(originalTime, updatedProduct.getCreatedAt());
        assertEquals(newTime, updatedProduct.getUpdatedAt());
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("경계값 - 최소 재고 (0)")
    void testBoundary_ZeroStock() {
        // When
        Product product = Product.builder()
                .totalStock(0)
                .build();

        // Then
        assertEquals(0, product.getTotalStock());
    }

    @Test
    @DisplayName("경계값 - 높은 재고")
    void testBoundary_HighStock() {
        // When
        Product product = Product.builder()
                .totalStock(Integer.MAX_VALUE)
                .build();

        // Then
        assertEquals(Integer.MAX_VALUE, product.getTotalStock());
    }

    @Test
    @DisplayName("경계값 - 최소 가격 (0원)")
    void testBoundary_ZeroPrice() {
        // When
        Product product = Product.builder()
                .price(0L)
                .build();

        // Then
        assertEquals(0L, product.getPrice());
    }

    @Test
    @DisplayName("경계값 - 높은 가격")
    void testBoundary_HighPrice() {
        // When
        Product product = Product.builder()
                .price(Long.MAX_VALUE / 2)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE / 2, product.getPrice());
    }

    @Test
    @DisplayName("경계값 - 최대 옵션 개수")
    void testBoundary_MaxOptions() {
        // When
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .build();

        for (int i = 1; i <= 100; i++) {
            ProductOption option = ProductOption.builder()
                    .optionId((long) i)
                    .productId(TEST_PRODUCT_ID)
                    .name("옵션 " + i)
                    .stock(1)
                    .version(1L)
                    .build();
            product.getOptions().add(option);
        }

        // Then
        assertEquals(100, product.getOptions().size());
    }

    @Test
    @DisplayName("경계값 - ID 값")
    void testBoundary_IdValues() {
        // When
        Product product = Product.builder()
                .productId(Long.MAX_VALUE)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE, product.getProductId());
    }

    // ========== null 안전성 ==========

    @Test
    @DisplayName("null 안전성 - 모든 필드 null")
    void testNullSafety_AllFields() {
        // When
        Product product = Product.builder().build();

        // Then
        assertNull(product.getProductId());
        assertNull(product.getProductName());
        assertNull(product.getPrice());
        assertNull(product.getTotalStock());
        assertNotNull(product.getOptions());  // Builder.Default
        assertTrue(product.getOptions().isEmpty());
    }

    @Test
    @DisplayName("null 안전성 - 선택적 필드 설정")
    void testNullSafety_PartialFields() {
        // When
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName(TEST_PRODUCT_NAME)
                .build();

        // Then
        assertEquals(TEST_PRODUCT_ID, product.getProductId());
        assertEquals(TEST_PRODUCT_NAME, product.getProductName());
        assertNull(product.getPrice());
        assertNull(product.getTotalStock());
    }

    // ========== NoArgsConstructor/AllArgsConstructor ==========

    @Test
    @DisplayName("NoArgsConstructor 테스트")
    void testNoArgsConstructor() {
        // When
        Product product = new Product();

        // Then
        assertNull(product.getProductId());
        assertNull(product.getProductName());
        assertNotNull(product.getOptions());
    }

    @Test
    @DisplayName("AllArgsConstructor 테스트")
    void testAllArgsConstructor() {
        // When
        LocalDateTime now = LocalDateTime.now();
        List<ProductOption> options = new ArrayList<>();
        Product product = new Product(
                TEST_PRODUCT_ID,
                TEST_PRODUCT_NAME,
                TEST_DESCRIPTION,
                TEST_PRICE,
                TEST_TOTAL_STOCK,
                TEST_STATUS,
                0L,  // version
                now,
                now,
                options
        );

        // Then
        assertEquals(TEST_PRODUCT_ID, product.getProductId());
        assertEquals(TEST_PRODUCT_NAME, product.getProductName());
        assertEquals(TEST_PRICE, product.getPrice());
        assertEquals(TEST_TOTAL_STOCK, product.getTotalStock());
        assertEquals(TEST_STATUS, product.getStatus());
    }

    // ========== Product 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 상품 생성 및 옵션 추가")
    void testScenario_CreateProductWithOptions() {
        // When
        Product product = Product.builder()
                .productId(1L)
                .productName("추천 상품")
                .description("인기 있는 상품입니다")
                .price(100000L)
                .totalStock(1000)
                .status("판매중")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProductOption option1 = ProductOption.builder()
                .optionId(1L)
                .productId(1L)
                .name("사이즈 S")
                .stock(500)
                .version(1L)
                .build();

        ProductOption option2 = ProductOption.builder()
                .optionId(2L)
                .productId(1L)
                .name("사이즈 M")
                .stock(500)
                .version(1L)
                .build();

        product.getOptions().add(option1);
        product.getOptions().add(option2);

        // Then
        assertEquals("추천 상품", product.getProductName());
        assertEquals(2, product.getOptions().size());
        assertEquals(1000, product.getTotalStock());
    }

    @Test
    @DisplayName("사용 시나리오 - 상품 상태 변경 (판매중 → 품절)")
    void testScenario_ChangeProductStatus() {
        // Given
        Product product = Product.builder()
                .productId(1L)
                .productName("상품")
                .status("판매중")
                .totalStock(100)
                .build();

        // When - Builder로 변경된 객체 생성
        Product updatedProduct = Product.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .status("품절")
                .totalStock(0)
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertEquals("품절", updatedProduct.getStatus());
        assertEquals(0, updatedProduct.getTotalStock());
    }

    @Test
    @DisplayName("사용 시나리오 - 상품 정보 업데이트")
    void testScenario_UpdateProductInformation() {
        // Given
        Product product = Product.builder()
                .productId(1L)
                .productName("기존 상품명")
                .description("기존 설명")
                .price(50000L)
                .totalStock(100)
                .status("판매중")
                .build();

        // When - Builder로 변경된 객체 생성
        Product updatedProduct = Product.builder()
                .productId(product.getProductId())
                .productName("새로운 상품명")
                .description("새로운 설명")
                .price(60000L)
                .totalStock(product.getTotalStock())
                .status(product.getStatus())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertEquals("새로운 상품명", updatedProduct.getProductName());
        assertEquals("새로운 설명", updatedProduct.getDescription());
        assertEquals(60000L, updatedProduct.getPrice());
    }
}
