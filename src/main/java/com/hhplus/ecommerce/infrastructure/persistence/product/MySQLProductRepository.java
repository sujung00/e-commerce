package com.hhplus.ecommerce.infrastructure.persistence.product;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 비관적 락을 사용하여 ProductOption 조회
     * SELECT ... FOR UPDATE로 즉시 락 획득
     *
     * 용도: 재고 차감 시 동시성 제어
     * 특징:
     * - 여러 스레드의 동시 접근 시 순서대로 처리
     * - 초과 판매 방지
     * - Race Condition 완벽 차단
     *
     * @param optionId 옵션 ID
     * @return 비관적 락이 적용된 ProductOption
     */
    public Optional<ProductOption> findOptionByIdForUpdate(Long optionId) {
        return productOptionJpaRepository.findByIdForUpdate(optionId);
    }

    @Override
    public Long getOrderCount3Days(Long productId) {
        // 최근 3일간의 주문 수는 0으로 초기화 (인기상품 계산용)
        // 실시간 인기상품 계산은 별도의 배치 작업이나 캐시로 처리
        return 0L;
    }

    /**
     * ✅ 배치 쿼리로 여러 상품의 최근 3일 주문 수량 조회 (Message 5)
     *
     * N+1 문제 해결:
     * - 기존 PopularProductServiceImpl: 각 상품마다 getOrderCount3Days() 호출
     * - 개선: 모든 상품의 주문 수량을 1번의 쿼리로 조회
     *
     * 반환 형태:
     * - Map<productId, orderCount> 형태로 메모리 lookup 최적화
     * - Stream API로 빠른 처리
     *
     * @param productIds 상품 ID 목록 (최근 3일 주문된 상품들)
     * @return productId별 최근 3일 주문 수량
     */
    public Map<Long, Long> getOrderCountsLast3Days(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new HashMap<>();
        }

        List<Map<String, Object>> results = productJpaRepository.countOrdersByProductIdsLast3Days(productIds);

        // 배치 쿼리 결과를 productId → orderCount Map으로 변환
        Map<Long, Long> orderCountMap = new HashMap<>();
        for (Map<String, Object> row : results) {
            Long productId = ((Number) row.get("productId")).longValue();
            Long orderCount = ((Number) row.get("orderCount")).longValue();
            orderCountMap.put(productId, orderCount);
        }

        return orderCountMap;
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
