package com.hhplus.ecommerce.presentation;

import com.hhplus.ecommerce.dto.ProductDetailResponse;
import com.hhplus.ecommerce.dto.ProductListResponse;
import com.hhplus.ecommerce.application.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ProductController - 상품 조회 API (Presentation 계층)
 * GET /api/products - 상품 목록 조회 (페이지네이션, 정렬)
 * GET /api/products/{product_id} - 상품 상세 조회 (옵션 포함)
 */
@RestController
@RequestMapping("/api/products")
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
        ProductDetailResponse response = productService.getProductDetail(productId);
        return ResponseEntity.ok(response);
    }
}