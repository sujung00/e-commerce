package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.presentation.order.response.CancelOrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * OrderCancelTransactionService - 주문 취소 트랜잭션 처리 서비스 (Application 계층)
 *
 * 역할:
 * - OrderService와 분리된 독립적인 서비스
 * - 주문 취소 시 2단계(원자적 거래)만 담당
 * - @Transactional이 프록시를 통해 정상 작동하도록 보장
 *
 * 아키텍처:
 * OrderService (1, 3단계)
 *     ↓ (의존성 주입)
 * OrderCancelTransactionService (2단계, @Transactional 처리)
 */
@Service
public class OrderCancelTransactionService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderCancelTransactionService(OrderRepository orderRepository,
                                        ProductRepository productRepository,
                                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    /**
     * 2단계: 원자적 주문 취소 처리 (@Transactional)
     *
     * 프록시를 통해 호출되므로 @Transactional이 정상 작동합니다.
     * 다음 작업이 하나의 트랜잭션으로 처리됩니다:
     * - 주문 상태 변경 (CANCELLED)
     * - 재고 복구 (낙관적 락)
     * - 사용자 잔액 복구
     * - 쿠폰 상태 복구 (있는 경우)
     *
     * 실패 시 모든 변경사항 롤백
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param order 주문 엔티티
     * @return 취소 응답
     */
    @Transactional
    public CancelOrderResponse executeTransactionalCancel(Long orderId, Long userId, Order order) {
        // 2-1: 주문 상태 변경
        order.setOrderStatus("CANCELLED");
        order.setUpdatedAt(java.time.LocalDateTime.now());

        // 2-2: 재고 복구
        List<CancelOrderResponse.RestoredItem> restoredItems = new ArrayList<>();
        for (var orderItem : order.getOrderItems()) {
            Product product = productRepository.findById(orderItem.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(orderItem.getProductId()));

            ProductOption option = product.getOptions().stream()
                    .filter(o -> o.getOptionId().equals(orderItem.getOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("옵션을 찾을 수 없습니다"));

            // 낙관적 락: version 확인
            Long currentVersion = option.getVersion();

            // 재고 복구 (동시성 제어를 위한 synchronized 블록)
            synchronized (option) {
                // version 재확인
                if (!option.getVersion().equals(currentVersion)) {
                    throw new IllegalStateException("ERR-005: 주문 취소에 실패했습니다 (동시 수정으로 인한 재고 변경). 다시 시도하세요.");
                }

                // 재고 복구
                int newStock = option.getStock() + orderItem.getQuantity();
                option.setStock(newStock);
                option.setVersion(currentVersion + 1);

                // 저장소에 반영
                productRepository.saveOption(option);

                // 복구된 항목 정보 추가
                restoredItems.add(CancelOrderResponse.RestoredItem.builder()
                        .orderItemId(orderItem.getOrderItemId())
                        .productId(orderItem.getProductId())
                        .productName(orderItem.getProductName())
                        .optionId(orderItem.getOptionId())
                        .optionName(orderItem.getOptionName())
                        .quantity(orderItem.getQuantity())
                        .restoredStock(newStock)
                        .build());
            }
        }

        // 2-3: 사용자 잔액 복구
        restoreUserBalance(userId, order.getFinalAmount());

        // 2-4: 쿠폰 상태 복구 (있는 경우)
        if (order.getCouponId() != null) {
            restoreCouponStatus(userId, order.getCouponId());
        }

        // 2-5: 주문 저장 (상태 변경 반영)
        Order savedOrder = orderRepository.save(order);

        // 2-6: 응답 반환
        return CancelOrderResponse.fromOrder(savedOrder, restoredItems);
    }

    /**
     * 사용자 잔액 복구
     *
     * 실제 DB 환경에서는:
     * UPDATE users SET balance = balance + final_amount WHERE user_id = ?
     */
    private void restoreUserBalance(Long userId, Long finalAmount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        long newBalance = user.getBalance() + finalAmount;
        user.setBalance(newBalance);
        // 저장소에 반영 (InMemory이므로 직접 수정)
    }

    /**
     * 쿠폰 상태 복구
     *
     * API 명세 (docs/api/api-specification.md 3.4):
     * - UPDATE user_coupons SET status = 'ACTIVE', used_at = NULL WHERE user_coupon_id = ?
     * - UPDATE coupons SET remaining_qty = remaining_qty + 1, version = version + 1
     *
     * 현재는 In-Memory 저장소이므로 실제 DB 쿼리는 인프라 계층에서 구현됨
     */
    private void restoreCouponStatus(Long userId, Long couponId) {
        // 실제 DB 환경에서는:
        // UPDATE user_coupons SET status = 'ACTIVE', used_at = NULL
        // WHERE user_id = ? AND coupon_id = ?
        //
        // UPDATE coupons SET remaining_qty = remaining_qty + 1, version = version + 1
        // WHERE coupon_id = ?
        System.out.println("[OrderCancelTransactionService] 쿠폰 상태 복구: userId=" + userId + ", couponId=" + couponId);
    }
}
