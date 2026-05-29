package com.hhplus.ecommerce.application.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.coupon.event.CouponIssuedEvent;
import com.hhplus.ecommerce.domain.order.ChildTransactionEvent;
import com.hhplus.ecommerce.domain.order.ChildTransactionEventRepository;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * CouponTransactionService - 쿠폰 발급 트랜잭션 경계 담당
 *
 * 역할:
 * - 모든 @Transactional(REQUIRES_NEW) 쿠폰 DB 작업을 하나의 클래스에서 관리
 * - CouponService의 self-invocation 문제를 해소: CouponService가 이 빈을 Spring
 *   프록시를 통해 호출하므로 REQUIRES_NEW 트랜잭션 경계가 실제로 적용됨
 *
 * 설계 근거:
 * - 프로젝트 기존 패턴(OrderService → OrderTransactionService)과 동일한 분리 방식
 * - 트랜잭션 경계(DB 락, 재고 차감, 발급 기록)와 흐름 조정(검증, 재시도, 캐시)의 관심사 분리
 *
 * 호출자:
 * - CouponService.issueCoupon()       → issueCouponWithLock(userId, couponId)
 * - CouponService.issueCouponWithLock → 위임 (CouponQueueService, CouponIssueConsumer 경유)
 * - CouponService.revertCouponToAvailable → 위임 (CompensationService 경유)
 */
@Service
public class CouponTransactionService {

    private static final Logger log = LoggerFactory.getLogger(CouponTransactionService.class);

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final ChildTransactionEventRepository childTransactionEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public CouponTransactionService(CouponRepository couponRepository,
                                    UserCouponRepository userCouponRepository,
                                    UserRepository userRepository,
                                    ChildTransactionEventRepository childTransactionEventRepository,
                                    ObjectMapper objectMapper,
                                    ApplicationEventPublisher eventPublisher) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
        this.childTransactionEventRepository = childTransactionEventRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 쿠폰 발급 (주문 연관 없음) - DB 비관적 락 + REQUIRES_NEW 트랜잭션
     *
     * CouponQueueService, CouponIssueConsumer, CouponService.issueCoupon() 에서 호출됨.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IssueCouponResponse issueCouponWithLock(Long userId, Long couponId) {
        return issueCouponInternal(userId, couponId, null);
    }

    /**
     * 쿠폰 발급 (Saga 주문 연관) - DB 비관적 락 + REQUIRES_NEW 트랜잭션
     *
     * Outbox 패턴용 ChildTransactionEvent 를 orderId 와 함께 저장함.
     * Saga 보상 트랜잭션에서 사용됨.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IssueCouponResponse issueCouponWithLock(Long userId, Long couponId, Long orderId) {
        return issueCouponInternal(userId, couponId, orderId);
    }

    /**
     * 쿠폰 상태 복구 (Saga 보상 트랜잭션)
     *
     * COUPON_ISSUE 단계 보상 시 발급된 user_coupon 기록을 삭제하고
     * 쿠폰의 remaining_qty 를 복원한다.
     * CompensationService 에서 호출됨.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revertCouponToAvailable(Long userId, Long couponId) {
        log.info("[CouponTransactionService] 쿠폰 상태 복구 시작: userId={}, couponId={}", userId, couponId);

        try {
            if (!userRepository.existsById(userId)) {
                throw new UserNotFoundException(userId);
            }

            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new CouponNotFoundException(couponId));

            userCouponRepository.deleteByUserIdAndCouponId(userId, couponId);
            log.info("[CouponTransactionService] UserCoupon 기록 삭제 완료: userId={}, couponId={}", userId, couponId);

            coupon.increaseRemainingQty();
            couponRepository.update(coupon);
            log.info("[CouponTransactionService] 쿠폰 상태 복구 완료: couponId={}, remainingQty={}, version={}",
                    couponId, coupon.getRemainingQty(), coupon.getVersion());

        } catch (Exception e) {
            log.error("[CouponTransactionService] 쿠폰 상태 복구 실패: userId={}, couponId={}, error={}",
                    userId, couponId, e.getMessage(), e);
            throw e;
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // private
    // ───────────────────────────────────────────────────────────────────────────

    private IssueCouponResponse issueCouponInternal(Long userId, Long couponId, Long orderId) {
        // 2단계: DB 레벨 비관적 락
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        // 3-5단계: 조건 검증 (락 획득 후)
        if (!coupon.isActiveCoupon()) {
            throw new IllegalArgumentException("쿠폰이 비활성화되어 있습니다");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isValidPeriod(now)) {
            throw new IllegalArgumentException("쿠폰이 유효기간을 벗어났습니다");
        }
        if (!coupon.hasStock()) {
            throw new IllegalArgumentException("쿠폰이 모두 소진되었습니다");
        }

        // 6단계: 원자적 감소
        Integer remainingQtyBefore = coupon.getRemainingQty();
        coupon.decreaseStock();
        Integer remainingQtyAfter = coupon.getRemainingQty();
        couponRepository.update(coupon);

        // 7단계: 중복 발급 방지 (UNIQUE constraint 검증)
        if (userCouponRepository.findByUserIdAndCouponId(userId, couponId).isPresent()) {
            throw new IllegalArgumentException("이 쿠폰은 이미 발급받으셨습니다");
        }

        // 8단계: 발급 기록 저장
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(userId)
                .couponId(couponId)
                .status(UserCouponStatus.UNUSED)
                .issuedAt(LocalDateTime.now())
                .usedAt(null)
                .build();
        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        log.info("[CouponTransactionService] 쿠폰 발급 완료: userId={}, couponId={}, userCouponId={}, remaining_qty={}, version={}",
                userId, couponId, savedUserCoupon.getUserCouponId(), coupon.getRemainingQty(), coupon.getVersion());

        // 캐시 무효화 이벤트 발행 — AFTER_COMMIT 리스너가 트랜잭션 커밋 후 처리
        try {
            eventPublisher.publishEvent(
                    new CouponIssuedEvent(couponId, userId, remainingQtyBefore, remainingQtyAfter));
            log.debug("[CouponTransactionService] CouponIssuedEvent 발행: couponId={}, userId={}", couponId, userId);
        } catch (Exception e) {
            log.warn("[CouponTransactionService] CouponIssuedEvent 발행 실패 (무시됨): couponId={}, userId={}, error={}",
                    couponId, userId, e.getMessage());
        }

        // Outbox 패턴: Saga 보상용 이벤트 저장 (orderId 있을 때만)
        if (orderId != null) {
            try {
                String eventData = objectMapper.writeValueAsString(Map.of(
                        "userId", userId,
                        "couponId", couponId,
                        "discountAmount", coupon.getDiscountAmount(),
                        "remainingQtyBefore", remainingQtyBefore,
                        "remainingQtyAfter", remainingQtyAfter,
                        "userCouponId", savedUserCoupon.getUserCouponId()
                ));
                childTransactionEventRepository.save(
                        ChildTransactionEvent.create(orderId, userId, ChildTxType.COUPON_ISSUE, eventData));
                log.info("[CouponTransactionService] 쿠폰 발급 Event 저장 완료: orderId={}, userId={}, couponId={}",
                        orderId, userId, couponId);
            } catch (Exception e) {
                log.error("[CouponTransactionService] Event 저장 실패 (무시됨): userId={}, couponId={}, orderId={}, error={}",
                        userId, couponId, orderId, e.getMessage(), e);
            }
        }

        return IssueCouponResponse.from(savedUserCoupon, coupon);
    }
}
