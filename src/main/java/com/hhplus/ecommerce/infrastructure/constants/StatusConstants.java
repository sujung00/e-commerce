package com.hhplus.ecommerce.infrastructure.constants;

/**
 * StatusConstants - 도메인 상태 코드 상수
 *
 * 역할:
 * - 상품, 주문, Outbox 메시지 등의 상태 값을 한곳에서 관리
 * - 문자열 리터럴을 제거하고 타입 안정성 확보
 * - 상태 변환 로직에서 일관된 값 사용
 *
 * 사용 예:
 * - if (product.getStatus().equals(StatusConstants.PRODUCT_STATUS_IN_STOCK)) { ... }
 * - order.setStatus(StatusConstants.ORDER_STATUS_PENDING);
 */
public class StatusConstants {

    // ========== Product Status Constants ==========

    /** 상품 상태: 재고 있음 */
    public static final String PRODUCT_STATUS_IN_STOCK = "IN_STOCK";

    /** 상품 상태: 품절 */
    public static final String PRODUCT_STATUS_SOLD_OUT = "SOLD_OUT";

    // ========== Order Status Constants ==========

    /** 주문 상태: 대기 중 */
    public static final String ORDER_STATUS_PENDING = "PENDING";

    /** 주문 상태: 결제 완료 */
    public static final String ORDER_STATUS_PAID = "PAID";

    /** 주문 상태: 배송 중 */
    public static final String ORDER_STATUS_SHIPPED = "SHIPPED";

    /** 주문 상태: 배송 완료 */
    public static final String ORDER_STATUS_DELIVERED = "DELIVERED";

    /** 주문 상태: 취소 */
    public static final String ORDER_STATUS_CANCELLED = "CANCELLED";

    // ========== Outbox Message Status Constants ==========

    /** Outbox 메시지 상태: 대기 중 (처리 대기) */
    public static final String OUTBOX_STATUS_PENDING = "PENDING";

    /** Outbox 메시지 상태: 처리 완료 */
    public static final String OUTBOX_STATUS_PUBLISHED = "PUBLISHED";

    /** Outbox 메시지 상태: 폐기됨 (최대 재시도 초과) */
    public static final String OUTBOX_STATUS_ABANDONED = "ABANDONED";

    // ========== Coupon Status Constants ==========

    /** 쿠폰 상태: 활성화 */
    public static final String COUPON_STATUS_ACTIVE = "ACTIVE";

    /** 쿠폰 상태: 비활성화 */
    public static final String COUPON_STATUS_INACTIVE = "INACTIVE";

    /** 쿠폰 상태: 종료됨 (유효기간 만료) */
    public static final String COUPON_STATUS_EXPIRED = "EXPIRED";

    // ========== Status Validation Helpers ==========

    /**
     * 유효한 상품 상태인지 확인
     */
    public static boolean isValidProductStatus(String status) {
        return status != null && (
            status.equals(PRODUCT_STATUS_IN_STOCK) ||
            status.equals(PRODUCT_STATUS_SOLD_OUT)
        );
    }

    /**
     * 유효한 주문 상태인지 확인
     */
    public static boolean isValidOrderStatus(String status) {
        return status != null && (
            status.equals(ORDER_STATUS_PENDING) ||
            status.equals(ORDER_STATUS_PAID) ||
            status.equals(ORDER_STATUS_SHIPPED) ||
            status.equals(ORDER_STATUS_DELIVERED) ||
            status.equals(ORDER_STATUS_CANCELLED)
        );
    }

    /**
     * 유효한 Outbox 메시지 상태인지 확인
     */
    public static boolean isValidOutboxStatus(String status) {
        return status != null && (
            status.equals(OUTBOX_STATUS_PENDING) ||
            status.equals(OUTBOX_STATUS_PUBLISHED) ||
            status.equals(OUTBOX_STATUS_ABANDONED)
        );
    }

    /**
     * 유효한 쿠폰 상태인지 확인
     */
    public static boolean isValidCouponStatus(String status) {
        return status != null && (
            status.equals(COUPON_STATUS_ACTIVE) ||
            status.equals(COUPON_STATUS_INACTIVE) ||
            status.equals(COUPON_STATUS_EXPIRED)
        );
    }

    private StatusConstants() {
        throw new AssertionError("StatusConstants는 인스턴스화할 수 없습니다");
    }
}
