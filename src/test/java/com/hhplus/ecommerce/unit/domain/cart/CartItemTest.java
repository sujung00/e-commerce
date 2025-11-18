package com.hhplus.ecommerce.unit.domain.cart;


import com.hhplus.ecommerce.domain.cart.CartItem;import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CartItem 도메인 엔티티 단위 테스트
 * - 장바구니 아이템 생성
 * - 금액 계산 검증
 * - 수량 관리
 */
@DisplayName("CartItem 도메인 엔티티 테스트")
class CartItemTest {

    private static final Long TEST_CART_ID = 1L;
    private static final Long TEST_PRODUCT_ID = 100L;
    private static final Long TEST_OPTION_ID = 1L;
    private static final Integer TEST_QUANTITY = 2;
    private static final Long TEST_UNIT_PRICE = 50000L;

    // ========== CartItem 생성 ==========

    @Test
    @DisplayName("CartItem 생성 - 성공")
    void testCartItemCreation_Success() {
        // When
        CartItem cartItem = CartItem.builder()
                .cartItemId(1L)
                .cartId(TEST_CART_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(TEST_QUANTITY)
                .unitPrice(TEST_UNIT_PRICE)
                .subtotal(TEST_UNIT_PRICE * TEST_QUANTITY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertNotNull(cartItem);
        assertEquals(1L, cartItem.getCartItemId());
        assertEquals(TEST_CART_ID, cartItem.getCartId());
        assertEquals(TEST_PRODUCT_ID, cartItem.getProductId());
        assertEquals(TEST_OPTION_ID, cartItem.getOptionId());
        assertEquals(TEST_QUANTITY, cartItem.getQuantity());
        assertEquals(TEST_UNIT_PRICE, cartItem.getUnitPrice());
    }

    // ========== 금액 계산 ==========

    @Test
    @DisplayName("소계 계산 - 단가 * 수량")
    void testSubtotalCalculation_Basic() {
        // When
        CartItem cartItem = CartItem.builder()
                .quantity(TEST_QUANTITY)
                .unitPrice(TEST_UNIT_PRICE)
                .subtotal(TEST_UNIT_PRICE * TEST_QUANTITY)
                .build();

        // Then
        Long expectedSubtotal = TEST_UNIT_PRICE * TEST_QUANTITY;
        assertEquals(expectedSubtotal, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("소계 계산 - 다양한 수량")
    void testSubtotalCalculation_DifferentQuantities() {
        // When/Then
        testSubtotalForQuantity(1, 50000L, 50000L);
        testSubtotalForQuantity(2, 50000L, 100000L);
        testSubtotalForQuantity(5, 50000L, 250000L);
        testSubtotalForQuantity(10, 50000L, 500000L);
    }

    private void testSubtotalForQuantity(Integer quantity, Long unitPrice, Long expectedSubtotal) {
        CartItem cartItem = CartItem.builder()
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(expectedSubtotal)
                .build();
        assertEquals(expectedSubtotal, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("소계 계산 - 다양한 단가")
    void testSubtotalCalculation_DifferentPrices() {
        // When/Then
        testSubtotalForPrice(2, 10000L, 20000L);
        testSubtotalForPrice(2, 50000L, 100000L);
        testSubtotalForPrice(2, 100000L, 200000L);
        testSubtotalForPrice(2, 999999L, 1999998L);
    }

    private void testSubtotalForPrice(Integer quantity, Long unitPrice, Long expectedSubtotal) {
        CartItem cartItem = CartItem.builder()
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(expectedSubtotal)
                .build();
        assertEquals(expectedSubtotal, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("소계 계산 - 수량이 1일 때")
    void testSubtotalCalculation_SingleQuantity() {
        // When
        CartItem cartItem = CartItem.builder()
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .build();

        // Then
        assertEquals(50000L, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("소계 계산 - 0원 상품")
    void testSubtotalCalculation_FreeProduct() {
        // When
        CartItem cartItem = CartItem.builder()
                .quantity(5)
                .unitPrice(0L)
                .subtotal(0L)
                .build();

        // Then
        assertEquals(0L, cartItem.getSubtotal());
    }

    // ========== 수량 관리 ==========

    @Test
    @DisplayName("수량 관리 - 수량 변경")
    void testQuantityManagement_Update() {
        // Given
        CartItem cartItem = CartItem.builder()
                .quantity(2)
                .unitPrice(50000L)
                .subtotal(100000L)
                .build();

        // When
        cartItem.setQuantity(3);
        cartItem.setSubtotal(150000L);  // 수동으로 재계산

        // Then
        assertEquals(3, cartItem.getQuantity());
        assertEquals(150000L, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("수량 관리 - 수량 증가")
    void testQuantityManagement_Increase() {
        // Given
        CartItem cartItem = CartItem.builder()
                .quantity(2)
                .unitPrice(50000L)
                .subtotal(100000L)
                .build();

        // When
        Integer newQuantity = cartItem.getQuantity() + 1;
        cartItem.setQuantity(newQuantity);
        cartItem.setSubtotal(50000L * newQuantity);

        // Then
        assertEquals(3, cartItem.getQuantity());
        assertEquals(150000L, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("수량 관리 - 수량 감소")
    void testQuantityManagement_Decrease() {
        // Given
        CartItem cartItem = CartItem.builder()
                .quantity(5)
                .unitPrice(50000L)
                .subtotal(250000L)
                .build();

        // When
        Integer newQuantity = cartItem.getQuantity() - 1;
        cartItem.setQuantity(newQuantity);
        cartItem.setSubtotal(50000L * newQuantity);

        // Then
        assertEquals(4, cartItem.getQuantity());
        assertEquals(200000L, cartItem.getSubtotal());
    }

    // ========== 아이템 조회 ==========

    @Test
    @DisplayName("아이템 조회 - 장바구니 ID")
    void testCartItemRetrieve_CartId() {
        // When
        CartItem cartItem = CartItem.builder()
                .cartId(TEST_CART_ID)
                .build();

        // Then
        assertEquals(TEST_CART_ID, cartItem.getCartId());
    }

    @Test
    @DisplayName("아이템 조회 - 상품 ID")
    void testCartItemRetrieve_ProductId() {
        // When
        CartItem cartItem = CartItem.builder()
                .productId(TEST_PRODUCT_ID)
                .build();

        // Then
        assertEquals(TEST_PRODUCT_ID, cartItem.getProductId());
    }

    @Test
    @DisplayName("아이템 조회 - 옵션 ID")
    void testCartItemRetrieve_OptionId() {
        // When
        CartItem cartItem = CartItem.builder()
                .optionId(TEST_OPTION_ID)
                .build();

        // Then
        assertEquals(TEST_OPTION_ID, cartItem.getOptionId());
    }

    // ========== 타임스탐프 ==========

    @Test
    @DisplayName("타임스탐프 - createdAt 설정")
    void testTimestamp_CreatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        CartItem cartItem = CartItem.builder()
                .createdAt(now)
                .build();

        // Then
        assertNotNull(cartItem.getCreatedAt());
        assertEquals(now, cartItem.getCreatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - updatedAt 설정")
    void testTimestamp_UpdatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        CartItem cartItem = CartItem.builder()
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(cartItem.getUpdatedAt());
        assertEquals(now, cartItem.getUpdatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - 변경")
    void testTimestamp_Update() {
        // Given
        LocalDateTime originalTime = LocalDateTime.now();
        CartItem cartItem = CartItem.builder()
                .createdAt(originalTime)
                .updatedAt(originalTime)
                .build();

        // When
        LocalDateTime newTime = originalTime.plusHours(1);
        cartItem.setUpdatedAt(newTime);

        // Then
        assertEquals(originalTime, cartItem.getCreatedAt());
        assertEquals(newTime, cartItem.getUpdatedAt());
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("경계값 - 최소 수량 (1)")
    void testBoundary_MinimumQuantity() {
        // When
        CartItem cartItem = CartItem.builder()
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .build();

        // Then
        assertEquals(1, cartItem.getQuantity());
    }

    @Test
    @DisplayName("경계값 - 높은 수량")
    void testBoundary_HighQuantity() {
        // When
        Integer highQuantity = 1000;
        CartItem cartItem = CartItem.builder()
                .quantity(highQuantity)
                .unitPrice(50000L)
                .subtotal(50000L * highQuantity)
                .build();

        // Then
        assertEquals(highQuantity, cartItem.getQuantity());
        assertEquals(50000000L, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("경계값 - 최소 금액 (0원)")
    void testBoundary_ZeroPrice() {
        // When
        CartItem cartItem = CartItem.builder()
                .quantity(5)
                .unitPrice(0L)
                .subtotal(0L)
                .build();

        // Then
        assertEquals(0L, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("경계값 - 높은 금액")
    void testBoundary_HighPrice() {
        // When
        Long highPrice = Long.MAX_VALUE / 2;  // Overflow 방지
        CartItem cartItem = CartItem.builder()
                .quantity(1)
                .unitPrice(highPrice)
                .subtotal(highPrice)
                .build();

        // Then
        assertEquals(highPrice, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("경계값 - ID 값")
    void testBoundary_IdValues() {
        // When
        CartItem cartItem = CartItem.builder()
                .cartItemId(Long.MAX_VALUE)
                .cartId(Long.MAX_VALUE)
                .productId(Long.MAX_VALUE)
                .optionId(Long.MAX_VALUE)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE, cartItem.getCartItemId());
        assertEquals(Long.MAX_VALUE, cartItem.getCartId());
        assertEquals(Long.MAX_VALUE, cartItem.getProductId());
        assertEquals(Long.MAX_VALUE, cartItem.getOptionId());
    }

    // ========== null 안전성 ==========

    @Test
    @DisplayName("null 안전성 - 모든 필드 null")
    void testNullSafety_AllFields() {
        // When
        CartItem cartItem = CartItem.builder().build();

        // Then
        assertNull(cartItem.getCartItemId());
        assertNull(cartItem.getCartId());
        assertNull(cartItem.getProductId());
        assertNull(cartItem.getQuantity());
        assertNull(cartItem.getSubtotal());
    }

    @Test
    @DisplayName("null 안전성 - 선택적 필드 설정")
    void testNullSafety_PartialFields() {
        // When
        CartItem cartItem = CartItem.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .build();

        // Then
        assertEquals(TEST_PRODUCT_ID, cartItem.getProductId());
        assertEquals(TEST_OPTION_ID, cartItem.getOptionId());
        assertNull(cartItem.getQuantity());
        assertNull(cartItem.getUnitPrice());
    }

    // ========== NoArgsConstructor/AllArgsConstructor ==========

    @Test
    @DisplayName("NoArgsConstructor 테스트")
    void testNoArgsConstructor() {
        // When
        CartItem cartItem = new CartItem();

        // Then
        assertNull(cartItem.getCartItemId());
        assertNull(cartItem.getProductId());
    }

    @Test
    @DisplayName("AllArgsConstructor 테스트")
    void testAllArgsConstructor() {
        // When
        LocalDateTime now = LocalDateTime.now();
        CartItem cartItem = new CartItem(
                1L, TEST_CART_ID, TEST_PRODUCT_ID, TEST_OPTION_ID,
                2, 50000L, 100000L, now, now
        );

        // Then
        assertEquals(1L, cartItem.getCartItemId());
        assertEquals(TEST_CART_ID, cartItem.getCartId());
        assertEquals(TEST_PRODUCT_ID, cartItem.getProductId());
        assertEquals(2, cartItem.getQuantity());
        assertEquals(100000L, cartItem.getSubtotal());
    }

    // ========== CartItem 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 장바구니에 상품 추가")
    void testScenario_AddProductToCart() {
        // When
        CartItem cartItem = CartItem.builder()
                .cartId(1L)
                .productId(100L)
                .optionId(1L)
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertEquals(1L, cartItem.getCartId());
        assertEquals(100L, cartItem.getProductId());
        assertEquals(50000L, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("사용 시나리오 - 장바구니 상품 수량 증가")
    void testScenario_IncreaseQuantity() {
        // Given
        CartItem cartItem = CartItem.builder()
                .quantity(2)
                .unitPrice(50000L)
                .subtotal(100000L)
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        // When
        cartItem.setQuantity(3);
        cartItem.setSubtotal(150000L);
        cartItem.setUpdatedAt(LocalDateTime.now());

        // Then
        assertEquals(3, cartItem.getQuantity());
        assertEquals(150000L, cartItem.getSubtotal());
    }

    @Test
    @DisplayName("사용 시나리오 - 장바구니 상품 수량 감소")
    void testScenario_DecreaseQuantity() {
        // Given
        CartItem cartItem = CartItem.builder()
                .quantity(5)
                .unitPrice(50000L)
                .subtotal(250000L)
                .build();

        // When
        cartItem.setQuantity(3);
        cartItem.setSubtotal(150000L);

        // Then
        assertEquals(3, cartItem.getQuantity());
        assertEquals(150000L, cartItem.getSubtotal());
    }
}
