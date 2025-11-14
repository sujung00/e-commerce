-- MySQL Test Database 초기화 스크립트
-- 테스트 데이터베이스 생성 및 사용자 권한 설정

-- 기존 테스트 데이터베이스 삭제
DROP DATABASE IF EXISTS ecommerce_test;

-- 테스트 데이터베이스 생성
CREATE DATABASE ecommerce_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 테스트 데이터베이스 사용
USE ecommerce_test;

-- root 사용자에게 모든 권한 부여
GRANT ALL PRIVILEGES ON ecommerce_test.* TO 'root'@'localhost';
FLUSH PRIVILEGES;

-- DDL 자동 생성 테스트 확인
SELECT 'ecommerce_test 데이터베이스 생성 완료' AS Status;
