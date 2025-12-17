package com.hhplus.ecommerce.domain.product;

/**
 * ProductConstants - 상품 도메인 상수
 *
 * 역할:
 * - 상품, 상품 옵션, 상품 재고 관련 검증 규칙 관리
 * - 상품 가격, 재고 검증 및 상태 관리
 *
 * 사용 예:
 * - if (product.getPrice() <= ProductConstants.ZERO_PRICE) throw ...
 * - if (option.getStock() < ProductConstants.ZERO_STOCK) throw ...
 */
public class ProductConstants {

    // ========== Product Price Constants ==========

    /** 상품 가격 기준값 (0원) */
    public static final long ZERO_PRICE = 0L;

    /** 상품 최소 가격 (0원 초과) */
    public static final long MIN_PRODUCT_PRICE = 0L;

    // ========== Product Stock Constants ==========

    /** 상품 초기 재고 */
    public static final int INITIAL_PRODUCT_STOCK = 0;

    /** 상품 재고 기준값 (0개) */
    public static final int ZERO_STOCK = 0;

    /** 상품 재고 최소값 (음수 체크) */
    public static final int MIN_PRODUCT_STOCK = 0;

    // ========== Product Option Stock Constants ==========

    /** 상품 옵션 재고 기준값 (0개) */
    public static final int ZERO_OPTION_STOCK = 0;

    /** 상품 옵션 차감 최소 수량 */
    public static final int MIN_DEDUCT_QUANTITY = 1;

    /** 재고 부족 임계값 (이 값 이하이면 재고 부족 알림 발송) */
    public static final int LOW_STOCK_THRESHOLD = 10;

    // ========== Product Status Constants ==========

    /** 상품 상태: 재고 있음 */
    public static final String STATUS_IN_STOCK = "IN_STOCK";

    /** 상품 상태: 품절 */
    public static final String STATUS_SOLD_OUT = "SOLD_OUT";

    /** 상품 초기 상태 */
    public static final String INITIAL_PRODUCT_STATUS = STATUS_IN_STOCK;

    // ========== Product Validation Messages ==========

    public static final String MSG_INVALID_PRODUCT_PRICE = "상품 가격은 0원 초과여야 합니다";
    public static final String MSG_INSUFFICIENT_PRODUCT_STOCK = "상품 재고가 부족합니다";
    public static final String MSG_INVALID_PRODUCT_STOCK = "상품 재고는 0개 이상이어야 합니다";
    public static final String MSG_INVALID_OPTION_STOCK = "옵션 재고는 0개 이상이어야 합니다";

    private ProductConstants() {
        throw new AssertionError("ProductConstants는 인스턴스화할 수 없습니다");
    }
}
