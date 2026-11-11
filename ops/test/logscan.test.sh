#!/usr/bin/env bash
# Tests for ops/logscan.sh. Run from anywhere: ops/test/logscan.test.sh
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
SCAN="$HERE/../logscan.sh"
FX="$HERE/fixtures"
WORK=$(mktemp -d "${TMPDIR:-/tmp}/logscan-test.XXXXXX")
trap 'rm -rf "$WORK"; [[ -n ${FOLLOW_PID:-} ]] && kill "$FOLLOW_PID" 2>/dev/null' EXIT

PASS=0
FAIL=0
ok() { PASS=$((PASS + 1)); printf '  ok    %s\n' "$1"; }
bad() { FAIL=$((FAIL + 1)); printf '  FAIL  %s\n' "$1"; [[ -n ${2:-} ]] && printf '%s\n' "$2" | sed 's/^/        /'; }

# assert_has NAME OUTPUT REGEX
assert_has() { if grep -Eq -- "$3" <<<"$2"; then ok "$1"; else bad "$1" "expected /$3/ in:"$'\n'"$2"; fi; }
assert_not() { if grep -Eq -- "$3" <<<"$2"; then bad "$1" "did not expect /$3/"; else ok "$1"; fi; }
assert_exit() { if [[ $2 == "$3" ]]; then ok "$1"; else bad "$1" "exit $2, expected $3"; fi; }

run() { "$SCAN" "$@" 2>&1; }

echo "summary and parsing"
out=$(run "$FX/access.log" "$FX/app.log")
assert_exit "exits 0 with no thresholds" $? 0
assert_has "counts requests, 4xx and 5xx" "$out" 'requests 12 \| 4xx 3 \(25\.00%\) \| 5xx 1 \(8\.33%\)'
assert_has "counts slow requests" "$out" 'slow >= 1000 ms: 2'
assert_has "counts app WARN and ERROR" "$out" 'app WARN 6, ERROR 1'
assert_has "reports the time window across both logs" "$out" '2026-11-11 09:00:00 \.\. 2026-11-11 10:30:00'

echo "error codes"
assert_has "groups repeated codes" "$out" 'E-AUTH-401 +2 +401 +POST /api/auth/login'
assert_has "keeps the status and endpoint from handler lines" "$out" 'E-VER-001 +1 +409 +POST /api/staff/requests/\{requestNumber\}/verify'
assert_has "includes coded lines without a request" "$out" 'E-NTF-001 +1 +- +-'
assert_has "includes 5xx codes logged at ERROR" "$out" 'E-SYS-500 +1 +500 +GET /api/reports/summary'

echo "endpoints"
assert_has "collapses request numbers and ids" "$out" 'GET /api/requests/\{requestNumber\}/files/\{id\}'
assert_has "drops the query string" "$out" 'GET /api/requests/\{requestNumber\} +2 '
assert_has "groups static assets" "$out" 'GET /assets/\* +1 '
assert_has "nearest-rank percentiles (160, 180 -> p50 160, p95 180)" "$out" 'POST /api/auth/login +2 +170\.0 +160 +180 +180'
assert_has "percentiles over three samples (9, 12, 15)" "$out" 'GET /api/requests/mine +3 +12\.0 +12 +15 +15'
first_endpoint=$(awk '/Endpoints by p95/ { getline; getline; print; exit }' <<<"$out")
assert_has "sorts endpoints by p95, slowest first" "$first_endpoint" '^GET /api/reports/summary'
assert_has "accepts '-' for unknown byte counts" "$out" 'GET /api/requests/mine +3 '

echo "exceptions"
assert_has "finds exception classes in stack traces" "$out" 'org\.springframework\.dao\.DataAccessResourceFailureException +1'
assert_has "finds 'Caused by' exceptions" "$out" 'java\.sql\.SQLTransientConnectionException +1'

echo "time window"
out=$(run --since "2026-11-11 09:00:06" --until "2026-11-11 10:00" "$FX/access.log" "$FX/app.log")
assert_has "--since/--until filter requests" "$out" 'requests 4 \|'
assert_not "--until drops later lines" "$out" 'E-REQ-004'
assert_has "--since drops earlier lines (one of two failed logins remains)" "$out" 'E-AUTH-401 +1 +401'
assert_has "--since keeps a line in the window's first second" "$out" 'E-REQ-001 +1 +400'
assert_has "stack traces follow their parent line into the window" "$out" 'SQLTransientConnectionException'

echo "inputs"
gzip -c "$FX/access.log" >"$WORK/access.log.gz"
out=$(run "$WORK/access.log.gz")
assert_has "reads gzipped logs" "$out" 'requests 12 \|'
out=$(cat "$FX/app.log" "$FX/access.log" | run -)
assert_has "reads mixed logs from stdin" "$out" 'requests 12 \|.*app WARN 6'
sed 's/^/[render] [abc12] /' "$FX/access.log" >"$WORK/prefixed.log"
out=$(run "$WORK/prefixed.log")
assert_has "tolerates line prefixes from log shippers" "$out" 'requests 12 \|'
: >"$WORK/empty.log"
out=$(run "$WORK/empty.log")
assert_exit "handles an empty log" $? 0
assert_has "empty log reports zero requests" "$out" 'requests 0 \| 4xx 0 \(0\.00%\)'

awk 'BEGIN { for (i = 0; i < 200000; i++)
  printf "2026-11-11 09:%02d:%02d.000 \"GET /api/requests/REQ-2026-%06d HTTP/1.1\" 200 512 %d\n", int(i / 60) % 60, i % 60, i % 500, i % 997 }' >"$WORK/big.log"
out=$(run "$WORK/big.log")
assert_exit "large logs: no SIGPIPE from sort | head (regression)" $? 0
assert_has "large logs: counts every request" "$out" 'requests 200000 \|'
assert_has "large logs: exact p95 over 200k samples" "$out" 'GET /api/requests/\{requestNumber\} +200000 +497\.4 +497 +946 +996'

echo "output options"
out=$(run --markdown "$FX/access.log" "$FX/app.log")
assert_has "Markdown header" "$out" '^### DocTrack log report'
assert_has "Markdown tables" "$out" '^\| Code \| Count \| Status'
assert_has "Markdown separator row" "$out" '^\|---\|---\|'
out=$(run --top 2 "$FX/access.log")
rows=$(awk '/Slowest requests/ { f = 1; next } f && /^==/ { f = 0 } f && /[0-9]/' <<<"$out" | wc -l | tr -d ' ')
assert_exit "--top limits rows" "$rows" 2

echo "thresholds"
run --max-5xx-rate 10 "$FX/access.log" >/dev/null
assert_exit "5xx rate under the limit passes" $? 0
out=$(run --max-5xx-rate 5 "$FX/access.log")
assert_exit "5xx rate over the limit exits 2" $? 2
assert_has "explains the 5xx alert" "$out" 'ALERT: 5xx rate 8\.33% is above 5%'
out=$(run --max-p95-ms 100 --min-samples 2 "$FX/access.log")
assert_exit "p95 over the limit exits 2" $? 2
assert_has "names the slow endpoint" "$out" 'ALERT: POST /api/auth/login p95 180 ms'
run --max-p95-ms 100 --min-samples 50 "$FX/access.log" >/dev/null
assert_exit "endpoints below --min-samples are not judged" $? 0

echo "argument handling"
run --top abc >/dev/null
assert_exit "rejects a non-numeric --top" $? 64
run --nope >/dev/null
assert_exit "rejects unknown options" $? 64
run "$WORK/missing.log" >/dev/null
assert_exit "rejects unreadable files" $? 64
assert_has "--help documents --follow" "$(run --help)" '--follow'

echo "live mode"
: >"$WORK/live.log"
"$SCAN" --follow --slow-ms 500 "$WORK/live.log" >"$WORK/follow.out" 2>/dev/null &
FOLLOW_PID=$!
sleep 1
cat >>"$WORK/live.log" <<'EOF'
2026-11-11 11:00:00.000 "GET /api/requests/mine HTTP/1.1" 200 100 5
2026-11-11 11:00:01.000 "GET /api/reports/summary HTTP/1.1" 503 100 20
2026-11-11 11:00:02.000 "GET /api/requests/REQ-2026-000001 HTTP/1.1" 200 100 900
2026-11-11T11:00:03.000Z ERROR 1 --- [docreq] [exec-1] c.l.d.web.GlobalExceptionHandler : E-SYS-500 500 GET /api/x user=1 unexpected error
EOF
for _ in 1 2 3 4 5 6 7 8 9 10; do [[ $(wc -l <"$WORK/follow.out") -ge 3 ]] && break; sleep 0.5; done
kill "$FOLLOW_PID" 2>/dev/null; wait "$FOLLOW_PID" 2>/dev/null; FOLLOW_PID=
live=$(cat "$WORK/follow.out")
assert_has "--follow alerts on 5xx" "$live" '5XX +503 GET /api/reports/summary'
assert_has "--follow alerts on slow requests" "$live" 'SLOW +900 ms GET /api/requests/\{requestNumber\}'
assert_has "--follow alerts on ERROR lines" "$live" 'ERROR E-SYS-500'
assert_not "--follow stays quiet on healthy requests" "$live" 'requests/mine'

echo
echo "$PASS passed, $FAIL failed"
((FAIL == 0))
