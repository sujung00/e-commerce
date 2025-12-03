package com.hhplus.ecommerce.domain.order;

/**
 * OrderConstants - 주문 도메인 상수
 *
 * 역할:
 * - 주문 도메인에서 사용하는 검증 규칙과 기본값 관리
 * - 주문 생성, 취소, 상태 변경 등의 비즈니스 규칙 상수화
 *
 * 사용 예:
 * - if (orderItem.getQuantity() < OrderConstants.MIN_ORDER_QUANTITY) throw ...
 * - if (order.getFinalAmount() < OrderConstants.ZERO_SUBTOTAL) throw ...
 */
public class OrderConstants {

    // ========== Order Item Quantity Constants ==========

    /** 주문 항목 최소 수량 */
    public static final int MIN_ORDER_QUANTITY = 1;

    // ========== Order Item Price Constants ==========

    /** 주문 항목 최소 가격 (0원 초과) */
    public static final long MIN_ORDER_ITEM_PRICE = 0L;

    // ========== Order Discount Constants ==========

    /** 주문 할인율 최소값 (0% 포함) */
    public static final double MIN_ORDER_DISCOUNT_RATE = 0.0;

    /** 주문 할인율 최대값 (100% 포함) */
    public static final double MAX_ORDER_DISCOUNT_RATE = 1.0;

    // ========== Order Total Amount Constants ==========

    /** 주문 최종 금액 기준값 (0원) */
    public static final long ZERO_SUBTOTAL = 0L;

    /** 주문 쿠폰 할인 기준값 (0원) */
    public static final long ZERO_COUPON_DISCOUNT = 0L;

    // ========== Order Payment Constants ==========

    /** 기본 쿠폰 할인금 (구현 예시) */
    public static final long DEFAULT_COUPON_DISCOUNT = 5000L;

    // ========== Order Status Constants ==========

    /** 주문 초기 상태 */
    public static final String INITIAL_ORDER_STATUS = "PENDING";

    // ========== Order Validation Messages ==========

    public static final String MSG_INVALID_ORDER_QUANTITY = String.format("주문 수량은 %d 이상이어야 합니다", MIN_ORDER_QUANTITY);
    public static final String MSG_INVALID_ORDER_PRICE = "주문 항목 가격은 0원 초과여야 합니다";
    public static final String MSG_INVALID_FINAL_AMOUNT = "최종 주문 금액은 0원 이상이어야 합니다";
    public static final String MSG_INVALID_COUPON_DISCOUNT = "쿠폰 할인액은 0원 이상이어야 합니다";

    private OrderConstants() {
        throw new AssertionError("OrderConstants는 인스턴스화할 수 없습니다");
    }
}
