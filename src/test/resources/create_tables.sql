CREATE DATABASE IF NOT EXISTS ecommerce_test;
USE ecommerce_test;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 사용자
CREATE TABLE `users` (
                         `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '사용자 ID',
                         `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이메일 (로그인용)',
                         `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '암호화된 비밀번호',
                         `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `phone` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `balance` bigint NOT NULL DEFAULT '0' COMMENT '현재 잔액 (원)',
                         `version` bigint NOT NULL DEFAULT '0' COMMENT '낙관적 락 버전',
                         `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                         `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
                         PRIMARY KEY (`user_id`),
                         UNIQUE KEY `uk_email` (`email`),
                         KEY `idx_email` (`email`),
                         KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 계정 정보';

-- 2. 쿠폰
CREATE TABLE `coupons` (
                           `coupon_id` bigint NOT NULL AUTO_INCREMENT COMMENT '쿠폰 ID',
                           `coupon_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '쿠폰명',
                           `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                           `discount_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                           `discount_amount` bigint NOT NULL DEFAULT '0' COMMENT '정액 할인 (원)',
                           `discount_rate` decimal(38,2) NOT NULL,
                           `total_quantity` int NOT NULL COMMENT '총 발급 수량',
                           `remaining_qty` int NOT NULL COMMENT '남은 발급 수량',
                           `valid_from` timestamp NOT NULL COMMENT '유효 시작 시각',
                           `valid_until` timestamp NOT NULL COMMENT '유효 종료 시각',
                           `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '활성 여부',
                           `version` bigint NOT NULL DEFAULT '1' COMMENT '낙관적 락 버전 (동시성 제어)',
                           `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                           `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
                           PRIMARY KEY (`coupon_id`),
                           KEY `idx_is_active` (`is_active`),
                           KEY `idx_valid_period` (`valid_from`,`valid_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='할인 쿠폰';

-- 3. 상품
CREATE TABLE `products` (
                            `product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '상품 ID',
                            `product_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품명',
                            `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                            `price` bigint NOT NULL COMMENT '상품 가격 (원)',
                            `total_stock` int NOT NULL DEFAULT '0' COMMENT '총 재고 (모든 옵션 합계)',
                            `status` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                            `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                            `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
                            PRIMARY KEY (`product_id`),
                            KEY `idx_status` (`status`),
                            KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 정보';

-- 4. 상품 옵션
CREATE TABLE `product_options` (
                                   `option_id` bigint NOT NULL AUTO_INCREMENT COMMENT '옵션 ID',
                                   `product_id` bigint NOT NULL COMMENT '상품 ID (FK)',
                                   `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                   `stock` int NOT NULL DEFAULT '0' COMMENT '옵션별 재고',
                                   `version` bigint NOT NULL DEFAULT '1' COMMENT '낙관적 락 버전',
                                   `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                                   `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
                                   PRIMARY KEY (`option_id`),
                                   UNIQUE KEY `uk_product_option_name` (`product_id`, `name`),
                                   KEY `idx_product_id` (`product_id`),
                                   CONSTRAINT `product_options_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 옵션 (색상, 사이즈 등)';

-- 5. 장바구니
CREATE TABLE `carts` (
                         `cart_id` bigint NOT NULL AUTO_INCREMENT COMMENT '장바구니 ID',
                         `user_id` bigint NOT NULL COMMENT '사용자 ID (1:1 관계)',
                         `total_items` int NOT NULL DEFAULT '0' COMMENT '장바구니 내 총 항목 수',
                         `total_price` bigint NOT NULL DEFAULT '0' COMMENT '장바구니 총 금액 (원)',
                         `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                         `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
                         PRIMARY KEY (`cart_id`),
                         UNIQUE KEY `uk_user_cart` (`user_id`),
                         CONSTRAINT `carts_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 장바구니';

-- 6. 장바구니 항목
CREATE TABLE `cart_items` (
                              `cart_item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '장바구니 항목 ID',
                              `cart_id` bigint NOT NULL COMMENT '장바구니 ID (FK)',
                              `product_id` bigint NOT NULL COMMENT '상품 ID',
                              `option_id` bigint NOT NULL COMMENT '옵션 ID',
                              `quantity` int NOT NULL COMMENT '수량',
                              `unit_price` bigint NOT NULL COMMENT '단가 (원)',
                              `subtotal` bigint NOT NULL COMMENT '소계 (단가 × 수량)',
                              `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                              `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
                              PRIMARY KEY (`cart_item_id`),
                              UNIQUE KEY `uk_cart_product_option` (`cart_id`,`product_id`,`option_id`),
                              CONSTRAINT `cart_items_ibfk_1` FOREIGN KEY (`cart_id`) REFERENCES `carts` (`cart_id`) ON DELETE CASCADE,
                              CONSTRAINT `cart_items_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE,
                              CONSTRAINT `cart_items_ibfk_3` FOREIGN KEY (`option_id`) REFERENCES `product_options` (`option_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='장바구니 항목 (라인 아이템)';

-- 7. 주문
CREATE TABLE `orders` (
                          `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '주문 ID',
                          `user_id` bigint NOT NULL COMMENT '사용자 ID (FK)',
                          `order_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETED' COMMENT '주문 상태',
                          `coupon_id` bigint DEFAULT NULL COMMENT '적용된 쿠폰 ID (nullable)',
                          `coupon_discount` bigint NOT NULL DEFAULT '0' COMMENT '쿠폰 할인액 (원)',
                          `subtotal` bigint NOT NULL COMMENT '소계 (할인 전)',
                          `final_amount` bigint NOT NULL COMMENT '최종 결제 금액 (할인 후)',
                          `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '주문 생성 시각',
                          `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
                          `cancelled_at` timestamp NULL DEFAULT NULL COMMENT '취소 시각 (취소된 경우)',
                          PRIMARY KEY (`order_id`),
                          CONSTRAINT `orders_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
                          CONSTRAINT `orders_ibfk_2` FOREIGN KEY (`coupon_id`) REFERENCES `coupons` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문';

-- 8. 주문 항목
CREATE TABLE `order_items` (
                               `order_item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '주문 항목 ID',
                               `order_id` bigint NOT NULL COMMENT '주문 ID (FK)',
                               `product_id` bigint NOT NULL COMMENT '상품 ID',
                               `option_id` bigint NOT NULL COMMENT '옵션 ID',
                               `product_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품명 (스냅샷)',
                               `option_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                               `quantity` int NOT NULL COMMENT '수량',
                               `unit_price` bigint NOT NULL COMMENT '단가 (원)',
                               `subtotal` bigint NOT NULL COMMENT '소계 (단가 × 수량)',
                               `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                               PRIMARY KEY (`order_item_id`),
                               CONSTRAINT `order_items_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문 항목 (라인 아이템)';

-- 9. 사용자 쿠폰
CREATE TABLE `user_coupons` (
                                `user_coupon_id` bigint NOT NULL AUTO_INCREMENT COMMENT '사용자 쿠폰 ID',
                                `user_id` bigint NOT NULL COMMENT '사용자 ID (FK)',
                                `coupon_id` bigint NOT NULL COMMENT '쿠폰 ID (FK)',
                                `status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNUSED' COMMENT '상태',
                                `issued_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 시각',
                                `used_at` timestamp NULL DEFAULT NULL COMMENT '사용 시각',
                                `order_id` bigint DEFAULT NULL COMMENT '사용된 주문 ID (nullable)',
                                PRIMARY KEY (`user_coupon_id`),
                                UNIQUE KEY `uk_user_coupon` (`user_id`,`coupon_id`),
                                CONSTRAINT `user_coupons_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
                                CONSTRAINT `user_coupons_ibfk_2` FOREIGN KEY (`coupon_id`) REFERENCES `coupons` (`coupon_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 발급 쿠폰';

-- 10. Outbox
CREATE TABLE `outbox` (
                          `message_id` bigint NOT NULL AUTO_INCREMENT COMMENT '메시지 ID',
                          `order_id` bigint NOT NULL COMMENT '주문 ID (FK)',
                          `user_id` bigint NOT NULL COMMENT '사용자 ID (FK)',
                          `message_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                          `status` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                          `retry_count` int NOT NULL DEFAULT '0' COMMENT '재시도 횟수',
                          `last_attempt` timestamp NULL DEFAULT NULL COMMENT '마지막 시도 시각',
                          `sent_at` timestamp NULL DEFAULT NULL COMMENT '전송 완료 시각',
                          `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                          PRIMARY KEY (`message_id`),
                          CONSTRAINT `outbox_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE CASCADE,
                          CONSTRAINT `outbox_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox 메시지 테이블';

SET FOREIGN_KEY_CHECKS = 1;
