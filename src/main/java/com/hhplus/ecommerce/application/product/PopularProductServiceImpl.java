package com.hhplus.ecommerce.application.product;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.presentation.product.response.PopularProductListResponse;
import com.hhplus.ecommerce.presentation.product.response.PopularProductView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PopularProductServiceImpl - 인기 상품 조회 비즈니스 로직 구현 (Application 계층)
 * 1시간 TTL 캐싱 적용
 */
@Service
public class PopularProductServiceImpl implements PopularProductService {

    private final ProductRepository productRepository;

    /**
     * 캐시 저장소
     * Key: 캐시 식별자 (고정값: "popular_products")
     * Value: CachedResponse (데이터 + 타임스탬프)
     */
    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    private static final String CACHE_KEY = "popular_products";
    private static final long CACHE_TTL_SECONDS = 3600; // 1시간

    public PopularProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public PopularProductListResponse getPopularProducts() {
        // 캐시에서 조회
        CachedResponse cachedResponse = cache.get(CACHE_KEY);

        // 캐시 유효성 검사 (TTL 만료 여부)
        if (cachedResponse != null && !isExpired(cachedResponse)) {
            return cachedResponse.getResponse();
        }

        // 캐시 미스 또는 TTL 만료 시 새로 계산
        PopularProductListResponse response = calculatePopularProducts();

        // 캐시에 저장 (현재 타임스탬프와 함께)
        cache.put(CACHE_KEY, new CachedResponse(response, LocalDateTime.now()));

        return response;
    }

    /**
     * 인기 상품 상위 5개 계산
     * 정렬 기준: orderCount3Days (내림차순, Repository에서 조회)
     * 포함 대상: 최근 3일간 주문된 상품만 (성능 최적화)
     * 반환값: PopularProductView 리스트 (rank 정보 포함)
     *
     * 성능 최적화:
     * - 전체 상품 대신 최근 3일 주문이 있는 상품만 조회 (findProductsOrderedLast3Days)
     * - 커버링 인덱스 활용으로 DB 쿼리 효율 개선
     * - 메모리 로드량 감소
     *
     * Note: orderCount3Days는 Infrastructure 계층(Repository)에서 별도로 조회하여
     * Application 계층에서 정렬 및 계산에 사용합니다.
     *
     * @return 상위 5개 상품 응답
     */
    private PopularProductListResponse calculatePopularProducts() {
        // 1. 최근 3일간 주문된 상품만 조회 (성능 개선)
        List<Product> allProducts = productRepository.findProductsOrderedLast3Days();

        // 2. 각 상품에 대해 Infrastructure 계층에서 orderCount3Days 조회하여 정렬
        List<PopularProductView> topProducts = allProducts.stream()
                .map(product -> {
                    Long orderCount3Days = productRepository.getOrderCount3Days(product.getProductId());
                    return new ProductWithOrderCount(product, orderCount3Days);
                })
                .sorted((p1, p2) -> Long.compare(p2.orderCount3Days, p1.orderCount3Days))
                .limit(5)
                .map((p) -> PopularProductView.builder()
                        .productId(p.product.getProductId())
                        .productName(p.product.getProductName())
                        .price(p.product.getPrice())
                        .totalStock(p.product.getTotalStock())
                        .status(p.product.getStatus())
                        .orderCount3Days(p.orderCount3Days)
                        .rank(0) // 임시 값, 아래에서 업데이트
                        .createdAt(p.product.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // 3. 순위 정보 추가
        for (int i = 0; i < topProducts.size(); i++) {
            PopularProductView view = topProducts.get(i);
            topProducts.set(i, PopularProductView.builder()
                    .productId(view.getProductId())
                    .productName(view.getProductName())
                    .price(view.getPrice())
                    .totalStock(view.getTotalStock())
                    .status(view.getStatus())
                    .orderCount3Days(view.getOrderCount3Days())
                    .rank(i + 1)
                    .createdAt(view.getCreatedAt())
                    .build());
        }

        return new PopularProductListResponse(topProducts);
    }

    /**
     * 상품과 주문 수량을 임시로 보관하는 내부 클래스
     * Application 계층에서 Domain과 Infrastructure 계층의 데이터를 조합할 때 사용
     */
    private static class ProductWithOrderCount {
        final Product product;
        final Long orderCount3Days;

        ProductWithOrderCount(Product product, Long orderCount3Days) {
            this.product = product;
            this.orderCount3Days = orderCount3Days;
        }
    }

    /**
     * 캐시 유효성 검사
     * TTL(1시간)을 초과했는지 확인
     *
     * @param cachedResponse 캐시된 응답
     * @return true: TTL 만료됨, false: 아직 유효함
     */
    private boolean isExpired(CachedResponse cachedResponse) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cachedAt = cachedResponse.getCachedAt();
        long elapsedSeconds = java.time.temporal.ChronoUnit.SECONDS.between(cachedAt, now);
        return elapsedSeconds >= CACHE_TTL_SECONDS;
    }

    /**
     * 캐시된 응답과 타임스탬프를 담는 내부 클래스
     */
    private static class CachedResponse {
        private final PopularProductListResponse response;
        private final LocalDateTime cachedAt;

        public CachedResponse(PopularProductListResponse response, LocalDateTime cachedAt) {
            this.response = response;
            this.cachedAt = cachedAt;
        }

        public PopularProductListResponse getResponse() {
            return response;
        }

        public LocalDateTime getCachedAt() {
            return cachedAt;
        }
    }
}
