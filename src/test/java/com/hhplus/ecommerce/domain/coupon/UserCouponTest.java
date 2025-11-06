package com.hhplus.ecommerce.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserCoupon 도메인 엔티티 단위 테스트
 * - 사용자 쿠폰 생성 및 정보 관리
 * - 쿠폰 상태 전이 (ACTIVE → USED/EXPIRED)
 * - 발급 및 사용 기록 관리
 * - 타임스탐프 추적
 * - UNIQUE(user_id, coupon_id) 제약 조건 고려
 */
@DisplayName("UserCoupon 도메인 엔티티 테스트")
class UserCouponTest {

    private static final Long TEST_USER_COUPON_ID = 1L;
    private static final Long TEST_USER_ID = 100L;
    private static final Long TEST_COUPON_ID = 1L;
    private static final String TEST_STATUS_ACTIVE = "ACTIVE";
    private static final String TEST_STATUS_USED = "USED";
    private static final String TEST_STATUS_EXPIRED = "EXPIRED";

    // ========== UserCoupon 생성 ==========

    @Test
    @DisplayName("UserCoupon 생성 - 성공")
    void testUserCouponCreation_Success() {
        // When
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(TEST_STATUS_ACTIVE)
                .issuedAt(now)
                .usedAt(null)
                .orderId(null)
                .build();

        // Then
        assertNotNull(userCoupon);
        assertEquals(TEST_USER_COUPON_ID, userCoupon.getUserCouponId());
        assertEquals(TEST_USER_ID, userCoupon.getUserId());
        assertEquals(TEST_COUPON_ID, userCoupon.getCouponId());
        assertEquals(TEST_STATUS_ACTIVE, userCoupon.getStatus());
        assertNotNull(userCoupon.getIssuedAt());
        assertNull(userCoupon.getUsedAt());
        assertNull(userCoupon.getOrderId());
    }

    @Test
    @DisplayName("UserCoupon 생성 - 초기 상태는 ACTIVE")
    void testUserCouponCreation_InitialStatusActive() {
        // When
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(TEST_STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        // Then
        assertEquals(TEST_STATUS_ACTIVE, userCoupon.getStatus());
        assertNull(userCoupon.getUsedAt());
        assertNull(userCoupon.getOrderId());
    }

    @Test
    @DisplayName("UserCoupon 생성 - 다양한 사용자-쿠폰 조합")
    void testUserCouponCreation_DifferentCombinations() {
        // When
        UserCoupon userCoupon1 = UserCoupon.builder()
                .userId(1L)
                .couponId(100L)
                .status(TEST_STATUS_ACTIVE)
                .build();
        UserCoupon userCoupon2 = UserCoupon.builder()
                .userId(2L)
                .couponId(100L)
                .status(TEST_STATUS_ACTIVE)
                .build();
        UserCoupon userCoupon3 = UserCoupon.builder()
                .userId(1L)
                .couponId(101L)
                .status(TEST_STATUS_ACTIVE)
                .build();

        // Then
        assertEquals(1L, userCoupon1.getUserId());
        assertEquals(100L, userCoupon1.getCouponId());
        assertEquals(2L, userCoupon2.getUserId());
        assertEquals(100L, userCoupon2.getCouponId());
        assertEquals(1L, userCoupon3.getUserId());
        assertEquals(101L, userCoupon3.getCouponId());
    }

    // ========== UserCoupon 정보 조회 ==========

    @Test
    @DisplayName("UserCoupon 조회 - 사용자 ID 확인")
    void testUserCouponRetrieve_UserId() {
        // When
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(TEST_USER_ID)
                .build();

        // Then
        assertEquals(TEST_USER_ID, userCoupon.getUserId());
    }

    @Test
    @DisplayName("UserCoupon 조회 - 쿠폰 ID 확인")
    void testUserCouponRetrieve_CouponId() {
        // When
        UserCoupon userCoupon = UserCoupon.builder()
                .couponId(TEST_COUPON_ID)
                .build();

        // Then
        assertEquals(TEST_COUPON_ID, userCoupon.getCouponId());
    }

    @Test
    @DisplayName("UserCoupon 조회 - 상태 확인")
    void testUserCouponRetrieve_Status() {
        // When
        UserCoupon userCoupon = UserCoupon.builder()
                .status(TEST_STATUS_ACTIVE)
                .build();

        // Then
        assertEquals(TEST_STATUS_ACTIVE, userCoupon.getStatus());
    }

    @Test
    @DisplayName("UserCoupon 조회 - 발급 시각 확인")
    void testUserCouponRetrieve_IssuedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .issuedAt(now)
                .build();

        // Then
        assertEquals(now, userCoupon.getIssuedAt());
    }

    // ========== 쿠폰 상태 전이 ==========

    @Test
    @DisplayName("상태 전이 - ACTIVE → USED")
    void testStateTransition_ActiveToUsed() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(TEST_STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        // When
        userCoupon.setStatus(TEST_STATUS_USED);
        userCoupon.setUsedAt(LocalDateTime.now());
        userCoupon.setOrderId(1000L);

        // Then
        assertEquals(TEST_STATUS_USED, userCoupon.getStatus());
        assertNotNull(userCoupon.getUsedAt());
        assertEquals(1000L, userCoupon.getOrderId());
    }

    @Test
    @DisplayName("상태 전이 - ACTIVE → EXPIRED")
    void testStateTransition_ActiveToExpired() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(TEST_STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        // When
        userCoupon.setStatus(TEST_STATUS_EXPIRED);

        // Then
        assertEquals(TEST_STATUS_EXPIRED, userCoupon.getStatus());
        assertNull(userCoupon.getUsedAt());
        assertNull(userCoupon.getOrderId());
    }

    // ========== 사용 기록 관리 ==========

    @Test
    @DisplayName("사용 기록 - 사용 시각 기록")
    void testUsageTracking_RecordUsedAt() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(TEST_STATUS_ACTIVE)
                .build();

        // When
        LocalDateTime usedAt = LocalDateTime.now();
        userCoupon.setUsedAt(usedAt);
        userCoupon.setStatus(TEST_STATUS_USED);

        // Then
        assertEquals(usedAt, userCoupon.getUsedAt());
        assertEquals(TEST_STATUS_USED, userCoupon.getStatus());
    }

    @Test
    @DisplayName("사용 기록 - 주문 ID 기록")
    void testUsageTracking_RecordOrderId() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(TEST_STATUS_ACTIVE)
                .build();

        // When
        Long orderId = 5000L;
        userCoupon.setOrderId(orderId);
        userCoupon.setUsedAt(LocalDateTime.now());
        userCoupon.setStatus(TEST_STATUS_USED);

        // Then
        assertEquals(orderId, userCoupon.getOrderId());
    }

    @Test
    @DisplayName("사용 기록 - 미사용 쿠폰은 주문 ID 없음")
    void testUsageTracking_UnusedCouponNoOrderId() {
        // When
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(TEST_STATUS_ACTIVE)
                .build();

        // Then
        assertNull(userCoupon.getOrderId());
        assertNull(userCoupon.getUsedAt());
    }

    // ========== 타임스탐프 ==========

    @Test
    @DisplayName("타임스탐프 - issuedAt 설정")
    void testTimestamp_IssuedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .issuedAt(now)
                .build();

        // Then
        assertNotNull(userCoupon.getIssuedAt());
        assertEquals(now, userCoupon.getIssuedAt());
    }

    @Test
    @DisplayName("타임스탐프 - usedAt 설정")
    void testTimestamp_UsedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .usedAt(now)
                .build();

        // Then
        assertNotNull(userCoupon.getUsedAt());
        assertEquals(now, userCoupon.getUsedAt());
    }

    @Test
    @DisplayName("타임스탐프 - 발급 시각과 사용 시각 순서")
    void testTimestamp_IssuedBeforeUsed() {
        // When
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime usedAt = issuedAt.plusHours(24);

        UserCoupon userCoupon = UserCoupon.builder()
                .issuedAt(issuedAt)
                .usedAt(usedAt)
                .build();

        // Then
        assertTrue(userCoupon.getIssuedAt().isBefore(userCoupon.getUsedAt()));
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("경계값 - ID 값")
    void testBoundary_IdValues() {
        // When
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(Long.MAX_VALUE)
                .userId(Long.MAX_VALUE)
                .couponId(Long.MAX_VALUE)
                .orderId(Long.MAX_VALUE)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE, userCoupon.getUserCouponId());
        assertEquals(Long.MAX_VALUE, userCoupon.getUserId());
        assertEquals(Long.MAX_VALUE, userCoupon.getCouponId());
        assertEquals(Long.MAX_VALUE, userCoupon.getOrderId());
    }

    @Test
    @DisplayName("경계값 - null orderId")
    void testBoundary_NullOrderId() {
        // When
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(TEST_STATUS_ACTIVE)
                .orderId(null)
                .build();

        // Then
        assertNull(userCoupon.getOrderId());
    }

    // ========== null 안전성 ==========

    @Test
    @DisplayName("null 안전성 - 모든 필드 null")
    void testNullSafety_AllFields() {
        // When
        UserCoupon userCoupon = UserCoupon.builder().build();

        // Then
        assertNull(userCoupon.getUserCouponId());
        assertNull(userCoupon.getUserId());
        assertNull(userCoupon.getCouponId());
        assertNull(userCoupon.getStatus());
        assertNull(userCoupon.getIssuedAt());
        assertNull(userCoupon.getUsedAt());
        assertNull(userCoupon.getOrderId());
    }

    @Test
    @DisplayName("null 안전성 - 선택적 필드 설정")
    void testNullSafety_PartialFields() {
        // When
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .build();

        // Then
        assertEquals(TEST_USER_ID, userCoupon.getUserId());
        assertEquals(TEST_COUPON_ID, userCoupon.getCouponId());
        assertNull(userCoupon.getStatus());
        assertNull(userCoupon.getIssuedAt());
    }

    // ========== NoArgsConstructor/AllArgsConstructor ==========

    @Test
    @DisplayName("NoArgsConstructor 테스트")
    void testNoArgsConstructor() {
        // When
        UserCoupon userCoupon = new UserCoupon();

        // Then
        assertNull(userCoupon.getUserCouponId());
        assertNull(userCoupon.getUserId());
    }

    @Test
    @DisplayName("AllArgsConstructor 테스트")
    void testAllArgsConstructor() {
        // When
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = new UserCoupon(
                TEST_USER_COUPON_ID,
                TEST_USER_ID,
                TEST_COUPON_ID,
                TEST_STATUS_ACTIVE,
                now,
                null,
                null
        );

        // Then
        assertEquals(TEST_USER_COUPON_ID, userCoupon.getUserCouponId());
        assertEquals(TEST_USER_ID, userCoupon.getUserId());
        assertEquals(TEST_COUPON_ID, userCoupon.getCouponId());
        assertEquals(TEST_STATUS_ACTIVE, userCoupon.getStatus());
        assertEquals(now, userCoupon.getIssuedAt());
        assertNull(userCoupon.getUsedAt());
        assertNull(userCoupon.getOrderId());
    }

    // ========== UserCoupon 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 쿠폰 발급")
    void testScenario_IssueCoupon() {
        // When
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status(TEST_STATUS_ACTIVE)
                .issuedAt(now)
                .build();

        // Then
        assertEquals(100L, userCoupon.getUserId());
        assertEquals(1L, userCoupon.getCouponId());
        assertEquals(TEST_STATUS_ACTIVE, userCoupon.getStatus());
        assertNull(userCoupon.getUsedAt());
    }

    @Test
    @DisplayName("사용 시나리오 - 쿠폰 사용")
    void testScenario_UseCoupon() {
        // Given
        LocalDateTime issuedAt = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status(TEST_STATUS_ACTIVE)
                .issuedAt(issuedAt)
                .build();

        // When
        LocalDateTime usedAt = issuedAt.plusHours(2);
        userCoupon.setStatus(TEST_STATUS_USED);
        userCoupon.setUsedAt(usedAt);
        userCoupon.setOrderId(5000L);

        // Then
        assertEquals(TEST_STATUS_USED, userCoupon.getStatus());
        assertEquals(5000L, userCoupon.getOrderId());
        assertEquals(usedAt, userCoupon.getUsedAt());
    }

    @Test
    @DisplayName("사용 시나리오 - 쿠폰 기간 만료")
    void testScenario_CouponExpire() {
        // Given
        LocalDateTime issuedAt = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status(TEST_STATUS_ACTIVE)
                .issuedAt(issuedAt)
                .build();

        // When
        userCoupon.setStatus(TEST_STATUS_EXPIRED);

        // Then
        assertEquals(TEST_STATUS_EXPIRED, userCoupon.getStatus());
        assertNull(userCoupon.getUsedAt());
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자의 여러 쿠폰 보유")
    void testScenario_UserMultipleCoupons() {
        // When
        LocalDateTime now = LocalDateTime.now();
        UserCoupon coupon1 = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status(TEST_STATUS_ACTIVE)
                .issuedAt(now)
                .build();

        UserCoupon coupon2 = UserCoupon.builder()
                .userCouponId(2L)
                .userId(100L)
                .couponId(2L)
                .status(TEST_STATUS_USED)
                .issuedAt(now.minusDays(10))
                .usedAt(now.minusDays(5))
                .orderId(1000L)
                .build();

        UserCoupon coupon3 = UserCoupon.builder()
                .userCouponId(3L)
                .userId(100L)
                .couponId(3L)
                .status(TEST_STATUS_EXPIRED)
                .issuedAt(now.minusDays(30))
                .build();

        // Then
        assertEquals(100L, coupon1.getUserId());
        assertEquals(100L, coupon2.getUserId());
        assertEquals(100L, coupon3.getUserId());
        assertEquals(TEST_STATUS_ACTIVE, coupon1.getStatus());
        assertEquals(TEST_STATUS_USED, coupon2.getStatus());
        assertEquals(TEST_STATUS_EXPIRED, coupon3.getStatus());
    }

    @Test
    @DisplayName("사용 시나리오 - UNIQUE(user_id, coupon_id) 제약")
    void testScenario_UniqueConstraint() {
        // When
        UserCoupon userCoupon1 = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status(TEST_STATUS_ACTIVE)
                .build();

        UserCoupon userCoupon2 = UserCoupon.builder()
                .userCouponId(2L)
                .userId(100L)
                .couponId(2L)
                .status(TEST_STATUS_ACTIVE)
                .build();

        // Then
        // 같은 사용자가 다른 쿠폰을 받을 수 있음
        assertEquals(100L, userCoupon1.getUserId());
        assertEquals(100L, userCoupon2.getUserId());
        assertNotEquals(userCoupon1.getCouponId(), userCoupon2.getCouponId());
    }
}
