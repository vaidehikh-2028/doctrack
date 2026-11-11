import { useCallback, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { downloadRequestFile, requestDetail } from '../api/doctrack';
import { useAuth } from '../auth/AuthContext';
import ActionPanel from '../components/ActionPanel';
import { Alert, ErrorBox, Spinner } from '../components/Feedback';
import StatusBadge from '../components/StatusBadge';
import StatusStepper from '../components/StatusStepper';
import { FINAL_STATUSES, HOME_BY_ROLE, POLL_MS } from '../config';
import { useAsync, useVisiblePolling } from '../hooks/useAsync';
import { formatBytes, formatDateTime, relativeTime, statusLabel, titleCase } from '../lib/format';

export default function RequestDetailPage() {
  const { requestNumber } = useParams();
  const { role } = useAuth();
  const location = useLocation();
  const load = useCallback((signal) => requestDetail(requestNumber, signal), [requestNumber]);
  const { data: r, error, loading, reload, setData } = useAsync(load, [requestNumber]);
  const [flash, setFlash] = useState(location.state?.flash ?? null);
  const [downloadError, setDownloadError] = useState(null);

  // Live status: re-fetch while the request can still change and the tab is visible.
  const live = Boolean(r) && !FINAL_STATUSES.has(r.status);
  useVisiblePolling(() => reload({ silent: true }), POLL_MS.detail, live);

  const back = HOME_BY_ROLE[role] ?? '/';

  if (loading) return <Spinner />;
  if (error) {
    return (
      <section className="narrow">
        <ErrorBox error={error} onRetry={error.status === 404 ? undefined : reload} />
        <Link to={back}>← Back</Link>
      </section>
    );
  }

  const onUpdated = (detail) => {
    setData(detail);
    setFlash(`Request is now ${statusLabel(detail.status).toLowerCase()}.`);
  };
  const onStale = (message) => {
    setFlash(null);
    reload({ silent: true });
    setDownloadError(`${message} The latest version is shown.`);
  };

  async function download(file) {
    setDownloadError(null);
    try {
      await downloadRequestFile(r.requestNumber, file);
    } catch (err) {
      setDownloadError(err.message);
    }
  }

  const issued = r.files.find((f) => f.kind === 'COMPLETED_DOCUMENT');
  // The API returns history oldest-first; reverse it rather than sorting, since steps in one
  // transaction (Verified -> Forwarded) can share a timestamp.
  const history = [...r.history].reverse();

  return (
    <section className="detail">
      <Link to={back} className="back">
        ← Back
      </Link>

      <div className="page-head">
        <div>
          <h1 className="request-number">{r.requestNumber}</h1>
          <p className="muted">
            {r.documentType.name} · submitted {formatDateTime(r.submittedAt)}
          </p>
        </div>
        <div className="page-head__aside">
          <StatusBadge status={r.status} />
          {live && (
            <span className="live" title="Updates automatically while this tab is open">
              <span className="live__dot" aria-hidden="true" /> Live
            </span>
          )}
        </div>
      </div>

      {flash && <Alert tone="good">{flash}</Alert>}
      {downloadError && <Alert tone="warn">{downloadError}</Alert>}

      <div className="card">
        <StatusStepper status={r.status} approvalRequired={r.documentType.approvalRequired} />
        {r.status === 'INCOMPLETE' && r.missingInfo && (
          <Alert tone="warn">
            <strong>Missing information:</strong> {r.missingInfo}
          </Alert>
        )}
        {r.status === 'REJECTED' && (
          <Alert tone="error">
            <strong>Rejected.</strong> {r.approvals.find((a) => a.decision === 'REJECTED')?.comments ?? ''}
          </Alert>
        )}
        {issued && role === 'STUDENT' && (
          <div className="ready">
            <span>Your document is ready.</span>
            <button type="button" className="btn btn--primary" onClick={() => download(issued)}>
              Download {r.documentType.name}
            </button>
          </div>
        )}
      </div>

      <div className="detail__grid">
        <div className="detail__main">
          <ActionPanel request={r} onUpdated={onUpdated} onStale={onStale} />

          <section className="card">
            <h2 className="section-title">Details</h2>
            <dl className="facts">
              {role !== 'STUDENT' && (
                <div>
                  <dt>Student</dt>
                  <dd>
                    {r.student.name} <span className="muted">({r.student.username})</span>
                  </dd>
                </div>
              )}
              {Object.entries(r.fieldValues).map(([k, v]) => (
                <div key={k}>
                  <dt>{k}</dt>
                  <dd>{v}</dd>
                </div>
              ))}
              <div className="facts__wide">
                <dt>Purpose</dt>
                <dd>{r.purpose}</dd>
              </div>
              {r.completedAt && (
                <div>
                  <dt>Completed</dt>
                  <dd>{formatDateTime(r.completedAt)}</dd>
                </div>
              )}
            </dl>

            {r.files.length > 0 && (
              <>
                <h3 className="subhead">Files</h3>
                <ul className="files">
                  {r.files.map((f) => (
                    <li key={f.id}>
                      <button type="button" className="link" onClick={() => download(f)}>
                        {f.fileName}
                      </button>
                      <span className="muted small">
                        {f.kind === 'COMPLETED_DOCUMENT' ? 'Issued document' : 'Supporting file'} · {formatBytes(f.sizeBytes)}
                      </span>
                    </li>
                  ))}
                </ul>
              </>
            )}

            {r.approvals.length > 0 && (
              <>
                <h3 className="subhead">Approval</h3>
                {r.approvals.map((a) => (
                  <p key={a.id} className="small">
                    {titleCase(a.decision)}
                    {a.decidedBy && ` by ${a.decidedBy}`}
                    {a.decidedAt && `, ${formatDateTime(a.decidedAt)}`}
                    {a.comments && <span className="muted"> · “{a.comments}”</span>}
                  </p>
                ))}
              </>
            )}
          </section>
        </div>

        <aside className="card">
          <h2 className="section-title">History</h2>
          <ol className="timeline">
            {history.map((h, i) => (
              <li key={`${h.changedAt}-${i}`} className="timeline__item">
                <div className="timeline__head">
                  <StatusBadge status={h.status} />
                  <time dateTime={h.changedAt} title={formatDateTime(h.changedAt)}>
                    {relativeTime(h.changedAt)}
                  </time>
                </div>
                <div className="muted small">
                  {h.changedBy} · {titleCase(h.changedByRole)}
                </div>
                {h.remarks && <p className="timeline__remark">{h.remarks}</p>}
              </li>
            ))}
          </ol>
        </aside>
      </div>
    </section>
  );
}
