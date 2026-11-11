import { useMemo, useState } from 'react';
import { staffQueue } from '../api/doctrack';
import { EmptyState, ErrorBox, PageHead, Spinner } from '../components/Feedback';
import RequestTable from '../components/RequestTable';
import { POLL_MS, STAFF_DEFAULT_STATUSES, STATUS } from '../config';
import { useAsync, useVisiblePolling } from '../hooks/useAsync';

const FILTERS = [
  { key: 'ACTIONABLE', label: 'Needs action', statuses: STAFF_DEFAULT_STATUSES },
  { key: 'SUBMITTED', label: 'To verify', statuses: ['SUBMITTED'] },
  { key: 'APPROVED', label: 'Approved', statuses: ['APPROVED'] },
  { key: 'PROCESSING', label: 'Processing', statuses: ['PROCESSING'] },
  { key: 'WAITING', label: 'Waiting on others', statuses: ['INCOMPLETE', 'FORWARDED'] },
  { key: 'DONE', label: 'Closed', statuses: ['COMPLETED', 'REJECTED'] },
];

/** GET /api/staff/requests?status=...: oldest first, so the longest-waiting request is on top. */
export default function StaffQueuePage() {
  const [filter, setFilter] = useState(FILTERS[0]);
  const { data, error, loading, reload } = useAsync((signal) => staffQueue(filter.statuses, signal), [filter.key]);
  useVisiblePolling(() => reload({ silent: true }), POLL_MS.list, Boolean(data));

  const overdue = useMemo(() => (data ?? []).filter((r) => r.escalated).length, [data]);

  return (
    <section>
      <PageHead title="Work queue">
        {data && (
          <span className="muted">
            {data.length} request{data.length === 1 ? '' : 's'}
            {overdue > 0 && <strong className="text-bad"> · {overdue} overdue</strong>}
          </span>
        )}
      </PageHead>

      <div className="chips" role="tablist" aria-label="Queue filter">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            role="tab"
            aria-selected={f.key === filter.key}
            className={`chip ${f.key === filter.key ? 'chip--on' : ''}`}
            onClick={() => setFilter(f)}
            title={f.statuses.map((s) => STATUS[s]).join(', ')}
          >
            {f.label}
          </button>
        ))}
      </div>

      {loading && <Spinner />}
      <ErrorBox error={error} onRetry={reload} />
      {data && data.length === 0 && <EmptyState title="Nothing in this view." />}
      {data?.length > 0 && <RequestTable rows={data} showStudent />}
    </section>
  );
}
