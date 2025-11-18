#!/bin/bash

################################################################################
# Application Layer Tests - 서비스 레이어 비즈니스 로직 테스트
#
# 목표: 애플리케이션 레이어 서비스 로직의 단위 테스트 실행 (DB 불필요)
# 특징: Mock 기반 테스트, 의존성 격리, 빠른 실행
#
# 사용법:
#   ./run_application_tests.sh                          # 모든 애플리케이션 테스트
#   ./run_application_tests.sh -t CartServiceTest       # 특정 서비스 테스트
#   ./run_application_tests.sh -v                       # 상세 로그 출력
#
# 지원 테스트:
#   - CartServiceTest
#   - CouponServiceTest
#   - OrderServiceTest
#   - OrderCancelServiceTest
#   - OrderCancelTransactionServiceTest
#   - ProductServiceTest
#   - InventoryServiceTest
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
    cat << USAGEEOF
애플리케이션 레이어 테스트 실행 스크립트

사용법:
    ./run_application_tests.sh [옵션]

옵션:
    -t, --test <TestName>     특정 서비스 테스트 클래스 실행
                              예: -t CartServiceTest
    -v, --verbose             상세 로그 출력
    -h, --help                도움말 표시

예시:
    ./run_application_tests.sh                          # 모든 애플리케이션 테스트
    ./run_application_tests.sh -t CartServiceTest       # 특정 테스트
    ./run_application_tests.sh -v                       # 상세 로그 출력
    ./run_application_tests.sh -t OrderServiceTest -v   # 특정 테스트 + 상세 로그

지원되는 테스트 클래스:
    - CartServiceTest
    - CouponServiceTest
    - OrderServiceTest
    - OrderCancelServiceTest
    - OrderCancelTransactionServiceTest
    - ProductServiceTest
    - InventoryServiceTest

USAGEEOF
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

print_header "애플리케이션 레이어 테스트 시작"
print_info "로그 레벨: $LOG_LEVEL"

# Gradle 명령어 배열로 구성
declare -a gradle_cmd=("./gradlew" "test" "--$LOG_LEVEL")

if [ -n "$TEST_CLASS" ]; then
    gradle_cmd+=("--tests" "*$TEST_CLASS")
    print_info "테스트 클래스: $TEST_CLASS"
else
    # 애플리케이션 패키지 필터링 (IntegrationTest 제외)
    gradle_cmd+=("--tests" 'com.hhplus.ecommerce.application.*')
    print_info "테스트 필터: Application 패키지 테스트만"
fi

print_info "Gradle 명령어: ${gradle_cmd[*]}"
echo ""

# Gradle 테스트 실행
cd "$SCRIPT_DIR"

# 시작 시간 기록
START_TIME=$(date +%s)

# 배열을 통한 안전한 명령 실행
if "${gradle_cmd[@]}"; then
    END_TIME=$(date +%s)
    ELAPSED=$((END_TIME - START_TIME))

    print_success "애플리케이션 레이어 테스트 완료 (소요 시간: ${ELAPSED}초)"

    # 테스트 결과 요약 출력
    echo ""
    echo -e "${GREEN}========== 테스트 결과 요약 ==========${NC}"
    echo "레이어: Application (서비스 로직 테스트)"
    echo "DB 사용: 없음 (Mock 기반)"
    echo "의존성: 격리됨 (Mock 처리)"
    echo "테스트 타입: 단위 테스트"
    echo ""
else
    END_TIME=$(date +%s)
    ELAPSED=$((END_TIME - START_TIME))

    print_error "애플리케이션 레이어 테스트 실패 (소요 시간: ${ELAPSED}초)"
    exit 1
fi
