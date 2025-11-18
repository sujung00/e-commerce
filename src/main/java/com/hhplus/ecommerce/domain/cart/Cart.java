package com.hhplus.ecommerce.domain.cart;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cart 도메인 엔티티
 * 사용자별 쇼핑 카트 (1:1 관계)
 * total_items와 total_price는 cart_items에서 계산되는 계산 필드
 */
@Entity
@Table(name = "carts", uniqueConstraints = {
    @UniqueConstraint(columnNames = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_items", nullable = false)
    private Integer totalItems;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}