package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.application.order.dto.CancelOrderResponse;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@Service
public class OrderCancelTransactionService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserCouponRepository userCouponRepository;

    public OrderCancelTransactionService(OrderRepository orderRepository,
                                        ProductRepository productRepository,
                                        UserRepository userRepository,
                                        UserCouponRepository userCouponRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.userCouponRepository = userCouponRepository;
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
        // 2-1: 주문 상태 변경 (Domain 메서드 활용)
        // Order.cancel() 메서드가 다음을 처리합니다:
        // - 주문 상태를 CANCELLED로 변경
        // - 업데이트 시간 자동 설정
        order.cancel();

        // 2-2: 재고 복구 (Domain 메서드 활용)
        List<CancelOrderResponse.RestoredItem> restoredItems = new ArrayList<>();
        for (var orderItem : order.getOrderItems()) {
            Product product = productRepository.findById(orderItem.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(orderItem.getProductId()));

            // Domain 메서드 호출 (Product가 내부적으로 ProductOption 조회 및 재고 복구)
            // 예외 처리: ProductOptionNotFoundException
            product.restoreStock(orderItem.getOptionId(), orderItem.getQuantity());

            // 저장소에 반영
            productRepository.save(product);

            // 복구된 항목 정보 추가
            // restoreStock() 호출 후 ProductOption의 재고를 조회해서 추가
            ProductOption option = product.getOptions().stream()
                    .filter(o -> o.getOptionId().equals(orderItem.getOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("옵션을 찾을 수 없습니다"));

            restoredItems.add(CancelOrderResponse.RestoredItem.builder()
                    .orderItemId(orderItem.getOrderItemId())
                    .productId(orderItem.getProductId())
                    .productName(orderItem.getProductName())
                    .optionId(orderItem.getOptionId())
                    .optionName(orderItem.getOptionName())
                    .quantity(orderItem.getQuantity())
                    .restoredStock(option.getStock())
                    .build());
        }

        // 2-3: 사용자 잔액 복구
        restoreUserBalance(userId, order.getFinalAmount());

        // 2-4: 쿠폰 상태 복구 (있는 경우)
        if (order.getCouponId() != null) {
            restoreCouponStatus(userId, order.getCouponId());
        }

        // 2-5: 주문 저장 (상태 변경 반영)
        Order savedOrder = orderRepository.save(order);

        // 2-6: 응답 반환 (Application layer DTO로 변환)
        return CancelOrderResponse.builder()
                .orderId(savedOrder.getOrderId())
                .orderStatus(savedOrder.getOrderStatus().name())
                .refundAmount(savedOrder.getFinalAmount())
                .cancelledAt(savedOrder.getCancelledAt())
                .restoredItems(restoredItems)
                .build();
    }

    /**
     * 사용자 잔액 복구 (Domain 메서드 활용)
     *
     * User.refundBalance() 메서드가 다음을 처리합니다:
     * - 잔액 복구 (추가)
     * - 업데이트된 사용자 반환
     */
    private void restoreUserBalance(Long userId, Long finalAmount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Domain 메서드 호출 (User가 잔액 환불/복구)
        user.refundBalance(finalAmount);

        // 저장소에 반영
        userRepository.save(user);
    }

    /**
     * 쿠폰 상태 복구
     *
     * 변경 사항 (2025-11-18):
     * - USER_COUPONS.order_id 삭제로 인한 새로운 쿠폰 추적 방식
     * - 쿠폰 사용 여부는 ORDERS.coupon_id의 존재 여부로만 추적
     * - USER_COUPONS은 "쿠폰 보유 상태"(UNUSED/USED/EXPIRED/CANCELLED)만 관리
     *
     * 복구 로직 흐름:
     * 1. 해당 couponId가 현재 다른 활성 주문에서 사용 중인지 확인
     *    - 사용 중이면: 현재 주문의 취소로 인해 다른 주문이 영향을 받지 않으므로 복구하지 않음
     *    - 미사용 상태면: UserCoupon을 UNUSED로 복구
     * 2. UserCoupon 엔티티 조회
     *    - 없으면 경고만 로깅 (업데이트하지 않음)
     *    - 있으면: UserCoupon.status = UNUSED로 설정하고 저장
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     */
    private void restoreCouponStatus(Long userId, Long couponId) {
        // (1) 해당 couponId가 현재 다른 활성 주문에서 사용 중인지 확인
        if (orderRepository.existsActiveByCouponId(couponId)) {
            log.info("[OrderCancelTransactionService] 쿠폰이 다른 활성 주문에서 사용 중이므로 복구하지 않음: userId={}, couponId={}",
                     userId, couponId);
            return;
        }

        // (2) UserCoupon 엔티티 조회 및 상태 복구
        var userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);

        if (userCoupon.isEmpty()) {
            log.warn("[OrderCancelTransactionService] UserCoupon을 찾을 수 없음 (이미 삭제되었을 수 있음): userId={}, couponId={}",
                     userId, couponId);
            return;
        }

        // (3) UserCoupon 상태를 UNUSED로 변경
        UserCoupon coupon = userCoupon.get();
        coupon.setStatus(UserCouponStatus.UNUSED);
        coupon.setUsedAt(null);  // 사용 시각 초기화

        userCouponRepository.save(coupon);

        log.info("[OrderCancelTransactionService] 쿠폰 상태를 UNUSED로 복구: userId={}, couponId={}",
                 userId, couponId);
    }
}
