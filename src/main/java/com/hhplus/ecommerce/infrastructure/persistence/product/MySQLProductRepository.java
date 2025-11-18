package com.hhplus.ecommerce.infrastructure.persistence.product;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * MySQL 기반 Product Repository 구현
 * Spring Data JPA를 사용한 영구 저장소
 *
 * Port(ProductRepository) 인터페이스를 구현하면서 JpaRepository 기능 제공
 */
@Repository
@Primary
public class MySQLProductRepository implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductOptionJpaRepository productOptionJpaRepository;

    public MySQLProductRepository(ProductJpaRepository productJpaRepository,
                                  ProductOptionJpaRepository productOptionJpaRepository) {
        this.productJpaRepository = productJpaRepository;
        this.productOptionJpaRepository = productOptionJpaRepository;
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAll();
    }

    @Override
    public Optional<Product> findById(Long productId) {
        // ✅ FetchType.LAZY: options를 함께 로드하기 위해 fetch join 사용
        return productJpaRepository.findByIdWithOptions(productId);
    }

    @Override
    public List<ProductOption> findOptionsByProductId(Long productId) {
        return productOptionJpaRepository.findByProductId(productId);
    }

    @Override
    public Optional<ProductOption> findOptionById(Long optionId) {
        return productOptionJpaRepository.findById(optionId);
    }

    @Override
    public Long getOrderCount3Days(Long productId) {
        // 최근 3일간의 주문 수는 0으로 초기화 (인기상품 계산용)
        // 실시간 인기상품 계산은 별도의 배치 작업이나 캐시로 처리
        return 0L;
    }

    @Override
    public List<Product> findProductsOrderedLast3Days() {
        // 최근 3일간 주문된 상품만 조회
        // - 성능 개선: 전체 상품이 아닌 실제 주문이 있는 상품만 로드
        // - 구현: order_items 테이블에서 최근 3일 주문 찾아서 해당 products 반환
        // - 현재는 전체 상품 반환 (향후 최적화 필요)
        return productJpaRepository.findAll();
    }

    @Override
    public void save(Product product) {
        productJpaRepository.save(product);
    }

    @Override
    public void saveOption(ProductOption option) {
        productOptionJpaRepository.save(option);
    }
}
