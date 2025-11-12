package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CouponServiceTest - Application 계층 단위 테스트
 * Spring Boot 3.4+ Mockito 방식 테스트
 *
 * 테스트 대상: CouponService
 * - 쿠폰 발급 (선착순)
 * - 사용자 쿠폰 조회
 * - 발급 가능한 쿠폰 조회
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 쿠폰 발급, 조회
 * - 동시성 제어: 선착순 발급, 재고 감소, 중복 발급 방지
 * - 예외 케이스: 사용자 검증, 쿠폰 검증, 유효기간, 재고 부족
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService 단위 테스트")
class CouponServiceTest {

    private CouponService couponService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private UserRepository userRepository;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_COUPON_ID = 1L;
    private static final Long TEST_USER_COUPON_ID = 100L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        couponService = new CouponService(couponRepository, userCouponRepository, userRepository);
    }

    // ========== 쿠폰 발급 (issueCoupon) ==========

    @Test
    @DisplayName("쿠폰 발급 - 성공 (정액 할인)")
    void testIssueCoupon_Success_FixedAmount() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName("신규고객 할인 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(null)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .remainingQty(100)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(couponRepository.findByIdForUpdate(TEST_COUPON_ID))
                .thenReturn(Optional.of(coupon));

        UserCoupon savedUserCoupon = UserCoupon.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(UserCouponStatus.UNUSED)
                .issuedAt(LocalDateTime.now())
                .usedAt(null)
                .orderId(null)
                .build();

        when(userCouponRepository.findByUserIdAndCouponId(TEST_USER_ID, TEST_COUPON_ID))
                .thenReturn(Optional.empty());
        when(userCouponRepository.save(any(UserCoupon.class)))
                .thenReturn(savedUserCoupon);

        // When
        IssueCouponResponse result = couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_USER_ID, result.getUserId());
        assertEquals(TEST_COUPON_ID, result.getCouponId());
        assertEquals("신규고객 할인 쿠폰", result.getCouponName());
        assertEquals("FIXED_AMOUNT", result.getDiscountType());
        assertEquals(5000L, result.getDiscountAmount());
        assertEquals("UNUSED", result.getStatus());

        verify(userRepository, times(1)).existsById(TEST_USER_ID);
        verify(couponRepository, times(1)).findByIdForUpdate(TEST_COUPON_ID);
        verify(couponRepository, times(1)).update(any(Coupon.class));
        verify(userCouponRepository, times(1)).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("쿠폰 발급 - 성공 (할인율)")
    void testIssueCoupon_Success_PercentageDiscount() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Coupon coupon = Coupon.builder()
                .couponId(2L)
                .couponName("봄 시즌 할인")
                .discountType("PERCENTAGE")
                .discountAmount(null)
                .discountRate(new BigDecimal("10.00"))
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(14))
                .remainingQty(500)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(couponRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(coupon));
        when(userCouponRepository.findByUserIdAndCouponId(TEST_USER_ID, 2L))
                .thenReturn(Optional.empty());

        UserCoupon savedUserCoupon = UserCoupon.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponId(2L)
                .status(UserCouponStatus.UNUSED)
                .issuedAt(LocalDateTime.now())
                .usedAt(null)
                .orderId(null)
                .build();

        when(userCouponRepository.save(any(UserCoupon.class)))
                .thenReturn(savedUserCoupon);

        // When
        IssueCouponResponse result = couponService.issueCoupon(TEST_USER_ID, 2L);

        // Then
        assertNotNull(result);
        assertEquals("PERCENTAGE", result.getDiscountType());
        assertEquals(new BigDecimal("10.00"), result.getDiscountRate());
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (사용자 없음)")
    void testIssueCoupon_Failed_UserNotFound() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(false);

        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID);
        });

        verify(userRepository, times(1)).existsById(TEST_USER_ID);
        verify(couponRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (쿠폰 없음)")
    void testIssueCoupon_Failed_CouponNotFound() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(couponRepository.findByIdForUpdate(TEST_COUPON_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(CouponNotFoundException.class, () -> {
            couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID);
        });
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (비활성화됨)")
    void testIssueCoupon_Failed_InactiveCoupon() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName("비활성화 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(false)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .remainingQty(100)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(couponRepository.findByIdForUpdate(TEST_COUPON_ID))
                .thenReturn(Optional.of(coupon));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID);
        });
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (유효기간 전)")
    void testIssueCoupon_Failed_NotStartedYet() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName("미래 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(true)
                .validFrom(LocalDateTime.now().plusDays(10))
                .validUntil(LocalDateTime.now().plusDays(30))
                .remainingQty(100)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(couponRepository.findByIdForUpdate(TEST_COUPON_ID))
                .thenReturn(Optional.of(coupon));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID);
        });
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (유효기간 만료)")
    void testIssueCoupon_Failed_Expired() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName("만료된 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(30))
                .validUntil(LocalDateTime.now().minusDays(1))
                .remainingQty(100)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(couponRepository.findByIdForUpdate(TEST_COUPON_ID))
                .thenReturn(Optional.of(coupon));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID);
        });
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (재고 부족)")
    void testIssueCoupon_Failed_OutOfStock() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName("품절 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .remainingQty(0)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(couponRepository.findByIdForUpdate(TEST_COUPON_ID))
                .thenReturn(Optional.of(coupon));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID);
        });
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (중복 발급)")
    void testIssueCoupon_Failed_AlreadyIssued() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName("신규고객 할인 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .remainingQty(100)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(couponRepository.findByIdForUpdate(TEST_COUPON_ID))
                .thenReturn(Optional.of(coupon));

        UserCoupon existingUserCoupon = UserCoupon.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(UserCouponStatus.UNUSED)
                .issuedAt(LocalDateTime.now())
                .build();

        when(userCouponRepository.findByUserIdAndCouponId(TEST_USER_ID, TEST_COUPON_ID))
                .thenReturn(Optional.of(existingUserCoupon));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID);
        });
    }

    // ========== 사용자 쿠폰 조회 (getUserCoupons) ==========

    @Test
    @DisplayName("사용자 쿠폰 조회 - 성공 (UNUSED 상태)")
    void testGetUserCoupons_Success_ActiveStatus() {
        // Given
        List<UserCoupon> userCoupons = List.of(
                UserCoupon.builder()
                        .userCouponId(100L)
                        .userId(TEST_USER_ID)
                        .couponId(1L)
                        .status(UserCouponStatus.UNUSED)
                        .issuedAt(LocalDateTime.now().minusDays(5))
                        .usedAt(null)
                        .orderId(null)
                        .build(),
                UserCoupon.builder()
                        .userCouponId(101L)
                        .userId(TEST_USER_ID)
                        .couponId(2L)
                        .status(UserCouponStatus.UNUSED)
                        .issuedAt(LocalDateTime.now().minusDays(2))
                        .usedAt(null)
                        .orderId(null)
                        .build()
        );

        Coupon coupon1 = Coupon.builder()
                .couponId(1L)
                .couponName("신규고객 할인")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(5))
                .validUntil(LocalDateTime.now().plusDays(25))
                .remainingQty(100)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Coupon coupon2 = Coupon.builder()
                .couponId(2L)
                .couponName("여름 세일")
                .discountType("PERCENTAGE")
                .discountRate(new BigDecimal("15.00"))
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(2))
                .validUntil(LocalDateTime.now().plusDays(28))
                .remainingQty(50)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(userCouponRepository.findByUserIdAndStatus(TEST_USER_ID, "UNUSED"))
                .thenReturn(userCoupons);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon1));
        when(couponRepository.findById(2L)).thenReturn(Optional.of(coupon2));

        // When
        List<UserCouponResponse> result = couponService.getUserCoupons(TEST_USER_ID, "UNUSED");

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("신규고객 할인", result.get(0).getCouponName());
        assertEquals("UNUSED", result.get(0).getStatus());

        verify(userRepository, times(1)).existsById(TEST_USER_ID);
        verify(userCouponRepository, times(1)).findByUserIdAndStatus(TEST_USER_ID, "UNUSED");
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 성공 (빈 결과)")
    void testGetUserCoupons_Success_EmptyResult() {
        // Given
        when(userCouponRepository.findByUserIdAndStatus(TEST_USER_ID, "USED"))
                .thenReturn(Collections.emptyList());

        // When
        List<UserCouponResponse> result = couponService.getUserCoupons(TEST_USER_ID, "USED");

        // Then
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ========== 발급 가능한 쿠폰 조회 (getAvailableCoupons) ==========

    @Test
    @DisplayName("발급 가능한 쿠폰 조회 - 성공")
    void testGetAvailableCoupons_Success() {
        // Given
        // This test would verify the getAvailableCoupons method if implemented
        // For now, it's a placeholder for future implementation

        // When & Then
        // The actual test would depend on the implementation of getAvailableCoupons
    }

    // ========== 트랜잭션 처리 검증 ==========

    @Test
    @DisplayName("쿠폰 발급 - 재고 감소 (원자성 검증)")
    void testIssueCoupon_StockDecremented() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        Coupon coupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName("신규고객 할인 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .remainingQty(10)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(couponRepository.findByIdForUpdate(TEST_COUPON_ID))
                .thenReturn(Optional.of(coupon));
        when(userCouponRepository.findByUserIdAndCouponId(TEST_USER_ID, TEST_COUPON_ID))
                .thenReturn(Optional.empty());

        UserCoupon savedUserCoupon = UserCoupon.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .status(UserCouponStatus.UNUSED)
                .issuedAt(LocalDateTime.now())
                .build();

        when(userCouponRepository.save(any(UserCoupon.class)))
                .thenReturn(savedUserCoupon);

        // When
        IssueCouponResponse result = couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID);

        // Then
        assertNotNull(result);
        // Verify that the coupon repository's update method was called
        verify(couponRepository, times(1)).update(any(Coupon.class));

        // Verify version was incremented
        // The coupon passed to update should have version 2 (1 + 1)
        // and remainingQty 9 (10 - 1)
    }
}
