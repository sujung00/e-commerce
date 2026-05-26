package com.hhplus.ecommerce.infrastructure.persistence.cart;

import com.hhplus.ecommerce.domain.cart.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Cart JPA Repository
 * Spring Data JPA를 통한 Cart 엔티티 영구 저장소
 */
public interface CartJpaRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);

    /**
     * 비관적 락으로 장바구니 조회 (SELECT ... FOR UPDATE)
     * 동시 항목 추가 시 Cart 행을 잠가 직렬화를 보장한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);
}
