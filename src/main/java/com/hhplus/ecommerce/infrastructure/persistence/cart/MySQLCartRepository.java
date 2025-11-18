package com.hhplus.ecommerce.infrastructure.persistence.cart;

import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import com.hhplus.ecommerce.domain.cart.CartRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 기반 Cart Repository 구현
 * Spring Data JPA를 사용한 영구 저장소
 *
 * Port(CartRepository) 인터페이스를 구현하면서 JpaRepository 기능 제공
 */
@Repository
@Primary
public class MySQLCartRepository implements CartRepository {

    private final CartJpaRepository cartJpaRepository;
    private final CartItemJpaRepository cartItemJpaRepository;

    public MySQLCartRepository(CartJpaRepository cartJpaRepository, CartItemJpaRepository cartItemJpaRepository) {
        this.cartJpaRepository = cartJpaRepository;
        this.cartItemJpaRepository = cartItemJpaRepository;
    }

    @Override
    public Cart findOrCreateByUserId(Long userId) {
        Optional<Cart> existing = cartJpaRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        // 새 장바구니 생성
        Cart newCart = Cart.builder()
                .userId(userId)
                .totalItems(0)
                .totalPrice(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return cartJpaRepository.save(newCart);
    }

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        return cartJpaRepository.findByUserId(userId);
    }

    @Override
    public Optional<CartItem> findCartItemById(Long cartItemId) {
        return cartItemJpaRepository.findById(cartItemId);
    }

    @Override
    public CartItem saveCartItem(CartItem cartItem) {
        return cartItemJpaRepository.save(cartItem);
    }

    @Override
    public void deleteCartItem(Long cartItemId) {
        cartItemJpaRepository.deleteById(cartItemId);
    }

    @Override
    public Cart saveCart(Cart cart) {
        return cartJpaRepository.save(cart);
    }

    @Override
    public List<CartItem> getCartItems(Long cartId) {
        return cartItemJpaRepository.findByCartId(cartId);
    }
}

