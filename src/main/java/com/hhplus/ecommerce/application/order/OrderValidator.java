package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OrderValidator - 주문 관련 검증 로직 전담
 *
 * 책임:
 * - 상품 존재 여부 검증
 * - 옵션 존재 및 재고 검증
 * - 사용자 잔액 검증
 * - 주문 상태 검증
 *
 * 설계 원칙:
 * - 유효성 검증만 담당 (부수 효과 없음)
 * - 예외 발생으로 검증 실패 표현
 * - OrderService에서 의존성 주입받음
 * - Repository는 주입받아 데이터 조회만 수행
 */
@Component
public class OrderValidator {

    private final ProductRepository productRepository;
    private final UserCouponRepository userCouponRepository;
    private final OrderRepository orderRepository;

    public OrderValidator(ProductRepository productRepository,
                         UserCouponRepository userCouponRepository,
                         OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.userCouponRepository = userCouponRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 주문 항목들의 모든 검증 수행
     *
     * 검증 항목:
     * 1. 각 상품의 존재 여부
     * 2. 각 옵션의 존재 여부
     * 3. 각 옵션의 재고 충분성
     * 4. 사용자 잔액 충분성
     *
     * @param user 주문자 정보
     * @param orderItems 주문 항목 목록
     * @param finalAmount 최종 결제액
     * @throws ProductNotFoundException 상품을 찾을 수 없음
     * @throws IllegalArgumentException 옵션/재고/잔액 부족
     */
    public void validateOrder(User user, List<OrderItemCommand> orderItems, long finalAmount) {
        // 각 주문 항목에 대해 검증 수행
        for (OrderItemCommand itemCommand : orderItems) {
            validateProductAndOption(itemCommand);
            validateStockAvailable(itemCommand);
        }

        // 사용자 잔액 검증
        validateUserBalance(user, finalAmount);
    }

    /**
     * 상품과 옵션 존재 여부 검증
     *
     * @param itemCommand 주문 항목 커맨드
     * @throws ProductNotFoundException 상품을 찾을 수 없음
     * @throws IllegalArgumentException 옵션을 찾을 수 없음
     */
    private void validateProductAndOption(OrderItemCommand itemCommand) {
        Product product = productRepository.findById(itemCommand.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(itemCommand.getProductId()));

        // 옵션 존재 여부 확인
        product.getOptions().stream()
                .filter(o -> o.getOptionId().equals(itemCommand.getOptionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("옵션을 찾을 수 없습니다"));
    }

    /**
     * 재고 충분성 검증
     *
     * @param itemCommand 주문 항목 커맨드
     * @throws ProductNotFoundException 상품을 찾을 수 없음
     * @throws IllegalArgumentException 재고 부족
     */
    private void validateStockAvailable(OrderItemCommand itemCommand) {
        Product product = productRepository.findById(itemCommand.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(itemCommand.getProductId()));

        // 옵션의 재고 확인
        int availableStock = product.getOptions().stream()
                .filter(o -> o.getOptionId().equals(itemCommand.getOptionId()))
                .mapToInt(ProductOption::getStock)
                .sum();

        if (availableStock < itemCommand.getQuantity()) {
            String optionName = product.getOptions().stream()
                    .filter(o -> o.getOptionId().equals(itemCommand.getOptionId()))
                    .map(ProductOption::getName)
                    .findFirst()
                    .orElse("알 수 없는 옵션");
            throw new IllegalArgumentException(optionName + "의 재고가 부족합니다");
        }
    }

    /**
     * 사용자 잔액 검증
     *
     * @param user 주문자
     * @param finalAmount 최종 결제액
     * @throws IllegalArgumentException 잔액 부족
     */
    private void validateUserBalance(User user, long finalAmount) {
        if (user.getBalance() < finalAmount) {
            throw new IllegalArgumentException("잔액이 부족합니다");
        }
    }

    /**
     * 주문 상태 검증 (취소 가능 여부)
     *
     * @param order 주문 엔티티
     * @throws IllegalArgumentException 취소 불가능한 상태
     */
    public void validateOrderStatus(Order order) {
        if (!"COMPLETED".equals(order.getOrderStatus())) {
            throw new IllegalArgumentException(
                    "취소할 수 없는 주문 상태입니다: " + order.getOrderStatus());
        }
    }

    /**
     * 쿠폰 소유 및 사용 가능 여부 검증
     *
     * 변경 사항 (2025-11-18):
     * - USER_COUPONS.order_id 삭제로 인한 새로운 검증 방식
     * - 쿠폰 사용 여부는 ORDERS.coupon_id로 추적
     *
     * 검증 항목:
     * 1. 사용자가 쿠폰을 보유하고 있는가? (user_coupons에서 확인)
     * 2. 쿠폰의 상태가 미사용인가? (user_coupons.status = 'UNUSED')
     * 3. 쿠폰이 이미 다른 주문에 사용 중인가? (orders.coupon_id에서 확인)
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID (null이면 쿠폰 미사용)
     * @throws IllegalArgumentException 쿠폰을 찾을 수 없거나, 상태가 유효하지 않음
     */
    public void validateCouponOwnershipAndUsage(Long userId, Long couponId) {
        if (couponId == null) {
            // 쿠폰을 사용하지 않는 경우 검증 스킵
            return;
        }

        // 1. 사용자가 쿠폰을 보유하고 있는지 확인
        var userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "사용자가 쿠폰을 보유하고 있지 않습니다: couponId=" + couponId));

        // 2. 쿠폰 상태가 UNUSED인지 확인 (이미 사용되었으면 실패)
        if (!"UNUSED".equals(userCoupon.getStatus().name())) {
            throw new IllegalArgumentException(
                    "쿠폰을 사용할 수 없습니다: 상태=" + userCoupon.getStatus());
        }

        // 3. orders 테이블에서 쿠폰이 이미 사용 중인지 확인
        if (orderRepository.existsOrderWithCoupon(userId, couponId)) {
            throw new IllegalArgumentException(
                    "이 쿠폰은 이미 다른 주문에 사용 중입니다");
        }
    }
}
