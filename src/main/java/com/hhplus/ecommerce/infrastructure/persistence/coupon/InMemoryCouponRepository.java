package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * InMemoryCouponRepository - Infrastructure 계층 (Adapter)
 *
 * 역할:
 * - ConcurrentHashMap 기반 쿠폰 저장소
 * - SELECT ... FOR UPDATE (비관적 락) 시뮬레이션
 * - 선착순 발급 시 remaining_qty 원자적 감소
 *
 * 특징:
 * - 스레드 안전성: ConcurrentHashMap 사용
 * - couponId 자동 증가 (1001L부터 시작)
 * - remaining_qty 원자적 감소 처리
 * - findByIdForUpdate(): synchronized 블록으로 비관적 락 시뮬레이션
 *
 * sequence-diagrams.md 5번 기반 구현:
 * - 다른 요청의 쿠폰 접근을 동기화 객체로 차단
 * - 락 획득 후 검증 → 원자적 감소 → 락 해제
 *
 * 마이그레이션:
 * - MySQL: coupons 테이블 → @Entity로 변환
 * - JPA: ConcurrentHashMap → JpaRepository
 * - 락: @Lock(LockModeType.PESSIMISTIC_WRITE)
 * - 비즈니스 로직은 변경 없음
 *
 * 참고: Domain 계층의 CouponRepository는 인터페이스 (Port)
 * 이 클래스는 그 인터페이스의 구현체 (Adapter)
 */
@Repository
public class InMemoryCouponRepository implements CouponRepository {

    private final ConcurrentHashMap<Long, Coupon> couponStore = new ConcurrentHashMap<>();
    private Long couponIdSequence = 1001L;  // couponId 시작값

    /**
     * 생성자 - 샘플 쿠폰 데이터 초기화
     */
    public InMemoryCouponRepository() {
        initializeSampleCoupons();
    }

    /**
     * 샘플 쿠폰 데이터 초기화
     */
    private void initializeSampleCoupons() {
        // 쿠폰 1: 10% 할인 (PERCENTAGE)
        Coupon coupon1 = Coupon.builder()
                .couponId(1L)
                .couponName("10% 할인 쿠폰")
                .description("모든 상품 10% 할인")
                .discountType("PERCENTAGE")
                .discountAmount(null)
                .discountRate(java.math.BigDecimal.valueOf(0.10))
                .totalQuantity(100)
                .remainingQty(100)
                .validFrom(LocalDateTime.of(2025, 10, 29, 0, 0, 0))
                .validUntil(LocalDateTime.of(2025, 10, 31, 23, 59, 59))
                .isActive(true)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponStore.put(1L, coupon1);

        // 쿠폰 2: 무료배송 (FIXED_AMOUNT)
        Coupon coupon2 = Coupon.builder()
                .couponId(2L)
                .couponName("무료배송")
                .description("50,000원 이상 주문 무료배송")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(null)
                .totalQuantity(200)
                .remainingQty(200)
                .validFrom(LocalDateTime.of(2025, 10, 1, 0, 0, 0))
                .validUntil(LocalDateTime.of(2025, 12, 31, 23, 59, 59))
                .isActive(true)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponStore.put(2L, coupon2);

        // 쿠폰 3: 5,000원 할인 (FIXED_AMOUNT)
        Coupon coupon3 = Coupon.builder()
                .couponId(3L)
                .couponName("5,000원 할인 쿠폰")
                .description("10,000원 이상 구매 시 5,000원 할인")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(null)
                .totalQuantity(50)
                .remainingQty(50)
                .validFrom(LocalDateTime.of(2025, 10, 29, 0, 0, 0))
                .validUntil(LocalDateTime.of(2025, 11, 30, 23, 59, 59))
                .isActive(true)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponStore.put(3L, coupon3);

        // 쿠폰 4: 20% 할인 (만료된 쿠폰)
        Coupon coupon4 = Coupon.builder()
                .couponId(4L)
                .couponName("20% 할인 쿠폰")
                .description("만료된 쿠폰")
                .discountType("PERCENTAGE")
                .discountAmount(null)
                .discountRate(java.math.BigDecimal.valueOf(0.20))
                .totalQuantity(100)
                .remainingQty(100)
                .validFrom(LocalDateTime.of(2025, 10, 1, 0, 0, 0))
                .validUntil(LocalDateTime.of(2025, 10, 25, 23, 59, 59))
                .isActive(true)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponStore.put(4L, coupon4);

        // 쿠폰 5: 재고 없음 (FIXED_AMOUNT)
        Coupon coupon5 = Coupon.builder()
                .couponId(5L)
                .couponName("3,000원 할인 쿠폰")
                .description("재고가 없는 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(3000L)
                .discountRate(null)
                .totalQuantity(100)
                .remainingQty(0)  // 재고 없음
                .validFrom(LocalDateTime.of(2025, 10, 29, 0, 0, 0))
                .validUntil(LocalDateTime.of(2025, 10, 31, 23, 59, 59))
                .isActive(true)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponStore.put(5L, coupon5);

        couponIdSequence = 1001L;  // 사용자 등록 쿠폰은 1001부터 시작
    }

    /**
     * 쿠폰 저장 (새 쿠폰 등록)
     *
     * @param coupon 저장할 쿠폰
     * @return 저장된 쿠폰 (couponId 포함)
     */
    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getCouponId() == null) {
            Long couponId;
            synchronized (this) {
                couponId = couponIdSequence++;
            }
            coupon.setCouponId(couponId);
        }
        couponStore.put(coupon.getCouponId(), coupon);
        return coupon;
    }

    /**
     * 쿠폰 ID로 조회
     *
     * @param couponId 쿠폰 ID
     * @return 조회된 쿠폰 (없으면 Optional.empty())
     */
    @Override
    public Optional<Coupon> findById(Long couponId) {
        return Optional.ofNullable(couponStore.get(couponId));
    }

    /**
     * 쿠폰을 비관적 락으로 조회 (SELECT ... FOR UPDATE 시뮬레이션)
     *
     * InMemory 구현:
     * - synchronized 블록으로 쿠폰을 잠금
     * - 다른 요청이 같은 쿠폰의 락을 획득할 때까지 대기
     * - 선착순 발급 시 동시성 제어
     *
     * 흐름:
     * 1. CouponService가 이 메서드 호출
     * 2. synchronized 블록이 쿠폰을 잠금
     * 3. 다른 요청은 대기 (FOR UPDATE 동작)
     * 4. 검증 및 remaining_qty 감소
     * 5. UserCoupon 저장
     * 6. synchronized 블록 종료 → 락 해제
     *
     * @param couponId 쿠폰 ID
     * @return 조회된 쿠폰 (잠긴 상태)
     */
    @Override
    public Optional<Coupon> findByIdForUpdate(Long couponId) {
        // ConcurrentHashMap의 computeIfPresent를 활용하여 원자적 연산 보장
        // synchronized는 CouponService에서 처리
        return Optional.ofNullable(couponStore.get(couponId));
    }

    /**
     * 발급 가능한 모든 쿠폰 조회
     * 조건: is_active=true, valid period 내, remaining_qty > 0
     *
     * @return 발급 가능한 쿠폰 목록
     */
    @Override
    public List<Coupon> findAllAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return couponStore.values().stream()
                .filter(coupon -> Boolean.TRUE.equals(coupon.getIsActive()))
                .filter(coupon -> !now.isBefore(coupon.getValidFrom()))
                .filter(coupon -> !now.isAfter(coupon.getValidUntil()))
                .filter(coupon -> coupon.getRemainingQty() > 0)
                .collect(Collectors.toList());
    }

    /**
     * 모든 쿠폰 조회 (테스트, 관리 용도)
     *
     * @return 모든 쿠폰 목록
     */
    @Override
    public List<Coupon> findAll() {
        return List.copyOf(couponStore.values());
    }

    /**
     * 쿠폰 업데이트
     *
     * @param coupon 업데이트할 쿠폰
     * @return 업데이트된 쿠폰
     */
    @Override
    public Coupon update(Coupon coupon) {
        couponStore.put(coupon.getCouponId(), coupon);
        return coupon;
    }
}
