// Load test for the DocTrack API: throughput and latency of the hot read paths under concurrency.
//
//   java -jar target/docreq-*.jar          # fresh database with the seeded demo users
//   cd loadtest && npm ci && node run.mjs  # DOCTRACK_URL, CONNECTIONS, DURATION to override
//
// Writes a Markdown table to stdout and, in GitHub Actions, to the job summary.
import autocannon from 'autocannon';
import { appendFileSync } from 'node:fs';

const BASE = (process.env.DOCTRACK_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const CONNECTIONS = Number(process.env.CONNECTIONS ?? 50);
const DURATION = Number(process.env.DURATION ?? 20);
const SEED_REQUESTS = 25;

async function login(username) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: 'Demo@1234' }),
  });
  if (!res.ok) throw new Error(`login ${username}: ${res.status}`);
  return (await res.json()).token;
}

/** Give the read endpoints realistic data: submit requests through the real multipart API. */
async function seed(token) {
  const types = await fetch(`${BASE}/api/document-types`, { headers: { Authorization: `Bearer ${token}` } }).then((r) => r.json());
  const type = types.find((t) => !t.attachmentRequired);
  if (!type) throw new Error('no document type without a required attachment');
  const numbers = [];
  for (let i = 0; i < SEED_REQUESTS; i++) {
    const form = new FormData();
    form.append('documentTypeId', String(type.id));
    form.append('purpose', `Load test request ${i + 1}`);
    form.append('fields', JSON.stringify(Object.fromEntries(type.requiredFields.map((f) => [f, 'Load test']))));
    const res = await fetch(`${BASE}/api/requests`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form });
    if (res.status !== 201) throw new Error(`seed submit: ${res.status} ${await res.text()}`);
    numbers.push((await res.json()).requestNumber);
  }
  return numbers;
}

function run(title, opts) {
  return new Promise((resolve, reject) => {
    autocannon({ connections: CONNECTIONS, duration: DURATION, ...opts }, (err, r) => {
      if (err) return reject(err);
      const total = r.requests.total;
      const bad = r.non2xx + r.errors + r.timeouts;
      resolve({
        title,
        rps: Math.round(r.requests.average),
        p50: r.latency.p50,
        p90: r.latency.p90,
        p99: r.latency.p99,
        total,
        errorRate: total ? (100 * bad) / total : 0,
      });
    });
  });
}

const student = await login('student1');
const staff = await login('staff1');
const numbers = await seed(student);
const auth = (t) => ({ Authorization: `Bearer ${t}` });

// Warm up the JIT and connection pools so the measured runs reflect steady state.
await run('warm-up', { url: `${BASE}/api/requests/mine`, headers: auth(student), duration: 5 });

const results = [];
results.push(await run('GET /api/requests/mine (student)', { url: `${BASE}/api/requests/mine`, headers: auth(student) }));
results.push(
  await run('GET /api/requests/{no} (detail with history)', {
    url: BASE,
    headers: auth(student),
    requests: [{ method: 'GET', setupRequest: (req) => ({ ...req, path: `/api/requests/${numbers[Math.floor(Math.random() * numbers.length)]}` }) }],
  }),
);
results.push(await run('GET /api/staff/requests (staff queue)', { url: `${BASE}/api/staff/requests`, headers: auth(staff) }));

const lines = [
  `### DocTrack API load test`,
  ``,
  `${CONNECTIONS} concurrent connections, ${DURATION}s per endpoint, ${SEED_REQUESTS} seeded requests, JWT-authenticated.`,
  ``,
  `| Endpoint | Req/s | p50 ms | p90 ms | p99 ms | Requests | Errors |`,
  `|---|---:|---:|---:|---:|---:|---:|`,
  ...results.map((r) => `| ${r.title} | ${r.rps} | ${r.p50} | ${r.p90} | ${r.p99} | ${r.total} | ${r.errorRate.toFixed(2)}% |`),
];
const out = lines.join('\n');
console.log(out);
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, out + '\n');

if (results.some((r) => r.errorRate > 1)) {
  console.error('More than 1% of requests failed.');
  process.exit(1);
}
