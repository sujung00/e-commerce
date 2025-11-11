package com.hhplus.ecommerce.presentation.product;

import com.hhplus.ecommerce.application.product.PopularProductService;
import com.hhplus.ecommerce.presentation.product.response.PopularProductListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PopularProductController - 인기 상품 조회 API (Presentation 계층)
 * GET /products/popular - 인기 상품 목록 조회 (상위 5개, 최근 3일 판매량 기준)
 * 응답 시간: < 2초 (캐싱 적용)
 */
@RestController
@RequestMapping("/products")
public class PopularProductController {

    private final PopularProductService popularProductService;

    public PopularProductController(PopularProductService popularProductService) {
        this.popularProductService = popularProductService;
    }

    /**
     * 인기 상품 조회 (상위 5개, 최근 3일 판매량 기준)
     * - 1시간 TTL 캐싱 적용
     * - 정렬: orderCount3Days (내림차순)
     * - 상태 코드: 200 OK (빈 결과 포함)
     * - 포함: 모든 상품 (판매중지, 품절 상품도 포함)
     *
     * @return 상위 5개 인기 상품 목록 (rank 정보 포함)
     */
    @GetMapping("/popular")
    public ResponseEntity<PopularProductListResponse> getPopularProducts() {
        PopularProductListResponse response = popularProductService.getPopularProducts();
        return ResponseEntity.ok(response);
    }
}
