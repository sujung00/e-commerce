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
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(OrderTransactionService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;
    private final UserCouponRepository userCouponRepository;

    public OrderTransactionService(OrderRepository orderRepository,
                                   ProductRepository productRepository,
                                   UserRepository userRepository,
                                   OutboxRepository outboxRepository,
                                   UserCouponRepository userCouponRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.outboxRepository = outboxRepository;
        this.userCouponRepository = userCouponRepository;
    }

    /**
     * 2단계: 원자적 거래 처리 (@Transactional)
     *
     * 프록시를 통해 호출되므로 @Transactional이 정상 작동합니다.
     * 다음 작업이 하나의 트랜잭션으로 처리됩니다:
     * - 재고 차감 (Domain 메서드 사용)
     * - 사용자 잔액 차감 (Domain 메서드 사용)
     * - 주문 저장
     * - 주문 항목 저장
     * - 쿠폰 사용 처리
     * - Outbox 메시지 저장 (3단계: 외부 전송 대기)
     *
     * 실패 시 모든 변경사항 롤백
     *
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트 (Application DTO)
     * @param couponId 쿠폰 ID (nullable)
     * @param couponDiscount 쿠폰 할인액
     * @param subtotal 소계
     * @param finalAmount 최종 결제액
     * @return 저장된 주문
     */
    @Transactional
    public Order executeTransactionalOrder(
            Long userId,
            List<OrderItemDto> orderItems,
            Long couponId,
            Long couponDiscount,
            Long subtotal,
            Long finalAmount) {

        // 2-1: 재고 차감 (낙관적 락 시뮬레이션)
        deductInventory(orderItems);

        // 2-2: 사용자 잔액 차감
        deductUserBalance(userId, finalAmount);

        // 2-3: 주문 생성 및 저장
        // 주의: order_items에 order_id를 설정하기 위해, 먼저 Order만 저장하고 OrderItem들을 나중에 연결
        Order order = Order.createOrder(userId, couponId, couponDiscount, subtotal, finalAmount);
        Order savedOrder = orderRepository.save(order);

        // 2-4: 주문 항목 생성 및 Order ID 설정
        for (OrderItemDto itemRequest : orderItems) {
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
            orderItem.setOrderId(savedOrder.getOrderId());
            savedOrder.addOrderItem(orderItem);
        }

        // 모든 OrderItem 추가 후 다시 저장 (CascadeType.PERSIST로 인해 자동 저장됨)
        savedOrder = orderRepository.save(savedOrder);

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
        log.info("[OrderTransactionService] Outbox 메시지 저장: orderId={}, status=PENDING", orderId);
    }

    /**
     * 재고 차감 (Domain 메서드 활용)
     *
     * Product.deductStock() 메서드가 다음을 처리합니다:
     * - ProductOption 재고 검증 및 차감
     * - 낙관적 락 (version 증가)
     * - 상품 상태 자동 업데이트 (모든 옵션 재고가 0인 경우 품절로 변경)
     * - 동시성 제어 (synchronized 블록)
     *
     * Application 계층은 Domain 메서드를 호출하기만 하면 됩니다.
     */
    private void deductInventory(List<OrderItemDto> orderItems) {
        for (OrderItemDto itemRequest : orderItems) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));

            // Domain 메서드 호출 (Product가 내부적으로 ProductOption 조회 및 재고 차감)
            // 예외 처리: InsufficientStockException, ProductOptionNotFoundException
            product.deductStock(itemRequest.getOptionId(), itemRequest.getQuantity());

            // 저장소에 반영
            productRepository.save(product);
        }
    }

    /**
     * 사용자 잔액 차감 (Domain 메서드 활용)
     *
     * User.deductBalance() 메서드가 다음을 처리합니다:
     * - 잔액 검증 (InsufficientBalanceException 발생)
     * - 잔액 차감
     * - 업데이트된 사용자 반환
     */
    private void deductUserBalance(Long userId, Long finalAmount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Domain 메서드 호출 (User가 잔액 검증 및 차감)
        // 예외 처리: InsufficientBalanceException
        user.deductBalance(finalAmount);

        // 저장소에 반영
        userRepository.save(user);
    }

    /**
     * 쿠폰 사용 처리
     * UPDATE user_coupons SET status = 'USED', used_at = NOW() WHERE user_id = ? AND coupon_id = ?
     *
     * ✅ 수정: String "USED" → Enum UserCouponStatus.USED
     * ✅ orderId 제거: user_coupons는 coupon 보유 상태만 관리하고,
     *                  쿠폰 사용은 orders.coupon_id로 추적
     */
    private void markCouponAsUsed(Long userId, Long couponId, Long orderId) {
        UserCoupon userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다"));

        // ✅ 수정: Enum을 사용하여 상태 변경
        userCoupon.setStatus(UserCouponStatus.USED);
        userCoupon.setUsedAt(java.time.LocalDateTime.now());

        userCouponRepository.update(userCoupon);

        log.info("[OrderTransactionService] 쿠폰 사용 처리 완료: userId={}, couponId={}, orderId={}",
                userId, couponId, orderId);
    }

    /**
     * 상품 상태 업데이트 (Domain 메서드 활용)
     *
     * Product.recalculateTotalStock()이 다음을 처리합니다:
     * - 모든 옵션의 재고 합계 재계산
     * - 모든 옵션의 재고가 0인 경우 자동으로 상태를 '품절'로 변경
     * - 그 외의 경우 상태를 '판매중'으로 변경
     *
     * Note: deductStock() 호출 시 자동으로 상태가 업데이트되므로,
     * 이 메서드는 상태 업데이트 재확인 용도로 사용할 수 있습니다.
     */
    private void updateProductStatus(List<OrderItemDto> orderItems) {
        // 상품별로 집계
        Set<Long> productIds = orderItems.stream()
                .map(OrderItemDto::getProductId)
                .collect(Collectors.toSet());

        for (Long productId : productIds) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));

            // Domain 메서드 호출 (Product가 상태 자동 업데이트)
            // 모든 옵션 재고가 0이면 '품절', 그 외에는 '판매중'으로 변경
            product.recalculateTotalStock();

            // 저장소에 반영
            productRepository.save(product);
        }
    }
}
