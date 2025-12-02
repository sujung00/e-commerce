package com.hhplus.ecommerce.application.inventory;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.presentation.inventory.response.InventoryResponse;
import com.hhplus.ecommerce.presentation.inventory.response.OptionInventoryView;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 5.1 상품 재고 현황 조회
     * GET /api/inventory/{product_id}
     *
     * ✅ 성능 최적화 (Message 5):
     * - Redis 캐시: TTL 30초-1분으로 조회 성능 개선
     * - 캐시 전략: key = "inventory:{productId}"
     * - 실시간 재고 변동: 짧은 TTL로 최신 데이터 유지
     * - 캐시 무효화: 재고 변동 시 자동 제거 (추후 구현)
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
    @Cacheable(
            value = "inventoryCache",
            key = "'inventory:' + #productId",
            unless = "#result == null"
    )
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

    /**
     * 재고 복구 (보상용)
     *
     * Outbox 패턴의 보상 로직에서 호출
     * - INVENTORY_DEDUCT 이벤트의 보상 시 재고를 복원
     * - eventData에서 productId와 optionId, 차감된 수량을 받아 복구
     *
     * 동작:
     * 1. 상품 ID 유효성 검증
     * 2. 상품 조회
     * 3. 특정 옵션의 재고 복구
     * 4. 캐시 무효화 (자동)
     *
     * @param productId 상품 ID
     * @param optionId 옵션 ID
     * @param restoreQuantity 복구할 수량
     * @throws ProductNotFoundException 상품을 찾을 수 없는 경우
     * @throws IllegalArgumentException productId <= 0 또는 restoreQuantity <= 0인 경우
     */
    @CacheEvict(
            value = "inventoryCache",
            key = "'inventory:' + #productId"
    )
    public void restoreInventory(Long productId, Long optionId, Integer restoreQuantity) {
        // 1. 상품 ID 유효성 검증
        validateProductId(productId);

        // 수량 유효성 검증
        if (restoreQuantity == null || restoreQuantity <= 0) {
            throw new IllegalArgumentException("복구 수량은 양수여야 합니다. restoreQuantity: " + restoreQuantity);
        }

        // optionId 유효성 검증
        if (optionId == null || optionId <= 0) {
            throw new IllegalArgumentException("옵션 ID는 양수여야 합니다. optionId: " + optionId);
        }

        // 2. 상품 조회
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 3. 특정 옵션의 재고 복구
        // Product.restoreStock(optionId, quantity)를 사용하여 옵션별 재고 복구
        product.restoreStock(optionId, restoreQuantity);
        productRepository.save(product);

        log.info("[InventoryService] 재고 복구 완료: productId={}, optionId={}, restoreQuantity={}, totalStock={}",
                productId, optionId, restoreQuantity, product.getTotalStock());
    }
}
