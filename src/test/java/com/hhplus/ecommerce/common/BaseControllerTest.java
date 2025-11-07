package com.hhplus.ecommerce.common;

import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * BaseControllerTest - Controller 계층 통합 테스트 기본 클래스
 *
 * Spring Boot 3.5.7에서 정적 리소스 매핑으로 인한 Controller 라우팅 문제 해결
 * - spring.web.resources.add-mappings=false로 설정하여 정적 리소스 매핑 비활성화
 * - MockMvc를 사용한 HTTP 요청 검증
 * - 모든 Controller 테스트는 이 클래스를 상속받아 일관된 설정을 유지
 *
 * 두 가지 사용 패턴 지원:
 * 1. @WebMvcTest 패턴: @Autowired mockMvc 자동 주입
 * 2. @ExtendWith(MockitoExtension.class) 패턴: 자식 클래스에서 protected mockMvc 필드로 사용
 */
@TestPropertySource(properties = {
    "spring.web.resources.add-mappings=false"
})
public abstract class BaseControllerTest {

    @Autowired(required = false)
    protected MockMvc mockMvc;
}
