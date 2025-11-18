package com.hhplus.ecommerce.unit.domain.coupon;


import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CouponNotFoundException 단위 테스트")
class CouponNotFoundExceptionTest {

    @Test
    @DisplayName("CouponNotFoundException 생성 및 메시지 검증")
    void testCouponNotFoundExceptionWithId() {
        Long couponId = 999L;
        CouponNotFoundException exception = new CouponNotFoundException(couponId);

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).contains("쿠폰을 찾을 수 없습니다");
        assertThat(exception.getCouponId()).isEqualTo(999L);
        assertThat(exception.getErrorCode()).isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    @DisplayName("CouponNotFoundException 다양한 ID 값")
    void testCouponNotFoundExceptionDifferentIds() {
        Long id1 = 1L;
        Long id2 = 100L;
        Long id3 = Long.MAX_VALUE;

        CouponNotFoundException exception1 = new CouponNotFoundException(id1);
        CouponNotFoundException exception2 = new CouponNotFoundException(id2);
        CouponNotFoundException exception3 = new CouponNotFoundException(id3);

        assertThat(exception1.getCouponId()).isEqualTo(1L);
        assertThat(exception2.getCouponId()).isEqualTo(100L);
        assertThat(exception3.getCouponId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("CouponNotFoundException 에러 코드")
    void testCouponNotFoundExceptionErrorCode() {
        CouponNotFoundException exception = new CouponNotFoundException(1L);

        assertThat(exception.getErrorCode()).isEqualTo("COUPON_NOT_FOUND");
    }
}
