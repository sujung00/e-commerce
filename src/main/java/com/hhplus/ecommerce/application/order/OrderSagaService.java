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
import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * OrderSagaService - 주문 생성 및 결제 Saga 패턴 구현 (Application 계층)
 *
 * 역할:
 * - Saga 패턴을 통한 분산 트랜잭션 조율
 * - 주문 생성 → 결제 처리 → 상태 전환
 * - 결제 실패 시 보상 트랜잭션 (Compensation) 실행
 *
 * 플로우:
 * Step 1: OrderTransactionService.executeTransactionalOrder()
 *         - 주문 생성, 재고 차감, 잔액 차감 (PENDING 상태)
 * Step 2: 외부 결제 API 호출
 * Step 3a: 결제 성공 → 주문 상태를 PAID로 변경
 * Step 3b: 결제 실패 → compensateOrder() 실행 (재고/잔액 복구, 주문 취소)
 *
 * 동시성 제어:
 * - Step 1: @Transactional + Pessimistic Lock (쿠폰, 재고)
 * - Step 2: 트랜잭션 외부 (외부 API 호출은 원자성 보장 불가)
 * - Step 3: @Transactional (상태 전환 및 보상 트랜잭션)
 *
 * SCENARIO 9: 결제 실패 후 재고 불일치
 * - Before: 재고 차감, 잔액 차감, 주문 생성 모두 성공 (PENDING)
 * - Payment API: 타임아웃 또는 실패
 * - After: compensateOrder()를 통해 재고/잔액 복구, 주문 취소 (CANCELLED)
 *
 * SCENARIO 10: 결제 부분 성공 (Void 필요)
 * - 카드 승인: 성공
 * - 3D Secure 인증: 실패
 * - Action: Void API 호출하여 승인 취소
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
     * Step 1 + Step 2 + Step 3: 주문 생성과 결제를 한 번의 Saga로 처리
     *
     * 플로우:
     * 1. 주문 생성 (재고 차감, 잔액 차감) - PENDING 상태
     * 2. 결제 API 호출
     * 3-1. 결제 성공 → PAID 상태
     * 3-2. 결제 실패 → compensateOrder() → CANCELLED 상태
     *
     * 주의:
     * - Step 1과 Step 3는 @Transactional이지만 Step 2는 외부 API 호출이므로 트랜잭션 밖
     * - Step 2 실패 시 Step 1의 변경사항이 커밋된 상태에서 보상 필요
     *
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트
     * @param couponId 쿠폰 ID (nullable)
     * @param finalAmount 최종 결제액
     * @return 생성된 주문 (PAID 상태 또는 보상 처리 후 CANCELLED 상태)
     * @throws RuntimeException 결제 실패 또는 보상 중 에러 발생 시
     */
    @Transactional
    public Order createOrderWithPayment(Long userId,
                                       List<OrderItemDto> orderItems,
                                       Long couponId,
                                       Long finalAmount) {
        Order order = null;

        try {
            // Step 1: 주문 생성 (재고 차감, 잔액 차감) - PENDING 상태로 생성됨
            log.info("[OrderSagaService] Step 1 시작: 주문 생성 - userId={}", userId);
            order = orderTransactionService.executeTransactionalOrder(
                    userId,
                    orderItems,
                    couponId,
                    couponId != null ? calculateCouponDiscount(couponId) : 0L, // TODO: 실제 쿠폰 할인액 조회
                    calculateSubtotal(orderItems),
                    finalAmount
            );
            log.info("[OrderSagaService] Step 1 완료: 주문 생성 성공 - orderId={}, status=PENDING", order.getOrderId());

            // Step 2: 외부 결제 API 호출 (트랜잭션 밖)
            log.info("[OrderSagaService] Step 2 시작: 결제 처리 - orderId={}, amount={}", order.getOrderId(), finalAmount);
            PaymentResponse paymentResponse = callPaymentAPI(order.getOrderId(), finalAmount);

            // Step 3a: 결제 성공 시 주문 상태를 PAID로 변경
            if (paymentResponse.isSuccess()) {
                log.info("[OrderSagaService] Step 3a: 결제 성공 - orderId={}, status=PAID", order.getOrderId());
                order.markAsPaid();
                orderRepository.save(order);

                // 관리자 알림 (선택사항)
                alertService.notifyPaymentSuccess(order.getOrderId(), userId, finalAmount);

                return order;
            }

            // Step 3b: 결제 실패 시 보상 트랜잭션 시작
            log.warn("[OrderSagaService] Step 3b: 결제 실패 - orderId={}, reason={}", order.getOrderId(), paymentResponse.getFailureReason());
            alertService.notifyPaymentFailure(order.getOrderId(), userId, finalAmount, paymentResponse.getFailureReason());

            // 보상: 재고 복구, 잔액 복구, 주문 취소
            compensateOrder(order);

            throw new PaymentException("Payment failed: " + paymentResponse.getFailureReason());

        } catch (PaymentException e) {
            // 결제 실패 시에만 보상 (이미 Step 3b에서 처리했을 가능성)
            if (order != null && order.isPending()) {
                log.error("[OrderSagaService] 결제 중 예외 발생, 보상 트랜잭션 시작 - orderId={}, error={}", order.getOrderId(), e.getMessage());
                compensateOrder(order);
            }
            throw new PaymentException("Order saga failed: " + e.getMessage(), e);
        } catch (Exception e) {
            // 기타 예외
            if (order != null && order.isPending()) {
                log.error("[OrderSagaService] 예상치 못한 예외 발생, 보상 트랜잭션 시작 - orderId={}, error={}", order.getOrderId(), e.getMessage());
                try {
                    compensateOrder(order);
                } catch (Exception compensationError) {
                    log.error("[OrderSagaService] 보상 트랜잭션 실패! 수동 개입 필요 - orderId={}, error={}", order.getOrderId(), compensationError.getMessage());
                    alertService.notifyCompensationFailure(order.getOrderId(), userId, compensationError.getMessage());
                    throw new CompensationException("Failed to compensate order: " + order.getOrderId(), compensationError);
                }
            }
            throw new RuntimeException("Order creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 보상 트랜잭션 (Compensation Transaction)
     *
     * 결제 실패 후 Step 1에서 차감된 재고와 잔액을 복구합니다.
     *
     * 복구 항목:
     * 1. 재고 복구: 각 주문 항목별로 ProductOption의 stock 복구
     * 2. 잔액 복구: User의 balance 복구
     * 3. 주문 상태 변경: PENDING → FAILED → CANCELLED
     *
     * 원자성:
     * - 이 메서드는 @Transactional 메서드 내에서 호출되므로 모든 작업이 원자적으로 처리됨
     * - 복구 중 에러 발생 시 rollback되어 데이터 일관성 보장
     *
     * @param order 보상할 주문 (PENDING 상태)
     * @throws Exception 복구 중 데이터베이스 오류 발생
     */
    private void compensateOrder(Order order) {
        if (!order.isPending()) {
            log.warn("[OrderSagaService] 보상 대상이 아닌 주문 - orderId={}, status={}", order.getOrderId(), order.getOrderStatus());
            return;
        }

        log.info("[OrderSagaService] 보상 트랜잭션 시작 - orderId={}", order.getOrderId());

        try {
            // 1. 재고 복구
            for (OrderItem item : order.getOrderItems()) {
                ProductOption option = productRepository.findOptionById(item.getOptionId())
                        .orElseThrow(() -> new IllegalArgumentException("옵션을 찾을 수 없습니다: " + item.getOptionId()));

                log.info("[OrderSagaService] 재고 복구 중 - optionId={}, 복구 수량={}", item.getOptionId(), item.getQuantity());
                option.restoreStock(item.getQuantity());
                productRepository.saveOption(option);
            }

            // 2. 잔액 복구
            User user = userRepository.findById(order.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + order.getUserId()));

            log.info("[OrderSagaService] 잔액 복구 중 - userId={}, 복구액={}", user.getUserId(), order.getFinalAmount());
            user.setBalance(user.getBalance() + order.getFinalAmount());
            userRepository.save(user);

            // 3. 주문 상태 변경: PENDING → FAILED → CANCELLED
            order.markAsFailed();
            order.cancel(); // FAILED → CANCELLED
            orderRepository.save(order);

            log.info("[OrderSagaService] 보상 트랜잭션 완료 - orderId={}, status=CANCELLED", order.getOrderId());
            alertService.notifyCompensationComplete(order.getOrderId(), order.getUserId(), order.getFinalAmount());

        } catch (Exception e) {
            log.error("[OrderSagaService] 보상 트랜잭션 중 오류 발생! 수동 개입 필요 - orderId={}, error={}", order.getOrderId(), e.getMessage());
            alertService.notifyCompensationFailure(order.getOrderId(), order.getUserId(), e.getMessage());
            throw new CompensationException("Compensation failed for order: " + order.getOrderId(), e);
        }
    }

    /**
     * 외부 결제 API 호출 (실제 구현)
     *
     * 현재는 더미 구현입니다. 프로덕션에서는:
     * - PG사 SDK (Iamport, Toss, KCP 등) 사용
     * - HTTP 클라이언트 (RestTemplate, WebClient) 사용
     * - 타임아웃, 재시도, Circuit Breaker 패턴 적용
     *
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @return 결제 응답
     * @throws PaymentException 결제 API 호출 실패
     */
    private PaymentResponse callPaymentAPI(Long orderId, Long amount) throws PaymentException {
        // TODO: 실제 결제 API 구현
        // try {
        //     PaymentRequest request = PaymentRequest.builder()
        //         .orderId(orderId)
        //         .amount(amount)
        //         .build();
        //     return paymentGateway.charge(request); // Iamport, Toss 등
        // } catch (TimeoutException e) {
        //     throw new PaymentException("Payment gateway timeout", e);
        // } catch (HttpException e) {
        //     throw new PaymentException("Payment gateway error: " + e.getStatusCode(), e);
        // }

        // 더미 구현: 항상 성공
        return PaymentResponse.success(orderId);
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
     * PaymentResponse - 결제 API 응답
     */
    public static class PaymentResponse {
        private final boolean success;
        private final String failureReason;

        private PaymentResponse(boolean success, String failureReason) {
            this.success = success;
            this.failureReason = failureReason;
        }

        public static PaymentResponse success(Long orderId) {
            return new PaymentResponse(true, null);
        }

        public static PaymentResponse failure(String reason) {
            return new PaymentResponse(false, reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFailureReason() {
            return failureReason;
        }
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
