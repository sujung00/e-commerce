package com.hhplus.ecommerce.presentation.inventory;

import com.hhplus.ecommerce.application.inventory.InventoryService;
import com.hhplus.ecommerce.presentation.inventory.response.InventoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * InventoryController - Presentation 계층
 *
 * 역할:
 * - 5.1 상품 재고 현황 조회 API의 HTTP 요청 처리
 * - 경로 파라미터 추출 (product_id)
 * - 응답 DTO 생성 및 반환
 *
 * API 명세 (api-specification.md 5.1):
 * - 5.1 상품 재고 현황 조회
 *   - GET /api/inventory/{product_id}
 *   - 경로 파라미터: product_id (양수)
 *   - 응답: InventoryResponse (200 OK)
 *   - 오류: 400 Bad Request (유효하지 않은 productId)
 *   - 오류: 404 Not Found (상품을 찾을 수 없음)
 *
 * HTTP 상태 코드:
 * - 200 OK: 재고 조회 성공
 * - 400 Bad Request: productId <= 0 또는 null (IllegalArgumentException)
 * - 404 Not Found: 해당 상품이 없음 (ProductNotFoundException)
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * 5.1 상품 재고 현황 조회
     * GET /api/inventory/{product_id}
     *
     * @param productId 상품 ID (경로 파라미터)
     * @return 상품의 재고 현황 정보 (InventoryResponse)
     */
    @GetMapping("/{product_id}")
    public ResponseEntity<InventoryResponse> getProductInventory(
            @PathVariable(name = "product_id") Long productId) {
        InventoryResponse response = inventoryService.getProductInventory(productId);
        return ResponseEntity.ok(response);
    }
}
