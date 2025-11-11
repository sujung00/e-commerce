package com.hhplus.ecommerce.domain.user;

import lombok.*;

import java.time.LocalDateTime;

/**
 * User 도메인 엔티티
 * 사용자 계정 정보 및 충전 잔액 관리
 */
@Getter
@Setter
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
}
