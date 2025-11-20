package com.hhplus.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * RetryConfig - Spring Retry 설정 클래스
 *
 * 역할:
 * - @Retryable, @Recover 어노테이션 활성화
 * - AOP 프록시를 통한 재시도 로직 자동 적용
 *
 * SCENARIO 16: 낙관적 락 재시도 폭증 (Thundering Herd)
 * - 문제: OptimisticLockException 발생 시 무제한 재시도로 서버 부하 증가
 * - 해결: @Retryable을 통한 제한된 재시도 + Jitter로 동시성 분산
 *
 * 작동 원리:
 * 1. @EnableRetry가 AOP를 활성화
 * 2. @Retryable이 붙은 메서드 호출 시 프록시를 통해 가로채기
 * 3. 지정된 예외 발생 시 maxAttempts까지 재시도
 * 4. backoff 설정에 따라 대기 시간 증가 (exponential backoff)
 * 5. random=true인 경우 Jitter 추가로 thundering herd 방지
 * 6. maxAttempts 초과 시 @Recover 메서드 호출 (정의된 경우)
 */
@Configuration
@EnableRetry
public class RetryConfig {
    // 기본 설정만 필요함
    // @Retryable, @Recover 어노테이션은 각 메서드에서 정의
}
