import { statusLabel } from '../lib/format';

const TONE = {
  SUBMITTED: 'info',
  INCOMPLETE: 'warn',
  VERIFIED: 'info',
  FORWARDED: 'warn',
  APPROVED: 'good',
  PROCESSING: 'info',
  COMPLETED: 'done',
  REJECTED: 'bad',
};

export default function StatusBadge({ status }) {
  return <span className={`badge badge--${TONE[status] ?? 'neutral'}`}>{statusLabel(status)}</span>;
}

export function EscalatedBadge() {
  return (
    <span className="badge badge--bad badge--outline" title="Pending in this stage for over 48 hours">
      Overdue
    </span>
  );
}
