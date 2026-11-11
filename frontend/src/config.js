/**
 * Contracts shared with the Spring Boot backend (com.lnmiit.docreq).
 * Keep in sync with RequestStatus, RequestValidator and the REST controllers.
 */

/** Empty in production: the UI is served by the API's own origin. */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

/** Mirrors RequestStatus. */
export const STATUS = {
  SUBMITTED: 'Submitted',
  INCOMPLETE: 'Incomplete',
  VERIFIED: 'Verified',
  FORWARDED: 'Forwarded',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
};

export const FINAL_STATUSES = new Set(['REJECTED', 'COMPLETED']);

/** Mirrors RequestValidator. */
export const PURPOSE_MAX_LENGTH = 250;
export const UPLOAD = {
  maxBytes: 5 * 1024 * 1024, // inclusive, as on the server (TC-05 / TC-06)
  extensions: ['pdf', 'png', 'jpg', 'jpeg'],
  accept: '.pdf,.png,.jpg,.jpeg',
};

/** Default staff queue when no filter is chosen, as in VerificationService.queue. */
export const STAFF_DEFAULT_STATUSES = ['SUBMITTED', 'APPROVED', 'PROCESSING'];

export const ROLES = ['STUDENT', 'STAFF', 'APPROVER', 'ADMIN'];

/** Where each role lands after login. */
export const HOME_BY_ROLE = {
  STUDENT: '/requests',
  STAFF: '/queue',
  APPROVER: '/approvals',
  ADMIN: '/admin/users',
};

/** Detail and list pages refresh this often while the tab is visible. */
export const POLL_MS = { detail: 10_000, list: 30_000 };
