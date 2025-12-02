package com.hhplus.ecommerce.unit.domain;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coupon Domain 단위 테스트
 *
 * 테스트 범위:
 * - 쿠폰 재고 차감 (decreaseStock)
 * - 비즈니스 규칙 검증 (재고 부족, 자동 비활성화)
 * - Domain 메서드의 정확성 확인
 */
@DisplayName("[Unit] Coupon Domain 테스트")
class CouponDomainTest {

    @Test
    @DisplayName("쿠폰 재고 차감 - 정상 케이스")
    void testDecreaseStock_Success() {
        // Given
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("테스트 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(1000L)
                .discountRate(BigDecimal.ZERO)
                .totalQuantity(10)
                .remainingQty(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When
        coupon.decreaseStock();

        // Then
        assertEquals(9, coupon.getRemainingQty(), "재고가 1 감소해야 함");
        assertTrue(coupon.getIsActive(), "재고가 남아있으면 활성화 상태 유지");
    }

    @Test
    @DisplayName("쿠폰 재고 차감 - 마지막 재고 소진 시 자동 비활성화")
    void testDecreaseStock_AutoDeactivate() {
        // Given
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("테스트 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(1000L)
                .discountRate(BigDecimal.ZERO)
                .totalQuantity(1)
                .remainingQty(1)  // 마지막 재고
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When
        coupon.decreaseStock();

        // Then
        assertEquals(0, coupon.getRemainingQty(), "재고가 0이 되어야 함");
        assertFalse(coupon.getIsActive(), "재고 소진 시 자동으로 비활성화되어야 함");
    }

    @Test
    @DisplayName("쿠폰 재고 차감 - 재고 부족 예외")
    void testDecreaseStock_InsufficientStock() {
        // Given
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("테스트 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(1000L)
                .discountRate(BigDecimal.ZERO)
                .totalQuantity(10)
                .remainingQty(0)  // 재고 없음
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                coupon::decreaseStock,
                "재고가 0일 때 차감하면 예외 발생"
        );

        assertTrue(exception.getMessage().contains("재고가 부족합니다"));
    }

    @Test
    @DisplayName("쿠폰 재고 확인 - hasStock()")
    void testHasStock() {
        // Given
        Coupon couponWithStock = Coupon.builder()
                .remainingQty(5)
                .build();
        Coupon couponNoStock = Coupon.builder()
                .remainingQty(0)
                .build();

        // When & Then
        assertTrue(couponWithStock.hasStock(), "재고가 있으면 true");
        assertFalse(couponNoStock.hasStock(), "재고가 없으면 false");
    }

    @Test
    @DisplayName("쿠폰 활성화 상태 확인 - isActiveCoupon()")
    void testIsActiveCoupon() {
        // Given
        Coupon activeCoupon = Coupon.builder()
                .isActive(true)
                .build();
        Coupon inactiveCoupon = Coupon.builder()
                .isActive(false)
                .build();

        // When & Then
        assertTrue(activeCoupon.isActiveCoupon(), "활성화된 쿠폰은 true");
        assertFalse(inactiveCoupon.isActiveCoupon(), "비활성화된 쿠폰은 false");
    }

    @Test
    @DisplayName("쿠폰 유효 기간 확인 - isValidPeriod()")
    void testIsValidPeriod() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon validCoupon = Coupon.builder()
                .validFrom(now.minusDays(1))
                .validUntil(now.plusDays(1))
                .build();
        Coupon expiredCoupon = Coupon.builder()
                .validFrom(now.minusDays(10))
                .validUntil(now.minusDays(1))
                .build();
        Coupon futureCoupon = Coupon.builder()
                .validFrom(now.plusDays(1))
                .validUntil(now.plusDays(10))
                .build();

        // When & Then
        assertTrue(validCoupon.isValidPeriod(now), "유효 기간 내 쿠폰은 true");
        assertFalse(expiredCoupon.isValidPeriod(now), "만료된 쿠폰은 false");
        assertFalse(futureCoupon.isValidPeriod(now), "아직 시작되지 않은 쿠폰은 false");
    }
}
