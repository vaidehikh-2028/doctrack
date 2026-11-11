import { api, downloadFile, upload } from './client';

const enc = encodeURIComponent;

// ---------- auth ----------
/** POST /api/auth/login, accepting a user ID or email -> {token, expiresInSeconds, user} */
export const login = (username, password) =>
  api('/api/auth/login', { method: 'POST', body: { username, password }, auth: false });

/** GET /api/auth/me -> {id, username, name, role}; used to restore a session from a stored token. */
export const me = (signal) => api('/api/auth/me', { signal });

// ---------- shared ----------
export const documentTypes = (signal) => api('/api/document-types', { signal });
export const requestDetail = (no, signal) => api(`/api/requests/${enc(no)}`, { signal });
export const myNotifications = (signal) => api('/api/notifications/mine', { signal });
export const downloadRequestFile = (no, file) =>
  downloadFile(`/api/requests/${enc(no)}/files/${file.id}`, file.fileName);

// ---------- student ----------
export const myRequests = (signal) => api('/api/requests/mine', { signal });

function requestForm({ documentTypeId, purpose, fields }, file) {
  const form = new FormData();
  if (documentTypeId != null) form.append('documentTypeId', String(documentTypeId));
  form.append('purpose', purpose);
  form.append('fields', JSON.stringify(fields ?? {}));
  if (file) form.append('file', file);
  return form;
}

/** Multipart: documentTypeId, purpose, fields (JSON), file -> {requestNumber, status, submittedAt} */
export const submitRequest = (values, file, opts) => upload('/api/requests', requestForm(values, file), opts);

/** INCOMPLETE -> SUBMITTED with corrected details. */
export const resubmitRequest = (no, values, file, opts) =>
  upload(`/api/requests/${enc(no)}/resubmit`, requestForm(values, file), opts);

// ---------- staff ----------
export function staffQueue(statuses, signal) {
  const qs = (statuses ?? []).map((s) => `status=${enc(s)}`).join('&');
  return api(`/api/staff/requests${qs ? `?${qs}` : ''}`, { signal });
}
/** outcome: VERIFIED | INCOMPLETE (missingInfo required for INCOMPLETE) */
export const verify = (no, { outcome, missingInfo, remarks }) =>
  api(`/api/staff/requests/${enc(no)}/verify`, { method: 'POST', body: { outcome, missingInfo, remarks } });
export const startProcessing = (no, remarks) =>
  api(`/api/staff/requests/${enc(no)}/start-processing`, { method: 'POST', body: { remarks } });
export function completeRequest(no, file, remarks, opts) {
  const form = new FormData();
  if (file) form.append('file', file);
  if (remarks) form.append('remarks', remarks);
  return upload(`/api/staff/requests/${enc(no)}/complete`, form, opts);
}

// ---------- approver ----------
export const pendingApprovals = (signal) => api('/api/approvals/pending', { signal });
export const myDecisions = (signal) => api('/api/approvals/history', { signal });
/** decision: APPROVED | REJECTED (reason required for REJECTED) */
export const decide = (no, { decision, reason, comments }) =>
  api(`/api/approvals/${enc(no)}/decision`, { method: 'POST', body: { decision, reason, comments } });

// ---------- admin & reports ----------
export const reportSummary = (signal) => api('/api/reports/summary', { signal });
export const listUsers = (signal) => api('/api/admin/users', { signal });
export const createUser = (body) => api('/api/admin/users', { method: 'POST', body });
export const updateUser = (id, body) => api(`/api/admin/users/${id}`, { method: 'PATCH', body });
export const adminTypes = (signal) => api('/api/admin/document-types', { signal });
export const saveType = (id, body) =>
  id == null
    ? api('/api/admin/document-types', { method: 'POST', body })
    : api(`/api/admin/document-types/${id}`, { method: 'PUT', body });
