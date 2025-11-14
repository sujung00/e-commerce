package com.hhplus.ecommerce.application.coupon.dto;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사용 가능한 쿠폰 응답 (Application layer 내부 DTO)
 *
 * 책임:
 * - Domain Coupon 엔티티에서 필요한 필드만 추출
 * - 계층 간 DTO 변환 (Domain → Application)
 * - Null-safe한 필드 매핑 보장
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableCouponResponse {
    private Long couponId;
    private String couponName;
    // 수정: Domain에서 String으로 관리하므로 그대로 유지
    // 실제로는 FIXED_AMOUNT | PERCENTAGE Enum으로 개선 필요
    private String discountType;
    private Long discountAmount;
    private BigDecimal discountRate;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    // 수정: Domain의 remainingQty는 Integer, DTO에서는 명시적으로 Long으로 변환
    private Integer remainingQty;
    // 수정: Domain에는 status 필드가 없음, 대신 isActive Boolean을 사용
    // isActive가 true면 "활성", false면 "비활성" 상태로 변환
    private String isActive;

    /**
     * Domain Coupon → Application AvailableCouponResponse 변환
     *
     * Null-safe 매핑을 통해 NullPointerException 방지
     * Domain 엔티티의 필드 타입에 맞게 명시적으로 매핑
     *
     * @param coupon Domain Coupon 엔티티
     * @return Application layer AvailableCouponResponse DTO
     */
    public static AvailableCouponResponse from(Coupon coupon) {
        if (coupon == null) {
            return null;
        }

        return AvailableCouponResponse.builder()
                .couponId(coupon.getCouponId())
                .couponName(coupon.getCouponName())
                // DiscountType: Domain의 String 값 그대로 사용
                .discountType(coupon.getDiscountType())
                // DiscountAmount: Domain에서 Long 타입 그대로 사용
                .discountAmount(coupon.getDiscountAmount())
                // DiscountRate: Domain에서 BigDecimal 타입 그대로 사용
                .discountRate(coupon.getDiscountRate())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                // RemainingQty: Domain의 Integer 타입 그대로 사용
                // (기존 Long 선언은 불일치로 인한 타입 변환 오류 발생)
                .remainingQty(coupon.getRemainingQty())
                // IsActive: Domain의 Boolean을 String으로 변환
                // null 안전성: Boolean.TRUE.equals() 사용으로 NPE 방지
                .isActive(Boolean.TRUE.equals(coupon.getIsActive()) ? "활성" : "비활성")
                .build();
    }
}
