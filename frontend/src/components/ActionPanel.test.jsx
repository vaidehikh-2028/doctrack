import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { jsonResponse, mockFetch, renderApp, validToken } from '../test/helpers';
import ActionPanel, { availableAction } from './ActionPanel';

const baseRequest = {
  requestNumber: 'REQ-2026-000001',
  status: 'SUBMITTED',
  version: 3,
  purpose: 'Passport application',
  fieldValues: { 'Roll number': '24UCS289' },
  documentType: { id: 1, name: 'Bonafide Certificate', requiredFields: ['Roll number'], attachmentRequired: false, approvalRequired: true },
  files: [],
};

function signIn(role) {
  sessionStorage.setItem('doctrack.token', validToken({ role }));
  return { 'GET /api/auth/me': { id: 9, username: role.toLowerCase(), name: 'Test User', role } };
}

describe('availableAction', () => {
  it.each([
    ['STUDENT', 'INCOMPLETE', 'resubmit'],
    ['STAFF', 'SUBMITTED', 'verify'],
    ['STAFF', 'APPROVED', 'start'],
    ['STAFF', 'PROCESSING', 'complete'],
    ['APPROVER', 'FORWARDED', 'decide'],
    ['STUDENT', 'SUBMITTED', null],
    ['APPROVER', 'SUBMITTED', null],
    ['STAFF', 'COMPLETED', null],
  ])('%s on %s -> %s', (role, status, expected) => expect(availableAction(role, status)).toBe(expected));
});

describe('ActionPanel', () => {
  it('staff verifies a request and hands the fresh detail to the page', async () => {
    const onUpdated = vi.fn();
    const fetch = mockFetch({
      ...signIn('STAFF'),
      'POST /api/staff/requests/REQ-2026-000001/verify': { ...baseRequest, status: 'FORWARDED' },
    });
    renderApp(<ActionPanel request={baseRequest} onUpdated={onUpdated} onStale={vi.fn()} />);

    expect(await screen.findByText(/forwarded to the approving authority/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }));

    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(expect.objectContaining({ status: 'FORWARDED' })));
    const call = fetch.mock.calls.find(([url]) => url.endsWith('/verify'));
    expect(JSON.parse(call[1].body)).toEqual({ outcome: 'VERIFIED' });
  });

  it('requires missing info before marking incomplete', async () => {
    const fetch = mockFetch(signIn('STAFF'));
    renderApp(<ActionPanel request={baseRequest} onUpdated={vi.fn()} onStale={vi.fn()} />);

    await userEvent.click(await screen.findByLabelText(/information is missing/i));
    await userEvent.click(screen.getByRole('button', { name: /mark incomplete/i }));
    expect(screen.getByRole('alert')).toHaveTextContent(/specify the missing information/i);
    expect(fetch.mock.calls.some(([url]) => url.endsWith('/verify'))).toBe(false);
  });

  it('on a 409 (another staff member acted first) asks the page to reload instead of showing an error', async () => {
    const onStale = vi.fn();
    mockFetch({
      ...signIn('STAFF'),
      'POST /api/staff/requests/REQ-2026-000001/verify': () =>
        jsonResponse({ code: 'E-SYS-409', message: 'This record was changed by someone else.' }, 409),
    });
    renderApp(<ActionPanel request={baseRequest} onUpdated={vi.fn()} onStale={onStale} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Verify' }));
    await waitFor(() => expect(onStale).toHaveBeenCalledWith('This record was changed by someone else.'));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('approver must give a reason to reject', async () => {
    const onUpdated = vi.fn();
    const fetch = mockFetch({
      ...signIn('APPROVER'),
      'POST /api/approvals/REQ-2026-000001/decision': { ...baseRequest, status: 'REJECTED' },
    });
    renderApp(<ActionPanel request={{ ...baseRequest, status: 'FORWARDED' }} onUpdated={onUpdated} onStale={vi.fn()} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Reject' }));
    await userEvent.click(screen.getByRole('button', { name: /confirm rejection/i }));
    expect(screen.getByRole('alert')).toHaveTextContent(/reason is required/i);

    await userEvent.type(screen.getByLabelText(/reason for rejection/i), 'Fee dues pending');
    await userEvent.click(screen.getByRole('button', { name: /confirm rejection/i }));
    await waitFor(() => expect(onUpdated).toHaveBeenCalled());
    const call = fetch.mock.calls.find(([url]) => url.endsWith('/decision'));
    expect(JSON.parse(call[1].body)).toEqual({ decision: 'REJECTED', reason: 'Fee dues pending' });
  });

  it('approve sends decision APPROVED on the first click (regression: state set and read in one event)', async () => {
    const fetch = mockFetch({
      ...signIn('APPROVER'),
      'POST /api/approvals/REQ-2026-000001/decision': { ...baseRequest, status: 'APPROVED' },
    });
    renderApp(<ActionPanel request={{ ...baseRequest, status: 'FORWARDED' }} onUpdated={vi.fn()} onStale={vi.fn()} />);

    await userEvent.type(await screen.findByLabelText(/comments/i), 'All in order');
    await userEvent.click(screen.getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(fetch.mock.calls.some(([url]) => url.endsWith('/decision'))).toBe(true));
    const call = fetch.mock.calls.find(([url]) => url.endsWith('/decision'));
    expect(JSON.parse(call[1].body)).toEqual({ decision: 'APPROVED', comments: 'All in order' });
  });

  it('shows nothing when the user has no action on this status', async () => {
    mockFetch(signIn('STUDENT'));
    const { container } = renderApp(<ActionPanel request={baseRequest} onUpdated={vi.fn()} onStale={vi.fn()} />);
    await waitFor(() => expect(screen.queryByText(/restoring/i)).not.toBeInTheDocument());
    expect(container.querySelector('.action-panel')).toBeNull();
  });
});
