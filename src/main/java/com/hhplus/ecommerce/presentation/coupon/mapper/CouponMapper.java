package com.hhplus.ecommerce.presentation.coupon.mapper;

import com.hhplus.ecommerce.presentation.coupon.request.IssueCouponRequest;
import com.hhplus.ecommerce.application.coupon.dto.IssueCouponCommand;
import com.hhplus.ecommerce.application.coupon.dto.IssueCouponResponse;
import com.hhplus.ecommerce.application.coupon.dto.AvailableCouponResponse;
import com.hhplus.ecommerce.application.coupon.dto.UserCouponResponse;
import com.hhplus.ecommerce.application.coupon.dto.GetAvailableCouponsResponse;
import com.hhplus.ecommerce.application.coupon.dto.GetUserCouponsResponse;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * CouponMapper - Presentation layer와 Application layer 간의 DTO 변환
 *
 * 책임:
 * - Presentation Request DTO → Application Command 변환
 * - Application Response DTO → Presentation Response DTO 변환
 *
 * 아키텍처 원칙:
 * - Application layer는 Presentation layer DTO에 독립적 (자체 DTO 사용)
 * - Presentation layer의 @JsonProperty 같은 직렬화 로직은 이곳에서만 처리
 * - 각 계층이 자신의 DTO를 소유하고 관리하여 계층 간 의존성 제거
 */
@Component
public class CouponMapper {

    /**
     * IssueCouponRequest → IssueCouponCommand로 변환
     */
    public IssueCouponCommand toIssueCouponCommand(IssueCouponRequest request) {
        return IssueCouponCommand.builder()
                .couponId(request.getCouponId())
                .build();
    }

    /**
     * Application IssueCouponResponse → Presentation IssueCouponResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse toIssueCouponResponse(IssueCouponResponse response) {
        return com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse.builder()
                .userCouponId(response.getUserCouponId())
                .userId(response.getUserId())
                .couponId(response.getCouponId())
                .couponName(response.getCouponName())
                .discountType(response.getDiscountType())
                .discountAmount(response.getDiscountAmount())
                .discountRate(response.getDiscountRate())
                .status(response.getStatus())
                .issuedAt(response.getIssuedAt())
                .validFrom(response.getValidFrom())
                .validUntil(response.getValidUntil())
                .build();
    }

    /**
     * Application GetAvailableCouponsResponse → Presentation GetAvailableCouponsResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.coupon.response.GetAvailableCouponsResponse toGetAvailableCouponsResponse(GetAvailableCouponsResponse response) {
        return com.hhplus.ecommerce.presentation.coupon.response.GetAvailableCouponsResponse.builder()
                .coupons(response.getCoupons().stream()
                        .map(this::toAvailableCouponResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Application AvailableCouponResponse → Presentation AvailableCouponResponse로 변환
     */
    private com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse toAvailableCouponResponse(AvailableCouponResponse response) {
        return com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse.builder()
                .couponId(response.getCouponId())
                .couponName(response.getCouponName())
                .discountType(response.getDiscountType())
                .discountAmount(response.getDiscountAmount())
                .discountRate(response.getDiscountRate())
                .validFrom(response.getValidFrom())
                .validUntil(response.getValidUntil())
                .remainingQty(response.getRemainingQty())
                // 수정: Presentation layer AvailableCouponResponse에는 isActive/status 필드가 없음
                // Application layer의 isActive는 Presentation에서 필요 없음 (선택적 필드)
                .build();
    }

    /**
     * Application GetUserCouponsResponse → Presentation GetUserCouponsResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.coupon.response.GetUserCouponsResponse toGetUserCouponsResponse(GetUserCouponsResponse response) {
        return com.hhplus.ecommerce.presentation.coupon.response.GetUserCouponsResponse.builder()
                .userCoupons(response.getCoupons().stream()
                        .map(this::toUserCouponResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Application UserCouponResponse → Presentation UserCouponResponse로 변환
     */
    private com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse toUserCouponResponse(UserCouponResponse response) {
        return com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse.builder()
                .userCouponId(response.getUserCouponId())
                .couponId(response.getCouponId())
                .couponName(response.getCouponName())
                .discountType(response.getDiscountType())
                .discountAmount(response.getDiscountAmount())
                .discountRate(response.getDiscountRate())
                .status(response.getStatus())
                .issuedAt(response.getIssuedAt())
                .validFrom(response.getValidFrom())
                .validUntil(response.getValidUntil())
                .build();
    }
}