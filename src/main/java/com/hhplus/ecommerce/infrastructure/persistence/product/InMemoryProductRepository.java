package com.hhplus.ecommerce.infrastructure.persistence.product;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * InMemory Product Repository 구현
 * ConcurrentHashMap 기반의 인메모리 저장소 (Infrastructure 계층)
 *
 * Note: 최근 3일 주문 수량(orderCount3Days) 조회는 별도의 데이터 소스(e.g., orders table)에서
 * 조회해야 하며, 이는 Application 계층의 PopularProductService에서 처리합니다.
 */
@Repository
public class InMemoryProductRepository implements com.hhplus.ecommerce.domain.product.ProductRepository {

    // 상품 저장소
    private final Map<Long, Product> products = new HashMap<>();

    // 상품 옵션 저장소
    private final Map<Long, ProductOption> productOptions = new HashMap<>();

    // 상품-옵션 매핑
    private final Map<Long, List<Long>> productToOptionsMap = new HashMap<>();

    // 최근 3일 주문 수량 (Application 계층에서 사용)
    // 실제 환경에서는 Order 테이블에서 조회해야 함
    private final Map<Long, Long> orderCount3DaysMap = new HashMap<>();

    public InMemoryProductRepository() {
        initializeData();
    }

    /**
     * 예시 데이터 초기화
     */
    private void initializeData() {
        LocalDateTime now = LocalDateTime.of(2025, 10, 29, 10, 0, 0);

        // 상품 1의 옵션들
        ProductOption option101 = ProductOption.builder()
                .optionId(101L)
                .productId(1L)
                .name("블랙/M")
                .stock(30)
                .version(5L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ProductOption option102 = ProductOption.builder()
                .optionId(102L)
                .productId(1L)
                .name("블랙/L")
                .stock(25)
                .version(5L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ProductOption option103 = ProductOption.builder()
                .optionId(103L)
                .productId(1L)
                .name("화이트/M")
                .stock(45)
                .version(3L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 상품 2의 옵션들
        ProductOption option201 = ProductOption.builder()
                .optionId(201L)
                .productId(2L)
                .name("청색/32")
                .stock(40)
                .version(2L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ProductOption option202 = ProductOption.builder()
                .optionId(202L)
                .productId(2L)
                .name("청색/34")
                .stock(40)
                .version(2L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 상품 3의 옵션들
        ProductOption option301 = ProductOption.builder()
                .optionId(301L)
                .productId(3L)
                .name("검정/260mm")
                .stock(100)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ProductOption option302 = ProductOption.builder()
                .optionId(302L)
                .productId(3L)
                .name("흰색/260mm")
                .stock(100)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 상품들 생성
        Product product1 = Product.builder()
                .productId(1L)
                .productName("티셔츠")
                .description("100% 면 티셔츠")
                .price(29900L)
                .totalStock(100)
                .status("판매 중")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product product2 = Product.builder()
                .productId(2L)
                .productName("청바지")
                .description("고급 데님 청바지")
                .price(79900L)
                .totalStock(80)
                .status("판매 중")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product product3 = Product.builder()
                .productId(3L)
                .productName("슬리퍼")
                .description("편안한 실내용 슬리퍼")
                .price(19900L)
                .totalStock(200)
                .status("판매 중")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product product4 = Product.builder()
                .productId(4L)
                .productName("후드 집업")
                .description("따뜘한 겨울용 후드")
                .price(49900L)
                .totalStock(150)
                .status("판매 중")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product product5 = Product.builder()
                .productId(5L)
                .productName("치마")
                .description("세련된 스타일 치마")
                .price(39900L)
                .totalStock(75)
                .status("판매 중")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product product6 = Product.builder()
                .productId(6L)
                .productName("운동화")
                .description("편안한 운동화")
                .price(69900L)
                .totalStock(120)
                .status("판매 중")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product product7 = Product.builder()
                .productId(7L)
                .productName("스카프")
                .description("따뜘한 울 스카프")
                .price(24900L)
                .totalStock(0)
                .status("품절")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product product8 = Product.builder()
                .productId(8L)
                .productName("모자")
                .description("캐쥬얼 스타일 모자")
                .price(34900L)
                .totalStock(50)
                .status("판매 중")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product product9 = Product.builder()
                .productId(9L)
                .productName("장갑")
                .description("따뜻한 겨울 장갑")
                .price(19900L)
                .totalStock(100)
                .status("판매 중지")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product product10 = Product.builder()
                .productId(10L)
                .productName("양말")
                .description("편안한 면 양말")
                .price(9900L)
                .totalStock(500)
                .status("판매 중")
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 상품에 옵션 할당
        product1.getOptions().addAll(List.of(option101, option102, option103));
        product2.getOptions().addAll(List.of(option201, option202));
        product3.getOptions().addAll(List.of(option301, option302));

        // 상품 저장
        products.put(1L, product1);
        products.put(2L, product2);
        products.put(3L, product3);
        products.put(4L, product4);
        products.put(5L, product5);
        products.put(6L, product6);
        products.put(7L, product7);
        products.put(8L, product8);
        products.put(9L, product9);
        products.put(10L, product10);

        // 상품 옵션 저장
        productOptions.put(101L, option101);
        productOptions.put(102L, option102);
        productOptions.put(103L, option103);
        productOptions.put(201L, option201);
        productOptions.put(202L, option202);
        productOptions.put(301L, option301);
        productOptions.put(302L, option302);

        // 상품-옵션 매핑
        productToOptionsMap.put(1L, List.of(101L, 102L, 103L));
        productToOptionsMap.put(2L, List.of(201L, 202L));
        productToOptionsMap.put(3L, List.of(301L, 302L));

        // 최근 3일 주문 수량 초기화 (Application 계층에서 조회 시 사용)
        // 실제 환경에서는 Order 테이블에서 동적으로 계산
        initializeOrderCount3Days();
    }

    /**
     * 최근 3일 주문 수량 초기화
     * 실제 환경에서는 Order 테이블에서 동적으로 계산해야 합니다.
     */
    private void initializeOrderCount3Days() {
        orderCount3DaysMap.put(1L, 150L);  // 티셔츠
        orderCount3DaysMap.put(2L, 120L);  // 청바지
        orderCount3DaysMap.put(3L, 180L);  // 슬리퍼
        orderCount3DaysMap.put(4L, 95L);   // 후드 집업
        orderCount3DaysMap.put(5L, 110L);  // 치마
        orderCount3DaysMap.put(6L, 85L);   // 운동화
        orderCount3DaysMap.put(7L, 200L);  // 스카프 (품절)
        orderCount3DaysMap.put(8L, 75L);   // 모자
        orderCount3DaysMap.put(9L, 30L);   // 장갑 (판매 중지)
        orderCount3DaysMap.put(10L, 65L);  // 양말
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(products.values());
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return Optional.ofNullable(products.get(productId));
    }

    @Override
    public List<ProductOption> findOptionsByProductId(Long productId) {
        return productToOptionsMap.getOrDefault(productId, Collections.emptyList())
                .stream()
                .map(productOptions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ProductOption> findOptionById(Long optionId) {
        return Optional.ofNullable(productOptions.get(optionId));
    }

    @Override
    public Long getOrderCount3Days(Long productId) {
        return orderCount3DaysMap.getOrDefault(productId, 0L);
    }

    @Override
    public void save(Product product) {
        products.put(product.getProductId(), product);
    }

    @Override
    public void saveOption(ProductOption option) {
        productOptions.put(option.getOptionId(), option);
    }
}
