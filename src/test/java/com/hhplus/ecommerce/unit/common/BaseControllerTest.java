package com.hhplus.ecommerce.unit.common;

import org.springframework.test.context.TestPropertySource;

/**
 * BaseControllerTest - Controller 계층 테스트 기본 클래스
 *
 * 목적: Controller 테스트를 위한 공통 설정 제공 (순수 테스트 유틸)
 *
 * Spring Boot 3.5.7에서 정적 리소스 매핑으로 인한 Controller 라우팅 문제 해결
 * - spring.web.resources.add-mappings=false로 설정하여 정적 리소스 매핑 비활성화
 *
 * 사용 패턴:
 * - @WebMvcTest를 사용하는 자식 클래스에서 MockMvc를 @Autowired로 주입
 * - 자식 클래스에서 MockMvc를 직접 선언하여 사용
 *
 * 주의사항:
 * - 이 클래스는 MockMvc 필드를 가지지 않음 (Mockito와 Spring 모두와 호환성 유지)
 * - Mockito 단위 테스트와 @WebMvcTest 통합 테스트 모두에서 상속 가능
 * - MockMvc는 필요한 자식 테스트 클래스에서만 정의
 */
@TestPropertySource(properties = {
    "spring.web.resources.add-mappings=false"
})
public abstract class BaseControllerTest {
    // 의도적으로 비어있음: 순수한 설정 기본 클래스
}
