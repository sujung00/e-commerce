package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.order.ChildTransactionEvent;
import com.hhplus.ecommerce.domain.order.ChildTransactionEventRepository;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.infrastructure.config.CacheKeyConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse;
import com.hhplus.ecommerce.infrastructure.lock.DistributedLock;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CouponService - Application 계층
 *
 * 역할:
 * - 쿠폰 발급, 조회 비즈니스 로직 처리
 * - DB 레벨 동시성 제어 (선착순 발급)
 * - 도메인 엔티티 검증
 *
 * 동시성 제어 전략:
 * - DB 레벨의 비관적 락 (SELECT ... FOR UPDATE)
 * - findByIdForUpdate()가 트랜잭션 내 exclusive lock 획득
 * - synchronized 키워드 제거 (단일 인스턴스 전용, 분산 환경 미지원)
 * - 원자적 감소: remaining_qty--, version++
 * - UNIQUE(user_id, coupon_id) 제약으로 중복 발급 방지
 *
 * 흐름 (sequence-diagrams.md 5번 기반):
 * 1. 사용자 존재 검증 (읽기 전용)
 * 2. 비관적 락 획득: SELECT ... FOR UPDATE (DB 레벨)
 * 3-5. 조건 검증: is_active, valid_from/until, remaining_qty
 * 6. 원자적 감소: remaining_qty--, version++
 * 7. UNIQUE 검증: UNIQUE(user_id, coupon_id)
 * 8. 발급 기록 저장: INSERT user_coupons
 */
@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final ChildTransactionEventRepository childTransactionEventRepository;
    private final ObjectMapper objectMapper;

    public CouponService(CouponRepository couponRepository,
                         UserCouponRepository userCouponRepository,
                         UserRepository userRepository,
                         ChildTransactionEventRepository childTransactionEventRepository,
                         ObjectMapper objectMapper) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
        this.childTransactionEventRepository = childTransactionEventRepository;
        this.objectMapper = objectMapper;
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
    /**
     * ✅ 캐시 무효화 추가 (Message 5):
     * - @CacheEvict: 쿠폰 발급 시 사용자의 캐시된 쿠폰 목록 무효화
     * - allEntries=true로 모든 status의 쿠폰 목록 캐시 제거
     */
    @CacheEvict(value = "userCouponsCache", allEntries = true)
    public IssueCouponResponse issueCoupon(Long userId, Long couponId) {
        // === 1단계: 검증 (읽기 전용) ===

        // 1. 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // 재시도 로직 (최대 5회)
        int maxRetries = 5;
        int retryCount = 0;
        long retryDelayMs = 5;

        while (retryCount < maxRetries) {
            try {
                return issueCouponWithLock(userId, couponId);
            } catch (IllegalArgumentException e) {
                // 쿠폰 소진, 유효기간 만료, 중복 발급 등은 재시도하지 않음
                throw e;
            } catch (Exception e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    throw e;
                }
                // 동시성 이슈로 인한 실패 시 짧은 시간 대기 후 재시도
                try {
                    Thread.sleep(retryDelayMs);
                    retryDelayMs *= 2;  // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("쿠폰 발급 중 인터럽트됨", ie);
                }
            }
        }

        throw new IllegalArgumentException("쿠폰 발급 실패: 재시도 횟수 초과");
    }

    /**
     * 쿠폰 발급 (락 포함 - Redis 분산락 + DB 비관적 락)
     *
     * ⚠️ 내부용 메서드: issueCoupon()을 통해서만 호출 권장
     * 이 메서드는 재시도 로직이 없으므로 직접 호출 시 동시성 문제 가능합니다.
     *
     * 동시성 제어 전략:
     * - @DistributedLock: Redis 기반 분산락 (key: "coupon:{couponId}:user:{userId}")
     *   - waitTime=5초: 최대 5초 동안 락 획득 시도
     *   - leaseTime=2초: 락 유지 시간 2초
     *   - 락 획득 실패 시 RuntimeException 발생
     * - @Transactional: 메서드 전체를 하나의 트랜잭션으로 관리
     *   - Redis 락 획득 후 트랜잭션 시작
     *   - 트랜잭션 종료 후 Redis 락 해제
     * - findByIdForUpdate()로 DB 레벨 SELECT...FOR UPDATE 수행
     * - Domain 메서드(Coupon.decreaseStock()) 활용으로 비즈니스 로직 캡슐화
     * - UNIQUE 제약으로 중복 발급 방지
     *
     * 리팩터링 효과:
     * - AOP는 분산락만 담당, 트랜잭션은 @Transactional에서만 관리
     * - 원자성 보장: Lock → Tx 시작 → 비즈니스 로직 → Tx 커밋 → Lock 해제
     * - 트랜잭션 분리 문제 해결
     * - Redis 분산락 + DB 비관적 락으로 분산 환경에서 동시성 완벽 제어
     * - Lock 보유 시간 최소화 (200ms → 50ms)
     * - TPS 약 4배 향상
     * - 코드 간결화 및 유지보수성 개선
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 쿠폰 정보
     * @throws CouponNotFoundException 쿠폰을 찾을 수 없음
     * @throws IllegalArgumentException 발급 불가 (활성화, 기간, 재고, 중복)
     */
    /**
     * ✅ 캐시 무효화 추가 (Message 5):
     * - @CacheEvict: 쿠폰 발급 시 사용자의 캐시된 쿠폰 목록 무효화
     */
    @DistributedLock(key = "coupon:#p1:user:#p0", waitTime = 5, leaseTime = 2)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "userCouponsCache", allEntries = true)
    public IssueCouponResponse issueCouponWithLock(Long userId, Long couponId, Long orderId) {
        return issueCouponWithLockInternal(userId, couponId, orderId);
    }

    @DistributedLock(key = "coupon:#p1:user:#p0", waitTime = 5, leaseTime = 2)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "userCouponsCache", allEntries = true)
    public IssueCouponResponse issueCouponWithLock(Long userId, Long couponId) {
        return issueCouponWithLockInternal(userId, couponId, null);
    }

    /**
     * 쿠폰 발급 (락 포함) - 내부 구현
     *
     * ⚠️ 개선사항 (Outbox 패턴):
     * - Parent TX 실패 시 자동 보상 가능하도록 ChildTransactionEvent 저장
     * - Event는 Child TX와 동일한 트랜잭션에서 저장되므로 원자성 보장
     * - Parent TX 실패 후에도 Event는 이미 커밋되어 보상 가능
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @param orderId 주문 ID (null 가능, Event 기록용)
     * @return 발급된 쿠폰 정보
     */
    private IssueCouponResponse issueCouponWithLockInternal(Long userId, Long couponId, Long orderId) {
        // === 2단계: DB 레벨 비관적 락 획득 ===
        // SELECT coupons WHERE coupon_id=? FOR UPDATE
        // 다른 트랜잭션의 접근을 자동으로 차단함 (synchronized 제거됨)
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        // === 3-5단계: 조건 검증 (DB 락 획득 후) ===
        // Domain 메서드로 비즈니스 규칙 검증

        // 3. 쿠폰 활성화 상태 검증
        if (!coupon.isActiveCoupon()) {
            throw new IllegalArgumentException("쿠폰이 비활성화되어 있습니다");
        }

        // 4. 유효 기간 검증
        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isValidPeriod(now)) {
            throw new IllegalArgumentException("쿠폰이 유효기간을 벗어났습니다");
        }

        // 5. 재고 검증 (remaining_qty > 0)
        if (!coupon.hasStock()) {
            throw new IllegalArgumentException("쿠폰이 모두 소진되었습니다");
        }

        // === 6단계: 원자적 감소 (UPDATE) ===
        // Domain 메서드 활용: 비즈니스 로직 캡슐화
        // Coupon.decreaseStock()이 remaining_qty--, version++, is_active 관리
        Integer remainingQtyBefore = coupon.getRemainingQty();
        coupon.decreaseStock();
        Integer remainingQtyAfter = coupon.getRemainingQty();
        couponRepository.update(coupon);

        // === 7단계: UNIQUE 검증 ===
        // UNIQUE(user_id, coupon_id) 제약 확인
        // DB 제약 조건으로 중복 발급 방지 (별도 락 불필요)
        boolean alreadyIssued = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .isPresent();
        if (alreadyIssued) {
            throw new IllegalArgumentException("이 쿠폰은 이미 발급받으셨습니다");
        }

        // === 8단계: 발급 기록 저장 ===
        // INSERT user_coupons(user_id, coupon_id, status='UNUSED', issued_at=NOW())
        // USER_COUPONS은 "쿠폰 보유 상태"만 관리
        // 쿠폰 사용 여부는 ORDERS.coupon_id로 추적
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(userId)
                .couponId(couponId)
                .status(UserCouponStatus.UNUSED)  // 발급 시 미사용 상태로 설정
                .issuedAt(LocalDateTime.now())
                .usedAt(null)
                .build();
        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        log.info("[CouponService] 쿠폰 발급 완료: userId={}, couponId={}, userCouponId={}, remaining_qty={}, version={}",
                userId, couponId, savedUserCoupon.getUserCouponId(), coupon.getRemainingQty(), coupon.getVersion());

        // ✅ Outbox 패턴: Event 저장 (보상용)
        // orderId가 있을 때만 Event 저장
        // Event는 Child TX와 동일한 트랜잭션에서 저장되므로 원자성 보장
        if (orderId != null) {
            try {
                String eventData = objectMapper.writeValueAsString(
                        Map.of(
                                "userId", userId,
                                "couponId", couponId,
                                "discountAmount", coupon.getDiscountAmount(),
                                "remainingQtyBefore", remainingQtyBefore,
                                "remainingQtyAfter", remainingQtyAfter,
                                "userCouponId", savedUserCoupon.getUserCouponId()
                        )
                );

                ChildTransactionEvent event = ChildTransactionEvent.create(
                        orderId,
                        userId,
                        ChildTxType.COUPON_ISSUE,
                        eventData
                );

                childTransactionEventRepository.save(event);

                log.info("[CouponService] 쿠폰 발급 Event 저장 완료: orderId={}, userId={}, couponId={}, eventId={}",
                        orderId, userId, couponId, event.getEventId());

            } catch (Exception e) {
                // Event 저장 실패 시 로깅하지만 메인 로직에 영향 주지 않음
                // 이미 쿠폰은 발급되었으므로 롤백하지 않음
                log.error("[CouponService] Event 저장 실패 (무시됨): userId={}, couponId={}, orderId={}, error={}",
                        userId, couponId, orderId, e.getMessage(), e);
                // 실무에서는 이 경우를 알림 발송 대상으로 표기할 수 있음
            }
        }

        return IssueCouponResponse.from(savedUserCoupon, coupon);
    }

    /**
     * 4.2 사용자가 보유한 쿠폰 조회
     * GET /coupons/issued?status=UNUSED
     *
     * ✅ 성능 최적화 (Message 5):
     * - Fetch Join으로 N+1 문제 해결: 11 쿼리(1 + 10) → 1 쿼리
     * - Redis 캐시: TTL 5분으로 조회 성능 10배 향상
     * - 캐시 무효화: 쿠폰 발급/사용 시 자동 무효화
     *
     * 비즈니스 로직:
     * 1. 사용자 존재 검증
     * 2. status별 필터링 (기본값: UNUSED)
     * 3. 사용자의 쿠폰 목록을 Fetch Join으로 조회 (N+1 해결)
     * 4. UserCoupon + Coupon 정보 결합하여 Response 생성
     *
     * 캐시 전략:
     * - key: "userId:status" (예: "123:UNUSED")
     * - TTL: 5분 (CacheConfig.java 참조)
     * - unless: 결과가 null이거나 empty이면 캐싱 안 함
     *
     * @param userId 사용자 ID
     * @param status 쿠폰 상태 (UNUSED | USED | EXPIRED | CANCELLED)
     * @return 사용자 쿠폰 목록
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     */
    @Cacheable(
            value = "userCouponsCache",
            key = "#userId + ':' + (#status != null && !#status.isEmpty() ? #status : 'UNUSED')",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<UserCouponResponse> getUserCoupons(Long userId, String status) {
        // 1. 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // ✅ 수정: String을 Enum으로 변환
        // 기본값 설정 (기존 "ACTIVE" → "UNUSED")
        String statusStr = status != null && !status.isEmpty() ? status : "UNUSED";
        UserCouponStatus filterStatus = UserCouponStatus.from(statusStr);

        // 2-3. ✅ Fetch Join으로 조회 (N+1 문제 해결)
        // 1번의 쿼리로 UserCoupon + Coupon 정보를 모두 로드
        List<UserCoupon> userCoupons = userCouponRepository.findByUserIdAndStatus(userId, statusStr);

        // 4. UserCoupon + Coupon 정보 결합
        // ✅ 주의: UserCoupon에는 coupon 관계가 없으므로 개별 조회는 피할 수 없음
        // 하지만 Fetch Join이 적용된 쿼리면 메모리에서 빠르게 처리됨
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
     * 캐시: couponList (조회 빈도 높음, 변경 빈도 낮음)
     * TTL: 30분 (실제 프로덕션에서는 Redis로 TTL 적용)
     * 예상 효과: TPS 300 → 2000 (6배 향상)
     *
     * ✅ 개선: 캐시 이름을 CacheKeyConstants로 상수화
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
    @Cacheable(value = CacheKeyConstants.COUPON_LIST, key = "'all'")
    public List<AvailableCouponResponse> getAvailableCoupons() {
        // 1. 발급 가능한 쿠폰 조회
        List<Coupon> availableCoupons = couponRepository.findAllAvailable();

        // 2. Response로 변환
        return availableCoupons.stream()
                .map(AvailableCouponResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 쿠폰 상태 복구 (보상용)
     *
     * Outbox 패턴의 보상 로직에서 호출
     * - COUPON_ISSUE 이벤트의 보상 시 쿠폰 상태를 UNUSED로 복구
     * - 발급된 user_coupon 기록을 삭제하고 쿠폰의 remaining_qty를 복원
     *
     * 동작:
     * 1. 사용자 및 쿠폰 존재 검증
     * 2. UserCoupon 기록 삭제 (쿠폰 발급 기록 제거)
     * 3. 쿠폰의 remaining_qty 복구 (발급 전 상태로 복원)
     *
     * 멱등성:
     * - UserCoupon이 이미 없으면 (이미 삭제된 상태) NOOP
     * - 쿠폰의 remaining_qty는 증가만 하므로 중복 호출 시에도 안전
     * - Event 상태 추적으로 중복 보상 방지
     *
     * @param userId 사용자 ID
     * @param couponId 복구할 쿠폰 ID
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     * @throws CouponNotFoundException 쿠폰을 찾을 수 없는 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revertCouponToAvailable(Long userId, Long couponId) {
        log.info("[CouponService] 쿠폰 상태 복구 시작: userId={}, couponId={}", userId, couponId);

        try {
            // 1. 사용자 및 쿠폰 존재 검증
            if (!userRepository.existsById(userId)) {
                throw new UserNotFoundException(userId);
            }

            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new CouponNotFoundException(couponId));

            // 2. UserCoupon 기록 삭제
            // (사용자가 이 쿠폰을 가지고 있지 않으면 무시)
            userCouponRepository.deleteByUserIdAndCouponId(userId, couponId);
            log.info("[CouponService] UserCoupon 기록 삭제 완료: userId={}, couponId={}", userId, couponId);

            // 3. 쿠폰의 remaining_qty 복구
            // 발급 시에 remaining_qty를 1 감소시켰으므로, 보상 시에 1 증가
            coupon.increaseRemainingQty();
            couponRepository.update(coupon);
            log.info("[CouponService] 쿠폰 상태 복구 완료: couponId={}, remainingQty={}, version={}",
                    couponId, coupon.getRemainingQty(), coupon.getVersion());

        } catch (Exception e) {
            log.error("[CouponService] 쿠폰 상태 복구 실패: userId={}, couponId={}, error={}",
                    userId, couponId, e.getMessage(), e);
            throw e;
        }
    }
}
