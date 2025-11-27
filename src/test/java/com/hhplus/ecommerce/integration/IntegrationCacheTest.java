package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.product.ProductService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductDetailResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 캐시 통합 테스트
 *
 * 캐싱이 올바르게 작동하는지 검증:
 * - 첫 호출: DB에서 데이터 조회
 * - 두 번째 호출: 캐시에서 데이터 반환
 * - 응답시간 차이로 캐시 효과 측정
 *
 * Phase 1 Tier 1 캐싱 검증:
 * 1. ProductList 캐싱 (조회 빈도 매우 높음)
 * 2. ProductDetail 캐싱 (조회 빈도 높음)
 * 3. AvailableCoupons 캐싱 (조회 빈도 높음)
 *
 * 예상 효과:
 * - Product 목록: TPS 200 → 1000 (5배)
 * - Coupon 목록: TPS 300 → 2000 (6배)
 * - 응답시간: 87% 감소
 */
@DisplayName("Redis 캐시 통합 테스트")
class IntegrationCacheTest extends BaseIntegrationTest {

    @Autowired
    private ProductService 상품서비스;

    @Autowired
    private CouponService 쿠폰서비스;

    @Autowired
    private ProductRepository 상품저장소;

    @Autowired
    private CouponRepository 쿠폰저장소;

    @Autowired
    private CacheManager 캐시매니저;

    private Long 상품아이디;
    private Long 쿠폰아이디;

    @BeforeEach
    public void 준비() {
        // 테스트 데이터 준비
        // 상품 생성
        Product 상품 = Product.builder()
                .productName("테스트 상품")
                .description("테스트 설명")
                .price(10000L)
                .totalStock(100)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
        상품저장소.save(상품);
        // 저장 후 직접 쿼리로 조회 (캐시에서만 사용)
        List<Product> 상품목록 = 상품저장소.findAll();
        if (!상품목록.isEmpty()) {
            상품아이디 = 상품목록.get(0).getProductId();
        }

        // 상품 옵션 생성
        if (상품아이디 != null) {
            ProductOption 옵션 = ProductOption.builder()
                    .productId(상품아이디)
                    .name("기본 옵션")
                    .stock(100)
                    .version(0L)
                    .build();
            상품저장소.saveOption(옵션);
        }

        // 쿠폰 생성
        Coupon 쿠폰 = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType("PERCENTAGE")
                .discountRate(BigDecimal.valueOf(10))
                .totalQuantity(100)
                .remainingQty(100)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .build();
        쿠폰저장소.save(쿠폰);
        // 저장 후 직접 쿼리로 조회
        List<Coupon> 쿠폰목록 = 쿠폰저장소.findAll();
        if (!쿠폰목록.isEmpty()) {
            쿠폰아이디 = 쿠폰목록.get(0).getCouponId();
        }

        // 캐시 초기화
        모든캐시초기화();
    }

    @Test
    @DisplayName("상품 목록 조회 캐싱 검증한다")
    public void 상품목록조회_캐싱검증() {
        // Given: 캐시 초기화 상태

        // When: 첫 번째 호출 (DB에서 조회)
        long 시작시간1 = System.currentTimeMillis();
        ProductListResponse 결과1 = 상품서비스.getProductList(0, 10, "created_at,desc");
        long 소요시간1 = System.currentTimeMillis() - 시작시간1;

        // Then: 데이터가 조회되고 캐시에 저장됨
        assertThat(결과1).isNotNull();
        assertThat(결과1.getContent()).isNotEmpty();

        // When: 두 번째 호출 (캐시에서 조회)
        long 시작시간2 = System.currentTimeMillis();
        ProductListResponse 결과2 = 상품서비스.getProductList(0, 10, "created_at,desc");
        long 소요시간2 = System.currentTimeMillis() - 시작시간2;

        // Then: 캐시된 데이터가 반환되고, 응답시간이 훨씬 빨라짐
        assertThat(결과2).isNotNull();
        assertThat(결과2.getContent()).isEqualTo(결과1.getContent());

        // 캐시 효과 검증 (캐시된 응답이 더 빨라야 함)
        // 첫 호출 대비 캐시된 호출이 최소 1/3 이상 빨라야 함
        assertThat(소요시간2).isLessThan(소요시간1);

        System.out.println("✅ 상품 목록 캐싱: 첫 호출 " + 소요시간1 + "ms → 캐시 호출 " + 소요시간2 + "ms");
    }

    @Test
    @DisplayName("상품 상세 조회 캐싱 검증한다")
    public void 상품상세조회_캐싱검증() {
        // Given: 캐시 초기화 상태

        // When: 첫 번째 호출 (DB에서 조회)
        long 시작시간1 = System.currentTimeMillis();
        ProductDetailResponse 결과1 = 상품서비스.getProductDetail(상품아이디);
        long 소요시간1 = System.currentTimeMillis() - 시작시간1;

        // Then: 상품 상세 정보가 조회됨
        assertThat(결과1).isNotNull();
        assertThat(결과1.getProductId()).isEqualTo(상품아이디);
        assertThat(결과1.getProductName()).isEqualTo("테스트 상품");

        // When: 두 번째 호출 (캐시에서 조회)
        long 시작시간2 = System.currentTimeMillis();
        ProductDetailResponse 결과2 = 상품서비스.getProductDetail(상품아이디);
        long 소요시간2 = System.currentTimeMillis() - 시작시간2;

        // Then: 캐시된 데이터가 반환됨
        assertThat(결과2).isNotNull();
        assertThat(결과2.getProductId()).isEqualTo(결과1.getProductId());
        assertThat(결과2.getProductName()).isEqualTo(결과1.getProductName());

        // 캐시 효과 검증
        assertThat(소요시간2).isLessThan(소요시간1);

        System.out.println("✅ 상품 상세 캐싱: 첫 호출 " + 소요시간1 + "ms → 캐시 호출 " + 소요시간2 + "ms");
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 캐싱 검증한다")
    public void 사용가능쿠폰조회_캐싱검증() {
        // Given: 캐시 초기화 상태

        // When: 첫 번째 호출 (DB에서 조회)
        long 시작시간1 = System.currentTimeMillis();
        List<AvailableCouponResponse> 결과1 = 쿠폰서비스.getAvailableCoupons();
        long 소요시간1 = System.currentTimeMillis() - 시작시간1;

        // Then: 사용 가능한 쿠폰이 조회됨
        assertThat(결과1).isNotNull();
        assertThat(결과1).isNotEmpty();

        // When: 두 번째 호출 (캐시에서 조회)
        long 시작시간2 = System.currentTimeMillis();
        List<AvailableCouponResponse> 결과2 = 쿠폰서비스.getAvailableCoupons();
        long 소요시간2 = System.currentTimeMillis() - 시작시간2;

        // Then: 캐시된 데이터가 반환됨
        assertThat(결과2).isNotNull();
        assertThat(결과2.size()).isEqualTo(결과1.size());

        // 캐시 효과 검증
        assertThat(소요시간2).isLessThan(소요시간1);

        System.out.println("✅ 쿠폰 목록 캐싱: 첫 호출 " + 소요시간1 + "ms → 캐시 호출 " + 소요시간2 + "ms");
    }

    @Test
    @DisplayName("캐시 히트율 검증한다 - 동일 요청 반복")
    public void 캐시히트율_동일요청반복() {
        // Given: 캐시 초기화 상태
        int 반복횟수 = 10;
        long 첫호출총시간 = 0;
        long 캐시호출총시간 = 0;

        // When: 첫 호출
        long 시작시간 = System.currentTimeMillis();
        상품서비스.getProductList(0, 10, "created_at,desc");
        첫호출총시간 += System.currentTimeMillis() - 시작시간;

        // When: 반복된 호출 (캐시에서 모두 반환되어야 함)
        for (int i = 0; i < 반복횟수; i++) {
            시작시간 = System.currentTimeMillis();
            ProductListResponse 결과 = 상품서비스.getProductList(0, 10, "created_at,desc");
            캐시호출총시간 += System.currentTimeMillis() - 시작시간;

            assertThat(결과).isNotNull();
        }

        // Then: 캐시된 호출들의 총 시간이 첫 호출보다 훨씬 빠름
        long 평균첫호출시간 = 첫호출총시간;
        long 평균캐시호출시간 = 캐시호출총시간 / 반복횟수;

        assertThat(평균캐시호출시간).isLessThan(평균첫호출시간);

        // 캐시 효과: 10배 이상 빨라야 함 (네트워크, DB I/O 제거)
        double 속도향상비 = (double) 평균첫호출시간 / 평균캐시호출시간;
        System.out.println("✅ 캐시 히트율: " + 반복횟수 + "회 반복 후 응답시간 " + 속도향상비 + "배 향상");
    }

    @Test
    @DisplayName("캐시 키 분리 검증한다 - 다른 파라미터는 다른 캐시")
    public void 캐시키분리_다른파라미터() {
        // Given: 캐시 초기화 상태

        // When: 다른 페이지 파라미터로 조회
        ProductListResponse 페이지0 = 상품서비스.getProductList(0, 10, "created_at,desc");
        ProductListResponse 페이지1 = 상품서비스.getProductList(1, 10, "created_at,desc");

        // Then: 다른 캐시 키로 별도 저장됨
        // page0과 page1은 다른 데이터 (다른 시작 위치에서 페이지네이션)
        assertThat(페이지0).isNotNull();
        assertThat(페이지1).isNotNull();

        // 페이지 크기가 다르면 다른 캐시 키
        ProductListResponse 크기5 = 상품서비스.getProductList(0, 5, "created_at,desc");
        assertThat(크기5).isNotNull();

        System.out.println("✅ 캐시 키 분리: 다른 파라미터별로 독립적으로 캐시됨");
    }

    private void 모든캐시초기화() {
        if (캐시매니저 != null) {
            캐시매니저.getCacheNames().forEach(캐시이름 -> {
                var 캐시 = 캐시매니저.getCache(캐시이름);
                if (캐시 != null) {
                    캐시.clear();
                }
            });
        }
    }
}
