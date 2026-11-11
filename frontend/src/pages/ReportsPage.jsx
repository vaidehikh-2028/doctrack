import { reportSummary } from '../api/doctrack';
import { ErrorBox, PageHead, Spinner } from '../components/Feedback';
import { STATUS } from '../config';
import { useAsync } from '../hooks/useAsync';

export default function ReportsPage() {
  const { data, error, loading, reload } = useAsync((signal) => reportSummary(signal), []);

  if (loading) return <Spinner />;
  if (error) return <ErrorBox error={error} onRetry={reload} />;

  const counts = Object.keys(STATUS).map((k) => ({ key: k, label: STATUS[k], n: data.byStatus?.[k] ?? 0 }));
  const max = Math.max(1, ...counts.map((c) => c.n));
  const avg = data.averageCompletionHours;

  return (
    <section>
      <PageHead title="Reports" />
      <div className="stats">
        <div className="card stat">
          <span className="stat__label">Total requests</span>
          <span className="stat__value">{data.total}</span>
        </div>
        <div className="card stat">
          <span className="stat__label">Average time to complete</span>
          <span className="stat__value">
            {avg == null ? '—' : avg < 48 ? `${avg.toFixed(1)} h` : `${(avg / 24).toFixed(1)} days`}
          </span>
        </div>
        <div className={`card stat ${data.escalatedOpen > 0 ? 'stat--bad' : ''}`}>
          <span className="stat__label">Open and overdue (&gt; 48 h)</span>
          <span className="stat__value">{data.escalatedOpen}</span>
        </div>
      </div>

      <div className="card">
        <h2 className="section-title">Requests by status</h2>
        <ul className="bars">
          {counts.map((c) => (
            <li key={c.key} className="bars__row">
              <span className="bars__label">{c.label}</span>
              <span className="bars__track">
                <span className={`bars__fill bars__fill--${c.key.toLowerCase()}`} style={{ width: `${(c.n / max) * 100}%` }} />
              </span>
              <span className="bars__n">{c.n}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
