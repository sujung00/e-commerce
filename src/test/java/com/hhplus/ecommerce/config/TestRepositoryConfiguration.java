package com.hhplus.ecommerce.config;

import com.hhplus.ecommerce.domain.cart.CartRepository;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.infrastructure.persistence.cart.MySQLCartRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.MySQLCouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.MySQLUserCouponRepository;
import com.hhplus.ecommerce.infrastructure.persistence.order.MySQLOrderRepository;
import com.hhplus.ecommerce.infrastructure.persistence.order.MySQLOutboxRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.MySQLProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.MySQLUserRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test Repository Configuration
 *
 * Integration tests use MySQL repositories with MySQL test database.
 * This configuration explicitly declares MySQL repository beans as primary
 * to avoid NoUniqueBeanDefinitionException when both InMemory and MySQL
 * repositories are present in the classpath.
 */
@TestConfiguration
public class TestRepositoryConfiguration {

    @Bean
    @Primary
    public UserRepository userRepository(MySQLUserRepository mysqlUserRepository) {
        return mysqlUserRepository;
    }

    @Bean
    @Primary
    public CartRepository cartRepository(MySQLCartRepository mysqlCartRepository) {
        return mysqlCartRepository;
    }

    @Bean
    @Primary
    public ProductRepository productRepository(MySQLProductRepository mysqlProductRepository) {
        return mysqlProductRepository;
    }

    @Bean
    @Primary
    public CouponRepository couponRepository(MySQLCouponRepository mysqlCouponRepository) {
        return mysqlCouponRepository;
    }

    @Bean
    @Primary
    public UserCouponRepository userCouponRepository(MySQLUserCouponRepository mysqlUserCouponRepository) {
        return mysqlUserCouponRepository;
    }

    @Bean
    @Primary
    public OrderRepository orderRepository(MySQLOrderRepository mysqlOrderRepository) {
        return mysqlOrderRepository;
    }

    @Bean
    @Primary
    public OutboxRepository outboxRepository(MySQLOutboxRepository mysqlOutboxRepository) {
        return mysqlOutboxRepository;
    }
}
