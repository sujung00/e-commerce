package com.hhplus.ecommerce.infrastructure.persistence.cart;

import com.hhplus.ecommerce.domain.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * CartItem JPA Repository
 * Spring Data JPA를 통한 CartItem 엔티티 영구 저장소
 */
public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartId(Long cartId);
}
