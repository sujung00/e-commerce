package com.hhplus.ecommerce.infrastructure.persistence.product;

import com.hhplus.ecommerce.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Product JPA Repository
 * Spring Data JPA를 통한 Product 엔티티 영구 저장소
 */
public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    /**
     * 최근 3일 동안 주문된 상품의 주문 수량 조회
     *
     * @param productId 상품 ID
     * @return 최근 3일간 해당 상품의 주문 수량
     */
    @Query("SELECT COUNT(DISTINCT o.orderId) FROM Order o " +
           "INNER JOIN o.orderItems oi " +
           "WHERE oi.productId = :productId " +
           "AND o.createdAt >= CURRENT_TIMESTAMP - 3")
    Long countOrdersInLast3Days(@Param("productId") Long productId);
}
