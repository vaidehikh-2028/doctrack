import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import LoginPage from '../pages/LoginPage';
import { jsonResponse, mockFetch, renderApp, validToken } from '../test/helpers';
import { useAuth } from './AuthContext';
import RequireAuth from './RequireAuth';

const student = { id: 1, username: 'student1', name: 'Aarav Sharma', role: 'STUDENT' };

function Whoami() {
  const { user } = useAuth();
  return <div>signed in as {user?.name}</div>;
}

describe('login', () => {
  it('logs in, stores the token and lands on the role home page', async () => {
    const token = validToken();
    const fetch = mockFetch({ 'POST /api/auth/login': { token, expiresInSeconds: 7200, user: student } });
    renderApp(<LoginPage />, { path: '/login', route: '/login' });
    await userEvent.type(screen.getByLabelText(/user id or email/i), 'student1');
    await userEvent.type(screen.getByLabelText(/password/i), 'Demo@1234');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));

    expect(await screen.findByText('student home')).toBeInTheDocument();
    expect(sessionStorage.getItem('doctrack.token')).toBe(token);
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ username: 'student1', password: 'Demo@1234' });
  });

  it("shows the server's generic message on bad credentials", async () => {
    mockFetch({
      'POST /api/auth/login': () =>
        jsonResponse({ code: 'E-AUTH-401', message: 'Invalid credentials. Please check your details and try again.' }, 401),
    });
    renderApp(<LoginPage />, { path: '/login', route: '/login' });
    await userEvent.type(screen.getByLabelText(/user id or email/i), 'student1');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid credentials/i);
    expect(sessionStorage.getItem('doctrack.token')).toBeNull();
  });
});

describe('session restore and expiry', () => {
  it('restores a stored token via /api/auth/me', async () => {
    sessionStorage.setItem('doctrack.token', validToken());
    const fetch = mockFetch({ 'GET /api/auth/me': student });
    renderApp(
      <RequireAuth>
        <Whoami />
      </RequireAuth>,
    );
    expect(await screen.findByText('signed in as Aarav Sharma')).toBeInTheDocument();
    expect(fetch.mock.calls[0][1].headers.Authorization).toMatch(/^Bearer /);
  });

  it('drops a token the server rejects and redirects to login', async () => {
    sessionStorage.setItem('doctrack.token', validToken());
    mockFetch({ 'GET /api/auth/me': () => jsonResponse({ code: 'E-AUTH-401', message: 'Please log in to continue.' }, 401) });
    renderApp(
      <RequireAuth>
        <Whoami />
      </RequireAuth>,
    );
    expect(await screen.findByText('login page')).toBeInTheDocument();
    expect(sessionStorage.getItem('doctrack.token')).toBeNull();
  });

  it('ignores an already-expired stored token without calling the API', async () => {
    sessionStorage.setItem('doctrack.token', validToken({ exp: Math.floor(Date.now() / 1000) - 5 }));
    const fetch = mockFetch({});
    renderApp(
      <RequireAuth>
        <Whoami />
      </RequireAuth>,
    );
    expect(await screen.findByText('login page')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('signs out exactly when the token expires', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    sessionStorage.setItem('doctrack.token', validToken({ exp: Math.floor(Date.now() / 1000) + 60 }));
    mockFetch({ 'GET /api/auth/me': student });
    renderApp(
      <RequireAuth>
        <Whoami />
      </RequireAuth>,
    );
    expect(await screen.findByText(/signed in as/)).toBeInTheDocument();
    await act(async () => {
      vi.advanceTimersByTime(61_000);
    });
    expect(await screen.findByText('login page')).toBeInTheDocument();
  });

  it('keeps students out of staff-only routes', async () => {
    sessionStorage.setItem('doctrack.token', validToken());
    mockFetch({ 'GET /api/auth/me': student });
    renderApp(
      <RequireAuth roles={['STAFF']}>
        <div>staff only</div>
      </RequireAuth>,
    );
    await waitFor(() => expect(screen.queryByText(/restoring/i)).not.toBeInTheDocument());
    expect(screen.queryByText('staff only')).not.toBeInTheDocument();
  });
});
