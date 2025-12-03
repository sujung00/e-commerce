package com.hhplus.ecommerce.infrastructure.config;

import com.hhplus.ecommerce.domain.order.OrderDomainService;
import com.hhplus.ecommerce.domain.coupon.CouponDomainService;
import com.hhplus.ecommerce.domain.product.ProductDomainService;
import com.hhplus.ecommerce.domain.user.UserBalanceDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DomainServiceConfig - Domain Services를 Spring Bean으로 등록
 *
 * Domain Services는 순수 비즈니스 로직만 포함하므로 외부 의존성이 없습니다.
 * Spring Bean으로 등록하면 Dependency Injection을 통해
 * Application Services에서 쉽게 사용할 수 있습니다.
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public OrderDomainService orderDomainService() {
        return new OrderDomainService();
    }

    @Bean
    public CouponDomainService couponDomainService() {
        return new CouponDomainService();
    }

    @Bean
    public ProductDomainService productDomainService() {
        return new ProductDomainService();
    }

    @Bean
    public UserBalanceDomainService userBalanceDomainService() {
        return new UserBalanceDomainService();
    }
}
