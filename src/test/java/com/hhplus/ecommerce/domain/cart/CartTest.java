package com.hhplus.ecommerce.domain.cart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cart 도메인 엔티티 단위 테스트
 * - 장바구니 생성
 * - 합계 정보 관리
 * - 타임스탐프 관리
 */
@DisplayName("Cart 도메인 엔티티 테스트")
class CartTest {

    private static final Long TEST_USER_ID = 1L;
    private static final Integer TEST_TOTAL_ITEMS = 5;
    private static final Long TEST_TOTAL_PRICE = 250000L;

    // ========== Cart 생성 ==========

    @Test
    @DisplayName("Cart 생성 - 성공")
    void testCartCreation_Success() {
        // When
        Cart cart = Cart.builder()
                .cartId(1L)
                .userId(TEST_USER_ID)
                .totalItems(TEST_TOTAL_ITEMS)
                .totalPrice(TEST_TOTAL_PRICE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertNotNull(cart);
        assertEquals(1L, cart.getCartId());
        assertEquals(TEST_USER_ID, cart.getUserId());
        assertEquals(TEST_TOTAL_ITEMS, cart.getTotalItems());
        assertEquals(TEST_TOTAL_PRICE, cart.getTotalPrice());
    }

    @Test
    @DisplayName("Cart 생성 - 빈 장바구니")
    void testCartCreation_EmptyCart() {
        // When
        Cart cart = Cart.builder()
                .cartId(1L)
                .userId(TEST_USER_ID)
                .totalItems(0)
                .totalPrice(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertEquals(0, cart.getTotalItems());
        assertEquals(0L, cart.getTotalPrice());
    }

    // ========== Cart 정보 조회 ==========

    @Test
    @DisplayName("Cart 조회 - 사용자 ID 확인")
    void testCartRetrieve_UserId() {
        // When
        Cart cart = Cart.builder()
                .cartId(1L)
                .userId(TEST_USER_ID)
                .build();

        // Then
        assertEquals(TEST_USER_ID, cart.getUserId());
    }

    @Test
    @DisplayName("Cart 조회 - 아이템 개수")
    void testCartRetrieve_TotalItems() {
        // When
        Cart cart = Cart.builder()
                .totalItems(10)
                .build();

        // Then
        assertEquals(10, cart.getTotalItems());
    }

    @Test
    @DisplayName("Cart 조회 - 총 금액")
    void testCartRetrieve_TotalPrice() {
        // When
        Cart cart = Cart.builder()
                .totalPrice(500000L)
                .build();

        // Then
        assertEquals(500000L, cart.getTotalPrice());
    }

    // ========== Cart 정보 변경 ==========

    @Test
    @DisplayName("Cart 정보 변경 - 아이템 개수 변경")
    void testCartUpdate_TotalItems() {
        // Given
        Cart cart = Cart.builder()
                .totalItems(5)
                .build();

        // When
        cart.setTotalItems(10);

        // Then
        assertEquals(10, cart.getTotalItems());
    }

    @Test
    @DisplayName("Cart 정보 변경 - 총 금액 변경")
    void testCartUpdate_TotalPrice() {
        // Given
        Cart cart = Cart.builder()
                .totalPrice(100000L)
                .build();

        // When
        cart.setTotalPrice(200000L);

        // Then
        assertEquals(200000L, cart.getTotalPrice());
    }

    @Test
    @DisplayName("Cart 정보 변경 - 타임스탐프 변경")
    void testCartUpdate_Timestamp() {
        // Given
        LocalDateTime originalUpdatedAt = LocalDateTime.now();
        Cart cart = Cart.builder()
                .updatedAt(originalUpdatedAt)
                .build();

        // When
        LocalDateTime newUpdatedAt = originalUpdatedAt.plusHours(1);
        cart.setUpdatedAt(newUpdatedAt);

        // Then
        assertEquals(newUpdatedAt, cart.getUpdatedAt());
    }

    // ========== 타임스탐프 ==========

    @Test
    @DisplayName("Cart 타임스탐프 - createdAt 설정")
    void testCartTimestamp_CreatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Cart cart = Cart.builder()
                .createdAt(now)
                .build();

        // Then
        assertNotNull(cart.getCreatedAt());
        assertEquals(now, cart.getCreatedAt());
    }

    @Test
    @DisplayName("Cart 타임스탐프 - updatedAt 설정")
    void testCartTimestamp_UpdatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Cart cart = Cart.builder()
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(cart.getUpdatedAt());
        assertEquals(now, cart.getUpdatedAt());
    }

    // ========== 합계 정보 ==========

    @Test
    @DisplayName("합계 정보 - 다양한 아이템 개수")
    void testCartTotals_DifferentItemCounts() {
        // When/Then
        testCartWithTotalItems(1);
        testCartWithTotalItems(10);
        testCartWithTotalItems(100);
        testCartWithTotalItems(1000);
    }

    private void testCartWithTotalItems(Integer totalItems) {
        Cart cart = Cart.builder().totalItems(totalItems).build();
        assertEquals(totalItems, cart.getTotalItems());
    }

    @Test
    @DisplayName("합계 정보 - 다양한 금액")
    void testCartTotals_DifferentPrices() {
        // When/Then
        testCartWithTotalPrice(0L);
        testCartWithTotalPrice(50000L);
        testCartWithTotalPrice(1000000L);
        testCartWithTotalPrice(9999999999L);
    }

    private void testCartWithTotalPrice(Long totalPrice) {
        Cart cart = Cart.builder().totalPrice(totalPrice).build();
        assertEquals(totalPrice, cart.getTotalPrice());
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("경계값 - 0개 아이템")
    void testBoundary_ZeroItems() {
        // When
        Cart cart = Cart.builder()
                .totalItems(0)
                .totalPrice(0L)
                .build();

        // Then
        assertEquals(0, cart.getTotalItems());
        assertEquals(0L, cart.getTotalPrice());
    }

    @Test
    @DisplayName("경계값 - 높은 아이템 개수")
    void testBoundary_HighItemCount() {
        // When
        Cart cart = Cart.builder()
                .totalItems(Integer.MAX_VALUE)
                .build();

        // Then
        assertEquals(Integer.MAX_VALUE, cart.getTotalItems());
    }

    @Test
    @DisplayName("경계값 - 높은 금액")
    void testBoundary_HighPrice() {
        // When
        Cart cart = Cart.builder()
                .totalPrice(Long.MAX_VALUE)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE, cart.getTotalPrice());
    }

    @Test
    @DisplayName("경계값 - ID 값")
    void testBoundary_IdValues() {
        // When
        Cart cart = Cart.builder()
                .cartId(Long.MAX_VALUE)
                .userId(Long.MAX_VALUE)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE, cart.getCartId());
        assertEquals(Long.MAX_VALUE, cart.getUserId());
    }

    // ========== null 안전성 ==========

    @Test
    @DisplayName("null 안전성 - 모든 필드 null")
    void testNullSafety_AllFields() {
        // When
        Cart cart = Cart.builder().build();

        // Then
        assertNull(cart.getCartId());
        assertNull(cart.getUserId());
        assertNull(cart.getTotalItems());
        assertNull(cart.getTotalPrice());
        assertNull(cart.getCreatedAt());
        assertNull(cart.getUpdatedAt());
    }

    @Test
    @DisplayName("null 안전성 - 선택적 필드 설정")
    void testNullSafety_PartialFields() {
        // When
        Cart cart = Cart.builder()
                .cartId(1L)
                .userId(TEST_USER_ID)
                .build();

        // Then
        assertEquals(1L, cart.getCartId());
        assertEquals(TEST_USER_ID, cart.getUserId());
        assertNull(cart.getTotalItems());
        assertNull(cart.getTotalPrice());
    }

    // ========== NoArgsConstructor/AllArgsConstructor ==========

    @Test
    @DisplayName("NoArgsConstructor 테스트")
    void testNoArgsConstructor() {
        // When
        Cart cart = new Cart();

        // Then
        assertNull(cart.getCartId());
        assertNull(cart.getUserId());
    }

    @Test
    @DisplayName("AllArgsConstructor 테스트")
    void testAllArgsConstructor() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Cart cart = new Cart(1L, TEST_USER_ID, 5, 100000L, now, now);

        // Then
        assertEquals(1L, cart.getCartId());
        assertEquals(TEST_USER_ID, cart.getUserId());
        assertEquals(5, cart.getTotalItems());
        assertEquals(100000L, cart.getTotalPrice());
    }

    // ========== Cart 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 장바구니 생성 후 상품 추가")
    void testScenario_CreateCartThenAddItems() {
        // Given: 새로운 장바구니
        Cart cart = Cart.builder()
                .cartId(1L)
                .userId(TEST_USER_ID)
                .totalItems(0)
                .totalPrice(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When: 상품 추가
        cart.setTotalItems(1);
        cart.setTotalPrice(50000L);

        // Then
        assertEquals(1, cart.getTotalItems());
        assertEquals(50000L, cart.getTotalPrice());
    }

    @Test
    @DisplayName("사용 시나리오 - 장바구니에서 상품 제거")
    void testScenario_RemoveItemsFromCart() {
        // Given: 아이템이 있는 장바구니
        Cart cart = Cart.builder()
                .cartId(1L)
                .userId(TEST_USER_ID)
                .totalItems(3)
                .totalPrice(150000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When: 아이템 제거
        cart.setTotalItems(2);
        cart.setTotalPrice(100000L);
        LocalDateTime updatedTime = LocalDateTime.now();
        cart.setUpdatedAt(updatedTime);

        // Then
        assertEquals(2, cart.getTotalItems());
        assertEquals(100000L, cart.getTotalPrice());
        assertEquals(updatedTime, cart.getUpdatedAt());
    }
}
