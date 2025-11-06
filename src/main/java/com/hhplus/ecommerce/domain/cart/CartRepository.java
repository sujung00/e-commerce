package com.hhplus.ecommerce.domain.cart;

import java.util.Optional;

/**
 * Cart Repository Interface (Domain Layer - Port)
 * 의존성 역전: 구현체는 이 인터페이스에 의존한다.
 */
public interface CartRepository {
    Cart findOrCreateByUserId(Long userId);

    Optional<Cart> findByUserId(Long userId);

    Optional<CartItem> findCartItemById(Long cartItemId);

    CartItem saveCartItem(CartItem cartItem);

    void deleteCartItem(Long cartItemId);

    Cart saveCart(Cart cart);
}