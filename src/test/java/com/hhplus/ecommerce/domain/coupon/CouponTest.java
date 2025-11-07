package com.hhplus.ecommerce.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

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
}
