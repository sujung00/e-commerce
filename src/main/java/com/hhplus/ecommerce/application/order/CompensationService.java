package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.*;
import com.hhplus.ecommerce.application.user.UserBalanceService;
import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.inventory.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * CompensationService - 자동 보상 트랜잭션 서비스 (Outbox 패턴)
 *
 * 역할:
 * - Parent TX 실패 시 이미 커밋된 Child TX 자동 보상
 * - ChildTransactionEvent를 조회하여 보상 대상 식별
 * - 각 Event의 타입별로 맞는 보상 로직 실행
 * - Event 상태를 COMPENSATED로 업데이트
 *
 * 아키텍처:
 * 1. Parent TX 실패 감지 (Event Listener 또는 배치)
 * 2. CompensationService.compensateOrder(orderId) 호출
 * 3. ChildTransactionEventRepository에서 COMPLETED 이벤트 조회
 * 4. 각 Event별 보상 로직 실행
 *    - BALANCE_DEDUCT → refundBalance() 호출
 *    - COUPON_ISSUE → revertCoupon() 호출
 *    - INVENTORY_DEDUCT → addInventory() 호출 (향후)
 * 5. Event 상태를 COMPENSATED로 업데이트
 *
 * 보상 로직의 특징:
 * - 보상은 Child TX와 동일하게 독립적 TX로 실행 (실패해도 주문에 영향 없음)
 * - 보상 실패 시 로깅하고, 별도 배치에서 재시도 가능
 * - 멱등성 보장: 같은 Event에 대해 여러 번 보상해도 안전
 *
 * 사용 시나리오:
 * ```
 * // Parent TX 실패 시 (Event Listener에서)
 * try {
 *     compensationService.compensateOrder(orderId);
 *     log.info("주문 {} 보상 완료", orderId);
 * } catch (Exception e) {
 *     log.error("주문 {} 보상 실패, 나중에 재시도", orderId, e);
 *     // 별도 배치 작업에서 미처리 Event 다시 조회하여 보상 시도
 * }
 * ```
 *
 * ✅ 개선사항:
 * - Parent TX 실패 시 자동 보상으로 데이터 일관성 복구
 * - 보상 로직 독립 실행으로 신뢰성 향상
 * - 배치 작업으로 미처리 건 자동 재처리 가능
 */
@Service
public class CompensationService {

    private static final Logger log = LoggerFactory.getLogger(CompensationService.class);

    private final ChildTransactionEventRepository childTransactionEventRepository;
    private final UserBalanceService userBalanceService;
    private final CouponService couponService;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public CompensationService(
            ChildTransactionEventRepository childTransactionEventRepository,
            UserBalanceService userBalanceService,
            CouponService couponService,
            InventoryService inventoryService,
            ObjectMapper objectMapper) {
        this.childTransactionEventRepository = childTransactionEventRepository;
        this.userBalanceService = userBalanceService;
        this.couponService = couponService;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 주문의 모든 Child TX 보상 (메인 진입점)
     *
     * 동작:
     * 1. 주문의 COMPLETED 이벤트 조회 (아직 보상 안 된 것)
     * 2. 각 이벤트별 보상 로직 실행
     * 3. 이벤트 상태를 COMPENSATED로 업데이트
     * 4. 모든 보상 완료 시 로깅
     *
     * 호출 타이밍:
     * - Parent TX 실패 시 Event Listener에서 자동 호출
     * - 또는 배치 작업에서 미처리 건 조회 후 호출
     *
     * 멱등성:
     * - 같은 orderId에 대해 여러 번 호출해도 안전
     * - COMPENSATED 상태인 이벤트는 자동으로 skip
     *
     * @param orderId 주문 ID
     * @throws Exception 보상 중 예외 발생 시 (보상 실패)
     */
    @Transactional
    public void compensateOrder(Long orderId) throws Exception {
        log.info("[CompensationService] 주문 {} 보상 시작", orderId);

        try {
            // 1. 아직 보상 안 된 이벤트 조회 (PENDING, COMPLETED, FAILED)
            List<ChildTransactionEvent> eventsToCompensate =
                    childTransactionEventRepository.findByOrderIdAndStatusNot(orderId, EventStatus.COMPENSATED);

            if (eventsToCompensate.isEmpty()) {
                log.info("[CompensationService] 주문 {}의 보상 대상 이벤트 없음 (모두 완료됨)", orderId);
                return;
            }

            // 2. 각 이벤트별 보상 로직 실행
            for (ChildTransactionEvent event : eventsToCompensate) {
                try {
                    // COMPLETED 상태의 이벤트만 보상 (FAILED, PENDING은 실행되지 않은 것)
                    if (EventStatus.COMPLETED.equals(event.getStatus())) {
                        compensateChildTransaction(event);
                    } else {
                        // PENDING이나 FAILED는 보상할 필요 없음 (실행되지 않았거나 실패함)
                        event.markAsCompensated();
                        childTransactionEventRepository.save(event);
                        log.info("[CompensationService] 이벤트 {} ({}): 이미 처리됨 또는 실행 안 됨, COMPENSATED로 표기",
                                event.getEventId(), event.getTxType());
                    }

                } catch (Exception e) {
                    // 보상 실패 시 로깅하고 계속 진행 (다른 이벤트 보상 진행)
                    log.error("[CompensationService] 이벤트 {} ({}) 보상 실패: {}",
                            event.getEventId(), event.getTxType(), e.getMessage(), e);
                    // 실제 운영 환경에서는 알림 발송 또는 별도 큐에 저장
                }
            }

            log.info("[CompensationService] 주문 {} 보상 완료", orderId);

        } catch (Exception e) {
            log.error("[CompensationService] 주문 {} 보상 중 예외 발생: {}", orderId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 개별 Child TX 보상
     * Event의 타입에 따라 맞는 보상 로직 실행
     *
     * @param event 보상 대상 Event
     * @throws Exception 보상 실패 시
     */
    private void compensateChildTransaction(ChildTransactionEvent event) throws Exception {
        log.debug("[CompensationService] 이벤트 {} ({}) 보상 시작, eventData={}",
                event.getEventId(), event.getTxType(), event.getEventData());

        try {
            switch (event.getTxType()) {
                case BALANCE_DEDUCT:
                    compensateBalanceDeduction(event);
                    break;

                case COUPON_ISSUE:
                    compensateCouponIssuance(event);
                    break;

                case INVENTORY_DEDUCT:
                    compensateInventoryDeduction(event);
                    break;

                default:
                    log.warn("[CompensationService] 알 수 없는 TX 타입: {}", event.getTxType());
            }

            // 보상 완료 후 Event 상태 업데이트
            event.markAsCompensated();
            childTransactionEventRepository.save(event);

            log.info("[CompensationService] 이벤트 {} ({}) 보상 완료",
                    event.getEventId(), event.getTxType());

        } catch (Exception e) {
            log.error("[CompensationService] 이벤트 {} ({}) 보상 실패: {}",
                    event.getEventId(), event.getTxType(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 잔액 차감 보상 (환불)
     *
     * 흐름:
     * 1. eventData에서 userId, deductedAmount 추출
     * 2. UserBalanceService.refundBalance() 호출
     * 3. 환불 완료
     *
     * 멱등성:
     * - 같은 금액 여러 번 환불되면 과다 환불 위험
     * - 해결책: Event 상태를 확인하여 이미 보상된 것은 skip
     * - 이미 COMPENSATED 상태인 이벤트는 compensateOrder에서 조회 안 함
     *
     * @param event BALANCE_DEDUCT 타입 이벤트
     * @throws Exception 환불 실패 시
     */
    private void compensateBalanceDeduction(ChildTransactionEvent event) throws Exception {
        try {
            // 1. eventData JSON 파싱
            // eventData 형식: {"userId": 1, "deductedAmount": 10000}
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(event.getEventData(), Map.class);

            Long userId = ((Number) data.get("userId")).longValue();
            Long deductedAmount = ((Number) data.get("deductedAmount")).longValue();

            log.info("[CompensationService] 잔액 차감 보상: userId={}, refundAmount={}", userId, deductedAmount);

            // 2. 환불 실행
            // UserBalanceService.refundBalance()는 REQUIRES_NEW 트랜잭션으로 독립 실행
            userBalanceService.refundBalance(userId, deductedAmount);

            log.info("[CompensationService] 잔액 환불 완료: userId={}, refundAmount={}", userId, deductedAmount);

        } catch (Exception e) {
            log.error("[CompensationService] 잔액 환불 실패: eventData={}, error={}",
                    event.getEventData(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 쿠폰 발급 보상 (쿠폰 상태 복구)
     *
     * 흐름:
     * 1. eventData에서 userId, couponId 추출
     * 2. CouponService.revertCouponToAvailable() 호출
     * 3. 쿠폰 발급 기록 삭제 및 쿠폰 재고 복구
     *
     * 멱등성:
     * - 같은 쿠폰 여러 번 복구 시 문제 없음
     * - 이미 복구된 상태는 변화 없음
     * - Event 상태 추적으로 중복 보상 방지
     *
     * @param event COUPON_ISSUE 타입 이벤트
     * @throws Exception 쿠폰 복구 실패 시
     */
    private void compensateCouponIssuance(ChildTransactionEvent event) throws Exception {
        try {
            // 1. eventData JSON 파싱
            // eventData 형식: {"userId": 1, "couponId": 5, "discountAmount": 5000, ...}
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(event.getEventData(), Map.class);

            Long userId = ((Number) data.get("userId")).longValue();
            Long couponId = ((Number) data.get("couponId")).longValue();

            log.info("[CompensationService] 쿠폰 발급 보상 시작: userId={}, couponId={}", userId, couponId);

            // 2. 쿠폰 상태 복구
            // CouponService.revertCouponToAvailable()는 REQUIRES_NEW 트랜잭션으로 독립 실행
            couponService.revertCouponToAvailable(userId, couponId);

            log.info("[CompensationService] 쿠폰 상태 복구 완료: userId={}, couponId={}", userId, couponId);

        } catch (Exception e) {
            log.error("[CompensationService] 쿠폰 복구 실패: eventData={}, error={}",
                    event.getEventData(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 재고 차감 보상 (재고 추가)
     *
     * 흐름:
     * 1. eventData에서 productId, optionId, deductedQuantity 추출
     * 2. InventoryService.restoreInventory() 호출
     * 3. 재고 복구 완료
     *
     * 멱등성:
     * - 같은 재고 여러 번 복구 시 누적됨 (재고는 계속 증가)
     * - Event 상태 추적으로 중복 보상 방지
     * - 재고는 음수가 될 수 없으므로 안전
     *
     * @param event INVENTORY_DEDUCT 타입 이벤트
     * @throws Exception 재고 추가 실패 시
     */
    private void compensateInventoryDeduction(ChildTransactionEvent event) throws Exception {
        try {
            // 1. eventData JSON 파싱
            // eventData 형식: {"productId": 1, "optionId": 10, "deductedQuantity": 5}
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(event.getEventData(), Map.class);

            Long productId = ((Number) data.get("productId")).longValue();
            Long optionId = ((Number) data.get("optionId")).longValue();
            Integer deductedQuantity = ((Number) data.get("deductedQuantity")).intValue();

            log.info("[CompensationService] 재고 차감 보상 시작: productId={}, optionId={}, restoreQuantity={}",
                    productId, optionId, deductedQuantity);

            // 2. 재고 복구
            // InventoryService.restoreInventory()는 REQUIRES_NEW 트랜잭션으로 독립 실행
            inventoryService.restoreInventory(productId, optionId, deductedQuantity);

            log.info("[CompensationService] 재고 복구 완료: productId={}, optionId={}, restoreQuantity={}",
                    productId, optionId, deductedQuantity);

        } catch (Exception e) {
            log.error("[CompensationService] 재고 복구 실패: eventData={}, error={}",
                    event.getEventData(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 배치 작업용: 미처리 이벤트 자동 보상
     *
     * 주기적 배치 작업에서 호출:
     * - 매 1분마다 COMPLETED 상태의 미처리 이벤트 조회
     * - 해당 주문의 보상 로직 실행
     * - 네트워크 이슈 등으로 미처리된 건을 자동으로 재처리
     *
     * 사용 방법:
     * @Scheduled(fixedRate = 60000) // 1분마다
     * public void processPendingCompensations() {
     *     List<ChildTransactionEvent> events =
     *         childTransactionEventRepository.findByStatus(EventStatus.COMPLETED);
     *     for (ChildTransactionEvent event : events) {
     *         try {
     *             compensateOrder(event.getOrderId());
     *         } catch (Exception e) {
     *             log.error("보상 실패, 다음 배치에서 재처리: orderId={}", event.getOrderId(), e);
     *         }
     *     }
     * }
     */
}
