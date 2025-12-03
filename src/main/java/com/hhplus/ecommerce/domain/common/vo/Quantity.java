package com.hhplus.ecommerce.domain.common.vo;

import java.io.Serializable;
import java.util.Objects;

/**
 * Quantity Value Object
 *
 * 수량 관련 정보를 나타내는 값 객체입니다.
 * 양의 정수만 허용하며, 모든 수량 관련 계산은 이 객체를 통해 수행됩니다.
 *
 * 사용처:
 * - OrderItem (quantity)
 * - ProductOption (stock)
 * - Coupon (remainingQty, totalQuantity)
 * - Cart (totalItems)
 *
 * 특징:
 * - Immutable: 생성 후 변경 불가능
 * - 1 이상의 수량만 허용 (기본 규칙)
 * - 0 이상의 수량 허용 (재고의 경우)
 * - 연산 결과는 새로운 Quantity 객체 반환
 * - equals/hashCode 구현으로 값 비교 가능
 */
public final class Quantity implements Comparable<Quantity>, Serializable {
    private static final long serialVersionUID = 1L;

    private final int quantity;

    // 특수 상수
    public static final Quantity ZERO = new Quantity(0);
    public static final Quantity ONE = new Quantity(1);
    public static final Quantity MIN = new Quantity(1);  // 기본 최소값

    /**
     * Quantity 객체를 생성합니다 (최소 1).
     *
     * @param quantity 수량 (1 이상이어야 함)
     * @throws IllegalArgumentException quantity < 1인 경우
     */
    public Quantity(int quantity) {
        this(quantity, true);  // 기본적으로 최소값 1 검증
    }

    /**
     * Quantity 객체를 생성합니다 (유연한 최소값 검증).
     *
     * @param quantity 수량
     * @param requirePositive true면 quantity >= 1, false면 quantity >= 0
     * @throws IllegalArgumentException 검증 실패시
     */
    public Quantity(int quantity, boolean requirePositive) {
        if (requirePositive && quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다: " + quantity);
        }
        if (!requirePositive && quantity < 0) {
            throw new IllegalArgumentException("수량은 0 이상이어야 합니다: " + quantity);
        }
        this.quantity = quantity;
    }

    /**
     * 수량을 반환합니다.
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * 현재 수량에 다른 수량을 더합니다.
     *
     * @param other 더할 수량
     * @return 더한 결과를 나타내는 새로운 Quantity 객체
     */
    public Quantity add(Quantity other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return new Quantity(this.quantity + other.quantity, false);  // 합산 결과는 0 허용
    }

    /**
     * 현재 수량에서 다른 수량을 뺍니다.
     *
     * @param other 뺄 수량
     * @return 뺀 결과를 나타내는 새로운 Quantity 객체
     * @throws IllegalArgumentException 결과가 음수가 되는 경우
     */
    public Quantity subtract(Quantity other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        int result = this.quantity - other.quantity;
        if (result < 0) {
            throw new IllegalArgumentException(
                String.format("음수 수량이 될 수 없습니다: %d - %d = %d", this.quantity, other.quantity, result)
            );
        }
        return new Quantity(result, false);  // 결과는 0 허용
    }

    /**
     * 현재 수량에 배수를 곱합니다.
     *
     * @param multiplier 곱할 배수 (음수 불가)
     * @return 곱한 결과를 나타내는 새로운 Quantity 객체
     * @throws IllegalArgumentException multiplier < 0인 경우
     */
    public Quantity multiply(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("배수는 음수가 될 수 없습니다: " + multiplier);
        }
        return new Quantity(this.quantity * multiplier, false);  // 결과는 0 허용
    }

    /**
     * 현재 수량이 다른 수량 이상인지 확인합니다.
     *
     * @param other 비교할 수량
     * @return 현재 수량 >= other 수량이면 true
     */
    public boolean isGreaterThanOrEqual(Quantity other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return this.quantity >= other.quantity;
    }

    /**
     * 현재 수량이 다른 수량보다 큰지 확인합니다.
     *
     * @param other 비교할 수량
     * @return 현재 수량 > other 수량이면 true
     */
    public boolean isGreaterThan(Quantity other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return this.quantity > other.quantity;
    }

    /**
     * 현재 수량이 다른 수량 이하인지 확인합니다.
     *
     * @param other 비교할 수량
     * @return 현재 수량 <= other 수량이면 true
     */
    public boolean isLessThanOrEqual(Quantity other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return this.quantity <= other.quantity;
    }

    /**
     * 현재 수량이 다른 수량보다 작은지 확인합니다.
     *
     * @param other 비교할 수량
     * @return 현재 수량 < other 수량이면 true
     */
    public boolean isLessThan(Quantity other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return this.quantity < other.quantity;
    }

    /**
     * 현재 수량이 0인지 확인합니다.
     */
    public boolean isZero() {
        return this.quantity == 0;
    }

    /**
     * 현재 수량이 1인지 확인합니다.
     */
    public boolean isOne() {
        return this.quantity == 1;
    }

    /**
     * 현재 수량이 양수인지 확인합니다.
     */
    public boolean isPositive() {
        return this.quantity > 0;
    }

    /**
     * 현재 수량이 충분한지 확인합니다 (>= other).
     *
     * @param required 필요한 수량
     * @return 현재 수량 >= 필요 수량이면 true
     */
    public boolean isSufficientFor(Quantity required) {
        Objects.requireNonNull(required, "required는 null이 될 수 없습니다");
        return this.quantity >= required.quantity;
    }

    @Override
    public int compareTo(Quantity other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return Integer.compare(this.quantity, other.quantity);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Quantity)) {
            return false;
        }
        Quantity other = (Quantity) obj;
        return this.quantity == other.quantity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(quantity);
    }

    @Override
    public String toString() {
        return String.format("Quantity(%d)", quantity);
    }
}
