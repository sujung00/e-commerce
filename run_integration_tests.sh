#!/bin/bash

# ============================================
# E-Commerce Spring Boot 통합 테스트 실행 스크립트
#
# 목적:
#   Gradle 기반 Spring Boot 프로젝트의 통합 테스트를 자동화
#   MySQL 테스트 DB 초기화부터 테스트 실행까지 한 번에 처리
#
# 주요 기능:
#   1. MySQL 서버 상태 확인 및 테스트 DB 생성
#   2. init-mysql-test.sql 실행 (DB 초기화)
#   3. src/test/resources/create_tables.sql 실행 (10개 테이블 생성)
#   4. 테이블 존재 여부 검증 (10/10)
#   5. 테스트 프로필(application-test.yml) 확인
#   6. Spring Boot application-test.yml 자동 로드
#   7. Hibernate DDL create-drop 스키마 생성
#   8. src/test/resources/data.sql 자동 데이터 삽입
#   9. P6Spy SQL 로깅 활성화
#   10. Gradle test 명령 실행 (--info, -i, --tests 플래그 지원)
#   11. 테스트 결과 요약 출력
#   12. 테스트 후 자동 정리 (스키마 삭제)
#
# 사용법:
#   ./run_integration_tests.sh              # 기본 테스트 (--info 플래그)
#   ./run_integration_tests.sh -v           # 상세 로그 (-i 플래그)
#   ./run_integration_tests.sh -p           # P6Spy SQL 로그만
#   ./run_integration_tests.sh -t TestName  # 특정 테스트만 실행
#   ./run_integration_tests.sh -h           # 도움말 표시
#
# 필수 파일:
#   - src/test/resources/application-test.yml: 테스트 환경 설정
#   - src/test/resources/init-mysql-test.sql: DB 초기화 SQL
#   - src/test/resources/create_tables.sql: 테이블 스키마
#   - src/test/resources/data.sql: 테스트 데이터
#
# 설정:
#   - 데이터베이스: ecommerce_test
#   - 문자셋: utf8mb4
#   - 엔진: InnoDB
#   - 사용자: root (application-test.yml에서 비밀번호 읽음)
#
# ============================================

set -e

# MySQL 8.0 PATH 설정 (Homebrew 기반)
if [ -d "/usr/local/opt/mysql@8.0/bin" ]; then
    # macOS Intel
    export PATH="/usr/local/opt/mysql@8.0/bin:$PATH"
elif [ -d "/opt/homebrew/opt/mysql@8.0/bin" ]; then
    # macOS M1/M2/M3
    export PATH="/opt/homebrew/opt/mysql@8.0/bin:$PATH"
fi

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 설정
DB_USER="root"
DB_NAME="ecommerce_test"
CHARSET="utf8mb4"

# application-test.yml에서 비밀번호 읽어오기
read_db_password() {
    local test_yml="src/test/resources/application-test.yml"

    if [ ! -f "$test_yml" ]; then
        log_error "application-test.yml 파일을 찾을 수 없습니다: $test_yml"
        exit 1
    fi

    # YAML 파일에서 password: 라인을 찾아 값 추출
    # 예: "    password: Happy0904*" -> "Happy0904*"
    local password=$(grep "^\s*password:" "$test_yml" | sed 's/^[[:space:]]*password:[[:space:]]*//')

    if [ -z "$password" ]; then
        log_error "application-test.yml에서 password 설정을 찾을 수 없습니다."
        exit 1
    fi

    echo "$password"
}

# 비밀번호 초기화
DB_PASSWORD=$(read_db_password)

# 함수: 로그 출력
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# 함수: MySQL 서버 확인
check_mysql() {
    log_info "MySQL 서버 상태 확인 중..."
    if ! command -v mysql &> /dev/null; then
        log_error "MySQL이 설치되지 않았습니다."
        exit 1
    fi

    if ! mysqladmin ping -u"$DB_USER" -p"$DB_PASSWORD" &> /dev/null; then
        log_error "MySQL 서버가 실행 중이 아니거나 비밀번호가 잘못되었습니다."
        echo "해결 방법: brew services start mysql"
        exit 1
    fi
    log_success "MySQL 서버 정상 작동 중"
}

# 함수: 데이터베이스 생성
create_database() {
    log_info "데이터베이스 생성 중: $DB_NAME"
    mysql -u "$DB_USER" -p"$DB_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET $CHARSET;"
    log_success "데이터베이스 생성 완료"
}

# 함수: 테이블 생성 (외부 SQL 파일 사용)
create_tables() {
    log_info "테이블 스키마를 생성하고 확인 중..."

    local sql_file="src/test/resources/create_tables.sql"

    # SQL 파일 존재 확인
    if [ ! -f "$sql_file" ]; then
        log_error "SQL 파일을 찾을 수 없습니다: $sql_file"
        exit 1
    fi

    # SQL 파일을 리다이렉션으로 실행
    if mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < "$sql_file"; then
        log_success "모든 테이블 생성 완료"
    else
        log_error "테이블 생성 중 오류 발생"
        exit 1
    fi
}

# 함수: 테이블 존재 확인
verify_tables() {
    log_info "테이블 존재 여부 확인 중..."

    local tables=("users" "products" "product_options" "carts" "cart_items" "orders" "order_items" "coupons" "user_coupons" "outbox")
    local missing_tables=()

    for table in "${tables[@]}"; do
        local count=$(mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -se "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$table';")

        if [ "$count" -eq 0 ]; then
            missing_tables+=("$table")
        fi
    done

    if [ ${#missing_tables[@]} -eq 0 ]; then
        log_success "모든 테이블이 존재합니다 (10/10)"
        return 0
    else
        log_warning "누락된 테이블: ${missing_tables[*]}"
        return 1
    fi
}

# 함수: init-mysql-test.sql 실행
run_init_sql() {
    local init_sql="src/test/resources/init-mysql-test.sql"

    if [ -f "$init_sql" ]; then
        log_info "init-mysql-test.sql 실행 중..."
        if mysql -u "$DB_USER" -p"$DB_PASSWORD" < "$init_sql"; then
            log_success "init-mysql-test.sql 실행 완료"
        else
            log_warning "init-mysql-test.sql 실행 중 오류 발생 (무시하고 계속 진행)"
        fi
    else
        log_warning "init-mysql-test.sql 파일이 없습니다: $init_sql"
    fi
}

# 함수: 테스트 프로필 확인
verify_test_profile() {
    log_info "테스트 프로필(application-test.yml) 확인 중..."

    local test_yml="src/test/resources/application-test.yml"

    if [ ! -f "$test_yml" ]; then
        log_error "application-test.yml 파일을 찾을 수 없습니다: $test_yml"
        exit 1
    fi

    log_success "테스트 프로필: $test_yml"
}

# 함수: 기본 테스트 실행 (integration 패키지만)
run_basic_tests() {
    log_info "통합 테스트 실행 중 (integration 패키지만)..."
    log_info "테스트 필터: com.hhplus.ecommerce.integration.*"
    log_info "명령: ./gradlew test --warn --tests 'com.hhplus.ecommerce.integration.*'"

    set +e
    ./gradlew test --warn --tests 'com.hhplus.ecommerce.integration.*'
    test_exit=$?
    set -e

    if [ $test_exit -eq 0 ]; then
        log_success "테스트 성공"
        return 0
    else
        log_error "테스트 실패 (종료 코드: $test_exit)"
        print_failure_analysis
        return 1
    fi
}

# 함수: 상세 로그와 함께 테스트 실행 (integration 패키지만)
run_verbose_tests() {
    log_info "통합 테스트 실행 중 (상세 로그, integration 패키지만)..."
    log_info "테스트 필터: com.hhplus.ecommerce.integration.*"
    log_info "명령: ./gradlew test --info --tests 'com.hhplus.ecommerce.integration.*'"

    set +e
    ./gradlew test --info --tests 'com.hhplus.ecommerce.integration.*'
    test_exit=$?
    set -e

    if [ $test_exit -eq 0 ]; then
        log_success "테스트 성공"
        return 0
    else
        log_error "테스트 실패 (종료 코드: $test_exit)"
        print_failure_analysis
        return 1
    fi
}

# 함수: P6Spy 로그와 함께 테스트 실행 (integration 패키지만)
run_p6spy_tests() {
    log_info "통합 테스트 실행 중 (P6Spy SQL 로깅, integration 패키지만)..."
    log_info "활성 프로필: test,p6spy"
    log_info "테스트 필터: com.hhplus.ecommerce.integration.*"

    export SPRING_PROFILES_ACTIVE="test,p6spy"

    set +e
    ./gradlew test --warn --tests 'com.hhplus.ecommerce.integration.*' 2>&1 | tee /tmp/integration_test.log
    test_exit=$?
    set -e

    unset SPRING_PROFILES_ACTIVE

    echo ""
    if [ $test_exit -eq 0 ]; then
        log_success "P6Spy SQL 로그 출력 완료"
        log_info "전체 테스트 로그: /tmp/integration_test.log"
    else
        log_error "테스트 실패 (종료 코드: $test_exit)"
        print_failure_analysis
    fi

    return $test_exit
}

# 함수: 특정 테스트 실행 (integration 패키지만)
run_specific_test() {
    local test_name=$1
    log_info "특정 테스트 실행 중: $test_name (integration 패키지)"
    log_info "명령: ./gradlew test --warn --tests '*$test_name*'"

    set +e
    ./gradlew test --warn --tests "*$test_name*"
    test_exit=$?
    set -e

    if [ $test_exit -eq 0 ]; then
        log_success "테스트 성공"
        return 0
    else
        log_error "테스트 실패 (종료 코드: $test_exit)"
        print_failure_analysis
        return 1
    fi
}

# 함수: 테스트 실패 분석
print_failure_analysis() {
    echo ""
    echo -e "${RED}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║   테스트 실패 원인 분석                                 ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════════════════════╝${NC}"
    echo ""

    echo -e "${YELLOW}📋 실패한 테스트 확인:${NC}"
    echo "  1. 테스트 리포트 확인: build/reports/tests/test/index.html"
    echo "  2. 실패한 테스트 클래스 찾기"
    echo "  3. 실패 메시지 읽기"
    echo ""

    echo -e "${YELLOW}🔧 일반적인 해결 방법:${NC}"
    echo ""
    echo "  [1] MySQL 연결 실패"
    echo "      → brew services start mysql"
    echo "      → application-test.yml의 비밀번호로 MySQL 연결 테스트"
    echo "      → mysql -u root -p<password> -e 'SELECT 1;'"
    echo ""
    echo "  [2] 데이터베이스 설정 오류"
    echo "      → application-test.yml 설정 확인"
    echo "      → username/password 일치 확인"
    echo "      → MySQL 8.0 문자셋(utf8mb4) 확인"
    echo ""
    echo "  [3] 테이블 생성 실패"
    echo "      → create_tables.sql 문법 검증"
    echo "      → FOREIGN KEY 제약 확인"
    echo "      → build/reports/tests/test/index.html에서 자세한 오류 확인"
    echo ""
    echo "  [4] 테스트 데이터 누락"
    echo "      → data.sql 파일 존재 확인"
    echo "      → 테스트 데이터 INSERT 구문 검증"
    echo ""
    echo "  [5] P6Spy 로깅 실패"
    echo "      → application-test.yml에서 P6Spy 설정 확인:"
    echo "        spring.datasource.driver-class-name: com.p6spy.engine.spy.P6SpyDriver"
    echo "        spring.datasource.url: jdbc:p6spy:mysql://..."
    echo "      → P6Spy 라이브러리 의존성 확인 (build.gradle)"
    echo ""
    echo -e "${YELLOW}📊 자세한 로그 확인:${NC}"
    echo "  - 표준 오류: 위 출력 메시지 보기"
    echo "  - 테스트 리포트: build/reports/tests/test/index.html"
    echo "  - P6Spy 로그: /tmp/integration_test.log (if -p 옵션 사용)"
    echo ""
    echo -e "${YELLOW}🚀 재시도 방법:${NC}"
    echo "  ./run_integration_tests.sh -v     # 상세 로그로 재시도"
    echo "  ./run_integration_tests.sh -p     # P6Spy SQL 로그 포함"
    echo ""
}

# 함수: 테스트 결과 요약 출력
print_test_summary() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║   테스트 실행 완료                                      ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${GREEN}✓ 테스트 환경 설정${NC}"
    echo "  - 데이터베이스: $DB_NAME"
    echo "  - 테스트 프로필: application-test.yml (spring.profiles.active=test)"
    echo "  - SQL 파일: src/test/resources/create_tables.sql"
    echo "  - 테스트 데이터: src/test/resources/data.sql"
    echo ""
    echo -e "${GREEN}✓ 테스트 범위${NC}"
    echo "  - Integration 계층: com.hhplus.ecommerce.integration.*"
    echo "  - MySQL 실 데이터베이스 연동"
    echo ""
    echo -e "${GREEN}✓ 테스트 작동 프로세스${NC}"
    echo "  1. MySQL 서버 상태 확인"
    echo "  2. init-mysql-test.sql 실행 (DB 초기화)"
    echo "  3. create_tables.sql 실행 (테이블 생성)"
    echo "  4. 테이블 존재 여부 검증 (10/10)"
    echo "  5. application-test.yml 프로필 활성화"
    echo "  6. Hibernate create-drop 스키마 생성"
    echo "  7. data.sql 자동 로드 (테스트 데이터)"
    echo "  8. P6Spy SQL 로깅 활성화"
    echo "  9. Presentation & Integration 테스트 실행"
    echo " 10. 테스트 후 스키마 자동 정리"
    echo ""
}

# 함수: 도움말 출력
print_help() {
    cat << EOF
사용법: ./run_integration_tests.sh [옵션]

옵션:
  (없음)           기본 테스트 실행 (integration 패키지만)
  -v, --verbose    상세 로그와 함께 테스트 실행 (--info 플래그)
  -p, --p6spy      P6Spy SQL 로깅 활성화하여 테스트 실행 (test,p6spy 프로필)
  -t, --test NAME  특정 테스트 클래스만 실행 (예: IntegrationTest)
  -h, --help       도움말 출력

테스트 범위:
  - Integration 계층: com.hhplus.ecommerce.integration.*
  - 기타 단위 테스트는 제외

예제:
  ./run_integration_tests.sh                          # integration 패키지 테스트 실행
  ./run_integration_tests.sh -v                       # 상세 로그로 실행
  ./run_integration_tests.sh -p                       # P6Spy SQL 로깅 포함
  ./run_integration_tests.sh -t IntegrationTest       # 특정 테스트만 실행

필수 파일:
  - src/test/resources/application-test.yml: 테스트 환경 설정
  - src/test/resources/init-mysql-test.sql: DB 초기화 SQL
  - src/test/resources/create_tables.sql: 테이블 스키마 정의
  - src/test/resources/data.sql: 테스트 데이터 INSERT

데이터베이스 설정:
  - 데이터베이스명: ecommerce_test
  - 사용자: root
  - 비밀번호: application-test.yml에서 읽음
  - 문자셋: utf8mb4
  - 엔진: InnoDB

테스트 프로필:
  - Spring Profile: test (application-test.yml에서 설정)
  - Hibernate DDL: create-drop (테스트 후 자동 정리)
  - P6Spy: 활성화 (SQL 로깅)

자동 실행 순서:
  1. MySQL 서버 상태 확인
  2. 테스트 프로필(application-test.yml) 확인
  3. ecommerce_test 데이터베이스 생성
  4. init-mysql-test.sql 실행 (DB 초기화)
  5. src/test/resources/create_tables.sql 실행
     - 10개 테이블 (users, products, product_options, carts, cart_items,
       orders, order_items, coupons, user_coupons, outbox)
     - FOREIGN KEY 및 제약조건 자동 설정
  6. 테이블 존재 여부 검증 (10/10 확인)
  7. application-test.yml 프로필 활성화
  8. Hibernate 스키마 생성 (create-drop)
  9. src/test/resources/data.sql 자동 로드
  10. P6Spy SQL 로깅 활성화
  11. Gradle 통합 테스트 실행
      - 명령: ./gradlew test --info (또는 --tests, -i 플래그)
  12. 테스트 결과 요약 출력
  13. 테스트 후 정리 (자동 롤백, 스키마 삭제)

테스트 필터 정보:
  -v 옵션: --info 플래그로 상세 정보 출력
  -p 옵션: SPRING_PROFILES_ACTIVE=test,p6spy로 P6Spy 활성화
  -t 옵션: 특정 테스트 클래스에 대한 완전한 출력

문제 해결:
  - MySQL 연결 오류:
    → brew services start mysql
    → application-test.yml에서 비밀번호 확인 후 연결 시도
    → mysql -u root -p<password> -e 'SELECT 1;'

  - application-test.yml 없음:
    → src/test/resources/ 확인

  - 테이블 생성 실패:
    → create_tables.sql 문법 확인
    → FOREIGN KEY 제약 확인
    → build/reports/tests/test/index.html에서 상세 오류 확인

  - P6Spy 로그 없음:
    → application-test.yml에서 P6Spy 드라이버 설정 확인
    → ./run_integration_tests.sh -p 로 재실행

성공 조건:
  ✓ Integration 테스트: 모두 통과
  ✓ MySQL 데이터 정합성: 확인됨
  ✓ 트랜잭션 롤백: 각 테스트 후 자동 수행

EOF
}

# 메인 함수
main() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║   E-Commerce Spring Boot 통합 테스트 실행 스크립트     ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
    echo ""

    # 인자 확인
    case "${1:-}" in
        -h|--help)
            print_help
            exit 0
            ;;
        -v|--verbose)
            check_mysql
            verify_test_profile
            create_database
            run_init_sql
            create_tables
            verify_tables
            run_verbose_tests
            test_result=$?
            print_test_summary
            exit $test_result
            ;;
        -p|--p6spy)
            check_mysql
            verify_test_profile
            create_database
            run_init_sql
            create_tables
            verify_tables
            run_p6spy_tests
            test_result=$?
            print_test_summary
            exit $test_result
            ;;
        -t|--test)
            if [ -z "$2" ]; then
                log_error "테스트 이름을 지정해주세요."
                echo "사용법: ./run_integration_tests.sh -t IntegrationTest"
                exit 1
            fi
            check_mysql
            verify_test_profile
            create_database
            run_init_sql
            create_tables
            verify_tables
            run_specific_test "$2"
            test_result=$?
            print_test_summary
            exit $test_result
            ;;
        "")
            check_mysql
            verify_test_profile
            create_database
            run_init_sql
            create_tables
            verify_tables
            run_basic_tests
            test_result=$?
            print_test_summary
            exit $test_result
            ;;
        *)
            log_error "알 수 없는 옵션: $1"
            print_help
            exit 1
            ;;
    esac
}

# 스크립트 실행
main "$@"
