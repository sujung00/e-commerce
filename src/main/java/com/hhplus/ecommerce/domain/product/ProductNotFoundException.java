package com.hhplus.ecommerce.domain.product;

import lombok.Getter;

/**
 * ProductNotFoundException - 상품을 찾을 수 없을 때 발생하는 예외 (Domain 계층)
 *
 * 역할:
 * - 상품 ID로 조회했을 때 존재하지 않는 경우 발생
 * - HTTP 404 Not Found 응답으로 변환됨
 *
 * API 명세 (api-specification.md):
 * - Error Code: PRODUCT_NOT_FOUND
 * - HTTP Status: 404 Not Found
 * - Message: "상품을 찾을 수 없습니다"
 *
 * 호출 예시:
 * - throw new ProductNotFoundException(1L)
 * - throw new ProductNotFoundException("상품을 찾을 수 없습니다")
 *
 * GlobalExceptionHandler에서 처리:
 * - 404 Not Found 응답으로 매핑
 * - ErrorResponse: { error_code: "PRODUCT_NOT_FOUND", error_message: "상품을 찾을 수 없습니다", ... }
 */
@Getter
public class ProductNotFoundException extends RuntimeException {

    private final String errorCode = "PRODUCT_NOT_FOUND";

    /**
     * 상품 ID를 기반으로 ProductNotFoundException 생성
     * 메시지는 자동으로 "상품을 찾을 수 없습니다"로 설정됨
     *
     * @param productId 찾을 수 없는 상품 ID
     */
    public ProductNotFoundException(Long productId) {
        super("상품을 찾을 수 없습니다");
    }

    /**
     * 커스텀 메시지로 ProductNotFoundException 생성
     *
     * @param message 커스텀 에러 메시지
     */
    public ProductNotFoundException(String message) {
        super(message);
    }
}
