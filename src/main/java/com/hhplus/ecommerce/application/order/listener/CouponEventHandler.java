package com.hhplus.ecommerce.application.order.listener;

import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.order.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * CouponEventHandler - 주문 생성 시 쿠폰 사용 처리 (동기 이벤트 핸들러)
 *
 * 역할:
 * - OrderCreatedEvent를 수신하여 쿠폰 사용 처리
 * - 주문 Core Transaction과 동일한 트랜잭션에서 실행 (BEFORE_COMMIT)
 * - 예외 발생 시 부모 트랜잭션 롤백
 *
 * Phase 2 개선:
 * - God Transaction 해체의 일환
 * - OrderTransactionService의 markCouponAsUsed() 로직을 이벤트 핸들러로 분리
 * - Core TX(재고 차감)와 쿠폰 처리를 이벤트로 분리하되, 동기성 유지
 *
 * 동시성 제어:
 * - findByUserIdAndCouponIdForUpdate()를 사용하여 SELECT ... FOR UPDATE 적용
 * - DB 레벨 비관적 락으로 쿠폰 중복 사용 방지
 * - BEFORE_COMMIT phase로 부모 트랜잭션과 함께 커밋
 *
 * @TransactionalEventListener(phase = BEFORE_COMMIT):
 * - 부모 트랜잭션이 커밋되기 전에 실행
 * - 이 핸들러에서 예외 발생 시 부모 트랜잭션도 롤백
 * - 쿠폰 처리의 트랜잭션 일관성 보장
 */
@Component
public class CouponEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CouponEventHandler.class);

    private final UserCouponRepository userCouponRepository;

    public CouponEventHandler(UserCouponRepository userCouponRepository) {
        this.userCouponRepository = userCouponRepository;
    }

    /**
     * 주문 생성 완료 시 쿠폰 사용 처리 (동기)
     *
     * 이벤트 수신 시점:
     * - OrderTransactionService.executeTransactionalOrderInternal() 트랜잭션 내부
     * - publishEvent(OrderCreatedEvent) 직후
     * - 부모 트랜잭션 커밋 전 (BEFORE_COMMIT)
     *
     * 처리 로직:
     * 1. 쿠폰을 비관적 락으로 조회 (SELECT ... FOR UPDATE)
     * 2. 쿠폰 상태 검증 (UNUSED 상태인지 확인)
     * 3. 쿠폰 상태를 USED로 변경
     * 4. used_at 타임스탬프 설정
     * 5. DB에 저장
     *
     * 예외 처리:
     * - IllegalArgumentException: 쿠폰을 찾을 수 없거나 이미 사용된 경우
     * - 예외 발생 시 부모 트랜잭션 롤백 (주문 생성도 함께 취소)
     *
     * @param event OrderCreatedEvent (orderId, userId, couponId, orderItems)
     * @throws IllegalArgumentException 쿠폰 처리 실패 시
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 쿠폰이 없는 경우 처리 안 함
        if (event.getCouponId() == null) {
            log.debug("[CouponEventHandler] 쿠폰 없음 - skip: orderId={}", event.getOrderId());
            return;
        }

        log.info("[CouponEventHandler] 쿠폰 사용 처리 시작: orderId={}, userId={}, couponId={}",
                event.getOrderId(), event.getUserId(), event.getCouponId());

        try {
            // ✅ 비관적 락 적용: SELECT ... FOR UPDATE로 즉시 락 획득
            // 다른 트랜잭션이 동시에 이 쿠폰을 사용하려고 하면 대기
            UserCoupon userCoupon = userCouponRepository.findByUserIdAndCouponIdForUpdate(
                            event.getUserId(),
                            event.getCouponId())
                    .orElseThrow(() -> new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다"));

            // ✅ 쿠폰 상태 검증: UNUSED 상태인지 확인
            if (userCoupon.getStatus() != UserCouponStatus.UNUSED) {
                throw new IllegalArgumentException(
                        "쿠폰은 UNUSED 상태여야 합니다. 현재 상태: " + userCoupon.getStatus());
            }

            // ✅ 쿠폰 상태를 USED로 변경
            userCoupon.setStatus(UserCouponStatus.USED);
            userCoupon.setUsedAt(java.time.LocalDateTime.now());

            // ✅ DB에 저장 (부모 트랜잭션에 의해 원자적 처리)
            userCouponRepository.update(userCoupon);

            log.info("[CouponEventHandler] 쿠폰 사용 처리 완료 (Pessimistic Lock 적용): " +
                            "userId={}, couponId={}, orderId={}, status={}",
                    event.getUserId(), event.getCouponId(), event.getOrderId(), userCoupon.getStatus());

        } catch (Exception e) {
            // 예외 발생 시 부모 트랜잭션 롤백
            log.error("[CouponEventHandler] 쿠폰 사용 처리 실패 - 부모 트랜잭션 롤백: " +
                            "orderId={}, userId={}, couponId={}, error={}",
                    event.getOrderId(), event.getUserId(), event.getCouponId(), e.getMessage());
            throw e;
        }
    }
}