package com.hhplus.ecommerce.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Coupon 단위 테스트")
class CouponTest {

    @Test
    @DisplayName("쿠폰 생성 - 성공")
    void testCouponCreation() {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("할인 쿠폰")
                .description("테스트 설명")
                .discountType("FIXED_AMOUNT")
                .discountAmount(10000L)
                .totalQuantity(100)
                .remainingQty(100)
                .validFrom(now)
                .validUntil(now.plusDays(30))
                .isActive(true)
                .createdAt(now)
                .build();

        assertThat(coupon.getCouponId()).isEqualTo(1L);
        assertThat(coupon.getCouponName()).isEqualTo("할인 쿠폰");
        assertThat(coupon.getDiscountType()).isEqualTo("FIXED_AMOUNT");
        assertThat(coupon.getTotalQuantity()).isEqualTo(100);
        assertThat(coupon.getRemainingQty()).isEqualTo(100);
    }

    @Test
    @DisplayName("쿠폰 필드 검증")
    void testCouponFieldValidation() {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("테스트")
                .description("테스트 설명")
                .discountAmount(10L)
                .totalQuantity(50)
                .remainingQty(50)
                .validFrom(now)
                .validUntil(now.plusDays(1))
                .isActive(true)
                .createdAt(now)
                .build();

        assertThat(coupon.getDescription()).isEqualTo("테스트 설명");
        assertThat(coupon.getTotalQuantity()).isEqualTo(50);
        assertThat(coupon.getRemainingQty()).isEqualTo(50);
    }

    @Test
    @DisplayName("쿠폰 상태 확인")
    void testCouponStatus() {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("테스트")
                .discountAmount(10L)
                .totalQuantity(100)
                .remainingQty(50)
                .validFrom(now)
                .validUntil(now.plusDays(1))
                .isActive(true)
                .createdAt(now)
                .build();

        assertThat(coupon.getIsActive()).isTrue();
        assertThat(coupon.getRemainingQty()).isEqualTo(50);
    }

    @Test
    @DisplayName("쿠폰 수량 업데이트")
    void testCouponQuantityUpdate() {
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .remainingQty(100)
                .build();

        coupon.setRemainingQty(50);

        assertThat(coupon.getRemainingQty()).isEqualTo(50);
    }

    @Test
    @DisplayName("쿠폰 할인율 - PERCENTAGE 타입")
    void testCouponPercentageDiscount() {
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .discountType("PERCENTAGE")
                .discountRate(BigDecimal.valueOf(10))
                .build();

        assertThat(coupon.getDiscountType()).isEqualTo("PERCENTAGE");
        assertThat(coupon.getDiscountRate()).isEqualTo(BigDecimal.valueOf(10));
    }

    @Test
    @DisplayName("쿠폰 유효 기간")
    void testCouponValidityPeriod() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusDays(30);

        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .validFrom(start)
                .validUntil(end)
                .build();

        assertThat(coupon.getValidFrom()).isEqualTo(start);
        assertThat(coupon.getValidUntil()).isEqualTo(end);
    }

    @Test
    @DisplayName("쿠폰 버전 관리")
    void testCouponVersionManagement() {
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .version(0L)
                .build();

        coupon.setVersion(1L);

        assertThat(coupon.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("쿠폰 null 필드 처리")
    void testCouponNullFields() {
        Coupon coupon = Coupon.builder().build();

        assertThat(coupon.getCouponId()).isNull();
        assertThat(coupon.getCouponName()).isNull();
        assertThat(coupon.getIsActive()).isNull();
    }

    // ========== validateDiscount() 메서드 테스트 ==========

    @Test
    @DisplayName("validateDiscount - FIXED_AMOUNT인데 discountAmount가 0 이하일 때 예외 발생")
    void validateDiscount_ShouldThrow_WhenInvalidFixedAmount() {
        // Given: FIXED_AMOUNT 타입이지만 discountAmount가 0 이하
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("정액 할인 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(0L)  // 0 이하 → 유효하지 않음
                .build();

        // When & Then: IllegalArgumentException 발생 확인
        assertThrows(IllegalArgumentException.class, () -> {
            coupon.validateDiscount();
        }, "정액 할인액이 0 이하일 때 예외가 발생해야 합니다");
    }

    @Test
    @DisplayName("validateDiscount - PERCENTAGE인데 discountRate가 음수일 때 예외 발생")
    void validateDiscount_ShouldThrow_WhenNegativePercentage() {
        // Given: PERCENTAGE 타입이지만 discountRate가 음수
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("할인율 쿠폰")
                .discountType("PERCENTAGE")
                .discountRate(BigDecimal.valueOf(-0.1))  // 음수 → 유효하지 않음
                .build();

        // When & Then: IllegalArgumentException 발생 확인
        assertThrows(IllegalArgumentException.class, () -> {
            coupon.validateDiscount();
        }, "할인율이 음수일 때 예외가 발생해야 합니다");
    }

    @Test
    @DisplayName("validateDiscount - PERCENTAGE인데 discountRate가 1.0을 초과할 때 예외 발생")
    void validateDiscount_ShouldThrow_WhenOverOnePercentage() {
        // Given: PERCENTAGE 타입이지만 discountRate가 1.0을 초과
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("할인율 쿠폰")
                .discountType("PERCENTAGE")
                .discountRate(BigDecimal.valueOf(1.5))  // 1.0 초과 → 유효하지 않음
                .build();

        // When & Then: IllegalArgumentException 발생 확인
        assertThrows(IllegalArgumentException.class, () -> {
            coupon.validateDiscount();
        }, "할인율이 1.0을 초과할 때 예외가 발생해야 합니다");
    }

    @Test
    @DisplayName("validateDiscount - 유효한 FIXED_AMOUNT 쿠폰일 때 예외 없이 통과")
    void validateDiscount_ShouldNotThrow_WhenValid() {
        // Given: 유효한 FIXED_AMOUNT 쿠폰
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("정액 할인 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(10000L)  // 유효한 값
                .build();

        // When & Then: 예외가 발생하지 않음
        assertDoesNotThrow(() -> {
            coupon.validateDiscount();
        }, "유효한 정액 할인 쿠폰은 예외가 발생하지 않아야 합니다");
    }

    @Test
    @DisplayName("validateDiscount - 유효한 PERCENTAGE 쿠폰일 때 예외 없이 통과")
    void validateDiscount_ShouldNotThrow_WhenValidPercentage() {
        // Given: 유효한 PERCENTAGE 쿠폰
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("할인율 쿠폰")
                .discountType("PERCENTAGE")
                .discountRate(BigDecimal.valueOf(0.5))  // 유효한 값 (0.0 ~ 1.0)
                .build();

        // When & Then: 예외가 발생하지 않음
        assertDoesNotThrow(() -> {
            coupon.validateDiscount();
        }, "유효한 할인율 쿠폰은 예외가 발생하지 않아야 합니다");
    }
}
