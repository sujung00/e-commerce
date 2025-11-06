package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CouponService - Application 계층
 *
 * 역할:
 * - 쿠폰 발급, 조회 비즈니스 로직 처리
 * - 동시성 제어 (선착순 발급)
 * - 도메인 엔티티 검증
 *
 * 특징:
 * - 비관적 락 (SELECT ... FOR UPDATE) 시뮬레이션
 *   - synchronized 블록으로 쿠폰을 잠금
 *   - 다른 요청이 락을 획득할 때까지 대기
 * - 원자적 감소: remaining_qty--, version++
 * - UNIQUE(user_id, coupon_id) 제약으로 중복 발급 방지
 *
 * 흐름 (sequence-diagrams.md 5번 기반):
 * 1. 사용자 존재 검증 (읽기 전용)
 * 2. 비관적 락 획득: SELECT ... FOR UPDATE
 * 3-5. 조건 검증: is_active, valid_from/until, remaining_qty
 * 6. 원자적 감소: remaining_qty--, version++
 * 7. UNIQUE 검증: UNIQUE(user_id, coupon_id)
 * 8. 발급 기록 저장: INSERT user_coupons
 */
@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    public CouponService(CouponRepository couponRepository,
                         UserCouponRepository userCouponRepository,
                         UserRepository userRepository) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
    }

    /**
     * 4.1 쿠폰 발급 (선착순)
     * POST /coupons/issue
     *
     * sequence-diagrams.md 5번 시퀀스 기반 구현
     *
     * 비즈니스 로직 (비관적 락 기반):
     * 1. 사용자 존재 검증 (읽기 전용)
     * 2. 비관적 락 획득: SELECT coupons WHERE coupon_id=? FOR UPDATE
     *    - 다른 요청 차단
     *    - synchronized 블록으로 시뮬레이션
     * 3. 쿠폰 활성화 상태 검증 (is_active=true)
     * 4. 유효 기간 검증 (valid_from <= NOW <= valid_until)
     * 5. 재고 검증 (remaining_qty > 0)
     * 6. 원자적 감소: UPDATE coupons SET remaining_qty--, version++
     * 7. 중복 발급 방지: UNIQUE(user_id, coupon_id) 검증
     * 8. 발급 기록 저장: INSERT user_coupons
     *
     * @param userId 사용자 ID
     * @param couponId 발급받을 쿠폰 ID
     * @return 발급된 쿠폰 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     * @throws CouponNotFoundException 쿠폰을 찾을 수 없음
     * @throws IllegalArgumentException 발급 불가 사유 (유효기간, 재고, 중복)
     */
    public IssueCouponResponse issueCoupon(Long userId, Long couponId) {
        // === 1단계: 검증 (읽기 전용) ===

        // 1. 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // === 2단계: 비관적 락 획득 ===
        // SELECT coupons WHERE coupon_id=? FOR UPDATE (InMemory: synchronized)
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        // synchronized 블록: 다른 요청 차단 (FOR UPDATE 시뮬레이션)
        synchronized (coupon) {
            // === 3-5단계: 조건 검증 (락 획득 후) ===

            // 3. 쿠폰 활성화 상태 검증
            if (!Boolean.TRUE.equals(coupon.getIsActive())) {
                throw new IllegalArgumentException("쿠폰이 비활성화되어 있습니다");
            }

            // 4. 유효 기간 검증
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(coupon.getValidFrom())) {
                throw new IllegalArgumentException("쿠폰이 유효기간을 벗어났습니다");
            }
            if (now.isAfter(coupon.getValidUntil())) {
                throw new IllegalArgumentException("쿠폰이 유효기간을 벗어났습니다");
            }

            // 5. 재고 검증 (remaining_qty > 0)
            if (coupon.getRemainingQty() <= 0) {
                throw new IllegalArgumentException("쿠폰이 모두 소진되었습니다");
            }

            // === 6단계: 원자적 감소 (UPDATE) ===
            // UPDATE coupons SET remaining_qty--, version++ WHERE coupon_id=?
            coupon.setRemainingQty(coupon.getRemainingQty() - 1);
            coupon.setVersion(coupon.getVersion() + 1);
            coupon.setUpdatedAt(LocalDateTime.now());
            couponRepository.update(coupon);

            // === 7단계: UNIQUE 검증 ===
            // UNIQUE(user_id, coupon_id) 제약 확인
            boolean alreadyIssued = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
                    .isPresent();
            if (alreadyIssued) {
                throw new IllegalArgumentException("이 쿠폰은 이미 발급받으셨습니다");
            }

            // === 8단계: 발급 기록 저장 ===
            // INSERT user_coupons(user_id, coupon_id, status='ACTIVE', issued_at=NOW())
            UserCoupon userCoupon = UserCoupon.builder()
                    .userId(userId)
                    .couponId(couponId)
                    .status("ACTIVE")
                    .issuedAt(LocalDateTime.now())
                    .usedAt(null)
                    .orderId(null)
                    .build();
            UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

            System.out.println("[CouponService] 쿠폰 발급 완료: userId=" + userId +
                    ", couponId=" + couponId +
                    ", userCouponId=" + savedUserCoupon.getUserCouponId() +
                    ", remaining_qty=" + coupon.getRemainingQty() +
                    ", version=" + coupon.getVersion());

            return IssueCouponResponse.from(savedUserCoupon, coupon);
        }
        // synchronized 블록 종료: 락 해제
    }

    /**
     * 4.2 사용자가 보유한 쿠폰 조회
     * GET /coupons/issued?status=ACTIVE
     *
     * 비즈니스 로직:
     * 1. 사용자 존재 검증
     * 2. status별 필터링 (기본값: ACTIVE)
     * 3. 사용자의 쿠폰 목록 조회
     * 4. UserCoupon + Coupon 정보 결합하여 Response 생성
     *
     * @param userId 사용자 ID
     * @param status 쿠폰 상태 (ACTIVE | USED | EXPIRED)
     * @return 사용자 쿠폰 목록
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     */
    public List<UserCouponResponse> getUserCoupons(Long userId, String status) {
        // 1. 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // 기본값 설정
        String filterStatus = status != null && !status.isEmpty() ? status : "ACTIVE";

        // 2-3. status별 필터링하여 조회
        List<UserCoupon> userCoupons = userCouponRepository.findByUserIdAndStatus(userId, filterStatus);

        // 4. UserCoupon + Coupon 정보 결합
        return userCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponRepository.findById(uc.getCouponId())
                            .orElseThrow(() -> new CouponNotFoundException(uc.getCouponId()));
                    return UserCouponResponse.from(uc, coupon);
                })
                .collect(Collectors.toList());
    }

    /**
     * 4.3 사용 가능한 쿠폰 조회
     * GET /coupons
     *
     * 비즈니스 로직:
     * 1. 발급 가능한 쿠폰 조회 (is_active=true, 유효기간 내, remaining_qty > 0)
     * 2. Response로 변환
     *
     * 필터링 조건:
     * - is_active = true
     * - valid_from <= NOW <= valid_until
     * - remaining_qty > 0
     *
     * @return 발급 가능한 쿠폰 목록
     */
    public List<AvailableCouponResponse> getAvailableCoupons() {
        // 1. 발급 가능한 쿠폰 조회
        List<Coupon> availableCoupons = couponRepository.findAllAvailable();

        // 2. Response로 변환
        return availableCoupons.stream()
                .map(AvailableCouponResponse::from)
                .collect(Collectors.toList());
    }
}
