package com.hhplus.ecommerce.infrastructure.external;

/**
 * DataPlatformClient - 데이터 플랫폼 외부 연동 클라이언트 인터페이스
 *
 * 역할:
 * - 주문 완료 데이터를 외부 데이터 플랫폼으로 전송하는 Port
 * - HTTP 또는 다른 전송 방식으로 확장 가능
 *
 * 구현체:
 * - HttpDataPlatformClient: java.net.http.HttpClient 기반 실제 HTTP POST 전송
 * - NoopDataPlatformClient: data-platform.base-url 미설정 시 로그만 출력 (폴백)
 *
 * 설정:
 * - application.yml: data-platform.base-url
 * - DataPlatformClientConfig에서 base-url 존재 여부에 따라 Bean 선택
 */
public interface DataPlatformClient {

    /**
     * 데이터 플랫폼으로 메시지 전송
     *
     * @param payload JSON 형태의 메시지 데이터
     * @throws Exception 전송 실패 시 (fire-and-forget 구현체는 예외를 전파하지 않음)
     */
    void send(String payload) throws Exception;
}
