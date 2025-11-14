-- MySQL 프로덕션 데이터베이스 초기화 스크립트
-- 메인 애플리케이션용 데이터베이스 생성 및 사용자 권한 설정

-- 기존 프로덕션 데이터베이스 삭제 (주의: 개발 환경에서만 사용)
-- DROP DATABASE IF EXISTS hhplus_ecommerce;

-- 프로덕션 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS hhplus_ecommerce
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 프로덕션 데이터베이스 사용
USE hhplus_ecommerce;

-- root 사용자에게 모든 권한 부여
GRANT ALL PRIVILEGES ON hhplus_ecommerce.* TO 'root'@'localhost';
GRANT ALL PRIVILEGES ON hhplus_ecommerce.* TO 'root'@'%';
FLUSH PRIVILEGES;

-- 데이터베이스 생성 완료
SELECT 'hhplus_ecommerce 데이터베이스 생성 완료' AS Status;
