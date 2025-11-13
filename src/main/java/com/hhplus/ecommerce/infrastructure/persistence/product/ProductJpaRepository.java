package com.hhplus.ecommerce.infrastructure.persistence.product;

import com.hhplus.ecommerce.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Product JPA Repository
 * Spring Data JPA를 통한 Product 엔티티 영구 저장소
 */
public interface ProductJpaRepository extends JpaRepository<Product, Long> {
}
