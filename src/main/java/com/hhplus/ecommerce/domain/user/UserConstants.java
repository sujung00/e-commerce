package com.hhplus.ecommerce.domain.user;

/**
 * UserConstants - 사용자 도메인 상수
 *
 * 역할:
 * - 사용자 잔액 관리 관련 검증 규칙
 * - 잔액 충전, 차감 시 필요한 상수값 관리
 *
 * 사용 예:
 * - if (balance < UserConstants.MIN_BALANCE_AMOUNT) throw ...
 * - if (chargeAmount <= UserConstants.ZERO_BALANCE) throw ...
 */
public class UserConstants {

    // ========== User Balance Constants ==========

    /** 사용자 잔액 기준값 (0원) */
    public static final long ZERO_BALANCE = 0L;

    /** 사용자 잔액 최소값 (0원) */
    public static final long MIN_BALANCE_AMOUNT = 0L;

    // ========== User Balance Charge Constants ==========

    /** 잔액 충전 최소 금액 (0원 초과) */
    public static final long MIN_CHARGE_AMOUNT = 0L;

    // ========== User Balance Deduct Constants ==========

    /** 잔액 차감 최소 금액 (0원 초과) */
    public static final long MIN_DEDUCT_AMOUNT = 0L;

    // ========== User Validation Messages ==========

    public static final String MSG_INSUFFICIENT_BALANCE = "잔액이 부족합니다";
    public static final String MSG_INVALID_CHARGE_AMOUNT = "충전 금액은 0원 초과여야 합니다";
    public static final String MSG_INVALID_DEDUCT_AMOUNT = "차감 금액은 0원 초과여야 합니다";
    public static final String MSG_INVALID_BALANCE = "사용자 잔액은 0원 이상이어야 합니다";

    private UserConstants() {
        throw new AssertionError("UserConstants는 인스턴스화할 수 없습니다");
    }
}
