import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';

/** Build an unsigned JWT with the given claims (the client never verifies signatures). */
export function fakeJwt(claims) {
  const b64 = (o) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.sig`;
}

export const validToken = (extra = {}) =>
  fakeJwt({ sub: '1', role: 'STUDENT', exp: Math.floor(Date.now() / 1000) + 3600, ...extra });

export function jsonResponse(body, status = 200) {
  return new Response(body == null ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * Routes fetch calls by "METHOD /path" to handlers. Unmatched calls fail the test loudly.
 * Returns the mock so tests can inspect calls.
 */
export function mockFetch(routes) {
  const fn = vi.fn(async (url, init = {}) => {
    const path = new URL(url, 'http://localhost').pathname + new URL(url, 'http://localhost').search;
    const key = `${init.method ?? 'GET'} ${path}`;
    const handler = routes[key] ?? routes[key.split('?')[0]];
    if (!handler) throw new Error(`Unexpected fetch: ${key}`);
    return typeof handler === 'function' ? handler(init) : jsonResponse(handler);
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

/** Render `ui` inside the real AuthProvider and a router at `path`. */
export function renderApp(ui, { path = '/', route = '*' } = {}) {
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <Routes>
          <Route path={route} element={ui} />
          <Route path="/login" element={<div>login page</div>} />
          <Route path="/requests" element={<div>student home</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}
