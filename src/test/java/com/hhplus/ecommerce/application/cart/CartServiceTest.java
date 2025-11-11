package com.hhplus.ecommerce.application.cart;

import com.hhplus.ecommerce.domain.cart.*;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.infrastructure.persistence.cart.InMemoryCartRepository;
import com.hhplus.ecommerce.presentation.cart.request.AddCartItemRequest;
import com.hhplus.ecommerce.presentation.cart.request.UpdateQuantityRequest;
import com.hhplus.ecommerce.presentation.cart.response.CartItemResponse;
import com.hhplus.ecommerce.presentation.cart.response.CartResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CartServiceTest - Application 계층 단위 테스트
 * Spring Boot 3.4+ Mockito 방식 테스트
 *
 * 테스트 대상: CartService
 * - 장바구니 조회
 * - 아이템 추가
 * - 수량 수정
 * - 아이템 제거
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 비즈니스 로직 처리
 * - 예외 케이스: 사용자 검증, 수량 검증, 아이템 검증
 * - 트랜잭션 처리: In-Memory 저장소 동작 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    private CartService cartService;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InMemoryCartRepository inMemoryCartRepository;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_CART_ID = 100L;
    private static final Long TEST_PRODUCT_ID = 1L;
    private static final Long TEST_OPTION_ID = 101L;
    private static final Long TEST_CART_ITEM_ID = 50L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        cartService = new CartService(cartRepository, userRepository, inMemoryCartRepository);
    }

    // ========== 장바구니 조회 (getCartByUserId) ==========

    @Test
    @DisplayName("장바구니 조회 - 성공")
    void testGetCartByUserId_Success() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Cart cart = Cart.builder()
                .cartId(TEST_CART_ID)
                .userId(TEST_USER_ID)
                .totalItems(2)
                .totalPrice(200000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(inMemoryCartRepository.findOrCreateByUserId(TEST_USER_ID)).thenReturn(cart);

        CartItem item1 = CartItem.builder()
                .cartItemId(1L)
                .cartId(TEST_CART_ID)
                .productId(1L)
                .optionId(101L)
                .quantity(1)
                .unitPrice(29900L)
                .subtotal(29900L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CartItem item2 = CartItem.builder()
                .cartItemId(2L)
                .cartId(TEST_CART_ID)
                .productId(2L)
                .optionId(201L)
                .quantity(1)
                .unitPrice(79900L)
                .subtotal(79900L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<CartItem> cartItems = List.of(item1, item2);
        when(inMemoryCartRepository.getCartItems(TEST_CART_ID)).thenReturn(cartItems);

        // When
        CartResponseDto result = cartService.getCartByUserId(TEST_USER_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_CART_ID, result.getCartId());
        assertEquals(TEST_USER_ID, result.getUserId());
        assertEquals(2, result.getTotalItems());
        assertEquals(109800L, result.getTotalPrice());
        assertEquals(2, result.getItems().size());

        verify(userRepository, times(1)).existsById(TEST_USER_ID);
        verify(inMemoryCartRepository, times(1)).findOrCreateByUserId(TEST_USER_ID);
    }

    @Test
    @DisplayName("장바구니 조회 - 빈 장바구니")
    void testGetCartByUserId_EmptyCart() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Cart cart = Cart.builder()
                .cartId(TEST_CART_ID)
                .userId(TEST_USER_ID)
                .totalItems(0)
                .totalPrice(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(inMemoryCartRepository.findOrCreateByUserId(TEST_USER_ID)).thenReturn(cart);
        when(inMemoryCartRepository.getCartItems(TEST_CART_ID)).thenReturn(new ArrayList<>());

        // When
        CartResponseDto result = cartService.getCartByUserId(TEST_USER_ID);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getTotalItems());
        assertEquals(0L, result.getTotalPrice());
        assertEquals(0, result.getItems().size());
    }

    @Test
    @DisplayName("장바구니 조회 - 실패 (사용자 없음)")
    void testGetCartByUserId_Failed_UserNotFound() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(false);

        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            cartService.getCartByUserId(TEST_USER_ID);
        });

        verify(userRepository, times(1)).existsById(TEST_USER_ID);
        verify(inMemoryCartRepository, never()).findOrCreateByUserId(anyLong());
    }

    // ========== 아이템 추가 (addItem) ==========

    @Test
    @DisplayName("장바구니 아이템 추가 - 성공")
    void testAddItem_Success() {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(2)
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Cart cart = Cart.builder()
                .cartId(TEST_CART_ID)
                .userId(TEST_USER_ID)
                .totalItems(1)
                .totalPrice(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(inMemoryCartRepository.findOrCreateByUserId(TEST_USER_ID)).thenReturn(cart);

        CartItem newItem = CartItem.builder()
                .cartItemId(TEST_CART_ITEM_ID)
                .cartId(TEST_CART_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(2)
                .unitPrice(29900L)
                .subtotal(59800L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(inMemoryCartRepository.saveCartItem(any(CartItem.class))).thenReturn(newItem);
        when(inMemoryCartRepository.getCartItems(TEST_CART_ID)).thenReturn(List.of(newItem));
        when(inMemoryCartRepository.saveCart(any(Cart.class))).thenReturn(cart);

        // When
        CartItemResponse result = cartService.addItem(TEST_USER_ID, request);

        // Then
        assertNotNull(result);
        assertEquals(TEST_CART_ITEM_ID, result.getCartItemId());
        assertEquals(TEST_PRODUCT_ID, result.getProductId());
        assertEquals(TEST_OPTION_ID, result.getOptionId());
        assertEquals(2, result.getQuantity());

        verify(userRepository, times(1)).existsById(TEST_USER_ID);
        verify(inMemoryCartRepository, times(1)).saveCartItem(any(CartItem.class));
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 실패 (수량 0)")
    void testAddItem_Failed_ZeroQuantity() {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(0)
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        // When & Then
        assertThrows(InvalidQuantityException.class, () -> {
            cartService.addItem(TEST_USER_ID, request);
        });
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 실패 (수량 초과)")
    void testAddItem_Failed_ExceededQuantity() {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1001)
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        // When & Then
        assertThrows(InvalidQuantityException.class, () -> {
            cartService.addItem(TEST_USER_ID, request);
        });
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 실패 (사용자 없음)")
    void testAddItem_Failed_UserNotFound() {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1)
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(false);

        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            cartService.addItem(TEST_USER_ID, request);
        });
    }

    // ========== 수량 수정 (updateItemQuantity) ==========

    @Test
    @DisplayName("장바구니 수량 수정 - 성공")
    void testUpdateItemQuantity_Success() {
        // Given
        UpdateQuantityRequest request = UpdateQuantityRequest.builder()
                .quantity(5)
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Cart cart = Cart.builder()
                .cartId(TEST_CART_ID)
                .userId(TEST_USER_ID)
                .totalItems(1)
                .totalPrice(250000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CartItem cartItem = CartItem.builder()
                .cartItemId(TEST_CART_ITEM_ID)
                .cartId(TEST_CART_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(5)
                .unitPrice(50000L)
                .subtotal(250000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(inMemoryCartRepository.findCartItemById(TEST_CART_ITEM_ID))
                .thenReturn(Optional.of(cartItem));
        when(inMemoryCartRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(cart));
        when(inMemoryCartRepository.saveCartItem(any(CartItem.class)))
                .thenReturn(cartItem);
        when(inMemoryCartRepository.getCartItems(TEST_CART_ID))
                .thenReturn(List.of(cartItem));
        when(inMemoryCartRepository.saveCart(any(Cart.class)))
                .thenReturn(cart);

        // When
        CartItemResponse result = cartService.updateItemQuantity(TEST_USER_ID, TEST_CART_ITEM_ID, request);

        // Then
        assertNotNull(result);
        assertEquals(TEST_CART_ITEM_ID, result.getCartItemId());
        assertEquals(5, result.getQuantity());
        assertEquals(250000L, result.getSubtotal());

        verify(inMemoryCartRepository, times(1)).findCartItemById(TEST_CART_ITEM_ID);
        verify(inMemoryCartRepository, times(1)).saveCartItem(any(CartItem.class));
    }

    @Test
    @DisplayName("장바구니 수량 수정 - 실패 (음수 수량)")
    void testUpdateItemQuantity_Failed_NegativeQuantity() {
        // Given
        UpdateQuantityRequest request = UpdateQuantityRequest.builder()
                .quantity(-1)
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        // When & Then
        assertThrows(InvalidQuantityException.class, () -> {
            cartService.updateItemQuantity(TEST_USER_ID, TEST_CART_ITEM_ID, request);
        });
    }

    @Test
    @DisplayName("장바구니 수량 수정 - 실패 (아이템 없음)")
    void testUpdateItemQuantity_Failed_ItemNotFound() {
        // Given
        UpdateQuantityRequest request = UpdateQuantityRequest.builder()
                .quantity(5)
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(inMemoryCartRepository.findCartItemById(TEST_CART_ITEM_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(CartItemNotFoundException.class, () -> {
            cartService.updateItemQuantity(TEST_USER_ID, TEST_CART_ITEM_ID, request);
        });
    }

    // ========== 아이템 제거 (removeItem) ==========

    @Test
    @DisplayName("장바구니 아이템 제거 - 성공")
    void testRemoveItem_Success() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Cart cart = Cart.builder()
                .cartId(TEST_CART_ID)
                .userId(TEST_USER_ID)
                .totalItems(1)
                .totalPrice(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CartItem cartItem = CartItem.builder()
                .cartItemId(TEST_CART_ITEM_ID)
                .cartId(TEST_CART_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(inMemoryCartRepository.findCartItemById(TEST_CART_ITEM_ID))
                .thenReturn(Optional.of(cartItem));
        when(inMemoryCartRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(cart));
        when(inMemoryCartRepository.getCartItems(TEST_CART_ID))
                .thenReturn(new ArrayList<>());
        when(inMemoryCartRepository.saveCart(any(Cart.class)))
                .thenReturn(cart);

        // When
        cartService.removeItem(TEST_USER_ID, TEST_CART_ITEM_ID);

        // Then
        verify(inMemoryCartRepository, times(1)).deleteCartItem(TEST_CART_ITEM_ID);
        verify(inMemoryCartRepository, times(1)).saveCart(any(Cart.class));
    }

    @Test
    @DisplayName("장바구니 아이템 제거 - 실패 (아이템 없음)")
    void testRemoveItem_Failed_ItemNotFound() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(inMemoryCartRepository.findCartItemById(TEST_CART_ITEM_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(CartItemNotFoundException.class, () -> {
            cartService.removeItem(TEST_USER_ID, TEST_CART_ITEM_ID);
        });
    }

    @Test
    @DisplayName("장바구니 아이템 제거 - 실패 (사용자 불일치)")
    void testRemoveItem_Failed_UserMismatch() {
        // Given
        Long differentUserId = 999L;
        Cart wrongCart = Cart.builder()
                .cartId(999L)
                .userId(differentUserId)
                .totalItems(0)
                .totalPrice(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CartItem cartItem = CartItem.builder()
                .cartItemId(TEST_CART_ITEM_ID)
                .cartId(TEST_CART_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(inMemoryCartRepository.findCartItemById(TEST_CART_ITEM_ID))
                .thenReturn(Optional.of(cartItem));
        when(inMemoryCartRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(wrongCart));

        // When & Then
        assertThrows(CartItemNotFoundException.class, () -> {
            cartService.removeItem(TEST_USER_ID, TEST_CART_ITEM_ID);
        });
    }
}
