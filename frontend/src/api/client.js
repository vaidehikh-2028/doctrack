import { API_BASE_URL } from '../config';

/**
 * Every backend error has the shape {code, message, fieldErrors: [{field, message}], timestamp}
 * (web/dto/ApiError). ApiError keeps the catalogue code so the UI can react to specific cases.
 */
export class ApiError extends Error {
  constructor(status, message, body = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code ?? null;
    this.fieldErrors = Array.isArray(body?.fieldErrors) ? body.fieldErrors : [];
  }

  /** {field: message} for form highlighting. */
  get fieldMap() {
    return Object.fromEntries(this.fieldErrors.map((f) => [f.field, f.message]));
  }

  /**
   * 409s mean the request moved on under us: a concurrent commit lost the optimistic-lock race
   * (E-SYS-409), another user already acted (E-VER-001, E-APR-001), or a type was withdrawn (E-REQ-002).
   */
  get isConflict() {
    return this.status === 409;
  }
}

let tokenGetter = () => null;
let onUnauthorized = () => {};

/** Wired once by AuthProvider, keeping this module free of React. */
export function configureClient({ getToken, unauthorized }) {
  tokenGetter = getToken;
  onUnauthorized = unauthorized;
}

function authHeaders(auth) {
  const headers = { Accept: 'application/json' };
  const token = auth ? tokenGetter() : null;
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

function toError(status, body) {
  const message =
    (body && typeof body === 'object' && body.message) ||
    (typeof body === 'string' && body) ||
    (status === 0 ? 'Could not reach the server. Check your connection.' : `Request failed (${status}).`);
  return new ApiError(status, message, typeof body === 'object' ? body : null);
}

async function readBody(res) {
  if (res.status === 204) return null;
  const type = res.headers.get('content-type') ?? '';
  if (type.includes('json')) return res.json().catch(() => null);
  return res.text().catch(() => null);
}

/**
 * fetch wrapper: JSON or FormData body, Bearer token, timeout, typed errors.
 * A 401 on an authenticated call means the token is invalid or expired (E-AUTH-440): sign out.
 */
export async function api(path, { method = 'GET', body, signal, timeoutMs = 20_000, auth = true } = {}) {
  const headers = authHeaders(auth);
  let payload = body;
  if (body !== undefined && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(new DOMException('Timeout', 'TimeoutError')), timeoutMs);
  const relay = () => ctrl.abort(signal.reason);
  signal?.addEventListener('abort', relay);

  let res;
  try {
    res = await fetch(API_BASE_URL + path, { method, headers, body: payload, signal: ctrl.signal });
  } catch (err) {
    if (signal?.aborted) throw err;
    if (ctrl.signal.reason?.name === 'TimeoutError') {
      throw new ApiError(0, 'The server is taking too long. It may be waking up; try again in a minute.');
    }
    throw toError(0, null);
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', relay);
  }

  const data = await readBody(res);
  if (res.ok) return data;
  const error = toError(res.status, data);
  if (res.status === 401 && auth) onUnauthorized(error.message);
  throw error;
}

/** Authenticated download: fetch as a blob, then hand it to the browser via a temporary object URL. */
export async function downloadFile(path, fileName) {
  const res = await fetch(API_BASE_URL + path, { headers: authHeaders(true) });
  if (!res.ok) {
    const error = toError(res.status, await readBody(res));
    if (res.status === 401) onUnauthorized(error.message);
    throw error;
  }
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/**
 * Multipart POST via XMLHttpRequest, because fetch does not report upload progress.
 * onProgress receives 0-100.
 */
export function upload(path, formData, { onProgress, signal } = {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', API_BASE_URL + path);
    for (const [k, v] of Object.entries(authHeaders(true))) xhr.setRequestHeader(k, v);

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress?.(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () => {
      let data = null;
      try {
        data = xhr.responseText ? JSON.parse(xhr.responseText) : null;
      } catch {
        data = xhr.responseText;
      }
      if (xhr.status >= 200 && xhr.status < 300) return resolve(data);
      const error = toError(xhr.status, data);
      if (xhr.status === 401) onUnauthorized(error.message);
      reject(error);
    };
    xhr.onerror = () => reject(toError(0, null));
    xhr.onabort = () => reject(new DOMException('Aborted', 'AbortError'));
    signal?.addEventListener('abort', () => xhr.abort());
    xhr.send(formData);
  });
}
