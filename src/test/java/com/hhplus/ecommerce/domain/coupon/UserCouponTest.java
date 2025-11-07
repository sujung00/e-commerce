package com.hhplus.ecommerce.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserCoupon 단위 테스트")
class UserCouponTest {

    @Test
    @DisplayName("사용자 쿠폰 생성 - 성공")
    void testUserCouponCreation() {
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status("ACTIVE")
                .issuedAt(now)
                .usedAt(null)
                .orderId(null)
                .build();

        assertThat(userCoupon.getUserCouponId()).isEqualTo(1L);
        assertThat(userCoupon.getUserId()).isEqualTo(100L);
        assertThat(userCoupon.getCouponId()).isEqualTo(1L);
        assertThat(userCoupon.getStatus()).isEqualTo("ACTIVE");
        assertThat(userCoupon.getIssuedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("사용자 쿠폰 사용 상태")
    void testUserCouponUsedStatus() {
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status("USED")
                .issuedAt(now)
                .usedAt(now.plusHours(1))
                .orderId(1L)
                .build();

        assertThat(userCoupon.getStatus()).isEqualTo("USED");
        assertThat(userCoupon.getUsedAt()).isNotNull();
        assertThat(userCoupon.getOrderId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("사용자 쿠폰 상태 변경")
    void testUserCouponStatusChange() {
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status("ACTIVE")
                .issuedAt(now)
                .usedAt(null)
                .build();

        userCoupon.setStatus("USED");
        userCoupon.setUsedAt(now.plusHours(1));
        userCoupon.setOrderId(1L);

        assertThat(userCoupon.getStatus()).isEqualTo("USED");
        assertThat(userCoupon.getUsedAt()).isNotNull();
        assertThat(userCoupon.getOrderId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("사용자 쿠폰 만료 상태")
    void testUserCouponExpiredStatus() {
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status("EXPIRED")
                .build();

        assertThat(userCoupon.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("사용자 쿠폰 발급 및 사용 시나리오")
    void testUserCouponIssueAndUseScenario() {
        LocalDateTime issuedTime = LocalDateTime.now();

        // 발급
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId(100L)
                .couponId(1L)
                .status("ACTIVE")
                .issuedAt(issuedTime)
                .build();

        assertThat(userCoupon.getStatus()).isEqualTo("ACTIVE");
        assertThat(userCoupon.getUsedAt()).isNull();

        // 사용
        LocalDateTime usedTime = issuedTime.plusHours(1);
        userCoupon.setStatus("USED");
        userCoupon.setUsedAt(usedTime);
        userCoupon.setOrderId(1L);

        assertThat(userCoupon.getStatus()).isEqualTo("USED");
        assertThat(userCoupon.getUsedAt()).isEqualTo(usedTime);
        assertThat(userCoupon.getOrderId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("사용자 쿠폰 null 필드")
    void testUserCouponNullFields() {
        UserCoupon userCoupon = UserCoupon.builder().build();

        assertThat(userCoupon.getUserCouponId()).isNull();
        assertThat(userCoupon.getUserId()).isNull();
        assertThat(userCoupon.getCouponId()).isNull();
        assertThat(userCoupon.getStatus()).isNull();
    }

    @Test
    @DisplayName("사용자 쿠폰 경계값 - 큰 ID")
    void testUserCouponBoundaryIds() {
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(Long.MAX_VALUE)
                .userId(Long.MAX_VALUE)
                .couponId(Long.MAX_VALUE)
                .orderId(Long.MAX_VALUE)
                .build();

        assertThat(userCoupon.getUserCouponId()).isEqualTo(Long.MAX_VALUE);
        assertThat(userCoupon.getUserId()).isEqualTo(Long.MAX_VALUE);
        assertThat(userCoupon.getCouponId()).isEqualTo(Long.MAX_VALUE);
        assertThat(userCoupon.getOrderId()).isEqualTo(Long.MAX_VALUE);
    }
}
