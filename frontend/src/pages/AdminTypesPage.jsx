import { useState } from 'react';
import { adminTypes, saveType } from '../api/doctrack';
import { Alert, ErrorBox, PageHead, Spinner } from '../components/Feedback';
import { useAsync } from '../hooks/useAsync';

const BLANK = {
  id: null,
  code: '',
  name: '',
  description: '',
  requiredFields: '',
  attachmentRequired: false,
  approvalRequired: false,
  processingDays: 3,
  active: true,
};

const toForm = (t) => ({ ...t, description: t.description ?? '', requiredFields: t.requiredFields.join(', ') });

export default function AdminTypesPage() {
  const { data, error, loading, reload, setData } = useAsync((signal) => adminTypes(signal), []);
  const [form, setForm] = useState(null);
  const [formErrors, setFormErrors] = useState({});
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setFormErrors({});
    setMessage(null);
    const body = {
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description.trim() || null,
      requiredFields: form.requiredFields
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
      attachmentRequired: form.attachmentRequired,
      approvalRequired: form.approvalRequired,
      processingDays: Number(form.processingDays),
      active: form.active,
    };
    try {
      const saved = await saveType(form.id, body);
      setData(form.id == null ? [...data, saved] : data.map((t) => (t.id === saved.id ? saved : t)));
      setForm(null);
      setMessage({ tone: 'good', text: `${saved.name} saved.` });
    } catch (err) {
      setFormErrors(err.fieldMap ?? {});
      setMessage({ tone: 'error', text: err.message });
    } finally {
      setBusy(false);
    }
  }

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  return (
    <section>
      <PageHead title="Document types">
        {!form && (
          <button type="button" className="btn btn--primary" onClick={() => setForm(BLANK)}>
            Add type
          </button>
        )}
      </PageHead>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}

      {form && (
        <form className="card form form--grid" onSubmit={submit} noValidate>
          <h2 className="section-title form__actions--span">{form.id == null ? 'New document type' : `Edit ${form.name}`}</h2>
          <label className="field">
            <span>Code (max 10)</span>
            <input value={form.code} onChange={set('code')} maxLength={10} />
            {formErrors.code && <small className="field__error">{formErrors.code}</small>}
          </label>
          <label className="field">
            <span>Name</span>
            <input value={form.name} onChange={set('name')} maxLength={100} />
            {formErrors.name && <small className="field__error">{formErrors.name}</small>}
          </label>
          <label className="field form__actions--span">
            <span>Description</span>
            <input value={form.description} onChange={set('description')} maxLength={500} />
          </label>
          <label className="field form__actions--span">
            <span>Required fields (comma-separated)</span>
            <input value={form.requiredFields} onChange={set('requiredFields')} placeholder="e.g. Roll number, Programme" />
          </label>
          <label className="field">
            <span>Processing days</span>
            <input type="number" min={1} value={form.processingDays} onChange={set('processingDays')} />
            {formErrors.processingDays && <small className="field__error">{formErrors.processingDays}</small>}
          </label>
          <div className="checks">
            <label>
              <input type="checkbox" checked={form.attachmentRequired} onChange={set('attachmentRequired')} /> Supporting file required
            </label>
            <label>
              <input type="checkbox" checked={form.approvalRequired} onChange={set('approvalRequired')} /> Needs approval
            </label>
            <label>
              <input type="checkbox" checked={form.active} onChange={set('active')} /> Offered to students
            </label>
          </div>
          <div className="form__actions form__actions--span">
            <button type="button" className="btn btn--ghost" onClick={() => setForm(null)} disabled={busy}>
              Cancel
            </button>
            <button className="btn btn--primary" disabled={busy}>
              {busy ? 'Saving…' : 'Save'}
            </button>
          </div>
        </form>
      )}

      {loading && <Spinner />}
      <ErrorBox error={error} onRetry={reload} />
      {data && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Required fields</th>
                <th>Rules</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.map((t) => (
                <tr key={t.id} className={t.active ? undefined : 'row--muted'}>
                  <td className="mono">{t.code}</td>
                  <td>
                    {t.name}
                    {!t.active && <span className="muted"> (withdrawn)</span>}
                  </td>
                  <td>{t.requiredFields.join(', ') || <span className="muted">—</span>}</td>
                  <td className="small">
                    {t.processingDays} d{t.approvalRequired && ' · approval'}
                    {t.attachmentRequired && ' · file'}
                  </td>
                  <td>
                    <button type="button" className="btn btn--small btn--ghost" onClick={() => setForm(toForm(t))}>
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
