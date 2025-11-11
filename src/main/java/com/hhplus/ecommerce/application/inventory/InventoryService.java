package com.hhplus.ecommerce.application.inventory;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.presentation.inventory.response.InventoryResponse;
import com.hhplus.ecommerce.presentation.inventory.response.OptionInventoryView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * InventoryService - Application 계층
 *
 * 역할:
 * - 5.1 상품 재고 현황 조회 비즈니스 로직 처리
 * - 상품 조회, 옵션 조회, 재고 계산, Response 변환
 *
 * 특징:
 * - 상품 ID 유효성 검증 (> 0)
 * - 상품 존재 여부 검증
 * - ProductRepository를 통한 조회 (기존 패턴 유지)
 * - 옵션별 재고 정보 제공 (stock, version 포함)
 *
 * 흐름:
 * 1. 상품 ID 유효성 검증
 * 2. 상품 존재 여부 확인
 * 3. 상품의 옵션 목록 조회
 * 4. OptionInventoryView 목록으로 변환
 * 5. InventoryResponse로 변환 (전체 재고 포함)
 */
@Service
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 5.1 상품 재고 현황 조회
     * GET /api/inventory/{product_id}
     *
     * 비즈니스 로직:
     * 1. 상품 ID 유효성 검증 (> 0)
     * 2. 상품 존재 여부 확인 (ProductNotFoundException)
     * 3. 상품의 옵션 목록 조회
     * 4. 옵션 정보를 OptionInventoryView로 변환
     * 5. InventoryResponse 생성 및 반환
     *
     * @param productId 상품 ID
     * @return 상품의 재고 현황 정보 (상품명, 전체 재고, 옵션별 재고)
     * @throws IllegalArgumentException productId <= 0인 경우
     * @throws ProductNotFoundException 상품을 찾을 수 없는 경우 (404)
     */
    public InventoryResponse getProductInventory(Long productId) {
        // 1. 상품 ID 유효성 검증
        validateProductId(productId);

        // 2. 상품 존재 여부 확인
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 3. 상품의 옵션 목록 조회
        List<ProductOption> options = productRepository.findOptionsByProductId(productId);

        // 4. 옵션 정보를 OptionInventoryView로 변환
        List<OptionInventoryView> optionViews = options.stream()
                .map(OptionInventoryView::from)
                .collect(Collectors.toList());

        // 5. InventoryResponse 생성 및 반환
        return InventoryResponse.from(product, optionViews);
    }

    /**
     * 상품 ID 유효성 검증
     *
     * 검증 규칙:
     * - productId > 0 (양수여야 함)
     *
     * @param productId 검증할 상품 ID
     * @throws IllegalArgumentException productId <= 0인 경우
     */
    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("상품 ID는 양수여야 합니다. productId: " + productId);
        }
    }
}
