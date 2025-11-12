package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OrderCalculator - 주문 관련 비즈니스 계산 로직 전담
 *
 * 책임:
 * - 상품 가격 기반 소계 계산
 * - 쿠폰 할인금 계산
 * - 최종 결제액 계산
 *
 * 설계 원칙:
 * - 순수한 계산 로직만 포함 (부수 효과 없음)
 * - 상태 변경 없음 (읽기 전용)
 * - OrderService에서 의존성 주입받음
 * - 계산식을 한 곳에 집중시켜 비즈니스 로직 일관성 보장
 *
 * 주요 설계:
 * - 쿠폰 할인금은 하드코딩 (실제로는 쿠폰 엔티티에서 조회)
 * - 계산 로직의 변경은 이 클래스에서만 관리
 * - 다양한 프로모션 추가 시 확장 용이한 구조
 */
@Component
public class OrderCalculator {

    private final ProductRepository productRepository;

    /** 쿠폰 기본 할인금 (하드코딩) */
    private static final long DEFAULT_COUPON_DISCOUNT = 5000L;

    public OrderCalculator(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 주문 항목들의 소계 계산
     *
     * 알고리즘:
     * 소계 = Σ(각 상품의 가격 × 수량)
     *
     * @param orderItems 주문 항목 목록
     * @return 소계 금액
     * @throws ProductNotFoundException 상품을 찾을 수 없음
     */
    public long calculateSubtotal(List<OrderItemCommand> orderItems) {
        long subtotal = 0;
        for (OrderItemCommand item : orderItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
            subtotal += product.getPrice() * item.getQuantity();
        }
        return subtotal;
    }

    /**
     * 쿠폰 할인금 계산
     *
     * 현재 구현:
     * - 쿠폰이 있으면 DEFAULT_COUPON_DISCOUNT (5,000원)
     * - 쿠폰이 없으면 0원
     *
     * 향후 개선:
     * - 쿠폰 엔티티에서 할인율/할인금 조회
     * - 복수 쿠폰 적용 로직
     * - 쿠폰별 최소 구매액 검증
     *
     * @param couponId 쿠폰 ID (null이면 쿠폰 미적용)
     * @return 할인금액
     */
    public long calculateCouponDiscount(Long couponId) {
        return couponId != null ? DEFAULT_COUPON_DISCOUNT : 0L;
    }

    /**
     * 최종 결제액 계산
     *
     * 알고리즘:
     * 최종금액 = 소계 - 쿠폰할인금
     *
     * @param subtotal 소계 금액
     * @param couponId 쿠폰 ID
     * @return 최종 결제액 (음수 방지: 최소값 0)
     */
    public long calculateFinalAmount(long subtotal, Long couponId) {
        long couponDiscount = calculateCouponDiscount(couponId);
        return Math.max(0, subtotal - couponDiscount);
    }

    /**
     * 소계와 할인금을 한번에 계산 (성능 최적화)
     *
     * 주문 생성 시 소계와 할인금을 모두 필요로 하므로
     * 한 번의 계산으로 두 값을 반환하도록 최적화
     *
     * @param orderItems 주문 항목 목록
     * @param couponId 쿠폰 ID
     * @return [소계, 할인금, 최종금액] 배열
     */
    public long[] calculatePrices(List<OrderItemCommand> orderItems, Long couponId) {
        long subtotal = calculateSubtotal(orderItems);
        long couponDiscount = calculateCouponDiscount(couponId);
        long finalAmount = subtotal - couponDiscount;

        return new long[]{subtotal, couponDiscount, Math.max(0, finalAmount)};
    }
}