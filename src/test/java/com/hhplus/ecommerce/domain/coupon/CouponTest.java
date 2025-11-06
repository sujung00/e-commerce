package com.hhplus.ecommerce.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coupon 도메인 엔티티 단위 테스트
 * - 쿠폰 생성 및 기본 정보 관리
 * - 할인 유형 관리 (FIXED_AMOUNT, PERCENTAGE)
 * - 발급 수량 및 남은 수량 관리
 * - 유효 기간 관리
 * - 활성화 상태 관리
 * - 낙관적 락(version) 관리
 */
@DisplayName("Coupon 도메인 엔티티 테스트")
class CouponTest {

    private static final Long TEST_COUPON_ID = 1L;
    private static final String TEST_COUPON_NAME = "신규가입 할인 쿠폰";
    private static final String TEST_DESCRIPTION = "신규 가입자 전용 10% 할인";
    private static final String TEST_DISCOUNT_TYPE_FIXED = "FIXED_AMOUNT";
    private static final String TEST_DISCOUNT_TYPE_PERCENTAGE = "PERCENTAGE";
    private static final Long TEST_DISCOUNT_AMOUNT = 5000L;
    private static final BigDecimal TEST_DISCOUNT_RATE = new BigDecimal("10");
    private static final Integer TEST_TOTAL_QUANTITY = 1000;
    private static final Integer TEST_REMAINING_QTY = 800;
    private static final Long TEST_VERSION = 1L;

    // ========== Coupon 생성 ==========

    @Test
    @DisplayName("Coupon 생성 - 성공 (고정금액 할인)")
    void testCouponCreation_Success_FixedAmount() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName(TEST_COUPON_NAME)
                .description(TEST_DESCRIPTION)
                .discountType(TEST_DISCOUNT_TYPE_FIXED)
                .discountAmount(TEST_DISCOUNT_AMOUNT)
                .totalQuantity(TEST_TOTAL_QUANTITY)
                .remainingQty(TEST_REMAINING_QTY)
                .validFrom(now)
                .validUntil(now.plusDays(30))
                .isActive(true)
                .version(TEST_VERSION)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(coupon);
        assertEquals(TEST_COUPON_ID, coupon.getCouponId());
        assertEquals(TEST_COUPON_NAME, coupon.getCouponName());
        assertEquals(TEST_DISCOUNT_TYPE_FIXED, coupon.getDiscountType());
        assertEquals(TEST_DISCOUNT_AMOUNT, coupon.getDiscountAmount());
        assertEquals(TEST_TOTAL_QUANTITY, coupon.getTotalQuantity());
        assertEquals(TEST_REMAINING_QTY, coupon.getRemainingQty());
        assertTrue(coupon.getIsActive());
    }

    @Test
    @DisplayName("Coupon 생성 - 성공 (비율 할인)")
    void testCouponCreation_Success_Percentage() {
        // When
        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName("10% 할인")
                .discountType(TEST_DISCOUNT_TYPE_PERCENTAGE)
                .discountRate(TEST_DISCOUNT_RATE)
                .totalQuantity(500)
                .remainingQty(450)
                .isActive(true)
                .version(1L)
                .build();

        // Then
        assertEquals(TEST_DISCOUNT_TYPE_PERCENTAGE, coupon.getDiscountType());
        assertEquals(TEST_DISCOUNT_RATE, coupon.getDiscountRate());
    }

    @Test
    @DisplayName("Coupon 생성 - 비활성화 상태")
    void testCouponCreation_Inactive() {
        // When
        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName(TEST_COUPON_NAME)
                .isActive(false)
                .build();

        // Then
        assertFalse(coupon.getIsActive());
    }

    // ========== Coupon 정보 조회 ==========

    @Test
    @DisplayName("Coupon 조회 - 쿠폰명 확인")
    void testCouponRetrieve_CouponName() {
        // When
        Coupon coupon = Coupon.builder()
                .couponName(TEST_COUPON_NAME)
                .build();

        // Then
        assertEquals(TEST_COUPON_NAME, coupon.getCouponName());
    }

    @Test
    @DisplayName("Coupon 조회 - 할인 유형 확인")
    void testCouponRetrieve_DiscountType() {
        // When
        Coupon coupon = Coupon.builder()
                .discountType(TEST_DISCOUNT_TYPE_FIXED)
                .build();

        // Then
        assertEquals(TEST_DISCOUNT_TYPE_FIXED, coupon.getDiscountType());
    }

    @Test
    @DisplayName("Coupon 조회 - 할인 금액 확인")
    void testCouponRetrieve_DiscountAmount() {
        // When
        Coupon coupon = Coupon.builder()
                .discountAmount(TEST_DISCOUNT_AMOUNT)
                .build();

        // Then
        assertEquals(TEST_DISCOUNT_AMOUNT, coupon.getDiscountAmount());
    }

    @Test
    @DisplayName("Coupon 조회 - 할인율 확인")
    void testCouponRetrieve_DiscountRate() {
        // When
        Coupon coupon = Coupon.builder()
                .discountRate(TEST_DISCOUNT_RATE)
                .build();

        // Then
        assertEquals(TEST_DISCOUNT_RATE, coupon.getDiscountRate());
    }

    // ========== 쿠폰 정보 변경 ==========

    @Test
    @DisplayName("Coupon 정보 변경 - 활성화 상태 변경")
    void testCouponUpdate_IsActive() {
        // Given
        Coupon coupon = Coupon.builder()
                .isActive(true)
                .build();

        // When
        coupon.setIsActive(false);

        // Then
        assertFalse(coupon.getIsActive());
    }

    @Test
    @DisplayName("Coupon 정보 변경 - 설명 변경")
    void testCouponUpdate_Description() {
        // Given
        Coupon coupon = Coupon.builder()
                .description("기존 설명")
                .build();

        // When
        coupon.setDescription("새로운 설명");

        // Then
        assertEquals("새로운 설명", coupon.getDescription());
    }

    // ========== 발급 수량 관리 ==========

    @Test
    @DisplayName("발급 수량 관리 - 남은 수량 감소")
    void testQuantityManagement_DecreaseRemaining() {
        // Given
        Coupon coupon = Coupon.builder()
                .totalQuantity(1000)
                .remainingQty(1000)
                .build();

        // When
        coupon.setRemainingQty(coupon.getRemainingQty() - 1);

        // Then
        assertEquals(999, coupon.getRemainingQty());
    }

    @Test
    @DisplayName("발급 수량 관리 - 남은 수량 0으로 설정")
    void testQuantityManagement_SetToZero() {
        // Given
        Coupon coupon = Coupon.builder()
                .totalQuantity(1000)
                .remainingQty(10)
                .build();

        // When
        coupon.setRemainingQty(0);

        // Then
        assertEquals(0, coupon.getRemainingQty());
    }

    @Test
    @DisplayName("발급 수량 관리 - 여러 번의 발급")
    void testQuantityManagement_MultipleIssuance() {
        // Given
        Coupon coupon = Coupon.builder()
                .totalQuantity(1000)
                .remainingQty(1000)
                .build();

        // When
        for (int i = 0; i < 100; i++) {
            coupon.setRemainingQty(coupon.getRemainingQty() - 1);
        }

        // Then
        assertEquals(900, coupon.getRemainingQty());
    }

    // ========== 유효 기간 관리 ==========

    @Test
    @DisplayName("유효 기간 - 유효 기간 설정")
    void testValidPeriod_Set() {
        // When
        LocalDateTime validFrom = LocalDateTime.now();
        LocalDateTime validUntil = validFrom.plusDays(30);

        Coupon coupon = Coupon.builder()
                .validFrom(validFrom)
                .validUntil(validUntil)
                .build();

        // Then
        assertEquals(validFrom, coupon.getValidFrom());
        assertEquals(validUntil, coupon.getValidUntil());
    }

    @Test
    @DisplayName("유효 기간 - 유효 기간 변경")
    void testValidPeriod_Update() {
        // Given
        LocalDateTime oldValidUntil = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .validUntil(oldValidUntil)
                .build();

        // When
        LocalDateTime newValidUntil = LocalDateTime.now().plusDays(60);
        coupon.setValidUntil(newValidUntil);

        // Then
        assertEquals(newValidUntil, coupon.getValidUntil());
    }

    @Test
    @DisplayName("유효 기간 - 기간 검증 로직")
    void testValidPeriod_Validation() {
        // When
        LocalDateTime validFrom = LocalDateTime.now();
        LocalDateTime validUntil = validFrom.plusDays(30);

        Coupon coupon = Coupon.builder()
                .validFrom(validFrom)
                .validUntil(validUntil)
                .build();

        // Then
        assertTrue(validFrom.isBefore(validUntil));
    }

    // ========== 낙관적 락(Version) 관리 ==========

    @Test
    @DisplayName("낙관적 락 - 버전 증가")
    void testOptimisticLock_VersionIncrement() {
        // Given
        Coupon coupon = Coupon.builder()
                .version(1L)
                .build();

        // When
        coupon.setVersion(2L);

        // Then
        assertEquals(2L, coupon.getVersion());
    }

    @Test
    @DisplayName("낙관적 락 - 발급 시 버전 증가")
    void testOptimisticLock_IncrementOnIssuance() {
        // Given
        Coupon coupon = Coupon.builder()
                .totalQuantity(1000)
                .remainingQty(1000)
                .version(1L)
                .build();

        // When: 쿠폰 발급 시뮬레이션
        coupon.setRemainingQty(coupon.getRemainingQty() - 1);
        coupon.setVersion(coupon.getVersion() + 1);

        // Then
        assertEquals(999, coupon.getRemainingQty());
        assertEquals(2L, coupon.getVersion());
    }

    // ========== 타임스탐프 ==========

    @Test
    @DisplayName("타임스탐프 - createdAt 설정")
    void testTimestamp_CreatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .createdAt(now)
                .build();

        // Then
        assertNotNull(coupon.getCreatedAt());
        assertEquals(now, coupon.getCreatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - updatedAt 설정")
    void testTimestamp_UpdatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(coupon.getUpdatedAt());
        assertEquals(now, coupon.getUpdatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - 변경")
    void testTimestamp_Update() {
        // Given
        LocalDateTime originalTime = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .createdAt(originalTime)
                .updatedAt(originalTime)
                .build();

        // When
        LocalDateTime newTime = originalTime.plusHours(1);
        coupon.setUpdatedAt(newTime);

        // Then
        assertEquals(originalTime, coupon.getCreatedAt());
        assertEquals(newTime, coupon.getUpdatedAt());
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("경계값 - 0원 할인")
    void testBoundary_ZeroDiscount() {
        // When
        Coupon coupon = Coupon.builder()
                .discountAmount(0L)
                .build();

        // Then
        assertEquals(0L, coupon.getDiscountAmount());
    }

    @Test
    @DisplayName("경계값 - 높은 할인액")
    void testBoundary_HighDiscount() {
        // When
        Coupon coupon = Coupon.builder()
                .discountAmount(Long.MAX_VALUE / 2)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE / 2, coupon.getDiscountAmount());
    }

    @Test
    @DisplayName("경계값 - 0% 할인율")
    void testBoundary_ZeroDiscountRate() {
        // When
        Coupon coupon = Coupon.builder()
                .discountRate(BigDecimal.ZERO)
                .build();

        // Then
        assertEquals(BigDecimal.ZERO, coupon.getDiscountRate());
    }

    @Test
    @DisplayName("경계값 - 100% 할인율")
    void testBoundary_FullDiscountRate() {
        // When
        Coupon coupon = Coupon.builder()
                .discountRate(new BigDecimal("100"))
                .build();

        // Then
        assertEquals(new BigDecimal("100"), coupon.getDiscountRate());
    }

    @Test
    @DisplayName("경계값 - 0개 발급 수량")
    void testBoundary_ZeroQuantity() {
        // When
        Coupon coupon = Coupon.builder()
                .totalQuantity(0)
                .remainingQty(0)
                .build();

        // Then
        assertEquals(0, coupon.getTotalQuantity());
        assertEquals(0, coupon.getRemainingQty());
    }

    @Test
    @DisplayName("경계값 - 높은 발급 수량")
    void testBoundary_HighQuantity() {
        // When
        Coupon coupon = Coupon.builder()
                .totalQuantity(Integer.MAX_VALUE)
                .remainingQty(Integer.MAX_VALUE)
                .build();

        // Then
        assertEquals(Integer.MAX_VALUE, coupon.getTotalQuantity());
    }

    @Test
    @DisplayName("경계값 - ID 값")
    void testBoundary_IdValue() {
        // When
        Coupon coupon = Coupon.builder()
                .couponId(Long.MAX_VALUE)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE, coupon.getCouponId());
    }

    // ========== null 안전성 ==========

    @Test
    @DisplayName("null 안전성 - 모든 필드 null")
    void testNullSafety_AllFields() {
        // When
        Coupon coupon = Coupon.builder().build();

        // Then
        assertNull(coupon.getCouponId());
        assertNull(coupon.getCouponName());
        assertNull(coupon.getDiscountType());
        assertNull(coupon.getDiscountAmount());
        assertNull(coupon.getDiscountRate());
    }

    @Test
    @DisplayName("null 안전성 - 선택적 필드 설정")
    void testNullSafety_PartialFields() {
        // When
        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName(TEST_COUPON_NAME)
                .build();

        // Then
        assertEquals(TEST_COUPON_ID, coupon.getCouponId());
        assertEquals(TEST_COUPON_NAME, coupon.getCouponName());
        assertNull(coupon.getDiscountType());
        assertNull(coupon.getDiscountAmount());
    }

    // ========== NoArgsConstructor/AllArgsConstructor ==========

    @Test
    @DisplayName("NoArgsConstructor 테스트")
    void testNoArgsConstructor() {
        // When
        Coupon coupon = new Coupon();

        // Then
        assertNull(coupon.getCouponId());
        assertNull(coupon.getCouponName());
    }

    @Test
    @DisplayName("AllArgsConstructor 테스트")
    void testAllArgsConstructor() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                TEST_COUPON_ID,
                TEST_COUPON_NAME,
                TEST_DESCRIPTION,
                TEST_DISCOUNT_TYPE_FIXED,
                TEST_DISCOUNT_AMOUNT,
                TEST_DISCOUNT_RATE,
                TEST_TOTAL_QUANTITY,
                TEST_REMAINING_QTY,
                now,
                now.plusDays(30),
                true,
                TEST_VERSION,
                now,
                now
        );

        // Then
        assertEquals(TEST_COUPON_ID, coupon.getCouponId());
        assertEquals(TEST_COUPON_NAME, coupon.getCouponName());
        assertEquals(TEST_DISCOUNT_TYPE_FIXED, coupon.getDiscountType());
        assertEquals(TEST_DISCOUNT_AMOUNT, coupon.getDiscountAmount());
        assertTrue(coupon.getIsActive());
    }

    // ========== Coupon 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 고정금액 할인 쿠폰 생성")
    void testScenario_CreateFixedAmountCoupon() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("5000원 할인")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .totalQuantity(1000)
                .remainingQty(1000)
                .validFrom(now)
                .validUntil(now.plusDays(30))
                .isActive(true)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Then
        assertEquals(5000L, coupon.getDiscountAmount());
        assertEquals(1000, coupon.getTotalQuantity());
        assertTrue(coupon.getIsActive());
    }

    @Test
    @DisplayName("사용 시나리오 - 비율 할인 쿠폰 생성")
    void testScenario_CreatePercentageCoupon() {
        // When
        Coupon coupon = Coupon.builder()
                .couponId(2L)
                .couponName("15% 할인")
                .discountType("PERCENTAGE")
                .discountRate(new BigDecimal("15"))
                .totalQuantity(500)
                .remainingQty(500)
                .isActive(true)
                .version(1L)
                .build();

        // Then
        assertEquals("PERCENTAGE", coupon.getDiscountType());
        assertEquals(new BigDecimal("15"), coupon.getDiscountRate());
    }

    @Test
    @DisplayName("사용 시나리오 - 쿠폰 발급 (재고 감소, 버전 증가)")
    void testScenario_IssuesCoupon() {
        // Given
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .totalQuantity(1000)
                .remainingQty(1000)
                .version(1L)
                .isActive(true)
                .build();

        // When: 100개 발급
        for (int i = 0; i < 100; i++) {
            coupon.setRemainingQty(coupon.getRemainingQty() - 1);
        }
        coupon.setVersion(coupon.getVersion() + 1);

        // Then
        assertEquals(900, coupon.getRemainingQty());
        assertEquals(2L, coupon.getVersion());
    }

    @Test
    @DisplayName("사용 시나리오 - 쿠폰 기간 만료")
    void testScenario_CouponExpired() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("기간 만료 쿠폰")
                .validFrom(now.minusDays(30))
                .validUntil(now.minusHours(1))
                .isActive(true)
                .build();

        // When: 기간 만료로 비활성화
        coupon.setIsActive(false);

        // Then
        assertFalse(coupon.getIsActive());
    }
}
