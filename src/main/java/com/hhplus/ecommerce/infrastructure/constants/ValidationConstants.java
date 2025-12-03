package com.hhplus.ecommerce.infrastructure.constants;

/**
 * ValidationConstants - 비즈니스 규칙 검증용 상수
 *
 * 역할:
 * - 페이지네이션, 수량, 가격, 재고 등 검증 규칙을 한곳에서 관리
 * - 도메인 계층과 애플리케이션 계층에서 공통으로 사용
 * - 매직 넘버 제거 및 일관성 확보
 *
 * 사용 예:
 * - if (quantity < ValidationConstants.MIN_CART_QUANTITY) throw ...
 * - if (pageSize > ValidationConstants.MAX_PAGE_SIZE) pageSize = ValidationConstants.MAX_PAGE_SIZE;
 */
public class ValidationConstants {

    // ========== Pagination Constants ==========
    /** 페이지 번호 (0-based index) */
    public static final int DEFAULT_PAGE = 0;

    /** 기본 페이지 크기 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 최소 페이지 크기 */
    public static final int MIN_PAGE_SIZE = 1;

    /** 최대 페이지 크기 */
    public static final int MAX_PAGE_SIZE = 100;

    // ========== Quantity Constants (장바구니) ==========
    /** 장바구니 항목 최소 수량 */
    public static final int MIN_CART_QUANTITY = 1;

    /** 장바구니 항목 최대 수량 */
    public static final int MAX_CART_QUANTITY = 1000;

    // ========== Price & Balance Constants ==========
    /** 최소 가격 (0원 초과) */
    public static final long MIN_PRICE = 0L;

    /** 최소 잔액 (0원 초과) */
    public static final long MIN_BALANCE_AMOUNT = 0L;

    /** 최소 충전 금액 (0원 초과) */
    public static final long MIN_CHARGE_AMOUNT = 0L;

    /** 최소 차감 금액 (0원 초과) */
    public static final long MIN_DEDUCT_AMOUNT = 0L;

    // ========== Stock Constants ==========
    /** 초기 재고 (기본값) */
    public static final int INITIAL_STOCK = 0;

    /** 최소 재고 (음수 체크) */
    public static final int ZERO_STOCK = 0;

    // ========== Discount Constants ==========
    /** 최소 할인 금액 (0원 초과) */
    public static final long MIN_DISCOUNT_AMOUNT = 0L;

    /** 최소 할인율 (0% 포함) */
    public static final double MIN_DISCOUNT_RATE = 0.0;

    /** 최대 할인율 (100% 포함) */
    public static final double MAX_DISCOUNT_RATE = 1.0;

    // ========== Coupon Constants ==========
    /** 쿠폰 최소 수량 (0개 초과) */
    public static final int MIN_COUPON_QUANTITY = 0;

    /** 쿠폰 최소 잔여 수량 (0개 초과) */
    public static final int ZERO_REMAINING_COUPON_QTY = 0;

    // ========== ID Validation Constants ==========
    /** 최소 ID 값 (양수 체크) */
    public static final long MIN_VALID_ID = 1L;

    /** ID 검증 기준 (0 이하는 유효하지 않음) */
    public static final long ZERO_ID = 0L;

    // ========== Validation Messages ==========
    public static final String MSG_PAGE_OUT_OF_RANGE = "페이지 번호는 0 이상이어야 합니다";
    public static final String MSG_PAGE_SIZE_OUT_OF_RANGE = String.format("페이지 크기는 %d~%d 범위여야 합니다", MIN_PAGE_SIZE, MAX_PAGE_SIZE);
    public static final String MSG_INVALID_QUANTITY = String.format("수량은 %d~%d 범위여야 합니다", MIN_CART_QUANTITY, MAX_CART_QUANTITY);
    public static final String MSG_INVALID_PRICE = "가격은 0원 초과여야 합니다";
    public static final String MSG_INSUFFICIENT_BALANCE = "잔액이 부족합니다";
    public static final String MSG_INSUFFICIENT_STOCK = "재고가 부족합니다";
    public static final String MSG_INVALID_DISCOUNT_RATE = "할인율은 0~100% 범위여야 합니다";
    public static final String MSG_INVALID_ID = "ID는 양수여야 합니다";

    private ValidationConstants() {
        throw new AssertionError("ValidationConstants는 인스턴스화할 수 없습니다");
    }
}
