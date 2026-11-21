// Compares load-test results of the base and head builds (written by run.mjs with RESULT_JSON).
//
//   node compare.mjs base-1.json,base-2.json head-1.json,head-2.json
//
// Each side may have several runs; their numbers are averaged per endpoint. Prints a Markdown table
// and, in GitHub Actions, appends it to the job summary.
import { appendFileSync, readFileSync } from 'node:fs';

const [baseFiles, headFiles] = process.argv.slice(2).map((arg) => arg.split(','));
if (!baseFiles || !headFiles) {
  console.error('usage: node compare.mjs <base.json[,base.json...]> <head.json[,head.json...]>');
  process.exit(64);
}

function load(files) {
  const byTitle = new Map();
  for (const file of files) {
    for (const r of JSON.parse(readFileSync(file, 'utf8'))) {
      if (!byTitle.has(r.title)) byTitle.set(r.title, []);
      byTitle.get(r.title).push(r);
    }
  }
  const mean = (rows, key) => rows.reduce((sum, r) => sum + r[key], 0) / rows.length;
  return new Map([...byTitle].map(([title, rows]) => [title, { rps: mean(rows, 'rps'), p50: mean(rows, 'p50'), p99: mean(rows, 'p99') }]));
}

const base = load(baseFiles);
const head = load(headFiles);
const pct = (from, to) => (from ? `${to >= from ? '+' : ''}${(((to - from) / from) * 100).toFixed(0)}%` : 'n/a');
const n = (x) => Math.round(x);

const lines = [
  '### Benchmark: base vs this branch',
  '',
  `Same runner, same load test, fresh database per run; averaged over ${baseFiles.length} run(s) per side.`,
  '',
  '| Endpoint | Req/s base | Req/s head | Change | p50 ms base → head | p99 ms base → head | p99 change |',
  '|---|---:|---:|---:|---:|---:|---:|',
];
for (const [title, h] of head) {
  const b = base.get(title);
  if (!b) {
    lines.push(`| ${title} | – | ${n(h.rps)} | new | – → ${n(h.p50)} | – → ${n(h.p99)} | new |`);
    continue;
  }
  lines.push(`| ${title} | ${n(b.rps)} | ${n(h.rps)} | ${pct(b.rps, h.rps)} | ${n(b.p50)} → ${n(h.p50)} | ${n(b.p99)} → ${n(h.p99)} | ${pct(b.p99, h.p99)} |`);
}
const out = lines.join('\n');
console.log(out);
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, out + '\n');
