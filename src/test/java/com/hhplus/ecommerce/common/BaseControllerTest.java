package com.hhplus.ecommerce.common;

import org.springframework.test.context.TestPropertySource;

/**
 * BaseControllerTest - Controller 계층 테스트 기본 클래스
 *
 * Spring Boot 3.5.7에서 정적 리소스 매핑으로 인한 Controller 라우팅 문제 해결
 * - spring.web.resources.add-mappings=false로 설정하여 정적 리소스 매핑 비활성화
 * - MockMvc를 사용한 HTTP 요청 검증
 * - 모든 Controller 테스트는 이 클래스를 상속받아 일관된 설정을 유지
 *
 * MockMvc 사용 방법:
 *
 * 1️⃣ Mockito 기반 단위 테스트 (@ExtendWith(MockitoExtension.class))
 *    ✅ MockMvcBuilders.standaloneSetup(controller).build() 사용
 *    ✅ BeforeEach에서 MockMvc 직접 구성
 *    ✅ 장점: 빠른 테스트, 컨트롤러 로직만 검증
 *
 *    예시:
 *    @ExtendWith(MockitoExtension.class)
 *    class CartControllerTest extends BaseControllerTest {
 *        @InjectMocks
 *        private CartController controller;
 *
 *        private MockMvc mockMvc;
 *
 *        @BeforeEach
 *        void setup() {
 *            mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
 *        }
 *    }
 *
 * 2️⃣ Spring Boot 통합 테스트 (@SpringBootTest)
 *    ✅ @Autowired MockMvc 자동 주입 (Spring이 MockMvc bean 생성)
 *    ✅ 전체 Spring 애플리케이션 컨텍스트 로드
 *    ✅ 장점: 통합 테스트, 의존성 자동 주입, DB 접근 가능
 *
 *    예시:
 *    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
 *    class CartControllerIntegrationTest extends BaseIntegrationTest {
 *        @Autowired
 *        private TestRestTemplate restTemplate;
 *
 *        // 또는
 *
 *        @Autowired
 *        private MockMvc mockMvc;
 *    }
 *
 * ⚠️ 주의사항:
 * - @Autowired는 @SpringBootTest, @WebMvcTest 등 Spring 컨텍스트가 있을 때만 동작
 * - MockitoExtension.class에서는 @Autowired가 동작하지 않음
 * - 각 테스트 클래스의 구성(annotation)에 맞게 MockMvc를 정의해야 함
 */
@TestPropertySource(properties = {
    "spring.web.resources.add-mappings=false"
})
public abstract class BaseControllerTest {
    // MockMvc는 각 테스트 클래스의 구성에 따라 다르게 정의합니다.
    // 이 기본 클래스에서는 MockMvc를 정의하지 않습니다.
}
