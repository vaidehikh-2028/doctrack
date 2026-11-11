import { useState } from 'react';
import { createUser, listUsers, updateUser } from '../api/doctrack';
import { useAuth } from '../auth/AuthContext';
import { Alert, ErrorBox, PageHead, Spinner } from '../components/Feedback';
import { ROLES } from '../config';
import { useAsync } from '../hooks/useAsync';
import { titleCase } from '../lib/format';

const BLANK = { username: '', name: '', email: '', contactInfo: '', role: 'STUDENT', password: '' };

export default function AdminUsersPage() {
  const { user: me } = useAuth();
  const { data, error, loading, reload, setData } = useAsync((signal) => listUsers(signal), []);
  const [form, setForm] = useState(null);
  const [formErrors, setFormErrors] = useState({});
  const [message, setMessage] = useState(null);
  const [busyId, setBusyId] = useState(null);

  async function patch(u, change) {
    setBusyId(u.id);
    setMessage(null);
    try {
      const updated = await updateUser(u.id, change);
      setData(data.map((x) => (x.id === u.id ? updated : x)));
      setMessage({ tone: 'good', text: `${updated.name} updated.` });
    } catch (err) {
      setMessage({ tone: 'error', text: err.message });
    } finally {
      setBusyId(null);
    }
  }

  async function create(e) {
    e.preventDefault();
    setFormErrors({});
    setMessage(null);
    try {
      const created = await createUser({ ...form, contactInfo: form.contactInfo || undefined });
      setData([...data, created]);
      setForm(null);
      setMessage({ tone: 'good', text: `${created.name} created.` });
    } catch (err) {
      setFormErrors(err.fieldMap ?? {});
      setMessage({ tone: 'error', text: err.message });
    }
  }

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  return (
    <section>
      <PageHead title="Users">
        {!form && (
          <button type="button" className="btn btn--primary" onClick={() => setForm(BLANK)}>
            Add user
          </button>
        )}
      </PageHead>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}

      {form && (
        <form className="card form form--grid" onSubmit={create} noValidate>
          {[
            ['username', 'User ID'],
            ['name', 'Full name'],
            ['email', 'Email', 'email'],
            ['contactInfo', 'Phone (optional)'],
            ['password', 'Initial password (8+ characters)', 'password'],
          ].map(([k, label, type]) => (
            <label key={k} className="field">
              <span>{label}</span>
              <input type={type ?? 'text'} value={form[k]} onChange={set(k)} autoComplete="off" />
              {formErrors[k] && <small className="field__error">{formErrors[k]}</small>}
            </label>
          ))}
          <label className="field">
            <span>Role</span>
            <select value={form.role} onChange={set('role')}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {titleCase(r)}
                </option>
              ))}
            </select>
          </label>
          <div className="form__actions form__actions--span">
            <button type="button" className="btn btn--ghost" onClick={() => setForm(null)}>
              Cancel
            </button>
            <button className="btn btn--primary">Create user</button>
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
                <th>User</th>
                <th>Email</th>
                <th>Role</th>
                <th>Account</th>
              </tr>
            </thead>
            <tbody>
              {data.map((u) => {
                const self = u.id === me?.id;
                return (
                  <tr key={u.id} className={u.accountStatus === 'INACTIVE' ? 'row--muted' : undefined}>
                    <td>
                      {u.name} <span className="muted mono">({u.username})</span>
                    </td>
                    <td>{u.email}</td>
                    <td>
                      <select
                        value={u.role}
                        disabled={busyId === u.id || self}
                        onChange={(e) => patch(u, { role: e.target.value })}
                        aria-label={`Role for ${u.username}`}
                      >
                        {ROLES.map((r) => (
                          <option key={r} value={r}>
                            {titleCase(r)}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <button
                        type="button"
                        className={`btn btn--small ${u.accountStatus === 'ACTIVE' ? 'btn--ghost' : 'btn--primary'}`}
                        disabled={busyId === u.id || self}
                        title={self ? 'You cannot deactivate your own account' : undefined}
                        onClick={() => patch(u, { accountStatus: u.accountStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' })}
                      >
                        {u.accountStatus === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
