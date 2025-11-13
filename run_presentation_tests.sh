#!/bin/bash

################################################################################
# Presentation Layer Tests - 프레젠테이션 계층 테스트 (Controller/API)
#
# 목표: Controller, REST API, HTTP 요청/응답 테스트
# 특징: 실제 MySQL 연동, 통합 테스트, P6Spy 로그 선택 가능
#
# 사용법:
#   ./run_presentation_tests.sh                          # 모든 Controller 테스트
#   ./run_presentation_tests.sh -t OrderControllerTest   # 특정 Controller 테스트
#   ./run_presentation_tests.sh -p                       # P6Spy SQL 로그 포함
#   ./run_presentation_tests.sh -v                       # 상세 로그 출력
################################################################################

set -e

# ============================================================================
# 설정 및 상수
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# MySQL 8.0 PATH 설정 (Homebrew 기반)
# macOS에서 mysql 명령어를 인식하기 위해 PATH에 MySQL bin 디렉토리 추가
if [ -d "/usr/local/opt/mysql@8.0/bin" ]; then
    # macOS Intel
    export PATH="/usr/local/opt/mysql@8.0/bin:$PATH"
elif [ -d "/opt/homebrew/opt/mysql@8.0/bin" ]; then
    # macOS M1/M2/M3
    export PATH="/opt/homebrew/opt/mysql@8.0/bin:$PATH"
fi

LOG_LEVEL="warn"
TEST_CLASS=""
ENABLE_P6SPY=false
GRADLE_ARGS=""

# MySQL 설정
MYSQL_HOST="localhost"
MYSQL_PORT="3306"
CONFIG_FILE="$SCRIPT_DIR/src/test/resources/application-test.yml"
MYSQL_DB="ecommerce_test"

# SQL 파일
CREATE_TABLES_SQL="$SCRIPT_DIR/src/test/resources/create_tables.sql"
TEST_DATA_SQL="$SCRIPT_DIR/src/test/resources/data.sql"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# ============================================================================
# 함수 정의
# ============================================================================

print_header() {
    echo -e "${BLUE}=================================================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}=================================================================================${NC}"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_debug() {
    echo -e "${CYAN}[DEBUG]${NC} $1"
}

print_p6spy() {
    echo -e "${MAGENTA}[P6SPY]${NC} $1"
}

show_usage() {
    cat << EOF
프레젠테이션 레이어 테스트 실행 스크립트

사용법:
    ./run_presentation_tests.sh [옵션]

옵션:
    -t, --test <TestName>     특정 Controller 테스트 클래스 실행
                              예: -t OrderControllerTest
    -v, --verbose             상세 로그 출력
    -p, --p6spy               P6Spy SQL 로그 활성화
    -h, --help               도움말 표시

예시:
    ./run_presentation_tests.sh                          # 모든 Controller 테스트
    ./run_presentation_tests.sh -t OrderControllerTest   # 특정 테스트
    ./run_presentation_tests.sh -p                       # P6Spy 로그 포함
    ./run_presentation_tests.sh -v -p                    # 상세 로그 + P6Spy

EOF
}

load_db_config() {
    print_info "application-test.yml에서 DB 설정을 읽는 중..."

    if [ ! -f "$CONFIG_FILE" ]; then
        print_error "설정 파일을 찾을 수 없습니다: $CONFIG_FILE"
        return 1
    fi

    # YAML 파싱: grep + sed를 사용하여 username과 password 추출
    # macOS와 Linux 모두 호환 가능
    MYSQL_USER=$(grep "^\s*username:" "$CONFIG_FILE" | head -1 | sed 's/.*username: *//' | tr -d "'" | tr -d '"' | tr -d ' ')
    MYSQL_PASSWORD=$(grep "^\s*password:" "$CONFIG_FILE" | head -1 | sed 's/.*password: *//' | tr -d "'" | tr -d '"' | tr -d ' ')

    # 검증
    if [ -z "$MYSQL_USER" ]; then
        print_error "application-test.yml에서 username을 찾을 수 없습니다"
        return 1
    fi

    if [ -z "$MYSQL_PASSWORD" ]; then
        print_error "application-test.yml에서 password를 찾을 수 없습니다"
        return 1
    fi

    print_success "DB 설정을 성공적으로 로드했습니다"
    print_debug "  사용자: $MYSQL_USER"
    return 0
}

check_mysql() {
    print_info "MySQL 서버 확인 중..."

    if ! command -v mysql &> /dev/null; then
        print_error "MySQL 클라이언트를 찾을 수 없습니다"
        print_error "MySQL 8.0이 설치되어 있는지 확인해주세요"
        return 1
    fi

    # --password 옵션 사용 (특수문자가 있는 비밀번호 안전하게 처리)
    if ! mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" --password="$MYSQL_PASSWORD" -e "SELECT 1" &> /dev/null; then
        print_error "MySQL 서버에 연결할 수 없습니다"
        print_error "  호스트: $MYSQL_HOST:$MYSQL_PORT"
        print_error "  사용자: $MYSQL_USER"
        print_error "  MySQL 8.0 서비스가 실행 중인지 확인해주세요"
        print_error "  예: brew services list"
        return 1
    fi

    print_success "MySQL 서버 정상 (localhost:3306)"
    return 0
}

prepare_database() {
    print_info "테스트 데이터베이스 준비 중..."

    # 기존 DB 삭제
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" --password="$MYSQL_PASSWORD" \
        -e "DROP DATABASE IF EXISTS $MYSQL_DB;" 2>/dev/null || true

    # DB 생성
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" --password="$MYSQL_PASSWORD" \
        -e "CREATE DATABASE $MYSQL_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null

    print_success "테스트 데이터베이스 생성 완료"

    # 테이블 생성
    if [ -f "$CREATE_TABLES_SQL" ]; then
        print_info "테이블 생성 중..."
        mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" --password="$MYSQL_PASSWORD" "$MYSQL_DB" \
            < "$CREATE_TABLES_SQL" 2>/dev/null
        print_success "테이블 생성 완료"
    else
        print_warning "테이블 생성 SQL 파일을 찾을 수 없습니다: $CREATE_TABLES_SQL"
    fi

    # 테스트 데이터 삽입
    if [ -f "$TEST_DATA_SQL" ]; then
        print_info "테스트 데이터 삽입 중..."
        mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" --password="$MYSQL_PASSWORD" "$MYSQL_DB" \
            < "$TEST_DATA_SQL" 2>/dev/null
        print_success "테스트 데이터 삽입 완료"
    else
        print_warning "테스트 데이터 파일을 찾을 수 없습니다: $TEST_DATA_SQL"
    fi
}

# ============================================================================
# 옵션 파싱
# ============================================================================

while [[ $# -gt 0 ]]; do
    case $1 in
        -t|--test)
            TEST_CLASS="$2"
            shift 2
            ;;
        -v|--verbose)
            LOG_LEVEL="info"
            shift
            ;;
        -p|--p6spy)
            ENABLE_P6SPY=true
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            print_error "알 수 없는 옵션: $1"
            show_usage
            exit 1
            ;;
    esac
done

# ============================================================================
# 메인 실행
# ============================================================================

print_header "프레젠테이션 레이어 테스트 시작"
print_info "로그 레벨: $LOG_LEVEL"
print_info "P6Spy 로그: $([ "$ENABLE_P6SPY" = true ] && echo '활성화' || echo '비활성화')"

# DB 설정 로드 (application-test.yml에서)
if ! load_db_config; then
    print_error "DB 설정을 로드할 수 없습니다"
    exit 1
fi

# MySQL 확인 및 DB 준비
if ! check_mysql; then
    print_error "MySQL 서버를 시작해주세요"
    exit 1
fi

prepare_database

# P6Spy 설정
if [ "$ENABLE_P6SPY" = true ]; then
    print_p6spy "P6Spy SQL 로그가 활성화됩니다"
    print_p6spy "실행 중 발생하는 모든 SQL 쿼리가 로그됩니다"
fi

# Gradle 인자 구성
declare -a GRADLE_CMD=("./gradlew" "test" "--$LOG_LEVEL")

# P6Spy 프로필 추가
if [ "$ENABLE_P6SPY" = true ]; then
    export SPRING_PROFILES_ACTIVE="test,p6spy"
    print_info "활성 프로필: test, p6spy"
else
    export SPRING_PROFILES_ACTIVE="test"
    print_info "활성 프로필: test"
fi

# 테스트 클래스 지정
if [ -n "$TEST_CLASS" ]; then
    GRADLE_CMD+=("--tests" "com.hhplus.ecommerce.presentation.*$TEST_CLASS")
    print_info "테스트 대상: $TEST_CLASS"
else
    # 프레젠테이션 레이어 패키지 기준으로 테스트 실행
    # com.hhplus.ecommerce.presentation.* 패키지의 모든 테스트 실행
    GRADLE_CMD+=("--tests" "com.hhplus.ecommerce.presentation.*")
    print_info "테스트 대상: 프레젠테이션 레이어 패키지 (com.hhplus.ecommerce.presentation.*)"
fi

print_info "Gradle 명령어: ${GRADLE_CMD[*]}"
echo ""

# Gradle 테스트 실행
cd "$SCRIPT_DIR"

set +e
"${GRADLE_CMD[@]}"
GRADLE_EXIT_CODE=$?
set -e

echo ""
if [ $GRADLE_EXIT_CODE -eq 0 ]; then
    print_header "✅ 프레젠테이션 레이어 테스트 완료"
    print_success "프레젠테이션 테스트 실행: 성공"

    # 테스트 결과 요약 출력
    echo ""
    echo -e "${GREEN}========== 테스트 결과 요약 ==========${NC}"
    echo "레이어: Presentation (Controller/API)"
    echo "패키지: com.hhplus.ecommerce.presentation.*"
    echo "DB 사용: MySQL (테스트 데이터)"
    echo "SQL 로깅: $([ "$ENABLE_P6SPY" = true ] && echo 'P6Spy 활성화' || echo '비활성화')"
    echo "특징: HTTP 요청/응답, REST API 검증"
    echo ""

    if [ "$ENABLE_P6SPY" = true ]; then
        echo -e "${MAGENTA}P6Spy SQL 로그:${NC}"
        echo "  - 콘솔 출력에서 SQL 쿼리 확인 가능"
        echo "  - 로그 파일: logs/p6spy.log (설정된 경우)"
        echo ""
    fi
else
    print_header "❌ 프레젠테이션 레이어 테스트 실패"
    print_error "프레젠테이션 레이어 테스트 실행: 실패"
    echo ""
    echo -e "${YELLOW}========== 실패 원인 분석 ==========${NC}"
    echo "테스트 리포트: build/reports/tests/test/index.html"
    echo ""
    print_warning "해결 방법:"
    echo "1. 테스트 리포트에서 실패한 테스트 확인"
    echo "2. 위 로그에서 에러 메시지 확인"
    echo "3. DB 설정(application-test.yml) 확인"
    echo "4. MySQL 서버 상태 확인: brew services list"
    echo ""
    exit 1
fi
