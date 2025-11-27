package com.hhplus.ecommerce.infrastructure.lock;

/**
 * AopForTransaction - DEPRECATED (더 이상 사용되지 않음)
 *
 * 변경 사항:
 * - DistributedLockAop에서 AopForTransaction.proceed() 호출을 제거하고
 *   joinPoint.proceed()를 직접 호출하도록 변경
 *
 * 이유:
 * - AopForTransaction이 REQUIRES_NEW로 독립적인 트랜잭션을 생성하면서
 *   트랜잭션 원자성이 깨지는 문제 발생
 * - AOP는 분산락만 담당하고, 트랜잭션은 서비스 메서드의 @Transactional에서만 관리
 * - 이를 통해 단일 원자적 트랜잭션 보장
 *
 * 현재 상태:
 * - 클래스는 유지되고 있으나 DistributedLockAop에서 사용되지 않음
 * - 향후 완전히 제거 가능
 *
 * 마이그레이션 가이드:
 * @DistributedLock(key = "order:#p0")  // ← AOP가 분산락만 관리
 * @Transactional                        // ← 서비스 메서드가 단일 트랜잭션 관리
 * public Order executeOrder(Long userId, ...) {
 *     // 비즈니스 로직
 * }
 */
public class AopForTransaction {
    // 이 클래스는 더 이상 사용되지 않습니다.
    // 필요시 삭제할 수 있습니다.
}
