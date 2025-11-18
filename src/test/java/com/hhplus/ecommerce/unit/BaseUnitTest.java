package com.hhplus.ecommerce.unit;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 단위 테스트 기본 클래스
 *
 * 모든 순수 단위 테스트(Mocking 기반)가 상속해야 하는 기본 클래스입니다.
 * - Spring Context 없음 (더 빠른 실행)
 * - Mock/Stub을 이용한 의존성 주입
 * - 단일 컴포넌트 테스트 (도메인, 애플리케이션 서비스)
 *
 * 사용 방법:
 * ```java
 * public class MyUnitTest extends BaseUnitTest {
 *     @Mock
 *     private SomeRepository repository;
 *
 *     @InjectMocks
 *     private SomeService service;
 *
 *     @Test
 *     public void testSomething() {
 *         when(repository.findById(1L)).thenReturn(Optional.of(...));
 *         // 테스트 코드
 *     }
 * }
 * ```
 *
 * 특징:
 * - Mockito ExtendWith 자동 적용
 * - @Mock, @InjectMocks 자동 초기화
 * - 빠른 실행 (DB 없음, Spring 없음)
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseUnitTest {
    // 공통 설정이 적용되는 기본 클래스
}