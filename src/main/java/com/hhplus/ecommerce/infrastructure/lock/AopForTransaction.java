package com.hhplus.ecommerce.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AOP 트랜잭션 분리
 *
 * DistributedLockAop에서 메서드 호출 시 트랜잭션을 분리하기 위해 사용됩니다.
 * REQUIRES_NEW를 사용하여 새로운 트랜잭션을 시작합니다.
 *
 * 이를 통해 락 호출과 실제 비즈니스 로직이 분리된 트랜잭션에서 실행됩니다.
 */
@Component
@RequiredArgsConstructor
public class AopForTransaction {

    /**
     * 메서드를 새로운 트랜잭션에서 실행
     *
     * @param callback 실행할 작업
     * @param <T> 반환 타입
     * @return 실행 결과
     * @throws Exception 예외 발생 시
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T proceed(ProceedCallback<T> callback) throws Exception {
        return callback.proceed();
    }

    /**
     * 메서드를 새로운 트랜잭션에서 실행 (반환값 없음)
     *
     * @param callback 실행할 작업
     * @throws Exception 예외 발생 시
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void proceed(VoidProceedCallback callback) throws Exception {
        callback.proceed();
    }

    /**
     * 값을 반환하는 콜백 인터페이스
     *
     * @param <T> 반환 타입
     */
    @FunctionalInterface
    public interface ProceedCallback<T> {
        T proceed() throws Exception;
    }

    /**
     * 값을 반환하지 않는 콜백 인터페이스
     */
    @FunctionalInterface
    public interface VoidProceedCallback {
        void proceed() throws Exception;
    }
}
