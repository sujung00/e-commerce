package com.hhplus.ecommerce.presentation.product;

import com.hhplus.ecommerce.presentation.product.response.ProductDetailResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductListResponse;
import com.hhplus.ecommerce.application.product.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ProductController - 상품 조회 API (Presentation 계층)
 * GET /products - 상품 목록 조회 (페이지네이션, 정렬)
 * GET /products/{product_id} - 상품 상세 조회 (옵션 포함)
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 상품 목록 조회 (페이지네이션 및 정렬 지원)
     *
     * @param page 페이지 번호 (기본값: 0)
     * @param size 페이지당 항목 수 (기본값: 10, 범위: 1~100)
     * @param sort 정렬 기준 (기본값: product_id,desc)
     * @return 페이지네이션된 상품 목록
     */
    @GetMapping
    public ResponseEntity<ProductListResponse> getProductList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sort", defaultValue = "product_id,desc") String sort) {

        // Controller 계층에서 기본적인 파라미터 검증
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다");
        }

        ProductListResponse response = productService.getProductList(page, size, sort);
        return ResponseEntity.ok(response);
    }

    /**
     * 상품 상세 조회 (옵션 포함)
     *
     * @param productId 상품 ID
     * @return 상품 상세 정보
     */
    @GetMapping("/{product_id}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(
            @PathVariable("product_id") Long productId) {

        // Controller 계층에서 기본적인 파라미터 검증
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("상품 ID는 양수여야 합니다");
        }

        ProductDetailResponse response = productService.getProductDetail(productId);
        return ResponseEntity.ok(response);
    }
}
