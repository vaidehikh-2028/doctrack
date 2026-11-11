#!/usr/bin/env bash
# logscan.sh: triage DocTrack logs from the command line.
#
# Reads the Tomcat access log (one line per request, with latency) and the Spring application
# log (coded API errors, exceptions), then reports what a support engineer looks at first:
# error codes and where they come from, the slowest endpoints, status mix and top exceptions.
#
#   ops/logscan.sh logs/access*.log logs/docreq.log
#   docker logs doctrack 2>&1 | ops/logscan.sh -
#   ops/logscan.sh --since "2026-11-11 09:00" --max-5xx-rate 1 logs/*.log   # exit 2 if breached
#   ops/logscan.sh --follow logs/access.log logs/docreq.log                 # live alerts
#
# Files may be gzipped. Line types are detected per line, so mixed or concatenated logs work.
# Timestamps are compared as text after normalising to "YYYY-MM-DD HH:MM:SS"; keep both logs in
# the same time zone (servers normally run in UTC).
set -euo pipefail

VERSION=1.0.0
TOP=10
SLOW_MS=1000
SINCE=""
UNTIL=""
MAX_5XX_RATE=""
MAX_P95_MS=""
MAX_ERRORS=""
MIN_SAMPLES=20
FORMAT=text
FOLLOW=0

usage() {
  cat <<'EOF'
Usage: logscan.sh [options] [FILE...]   (no FILE or "-" reads stdin; *.gz is decompressed)

Report
  -n, --top N              rows per table (default 10)
      --slow-ms N          a request at or above N ms counts as slow (default 1000)
      --since "TIME"       only lines at or after TIME, e.g. "2026-11-11 09:00"
      --until "TIME"       only lines before TIME
      --markdown           Markdown tables (for CI job summaries and tickets)

Alerting (exit status 2 when a threshold is breached)
      --max-5xx-rate PCT   fail if more than PCT % of requests returned 5xx
      --max-p95-ms N       fail if any endpoint with enough samples has p95 above N ms
      --min-samples N      samples an endpoint needs for --max-p95-ms (default 20)
      --max-errors N       fail if the application log has more than N ERROR lines

Live
  -f, --follow             watch the files and print 5xx, slow requests and errors as they happen

  -h, --help               this help
  -V, --version            print the version
EOF
}

die() { echo "logscan: $*" >&2; exit 64; }

is_uint() { [[ $1 =~ ^[0-9]+$ ]]; }
is_num() { [[ $1 =~ ^[0-9]+([.][0-9]+)?$ ]]; }

FILES=()
while (($#)); do
  case $1 in
    -n | --top) is_uint "${2:-}" || die "--top needs a number"; TOP=$2; shift 2 ;;
    --slow-ms) is_uint "${2:-}" || die "--slow-ms needs a number"; SLOW_MS=$2; shift 2 ;;
    --since) [[ -n ${2:-} ]] || die "--since needs a time"; SINCE=$2; shift 2 ;;
    --until) [[ -n ${2:-} ]] || die "--until needs a time"; UNTIL=$2; shift 2 ;;
    --max-5xx-rate) is_num "${2:-}" || die "--max-5xx-rate needs a percentage"; MAX_5XX_RATE=$2; shift 2 ;;
    --max-p95-ms) is_uint "${2:-}" || die "--max-p95-ms needs a number"; MAX_P95_MS=$2; shift 2 ;;
    --min-samples) is_uint "${2:-}" || die "--min-samples needs a number"; MIN_SAMPLES=$2; shift 2 ;;
    --max-errors) is_uint "${2:-}" || die "--max-errors needs a number"; MAX_ERRORS=$2; shift 2 ;;
    --markdown) FORMAT=markdown; shift ;;
    -f | --follow) FOLLOW=1; shift ;;
    -h | --help) usage; exit 0 ;;
    -V | --version) echo "logscan $VERSION"; exit 0 ;;
    --) shift; FILES+=("$@"); break ;;
    -) FILES+=("-"); shift ;;
    -*) die "unknown option $1 (see --help)" ;;
    *) FILES+=("$1"); shift ;;
  esac
done
((${#FILES[@]})) || FILES=("-")

for f in "${FILES[@]}"; do
  [[ $f == - || -r $f ]] || die "cannot read $f"
done

# Shared awk: recognise the two line formats and normalise fields.
#   access: 2026-11-11 09:35:23.343 "GET /api/x HTTP/1.1" 200 512 12
#   app:    2026-11-11T09:35:23.343Z  WARN 1 --- [docreq] [exec-1] c.l.d.w.GlobalExceptionHandler : E-REQ-001 400 POST /api/requests user=7 msg
# Lines may carry a prefix (Render, docker --timestamps); matching is not anchored at column 0.
read -r -d '' AWK_LIB <<'AWK' || true
function ts_of(line,    t) {
  if (match(line, /[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9][T ][0-9][0-9]:[0-9][0-9]:[0-9][0-9]/)) {
    t = substr(line, RSTART, RLENGTH); sub(/T/, " ", t); return t
  }
  return ""
}
function in_window(t) {
  if (t == "") return 1                       # continuation lines (stack traces) follow their parent
  if (since != "" && t < since) return 0
  if (until != "" && t >= until) return 0
  return 1
}
# Collapse ids so requests group by route: REQ-2026-000123 -> {requestNumber}, /42 -> /{id}.
function route(path) {
  sub(/[?#].*/, "", path)
  gsub(/REQ-[0-9][0-9][0-9][0-9]-[0-9]+/, "{requestNumber}", path)
  gsub(/\/[0-9]+(\/|$)/, "/{id}/", path); sub(/\/$/, "", path)
  if (path ~ /^\/assets\//) path = "/assets/*"
  if (path == "") path = "/"
  return path
}
# Sets A_METHOD A_ROUTE A_STATUS A_MS; returns 1 for an access-log line.
function parse_access(line,    q, req, n, parts, rest, f) {
  if (!match(line, /"[A-Z]+ [^ "]+ HTTP\/[0-9.]+" [0-9][0-9][0-9] [0-9-]+ [0-9]+/)) return 0
  q = substr(line, RSTART, RLENGTH)
  req = substr(q, 2); req = substr(req, 1, index(req, "\"") - 1)
  n = split(req, parts, " ")
  rest = substr(q, length(req) + 4)
  split(rest, f, " ")
  A_METHOD = parts[1]; A_ROUTE = route(parts[2]); A_STATUS = f[1] + 0; A_MS = f[3] + 0
  return 1
}
# Sets L_LEVEL L_MSG; returns 1 for a Spring application-log line.
function parse_app(line,    i) {
  if (!match(line, / (TRACE|DEBUG|INFO|WARN|ERROR) +[0-9]+ --- /)) return 0
  L_LEVEL = substr(line, RSTART + 1, RLENGTH); sub(/ .*/, "", L_LEVEL)
  i = index(line, " : "); L_MSG = (i ? substr(line, i + 3) : "")
  return 1
}
AWK

read_all() {
  local f
  for f in "${FILES[@]}"; do
    if [[ $f == - ]]; then cat
    elif [[ $f == *.gz ]]; then gzip -dc -- "$f"
    else cat -- "$f"
    fi
  done
}

# ---------------------------------------------------------------- live mode
if ((FOLLOW)); then
  for f in "${FILES[@]}"; do [[ $f != - && $f != *.gz ]] || die "--follow needs plain files"; done
  echo "logscan: watching ${FILES[*]} (5xx, requests >= ${SLOW_MS} ms, WARN/ERROR with codes). Ctrl-C to stop." >&2
  # mawk (the default awk on Debian/Ubuntu) reads pipes in large blocks, which would hold back
  # live lines indefinitely; -W interactive makes it read line by line. gawk needs no flag.
  live_awk=(awk)
  if awk -W version 2>&1 | grep -q mawk; then live_awk=(awk -W interactive); fi
  # shellcheck disable=SC2016  # the single-quoted text is an awk program; $0 is awk's, not the shell's
  tail -n0 -F -- "${FILES[@]}" 2>/dev/null | "${live_awk[@]}" -v slow="$SLOW_MS" "$AWK_LIB"'
    {
      t = ts_of($0)
      if (parse_access($0)) {
        if (A_STATUS >= 500) { printf "%s  5XX   %d %s %s (%d ms)\n", t, A_STATUS, A_METHOD, A_ROUTE, A_MS; fflush() }
        else if (A_MS >= slow) { printf "%s  SLOW  %d ms %s %s -> %d\n", t, A_MS, A_METHOD, A_ROUTE, A_STATUS; fflush() }
      } else if (parse_app($0) && (L_LEVEL == "ERROR" || (L_LEVEL == "WARN" && L_MSG ~ /^E-[A-Z]+-[0-9]+/))) {
        printf "%s  %-5s %s\n", t, L_LEVEL, substr(L_MSG, 1, 160); fflush()
      }
    }'
  exit 0
fi

# ---------------------------------------------------------------- batch mode
TMP=$(mktemp -d "${TMPDIR:-/tmp}/logscan.XXXXXX")
trap 'rm -rf "$TMP"' EXIT

# One pass over the input, splitting it into small tab-separated tables.
read_all | awk -v since="$SINCE" -v until="$UNTIL" -v dir="$TMP" "$AWK_LIB"'
  BEGIN { keep = 1 }
  {
    t = ts_of($0)
    if (t != "") keep = in_window(t)
    if (!keep) next
    if (t != "" && (first == "" || t < first)) first = t
    if (t != "" && t > last) last = t

    if (parse_access($0)) {
      printf "%s %s\t%d\t%d\t%s\n", A_METHOD, A_ROUTE, A_MS, A_STATUS, t > (dir "/req")
      next
    }
    if (parse_app($0)) {
      lv[L_LEVEL]++
      if ((L_LEVEL == "WARN" || L_LEVEL == "ERROR") && match(L_MSG, /^E-[A-Z]+-[0-9]+/)) {
        code = substr(L_MSG, RSTART, RLENGTH)
        # Handler format: CODE STATUS METHOD PATH user=... message. Other coded lines keep "-".
        n = split(L_MSG, w, " "); st = "-"; ep = "-"
        if (n >= 4 && w[2] ~ /^[0-9][0-9][0-9]$/ && w[3] ~ /^[A-Z]+$/) { st = w[2]; ep = w[3] " " route(w[4]) }
        printf "%s\t%s\t%s\t%s\n", code, st, ep, t > (dir "/err")
      }
      next
    }
    # Exception class names from stack traces: "Caused by: x.y.FooException" or "x.y.BarError: msg".
    if (match($0, /^(Caused by: )?[a-z][A-Za-z0-9_]*(\.[A-Za-z0-9_$]+)+(Exception|Error)(: |$)/)) {
      e = substr($0, RSTART, RLENGTH); sub(/^Caused by: /, "", e); sub(/: $/, "", e)
      print e > (dir "/exc")
    }
  }
  END {
    printf "first\t%s\nlast\t%s\nwarn\t%d\nerror\t%d\n", first, last, lv["WARN"], lv["ERROR"] > (dir "/meta")
  }'

touch "$TMP/req" "$TMP/err" "$TMP/exc"
meta() { awk -F'\t' -v k="$1" '$1 == k { print $2 }' "$TMP/meta"; }

TOTAL=$(wc -l <"$TMP/req" | tr -d ' ')
read -r N4XX N5XX NSLOW < <(awk -F'\t' -v slow="$SLOW_MS" '
  { if ($3 >= 500) f++; else if ($3 >= 400) c++; if ($2 >= slow) s++ }
  END { printf "%d %d %d\n", c, f, s }' "$TMP/req")
pct() { awk -v a="$1" -v b="$2" 'BEGIN { if (b == 0) print "0.00"; else printf "%.2f", 100 * a / b }'; }

# Per-endpoint latency: sort by route then latency, then walk each group once for exact percentiles.
sort -t$'\t' -k1,1 -k2,2n "$TMP/req" | awk -F'\t' '
  # Nearest-rank percentile: the smallest sample with at least p% of samples at or below it.
  function rank(p,    r) { r = int(p * n); if (r < p * n) r++; return r < 1 ? 1 : r }
  function flush(    i50, i95) {
    if (n == 0) return
    i50 = rank(0.50); i95 = rank(0.95)
    printf "%s\t%d\t%.1f\t%d\t%d\t%d\t%d\n", key, n, sum / n, v[i50], v[i95], v[n], f5
  }
  $1 != key { flush(); key = $1; n = 0; sum = 0; f5 = 0 }
  { v[++n] = $2; sum += $2; if ($3 >= 500) f5++ }
  END { flush() }' | sort -t$'\t' -k5,5nr -k2,2nr >"$TMP/endpoints"

# Error codes: count, most common status and endpoint, last seen.
awk -F'\t' '
  { c[$1]++; s[$1 SUBSEP $2]++; e[$1 SUBSEP $3]++; if ($4 > last[$1]) last[$1] = $4 }
  END {
    for (k in s) { split(k, p, SUBSEP); if (s[k] > bs[p[1]]) { bs[p[1]] = s[k]; st[p[1]] = p[2] } }
    for (k in e) { split(k, p, SUBSEP); if (e[k] > be[p[1]]) { be[p[1]] = e[k]; ep[p[1]] = p[2] } }
    for (k in c) printf "%s\t%d\t%s\t%s\t%s\n", k, c[k], st[k], ep[k], last[k]
  }' "$TMP/err" | sort -t$'\t' -k2,2nr -k1,1 >"$TMP/codes"

awk -F'\t' '{ print $3 }' "$TMP/req" | sort | uniq -c | sort -k2,2n >"$TMP/status"
sort "$TMP/exc" | uniq -c | sort -k1,1nr -k2,2 >"$TMP/exceptions"
# Not "sort | head": head exits after TOP lines, sort then dies of SIGPIPE on large logs, and
# pipefail turns that into a script failure. Sort to a file, then take the top rows.
sort -t$'\t' -k2,2nr "$TMP/req" >"$TMP/slowest.all"
head -n "$TOP" "$TMP/slowest.all" >"$TMP/slowest"

# ---------------------------------------------------------------- output
# table TITLE HEADER(tab-separated) FILE: prints the first TOP rows as aligned text or Markdown.
table() {
  local title=$1 header=$2 file=$3
  if [[ $FORMAT == markdown ]]; then
    printf '\n#### %s\n\n' "$title"
  else
    printf '\n== %s ==\n' "$title"
  fi
  if [[ ! -s $file ]]; then echo "(none)"; return; fi
  { printf '%s\n' "$header"; head -n "$TOP" "$file"; } | awk -F'\t' -v md="$([[ $FORMAT == markdown ]] && echo 1)" '
    { for (i = 1; i <= NF; i++) { cell[NR, i] = $i; if (length($i) > w[i]) w[i] = length($i) } if (NF > nf) nf = NF }
    END {
      for (r = 1; r <= NR; r++) {
        line = ""
        for (i = 1; i <= nf; i++) line = line (md ? "| " cell[r, i] " " : sprintf("%-" w[i] "s  ", cell[r, i]))
        print (md ? line "|" : line)
        if (md && r == 1) { sep = ""; for (i = 1; i <= nf; i++) sep = sep "|---"; print sep "|" }
      }
    }'
}

FIRST=$(meta first); LAST=$(meta last)
if [[ $FORMAT == markdown ]]; then
  printf '### DocTrack log report\n\n'
  printf -- '- **Window:** %s to %s\n' "${FIRST:-n/a}" "${LAST:-n/a}"
  printf -- '- **Requests:** %s · **4xx:** %s (%s%%) · **5xx:** %s (%s%%) · **slow (>= %s ms):** %s\n' \
    "$TOTAL" "$N4XX" "$(pct "$N4XX" "$TOTAL")" "$N5XX" "$(pct "$N5XX" "$TOTAL")" "$SLOW_MS" "$NSLOW"
  printf -- '- **App log:** %s WARN, %s ERROR\n' "$(meta warn)" "$(meta error)"
else
  printf 'DocTrack log report  %s .. %s\n' "${FIRST:-n/a}" "${LAST:-n/a}"
  printf 'requests %s | 4xx %s (%s%%) | 5xx %s (%s%%) | slow >= %s ms: %s | app WARN %s, ERROR %s\n' \
    "$TOTAL" "$N4XX" "$(pct "$N4XX" "$TOTAL")" "$N5XX" "$(pct "$N5XX" "$TOTAL")" "$SLOW_MS" "$NSLOW" \
    "$(meta warn)" "$(meta error)"
fi

table "Error codes" $'Code\tCount\tStatus\tTop endpoint\tLast seen' "$TMP/codes"
table "Endpoints by p95 latency (ms)" $'Endpoint\tRequests\tAvg\tp50\tp95\tMax\t5xx' "$TMP/endpoints"
awk '{ printf "%s\t%s\n", $2, $1 }' "$TMP/status" >"$TMP/status.tsv"
table "HTTP status" $'Status\tCount' "$TMP/status.tsv"
awk -F'\t' '{ printf "%s\t%s\t%s\t%s\n", $2, $1, $3, $4 }' "$TMP/slowest" >"$TMP/slowest.tsv"
table "Slowest requests" $'ms\tEndpoint\tStatus\tTime' "$TMP/slowest.tsv"
awk '{ c = $1; $1 = ""; sub(/^ /, ""); printf "%s\t%s\n", $0, c }' "$TMP/exceptions" >"$TMP/exceptions.tsv"
table "Exceptions" $'Exception\tCount' "$TMP/exceptions.tsv"

# ---------------------------------------------------------------- thresholds
BREACH=0
if [[ -n $MAX_5XX_RATE ]] && awk -v r="$(pct "$N5XX" "$TOTAL")" -v m="$MAX_5XX_RATE" 'BEGIN { exit !(r > m) }'; then
  echo "ALERT: 5xx rate $(pct "$N5XX" "$TOTAL")% is above ${MAX_5XX_RATE}%" >&2
  BREACH=1
fi
if [[ -n $MAX_P95_MS ]]; then
  while IFS=$'\t' read -r ep n _ _ p95 _; do
    echo "ALERT: $ep p95 ${p95} ms is above ${MAX_P95_MS} ms (${n} requests)" >&2
    BREACH=1
  done < <(awk -F'\t' -v m="$MAX_P95_MS" -v min="$MIN_SAMPLES" '$2 >= min && $5 > m' "$TMP/endpoints")
fi
if [[ -n $MAX_ERRORS ]] && (($(meta error) > MAX_ERRORS)); then
  echo "ALERT: $(meta error) ERROR lines in the application log (limit ${MAX_ERRORS})" >&2
  BREACH=1
fi
exit $((BREACH ? 2 : 0))
