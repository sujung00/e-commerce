package com.hhplus.ecommerce.domain.coupon;

/**
 * UserCouponStatus - Enum (값 객체)
 * 사용자 쿠폰의 상태를 나타내는 열거형
 *
 * 상태 전이:
 * - UNUSED (미사용) → USED (사용됨) OR EXPIRED (만료됨) OR CANCELLED (취소됨)
 * - USED (사용됨) → 종료 상태 (변경 불가)
 * - EXPIRED (만료됨) → 종료 상태 (변경 불가)
 * - CANCELLED (취소됨) → 종료 상태 (변경 불가)
 */
public enum UserCouponStatus {
    UNUSED("미사용"),         // 발급받았으나 아직 사용하지 않음
    USED("사용됨"),          // 주문에 사용됨
    EXPIRED("만료됨"),       // 유효 기간이 지남
    CANCELLED("취소됨");     // 관리자에 의해 취소됨

    private final String displayName;

    UserCouponStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 문자열을 UserCouponStatus로 변환
     *
     * @param value 상태 문자열 ("ACTIVE" → UNUSED, "USED" → USED, "EXPIRED" → EXPIRED)
     * @return 변환된 UserCouponStatus
     * @throws IllegalArgumentException 올바르지 않은 값
     */
    public static UserCouponStatus from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Status 값이 null입니다");
        }

        // 호환성: 기존 "ACTIVE" 값을 "UNUSED"로 변환
        String normalizedValue = value.trim().toUpperCase();
        if ("ACTIVE".equals(normalizedValue)) {
            return UNUSED;
        }

        try {
            return UserCouponStatus.valueOf(normalizedValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("올바르지 않은 UserCouponStatus 값: %s (허용값: UNUSED, USED, EXPIRED, CANCELLED)", value)
            );
        }
    }
}
