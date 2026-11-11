import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { myRequests } from '../api/doctrack';
import { EmptyState, ErrorBox, PageHead, Spinner } from '../components/Feedback';
import RequestTable from '../components/RequestTable';
import { POLL_MS, STATUS } from '../config';
import { useAsync, useVisiblePolling } from '../hooks/useAsync';

export default function MyRequestsPage() {
  const { data, error, loading, reload } = useAsync((signal) => myRequests(signal), []);
  const [status, setStatus] = useState('ALL');
  const [query, setQuery] = useState('');
  useVisiblePolling(() => reload({ silent: true }), POLL_MS.list, Boolean(data));

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (data ?? [])
      .filter((r) => status === 'ALL' || r.status === status)
      .filter((r) => !q || r.requestNumber.toLowerCase().includes(q) || r.documentType.toLowerCase().includes(q))
      .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt));
  }, [data, status, query]);

  const needsAction = (data ?? []).filter((r) => r.status === 'INCOMPLETE').length;

  return (
    <section>
      <PageHead title="My requests">
        <Link to="/requests/new" className="btn btn--primary">
          New request
        </Link>
      </PageHead>

      {needsAction > 0 && (
        <div className="alert alert--warn" role="status">
          {needsAction === 1 ? 'One request needs' : `${needsAction} requests need`} more information from you.
          Open it to see what is missing and resubmit.
        </div>
      )}

      {data?.length > 0 && (
        <div className="toolbar">
          <input
            type="search"
            placeholder="Search by request number or document"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search requests"
          />
          <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Filter by status">
            <option value="ALL">All statuses</option>
            {Object.entries(STATUS).map(([k, label]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </select>
        </div>
      )}

      {loading && <Spinner />}
      <ErrorBox error={error} onRetry={reload} />

      {data && rows.length === 0 && (
        <EmptyState title={data.length ? 'No requests match these filters.' : "You haven't requested any documents yet."}>
          {!data.length && (
            <Link to="/requests/new" className="btn">
              Request a document
            </Link>
          )}
        </EmptyState>
      )}
      {rows.length > 0 && <RequestTable rows={rows} />}
    </section>
  );
}
