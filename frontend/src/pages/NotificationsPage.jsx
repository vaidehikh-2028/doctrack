import { Link } from 'react-router-dom';
import { myNotifications } from '../api/doctrack';
import { EmptyState, ErrorBox, PageHead, Spinner } from '../components/Feedback';
import { POLL_MS } from '../config';
import { useAsync, useVisiblePolling } from '../hooks/useAsync';
import { formatDateTime, relativeTime, titleCase } from '../lib/format';

export default function NotificationsPage() {
  const { data, error, loading, reload } = useAsync((signal) => myNotifications(signal), []);
  useVisiblePolling(() => reload({ silent: true }), POLL_MS.list, Boolean(data));
  const rows = [...(data ?? [])].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

  return (
    <section className="narrow">
      <PageHead title="Notifications" />
      {loading && <Spinner />}
      <ErrorBox error={error} onRetry={reload} />
      {data && rows.length === 0 && <EmptyState title="No notifications yet." />}
      <ul className="notes">
        {rows.map((n) => (
          <li key={n.id} className="card note">
            <div className="note__head">
              <span className="note__type">{titleCase(n.type)}</span>
              <time dateTime={n.createdAt} title={formatDateTime(n.createdAt)}>
                {relativeTime(n.createdAt)}
              </time>
            </div>
            <p>{n.message}</p>
            {n.requestNumber && (
              <Link to={`/requests/${encodeURIComponent(n.requestNumber)}`} className="small">
                Open {n.requestNumber}
              </Link>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
