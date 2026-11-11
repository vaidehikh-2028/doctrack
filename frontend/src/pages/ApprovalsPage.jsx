import { Link } from 'react-router-dom';
import { myDecisions, pendingApprovals } from '../api/doctrack';
import { EmptyState, ErrorBox, PageHead, Spinner } from '../components/Feedback';
import RequestTable from '../components/RequestTable';
import { POLL_MS } from '../config';
import { useAsync, useVisiblePolling } from '../hooks/useAsync';
import { formatDateTime, titleCase } from '../lib/format';

export function PendingApprovalsPage() {
  const { data, error, loading, reload } = useAsync((signal) => pendingApprovals(signal), []);
  useVisiblePolling(() => reload({ silent: true }), POLL_MS.list, Boolean(data));

  return (
    <section>
      <PageHead title="Pending approvals">{data && <span className="muted">{data.length} waiting</span>}</PageHead>
      {loading && <Spinner />}
      <ErrorBox error={error} onRetry={reload} />
      {data && data.length === 0 && <EmptyState title="No requests are waiting for your decision." />}
      {data?.length > 0 && <RequestTable rows={data} showStudent />}
    </section>
  );
}

export function DecisionHistoryPage() {
  const { data, error, loading, reload } = useAsync((signal) => myDecisions(signal), []);

  return (
    <section>
      <PageHead title="My decisions" />
      {loading && <Spinner />}
      <ErrorBox error={error} onRetry={reload} />
      {data && data.length === 0 && <EmptyState title="You haven't decided on any requests yet." />}
      {data?.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Request</th>
                <th>Decision</th>
                <th>Comments / reason</th>
                <th>Decided</th>
              </tr>
            </thead>
            <tbody>
              {data.map((a) => (
                <tr key={a.id}>
                  <td>
                    <Link to={`/requests/${encodeURIComponent(a.requestNumber)}`} className="mono">
                      {a.requestNumber}
                    </Link>
                  </td>
                  <td>
                    <span className={`badge badge--${a.decision === 'APPROVED' ? 'good' : a.decision === 'REJECTED' ? 'bad' : 'neutral'}`}>
                      {titleCase(a.decision)}
                    </span>
                  </td>
                  <td>{a.comments || <span className="muted">—</span>}</td>
                  <td>{formatDateTime(a.decidedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
