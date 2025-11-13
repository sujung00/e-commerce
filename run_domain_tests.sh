#!/bin/bash

################################################################################
# Domain Layer Tests - 순수 로직 테스트 (DB 불필요)
#
# 목표: 도메인 엔티티 및 비즈니스 로직의 순수 단위 테스트 실행
# 특징: DB 연동 없음, 의존성 최소화, 빠른 실행
#
# 사용법:
#   ./run_domain_tests.sh                          # 모든 도메인 테스트
#   ./run_domain_tests.sh -t UserDomainTest        # 특정 도메인 테스트
#   ./run_domain_tests.sh -v                       # 상세 로그 출력
################################################################################

set -e

# ============================================================================
# 설정 및 상수
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_LEVEL="warn"
TEST_CLASS=""
GRADLE_ARGS=""

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

show_usage() {
    cat << EOF
도메인 레이어 테스트 실행 스크립트

사용법:
    ./run_domain_tests.sh [옵션]

옵션:
    -t, --test <TestName>     특정 도메인 테스트 클래스 실행
                              예: -t UserDomainTest
    -v, --verbose             상세 로그 출력
    -h, --help                도움말 표시

예시:
    ./run_domain_tests.sh                          # 모든 도메인 테스트
    ./run_domain_tests.sh -t UserDomainTest        # 특정 테스트
    ./run_domain_tests.sh -v                       # 상세 로그 출력

EOF
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

print_header "도메인 레이어 테스트 시작"
print_info "로그 레벨: $LOG_LEVEL"

# Gradle 인자 구성
GRADLE_ARGS="test --$LOG_LEVEL"

# 테스트 클래스 지정
if [ -n "$TEST_CLASS" ]; then
    GRADLE_ARGS="$GRADLE_ARGS --tests '*$TEST_CLASS'"
    print_info "테스트 대상: $TEST_CLASS"
else
    # 도메인 레이어 테스트만 선택
    # *Test 패턴의 테스트 실행 (DomainTest, Test 등)
    # IntegrationTest, ServiceTest, RepositoryTest, ControllerTest 제외
    GRADLE_ARGS="$GRADLE_ARGS --tests '*Test' -x '*IntegrationTest' -x '*ServiceTest' -x '*RepositoryTest' -x '*ControllerTest'"
    print_info "테스트 대상: 도메인 레이어 테스트"
fi

echo ""

# Gradle 테스트 실행
cd "$SCRIPT_DIR"

# 배열을 사용하여 인자를 안전하게 전달
declare -a gradle_cmd=("./gradlew" "test" "--$LOG_LEVEL")

if [ -n "$TEST_CLASS" ]; then
    gradle_cmd+=("--tests" "*$TEST_CLASS")
    print_info "테스트 클래스: $TEST_CLASS"
else
    # 도메인 레이어 테스트만 실행
    # Domain 패키지의 테스트: *DomainTest, *Test (IntegrationTest는 제외)
    gradle_cmd+=("--tests" 'com.hhplus.ecommerce.domain.*')
    print_info "테스트 필터: Domain 패키지 테스트만"
fi

print_info "Gradle 명령어: ${gradle_cmd[*]}"

if "${gradle_cmd[@]}"; then
    print_success "도메인 레이어 테스트 완료"

    # 테스트 결과 요약 출력
    echo ""
    echo -e "${GREEN}========== 테스트 결과 요약 ==========${NC}"
    echo "레이어: Domain (순수 로직 테스트)"
    echo "DB 사용: 없음"
    echo "의존성: 최소화"
    echo ""
else
    print_error "도메인 레이어 테스트 실패"
    exit 1
fi
