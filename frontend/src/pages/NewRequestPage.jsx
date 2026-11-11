import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { documentTypes, submitRequest } from '../api/doctrack';
import { Alert, EmptyState, ErrorBox, PageHead, Spinner } from '../components/Feedback';
import RequestFields, { UploadProgress, validateRequestValues } from '../components/RequestFields';
import { useAsync } from '../hooks/useAsync';
import { useFilePicker } from '../hooks/useFilePicker';

const EMPTY = { purpose: '', fields: {} };

export default function NewRequestPage() {
  const navigate = useNavigate();
  const types = useAsync((signal) => documentTypes(signal), []);
  const [type, setType] = useState(null);
  const [values, setValues] = useState(EMPTY);
  const picker = useFilePicker();
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [progress, setProgress] = useState(null);
  const ctrlRef = useRef(null);

  useEffect(() => () => ctrlRef.current?.abort(), []);

  function choose(t) {
    setType(t);
    setValues(EMPTY);
    setErrors({});
    setSubmitError(null);
    picker.reset();
  }

  async function onSubmit(e) {
    e.preventDefault();
    setSubmitError(null);
    const found = validateRequestValues(type, values, picker.file);
    if (picker.error) found.attachment = picker.error;
    setErrors(found);
    if (Object.keys(found).length) return;

    ctrlRef.current = new AbortController();
    setProgress(0);
    try {
      const receipt = await submitRequest(
        { documentTypeId: type.id, purpose: values.purpose.trim(), fields: values.fields },
        picker.file,
        { onProgress: setProgress, signal: ctrlRef.current.signal },
      );
      navigate(`/requests/${encodeURIComponent(receipt.requestNumber)}`, {
        replace: true,
        state: { flash: `Request ${receipt.requestNumber} submitted.` },
      });
    } catch (err) {
      setProgress(null);
      if (err.name === 'AbortError') return;
      setErrors(err.fieldMap ?? {});
      setSubmitError(err.message);
      if (err.code === 'E-REQ-002') types.reload(); // type withdrawn while the form was open
    }
  }

  if (types.loading) return <Spinner />;
  if (types.error) return <ErrorBox error={types.error} onRetry={types.reload} />;

  if (!type) {
    return (
      <section>
        <PageHead title="Request a document" />
        {types.data.length === 0 ? (
          <EmptyState title="No documents are being offered right now." />
        ) : (
          <div className="type-grid">
            {types.data.map((t) => (
              <button key={t.id} type="button" className="card type-card" onClick={() => choose(t)}>
                <span className="type-card__name">{t.name}</span>
                {t.description && <span className="type-card__desc">{t.description}</span>}
                <span className="type-card__meta">
                  About {t.processingDays} working day{t.processingDays === 1 ? '' : 's'}
                  {t.approvalRequired && ' · needs approval'}
                  {t.attachmentRequired && ' · file required'}
                </span>
              </button>
            ))}
          </div>
        )}
      </section>
    );
  }

  const busy = progress !== null;
  return (
    <section className="narrow">
      <button type="button" className="back" onClick={() => choose(null)} disabled={busy}>
        ← Choose a different document
      </button>
      <PageHead title={type.name} />
      <form className="card form" onSubmit={onSubmit} noValidate>
        {submitError && <Alert tone="error">{submitError}</Alert>}
        <RequestFields
          type={type}
          values={values}
          onChange={setValues}
          file={picker.file}
          onFile={picker.onFile}
          errors={{ ...errors, ...(picker.error ? { attachment: picker.error } : {}) }}
          disabled={busy}
        />
        <UploadProgress value={progress} />
        <div className="form__actions">
          {busy ? (
            <button type="button" className="btn" onClick={() => ctrlRef.current?.abort()}>
              Cancel upload
            </button>
          ) : (
            <Link to="/requests" className="btn btn--ghost">
              Cancel
            </Link>
          )}
          <button type="submit" className="btn btn--primary" disabled={busy}>
            Submit request
          </button>
        </div>
      </form>
    </section>
  );
}
