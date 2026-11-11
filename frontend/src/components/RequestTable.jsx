import { Link } from 'react-router-dom';
import { formatDateTime, relativeTime } from '../lib/format';
import StatusBadge, { EscalatedBadge } from './StatusBadge';

/** Renders RequestSummary rows (requests/mine, staff queue, pending approvals). */
export default function RequestTable({ rows, showStudent = false }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Request</th>
            <th>Document</th>
            {showStudent && <th>Student</th>}
            <th>Status</th>
            <th>Submitted</th>
            <th>Last update</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.requestNumber} className={r.status === 'INCOMPLETE' ? 'row--attention' : undefined}>
              <td>
                <Link to={`/requests/${encodeURIComponent(r.requestNumber)}`} className="mono">
                  {r.requestNumber}
                </Link>
              </td>
              <td>{r.documentType}</td>
              {showStudent && (
                <td>
                  {r.studentName} <span className="muted">({r.studentUsername})</span>
                </td>
              )}
              <td>
                <span className="badges">
                  <StatusBadge status={r.status} />
                  {r.escalated && <EscalatedBadge />}
                </span>
              </td>
              <td>{formatDateTime(r.submittedAt)}</td>
              <td title={formatDateTime(r.updatedAt)}>{relativeTime(r.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
