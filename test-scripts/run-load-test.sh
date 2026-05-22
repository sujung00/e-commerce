#!/usr/bin/env bash
# run-load-test.sh
# Docker JMeter CLI 모드로 쿠폰 발급 동시성 부하 테스트를 실행하고 결과를 분석한다.
#
# 사용법:
#   ./run-load-test.sh
#   SERVER_PORT=9090 COUPON_ID=2 ./run-load-test.sh
#
# 사전 조건:
#   1. Docker 실행 중
#   2. ./gradlew bootRun  (Spring Boot 앱, 기본 포트 8080)
#   3. DB: coupon_id=${COUPON_ID} 재고 10개, 사용자 ID 1~100 존재

set -euo pipefail

# ── 설정 ──────────────────────────────────────────────────────────────────────
JMETER_IMAGE="justb4/jmeter:5.6.3"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JMX_FILE="${SCRIPT_DIR}/coupon_concurrency_test.jmx"
RESULTS_DIR="${SCRIPT_DIR}/results"
JTL_FILE="${RESULTS_DIR}/result.jtl"
REPORT_DIR="${RESULTS_DIR}/report"

# 환경변수로 덮어쓸 수 있다 (예: SERVER_PORT=9090 ./run-load-test.sh)
SERVER_PORT="${SERVER_PORT:-8080}"
COUPON_ID="${COUPON_ID:-1}"

# Docker에서 로컬호스트(Host OS)를 가리키는 특수 도메인
DOCKER_HOST_ADDR="host.docker.internal"

# ── 색상 코드 ─────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

log_info()  { echo -e "${CYAN}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERR ]${NC} $*" >&2; }

# ── 배너 ──────────────────────────────────────────────────────────────────────
print_banner() {
  echo -e "\n${BOLD}╔══════════════════════════════════════════════════╗${NC}"
  echo -e "${BOLD}║     쿠폰 발급 동시성 JMeter 부하 테스트         ║${NC}"
  echo -e "${BOLD}╠══════════════════════════════════════════════════╣${NC}"
  echo -e "${BOLD}║  전략     : POST /api/coupons/issue (Pessimistic)║${NC}"
  echo -e "${BOLD}║  가상사용자: 100명 (Synchronizing Timer 동시출발)║${NC}"
  printf  "${BOLD}║  시작시각  : %-34s ║${NC}\n" "$(date '+%Y-%m-%d %H:%M:%S')"
  echo -e "${BOLD}╚══════════════════════════════════════════════════╝${NC}\n"
}

# ── 1. 사전 체크 ──────────────────────────────────────────────────────────────
check_prerequisites() {
  log_info "[1/5] 사전 조건 확인..."

  # Docker 실행 여부
  if ! docker info > /dev/null 2>&1; then
    log_error "Docker daemon이 실행 중이지 않습니다. Docker를 시작하세요."
    exit 1
  fi
  log_ok "Docker 실행 중 ($(docker --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1))"

  # JMX 파일 존재 여부
  if [ ! -f "${JMX_FILE}" ]; then
    log_error "JMX 파일 없음: ${JMX_FILE}"
    exit 1
  fi
  log_ok "JMX 파일 확인"

  # Spring Boot 앱 헬스 체크
  log_info "서버 헬스 체크 (localhost:${SERVER_PORT})..."
  local http_code
  http_code=$(curl -sf -o /dev/null -w "%{http_code}" \
    --connect-timeout 3 \
    "http://localhost:${SERVER_PORT}/api/coupons" 2>/dev/null || echo "000")

  if [ "${http_code}" = "000" ]; then
    echo ""
    log_warn "서버 응답 없음 — Spring Boot 앱이 실행 중이지 않습니다."
    echo -e "  ${BOLD}./gradlew bootRun${NC}  으로 앱을 먼저 기동하세요.\n"
    read -rp "  그래도 계속 진행하시겠습니까? (y/N): " confirm
    [[ "${confirm}" =~ ^[Yy]$ ]] || { log_info "테스트 중단."; exit 0; }
    echo ""
  else
    log_ok "서버 응답 확인 (HTTP ${http_code})"
  fi
}

# ── 2. 결과 디렉토리 초기화 ───────────────────────────────────────────────────
prepare_results() {
  log_info "[2/5] 결과 디렉토리 초기화..."
  mkdir -p "${RESULTS_DIR}"
  rm -f "${JTL_FILE}"
  rm -rf "${REPORT_DIR}"
  log_ok "결과 경로: ${RESULTS_DIR}"
}

# ── 3. JMeter 이미지 준비 ─────────────────────────────────────────────────────
pull_image() {
  log_info "[3/5] JMeter 이미지 준비 (${JMETER_IMAGE})..."
  if docker image inspect "${JMETER_IMAGE}" > /dev/null 2>&1; then
    log_ok "로컬 이미지 캐시 사용"
  else
    docker pull "${JMETER_IMAGE}" 2>&1 | grep -E "Pull|Digest|Status" || true
    log_ok "이미지 다운로드 완료"
  fi
}

# ── 4. JMeter 실행 ────────────────────────────────────────────────────────────
run_jmeter() {
  log_info "[4/5] JMeter 테스트 실행..."
  echo -e "  대상: http://localhost:${SERVER_PORT}/api/coupons/issue"
  echo -e "  쿠폰 ID: ${COUPON_ID}  |  가상 사용자: 100명\n"

  # Linux에서는 host.docker.internal이 자동 등록되지 않으므로 명시적으로 추가
  local host_flag=""
  [[ "$(uname -s)" == "Linux" ]] && host_flag="--add-host=host.docker.internal:host-gateway"

  # shellcheck disable=SC2086
  docker run --rm \
    ${host_flag} \
    -v "${SCRIPT_DIR}:/scripts:ro" \
    -v "${RESULTS_DIR}:/results" \
    "${JMETER_IMAGE}" \
    -n \
    -t /scripts/coupon_concurrency_test.jmx \
    -l /results/result.jtl \
    -e -o /results/report \
    -JSERVER_HOST="${DOCKER_HOST_ADDR}" \
    -JSERVER_PORT="${SERVER_PORT}" \
    -JCOUPON_ID="${COUPON_ID}" \
    -JRESULTS_DIR="/results" \
    2>&1 | grep --line-buffered -v "^[[:space:]]*$" | \
    grep --line-buffered -E "summary|error|ERROR|thread|Waiting|Starting|Finished|jmeter" || true

  echo ""
  log_ok "JMeter 실행 완료"
}

# ── 5. 결과 확인 ──────────────────────────────────────────────────────────────
check_output() {
  log_info "[5/5] 결과 파일 확인..."

  if [ ! -f "${JTL_FILE}" ]; then
    log_error "result.jtl가 생성되지 않았습니다 — JMeter 로그를 확인하세요."
    exit 1
  fi

  local lines
  lines=$(wc -l < "${JTL_FILE}")
  log_ok "result.jtl 생성 (${lines} 행)"

  if [ -d "${REPORT_DIR}" ]; then
    log_ok "HTML 리포트: ${REPORT_DIR}/index.html"
  fi
  echo ""
}

# ── 6. 결과 분석 브리핑 (Python3) ─────────────────────────────────────────────
analyze_and_brief() {
  python3 - "${JTL_FILE}" << 'PYEOF'
import csv, sys, os

# ── ANSI 색상 ──
RED   = '\033[0;31m'
GREEN = '\033[0;32m'
CYAN  = '\033[0;36m'
YELL  = '\033[1;33m'
BOLD  = '\033[1m'
NC    = '\033[0m'

def pct_bar(pct, width=16):
    filled = round(pct / 100 * width)
    return f"{'█' * filled}{'░' * (width - filled)}"

def percentile(lst, p):
    if not lst: return 0
    s = sorted(lst)
    return s[max(0, int(len(s) * p / 100) - 1)]

# ── 파일 읽기 ──
jtl = sys.argv[1]
if not os.path.exists(jtl):
    print(f"{RED}결과 파일 없음: {jtl}{NC}")
    sys.exit(1)

with open(jtl, newline='', encoding='utf-8') as f:
    rows = list(csv.DictReader(f))

if not rows:
    print(f"{RED}결과 없음 — JMeter 로그를 확인하세요.{NC}")
    sys.exit(0)

# ── 지표 계산 ──
total  = len(rows)
errors = sum(1 for r in rows if r.get('success', 'true').lower() == 'false')

elapsed    = [int(r['elapsed'])   for r in rows if r.get('elapsed','').isdigit()]
lat        = [int(r['Latency'])   for r in rows if r.get('Latency','').isdigit()]
timestamps = [int(r['timeStamp']) for r in rows if r.get('timeStamp','').isdigit()]

avg_e   = sum(elapsed) / len(elapsed) if elapsed else 0
avg_lat = sum(lat)     / len(lat)     if lat     else 0
min_e   = min(elapsed) if elapsed else 0
max_e   = max(elapsed) if elapsed else 0
p50     = percentile(elapsed, 50)
p90     = percentile(elapsed, 90)
p99     = percentile(elapsed, 99)

if len(timestamps) > 1:
    dur_sec = (max(timestamps) - min(timestamps)) / 1000.0
    tps     = total / dur_sec if dur_sec > 0 else 0
else:
    dur_sec = avg_e / 1000.0 * total if avg_e else 0
    tps     = 1 / (avg_e / 1000.0) if avg_e else 0

err_rate = errors / total * 100

# HTTP 응답 코드 집계
codes = {}
for r in rows:
    c = r.get('responseCode', 'N/A')
    codes[c] = codes.get(c, 0) + 1

W = 54  # 구분선 너비

def divider(char='─'): print(f"  {char * W}")
def row(label, value, color=''):
    reset = NC if color else ''
    print(f"  {label:<24} {color}{value}{reset}")

# ── 출력 ──────────────────────────────────────────────────────────────────────
print(f"\n{BOLD}{'═' * (W+2)}{NC}")
print(f"{BOLD}   📊 부하 테스트 결과 브리핑{NC}")
print(f"{BOLD}{'─' * (W+2)}{NC}")

print(f"\n  {BOLD}[ 요청 현황 ]{NC}")
row("총 요청 수",     f"{total} 건")
row("성공 (2xx)",    f"{total - errors} 건", GREEN)
err_c = RED if errors > 0 else GREEN
row("실패 (에러)",   f"{errors} 건",         err_c)
err_pct_c = RED if err_rate > 5 else (YELL if err_rate > 0 else GREEN)
row("에러율",        f"{err_rate:.1f} %",    err_pct_c)

divider()
print(f"\n  {BOLD}[ 응답 시간 ]{NC}")
row("평균 응답시간",  f"{avg_e:.0f} ms")
row("최소 응답시간",  f"{min_e} ms")
row("최대 응답시간",  f"{max_e} ms")
row("P50 (중간값)",  f"{p50} ms")
row("P90",           f"{p90} ms")
row("P99",           f"{p99} ms")
row("평균 레이턴시",  f"{avg_lat:.0f} ms",   CYAN)

divider()
print(f"\n  {BOLD}[ 처리량 (TPS) ]{NC}")
row("테스트 지속시간", f"{dur_sec:.2f} 초")
tps_c = GREEN if tps >= 10 else (CYAN if tps >= 5 else RED)
row("실측 TPS",       f"{tps:.1f} req/s",  tps_c)

divider()
print(f"\n  {BOLD}[ HTTP 응답 코드 분포 ]{NC}")
for code, cnt in sorted(codes.items()):
    pct    = cnt / total * 100
    c_col  = GREEN if str(code).startswith('2') \
             else (RED   if str(code).startswith('5') else CYAN)
    print(f"  {c_col}{str(code):<5}{NC} │ {cnt:>4} 건 ({pct:>5.1f}%) │ {pct_bar(pct)}")

divider()
print(f"\n  {BOLD}[ 동시성 제어 판정 — Pessimistic Lock ]{NC}")
c201 = codes.get('201', 0)
if c201 <= 10 and c201 > 0 and err_rate < 5:
    print(f"  {GREEN}{BOLD}✅ 정상{NC}: 201 Created = {c201}건 (재고 {c201}/10 발급)")
elif c201 == 0:
    print(f"  {YELL}{BOLD}⚠️  주의{NC}: 201 Created = 0건 — DB 데이터 및 서버 상태 확인")
elif c201 > 10:
    print(f"  {RED}{BOLD}❌ 이상{NC}: 201 Created = {c201}건 — 재고 초과 발급 감지!")
else:
    print(f"  {CYAN}ℹ️  정보{NC}: 응답 코드 분포를 확인하세요.")

print(f"\n{BOLD}{'═' * (W+2)}{NC}\n")
PYEOF
}

# ── 메인 ──────────────────────────────────────────────────────────────────────
main() {
  print_banner
  check_prerequisites
  prepare_results
  pull_image
  run_jmeter
  check_output
  analyze_and_brief
  echo -e "  ${CYAN}HTML 리포트 열기:${NC}  open \"${REPORT_DIR}/index.html\""
  echo -e "  ${CYAN}원본 JTL 경로:${NC}     ${JTL_FILE}\n"
}

main "$@"