package com.hhplus.ecommerce.common.exception;

/**
 * SystemException - 시스템/인프라 계층 오류 예외
 *
 * 역할:
 * - 데이터베이스, 캐시, 외부 API 등 인프라 오류
 * - 예측 불가능한 시스템 오류
 * - 항상 서버 오류(5XX)로 응답
 *
 * 사용 예:
 * - DatabaseException: DB 연결 오류
 * - CacheException: Redis 오류
 * - LockAcquisitionFailedException: 분산락 획득 실패
 * - ExternalApiException: 외부 API 호출 오류
 *
 * 특징:
 * - 클라이언트 재시도 가능성 있음
 * - 모니터링 필요
 * - 회로 차단기(Circuit Breaker) 고려 대상
 */
public class SystemException extends BizException {

    public SystemException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SystemException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public SystemException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }
}
