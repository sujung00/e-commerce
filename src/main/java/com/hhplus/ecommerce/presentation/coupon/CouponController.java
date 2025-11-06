package com.hhplus.ecommerce.presentation.coupon;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.presentation.coupon.request.IssueCouponRequest;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetAvailableCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetUserCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CouponController - Presentation 계층
 *
 * 역할:
 * - HTTP 요청 처리
 * - 요청 헤더에서 userId 추출
 * - 응답 DTO 생성
 *
 * API 명세:
 * - 4.1 POST /coupons/issue (쿠폰 발급)
 * - 4.2 GET /coupons/issued (사용자 쿠폰 조회)
 * - 4.3 GET /coupons (발급 가능한 쿠폰 조회)
 */
@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * 4.1 쿠폰 발급 (선착순)
     * POST /api/coupons/issue
     *
     * @param userId X-USER-ID 헤더 (사용자 ID)
     * @param request 쿠폰 발급 요청 (coupon_id)
     * @return 발급된 쿠폰 정보 (201 Created)
     */
    @PostMapping("/issue")
    public ResponseEntity<IssueCouponResponse> issueCoupon(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody IssueCouponRequest request) {
        IssueCouponResponse response = couponService.issueCoupon(userId, request.getCouponId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 4.2 사용자가 보유한 쿠폰 조회
     * GET /api/coupons/issued?status=ACTIVE
     *
     * @param userId X-USER-ID 헤더 (사용자 ID)
     * @param status 쿠폰 상태 (ACTIVE | USED | EXPIRED), 기본값: "ACTIVE"
     * @return 사용자 쿠폰 목록 (200 OK)
     */
    @GetMapping("/issued")
    public ResponseEntity<GetUserCouponsResponse> getUserCoupons(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        List<UserCouponResponse> userCoupons = couponService.getUserCoupons(userId, status);
        GetUserCouponsResponse response = GetUserCouponsResponse.builder()
                .userCoupons(userCoupons)
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * 4.3 사용 가능한 쿠폰 조회
     * GET /api/coupons
     *
     * @return 발급 가능한 쿠폰 목록 (200 OK)
     */
    @GetMapping
    public ResponseEntity<GetAvailableCouponsResponse> getAvailableCoupons() {
        List<AvailableCouponResponse> coupons = couponService.getAvailableCoupons();
        GetAvailableCouponsResponse response = GetAvailableCouponsResponse.builder()
                .coupons(coupons)
                .build();
        return ResponseEntity.ok(response);
    }
}
