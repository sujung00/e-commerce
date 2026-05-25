package com.hhplus.ecommerce.infrastructure.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NoopDataPlatformClient - 폴백 데이터 플랫폼 클라이언트 (No-operation)
 *
 * 역할:
 * - data-platform.base-url이 설정되지 않은 경우 사용되는 폴백 구현체
 * - 실제 전송 없이 로그만 출력
 *
 * 사용 시나리오:
 * - 로컬 개발 환경
 * - 데이터 플랫폼 연동이 비활성화된 환경
 * - 테스트 환경
 *
 * 설정:
 * - DataPlatformClientConfig에서 base-url 미설정 시 Bean 등록
 */
public class NoopDataPlatformClient implements DataPlatformClient {

    private static final Logger log = LoggerFactory.getLogger(NoopDataPlatformClient.class);

    /**
     * Noop 전송: 로그만 출력하고 실제 전송하지 않음
     *
     * @param payload JSON 형태의 메시지 데이터
     */
    @Override
    public void send(String payload) {
        log.info("[NoopDataPlatformClient] 데이터 플랫폼 전송 skip (data-platform.base-url 미설정) - payload={}",
                payload);
    }
}
