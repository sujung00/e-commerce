package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import com.hhplus.ecommerce.presentation.order.request.OrderItemRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OrderTransactionService - 주문 트랜잭션 처리 서비스 (Application 계층)
 *
 * 역할:
 * - OrderService와 분리된 독립적인 서비스
 * - 2단계(원자적 거래)만 담당
 * - @Transactional이 프록시를 통해 정상 작동하도록 보장
 *
 * 이유:
 * - OrderService 내에서 @Transactional 메서드를 직접 호출하면
 *   Spring AOP 프록시가 작동하지 않아 트랜잭션이 적용되지 않음
 * - 별도 서비스로 분리하면 Spring이 프록시를 생성하여 트랜잭션 관리 가능
 *
 * 아키텍처:
 * OrderService (1, 3단계)
 *     ↓ (의존성 주입)
 * OrderTransactionService (2단계, @Transactional 처리)
 */
@Service
public class OrderTransactionService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;

    public OrderTransactionService(OrderRepository orderRepository,
                                   ProductRepository productRepository,
                                   UserRepository userRepository,
                                   OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * 2단계: 원자적 거래 처리 (@Transactional)
     *
     * 프록시를 통해 호출되므로 @Transactional이 정상 작동합니다.
     * 다음 작업이 하나의 트랜잭션으로 처리됩니다:
     * - 재고 차감 (낙관적 락)
     * - 사용자 잔액 차감
     * - 주문 저장
     * - 주문 항목 저장
     * - 쿠폰 사용 처리
     * - 상품 상태 업데이트
     * - Outbox 메시지 저장 (3단계: 외부 전송 대기)
     *
     * 실패 시 모든 변경사항 롤백
     *
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트
     * @param couponId 쿠폰 ID (nullable)
     * @param couponDiscount 쿠폰 할인액
     * @param subtotal 소계
     * @param finalAmount 최종 결제액
     * @return 저장된 주문
     */
    @Transactional
    public Order executeTransactionalOrder(
            Long userId,
            List<OrderItemRequest> orderItems,
            Long couponId,
            Long couponDiscount,
            Long subtotal,
            Long finalAmount) {

        // 2-1: 재고 차감 (낙관적 락 시뮬레이션)
        deductInventory(orderItems);

        // 2-2: 사용자 잔액 차감
        deductUserBalance(userId, finalAmount);

        // 2-3: 주문 생성 및 저장
        Order order = Order.createOrder(userId, couponId, couponDiscount, subtotal, finalAmount);

        // 2-4: 주문 항목 추가
        for (OrderItemRequest itemRequest : orderItems) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));

            ProductOption option = product.getOptions().stream()
                    .filter(o -> o.getOptionId().equals(itemRequest.getOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("옵션을 찾을 수 없습니다"));

            OrderItem orderItem = OrderItem.createOrderItem(
                    itemRequest.getProductId(),
                    itemRequest.getOptionId(),
                    product.getProductName(),
                    option.getName(),
                    itemRequest.getQuantity(),
                    product.getPrice()
            );
            order.addOrderItem(orderItem);
        }

        // 2-5: 주문 저장
        Order savedOrder = orderRepository.save(order);

        // 2-6: 쿠폰 사용 처리 (있는 경우)
        if (couponId != null) {
            markCouponAsUsed(userId, couponId, savedOrder.getOrderId());
        }

        // 2-7: 상품 상태 업데이트 (모든 옵션 재고가 0인 경우 품절로 변경)
        updateProductStatus(orderItems);

        // 2-8: Outbox 메시지 저장 (3단계: 외부 전송)
        // 트랜잭션 2단계 내에서 저장되므로 원자성 보장
        // 별도 배치 프로세스가 PENDING 상태의 메시지를 외부 시스템에 전송
        saveOrderCompletionEvent(savedOrder.getOrderId(), userId);

        return savedOrder;
    }

    /**
     * Outbox 메시지 저장 (Order_COMPLETED 이벤트)
     *
     * 트랜잭션 2단계 내에서 호출되므로 주문 저장과 함께 원자적으로 처리됩니다.
     * 배치 프로세스가 이 메시지를 조회하여 외부 시스템(배송, 결제 등)에 전송합니다.
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     */
    private void saveOrderCompletionEvent(Long orderId, Long userId) {
        Outbox outbox = Outbox.createOutbox(orderId, userId, "ORDER_COMPLETED");
        outboxRepository.save(outbox);
        System.out.println("[OrderTransactionService] Outbox 메시지 저장: orderId=" + orderId + ", status=PENDING");
    }

    /**
     * 재고 차감 (낙관적 락 시뮬레이션)
     *
     * 실제 DB 환경에서는:
     * UPDATE product_options SET stock = stock - qty, version = version + 1
     * WHERE option_id = ? AND version = current_version
     *
     * 버전 불일치 시 race condition 감지 → 예외 발생 → 트랜잭션 롤백
     */
    private void deductInventory(List<OrderItemRequest> orderItems) {
        for (OrderItemRequest itemRequest : orderItems) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));

            ProductOption option = product.getOptions().stream()
                    .filter(o -> o.getOptionId().equals(itemRequest.getOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("옵션을 찾을 수 없습니다"));

            // 낙관적 락: version 확인
            Long currentVersion = option.getVersion();

            // 재고 차감 (동시성 제어를 위한 synchronized 블록)
            synchronized (option) {
                // version 재확인 (race condition 감지)
                if (!option.getVersion().equals(currentVersion)) {
                    throw new IllegalStateException(
                            "ERR-004: 주문 생성에 실패했습니다 (동시 주문으로 인한 재고 변경). 다시 시도하세요."
                    );
                }

                // 재고 차감
                int newStock = option.getStock() - itemRequest.getQuantity();
                if (newStock < 0) {
                    throw new IllegalArgumentException(
                            option.getName() + "의 재고가 부족합니다"
                    );
                }

                // 재고 업데이트 및 version 증가
                option.setStock(newStock);
                option.setVersion(currentVersion + 1);

                // 저장소에 반영
                productRepository.saveOption(option);
            }
        }
    }

    /**
     * 사용자 잔액 차감
     *
     * 실제 DB 환경에서는:
     * UPDATE users SET balance = balance - final_amount WHERE user_id = ?
     */
    private void deductUserBalance(Long userId, Long finalAmount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        long newBalance = user.getBalance() - finalAmount;
        if (newBalance < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다");
        }

        user.setBalance(newBalance);
        // 저장소에 반영 (InMemory이므로 직접 수정)
    }

    /**
     * 쿠폰 사용 처리
     */
    private void markCouponAsUsed(Long userId, Long couponId, Long orderId) {
        // user_coupons 테이블에서 해당 쿠폰을 'USED'로 표시
        // UPDATE user_coupons SET status = 'USED', used_at = NOW() WHERE user_id = ? AND coupon_id = ?
        System.out.println("[OrderTransactionService] 쿠폰 사용 처리: userId=" + userId + ", couponId=" + couponId);
    }

    /**
     * 상품 상태 업데이트
     *
     * 모든 옵션의 재고가 0인 경우 상품 상태를 '품절'로 변경
     */
    private void updateProductStatus(List<OrderItemRequest> orderItems) {
        // 상품별로 집계
        Set<Long> productIds = orderItems.stream()
                .map(OrderItemRequest::getProductId)
                .collect(Collectors.toSet());

        for (Long productId : productIds) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));

            // 모든 옵션의 재고 합계 확인
            int totalStock = product.getOptions().stream()
                    .mapToInt(ProductOption::getStock)
                    .sum();

            // 재고가 0이면 상태를 '품절'로 변경
            if (totalStock <= 0) {
                product.setStatus("품절");
                productRepository.save(product);
            }
        }
    }
}
