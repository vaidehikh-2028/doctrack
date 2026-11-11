import { beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, mockFetch } from '../test/helpers';
import { api, ApiError, configureClient } from './client';

describe('api client', () => {
  let unauthorized;

  beforeEach(() => {
    unauthorized = vi.fn();
    configureClient({ getToken: () => 'tok-123', unauthorized });
  });

  it('sends the bearer token and JSON body', async () => {
    const fetch = mockFetch({ 'POST /api/x': { ok: true } });
    await expect(api('/api/x', { method: 'POST', body: { a: 1 } })).resolves.toEqual({ ok: true });
    const [, init] = fetch.mock.calls[0];
    expect(init.headers.Authorization).toBe('Bearer tok-123');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe('{"a":1}');
  });

  it('omits the token for unauthenticated calls', async () => {
    const fetch = mockFetch({ 'POST /api/auth/login': { token: 't' } });
    await api('/api/auth/login', { method: 'POST', body: {}, auth: false });
    expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('lets the browser set the multipart boundary for FormData', async () => {
    const fetch = mockFetch({ 'POST /api/up': { ok: true } });
    await api('/api/up', { method: 'POST', body: new FormData() });
    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBeUndefined();
  });

  it('maps the backend error catalogue into ApiError with field errors', async () => {
    mockFetch({
      'POST /api/requests': () =>
        jsonResponse(
          { code: 'E-REQ-001', message: 'Please complete the highlighted fields.', fieldErrors: [{ field: 'purpose', message: 'Purpose is required.' }] },
          400,
        ),
    });
    const err = await api('/api/requests', { method: 'POST', body: {} }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('E-REQ-001');
    expect(err.message).toBe('Please complete the highlighted fields.');
    expect(err.fieldMap).toEqual({ purpose: 'Purpose is required.' });
    expect(err.isConflict).toBe(false);
  });

  it('flags 409 optimistic-lock failures as conflicts', async () => {
    mockFetch({ 'POST /api/staff/requests/R1/verify': () => jsonResponse({ code: 'E-SYS-409', message: 'Changed by someone else.' }, 409) });
    const err = await api('/api/staff/requests/R1/verify', { method: 'POST', body: {} }).catch((e) => e);
    expect(err.isConflict).toBe(true);
    expect(err.code).toBe('E-SYS-409');
  });

  it('signs the user out on 401 with the server message (expired session)', async () => {
    mockFetch({ 'GET /api/requests/mine': () => jsonResponse({ code: 'E-AUTH-440', message: 'Your session has expired.' }, 401) });
    await expect(api('/api/requests/mine')).rejects.toMatchObject({ status: 401 });
    expect(unauthorized).toHaveBeenCalledWith('Your session has expired.');
  });

  it('reports network failures as status 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const err = await api('/api/x').catch((e) => e);
    expect(err.status).toBe(0);
    expect(err.message).toMatch(/could not reach/i);
  });

  it('times out slow requests with a helpful message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_, init) => new Promise((_, reject) => init.signal.addEventListener('abort', () => reject(new DOMException('a', 'AbortError'))))),
    );
    const err = await api('/api/x', { timeoutMs: 10 }).catch((e) => e);
    expect(err.status).toBe(0);
    expect(err.message).toMatch(/waking up/);
  });
});
