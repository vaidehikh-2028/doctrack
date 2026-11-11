import { useEffect, useRef, useState } from 'react';
import * as backend from '../api/doctrack';
import { useAuth } from '../auth/AuthContext';
import { useFilePicker } from '../hooks/useFilePicker';
import { UPLOAD } from '../config';
import { Alert } from './Feedback';
import RequestFields, { UploadProgress, validateRequestValues } from './RequestFields';

/**
 * The action the signed-in user can take on a request right now, derived from role + status.
 * Mirrors the transitions in RequestStatus and the role rules in SecurityConfig.
 */
export function availableAction(role, status) {
  if (role === 'STUDENT' && status === 'INCOMPLETE') return 'resubmit';
  if (role === 'STAFF' && status === 'SUBMITTED') return 'verify';
  if (role === 'STAFF' && status === 'APPROVED') return 'start';
  if (role === 'STAFF' && status === 'PROCESSING') return 'complete';
  if (role === 'APPROVER' && status === 'FORWARDED') return 'decide';
  return null;
}

/**
 * Runs an action and handles the outcomes every action shares:
 * success -> parent gets the fresh RequestDetail; 409 -> someone else acted first, so reload.
 */
function useAction(onUpdated, onStale) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  async function run(fn) {
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      const detail = await fn();
      onUpdated(detail);
      return true;
    } catch (err) {
      if (err.name === 'AbortError') return false;
      if (err.isConflict) {
        onStale(err.message);
      } else {
        setError(err.message);
        setFieldErrors(err.fieldMap ?? {});
      }
      return false;
    } finally {
      setBusy(false);
    }
  }
  return { busy, error, fieldErrors, run, setError };
}

export default function ActionPanel({ request, onUpdated, onStale }) {
  const { role } = useAuth();
  const action = availableAction(role, request.status);
  if (!action) return null;

  const props = { request, onUpdated, onStale };
  return (
    <section className="card action-panel" aria-label="Actions">
      {action === 'verify' && <VerifyForm {...props} />}
      {action === 'start' && <StartProcessingForm {...props} />}
      {action === 'complete' && <CompleteForm {...props} />}
      {action === 'decide' && <DecisionForm {...props} />}
      {action === 'resubmit' && <ResubmitForm {...props} />}
    </section>
  );
}

function VerifyForm({ request, onUpdated, onStale }) {
  const [mode, setMode] = useState('VERIFIED');
  const [missingInfo, setMissingInfo] = useState('');
  const [remarks, setRemarks] = useState('');
  const { busy, error, fieldErrors, run, setError } = useAction(onUpdated, onStale);
  const next = request.documentType.approvalRequired ? 'forwarded to the approving authority' : 'moved to processing';

  function submit(e) {
    e.preventDefault();
    if (mode === 'INCOMPLETE' && !missingInfo.trim()) {
      setError('Specify the missing information.');
      return;
    }
    run(() =>
      backend.verify(request.requestNumber, {
        outcome: mode,
        missingInfo: mode === 'INCOMPLETE' ? missingInfo.trim() : undefined,
        remarks: remarks.trim() || undefined,
      }),
    );
  }

  return (
    <form onSubmit={submit} noValidate>
      <h2 className="action-panel__title">Verify this request</h2>
      <div className="segmented" role="radiogroup" aria-label="Outcome">
        {[
          ['VERIFIED', 'Details are complete'],
          ['INCOMPLETE', 'Information is missing'],
        ].map(([value, label]) => (
          <label key={value} className={mode === value ? 'is-on' : ''}>
            <input type="radio" name="outcome" value={value} checked={mode === value} onChange={() => setMode(value)} />
            {label}
          </label>
        ))}
      </div>
      {mode === 'INCOMPLETE' ? (
        <label className="field">
          <span>What is missing? (sent to the student)</span>
          <textarea rows={2} maxLength={500} value={missingInfo} onChange={(e) => setMissingInfo(e.target.value)} />
          {fieldErrors.missingInfo && <small className="field__error">{fieldErrors.missingInfo}</small>}
        </label>
      ) : (
        <p className="muted small">On verification the request is {next}.</p>
      )}
      <label className="field">
        <span>Internal remarks (optional)</span>
        <input maxLength={500} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
      </label>
      {error && <Alert>{error}</Alert>}
      <div className="form__actions">
        <button className={`btn ${mode === 'INCOMPLETE' ? 'btn--warn' : 'btn--primary'}`} disabled={busy}>
          {busy ? 'Saving…' : mode === 'INCOMPLETE' ? 'Mark incomplete' : 'Verify'}
        </button>
      </div>
    </form>
  );
}

function StartProcessingForm({ request, onUpdated, onStale }) {
  const [remarks, setRemarks] = useState('');
  const { busy, error, run } = useAction(onUpdated, onStale);
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        run(() => backend.startProcessing(request.requestNumber, remarks.trim() || undefined));
      }}
    >
      <h2 className="action-panel__title">Approved: start preparing the document</h2>
      <label className="field">
        <span>Remarks (optional)</span>
        <input maxLength={500} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
      </label>
      {error && <Alert>{error}</Alert>}
      <div className="form__actions">
        <button className="btn btn--primary" disabled={busy}>
          {busy ? 'Saving…' : 'Start processing'}
        </button>
      </div>
    </form>
  );
}

function CompleteForm({ request, onUpdated, onStale }) {
  const picker = useFilePicker();
  const [remarks, setRemarks] = useState('');
  const [progress, setProgress] = useState(null);
  const { busy, error, run, setError } = useAction(onUpdated, onStale);

  async function submit(e) {
    e.preventDefault();
    if (!picker.file) {
      setError(picker.error ?? 'Upload the completed document.');
      return;
    }
    setProgress(0);
    await run(() =>
      backend.completeRequest(request.requestNumber, picker.file, remarks.trim() || undefined, { onProgress: setProgress }),
    );
    setProgress(null);
  }

  return (
    <form onSubmit={submit} noValidate>
      <h2 className="action-panel__title">Issue the document</h2>
      <label className="field">
        <span>Completed document</span>
        <input type="file" accept={UPLOAD.accept} onChange={picker.onFile} disabled={busy} />
        {picker.error && <small className="field__error">{picker.error}</small>}
      </label>
      <label className="field">
        <span>Remarks (optional)</span>
        <input maxLength={500} value={remarks} onChange={(e) => setRemarks(e.target.value)} disabled={busy} />
      </label>
      <UploadProgress value={progress} />
      {error && <Alert>{error}</Alert>}
      <div className="form__actions">
        <button className="btn btn--primary" disabled={busy}>
          {busy ? 'Uploading…' : 'Upload and complete'}
        </button>
      </div>
    </form>
  );
}

function DecisionForm({ request, onUpdated, onStale }) {
  const [decision, setDecision] = useState(null);
  const [reason, setReason] = useState('');
  const [comments, setComments] = useState('');
  const { busy, error, fieldErrors, run, setError } = useAction(onUpdated, onStale);
  const reasonRef = useRef(null);

  useEffect(() => {
    if (decision === 'REJECTED') reasonRef.current?.focus();
  }, [decision]);

  // The decision is passed explicitly rather than read from state: a click that sets state and
  // submits in the same event would otherwise send the previous (null) value.
  function send(chosen) {
    if (chosen === 'REJECTED' && !reason.trim()) {
      setError('A reason is required to reject a request.');
      return;
    }
    run(() =>
      backend.decide(request.requestNumber, {
        decision: chosen,
        reason: chosen === 'REJECTED' ? reason.trim() : undefined,
        comments: comments.trim() || undefined,
      }),
    );
  }

  function submit(e) {
    e.preventDefault();
    send(decision === 'REJECTED' ? 'REJECTED' : 'APPROVED');
  }

  return (
    <form onSubmit={submit} noValidate>
      <h2 className="action-panel__title">Your decision</h2>
      {decision === 'REJECTED' && (
        <label className="field">
          <span>Reason for rejection (shown to the student)</span>
          <textarea ref={reasonRef} rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
          {fieldErrors.reason && <small className="field__error">{fieldErrors.reason}</small>}
        </label>
      )}
      <label className="field">
        <span>Comments (optional)</span>
        <input maxLength={500} value={comments} onChange={(e) => setComments(e.target.value)} />
      </label>
      {error && <Alert>{error}</Alert>}
      <div className="form__actions">
        {decision === 'REJECTED' ? (
          <>
            <button type="button" className="btn btn--ghost" onClick={() => setDecision(null)} disabled={busy}>
              Back
            </button>
            <button className="btn btn--danger" disabled={busy}>
              {busy ? 'Saving…' : 'Confirm rejection'}
            </button>
          </>
        ) : (
          <>
            <button type="button" className="btn btn--danger-outline" onClick={() => setDecision('REJECTED')} disabled={busy}>
              Reject
            </button>
            <button type="button" className="btn btn--primary" onClick={() => send('APPROVED')} disabled={busy}>
              {busy ? 'Saving…' : 'Approve'}
            </button>
          </>
        )}
      </div>
    </form>
  );
}

function ResubmitForm({ request, onUpdated, onStale }) {
  const hasAttachment = request.files.some((f) => f.kind === 'ATTACHMENT');
  // A new file is needed only if the type requires one and none was uploaded before (RequestService.resubmit).
  const type = { ...request.documentType, attachmentRequired: request.documentType.attachmentRequired && !hasAttachment };
  const [values, setValues] = useState({ purpose: request.purpose ?? '', fields: { ...request.fieldValues } });
  const picker = useFilePicker();
  const [errors, setErrors] = useState({});
  const [progress, setProgress] = useState(null);
  const { busy, error, fieldErrors, run } = useAction(onUpdated, onStale);

  async function submit(e) {
    e.preventDefault();
    const found = validateRequestValues(type, values, picker.file);
    setErrors(found);
    if (Object.keys(found).length || picker.error) return;
    setProgress(0);
    await run(() =>
      backend.resubmitRequest(
        request.requestNumber,
        { purpose: values.purpose.trim(), fields: values.fields },
        picker.file,
        { onProgress: setProgress },
      ),
    );
    setProgress(null);
  }

  return (
    <form onSubmit={submit} noValidate>
      <h2 className="action-panel__title">Update and resubmit</h2>
      <RequestFields
        type={type}
        values={values}
        onChange={setValues}
        file={picker.file}
        onFile={picker.onFile}
        errors={{ ...fieldErrors, ...errors, ...(picker.error ? { attachment: picker.error } : {}) }}
        disabled={busy}
      />
      {hasAttachment && <p className="muted small">Your earlier file is kept unless you upload a new one.</p>}
      <UploadProgress value={progress} />
      {error && <Alert>{error}</Alert>}
      <div className="form__actions">
        <button className="btn btn--primary" disabled={busy}>
          {busy ? 'Resubmitting…' : 'Resubmit'}
        </button>
      </div>
    </form>
  );
}
