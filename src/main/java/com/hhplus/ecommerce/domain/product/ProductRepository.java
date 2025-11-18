package com.hhplus.ecommerce.domain.product;

import java.util.List;
import java.util.Optional;

/**
 * Product Repository Interface (Domain Layer - Port)
 * 상품 및 상품 옵션 데이터 접근 인터페이스
 * 의존성 역전: 구현체는 이 인터페이스에 의존한다.
 */
public interface ProductRepository {

    /**
     * 모든 상품 조회
     */
    List<Product> findAll();

    /**
     * ID로 상품 조회
     */
    Optional<Product> findById(Long productId);

    /**
     * 상품 ID로 옵션들 조회
     */
    List<ProductOption> findOptionsByProductId(Long productId);

    /**
     * 옵션 ID로 옵션 조회
     */
    Optional<ProductOption> findOptionById(Long optionId);

    /**
     * 최근 3일 주문 수량 조회
     * Application 계층에서 인기 상품 계산 시 사용
     */
    Long getOrderCount3Days(Long productId);

    /**
     * 최근 3일간 주문된 상품만 조회 (인기 상품 계산 최적화)
     * - 성능 개선: findAll() 대신 실제 주문이 있는 상품만 조회
     * - 커버링 인덱스 활용 가능
     * - 많은 상품 중 일부만 로드
     */
    List<Product> findProductsOrderedLast3Days();

    /**
     * 상품 저장
     */
    void save(Product product);

    /**
     * 옵션 저장
     */
    void saveOption(ProductOption option);
}