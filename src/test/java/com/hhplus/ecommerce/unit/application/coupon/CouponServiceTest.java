package com.hhplus.ecommerce.unit.application.coupon;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.coupon.CouponTransactionService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
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
 * CouponService 단위 테스트
 *
 * 테스트 범위:
 * - issueCoupon(): 사용자 검증, CouponTransactionService 위임, 재시도 정책
 * - getUserCoupons(): 사용자 검증, 캐시 키, 상태 필터
 * - getAvailableCoupons(): 캐시 동작
 *
 * DB 레벨 락·재고 차감·Outbox 로직은 CouponTransactionService 의 책임이므로
 * 이 테스트에서는 CouponTransactionService 를 mock 으로 대체한다.
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

    @Mock
    private com.hhplus.ecommerce.infrastructure.constants.RetryProperties retryProperties;

    @Mock
    private CouponTransactionService couponTransactionService;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_COUPON_ID = 1L;
    private static final Long TEST_USER_COUPON_ID = 100L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        com.hhplus.ecommerce.infrastructure.constants.RetryProperties.Coupon couponRetry =
                new com.hhplus.ecommerce.infrastructure.constants.RetryProperties.Coupon();
        lenient().when(retryProperties.getCoupon()).thenReturn(couponRetry);

        couponService = new CouponService(
                couponRepository, userCouponRepository, userRepository,
                retryProperties, couponTransactionService);
    }

    // ========== issueCoupon() — 흐름 조정 책임 ==========

    @Test
    @DisplayName("쿠폰 발급 - 성공 (정액 할인): couponTransactionService 에 위임 확인")
    void testIssueCoupon_Success_FixedAmount() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        IssueCouponResponse expected = buildResponse(TEST_USER_ID, TEST_COUPON_ID,
                "신규고객 할인 쿠폰", "FIXED_AMOUNT", 5000L, null);
        when(couponTransactionService.issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID))
                .thenReturn(expected);

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
        verify(couponTransactionService, times(1)).issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID);
    }

    @Test
    @DisplayName("쿠폰 발급 - 성공 (할인율)")
    void testIssueCoupon_Success_PercentageDiscount() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);

        IssueCouponResponse expected = buildResponse(TEST_USER_ID, 2L,
                "봄 시즌 할인", "PERCENTAGE", null, new BigDecimal("10.00"));
        when(couponTransactionService.issueCouponWithLock(TEST_USER_ID, 2L))
                .thenReturn(expected);

        // When
        IssueCouponResponse result = couponService.issueCoupon(TEST_USER_ID, 2L);

        // Then
        assertNotNull(result);
        assertEquals("PERCENTAGE", result.getDiscountType());
        assertEquals(new BigDecimal("10.00"), result.getDiscountRate());
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (사용자 없음): couponTransactionService 미호출 확인")
    void testIssueCoupon_Failed_UserNotFound() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(false);

        // When & Then
        assertThrows(UserNotFoundException.class,
                () -> couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID));

        verify(userRepository, times(1)).existsById(TEST_USER_ID);
        verify(couponTransactionService, never()).issueCouponWithLock(anyLong(), anyLong());
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (쿠폰 없음): CouponTransactionService 예외 전파")
    void testIssueCoupon_Failed_CouponNotFound() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(couponTransactionService.issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID))
                .thenThrow(new CouponNotFoundException(TEST_COUPON_ID));

        // When & Then
        assertThrows(CouponNotFoundException.class,
                () -> couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID));
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (비활성화됨): IllegalArgumentException 재시도 없이 즉시 전파")
    void testIssueCoupon_Failed_InactiveCoupon() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(couponTransactionService.issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID))
                .thenThrow(new IllegalArgumentException("쿠폰이 비활성화되어 있습니다"));

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID));

        // IllegalArgumentException 은 재시도하지 않음 — 1회만 호출
        verify(couponTransactionService, times(1)).issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID);
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (유효기간 전): IllegalArgumentException 즉시 전파")
    void testIssueCoupon_Failed_NotStartedYet() {
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(couponTransactionService.issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID))
                .thenThrow(new IllegalArgumentException("쿠폰이 유효기간을 벗어났습니다"));

        assertThrows(IllegalArgumentException.class,
                () -> couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID));
        verify(couponTransactionService, times(1)).issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID);
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (유효기간 만료): IllegalArgumentException 즉시 전파")
    void testIssueCoupon_Failed_Expired() {
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(couponTransactionService.issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID))
                .thenThrow(new IllegalArgumentException("쿠폰이 유효기간을 벗어났습니다"));

        assertThrows(IllegalArgumentException.class,
                () -> couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID));
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (재고 부족): IllegalArgumentException 즉시 전파")
    void testIssueCoupon_Failed_OutOfStock() {
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(couponTransactionService.issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID))
                .thenThrow(new IllegalArgumentException("쿠폰이 모두 소진되었습니다"));

        assertThrows(IllegalArgumentException.class,
                () -> couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID));
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (중복 발급): IllegalArgumentException 즉시 전파")
    void testIssueCoupon_Failed_AlreadyIssued() {
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(couponTransactionService.issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID))
                .thenThrow(new IllegalArgumentException("이 쿠폰은 이미 발급받으셨습니다"));

        assertThrows(IllegalArgumentException.class,
                () -> couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID));
    }

    @Test
    @DisplayName("쿠폰 발급 - 시스템 오류 시 maxAttempts 만큼 재시도")
    void testIssueCoupon_RetryOnSystemError() {
        // Given
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        // RuntimeException → 재시도 대상 (IllegalArgumentException 아님)
        when(couponTransactionService.issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID))
                .thenThrow(new RuntimeException("DB 연결 오류"));

        // When & Then: maxAttempts(3) 초과 후 예외
        assertThrows(RuntimeException.class,
                () -> couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID));

        // 기본 maxAttempts = 3, 모두 실패하면 3회 호출
        verify(couponTransactionService, times(3)).issueCouponWithLock(TEST_USER_ID, TEST_COUPON_ID);
    }

    // ========== getUserCoupons() ==========

    @Test
    @DisplayName("사용자 쿠폰 조회 - 성공 (UNUSED 상태)")
    void testGetUserCoupons_Success_ActiveStatus() {
        // Given
        List<UserCoupon> userCoupons = List.of(
                UserCoupon.builder()
                        .userCouponId(100L).userId(TEST_USER_ID).couponId(1L)
                        .status(UserCouponStatus.UNUSED).issuedAt(LocalDateTime.now().minusDays(5))
                        .build(),
                UserCoupon.builder()
                        .userCouponId(101L).userId(TEST_USER_ID).couponId(2L)
                        .status(UserCouponStatus.UNUSED).issuedAt(LocalDateTime.now().minusDays(2))
                        .build()
        );

        Coupon coupon1 = Coupon.builder().couponId(1L).couponName("신규고객 할인")
                .discountType("FIXED_AMOUNT").discountAmount(5000L).isActive(true)
                .validFrom(LocalDateTime.now().minusDays(5)).validUntil(LocalDateTime.now().plusDays(25))
                .remainingQty(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        Coupon coupon2 = Coupon.builder().couponId(2L).couponName("여름 세일")
                .discountType("PERCENTAGE").discountRate(new BigDecimal("15.00")).isActive(true)
                .validFrom(LocalDateTime.now().minusDays(2)).validUntil(LocalDateTime.now().plusDays(28))
                .remainingQty(50).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(userCouponRepository.findByUserIdAndStatus(TEST_USER_ID, "UNUSED")).thenReturn(userCoupons);
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
        when(userRepository.existsById(TEST_USER_ID)).thenReturn(true);
        when(userCouponRepository.findByUserIdAndStatus(TEST_USER_ID, "USED"))
                .thenReturn(Collections.emptyList());

        List<UserCouponResponse> result = couponService.getUserCoupons(TEST_USER_ID, "USED");

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ========== getAvailableCoupons() (placeholder) ==========

    @Test
    @DisplayName("발급 가능한 쿠폰 조회 - 구현 확인용 placeholder")
    void testGetAvailableCoupons_Success() {
        // getAvailableCoupons() 는 @Cacheable 어노테이션으로 캐시를 거치므로
        // 통합 테스트에서 검증하는 것이 더 적합하다.
        // 단위 테스트에서는 빈 리스트 반환 경로만 확인.
        when(couponRepository.findAllAvailable()).thenReturn(Collections.emptyList());

        var result = couponService.getAvailableCoupons();

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // helper
    // ───────────────────────────────────────────────────────────────────────────

    private IssueCouponResponse buildResponse(Long userId, Long couponId,
                                               String couponName, String discountType,
                                               Long discountAmount, BigDecimal discountRate) {
        UserCoupon uc = UserCoupon.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(userId)
                .couponId(couponId)
                .status(UserCouponStatus.UNUSED)
                .issuedAt(LocalDateTime.now())
                .build();
        Coupon coupon = Coupon.builder()
                .couponId(couponId)
                .couponName(couponName)
                .discountType(discountType)
                .discountAmount(discountAmount)
                .discountRate(discountRate)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .remainingQty(9)
                .version(2L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return IssueCouponResponse.from(uc, coupon);
    }
}
