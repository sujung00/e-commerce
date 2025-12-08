package com.hhplus.ecommerce.domain.common.vo;

import java.io.Serializable;
import java.util.Objects;

/**
 * Money Value Object
 *
 * 금전 관련 금액을 나타내는 값 객체입니다.
 * 음수 금액을 허용하지 않으며, 모든 금액 계산은 이 객체를 통해 수행됩니다.
 *
 * 사용처:
 * - Order (subtotal, couponDiscount, finalAmount)
 * - OrderItem (unitPrice, subtotal)
 * - Coupon (discountAmount)
 * - User (balance)
 *
 * 특징:
 * - Immutable: 생성 후 변경 불가능
 * - 0 이상의 금액만 허용
 * - 연산 결과는 새로운 Money 객체 반환
 * - equals/hashCode 구현으로 값 비교 가능
 */
public final class Money implements Comparable<Money>, Serializable {
    private static final long serialVersionUID = 1L;

    private final long amount;

    // 특수 상수
    public static final Money ZERO = new Money(0L);

    /**
     * Money 객체를 생성합니다.
     *
     * @param amount 금액 (0 이상이어야 함)
     * @throws IllegalArgumentException amount < 0인 경우
     */
    public Money(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("금액은 음수가 될 수 없습니다: " + amount);
        }
        this.amount = amount;
    }

    /**
     * 금액을 반환합니다.
     */
    public long getAmount() {
        return amount;
    }

    /**
     * 두 금액을 더합니다.
     *
     * @param other 더할 금액
     * @return 더한 결과를 나타내는 새로운 Money 객체
     */
    public Money add(Money other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return new Money(this.amount + other.amount);
    }

    /**
     * 두 금액을 뺍니다.
     *
     * @param other 뺄 금액
     * @return 뺀 결과를 나타내는 새로운 Money 객체
     * @throws IllegalArgumentException 결과가 음수가 되는 경우
     */
    public Money subtract(Money other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        long result = this.amount - other.amount;
        if (result < 0) {
            throw new IllegalArgumentException(
                String.format("음수 금액이 될 수 없습니다: %d - %d = %d", this.amount, other.amount, result)
            );
        }
        return new Money(result);
    }

    /**
     * 금액에 배수를 곱합니다.
     *
     * @param multiplier 곱할 배수 (음수 불가)
     * @return 곱한 결과를 나타내는 새로운 Money 객체
     * @throws IllegalArgumentException multiplier < 0인 경우
     */
    public Money multiply(long multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("배수는 음수가 될 수 없습니다: " + multiplier);
        }
        return new Money(this.amount * multiplier);
    }

    /**
     * 금액에 배수(double)를 곱합니다.
     *
     * @param multiplier 곱할 배수 (0.0-1.0 범위, 보통 할인율)
     * @return 곱한 결과를 나타내는 새로운 Money 객체
     * @throws IllegalArgumentException multiplier < 0 또는 multiplier > 1인 경우
     */
    public Money multiply(double multiplier) {
        if (multiplier < 0.0 || multiplier > 1.0) {
            throw new IllegalArgumentException("배수는 0.0-1.0 범위여야 합니다: " + multiplier);
        }
        long result = (long) (this.amount * multiplier);
        return new Money(result);
    }

    /**
     * 현재 금액이 다른 금액 이상인지 확인합니다.
     *
     * @param other 비교할 금액
     * @return 현재 금액 >= other 금액이면 true
     */
    public boolean isGreaterThanOrEqual(Money other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return this.amount >= other.amount;
    }

    /**
     * 현재 금액이 다른 금액보다 큰지 확인합니다.
     *
     * @param other 비교할 금액
     * @return 현재 금액 > other 금액이면 true
     */
    public boolean isGreaterThan(Money other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return this.amount > other.amount;
    }

    /**
     * 현재 금액이 다른 금액 이하인지 확인합니다.
     *
     * @param other 비교할 금액
     * @return 현재 금액 <= other 금액이면 true
     */
    public boolean isLessThanOrEqual(Money other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return this.amount <= other.amount;
    }

    /**
     * 현재 금액이 다른 금액보다 작은지 확인합니다.
     *
     * @param other 비교할 금액
     * @return 현재 금액 < other 금액이면 true
     */
    public boolean isLessThan(Money other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return this.amount < other.amount;
    }

    /**
     * 현재 금액이 0인지 확인합니다.
     */
    public boolean isZero() {
        return this.amount == 0;
    }

    /**
     * 현재 금액이 양수인지 확인합니다.
     */
    public boolean isPositive() {
        return this.amount > 0;
    }

    @Override
    public int compareTo(Money other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return Long.compare(this.amount, other.amount);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Money)) {
            return false;
        }
        Money other = (Money) obj;
        return this.amount == other.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return String.format("Money(%,d)", amount);
    }
}
