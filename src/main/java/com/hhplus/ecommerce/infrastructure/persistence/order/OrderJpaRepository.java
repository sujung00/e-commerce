package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Order JPA Repository
 * Spring Data JPA를 통한 Order 엔티티 영구 저장소
 *
 * ✅ FetchType 정책 (2025-11-18):
 * - Order.orderItems: FetchType.LAZY로 변경
 * - 필요한 조회 시 fetch join 사용
 * - N+1 문제 방지를 위해 명시적 fetch join 적용
 */
public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    /**
     * 사용자별 주문 조회 (페이지네이션)
     * ✅ fetch join으로 orderItems 함께 로드
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "WHERE o.userId = :userId " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByUserIdWithPagination(@Param("userId") Long userId, Pageable pageable);

    /**
     * 주문 ID로 조회 (orderItems 함께 로드)
     * ✅ fetch join으로 orderItems 함께 로드
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "WHERE o.orderId = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    long countByUserId(Long userId);
}
