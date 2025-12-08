package com.hhplus.ecommerce.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 테스트(Controller) 기본 클래스
 *
 * 모든 Controller 레이어 테스트가 상속해야 하는 기본 클래스입니다.
 * MockMvc를 이용한 API 엔드포인트 테스트를 지원합니다.
 *
 * 사용 방법:
 * ```java
 * public class MyApiTest extends BaseApiTest {
 *     @MockBean
 *     private SomeService service;
 *
 *     @Test
 *     public void testCreateEndpoint() throws Exception {
 *         mockMvc.perform(post("/api/endpoint")
 *                 .contentType(MediaType.APPLICATION_JSON)
 *                 .content(objectMapper.writeValueAsString(...)))
 *                 .andExpect(status().isOk());
 *     }
 * }
 * ```
 *
 * 특징:
 * - MockMvc 자동 주입 (Spring Context 포함)
 * - ObjectMapper 자동 주입 (JSON 변환)
 * - Mockito 확장 (Mock 객체 사용 가능)
 * - 전체 Spring Context가 로드되지만, 서비스는 Mock으로 대체 가능
 * - HTTP 요청/응답 테스트 가능
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(MockitoExtension.class)
public abstract class BaseApiTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        // 각 테스트 전에 필요한 초기화 작업
        // 서브클래스에서 필요시 @Override하여 추가 설정 가능
    }
}