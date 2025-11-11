package com.hhplus.ecommerce.infrastructure.persistence.cart;

import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryCartRepository 단위 테스트
 * - Cart 저장 및 조회
 * - CartItem 관리
 * - ConcurrentHashMap 기반 동작 검증
 * - 스레드 안전성 검증
 */
@DisplayName("InMemoryCartRepository 테스트")
class InMemoryCartRepositoryTest {

    private InMemoryCartRepository cartRepository;

    @BeforeEach
    void setUp() {
        cartRepository = new InMemoryCartRepository();
    }

    // ========== Cart 조회 ==========

    @Test
    @DisplayName("findOrCreateByUserId - 기존 장바구니 조회")
    void testFindOrCreateByUserId_ExistingCart() {
        // When
        Cart cart = cartRepository.findOrCreateByUserId(100L);

        // Then
        assertNotNull(cart);
        assertEquals(100L, cart.getUserId());
        assertEquals(1L, cart.getCartId());
    }

    @Test
    @DisplayName("findOrCreateByUserId - 새 장바구니 생성")
    void testFindOrCreateByUserId_NewCart() {
        // When
        Cart cart = cartRepository.findOrCreateByUserId(200L);

        // Then
        assertNotNull(cart);
        assertEquals(200L, cart.getUserId());
        assertNotNull(cart.getCartId());
        assertEquals(0, cart.getTotalItems());
        assertEquals(0L, cart.getTotalPrice());
    }

    @Test
    @DisplayName("findOrCreateByUserId - 같은 사용자는 같은 장바구니 반환")
    void testFindOrCreateByUserId_SameUserSameCart() {
        // When
        Cart cart1 = cartRepository.findOrCreateByUserId(300L);
        Cart cart2 = cartRepository.findOrCreateByUserId(300L);

        // Then
        assertEquals(cart1.getCartId(), cart2.getCartId());
    }

    @Test
    @DisplayName("findByUserId - 기존 장바구니 조회")
    void testFindByUserId_ExistingCart() {
        // When
        Optional<Cart> cart = cartRepository.findByUserId(100L);

        // Then
        assertTrue(cart.isPresent());
        assertEquals(100L, cart.get().getUserId());
    }

    @Test
    @DisplayName("findByUserId - 없는 사용자는 Optional.empty() 반환")
    void testFindByUserId_NotFound() {
        // When
        Optional<Cart> cart = cartRepository.findByUserId(999L);

        // Then
        assertTrue(cart.isEmpty());
    }

    // ========== CartItem 관리 ==========

    @Test
    @DisplayName("findCartItemById - 기존 장바구니 아이템 조회")
    void testFindCartItemById_ExistingItem() {
        // When
        Optional<CartItem> item = cartRepository.findCartItemById(1L);

        // Then
        assertTrue(item.isPresent());
        assertEquals(1L, item.get().getCartItemId());
        assertEquals(1L, item.get().getProductId());
    }

    @Test
    @DisplayName("findCartItemById - 없는 아이템은 Optional.empty() 반환")
    void testFindCartItemById_NotFound() {
        // When
        Optional<CartItem> item = cartRepository.findCartItemById(999L);

        // Then
        assertTrue(item.isEmpty());
    }

    @Test
    @DisplayName("saveCartItem - 새 아이템 저장 (ID 자동 할당)")
    void testSaveCartItem_NewItem() {
        // When
        CartItem newItem = CartItem.builder()
                .cartId(1L)
                .productId(10L)
                .optionId(1001L)
                .quantity(3)
                .unitPrice(50000L)
                .subtotal(150000L)
                .build();

        CartItem saved = cartRepository.saveCartItem(newItem);

        // Then
        assertNotNull(saved.getCartItemId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(3, saved.getQuantity());
    }

    @Test
    @DisplayName("saveCartItem - 기존 아이템 업데이트")
    void testSaveCartItem_UpdateExistingItem() {
        // Given
        Optional<CartItem> item = cartRepository.findCartItemById(1L);
        assertTrue(item.isPresent());
        CartItem existingItem = item.get();

        // When
        existingItem.setQuantity(5);
        existingItem.setSubtotal(149500L);
        CartItem updated = cartRepository.saveCartItem(existingItem);

        // Then
        assertEquals(5, updated.getQuantity());
        assertEquals(1L, updated.getCartItemId());
    }

    @Test
    @DisplayName("saveCartItem - 타임스탐프 자동 업데이트")
    void testSaveCartItem_TimestampUpdate() {
        // Given
        CartItem item = CartItem.builder()
                .cartId(1L)
                .productId(10L)
                .quantity(1)
                .unitPrice(10000L)
                .build();

        // When
        LocalDateTime beforeSave = LocalDateTime.now();
        CartItem saved = cartRepository.saveCartItem(item);
        LocalDateTime afterSave = LocalDateTime.now();

        // Then
        assertFalse(saved.getUpdatedAt().isBefore(beforeSave));
        assertFalse(saved.getUpdatedAt().isAfter(afterSave.plusSeconds(1)));
    }

    @Test
    @DisplayName("deleteCartItem - 아이템 삭제")
    void testDeleteCartItem_DeleteExistingItem() {
        // When
        cartRepository.deleteCartItem(1L);

        // Then
        Optional<CartItem> deleted = cartRepository.findCartItemById(1L);
        assertTrue(deleted.isEmpty());
    }

    @Test
    @DisplayName("deleteCartItem - 없는 아이템 삭제는 에러 없음")
    void testDeleteCartItem_DeleteNonExistingItem() {
        // When/Then
        assertDoesNotThrow(() -> cartRepository.deleteCartItem(999L));
    }

    // ========== Cart 저장 ==========

    @Test
    @DisplayName("saveCart - 장바구니 저장")
    void testSaveCart_SaveCart() {
        // When
        Cart cart = cartRepository.findOrCreateByUserId(400L);
        cart.setTotalItems(5);
        cart.setTotalPrice(100000L);

        Cart saved = cartRepository.saveCart(cart);

        // Then
        assertEquals(5, saved.getTotalItems());
        assertEquals(100000L, saved.getTotalPrice());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("saveCart - 여러 번 저장 시 ID 유지")
    void testSaveCart_MultipleSaves() {
        // When
        Cart cart = cartRepository.findOrCreateByUserId(500L);
        Long originalId = cart.getCartId();

        cart.setTotalItems(1);
        cartRepository.saveCart(cart);

        cart.setTotalItems(2);
        cartRepository.saveCart(cart);

        // Then
        Cart retrieved = cartRepository.findByUserId(500L).get();
        assertEquals(originalId, retrieved.getCartId());
        assertEquals(2, retrieved.getTotalItems());
    }

    // ========== 헬퍼 메서드 ==========

    @Test
    @DisplayName("getCartItems - 특정 장바구니의 모든 아이템 조회")
    void testGetCartItems_GetAllItemsInCart() {
        // When
        List<CartItem> items = cartRepository.getCartItems(1L);

        // Then
        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals(1L, items.get(0).getProductId());
    }

    @Test
    @DisplayName("getCartItems - 없는 장바구니는 빈 리스트")
    void testGetCartItems_EmptyList() {
        // When
        List<CartItem> items = cartRepository.getCartItems(999L);

        // Then
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    // ========== 동시성 테스트 ==========

    @Test
    @DisplayName("동시성 - 여러 스레드에서 새 장바구니 생성")
    void testConcurrency_CreateCartsConcurrently() throws InterruptedException {
        // When
        Thread thread1 = new Thread(() -> cartRepository.findOrCreateByUserId(600L));
        Thread thread2 = new Thread(() -> cartRepository.findOrCreateByUserId(601L));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // Then
        assertTrue(cartRepository.findByUserId(600L).isPresent());
        assertTrue(cartRepository.findByUserId(601L).isPresent());
    }

    // ========== 데이터 초기화 검증 ==========

    @Test
    @DisplayName("초기화 데이터 - 기본 샘플 데이터 존재")
    void testInitialData_SampleDataExists() {
        // Then
        assertTrue(cartRepository.findByUserId(100L).isPresent());
        assertTrue(cartRepository.findByUserId(101L).isPresent());
        assertTrue(cartRepository.findCartItemById(1L).isPresent());
        assertTrue(cartRepository.findCartItemById(2L).isPresent());
    }

    @Test
    @DisplayName("초기화 데이터 - 장바구니 아이템 수 일치")
    void testInitialData_CartItemCounts() {
        // Then
        List<CartItem> cart1Items = cartRepository.getCartItems(1L);
        List<CartItem> cart2Items = cartRepository.getCartItems(2L);

        assertEquals(1, cart1Items.size());
        assertEquals(1, cart2Items.size());
    }

    // ========== 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 사용자의 장바구니에 상품 추가")
    void testScenario_AddProductToCart() {
        // Given
        Cart userCart = cartRepository.findOrCreateByUserId(700L);

        // When
        CartItem item = CartItem.builder()
                .cartId(userCart.getCartId())
                .productId(100L)
                .optionId(1001L)
                .quantity(2)
                .unitPrice(29900L)
                .subtotal(59800L)
                .build();

        CartItem savedItem = cartRepository.saveCartItem(item);
        userCart.setTotalItems(1);
        userCart.setTotalPrice(59800L);
        cartRepository.saveCart(userCart);

        // Then
        Optional<CartItem> retrieved = cartRepository.findCartItemById(savedItem.getCartItemId());
        assertTrue(retrieved.isPresent());
        assertEquals(100L, retrieved.get().getProductId());
    }

    @Test
    @DisplayName("사용 시나리오 - 장바구니 조회 후 상품 수정")
    void testScenario_UpdateCartItem() {
        // Given
        Cart cart = cartRepository.findOrCreateByUserId(800L);
        CartItem item = CartItem.builder()
                .cartId(cart.getCartId())
                .productId(200L)
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .build();
        CartItem saved = cartRepository.saveCartItem(item);

        // When
        saved.setQuantity(3);
        saved.setSubtotal(150000L);
        CartItem updated = cartRepository.saveCartItem(saved);

        // Then
        Optional<CartItem> retrieved = cartRepository.findCartItemById(updated.getCartItemId());
        assertTrue(retrieved.isPresent());
        assertEquals(3, retrieved.get().getQuantity());
        assertEquals(150000L, retrieved.get().getSubtotal());
    }

    @Test
    @DisplayName("사용 시나리오 - 장바구니 상품 삭제 후 재조회")
    void testScenario_DeleteAndCheckCart() {
        // Given
        Cart cart = cartRepository.findOrCreateByUserId(900L);
        CartItem item = CartItem.builder()
                .cartId(cart.getCartId())
                .productId(300L)
                .quantity(1)
                .unitPrice(30000L)
                .build();
        CartItem saved = cartRepository.saveCartItem(item);

        // When
        cartRepository.deleteCartItem(saved.getCartItemId());
        Optional<CartItem> deleted = cartRepository.findCartItemById(saved.getCartItemId());

        // Then
        assertTrue(deleted.isEmpty());
        Optional<Cart> cartStillExists = cartRepository.findByUserId(900L);
        assertTrue(cartStillExists.isPresent());
    }
}
