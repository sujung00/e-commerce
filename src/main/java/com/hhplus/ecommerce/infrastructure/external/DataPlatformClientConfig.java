package com.hhplus.ecommerce.infrastructure.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DataPlatformClientConfig - 데이터 플랫폼 클라이언트 Bean 설정
 *
 * data-platform.base-url이 설정된 경우 HttpDataPlatformClient 사용,
 * 설정되지 않은 경우 NoopDataPlatformClient(폴백)를 사용한다.
 *
 * application.yml 설정 예시:
 * <pre>
 * data-platform:
 *   base-url: https://data-platform.example.com  # 데이터 플랫폼 API 기본 URL
 * </pre>
 */
@Configuration
public class DataPlatformClientConfig {

    private static final Logger log = LoggerFactory.getLogger(DataPlatformClientConfig.class);

    /**
     * 데이터 플랫폼 기본 URL (data-platform.base-url)
     * - 설정된 경우: HttpDataPlatformClient 활성화
     * - 미설정 시 빈 문자열로 기본값 처리
     */
    @Value("${data-platform.base-url:}")
    private String baseUrl;

    /**
     * DataPlatformClient Bean 등록
     *
     * - base-url이 설정된 경우: HttpDataPlatformClient (java.net.http.HttpClient)
     * - base-url이 없는 경우: NoopDataPlatformClient (로그만 출력)
     *
     * @return DataPlatformClient 구현체
     */
    @Bean
    public DataPlatformClient dataPlatformClient() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            log.info("[DataPlatformClientConfig] HttpDataPlatformClient 활성화 - baseUrl={}", baseUrl);
            return new HttpDataPlatformClient(baseUrl);
        }
        log.info("[DataPlatformClientConfig] NoopDataPlatformClient 활성화 (폴백) - data-platform.base-url 미설정");
        return new NoopDataPlatformClient();
    }
}
