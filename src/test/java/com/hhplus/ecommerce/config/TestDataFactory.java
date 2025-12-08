package com.hhplus.ecommerce.config;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductStatus;
import com.hhplus.ecommerce.domain.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트 데이터 팩토리
 *
 * 통합 테스트에서 필요한 다양한 도메인 객체를 생성하는 팩토리입니다.
 * 일관된 테스트 데이터 생성으로 테스트 가독성을 높입니다.
 */
public class TestDataFactory {

    /**
     * 기본 사용자 생성
     */
    public static User createUser(long userId, String email, long balance) {
        return User.builder()
                .userId(userId)
                .email(email)
                .balance(balance)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 기본 사용자 생성 (간단한 버전)
     */
    public static User createUser(String email) {
        return User.builder()
                .email(email)
                .balance(100_000L)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 상품 생성
     */
    public static Product createProduct(long productId, String productName, long price, int totalStock) {
        return Product.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .totalStock(totalStock)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 상품 생성 (옵션 포함)
     */
    public static Product createProductWithOptions(long productId, String productName, long price) {
        Product product = createProduct(productId, productName, price, 100);
        return product;
    }

    /**
     * 상품 옵션 생성
     */
    public static ProductOption createProductOption(Product product, String name, int stock) {
        return ProductOption.builder()
                .name(name)
                .stock(stock)
                .version(0L)
                .build();
    }

    /**
     * 쿠폰 생성
     */
    public static Coupon createCoupon(long couponId, String couponName, int remainingQty, long discountAmount) {
        LocalDateTime now = LocalDateTime.now();
        return Coupon.builder()
                .couponId(couponId)
                .couponName(couponName)
                .discountType("FIXED")
                .discountAmount(discountAmount)
                .totalQuantity(remainingQty)
                .remainingQty(remainingQty)
                .validFrom(now.minusDays(1))
                .validUntil(now.plusDays(30))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 쿠폰 생성 (기본값 사용)
     */
    public static Coupon createCoupon(String couponName, int remainingQty) {
        LocalDateTime now = LocalDateTime.now();
        return Coupon.builder()
                .couponName(couponName)
                .discountType("FIXED")
                .discountAmount(10_000L)
                .totalQuantity(remainingQty)
                .remainingQty(remainingQty)
                .validFrom(now.minusDays(1))
                .validUntil(now.plusDays(30))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
