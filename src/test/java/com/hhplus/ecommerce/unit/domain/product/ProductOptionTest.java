package com.hhplus.ecommerce.unit.domain.product;


import com.hhplus.ecommerce.domain.product.ProductOption;import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductOption 도메인 엔티티 단위 테스트
 * - 옵션 생성 및 기본 정보 관리
 * - 재고 관리
 * - 낙관적 락(version) 관리
 * - 타임스탐프 추적
 */
@DisplayName("ProductOption 도메인 엔티티 테스트")
class ProductOptionTest {

    private static final Long TEST_OPTION_ID = 1L;
    private static final Long TEST_PRODUCT_ID = 100L;
    private static final String TEST_OPTION_NAME = "사이즈 M";
    private static final Integer TEST_STOCK = 50;
    private static final Long TEST_VERSION = 1L;

    // ========== ProductOption 생성 ==========

    @Test
    @DisplayName("ProductOption 생성 - 성공")
    void testProductOptionCreation_Success() {
        // When
        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .name(TEST_OPTION_NAME)
                .stock(TEST_STOCK)
                .version(TEST_VERSION)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertNotNull(option);
        assertEquals(TEST_OPTION_ID, option.getOptionId());
        assertEquals(TEST_PRODUCT_ID, option.getProductId());
        assertEquals(TEST_OPTION_NAME, option.getName());
        assertEquals(TEST_STOCK, option.getStock());
        assertEquals(TEST_VERSION, option.getVersion());
    }

    @Test
    @DisplayName("ProductOption 생성 - 기본 버전 설정")
    void testProductOptionCreation_DefaultVersion() {
        // When
        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .name(TEST_OPTION_NAME)
                .stock(TEST_STOCK)
                .version(0L)
                .build();

        // Then
        assertEquals(0L, option.getVersion());
    }

    @Test
    @DisplayName("ProductOption 생성 - 다양한 옵션명")
    void testProductOptionCreation_DifferentNames() {
        // When
        ProductOption option1 = ProductOption.builder()
                .optionId(1L)
                .name("사이즈 S")
                .build();
        ProductOption option2 = ProductOption.builder()
                .optionId(2L)
                .name("색상: 빨강")
                .build();
        ProductOption option3 = ProductOption.builder()
                .optionId(3L)
                .name("소재: 면")
                .build();

        // Then
        assertEquals("사이즈 S", option1.getName());
        assertEquals("색상: 빨강", option2.getName());
        assertEquals("소재: 면", option3.getName());
    }

    // ========== ProductOption 정보 조회 ==========

    @Test
    @DisplayName("ProductOption 조회 - 옵션명 확인")
    void testProductOptionRetrieve_Name() {
        // When
        ProductOption option = ProductOption.builder()
                .name(TEST_OPTION_NAME)
                .build();

        // Then
        assertEquals(TEST_OPTION_NAME, option.getName());
    }

    @Test
    @DisplayName("ProductOption 조회 - 재고 확인")
    void testProductOptionRetrieve_Stock() {
        // When
        ProductOption option = ProductOption.builder()
                .stock(TEST_STOCK)
                .build();

        // Then
        assertEquals(TEST_STOCK, option.getStock());
    }

    @Test
    @DisplayName("ProductOption 조회 - 버전 확인")
    void testProductOptionRetrieve_Version() {
        // When
        ProductOption option = ProductOption.builder()
                .version(TEST_VERSION)
                .build();

        // Then
        assertEquals(TEST_VERSION, option.getVersion());
    }

    @Test
    @DisplayName("ProductOption 조회 - 상품 ID 확인")
    void testProductOptionRetrieve_ProductId() {
        // When
        ProductOption option = ProductOption.builder()
                .productId(TEST_PRODUCT_ID)
                .build();

        // Then
        assertEquals(TEST_PRODUCT_ID, option.getProductId());
    }

    // ========== 재고 관리 ==========

    @Test
    @DisplayName("재고 관리 - 재고 변경 (도메인 메서드 사용)")
    void testStockManagement_Update() {
        // Given
        ProductOption option = ProductOption.builder()
                .stock(50)
                .version(1L)
                .build();

        // When: 재고 10개 차감
        option.deductStock(10);

        // Then
        assertEquals(40, option.getStock());
    }

    @Test
    @DisplayName("재고 관리 - 재고 감소 (도메인 메서드 사용)")
    void testStockManagement_Decrease() {
        // Given
        ProductOption option = ProductOption.builder()
                .stock(50)
                .version(1L)
                .build();

        // When: 재고 1개 차감
        option.deductStock(1);

        // Then
        assertEquals(49, option.getStock());
        assertEquals(2L, option.getVersion()); // version 증가 확인
    }

    @Test
    @DisplayName("재고 관리 - 재고 증가 (복구 메서드 사용)")
    void testStockManagement_Increase() {
        // Given
        ProductOption option = ProductOption.builder()
                .stock(50)
                .version(1L)
                .build();

        // When: 재고 10개 복구
        option.restoreStock(10);

        // Then
        assertEquals(60, option.getStock());
        assertEquals(2L, option.getVersion()); // version 증가 확인
    }

    @Test
    @DisplayName("재고 관리 - 재고 0으로 설정 (전량 차감)")
    void testStockManagement_SetToZero() {
        // Given
        ProductOption option = ProductOption.builder()
                .stock(50)
                .version(1L)
                .build();

        // When: 전량 차감
        option.deductStock(50);

        // Then
        assertEquals(0, option.getStock());
    }

    // ========== 낙관적 락(Version) 관리 ==========

    @Test
    @DisplayName("낙관적 락 - 버전 증가 (재고 차감 시)")
    void testOptimisticLock_VersionIncrement() {
        // Given
        ProductOption option = ProductOption.builder()
                .stock(50)
                .version(1L)
                .build();

        // When: 재고 차감 시 version 자동 증가
        option.deductStock(10);

        // Then
        assertEquals(2L, option.getVersion());
    }

    @Test
    @DisplayName("낙관적 락 - 버전 초기값 0")
    void testOptimisticLock_InitialVersion() {
        // When
        ProductOption option = ProductOption.builder()
                .version(0L)
                .build();

        // Then
        assertEquals(0L, option.getVersion());
    }

    @Test
    @DisplayName("낙관적 락 - 연속적인 버전 증가 (다중 재고 차감)")
    void testOptimisticLock_MultipleIncrements() {
        // Given
        ProductOption option = ProductOption.builder()
                .stock(100)
                .version(1L)
                .build();

        // When: 5번 재고 차감 -> version 5번 증가
        for (int i = 0; i < 5; i++) {
            option.deductStock(1);
        }

        // Then
        assertEquals(6L, option.getVersion());
        assertEquals(95, option.getStock());
    }

    @Test
    @DisplayName("낙관적 락 - 높은 버전 값")
    void testOptimisticLock_HighVersionValue() {
        // When
        ProductOption option = ProductOption.builder()
                .version(Long.MAX_VALUE / 2)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE / 2, option.getVersion());
    }

    // ========== 타임스탐프 ==========

    @Test
    @DisplayName("타임스탐프 - createdAt 설정")
    void testTimestamp_CreatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        ProductOption option = ProductOption.builder()
                .createdAt(now)
                .build();

        // Then
        assertNotNull(option.getCreatedAt());
        assertEquals(now, option.getCreatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - updatedAt 설정")
    void testTimestamp_UpdatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        ProductOption option = ProductOption.builder()
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(option.getUpdatedAt());
        assertEquals(now, option.getUpdatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - 변경")
    void testTimestamp_Update() {
        // Given
        LocalDateTime originalTime = LocalDateTime.now();
        ProductOption option = ProductOption.builder()
                .stock(50)
                .version(1L)
                .createdAt(originalTime)
                .updatedAt(originalTime)
                .build();

        // When: 재고 차감 시 updatedAt 자동 갱신
        option.deductStock(10);

        // Then
        assertEquals(originalTime, option.getCreatedAt());
        assertTrue(option.getUpdatedAt().isAfter(originalTime) ||
                   option.getUpdatedAt().isEqual(originalTime));
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("경계값 - 최소 재고 (0)")
    void testBoundary_ZeroStock() {
        // When
        ProductOption option = ProductOption.builder()
                .stock(0)
                .build();

        // Then
        assertEquals(0, option.getStock());
    }

    @Test
    @DisplayName("경계값 - 높은 재고")
    void testBoundary_HighStock() {
        // When
        ProductOption option = ProductOption.builder()
                .stock(Integer.MAX_VALUE)
                .build();

        // Then
        assertEquals(Integer.MAX_VALUE, option.getStock());
    }

    @Test
    @DisplayName("경계값 - 최소 버전 (0)")
    void testBoundary_MinimumVersion() {
        // When
        ProductOption option = ProductOption.builder()
                .version(0L)
                .build();

        // Then
        assertEquals(0L, option.getVersion());
    }

    @Test
    @DisplayName("경계값 - 높은 버전")
    void testBoundary_HighVersion() {
        // When
        ProductOption option = ProductOption.builder()
                .version(Long.MAX_VALUE)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE, option.getVersion());
    }

    @Test
    @DisplayName("경계값 - ID 값")
    void testBoundary_IdValues() {
        // When
        ProductOption option = ProductOption.builder()
                .optionId(Long.MAX_VALUE)
                .productId(Long.MAX_VALUE)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE, option.getOptionId());
        assertEquals(Long.MAX_VALUE, option.getProductId());
    }

    // ========== null 안전성 ==========

    @Test
    @DisplayName("null 안전성 - 모든 필드 null")
    void testNullSafety_AllFields() {
        // When
        ProductOption option = ProductOption.builder().build();

        // Then
        assertNull(option.getOptionId());
        assertNull(option.getProductId());
        assertNull(option.getName());
        assertNull(option.getStock());
        assertNull(option.getVersion());
    }

    @Test
    @DisplayName("null 안전성 - 선택적 필드 설정")
    void testNullSafety_PartialFields() {
        // When
        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .build();

        // Then
        assertEquals(TEST_OPTION_ID, option.getOptionId());
        assertEquals(TEST_PRODUCT_ID, option.getProductId());
        assertNull(option.getName());
        assertNull(option.getStock());
    }

    // ========== NoArgsConstructor/AllArgsConstructor ==========

    @Test
    @DisplayName("NoArgsConstructor 테스트")
    void testNoArgsConstructor() {
        // When
        ProductOption option = new ProductOption();

        // Then
        assertNull(option.getOptionId());
        assertNull(option.getProductId());
        assertNull(option.getName());
    }

    @Test
    @DisplayName("AllArgsConstructor 테스트")
    void testAllArgsConstructor() {
        // When
        LocalDateTime now = LocalDateTime.now();
        ProductOption option = new ProductOption(
                TEST_OPTION_ID,
                TEST_PRODUCT_ID,
                TEST_OPTION_NAME,
                TEST_STOCK,
                TEST_VERSION,
                now,
                now
        );

        // Then
        assertEquals(TEST_OPTION_ID, option.getOptionId());
        assertEquals(TEST_PRODUCT_ID, option.getProductId());
        assertEquals(TEST_OPTION_NAME, option.getName());
        assertEquals(TEST_STOCK, option.getStock());
        assertEquals(TEST_VERSION, option.getVersion());
    }

    // ========== ProductOption 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 옵션 생성 및 재고 관리")
    void testScenario_CreateOptionAndManageStock() {
        // When
        ProductOption option = ProductOption.builder()
                .optionId(1L)
                .productId(100L)
                .name("사이즈 M")
                .stock(100)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertEquals("사이즈 M", option.getName());
        assertEquals(100, option.getStock());
        assertEquals(1L, option.getVersion());
    }

    @Test
    @DisplayName("사용 시나리오 - 재고 감소 (구매 시뮬레이션)")
    void testScenario_DecreaseStockOnPurchase() {
        // Given
        ProductOption option = ProductOption.builder()
                .optionId(1L)
                .productId(100L)
                .name("사이즈 M")
                .stock(100)
                .version(1L)
                .build();

        // When: 10개 구매 (도메인 메서드 사용)
        int purchaseQuantity = 10;
        option.deductStock(purchaseQuantity);

        // Then
        assertEquals(90, option.getStock());
        assertEquals(2L, option.getVersion());
    }

    @Test
    @DisplayName("사용 시나리오 - 재고 복구 (반품 시뮬레이션)")
    void testScenario_RestockOnReturn() {
        // Given
        ProductOption option = ProductOption.builder()
                .optionId(1L)
                .productId(100L)
                .name("사이즈 M")
                .stock(90)
                .version(2L)
                .build();

        // When: 5개 반품 (도메인 메서드 사용)
        int returnQuantity = 5;
        option.restoreStock(returnQuantity);

        // Then
        assertEquals(95, option.getStock());
        assertEquals(3L, option.getVersion());
    }

    @Test
    @DisplayName("사용 시나리오 - 품절 상태 전환")
    void testScenario_SoldOut() {
        // Given
        ProductOption option = ProductOption.builder()
                .optionId(1L)
                .productId(100L)
                .name("사이즈 S")
                .stock(5)
                .version(10L)
                .build();

        // When: 마지막 5개 구매로 품절 (도메인 메서드 사용)
        option.deductStock(5);

        // Then
        assertEquals(0, option.getStock());
        assertEquals(11L, option.getVersion());
    }
}
