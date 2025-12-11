package com.hhplus.ecommerce.application.order.saga.steps;

import com.hhplus.ecommerce.application.order.saga.SagaContext;
import com.hhplus.ecommerce.application.order.saga.SagaStep;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * UseCouponStep - 쿠폰 사용 처리 Step (Saga Step 3/4)
 *
 * 역할:
 * - 사용자 쿠폰 상태를 UNUSED → USED로 변경
 * - 쿠폰 사용 시점(usedAt) 기록
 * - 쿠폰 정보를 SagaContext에 기록 (보상용)
 * - 쿠폰이 없는 경우 skip
 *
 * 실행 순서: 3번 (재고 차감, 포인트 차감 다음)
 * - 재고/포인트 차감 성공 후 쿠폰 처리
 * - 쿠폰이 없으면 execute()와 compensate() 모두 skip
 *
 * Forward Flow (execute):
 * 1. context.getCouponId() 확인 (null이면 skip)
 * 2. UserCoupon 조회 (비관적 락 적용)
 * 3. 쿠폰 상태 검증 (UNUSED 여부)
 * 4. 상태를 USED로 변경, usedAt 설정
 * 5. DB 저장
 * 6. context.setUsedCouponId() 호출 (보상 메타데이터 기록)
 * 7. context.setCouponUsed(true) 설정 (보상 플래그)
 *
 * Backward Flow (compensate):
 * 1. context.isCouponUsed() 확인
 * 2. true이면 쿠폰 상태 복구 실행
 * 3. UserCoupon 조회 (비관적 락 적용)
 * 4. 상태를 UNUSED로 변경, usedAt을 null로 설정
 * 5. DB 저장
 *
 * 동시성 제어:
 * - findByUserIdAndCouponIdForUpdate()로 비관적 락 적용
 * - SELECT ... FOR UPDATE로 동시성 안전성 보장
 *
 * 트랜잭션 전략:
 * - @Transactional(propagation = REQUIRES_NEW)
 * - 독립적인 트랜잭션으로 실행
 * - Step 실패 시 해당 Step만 롤백
 */
@Component
public class UseCouponStep implements SagaStep {

    private static final Logger log = LoggerFactory.getLogger(UseCouponStep.class);

    private final UserCouponRepository userCouponRepository;

    public UseCouponStep(UserCouponRepository userCouponRepository) {
        this.userCouponRepository = userCouponRepository;
    }

    @Override
    public String getName() {
        return "UseCouponStep";
    }

    @Override
    public int getOrder() {
        return 3; // 재고/포인트 차감 다음
    }

    /**
     * 쿠폰 사용 처리 (Forward Flow)
     *
     * 처리 로직:
     * 1. 쿠폰 ID 확인 (null이면 skip)
     * 2. UserCoupon 조회 (비관적 락)
     * 3. 상태 검증 (UNUSED 확인)
     * 4. 상태를 USED로 변경, usedAt 설정
     * 5. DB 저장
     * 6. SagaContext에 메타데이터 기록
     *
     * 예외 처리:
     * - 쿠폰을 찾을 수 없음: IllegalArgumentException
     * - 쿠폰이 UNUSED가 아님: IllegalArgumentException
     * - 예외 발생 시 Orchestrator가 보상 플로우 시작
     *   (재고, 포인트 복구)
     *
     * @param context Saga 실행 컨텍스트
     * @throws Exception 쿠폰 사용 처리 실패 시
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(SagaContext context) throws Exception {
        // ========== Step 1: 쿠폰 ID 확인 ==========
        Long couponId = context.getCouponId();
        if (couponId == null) {
            log.info("[{}] 쿠폰이 없으므로 skip", getName());
            return;
        }

        Long userId = context.getUserId();

        log.info("[{}] 쿠폰 사용 처리 시작 - userId={}, couponId={}",
                getName(), userId, couponId);

        // ========== Step 2: UserCoupon 조회 (비관적 락) ==========
        // findByUserIdAndCouponIdForUpdate()로 SELECT ... FOR UPDATE 적용
        // 다른 트랜잭션이 동시에 이 쿠폰을 사용하려고 하면 대기
        UserCoupon userCoupon = userCouponRepository.findByUserIdAndCouponIdForUpdate(userId, couponId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "사용자 쿠폰을 찾을 수 없습니다: userId=" + userId + ", couponId=" + couponId));

        log.info("[{}] UserCoupon 조회 완료 (비관적 락 획득) - userCouponId={}, 현재상태={}",
                getName(), userCoupon.getUserCouponId(), userCoupon.getStatus());

        // ========== Step 3: 쿠폰 상태 검증 ==========
        if (userCoupon.getStatus() != UserCouponStatus.UNUSED) {
            throw new IllegalArgumentException(
                    "쿠폰은 UNUSED 상태여야 합니다. 현재 상태: " + userCoupon.getStatus());
        }

        // ========== Step 4: 쿠폰 상태를 USED로 변경 ==========
        userCoupon.setStatus(UserCouponStatus.USED);
        userCoupon.setUsedAt(LocalDateTime.now());

        log.info("[{}] 쿠폰 상태 변경 (UNUSED → USED) - userCouponId={}, usedAt={}",
                getName(), userCoupon.getUserCouponId(), userCoupon.getUsedAt());

        // ========== Step 5: DB에 저장 ==========
        userCouponRepository.update(userCoupon);

        log.info("[{}] 쿠폰 사용 처리 완료 - userId={}, couponId={}, status={}",
                getName(), userId, couponId, userCoupon.getStatus());

        // ========== Step 6: SagaContext에 메타데이터 기록 (보상용) ==========
        context.setUsedCouponId(couponId);

        // ========== Step 7: 보상 플래그 설정 ==========
        context.setCouponUsed(true);

        log.info("[{}] 쿠폰 사용 Step 완료 - userId={}, couponId={}",
                getName(), userId, couponId);
    }

    /**
     * 쿠폰 복구 (Backward Flow / Compensation)
     *
     * 처리 로직:
     * 1. context.isCouponUsed() 확인
     * 2. true이면 쿠폰 복구 실행, false이면 skip
     * 3. context.getUsedCouponId()에서 복구할 쿠폰 ID 조회
     * 4. UserCoupon 조회 (비관적 락)
     * 5. 상태를 UNUSED로 변경, usedAt을 null로 설정
     * 6. DB 저장
     *
     * Best Effort 보상:
     * - 보상 실패 시 예외를 발생시키지 말고 로깅만 수행
     * - Orchestrator가 다음 보상을 계속 진행할 수 있도록 함
     *
     * @param context Saga 실행 컨텍스트 (보상 메타데이터 포함)
     * @throws Exception 보상 중 치명적 오류 발생 시
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(SagaContext context) throws Exception {
        // ========== Step 1: 보상 플래그 확인 ==========
        if (!context.isCouponUsed()) {
            log.info("[{}] 쿠폰 사용이 실행되지 않았으므로 보상 skip", getName());
            return;
        }

        Long userId = context.getUserId();
        Long usedCouponId = context.getUsedCouponId();

        log.warn("[{}] 쿠폰 복구 시작 - userId={}, couponId={}",
                getName(), userId, usedCouponId);

        try {
            // ========== Step 2: UserCoupon 조회 (비관적 락) ==========
            UserCoupon userCoupon = userCouponRepository.findByUserIdAndCouponIdForUpdate(userId, usedCouponId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "쿠폰 복구 중 사용자 쿠폰을 찾을 수 없습니다: userId=" + userId + ", couponId=" + usedCouponId));

            log.info("[{}] UserCoupon 조회 완료 (비관적 락 획득) - userCouponId={}, 현재상태={}",
                    getName(), userCoupon.getUserCouponId(), userCoupon.getStatus());

            // ========== Step 3: 쿠폰 상태를 UNUSED로 복구 ==========
            userCoupon.setStatus(UserCouponStatus.UNUSED);
            userCoupon.setUsedAt(null);

            log.info("[{}] 쿠폰 상태 복구 (USED → UNUSED) - userCouponId={}",
                    getName(), userCoupon.getUserCouponId());

            // ========== Step 4: DB에 저장 ==========
            userCouponRepository.update(userCoupon);

            log.warn("[{}] 쿠폰 복구 완료 - userId={}, couponId={}, status={}",
                    getName(), userId, usedCouponId, userCoupon.getStatus());

        } catch (Exception e) {
            // 쿠폰 복구 실패는 로깅만 하고 예외를 전파하지 않음 (Best Effort)
            log.error("[{}] 쿠폰 복구 실패 (무시하고 계속) - userId={}, couponId={}, error={}",
                    getName(), userId, usedCouponId, e.getMessage(), e);
        }
    }
}