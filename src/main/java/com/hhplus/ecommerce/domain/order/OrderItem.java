package com.hhplus.ecommerce.domain.order;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * OrderItem 도메인 엔티티 (Rich Domain Model)
 *
 * 책임:
 * - 주문 내 각 상품 항목의 정보 관리
 * - 항목별 소계 계산
 * - 스냅샷: 주문 시점의 상품/옵션 정보 보존
 *
 * 핵심 비즈니스 규칙:
 * - 소계 = 단가 × 수량 (항상 일관성 유지)
 * - 수량은 1 이상이어야 함
 * - 단가는 0보다 커야 함
 * - 주문 시점의 상품명/옵션명을 스냅샷으로 저장
 *   (추후 상품 가격 변경 시에도 원래 가격으로 청구)
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "option_name", nullable = false)
    private String optionName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "subtotal", nullable = false)
    private Long subtotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * OrderItem 생성 팩토리 메서드
     *
     * 비즈니스 규칙:
     * - 수량은 1 이상이어야 함
     * - 단가는 0보다 커야 함
     * - 소계는 단가 × 수량으로 자동 계산
     * - 상품명/옵션명은 필수 (스냅샷)
     *
     * @param productId 상품 ID
     * @param optionId 옵션 ID
     * @param productName 주문 시점의 상품명 (스냅샷)
     * @param optionName 주문 시점의 옵션명 (스냅샷)
     * @param quantity 수량
     * @param unitPrice 단가
     * @return OrderItem 인스턴스
     */
    public static OrderItem createOrderItem(Long productId, Long optionId, String productName,
                                           String optionName, Integer quantity, Long unitPrice) {
        // 비즈니스 규칙 검증
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
        }
        if (unitPrice == null || unitPrice <= 0) {
            throw new IllegalArgumentException("단가는 0보다 커야 합니다");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }
        if (optionName == null || optionName.isBlank()) {
            throw new IllegalArgumentException("옵션명은 필수입니다");
        }

        Long subtotal = unitPrice * quantity;
        return OrderItem.builder()
                .productId(productId)
                .optionId(optionId)
                .productName(productName)
                .optionName(optionName)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 항목별 소계 (단가 × 수량)
     *
     * @return 소계 금액
     */
    public Long getLineTotal() {
        return this.unitPrice * this.quantity;
    }

    /**
     * 단가 확인
     */
    public Long getPrice() {
        return this.unitPrice;
    }

    /**
     * 수량 확인
     */
    public Integer getOrderQuantity() {
        return this.quantity;
    }

    /**
     * 항목의 상품 정보 (스냅샷)
     */
    public String getProductDisplayName() {
        return this.productName + " - " + this.optionName;
    }

    /**
     * 할인 적용 (특정 비율의 할인)
     *
     * 예: 10% 할인 → 0.1 입력
     * 새로운 소계를 반환하며, 기존 subtotal은 변경하지 않음
     *
     * @param discountRate 할인율 (0.0 ~ 1.0)
     * @return 할인 후 금액
     */
    public Long calculateDiscountedAmount(double discountRate) {
        if (discountRate < 0 || discountRate > 1) {
            throw new IllegalArgumentException("할인율은 0.0 ~ 1.0 사이여야 합니다");
        }
        Long discountAmount = (long) (this.subtotal * discountRate);
        return this.subtotal - discountAmount;
    }

    /**
     * 항목별 할인액 계산
     *
     * @param discountRate 할인율
     * @return 할인액
     */
    public Long calculateDiscountAmount(double discountRate) {
        if (discountRate < 0 || discountRate > 1) {
            throw new IllegalArgumentException("할인율은 0.0 ~ 1.0 사이여야 합니다");
        }
        return (long) (this.subtotal * discountRate);
    }

    /**
     * 항목이 유효한지 확인 (음수 값 체크)
     */
    public boolean isValid() {
        return this.quantity >= 1 && this.unitPrice > 0 && this.subtotal > 0;
    }
}
