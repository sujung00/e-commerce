package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryCouponRepository 단위 테스트
 * - Coupon CRUD 작업 검증
 * - 비관적 락 시뮬레이션 (findByIdForUpdate)
 * - 발급 가능한 쿠폰 조회 (findAllAvailable)
 * - 선착순 발급 시뮬레이션
 */
@DisplayName("InMemoryCouponRepository 테스트")
class InMemoryCouponRepositoryTest {

    private InMemoryCouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new InMemoryCouponRepository();
    }

    // ========== Coupon 조회 ==========

    @Test
    @DisplayName("findById - 기존 쿠폰 조회")
    void testFindById_ExistingCoupon() {
        // When
        Optional<Coupon> coupon = couponRepository.findById(1L);

        // Then
        assertTrue(coupon.isPresent());
        assertEquals(1L, coupon.get().getCouponId());
        assertEquals("10% 할인 쿠폰", coupon.get().getCouponName());
        assertEquals("PERCENTAGE", coupon.get().getDiscountType());
    }

    @Test
    @DisplayName("findById - 없는 쿠폰은 Optional.empty()")
    void testFindById_NonExistent() {
        // When
        Optional<Coupon> coupon = couponRepository.findById(999L);

        // Then
        assertTrue(coupon.isEmpty());
    }

    @Test
    @DisplayName("findById - 다양한 할인 유형 쿠폰 조회")
    void testFindById_DifferentDiscountTypes() {
        // When
        Optional<Coupon> percentageCoupon = couponRepository.findById(1L);
        Optional<Coupon> fixedAmountCoupon = couponRepository.findById(2L);

        // Then
        assertTrue(percentageCoupon.isPresent());
        assertTrue(fixedAmountCoupon.isPresent());
        assertEquals("PERCENTAGE", percentageCoupon.get().getDiscountType());
        assertEquals("FIXED_AMOUNT", fixedAmountCoupon.get().getDiscountType());
    }

    // ========== Coupon 저장 ==========

    @Test
    @DisplayName("save - 새 쿠폰 저장 (ID 자동 할당)")
    void testSave_NewCoupon() {
        // When
        Coupon newCoupon = Coupon.builder()
                .couponName("신규 쿠폰")
                .description("새로운 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(10000L)
                .totalQuantity(50)
                .remainingQty(50)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .version(1L)
                .build();

        Coupon saved = couponRepository.save(newCoupon);

        // Then
        assertNotNull(saved.getCouponId());
        assertTrue(saved.getCouponId() >= 1001L);
        assertEquals("신규 쿠폰", saved.getCouponName());
    }

    @Test
    @DisplayName("save - 여러 쿠폰 저장 시 ID 증가")
    void testSave_IdSequenceIncrement() {
        // When
        Coupon coupon1 = Coupon.builder()
                .couponName("쿠폰1")
                .description("설명1")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .totalQuantity(10)
                .remainingQty(10)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(10))
                .isActive(true)
                .version(1L)
                .build();

        Coupon coupon2 = Coupon.builder()
                .couponName("쿠폰2")
                .description("설명2")
                .discountType("FIXED_AMOUNT")
                .discountAmount(3000L)
                .totalQuantity(10)
                .remainingQty(10)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(10))
                .isActive(true)
                .version(1L)
                .build();

        Coupon saved1 = couponRepository.save(coupon1);
        Coupon saved2 = couponRepository.save(coupon2);

        // Then
        assertTrue(saved1.getCouponId() < saved2.getCouponId());
    }

    // ========== 비관적 락 (Pessimistic Lock) 시뮬레이션 ==========

    @Test
    @DisplayName("findByIdForUpdate - 락 시뮬레이션 조회")
    void testFindByIdForUpdate_LockSimulation() {
        // When
        Optional<Coupon> coupon = couponRepository.findByIdForUpdate(1L);

        // Then
        assertTrue(coupon.isPresent());
        assertEquals("10% 할인 쿠폰", coupon.get().getCouponName());
    }

    @Test
    @DisplayName("findByIdForUpdate - 없는 쿠폰 조회")
    void testFindByIdForUpdate_NonExistent() {
        // When
        Optional<Coupon> coupon = couponRepository.findByIdForUpdate(999L);

        // Then
        assertTrue(coupon.isEmpty());
    }

    // ========== 발급 가능한 쿠폰 조회 ==========

    @Test
    @DisplayName("findAllAvailable - 발급 가능한 쿠폰 조회")
    void testFindAllAvailable_AvailableCoupons() {
        // When
        List<Coupon> availableCoupons = couponRepository.findAllAvailable();

        // Then
        assertNotNull(availableCoupons);
        assertFalse(availableCoupons.isEmpty());
        assertTrue(availableCoupons.stream().allMatch(c -> Boolean.TRUE.equals(c.getIsActive())));
        assertTrue(availableCoupons.stream().allMatch(c -> c.getRemainingQty() > 0));
    }

    @Test
    @DisplayName("findAllAvailable - 활성 상태 확인")
    void testFindAllAvailable_ActiveOnly() {
        // When
        List<Coupon> availableCoupons = couponRepository.findAllAvailable();

        // Then
        assertTrue(availableCoupons.stream().allMatch(c -> Boolean.TRUE.equals(c.getIsActive())));
    }

    @Test
    @DisplayName("findAllAvailable - 유효 기간 내 쿠폰만 포함")
    void testFindAllAvailable_ValidPeriodOnly() {
        // When
        List<Coupon> availableCoupons = couponRepository.findAllAvailable();
        LocalDateTime now = LocalDateTime.now();

        // Then
        for (Coupon coupon : availableCoupons) {
            assertFalse(now.isBefore(coupon.getValidFrom()), "쿠폰이 아직 시작되지 않음");
            assertFalse(now.isAfter(coupon.getValidUntil()), "쿠폰이 만료됨");
        }
    }

    @Test
    @DisplayName("findAllAvailable - 재고가 있는 쿠폰만 포함")
    void testFindAllAvailable_HasRemainingQty() {
        // When
        List<Coupon> availableCoupons = couponRepository.findAllAvailable();

        // Then
        assertTrue(availableCoupons.stream().allMatch(c -> c.getRemainingQty() > 0));
    }

    // ========== 쿠폰 업데이트 ==========

    @Test
    @DisplayName("update - 쿠폰 재고 감소")
    void testUpdate_DecreaseRemaining() {
        // Given
        Optional<Coupon> coupon = couponRepository.findById(1L);
        assertTrue(coupon.isPresent());
        int originalRemaining = coupon.get().getRemainingQty();

        // When
        coupon.get().setRemainingQty(originalRemaining - 1);
        Coupon updated = couponRepository.update(coupon.get());

        // Then
        assertEquals(originalRemaining - 1, updated.getRemainingQty());
    }

    @Test
    @DisplayName("update - 쿠폰 활성 상태 변경")
    void testUpdate_ChangeActiveStatus() {
        // Given
        Optional<Coupon> coupon = couponRepository.findById(1L);
        assertTrue(coupon.isPresent());

        // When
        coupon.get().setIsActive(false);
        Coupon updated = couponRepository.update(coupon.get());

        // Then
        assertEquals(false, updated.getIsActive());
    }

    // ========== Coupon 모두 조회 ==========

    @Test
    @DisplayName("findAll - 모든 쿠폰 조회")
    void testFindAll_AllCoupons() {
        // When
        List<Coupon> allCoupons = couponRepository.findAll();

        // Then
        assertNotNull(allCoupons);
        assertTrue(allCoupons.size() >= 5);
    }

    // ========== 초기화 데이터 검증 ==========

    @Test
    @DisplayName("초기화 데이터 - 기본 쿠폰 데이터 확인")
    void testInitialData_SampleCoupons() {
        // Then
        assertTrue(couponRepository.findById(1L).isPresent());
        assertTrue(couponRepository.findById(2L).isPresent());
        assertTrue(couponRepository.findById(3L).isPresent());
        assertTrue(couponRepository.findById(4L).isPresent());
        assertTrue(couponRepository.findById(5L).isPresent());
    }

    @Test
    @DisplayName("초기화 데이터 - PERCENTAGE 할인 쿠폰")
    void testInitialData_PercentageCoupon() {
        // When
        Optional<Coupon> coupon = couponRepository.findById(1L);

        // Then
        assertTrue(coupon.isPresent());
        assertEquals("PERCENTAGE", coupon.get().getDiscountType());
        assertNotNull(coupon.get().getDiscountRate());
        assertEquals(BigDecimal.valueOf(0.10), coupon.get().getDiscountRate());
    }

    @Test
    @DisplayName("초기화 데이터 - FIXED_AMOUNT 할인 쿠폰")
    void testInitialData_FixedAmountCoupon() {
        // When
        Optional<Coupon> coupon = couponRepository.findById(2L);

        // Then
        assertTrue(coupon.isPresent());
        assertEquals("FIXED_AMOUNT", coupon.get().getDiscountType());
        assertNotNull(coupon.get().getDiscountAmount());
        assertEquals(5000L, coupon.get().getDiscountAmount());
    }

    @Test
    @DisplayName("초기화 데이터 - 만료된 쿠폰")
    void testInitialData_ExpiredCoupon() {
        // When
        Optional<Coupon> coupon = couponRepository.findById(4L);

        // Then
        assertTrue(coupon.isPresent());
        assertTrue(LocalDateTime.now().isAfter(coupon.get().getValidUntil()));
    }

    @Test
    @DisplayName("초기화 데이터 - 재고 없는 쿠폰")
    void testInitialData_SoldOutCoupon() {
        // When
        Optional<Coupon> coupon = couponRepository.findById(5L);

        // Then
        assertTrue(coupon.isPresent());
        assertEquals(0, coupon.get().getRemainingQty());
    }

    // ========== 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 쿠폰 발급 프로세스")
    void testScenario_CouponIssuanceProcess() {
        // When - 1. 발급 가능한 쿠폰 조회
        List<Coupon> available = couponRepository.findAllAvailable();
        assertTrue(!available.isEmpty());

        // When - 2. 첫 번째 쿠폰 선택
        Coupon selectedCoupon = available.get(0);
        int originalRemaining = selectedCoupon.getRemainingQty();

        // When - 3. 비관적 락으로 쿠폰 조회 (선착순 제어)
        Optional<Coupon> lockedCoupon = couponRepository.findByIdForUpdate(selectedCoupon.getCouponId());

        // Then - 4. 재고 감소
        assertTrue(lockedCoupon.isPresent());
        lockedCoupon.get().setRemainingQty(originalRemaining - 1);
        Coupon updated = couponRepository.update(lockedCoupon.get());
        assertEquals(originalRemaining - 1, updated.getRemainingQty());
    }

    @Test
    @DisplayName("사용 시나리오 - 여러 사용자의 동시 발급 시뮬레이션")
    void testScenario_MultipleCouponIssuance() {
        // When - 쿠폰 3개를 연속으로 발급
        Optional<Coupon> coupon = couponRepository.findById(2L);
        assertTrue(coupon.isPresent());
        int initialRemaining = coupon.get().getRemainingQty();

        for (int i = 0; i < 3; i++) {
            Optional<Coupon> locked = couponRepository.findByIdForUpdate(coupon.get().getCouponId());
            assertTrue(locked.isPresent());
            int currentRemaining = locked.get().getRemainingQty();
            locked.get().setRemainingQty(currentRemaining - 1);
            couponRepository.update(locked.get());
        }

        // Then - 재고 3개 감소 확인
        Optional<Coupon> final_coupon = couponRepository.findById(2L);
        assertTrue(final_coupon.isPresent());
        assertEquals(initialRemaining - 3, final_coupon.get().getRemainingQty());
    }

    @Test
    @DisplayName("사용 시나리오 - 쿠폰 종류별 할인 검증")
    void testScenario_VerifyDiscountTypes() {
        // When
        Optional<Coupon> percentageCoupon = couponRepository.findById(1L);
        Optional<Coupon> fixedAmountCoupon = couponRepository.findById(2L);

        // Then
        assertTrue(percentageCoupon.isPresent());
        assertTrue(fixedAmountCoupon.isPresent());

        Coupon pCoupon = percentageCoupon.get();
        Coupon fCoupon = fixedAmountCoupon.get();

        // PERCENTAGE 할인 검증
        assertEquals("PERCENTAGE", pCoupon.getDiscountType());
        assertNotNull(pCoupon.getDiscountRate());
        assertEquals(BigDecimal.valueOf(0.10), pCoupon.getDiscountRate());

        // FIXED_AMOUNT 할인 검증
        assertEquals("FIXED_AMOUNT", fCoupon.getDiscountType());
        assertNotNull(fCoupon.getDiscountAmount());
        assertEquals(5000L, fCoupon.getDiscountAmount());
    }

    @Test
    @DisplayName("사용 시나리오 - 발급 불가능한 쿠폰 제외")
    void testScenario_FilterUnavailableCoupons() {
        // When
        List<Coupon> available = couponRepository.findAllAvailable();
        List<Coupon> all = couponRepository.findAll();

        // Then
        assertTrue(available.size() < all.size());
        assertFalse(available.stream().anyMatch(c -> !Boolean.TRUE.equals(c.getIsActive())));
        assertFalse(available.stream().anyMatch(c -> c.getRemainingQty() == 0));
    }
}
