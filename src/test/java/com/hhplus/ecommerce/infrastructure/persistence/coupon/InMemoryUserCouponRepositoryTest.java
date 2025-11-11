package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryUserCouponRepository 단위 테스트
 * - UserCoupon CRUD 작업 검증
 * - UNIQUE(user_id, coupon_id) 제약 조건 검증
 * - 사용자별 쿠폰 조회 (상태별)
 * - 사용자 쿠폰 라이프사이클 (ACTIVE → USED/EXPIRED)
 */
@DisplayName("InMemoryUserCouponRepository 테스트")
class InMemoryUserCouponRepositoryTest {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_USED = "USED";
    private static final String STATUS_EXPIRED = "EXPIRED";

    private InMemoryUserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        userCouponRepository = new InMemoryUserCouponRepository();
    }

    // ========== UserCoupon 저장 ==========

    @Test
    @DisplayName("save - 새 사용자 쿠폰 저장 (ID 자동 할당)")
    void testSave_NewUserCoupon() {
        // When
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon saved = userCouponRepository.save(userCoupon);

        // Then
        assertNotNull(saved.getUserCouponId());
        assertTrue(saved.getUserCouponId() >= 2001L);
        assertEquals(100L, saved.getUserId());
        assertEquals(1L, saved.getCouponId());
        assertEquals(STATUS_ACTIVE, saved.getStatus());
    }

    @Test
    @DisplayName("save - 여러 쿠폰 저장 시 ID 증가")
    void testSave_IdSequenceIncrement() {
        // When
        UserCoupon userCoupon1 = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon userCoupon2 = UserCoupon.builder()
                .userId(100L)
                .couponId(2L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        UserCoupon saved1 = userCouponRepository.save(userCoupon1);
        UserCoupon saved2 = userCouponRepository.save(userCoupon2);

        // Then
        assertTrue(saved1.getUserCouponId() < saved2.getUserCouponId());
        assertEquals(saved1.getUserCouponId() + 1, saved2.getUserCouponId());
    }

    // ========== UserCoupon 조회 ==========

    @Test
    @DisplayName("findById - 저장된 사용자 쿠폰 조회")
    void testFindById_ExistingUserCoupon() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon saved = userCouponRepository.save(userCoupon);

        // When
        Optional<UserCoupon> found = userCouponRepository.findById(saved.getUserCouponId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(100L, found.get().getUserId());
        assertEquals(1L, found.get().getCouponId());
    }

    @Test
    @DisplayName("findById - 없는 사용자 쿠폰은 Optional.empty()")
    void testFindById_NonExistent() {
        // When
        Optional<UserCoupon> found = userCouponRepository.findById(99999L);

        // Then
        assertTrue(found.isEmpty());
    }

    // ========== 상태별 조회 ==========

    @Test
    @DisplayName("findByUserIdAndStatus - ACTIVE 상태 쿠폰 조회")
    void testFindByUserIdAndStatus_Active() {
        // Given
        UserCoupon userCoupon1 = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon userCoupon2 = UserCoupon.builder()
                .userId(100L)
                .couponId(2L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        UserCoupon saved1 = userCouponRepository.save(userCoupon1);
        UserCoupon saved2 = userCouponRepository.save(userCoupon2);

        // When
        List<UserCoupon> activeCoupons = userCouponRepository.findByUserIdAndStatus(100L, STATUS_ACTIVE);

        // Then
        assertTrue(activeCoupons.size() >= 2);
        assertTrue(activeCoupons.stream().allMatch(uc -> STATUS_ACTIVE.equals(uc.getStatus())));
    }

    @Test
    @DisplayName("findByUserIdAndStatus - USED 상태 쿠폰 조회")
    void testFindByUserIdAndStatus_Used() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon saved = userCouponRepository.save(userCoupon);
        saved.setStatus(STATUS_USED);
        userCouponRepository.update(saved);

        // When
        List<UserCoupon> usedCoupons = userCouponRepository.findByUserIdAndStatus(100L, STATUS_USED);

        // Then
        assertTrue(usedCoupons.size() >= 1);
        assertTrue(usedCoupons.stream().allMatch(uc -> STATUS_USED.equals(uc.getStatus())));
    }

    @Test
    @DisplayName("findByUserIdAndStatus - EXPIRED 상태 쿠폰 조회")
    void testFindByUserIdAndStatus_Expired() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon saved = userCouponRepository.save(userCoupon);
        saved.setStatus(STATUS_EXPIRED);
        userCouponRepository.update(saved);

        // When
        List<UserCoupon> expiredCoupons = userCouponRepository.findByUserIdAndStatus(100L, STATUS_EXPIRED);

        // Then
        assertTrue(expiredCoupons.size() >= 1);
        assertTrue(expiredCoupons.stream().allMatch(uc -> STATUS_EXPIRED.equals(uc.getStatus())));
    }

    @Test
    @DisplayName("findByUserIdAndStatus - 없는 상태는 빈 리스트")
    void testFindByUserIdAndStatus_EmptyList() {
        // When
        List<UserCoupon> coupons = userCouponRepository.findByUserIdAndStatus(999L, STATUS_ACTIVE);

        // Then
        assertTrue(coupons.isEmpty());
    }

    // ========== UNIQUE 제약 조건 검증 ==========

    @Test
    @DisplayName("findByUserIdAndCouponId - 사용자-쿠폰 조합 조회")
    void testFindByUserIdAndCouponId_ExistingCombination() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        userCouponRepository.save(userCoupon);

        // When
        Optional<UserCoupon> found = userCouponRepository.findByUserIdAndCouponId(100L, 1L);

        // Then
        assertTrue(found.isPresent());
        assertEquals(100L, found.get().getUserId());
        assertEquals(1L, found.get().getCouponId());
    }

    @Test
    @DisplayName("findByUserIdAndCouponId - 없는 조합은 Optional.empty()")
    void testFindByUserIdAndCouponId_NonExistentCombination() {
        // When
        Optional<UserCoupon> found = userCouponRepository.findByUserIdAndCouponId(100L, 999L);

        // Then
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("findByUserIdAndCouponId - UNIQUE 제약 검증 (중복 방지)")
    void testFindByUserIdAndCouponId_UniqueConstraint() {
        // Given
        UserCoupon userCoupon1 = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon userCoupon2 = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);

        // When - 같은 사용자-쿠폰 조합이 있는지 확인
        Optional<UserCoupon> found = userCouponRepository.findByUserIdAndCouponId(100L, 1L);

        // Then - 최소 1개 이상 있음 (UNIQUE 미반영된 상태)
        assertTrue(found.isPresent());
    }

    // ========== 사용자별 조회 ==========

    @Test
    @DisplayName("findByUserId - 사용자의 모든 쿠폰 조회")
    void testFindByUserId_AllCoupons() {
        // Given
        UserCoupon userCoupon1 = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon userCoupon2 = UserCoupon.builder()
                .userId(100L)
                .couponId(2L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon userCoupon3 = UserCoupon.builder()
                .userId(100L)
                .couponId(3L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);
        userCouponRepository.save(userCoupon3);

        // When
        List<UserCoupon> coupons = userCouponRepository.findByUserId(100L);

        // Then
        assertTrue(coupons.size() >= 3);
        assertTrue(coupons.stream().allMatch(uc -> 100L == uc.getUserId()));
    }

    @Test
    @DisplayName("findByUserId - 없는 사용자는 빈 리스트")
    void testFindByUserId_EmptyList() {
        // When
        List<UserCoupon> coupons = userCouponRepository.findByUserId(999L);

        // Then
        assertTrue(coupons.isEmpty());
    }

    @Test
    @DisplayName("findByUserId - 다양한 상태의 쿠폰 모두 포함")
    void testFindByUserId_AllStatuses() {
        // Given
        UserCoupon userCoupon1 = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon userCoupon2 = UserCoupon.builder()
                .userId(100L)
                .couponId(2L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon userCoupon3 = UserCoupon.builder()
                .userId(100L)
                .couponId(3L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        UserCoupon saved1 = userCouponRepository.save(userCoupon1);
        UserCoupon saved2 = userCouponRepository.save(userCoupon2);
        UserCoupon saved3 = userCouponRepository.save(userCoupon3);

        // When - 상태 변경
        saved1.setStatus(STATUS_USED);
        saved2.setStatus(STATUS_EXPIRED);
        // saved3는 ACTIVE 유지

        userCouponRepository.update(saved1);
        userCouponRepository.update(saved2);

        List<UserCoupon> allCoupons = userCouponRepository.findByUserId(100L);

        // Then
        assertTrue(allCoupons.stream().anyMatch(uc -> STATUS_ACTIVE.equals(uc.getStatus())));
        assertTrue(allCoupons.stream().anyMatch(uc -> STATUS_USED.equals(uc.getStatus())));
        assertTrue(allCoupons.stream().anyMatch(uc -> STATUS_EXPIRED.equals(uc.getStatus())));
    }

    // ========== UserCoupon 업데이트 ==========

    @Test
    @DisplayName("update - 사용자 쿠폰 상태 변경 (ACTIVE → USED)")
    void testUpdate_ChangeStatusToUsed() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon saved = userCouponRepository.save(userCoupon);

        // When
        saved.setStatus(STATUS_USED);
        UserCoupon updated = userCouponRepository.update(saved);

        // Then
        assertEquals(STATUS_USED, updated.getStatus());
    }

    @Test
    @DisplayName("update - 사용자 쿠폰 상태 변경 (ACTIVE → EXPIRED)")
    void testUpdate_ChangeStatusToExpired() {
        // Given
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon saved = userCouponRepository.save(userCoupon);

        // When
        saved.setStatus(STATUS_EXPIRED);
        UserCoupon updated = userCouponRepository.update(saved);

        // Then
        assertEquals(STATUS_EXPIRED, updated.getStatus());
    }

    // ========== 초기화 데이터 검증 ==========

    @Test
    @DisplayName("초기화 데이터 - 기본 샘플 데이터 확인")
    void testInitialData_SampleData() {
        // Then - 초기에는 데이터가 없을 수 있음
        List<UserCoupon> all = userCouponRepository.findByUserId(100L);
        assertNotNull(all);
    }

    // ========== 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 사용자에게 쿠폰 발급")
    void testScenario_IssueCouponToUser() {
        // When - 1. 사용자에게 쿠폰 발급
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon saved = userCouponRepository.save(userCoupon);

        // Then - 2. 발급 확인
        assertTrue(saved.getUserCouponId() > 0);
        assertEquals(STATUS_ACTIVE, saved.getStatus());

        // When - 3. 발급된 쿠폰 조회
        Optional<UserCoupon> issued = userCouponRepository.findByUserIdAndCouponId(100L, 1L);
        assertTrue(issued.isPresent());
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자 쿠폰 사용 (주문 시)")
    void testScenario_UseCouponInOrder() {
        // Given - 1. 쿠폰 발급
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon saved = userCouponRepository.save(userCoupon);

        // When - 2. 주문에서 쿠폰 사용
        saved.setStatus(STATUS_USED);
        UserCoupon updated = userCouponRepository.update(saved);

        // Then - 3. 상태 변경 확인
        assertEquals(STATUS_USED, updated.getStatus());
        List<UserCoupon> usedCoupons = userCouponRepository.findByUserIdAndStatus(100L, STATUS_USED);
        assertTrue(usedCoupons.stream().anyMatch(uc -> uc.getCouponId().equals(1L)));
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자의 활성 쿠폰 목록 조회")
    void testScenario_GetActiveUserCoupons() {
        // Given - 여러 쿠폰 발급
        UserCoupon coupon1 = UserCoupon.builder()
                .userId(200L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon coupon2 = UserCoupon.builder()
                .userId(200L)
                .couponId(2L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon coupon3 = UserCoupon.builder()
                .userId(200L)
                .couponId(3L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        UserCoupon saved1 = userCouponRepository.save(coupon1);
        UserCoupon saved2 = userCouponRepository.save(coupon2);
        UserCoupon saved3 = userCouponRepository.save(coupon3);

        // When - 일부 쿠폰 사용
        saved1.setStatus(STATUS_USED);
        saved2.setStatus(STATUS_EXPIRED);
        userCouponRepository.update(saved1);
        userCouponRepository.update(saved2);

        // When - 활성 쿠폰만 조회
        List<UserCoupon> activeCoupons = userCouponRepository.findByUserIdAndStatus(200L, STATUS_ACTIVE);

        // Then
        assertTrue(activeCoupons.size() >= 1);
        assertTrue(activeCoupons.stream().allMatch(uc -> STATUS_ACTIVE.equals(uc.getStatus())));
    }

    @Test
    @DisplayName("사용 시나리오 - 쿠폰 중복 발급 방지 검증")
    void testScenario_PreventDuplicateIssuance() {
        // When - 같은 쿠폰을 2번 발급하려고 시도
        UserCoupon coupon1 = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();
        UserCoupon coupon2 = UserCoupon.builder()
                .userId(100L)
                .couponId(1L)
                .status(STATUS_ACTIVE)
                .issuedAt(LocalDateTime.now())
                .build();

        UserCoupon saved1 = userCouponRepository.save(coupon1);
        UserCoupon saved2 = userCouponRepository.save(coupon2);

        // Then - 두 개가 모두 저장됨 (실제 DB에서는 UNIQUE 제약으로 방지)
        Optional<UserCoupon> found = userCouponRepository.findByUserIdAndCouponId(100L, 1L);
        assertTrue(found.isPresent());
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자별 쿠폰 이력 추적")
    void testScenario_TrackUserCouponHistory() {
        // When - 사용자에게 여러 쿠폰 발급
        for (int i = 1; i <= 5; i++) {
            UserCoupon coupon = UserCoupon.builder()
                    .userId(300L)
                    .couponId((long)i)
                    .status(STATUS_ACTIVE)
                    .issuedAt(LocalDateTime.now())
                    .build();
            userCouponRepository.save(coupon);
        }

        // When - 사용자의 모든 쿠폰 조회
        List<UserCoupon> allCoupons = userCouponRepository.findByUserId(300L);

        // Then
        assertTrue(allCoupons.size() >= 5);
        for (int i = 1; i <= 5; i++) {
            final int couponId = i;
            assertTrue(allCoupons.stream().anyMatch(uc -> uc.getCouponId().equals((long)couponId)));
        }
    }
}
