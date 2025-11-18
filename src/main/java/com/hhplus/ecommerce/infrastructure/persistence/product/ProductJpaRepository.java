package com.hhplus.ecommerce.infrastructure.persistence.product;

import com.hhplus.ecommerce.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Product JPA Repository
 * Spring Data JPA를 통한 Product 엔티티 영구 저장소
 *
 * ✅ FetchType 정책 (2025-11-18):
 * - Product.options: FetchType.LAZY로 변경
 * - 필요한 조회 시 fetch join 사용
 * - N+1 문제 방지를 위해 명시적 fetch join 적용
 */
public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    /**
     * 상품 ID로 조회 (options 함께 로드)
     * ✅ fetch join으로 productOptions 함께 로드
     */
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.options po " +
           "WHERE p.productId = :productId")
    Optional<Product> findByIdWithOptions(@Param("productId") Long productId);
}
