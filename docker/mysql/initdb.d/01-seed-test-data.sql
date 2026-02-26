-- =====================================================
-- k6 부하 테스트용 시딩 데이터
-- =====================================================
-- 목적: load-test-LT-001.js, peak-test-PT-001.js, stress-test-ST-001.js 테스트 데이터
-- 실행: docker-compose up 시 자동 실행 (initdb.d 마운트)
-- Idempotent: 여러 번 실행해도 안전 (INSERT IGNORE 사용)
-- =====================================================

-- 데이터베이스 선택
USE hhplus_ecommerce;

-- =====================================================
-- Stored Procedure 정의: 대량 데이터 생성
-- =====================================================
DELIMITER $$

DROP PROCEDURE IF EXISTS seed_test_data$$

CREATE PROCEDURE seed_test_data()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE product_id_val BIGINT;

    -- ==========================================
    -- 1. Users (1~1000)
    -- ==========================================
    -- k6가 1~10000 범위를 사용하지만, 1000명으로 제한 (성능)
    -- balance: 10,000,000원 (충분한 잔액)
    -- ==========================================
    WHILE i <= 1000 DO
        INSERT IGNORE INTO users (
            user_id, email, name, phone, balance, version, created_at, updated_at
        ) VALUES (
            i,
            CONCAT('testuser', i, '@hhplus.com'),
            CONCAT('테스트유저', i),
            CONCAT('010-', LPAD(i, 4, '0'), '-', LPAD(i, 4, '0')),
            10000000,  -- 1000만원
            0,
            NOW(),
            NOW()
        );
        SET i = i + 1;
    END WHILE;

    -- ==========================================
    -- 2. Products (1~100)
    -- ==========================================
    -- k6가 1~1000 범위를 사용하지만, 100개로 제한 (성능)
    -- price: 10,000~19,900원
    -- total_stock: 10,000개 (옵션 재고 합계)
    -- status: IN_STOCK (판매 가능)
    -- ==========================================
    SET i = 1;
    WHILE i <= 100 DO
        INSERT IGNORE INTO products (
            product_id, product_name, description, price, total_stock, status, version, created_at, updated_at
        ) VALUES (
            i,
            CONCAT('테스트 상품 ', i),
            CONCAT('부하 테스트용 상품 ', i, '번 - 재고 충분'),
            10000 + (i * 100),  -- 가격: 10,000~19,900원
            10000,  -- 총 재고: 10,000개
            'IN_STOCK',
            0,
            NOW(),
            NOW()
        );
        SET i = i + 1;
    END WHILE;

    -- ==========================================
    -- 3. ProductOptions (각 product마다 1개)
    -- ==========================================
    -- optionId는 AUTO_INCREMENT지만 명시적으로 1~100 할당
    -- stock: 10,000개 (충분한 재고)
    -- ==========================================
    SET i = 1;
    WHILE i <= 100 DO
        INSERT IGNORE INTO product_options (
            option_id, product_id, name, stock, version, created_at, updated_at
        ) VALUES (
            i,  -- optionId: 1~100
            i,  -- productId: 1~100
            CONCAT('기본 옵션 (상품 ', i, ')'),
            10000,  -- 재고: 10,000개
            0,
            NOW(),
            NOW()
        );
        SET i = i + 1;
    END WHILE;

    -- ==========================================
    -- 4. Coupons (couponId = 1)
    -- ==========================================
    -- k6의 couponIssueScenario에서 고정으로 couponId=1 사용
    -- discount_type: PERCENTAGE (비율 할인)
    -- discount_rate: 0.1 (10% 할인)
    -- total_quantity: 100,000개 (선착순 쿠폰)
    -- valid_from: 어제부터
    -- valid_until: 30일 후까지
    -- ==========================================
    INSERT IGNORE INTO coupons (
        coupon_id,
        coupon_name,
        description,
        discount_type,
        discount_amount,
        discount_rate,
        total_quantity,
        remaining_qty,
        valid_from,
        valid_until,
        is_active,
        version,
        created_at,
        updated_at
    ) VALUES (
        1,
        '[부하테스트] 10% 할인 쿠폰',
        'k6 부하 테스트용 선착순 쿠폰 - 100,000개 한정',
        'PERCENTAGE',  -- 비율 할인
        0,  -- discount_amount는 PERCENTAGE일 때 0
        0.1,  -- 10% 할인
        100000,  -- 총 수량: 100,000개
        100000,  -- 남은 수량: 100,000개
        DATE_SUB(NOW(), INTERVAL 1 DAY),  -- 어제부터 유효
        DATE_ADD(NOW(), INTERVAL 30 DAY),  -- 30일 후까지 유효
        TRUE,  -- 활성화 상태
        0,
        NOW(),
        NOW()
    );

    -- ==========================================
    -- 5. 추가 쿠폰 (다양한 테스트용)
    -- ==========================================
    INSERT IGNORE INTO coupons (
        coupon_id,
        coupon_name,
        description,
        discount_type,
        discount_amount,
        discount_rate,
        total_quantity,
        remaining_qty,
        valid_from,
        valid_until,
        is_active,
        version,
        created_at,
        updated_at
    ) VALUES (
        2,
        '[부하테스트] 5,000원 할인 쿠폰',
        'k6 부하 테스트용 정액 할인 쿠폰',
        'FIXED_AMOUNT',  -- 정액 할인
        5000,  -- 5,000원 할인
        0.0,
        50000,  -- 총 수량: 50,000개
        50000,
        DATE_SUB(NOW(), INTERVAL 1 DAY),
        DATE_ADD(NOW(), INTERVAL 30 DAY),
        TRUE,
        0,
        NOW(),
        NOW()
    );

    -- ==========================================
    -- 성공 메시지
    -- ==========================================
    SELECT '✅ 시딩 데이터 생성 완료!' AS message;
    SELECT CONCAT('Users: ', COUNT(*), '명') AS users_count FROM users;
    SELECT CONCAT('Products: ', COUNT(*), '개') AS products_count FROM products;
    SELECT CONCAT('ProductOptions: ', COUNT(*), '개') AS options_count FROM product_options;
    SELECT CONCAT('Coupons: ', COUNT(*), '개') AS coupons_count FROM coupons;

END$$

DELIMITER ;

-- =====================================================
-- Procedure 실행 및 정리
-- =====================================================
CALL seed_test_data();
DROP PROCEDURE IF EXISTS seed_test_data;

-- =====================================================
-- 검증 쿼리 (로그 확인용)
-- =====================================================
SELECT
    '📊 시딩 데이터 요약' AS summary,
    (SELECT COUNT(*) FROM users) AS users,
    (SELECT COUNT(*) FROM products) AS products,
    (SELECT COUNT(*) FROM product_options) AS product_options,
    (SELECT COUNT(*) FROM coupons) AS coupons,
    (SELECT SUM(remaining_qty) FROM coupons) AS coupon_stock;

-- 주요 데이터 샘플 확인
SELECT 'Users (처음 3명):' AS sample;
SELECT user_id, email, name, balance FROM users LIMIT 3;

SELECT 'Products (처음 3개):' AS sample;
SELECT product_id, product_name, price, total_stock, status FROM products LIMIT 3;

SELECT 'ProductOptions (처음 3개):' AS sample;
SELECT option_id, product_id, name, stock FROM product_options LIMIT 3;

SELECT 'Coupons (전체):' AS sample;
SELECT coupon_id, coupon_name, discount_type, discount_amount, discount_rate, remaining_qty, is_active FROM coupons;