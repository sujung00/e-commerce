package com.hhplus.ecommerce.domain.common.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * ValidityPeriod Value Object
 *
 * 유효 기간(시작일~종료일)을 나타내는 값 객체입니다.
 * 쿠폰, 프로모션, 이벤트 등 시간 범위가 필요한 도메인에서 사용합니다.
 *
 * 사용처:
 * - Coupon (validFrom, validUntil)
 * - 향후 Promotion, Event 등
 *
 * 특징:
 * - Immutable: 생성 후 변경 불가능
 * - validFrom <= validUntil 검증
 * - 특정 시점이 유효 기간 내인지 확인 가능
 * - 기간 길이 계산 가능
 * - equals/hashCode 구현으로 값 비교 가능
 */
public final class ValidityPeriod implements Serializable {
    private static final long serialVersionUID = 1L;

    private final LocalDateTime validFrom;
    private final LocalDateTime validUntil;

    /**
     * ValidityPeriod 객체를 생성합니다.
     *
     * @param validFrom 시작 시점 (non-null)
     * @param validUntil 종료 시점 (non-null)
     * @throws IllegalArgumentException validFrom > validUntil인 경우
     * @throws NullPointerException 인자가 null인 경우
     */
    public ValidityPeriod(LocalDateTime validFrom, LocalDateTime validUntil) {
        Objects.requireNonNull(validFrom, "validFrom은 null이 될 수 없습니다");
        Objects.requireNonNull(validUntil, "validUntil은 null이 될 수 없습니다");

        if (validFrom.isAfter(validUntil)) {
            throw new IllegalArgumentException(
                String.format("시작 시점은 종료 시점 이전이어야 합니다: %s ~ %s", validFrom, validUntil)
            );
        }

        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    /**
     * 시작 시점을 반환합니다.
     */
    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    /**
     * 종료 시점을 반환합니다.
     */
    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    /**
     * 특정 시점이 유효 기간 내인지 확인합니다 (포함형).
     *
     * @param dateTime 확인할 시점
     * @return validFrom <= dateTime <= validUntil이면 true
     */
    public boolean contains(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime은 null이 될 수 없습니다");
        return !dateTime.isBefore(validFrom) && !dateTime.isAfter(validUntil);
    }

    /**
     * 특정 시점이 유효 기간 내인지 확인합니다 (배타적 포함형).
     * 종료 시점은 포함하지 않습니다.
     *
     * @param dateTime 확인할 시점
     * @return validFrom <= dateTime < validUntil이면 true
     */
    public boolean containsExclusive(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime은 null이 될 수 없습니다");
        return !dateTime.isBefore(validFrom) && dateTime.isBefore(validUntil);
    }

    /**
     * 현재 시점(LocalDateTime.now())이 유효 기간 내인지 확인합니다.
     *
     * @return 현재 시점이 유효 기간 내면 true
     */
    public boolean isValid() {
        return contains(LocalDateTime.now());
    }

    /**
     * 현재 시점(LocalDateTime.now())이 유효 기간 내인지 확인합니다 (배타적).
     * 종료 시점은 포함하지 않습니다.
     *
     * @return 현재 시점이 유효 기간 내(배타적)면 true
     */
    public boolean isValidExclusive() {
        return containsExclusive(LocalDateTime.now());
    }

    /**
     * 유효 기간이 시작되었는지 확인합니다.
     * (현재 시점이 시작 시점 이후)
     *
     * @return 유효 기간이 시작되었으면 true
     */
    public boolean hasStarted() {
        return !LocalDateTime.now().isBefore(validFrom);
    }

    /**
     * 유효 기간이 종료되었는지 확인합니다.
     * (현재 시점이 종료 시점 이후)
     *
     * @return 유효 기간이 종료되었으면 true
     */
    public boolean hasExpired() {
        return LocalDateTime.now().isAfter(validUntil);
    }

    /**
     * 유효 기간이 진행 중인지 확인합니다.
     * (시작됨 AND 종료되지 않음)
     *
     * @return 진행 중이면 true
     */
    public boolean isInProgress() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(validFrom) && !now.isAfter(validUntil);
    }

    /**
     * 다른 ValidityPeriod와 겹치는지 확인합니다.
     *
     * @param other 다른 유효 기간
     * @return 겹치는 부분이 있으면 true
     */
    public boolean overlaps(ValidityPeriod other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return !this.validUntil.isBefore(other.validFrom) &&
               !other.validUntil.isBefore(this.validFrom);
    }

    /**
     * 다른 ValidityPeriod를 포함하는지 확인합니다.
     *
     * @param other 포함되는지 확인할 유효 기간
     * @return 다른 기간을 완전히 포함하면 true
     */
    public boolean encloses(ValidityPeriod other) {
        Objects.requireNonNull(other, "other는 null이 될 수 없습니다");
        return !this.validFrom.isAfter(other.validFrom) &&
               !this.validUntil.isBefore(other.validUntil);
    }

    /**
     * 유효 기간의 길이를 일(day) 단위로 반환합니다.
     *
     * @return 기간의 일 단위 길이
     */
    public long getDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(validFrom, validUntil);
    }

    /**
     * 유효 기간의 길이를 시간(hour) 단위로 반환합니다.
     *
     * @return 기간의 시간 단위 길이
     */
    public long getHours() {
        return java.time.temporal.ChronoUnit.HOURS.between(validFrom, validUntil);
    }

    /**
     * 유효 기간의 길이를 분(minute) 단위로 반환합니다.
     *
     * @return 기간의 분 단위 길이
     */
    public long getMinutes() {
        return java.time.temporal.ChronoUnit.MINUTES.between(validFrom, validUntil);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ValidityPeriod)) {
            return false;
        }
        ValidityPeriod other = (ValidityPeriod) obj;
        return this.validFrom.equals(other.validFrom) &&
               this.validUntil.equals(other.validUntil);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validFrom, validUntil);
    }

    @Override
    public String toString() {
        return String.format("ValidityPeriod(%s ~ %s)", validFrom, validUntil);
    }
}
