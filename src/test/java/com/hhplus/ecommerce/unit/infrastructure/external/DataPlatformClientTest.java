package com.hhplus.ecommerce.unit.infrastructure.external;

import com.hhplus.ecommerce.infrastructure.external.DataPlatformClient;
import com.hhplus.ecommerce.infrastructure.external.DataPlatformClientConfig;
import com.hhplus.ecommerce.infrastructure.external.HttpDataPlatformClient;
import com.hhplus.ecommerce.infrastructure.external.NoopDataPlatformClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DataPlatformClient 단위 테스트
 * - base-url 설정에 따른 Bean 선택 검증
 * - NoopDataPlatformClient: 예외 없이 실행 (fire-and-forget)
 * - HttpDataPlatformClient: 연결 실패 시 예외 전파하지 않음 (fire-and-forget)
 */
@DisplayName("[Unit] DataPlatformClient - 구현체 선택 및 fire-and-forget 검증")
class DataPlatformClientTest {

    @Test
    @DisplayName("base-url이 없으면 NoopDataPlatformClient가 선택된다")
    void noBaseUrl_returnsNoopClient() {
        DataPlatformClientConfig config = new DataPlatformClientConfig();
        ReflectionTestUtils.setField(config, "baseUrl", "");

        DataPlatformClient client = config.dataPlatformClient();

        assertThat(client).isInstanceOf(NoopDataPlatformClient.class);
    }

    @Test
    @DisplayName("base-url이 설정되면 HttpDataPlatformClient가 선택된다")
    void withBaseUrl_returnsHttpClient() {
        DataPlatformClientConfig config = new DataPlatformClientConfig();
        ReflectionTestUtils.setField(config, "baseUrl", "https://data-platform.example.com");

        DataPlatformClient client = config.dataPlatformClient();

        assertThat(client).isInstanceOf(HttpDataPlatformClient.class);
    }

    @Test
    @DisplayName("NoopDataPlatformClient: send() 예외 없이 완료된다")
    void noopClient_sendDoesNotThrow() {
        NoopDataPlatformClient client = new NoopDataPlatformClient();

        assertThatCode(() -> client.send("{\"orderId\":1}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HttpDataPlatformClient: 잘못된 URL로 send() 호출 시 예외 전파하지 않는다 (fire-and-forget)")
    void httpClient_unreachableUrl_doesNotThrow() {
        // fire-and-forget: 연결 실패해도 예외를 전파하지 않음
        HttpDataPlatformClient client = new HttpDataPlatformClient("http://localhost:9999");

        assertThatCode(() -> client.send("{\"orderId\":1}"))
                .doesNotThrowAnyException();
    }
}
