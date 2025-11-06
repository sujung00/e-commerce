package com.hhplus.ecommerce.infrastructure.persistence.cart;

import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InMemory Cart Repository 구현
 * ConcurrentHashMap 기반의 인메모리 저장소
 */
@Repository
public class CartRepository implements com.hhplus.ecommerce.domain.cart.CartRepository {

    private final ConcurrentHashMap<Long, Cart> carts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CartItem> cartItems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> userCartMap = new ConcurrentHashMap<>(); // userId -> cartId 매핑

    private final AtomicLong cartIdGenerator = new AtomicLong(0);
    private final AtomicLong cartItemIdGenerator = new AtomicLong(0);

    public CartRepository() {
        initializeSampleData();
    }

    /**
     * 샘플 데이터 초기화
     */
    private void initializeSampleData() {
        // Cart 1: User 100
        Cart cart1 = Cart.builder()
                .cartId(1L)
                .userId(100L)
                .totalItems(0)
                .totalPrice(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        carts.put(1L, cart1);
        userCartMap.put(100L, 1L);
        cartIdGenerator.set(1);

        // Cart 2: User 101
        Cart cart2 = Cart.builder()
                .cartId(2L)
                .userId(101L)
                .totalItems(0)
                .totalPrice(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        carts.put(2L, cart2);
        userCartMap.put(101L, 2L);
        cartIdGenerator.set(2);

        // CartItem 1: User 100의 장바구니에 티셔츠 추가
        CartItem item1 = CartItem.builder()
                .cartItemId(1L)
                .cartId(1L)
                .productId(1L)
                .optionId(101L)
                .quantity(2)
                .unitPrice(29900L)
                .subtotal(59800L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        cartItems.put(1L, item1);

        // CartItem 2: User 101의 장바구니에 슬리퍼 추가
        CartItem item2 = CartItem.builder()
                .cartItemId(2L)
                .cartId(2L)
                .productId(5L)
                .optionId(501L)
                .quantity(1)
                .unitPrice(19900L)
                .subtotal(19900L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        cartItems.put(2L, item2);
        cartItemIdGenerator.set(2);

        // Cart 정보 업데이트
        cart1.setTotalItems(1);
        cart1.setTotalPrice(59800L);
        cart2.setTotalItems(1);
        cart2.setTotalPrice(19900L);
    }

    @Override
    public Cart findOrCreateByUserId(Long userId) {
        Long cartId = userCartMap.get(userId);

        if (cartId != null) {
            return carts.get(cartId);
        }

        // 새로운 카트 생성
        long newCartId = cartIdGenerator.incrementAndGet();
        Cart newCart = Cart.builder()
                .cartId(newCartId)
                .userId(userId)
                .totalItems(0)
                .totalPrice(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        carts.put(newCartId, newCart);
        userCartMap.put(userId, newCartId);

        return newCart;
    }

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        Long cartId = userCartMap.get(userId);
        if (cartId != null) {
            return Optional.of(carts.get(cartId));
        }
        return Optional.empty();
    }

    @Override
    public Optional<CartItem> findCartItemById(Long cartItemId) {
        return Optional.ofNullable(cartItems.get(cartItemId));
    }

    @Override
    public CartItem saveCartItem(CartItem cartItem) {
        if (cartItem.getCartItemId() == null) {
            long newId = cartItemIdGenerator.incrementAndGet();
            cartItem.setCartItemId(newId);
            cartItem.setCreatedAt(LocalDateTime.now());
        }
        cartItem.setUpdatedAt(LocalDateTime.now());
        cartItems.put(cartItem.getCartItemId(), cartItem);
        return cartItem;
    }

    @Override
    public void deleteCartItem(Long cartItemId) {
        cartItems.remove(cartItemId);
    }

    @Override
    public Cart saveCart(Cart cart) {
        cart.setUpdatedAt(LocalDateTime.now());
        carts.put(cart.getCartId(), cart);
        return cart;
    }

    /**
     * Cart의 아이템 목록 조회 (내부 사용)
     */
    public List<CartItem> getCartItems(Long cartId) {
        List<CartItem> items = new ArrayList<>();
        cartItems.forEach((itemId, item) -> {
            if (item.getCartId().equals(cartId)) {
                items.add(item);
            }
        });
        return items;
    }
}
