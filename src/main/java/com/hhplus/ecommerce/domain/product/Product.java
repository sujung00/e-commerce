package com.hhplus.ecommerce.domain.product;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Product 도메인 엔티티 (Rich Domain Model)
 *
 * 책임:
 * - 상품 정보 관리
 * - 상품의 옵션(색상, 사이즈 등) 관리
 * - 총 재고 계산 및 상태 관리
 * - 품절 상태 전환 로직
 *
 * 핵심 비즈니스 규칙:
 * - 총 재고는 모든 옵션의 재고 합계로 계산됨
 * - 모든 옵션의 재고가 0이면 자동으로 품절(SOLD_OUT) 상태로 변경
 * - 가격은 0보다 커야 함
 * - 옵션은 null이 될 수 없음
 */
@Entity
@Table(name = "products")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    @Builder.Default
    private List<ProductOption> options = new ArrayList<>();

    /**
     * 상품 생성 팩토리 메서드
     *
     * 비즈니스 규칙:
     * - 상품명은 필수
     * - 가격은 0보다 커야 함
     * - 초기 상태는 IN_STOCK
     */
    public static Product createProduct(String productName, String description, Long price) {
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }
        if (price == null || price <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다");
        }

        return Product.builder()
                .productName(productName)
                .description(description)
                .price(price)
                .totalStock(0)
                .status("IN_STOCK")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 옵션 추가
     *
     * 비즈니스 규칙:
     * - null 옵션은 추가 불가능
     */
    public void addOption(ProductOption option) {
        if (option == null) {
            throw new IllegalArgumentException("null 옵션을 추가할 수 없습니다");
        }
        this.options.add(option);
        this.recalculateTotalStock();
    }

    /**
     * 옵션 찾기 (ID로)
     */
    public Optional<ProductOption> findOptionById(Long optionId) {
        return this.options.stream()
                .filter(opt -> opt.getOptionId().equals(optionId))
                .findFirst();
    }

    /**
     * 총 재고 재계산
     *
     * 모든 옵션의 재고 합계를 계산합니다.
     * 재계산 후 상태를 자동으로 업데이트합니다.
     */
    public void recalculateTotalStock() {
        int newTotalStock = this.options.stream()
                .mapToInt(ProductOption::getStock)
                .sum();
        this.totalStock = newTotalStock;
        this.updatedAt = LocalDateTime.now();

        // 재고가 0이면 자동으로 품절 상태로 변경
        if (this.totalStock <= 0) {
            this.status = "SOLD_OUT";
        } else {
            this.status = "IN_STOCK";
        }
    }

    /**
     * 재고 보유 여부 확인
     */
    public boolean hasStock() {
        return this.totalStock > 0;
    }

    /**
     * 특정 옵션의 재고 확인
     */
    public boolean hasStockForOption(Long optionId, int quantity) {
        return this.findOptionById(optionId)
                .map(option -> option.getStock() >= quantity)
                .orElse(false);
    }

    /**
     * 상품이 품절 상태인지 확인
     */
    public boolean isSoldOut() {
        return "SOLD_OUT".equals(this.status);
    }

    /**
     * 상품이 판매 가능 상태인지 확인
     */
    public boolean isAvailable() {
        return "IN_STOCK".equals(this.status) && this.totalStock > 0;
    }

    /**
     * 옵션의 재고 차감 (주문 시)
     *
     * 비즈니스 규칙:
     * - 옵션이 존재해야 함
     * - 차감량은 0보다 커야 함
     * - 충분한 재고가 필요
     *
     * @param optionId 옵션 ID
     * @param quantity 차감 수량
     * @throws ProductNotFoundException 옵션을 찾을 수 없음
     */
    public void deductStock(Long optionId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 0보다 커야 합니다");
        }

        ProductOption option = this.findOptionById(optionId)
                .orElseThrow(() -> new ProductNotFoundException(this.productId));

        if (option.getStock() < quantity) {
            throw new IllegalArgumentException(
                    option.getName() + "의 재고가 부족합니다 (요청: " + quantity + ", 보유: " + option.getStock() + ")"
            );
        }

        option.deductStock(quantity);
        this.recalculateTotalStock();
    }

    /**
     * 옵션의 재고 복구 (주문 취소 시)
     *
     * @param optionId 옵션 ID
     * @param quantity 복구 수량
     */
    public void restoreStock(Long optionId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복구 수량은 0보다 커야 합니다");
        }

        ProductOption option = this.findOptionById(optionId)
                .orElseThrow(() -> new ProductNotFoundException(this.productId));

        option.restoreStock(quantity);
        this.recalculateTotalStock();
    }

    /**
     * 가격 조정 (상품 정보 수정 시)
     *
     * @param newPrice 새 가격
     */
    public void updatePrice(Long newPrice) {
        if (newPrice == null || newPrice <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다");
        }
        this.price = newPrice;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 정보 업데이트
     */
    public void updateInfo(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }
}
