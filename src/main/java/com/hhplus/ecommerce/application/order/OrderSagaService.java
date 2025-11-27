package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.InsufficientBalanceException;
import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * OrderSagaService - 포인트 기반 주문 결제 시스템 (Application 계층)
 *
 * 역할:
 * - 포인트 차감을 통한 주문 결제 처리
 * - 주문 생성과 동시에 포인트 차감 및 결제 완료
 * - 결제 실패 시 보상 트랜잭션 (Compensation) 실행
 *
 * 현재 플로우 (포인트 기반 단순화):
 * Step 1: OrderTransactionService.executeTransactionalOrder()
 *         - 주문 생성, 재고 차감, 포인트 차감 (원자적 처리)
 *         - ✅ @Transactional 메서드
 *         - ✅ 성공 시 주문 생성 + 포인트 차감 완료 (= 결제 완료)
 * Step 2: 실패 시 보상 트랜잭션
 *         - 재고 복구
 *         - 포인트 환불
 *         - 주문 취소
 *
 * 동시성 제어:
 * - @Transactional + Pessimistic Lock (재고) + Redis DistributedLock
 * - 포인트 차감: UserBalanceService의 분산락 + DB 비관적 락
 * - 외부 API 호출 없음 (포인트 기반 즉시 결제)
 *
 * 중요 특징:
 * - Step 1 실행 = 주문 생성 + 결제 완료 (동일 트랜잭션)
 * - 외부 API 호출 없음 (더 이상 Step 2 필요 없음)
 * - 포인트 차감 실패 시만 보상 트랜잭션 실행
 * - 단순하고 안정적인 포인트 기반 폐쇄형 결제 시스템
 */
@Service
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final OrderTransactionService orderTransactionService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AlertService alertService;

    public OrderSagaService(OrderTransactionService orderTransactionService,
                          OrderRepository orderRepository,
                          ProductRepository productRepository,
                          UserRepository userRepository,
                          AlertService alertService) {
        this.orderTransactionService = orderTransactionService;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.alertService = alertService;
    }

    /**
     * 포인트 기반 주문 생성 및 자동 결제
     *
     * 플로우 (포인트 기반 단순화):
     * Step 1: OrderTransactionService.executeTransactionalOrder()
     *         - 주문 생성, 재고 차감, 포인트 차감 (원자적 처리)
     *         - ✅ @Transactional 메서드
     *         - ✅ 성공 시 = 주문 생성 + 결제 완료
     * Step 2: 실패 시 보상 트랜잭션
     *         - 재고 복구, 포인트 환불, 주문 취소
     *
     * 특징:
     * - 포인트 차감 성공 = 즉시 결제 완료 (외부 API 없음)
     * - 포인트 부족 시 InsufficientBalanceException 발생 → 보상 불필요
     * - 트랜잭션 중 재고/포인트 차감 실패 시만 보상 필요
     * - 단순하고 안정적인 폐쇄형 결제 시스템
     *
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트
     * @param couponId 쿠폰 ID (nullable)
     * @param finalAmount 최종 결제액 (포인트)
     * @return 생성된 주문 (결제 완료된 상태)
     * @throws InsufficientBalanceException 포인트 부족
     * @throws RuntimeException 주문 생성 중 오류
     */
    public Order createOrderWithPayment(Long userId,
                                       List<OrderItemDto> orderItems,
                                       Long couponId,
                                       Long finalAmount) {
        Order order = null;

        try {
            // ========== Step 1: 주문 생성 + 포인트 차감 (트랜잭션) ==========
            // 재고 차감 + 포인트 차감 + 주문 생성을 동일 트랜잭션에서 처리
            // ✅ 원자적 처리: 모두 성공하거나 모두 실패
            // ✅ 포인트 차감 성공 = 즉시 결제 완료 (외부 API 없음)
            log.info("[OrderSagaService] 주문 생성 시작 - userId={}, finalAmount={}", userId, finalAmount);
            order = orderTransactionService.executeTransactionalOrder(
                    userId,
                    orderItems,
                    couponId,
                    couponId != null ? calculateCouponDiscount(couponId) : 0L,
                    calculateSubtotal(orderItems),
                    finalAmount
            );
            log.info("[OrderSagaService] 주문 생성 + 포인트 차감 완료 - orderId={}, 결제완료", order.getOrderId());

            // ✅ Step 1 성공 = 결제 완료
            // 외부 API 호출이 없으므로 추가 단계 필요 없음
            alertService.notifyPaymentSuccess(order.getOrderId(), userId, finalAmount);

            return order;

        } catch (InsufficientBalanceException e) {
            // 포인트 부족: 보상 불필요 (트랜잭션이 롤백되지 않음)
            // 사실 이 경우는 Step 1에서 예외 발생 → 트랜잭션 롤백됨
            log.warn("[OrderSagaService] 포인트 부족으로 주문 생성 실패 - userId={}, 필요포인트={}, error={}",
                    userId, finalAmount, e.getMessage());
            throw e;

        } catch (RuntimeException e) {
            // Step 1 중 기타 예외 (재고 부족, DB 오류 등): 자동 롤백
            log.error("[OrderSagaService] 주문 생성 중 오류 - userId={}, error={}",
                    userId, e.getMessage(), e);

            // order가 생성되었지만 트랜잭션 실패한 경우는 드물지만, 보상 처리
            if (order != null && order.isPending()) {
                try {
                    log.warn("[OrderSagaService] 보상 트랜잭션 시작 - orderId={}", order.getOrderId());
                    compensateOrder(order.getOrderId());
                    log.info("[OrderSagaService] 보상 처리 완료 - orderId={}", order.getOrderId());
                } catch (Exception compensationError) {
                    log.error("[OrderSagaService] 보상 트랜잭션 실패! 수동 개입 필요 - orderId={}, error={}",
                            order.getOrderId(), compensationError.getMessage());
                    alertService.notifyCompensationFailure(order.getOrderId(), userId, compensationError.getMessage());
                    throw new CompensationException("Failed to compensate order: " + order.getOrderId(), compensationError);
                }
            }

            throw new RuntimeException("Order creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 보상 트랜잭션 (Compensation Transaction - Step 3b)
     *
     * ✅ CRITICAL: 상태 검증 및 비즈니스 로직 순서 보장
     * ========================================
     * 1. findByIdForUpdate() - 비관적 락 획득 (SELECT ... FOR UPDATE)
     *    - 다른 트랜잭션의 동시 접근 차단
     * 2. Order 조회 직후 즉시 상태 검증 (PENDING 확인)
     *    - 상태가 PENDING이 아니면 조기 반환 (이미 보상 처리됨)
     * 3. 상태 검증 성공 후에야 비즈니스 로직 시작
     *    - 재고 복구
     *    - 잔액 복구
     *    - 주문 상태 변경
     * 4. 모든 작업이 원자적으로 처리됨 (@Transactional)
     *
     * ⚠️ 주의: 상태 검증 전에 재고/잔액 복구 로직 실행 금지
     *
     * 복구 항목:
     * - 재고 복구: 각 주문 항목별로 ProductOption의 stock 복구
     * - 잔액 복구: User의 balance 복구
     * - 주문 상태 변경: PENDING → FAILED → CANCELLED
     *
     * ✅ IMPORTANT: 이 메서드는 Step 2 (PG API 호출) 이후에 호출됨
     * ✅ 독립적인 @Transactional 메서드로 분리
     * ✅ Step 1과 분리된 트랜잭션에서 실행
     *
     * @param orderId 보상할 주문의 ID
     * @return 보상 처리된 주문 (CANCELLED 상태) 또는 이미 보상된 주문
     * @throws CompensationException 복구 중 데이터베이스 오류 발생
     */
    @Transactional
    public Order compensateOrder(Long orderId) {
        // ==================== STEP 1: Order 조회 + 비관적 락 ====================
        // findByIdForUpdate()로 SELECT ... FOR UPDATE 적용
        // 다른 트랜잭션이 이 Order 행을 쓰기/읽기 할 수 없음 (배타적 잠금)
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found for compensation: " + orderId));

        log.debug("[OrderSagaService] Order 조회 완료 (비관적 락 획득) - orderId={}, 현재상태={}",
                  orderId, order.getOrderStatus());

        // ==================== STEP 2: 상태 검증 (조회 직후 즉시) ====================
        // ✅ CRITICAL: 상태 검증은 조회 바로 직후에 수행해야 함
        // ✅ 이전에 다른 비즈니스 로직 없음
        // ✅ PENDING이 아니면 조기 반환 (이미 보상 처리됨)
        if (!order.isPending()) {
            log.warn("[OrderSagaService] 보상 대상이 아닌 주문 (이미 처리됨) - orderId={}, 현재상태={}, " +
                     "보상을 수행하지 않습니다",
                    orderId, order.getOrderStatus());
            return order;
        }

        log.info("[OrderSagaService] Order 상태 검증 성공 - orderId={}, 상태=PENDING (보상 필요)", orderId);
        log.info("[OrderSagaService] 보상 트랜잭션 시작 - orderId={}", orderId);

        try {
            // ==================== STEP 3: 재고 복구 (상태 검증 후) ====================
            // ✅ 상태 검증 성공 후에만 재고 복구 시작
            log.info("[OrderSagaService] STEP 3-1: 재고 복구 시작 - orderId={}", orderId);
            for (OrderItem item : order.getOrderItems()) {
                ProductOption option = productRepository.findOptionById(item.getOptionId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "재고 복구 중 옵션을 찾을 수 없습니다: " + item.getOptionId()));

                log.info("[OrderSagaService] 재고 복구 중 - optionId={}, 복구 수량={}, 상품명={}",
                         item.getOptionId(), item.getQuantity(), option.getName());
                option.restoreStock(item.getQuantity());
                productRepository.saveOption(option);
            }
            log.info("[OrderSagaService] STEP 3-1 완료: 재고 복구 완료 - orderId={}", orderId);

            // ==================== STEP 4: 잔액 복구 (상태 검증 후) ====================
            // ✅ 재고 복구 성공 후 잔액 복구
            log.info("[OrderSagaService] STEP 3-2: 잔액 복구 시작 - orderId={}", orderId);
            User user = userRepository.findById(order.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "잔액 복구 중 사용자를 찾을 수 없습니다: " + order.getUserId()));

            Long recoveryAmount = order.getFinalAmount();
            Long previousBalance = user.getBalance();
            log.info("[OrderSagaService] 잔액 복구 중 - userId={}, 복구액={}, 기존잔액={}, 복구후잔액={}",
                     user.getUserId(), recoveryAmount, previousBalance, previousBalance + recoveryAmount);
            user.setBalance(user.getBalance() + recoveryAmount);
            userRepository.save(user);
            log.info("[OrderSagaService] STEP 3-2 완료: 잔액 복구 완료 - orderId={}", orderId);

            // ==================== STEP 5: 주문 상태 변경 (재고/잔액 복구 후) ====================
            // ✅ 재고/잔액 복구 성공 후 주문 상태 변경
            // PENDING → FAILED → CANCELLED
            log.info("[OrderSagaService] STEP 3-3: 주문 상태 변경 시작 - orderId={}", orderId);
            try {
                order.markAsFailed();
                log.info("[OrderSagaService] 주문 상태 변경 (PENDING→FAILED) - orderId={}", orderId);

                order.cancel(); // FAILED → CANCELLED
                log.info("[OrderSagaService] 주문 상태 변경 (FAILED→CANCELLED) - orderId={}", orderId);
            } catch (Exception statusChangeError) {
                log.error("[OrderSagaService] 주문 상태 변경 중 오류 - orderId={}, error={}",
                         orderId, statusChangeError.getMessage());
                throw statusChangeError;
            }

            // ==================== STEP 6: DB 저장 ====================
            // ✅ 모든 변경사항을 DB에 저장 (이 @Transactional 내에서 원자적으로 처리)
            Order cancelledOrder = orderRepository.save(order);
            log.info("[OrderSagaService] STEP 3-3 완료: 주문 상태 변경 완료 - orderId={}, 최종상태=CANCELLED", orderId);

            // ==================== STEP 7: 완료 알림 ====================
            log.info("[OrderSagaService] 보상 트랜잭션 완료 - orderId={}, 최종상태=CANCELLED", orderId);
            alertService.notifyCompensationComplete(orderId, order.getUserId(), order.getFinalAmount());

            return cancelledOrder;

        } catch (Exception e) {
            log.error("[OrderSagaService] 보상 트랜잭션 중 오류 발생! 수동 개입 필요 - orderId={}, error={}, message={}",
                    orderId, e.getClass().getSimpleName(), e.getMessage(), e);
            alertService.notifyCompensationFailure(orderId, order.getUserId(), e.getMessage());
            throw new CompensationException("Compensation failed for order: " + orderId, e);
        }
    }

    /**
     * 쿠폰 할인액 조회 (실제 구현 필요)
     * TODO: CouponRepository를 통해 실제 할인액 조회
     */
    private Long calculateCouponDiscount(Long couponId) {
        return 0L; // 임시
    }

    /**
     * 소계 계산 (주문 항목 가격 합계)
     */
    private Long calculateSubtotal(List<OrderItemDto> orderItems) {
        return orderItems.stream()
                .mapToLong(item -> {
                    Product product = productRepository.findById(item.getProductId())
                            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));
                    return product.getPrice() * item.getQuantity();
                })
                .sum();
    }

    /**
     * PaymentException - 결제 실패 예외
     */
    public static class PaymentException extends RuntimeException {
        public PaymentException(String message) {
            super(message);
        }

        public PaymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * CompensationException - 보상 트랜잭션 실패 예외 (심각함)
     */
    public static class CompensationException extends RuntimeException {
        public CompensationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
