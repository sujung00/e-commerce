package com.hhplus.ecommerce.domain.cart;

/**
 * CartConstants - 장바구니 도메인 상수
 *
 * 역할:
 * - 장바구니 항목 수량 검증 규칙
 * - 장바구니 항목 추가, 수정, 삭제 관련 상수 관리
 *
 * 사용 예:
 * - if (quantity < CartConstants.MIN_CART_QUANTITY || quantity > CartConstants.MAX_CART_QUANTITY) throw ...
 */
public class CartConstants {

    // ========== Cart Item Quantity Constants ==========

    /** 장바구니 항목 최소 수량 */
    public static final int MIN_CART_QUANTITY = 1;

    /** 장바구니 항목 최대 수량 */
    public static final int MAX_CART_QUANTITY = 1000;

    // ========== Cart Validation Messages ==========

    public static final String MSG_INVALID_CART_QUANTITY = String.format("장바구니 수량은 %d~%d 범위여야 합니다", MIN_CART_QUANTITY, MAX_CART_QUANTITY);
    public static final String MSG_INVALID_QUANTITY_RANGE = String.format("수량은 %d 이상 %d 이하여야 합니다", MIN_CART_QUANTITY, MAX_CART_QUANTITY);

    private CartConstants() {
        throw new AssertionError("CartConstants는 인스턴스화할 수 없습니다");
    }
}
