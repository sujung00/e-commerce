package com.hhplus.ecommerce;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * P6Spy SQL 로깅 테스트
 *
 * 테스트 실행 시 다음과 같은 형태의 SQL 로그가 콘솔에 출력되어야 합니다:
 *
 * ================== P6Spy SQL Logger ==================
 * Category : STATEMENT
 * Elapsed  : 5ms
 * SQL      :
 * insert into test_users (name, email) values ('testuser', 'test@example.com')
 * ======================================================
 *
 * 주의: P6Spy에서만 로그가 출력됩니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("P6Spy SQL 로깅 테스트")
class P6SpyLoggingTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("INSERT 쿼리 - P6Spy SQL 로깅 확인")
    void testInsertQuery() throws Exception {
        // Given
        Connection conn = dataSource.getConnection();
        conn.prepareStatement(
            "CREATE TABLE IF NOT EXISTS test_users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), email VARCHAR(255))"
        ).execute();

        // When
        // 다음 쿼리는 P6Spy에서 로깅됩니다:
        // "insert into test_users (name, email) values ('testuser', 'test@example.com')"
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO test_users (name, email) VALUES (?, ?)"
        );
        ps.setString(1, "testuser");
        ps.setString(2, "test@example.com");
        ps.execute();

        // Then
        PreparedStatement selectPs = conn.prepareStatement(
            "SELECT * FROM test_users WHERE email = ?"
        );
        selectPs.setString(1, "test@example.com");
        ResultSet rs = selectPs.executeQuery();

        assert rs.next();
        assert rs.getString("name").equals("testuser");

        conn.close();
    }

    @Test
    @DisplayName("SELECT 쿼리 - P6Spy SQL 로깅 확인")
    void testSelectQuery() throws Exception {
        // Given
        Connection conn = dataSource.getConnection();
        conn.prepareStatement(
            "CREATE TABLE IF NOT EXISTS test_users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), email VARCHAR(255))"
        ).execute();

        PreparedStatement insertPs = conn.prepareStatement(
            "INSERT INTO test_users (name, email) VALUES (?, ?)"
        );
        insertPs.setString(1, "user1");
        insertPs.setString(2, "user1@example.com");
        insertPs.execute();

        // When
        // 다음 쿼리는 P6Spy에서 로깅됩니다:
        // "select ... from test_users where name = 'user1'"
        PreparedStatement selectPs = conn.prepareStatement(
            "SELECT * FROM test_users WHERE name = ?"
        );
        selectPs.setString(1, "user1");
        ResultSet rs = selectPs.executeQuery();

        // Then
        assert rs.next();
        assert rs.getString("email").equals("user1@example.com");

        conn.close();
    }
}
