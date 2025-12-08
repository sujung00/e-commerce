package com.hhplus.ecommerce.unit.domain.coupon;

import com.hhplus.ecommerce.common.exception.ErrorCode;
import com.hhplus.ecommerce.common.exception.DomainException;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponDomainService;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CouponDomainService - 쿠폰 도메인 비즈니스 로직")
class CouponDomainServiceTest {

    private CouponDomainService couponDomainService;

    @BeforeEach
    void setUp() {
        couponDomainService = new CouponDomainService();
    }

    // ==================== validateCouponIssuable Tests ====================

    @Test
    @DisplayName("쿠폰 발급 검증 - 정상 케이스")
    void validateCouponIssuable_WithValidData_Success() {
        // Given
        Coupon coupon = createActiveCoupon(1L);
        User user = createTestUser(1L);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertDoesNotThrow(() -> couponDomainService.validateCouponIssuable(coupon, user, now));
    }

    @Test
    @DisplayName("쿠폰 발급 검증 - null 쿠폰")
    void validateCouponIssuable_WithNullCoupon_ThrowsException() {
        // Given
        User user = createTestUser(1L);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.validateCouponIssuable(null, user, now));
        assertEquals(ErrorCode.COUPON_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("쿠폰 발급 검증 - null 사용자")
    void validateCouponIssuable_WithNullUser_ThrowsException() {
        // Given
        Coupon coupon = createActiveCoupon(1L);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.validateCouponIssuable(coupon, null, now));
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("쿠폰 발급 검증 - 비활성 쿠폰")
    void validateCouponIssuable_WithInactiveCoupon_ThrowsException() {
        // Given
        Coupon coupon = createInactiveCoupon(1L);
        User user = createTestUser(1L);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.validateCouponIssuable(coupon, user, now));
        assertEquals(ErrorCode.COUPON_INACTIVE, exception.getErrorCode());
    }

    @Test
    @DisplayName("쿠폰 발급 검증 - 유효기간 만료")
    void validateCouponIssuable_WithExpiredCoupon_ThrowsException() {
        // Given
        Coupon coupon = createExpiredCoupon(1L);
        User user = createTestUser(1L);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.validateCouponIssuable(coupon, user, now));
        assertEquals(ErrorCode.COUPON_EXPIRED, exception.getErrorCode());
    }

    @Test
    @DisplayName("쿠폰 발급 검증 - 재고 부족")
    void validateCouponIssuable_WithZeroStock_ThrowsException() {
        // Given
        Coupon coupon = createCouponWithStock(1L, 0);
        User user = createTestUser(1L);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.validateCouponIssuable(coupon, user, now));
        assertEquals(ErrorCode.COUPON_OUT_OF_STOCK, exception.getErrorCode());
    }

    // ==================== decreaseStock Tests ====================

    @Test
    @DisplayName("쿠폰 재고 차감 - 정상 케이스")
    void decreaseStock_WithValidData_Success() {
        // Given
        Coupon coupon = createCouponWithStock(1L, 10);
        int initialStock = coupon.getRemainingQty();

        // When
        couponDomainService.decreaseStock(coupon, 1);

        // Then
        assertEquals(initialStock - 1, coupon.getRemainingQty());
        assertTrue(coupon.isActiveCoupon()); // 아직 남은 재고 있음
    }

    @Test
    @DisplayName("쿠폰 재고 차감 - null 쿠폰")
    void decreaseStock_WithNullCoupon_ThrowsException() {
        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.decreaseStock(null, 1));
        assertEquals(ErrorCode.COUPON_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("쿠폰 재고 차감 - 잘못된 수량")
    void decreaseStock_WithInvalidQuantity_ThrowsException() {
        // Given
        Coupon coupon = createCouponWithStock(1L, 10);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.decreaseStock(coupon, 0));
        assertEquals(ErrorCode.INVALID_QUANTITY, exception.getErrorCode());
    }

    @Test
    @DisplayName("쿠폰 재고 차감 - 재고 소진 시 자동 비활성화")
    void decreaseStock_WithLastItem_AutoDeactivates() {
        // Given
        Coupon coupon = createCouponWithStock(1L, 1);

        // When
        couponDomainService.decreaseStock(coupon, 1);

        // Then
        assertEquals(0, coupon.getRemainingQty());
        assertFalse(coupon.isActiveCoupon()); // 자동 비활성화
    }

    @Test
    @DisplayName("쿠폰 재고 차감 - 재고 없을 때")
    void decreaseStock_WithNoStock_ThrowsException() {
        // Given
        Coupon coupon = createCouponWithStock(1L, 0);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.decreaseStock(coupon, 1));
        assertEquals(ErrorCode.COUPON_OUT_OF_STOCK, exception.getErrorCode());
    }

    // ==================== validateCouponUsable Tests ====================

    @Test
    @DisplayName("쿠폰 사용 검증 - 정상 케이스")
    void validateCouponUsable_WithValidData_Success() {
        // Given
        Coupon coupon = createActiveCoupon(1L);
        UserCoupon userCoupon = createUserCoupon(1L, 1L, UserCouponStatus.UNUSED);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertDoesNotThrow(() -> couponDomainService.validateCouponUsable(userCoupon, coupon, now));
    }

    @Test
    @DisplayName("쿠폰 사용 검증 - 이미 사용된 쿠폰")
    void validateCouponUsable_WithUsedCoupon_ThrowsException() {
        // Given
        Coupon coupon = createActiveCoupon(1L);
        UserCoupon userCoupon = createUserCoupon(1L, 1L, UserCouponStatus.USED);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.validateCouponUsable(userCoupon, coupon, now));
        assertEquals(ErrorCode.COUPON_ALREADY_ISSUED, exception.getErrorCode());
    }

    @Test
    @DisplayName("쿠폰 사용 검증 - 비활성 쿠폰")
    void validateCouponUsable_WithInactiveCoupon_ThrowsException() {
        // Given
        Coupon coupon = createInactiveCoupon(1L);
        UserCoupon userCoupon = createUserCoupon(1L, 1L, UserCouponStatus.UNUSED);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.validateCouponUsable(userCoupon, coupon, now));
        assertEquals(ErrorCode.COUPON_INACTIVE, exception.getErrorCode());
    }

    @Test
    @DisplayName("쿠폰 사용 검증 - 유효기간 만료")
    void validateCouponUsable_WithExpiredCoupon_ThrowsException() {
        // Given
        Coupon coupon = createExpiredCoupon(1L);
        UserCoupon userCoupon = createUserCoupon(1L, 1L, UserCouponStatus.UNUSED);
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.validateCouponUsable(userCoupon, coupon, now));
        assertEquals(ErrorCode.COUPON_EXPIRED, exception.getErrorCode());
    }

    // ==================== calculateDiscountAmount Tests ====================

    @Test
    @DisplayName("쿠폰 할인액 계산 - 정액 할인")
    void calculateDiscountAmount_WithFixedAmount_ReturnsAmount() {
        // Given
        Coupon coupon = createFixedAmountCoupon(1L, 5000L);
        long subtotal = 20000L;

        // When
        long discount = couponDomainService.calculateDiscountAmount(coupon, subtotal);

        // Then
        assertEquals(5000L, discount);
    }

    @Test
    @DisplayName("쿠폰 할인액 계산 - 비율 할인")
    void calculateDiscountAmount_WithPercentage_ReturnsCalculatedAmount() {
        // Given
        Coupon coupon = createPercentageCoupon(1L, 0.1); // 10%
        long subtotal = 20000L;

        // When
        long discount = couponDomainService.calculateDiscountAmount(coupon, subtotal);

        // Then
        assertEquals(2000L, discount);
    }

    @Test
    @DisplayName("쿠폰 할인액 계산 - null 쿠폰")
    void calculateDiscountAmount_WithNullCoupon_ReturnsZero() {
        // When
        long discount = couponDomainService.calculateDiscountAmount(null, 20000L);

        // Then
        assertEquals(0, discount);
    }

    // ==================== restoreCoupon Tests ====================

    @Test
    @DisplayName("쿠폰 복구 - 정상 케이스")
    void restoreCoupon_WithValidCoupon_Success() {
        // Given
        Coupon coupon = createActiveCoupon(1L);

        // When & Then
        assertDoesNotThrow(() -> couponDomainService.restoreCoupon(coupon));
    }

    @Test
    @DisplayName("쿠폰 복구 - null 쿠폰")
    void restoreCoupon_WithNullCoupon_ThrowsException() {
        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> couponDomainService.restoreCoupon(null));
        assertEquals(ErrorCode.COUPON_NOT_FOUND, exception.getErrorCode());
    }

    // ==================== Helper Methods ====================

    private Coupon createActiveCoupon(Long couponId) {
        return Coupon.builder()
                .couponId(couponId)
                .couponName("Active Coupon")
                .description("Test coupon")
                .isActive(true)
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(BigDecimal.ZERO)
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Coupon createInactiveCoupon(Long couponId) {
        return Coupon.builder()
                .couponId(couponId)
                .couponName("Inactive Coupon")
                .isActive(false)
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(BigDecimal.ZERO)
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Coupon createExpiredCoupon(Long couponId) {
        return Coupon.builder()
                .couponId(couponId)
                .couponName("Expired Coupon")
                .isActive(true)
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(BigDecimal.ZERO)
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(31))
                .validUntil(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Coupon createCouponWithStock(Long couponId, int remainingQty) {
        return Coupon.builder()
                .couponId(couponId)
                .couponName("Stock Test Coupon")
                .isActive(true)
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(BigDecimal.ZERO)
                .remainingQty(remainingQty)
                .totalQuantity(remainingQty)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Coupon createFixedAmountCoupon(Long couponId, Long discountAmount) {
        return Coupon.builder()
                .couponId(couponId)
                .couponName("Fixed Amount Coupon")
                .isActive(true)
                .discountType("FIXED_AMOUNT")
                .discountAmount(discountAmount)
                .discountRate(BigDecimal.ZERO)
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Coupon createPercentageCoupon(Long couponId, double discountRate) {
        return Coupon.builder()
                .couponId(couponId)
                .couponName("Percentage Coupon")
                .isActive(true)
                .discountType("PERCENTAGE")
                .discountAmount(0L)
                .discountRate(new BigDecimal(discountRate))
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private User createTestUser(Long userId) {
        return User.builder()
                .userId(userId)
                .email("test" + userId + "@example.com")
                .name("TestUser")
                .phone("010-1234-5678")
                .balance(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private UserCoupon createUserCoupon(Long userCouponId, Long couponId, UserCouponStatus status) {
        return UserCoupon.builder()
                .userCouponId(userCouponId)
                .userId(1L)
                .couponId(couponId)
                .status(status)
                .issuedAt(LocalDateTime.now())
                .usedAt(null)
                .build();
    }
}
