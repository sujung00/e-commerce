package com.hhplus.ecommerce.unit.config;

import com.hhplus.ecommerce.integration.BaseIntegrationTest;

/**
 * ⚠️ 하위 호환성 유지를 위한 래퍼 클래스
 *
 * 기존 코드에서 AbstractIntegrationTest를 사용하는 경우를 위해 유지됩니다.
 * 새로운 코드는 BaseIntegrationTest를 직접 상속받아야 합니다.
 *
 * 사용 방법:
 * ```java
 * // ❌ 기존 (deprecated)
 * public class MyTest extends AbstractIntegrationTest { ... }
 *
 * // ✅ 새로운 방식
 * import com.hhplus.ecommerce.integration.BaseIntegrationTest;
 * public class MyTest extends BaseIntegrationTest { ... }
 * ```
 */
@Deprecated(since = "2025-11-18", forRemoval = true)
public abstract class AbstractIntegrationTest extends BaseIntegrationTest {
    // BaseIntegrationTest로 완전히 이전되었습니다.
    // 이 클래스는 하위 호환성을 위해서만 유지됩니다.
}
