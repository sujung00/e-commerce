package com.hhplus.ecommerce.domain.user;

import lombok.*;

import java.time.LocalDateTime;

/**
 * User 도메인 엔티티 (Rich Domain Model)
 *
 * 책임:
 * - 사용자의 계정 정보 관리
 * - 잔액(충전액) 관리 및 출금 로직
 * - 잔액 유효성 검증
 *
 * 핵심 비즈니스 규칙:
 * - 잔액은 음수가 될 수 없음 (>= 0)
 * - 출금 시 충분한 잔액 필요
 * - 충전 시 음수 금액 불가능
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long userId;
    private String email;
    private String passwordHash;
    private String name;
    private String phone;
    private Long balance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 사용자 생성 팩토리 메서드
     *
     * 비즈니스 규칙:
     * - 신규 사용자는 초기 잔액 0으로 시작
     * - 이메일은 필수
     */
    public static User createUser(String email, String passwordHash, String name, String phone) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }

        return User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .name(name)
                .phone(phone)
                .balance(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 잔액 충전
     *
     * 비즈니스 규칙:
     * - 충전액은 0보다 커야 함
     * - 성공 시 updatedAt 갱신
     *
     * @param amount 충전할 금액
     * @throws IllegalArgumentException 잔액이 음수
     */
    public void chargeBalance(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전액은 0보다 커야 합니다");
        }
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 잔액 출금 (결제)
     *
     * 비즈니스 규칙:
     * - 출금액은 0보다 커야 함
     * - 현재 잔액 >= 출금액
     * - 성공 시 updatedAt 갱신
     *
     * @param amount 출금할 금액
     * @throws InsufficientBalanceException 잔액 부족
     * @throws IllegalArgumentException 금액이 0 이하
     */
    public void deductBalance(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("출금액은 0보다 커야 합니다");
        }
        if (this.balance < amount) {
            throw new InsufficientBalanceException(this.userId, this.balance, amount);
        }
        this.balance -= amount;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 잔액 환불 (주문 취소 시)
     *
     * 비즈니스 규칙:
     * - 환불액은 0보다 커야 함
     * - 환불 = 충전과 동일 로직
     *
     * @param amount 환불할 금액
     */
    public void refundBalance(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("환불액은 0보다 커야 합니다");
        }
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 특정 금액을 출금할 수 있는지 확인
     */
    public boolean canDeduct(Long amount) {
        return this.balance >= amount;
    }

    /**
     * 잔액이 충분한지 확인
     */
    public boolean hasSufficientBalance(Long amount) {
        return this.balance >= amount;
    }

    /**
     * 현재 보유 잔액
     */
    public Long getCurrentBalance() {
        return this.balance;
    }

    /**
     * 잔액이 0인지 확인
     */
    public boolean hasNoBalance() {
        return this.balance == 0;
    }

    /**
     * 계정 정보 갱신
     */
    public void updateUserInfo(String name, String phone) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (phone != null && !phone.isBlank()) {
            this.phone = phone;
        }
        this.updatedAt = LocalDateTime.now();
    }
}
