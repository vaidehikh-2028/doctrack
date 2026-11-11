import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Alert } from '../components/Feedback';
import { HOME_BY_ROLE } from '../config';

export default function LoginPage() {
  const { login, isAuthenticated, role, notice } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const target = (r) => location.state?.from?.pathname ?? HOME_BY_ROLE[r] ?? '/';

  if (isAuthenticated) return <Navigate to={target(role)} replace />;

  async function onSubmit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const user = await login(username.trim(), password);
      navigate(target(user.role), { replace: true });
    } catch (err) {
      // The backend deliberately returns one generic message for any bad credential (FR-002).
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <div className="auth">
      <form className="card auth__card" onSubmit={onSubmit} noValidate>
        <div className="brand brand--stacked">
          <span className="brand__logo" aria-hidden="true">
            D
          </span>
          <span className="brand__name">DocTrack</span>
        </div>
        <p className="muted center">Request and track official documents from LNMIIT.</p>

        {notice && !error && <Alert tone="info">{notice}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}

        <label className="field">
          <span>User ID or email</span>
          <input
            name="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button className="btn btn--primary btn--block" type="submit" disabled={busy || !username.trim() || !password}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
        <p className="muted small center">The demo server may take about a minute to wake up.</p>
      </form>
    </div>
  );
}
