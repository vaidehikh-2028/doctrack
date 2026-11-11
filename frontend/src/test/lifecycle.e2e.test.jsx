import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import App from '../App';
import { AuthProvider } from '../auth/AuthContext';

/**
 * Drives the whole lifecycle through the UI against the real backend, one role at a time:
 * student submits -> staff verifies -> approver approves -> staff processes and uploads ->
 * student downloads. Plus the rejection and incomplete/resubmit paths.
 * Uses the demo users seeded by DataSeeder (password Demo@1234).
 */

const PASSWORD = 'Demo@1234';
const PDF = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a, 0x25, 0x25, 0x45, 0x4f, 0x46]);

function renderAt(path = '/login') {
  sessionStorage.clear();
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function loginAs(username) {
  const view = renderAt('/login');
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/user id or email/i), username);
  await user.type(screen.getByLabelText(/password/i), PASSWORD);
  await user.click(screen.getByRole('button', { name: /log in/i }));
  await screen.findByRole('button', { name: /log out/i });
  return { user, view };
}

async function logout(view) {
  await userEvent.click(screen.getByRole('button', { name: /log out/i }));
  view.unmount();
}

/** Click a top-level section in the role's nav bar. */
async function goTo(user, label) {
  await user.click(within(screen.getByRole('navigation', { name: 'Sections' })).getByRole('link', { name: label }));
}

async function openRequest(user, requestNumber) {
  await user.click(await screen.findByRole('link', { name: requestNumber }));
  await screen.findByRole('heading', { name: requestNumber });
}

async function studentSubmits(typeName, { attach = false } = {}) {
  const { user, view } = await loginAs('student1');
  await goTo(user, 'New request');
  await user.click(await screen.findByRole('button', { name: new RegExp(typeName, 'i') }));

  // Fill every required field the type declares, whatever the admin configured.
  for (const input of screen.queryAllByRole('textbox')) {
    if (input.tagName === 'INPUT' && !input.value) await user.type(input, 'E2E value');
  }
  await user.type(screen.getByLabelText(/purpose/i), 'Needed for an internship application');
  const fileInput = document.querySelector('input[type=file]');
  if (attach || fileInput.closest('label').querySelector('abbr')) {
    await user.upload(fileInput, new File([PDF], 'marksheet.pdf', { type: 'application/pdf' }));
  }
  await user.click(screen.getByRole('button', { name: /submit request/i }));

  const heading = await screen.findByRole('heading', { name: /^REQ-\d{4}-\d{6}$/ }, { timeout: 15_000 });
  expect(screen.getByText(/submitted\./i)).toBeInTheDocument();
  const requestNumber = heading.textContent;
  await logout(view);
  return requestNumber;
}

async function typesNeedingApproval() {
  // Read the seeded types through the API the UI uses, to pick one with and one without approval.
  const base = import.meta.env.VITE_API_BASE_URL;
  const login = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'student1', password: PASSWORD }),
  }).then((r) => r.json());
  const types = await fetch(`${base}/api/document-types`, { headers: { Authorization: `Bearer ${login.token}` } }).then((r) => r.json());
  return { withApproval: types.find((t) => t.approvalRequired), withoutApproval: types.find((t) => !t.approvalRequired) };
}

describe('DocTrack UI against the real API', () => {
  it('rejects bad credentials with one generic message', async () => {
    renderAt('/login');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/user id or email/i), 'student1');
    await user.type(screen.getByLabelText(/password/i), 'wrong-password');
    await user.click(screen.getByRole('button', { name: /log in/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid credentials/i);
  });

  it('runs the full approval lifecycle across four roles', async () => {
    const { withApproval } = await typesNeedingApproval();
    expect(withApproval).toBeDefined();
    const no = await studentSubmits(withApproval.name, { attach: true });

    // staff verifies -> FORWARDED
    let s = await loginAs('staff1');
    await openRequest(s.user, no);
    await s.user.click(await screen.findByRole('button', { name: 'Verify' }));
    expect(await screen.findByText(/request is now forwarded/i)).toBeInTheDocument();
    await logout(s.view);

    // approver approves -> APPROVED
    s = await loginAs('approver1');
    await openRequest(s.user, no);
    await s.user.type(await screen.findByLabelText(/comments/i), 'All in order');
    await s.user.click(screen.getByRole('button', { name: 'Approve' }));
    expect(await screen.findByText(/request is now approved/i)).toBeInTheDocument();
    await logout(s.view);

    // staff starts processing, then uploads the issued document -> COMPLETED
    s = await loginAs('staff1');
    await s.user.click(screen.getByRole('tab', { name: 'Approved' }));
    await openRequest(s.user, no);
    await s.user.click(await screen.findByRole('button', { name: /start processing/i }));
    expect(await screen.findByText(/request is now processing/i)).toBeInTheDocument();
    await s.user.upload(screen.getByLabelText(/completed document/i), new File([PDF], 'bonafide-issued.pdf', { type: 'application/pdf' }));
    await s.user.click(screen.getByRole('button', { name: /upload and complete/i }));
    expect(await screen.findByText(/request is now completed/i, {}, { timeout: 15_000 })).toBeInTheDocument();
    await logout(s.view);

    // student sees it ready, with the full history and a notification
    s = await loginAs('student1');
    await openRequest(s.user, no);
    expect(await screen.findByRole('button', { name: new RegExp(`download ${withApproval.name}`, 'i') })).toBeInTheDocument();
    const history = screen.getByRole('heading', { name: 'History' }).closest('aside');
    for (const label of ['Submitted', 'Forwarded', 'Approved', 'Processing', 'Completed']) {
      expect(within(history).getAllByText(label).length).toBeGreaterThan(0);
    }
    await goTo(s.user, 'Notifications');
    expect(await screen.findAllByText(new RegExp(`${no}.*ready to download`, 'i'))).not.toHaveLength(0);
    await logout(s.view);
  });

  it('marks a request incomplete, then the student resubmits it', async () => {
    const { withoutApproval } = await typesNeedingApproval();
    const no = await studentSubmits(withoutApproval.name);

    let s = await loginAs('staff2');
    await openRequest(s.user, no);
    await s.user.click(await screen.findByLabelText(/information is missing/i));
    await s.user.type(screen.getByLabelText(/what is missing/i), 'Attach your fee receipt');
    await s.user.click(screen.getByRole('button', { name: /mark incomplete/i }));
    expect(await screen.findByText(/request is now incomplete/i)).toBeInTheDocument();
    await logout(s.view);

    s = await loginAs('student1');
    expect(await screen.findByText(/needs? more information from you/i)).toBeInTheDocument();
    await openRequest(s.user, no);
    // The label appears in the warning banner and, from the backend, in the history remark.
    const banner = screen.getAllByText(/missing information:/i).map((el) => el.closest('.alert')).find(Boolean);
    expect(banner).toHaveTextContent(/attach your fee receipt/i);
    const purpose = screen.getByLabelText(/purpose/i);
    await s.user.clear(purpose);
    await s.user.type(purpose, 'Internship application, fee receipt attached');
    await s.user.upload(document.querySelector('.action-panel input[type=file]'), new File([PDF], 'receipt.pdf', { type: 'application/pdf' }));
    await s.user.click(screen.getByRole('button', { name: /^resubmit$/i }));
    expect(await screen.findByText(/request is now submitted/i, {}, { timeout: 15_000 })).toBeInTheDocument();
    await logout(s.view);
  });

  it('requires a reason to reject, then shows the rejection to the student', async () => {
    const { withApproval } = await typesNeedingApproval();
    const no = await studentSubmits(withApproval.name, { attach: true });

    let s = await loginAs('staff1');
    await openRequest(s.user, no);
    await s.user.click(await screen.findByRole('button', { name: 'Verify' }));
    await screen.findByText(/request is now forwarded/i);
    await logout(s.view);

    s = await loginAs('approver2');
    await openRequest(s.user, no);
    await s.user.click(await screen.findByRole('button', { name: 'Reject' }));
    await s.user.type(screen.getByLabelText(/reason for rejection/i), 'Outstanding library dues');
    await s.user.click(screen.getByRole('button', { name: /confirm rejection/i }));
    expect(await screen.findByText(/request is now rejected/i)).toBeInTheDocument();
    await logout(s.view);

    s = await loginAs('student1');
    await openRequest(s.user, no);
    expect(screen.getAllByText(/outstanding library dues/i).length).toBeGreaterThan(0);
    await logout(s.view);
  });

  it('blocks a renamed executable before it is uploaded', async () => {
    const { withApproval } = await typesNeedingApproval();
    const { user, view } = await loginAs('student1');
    await goTo(user, 'New request');
    await user.click(await screen.findByRole('button', { name: new RegExp(withApproval.name, 'i') }));
    const exe = new File([new Uint8Array([0x4d, 0x5a, 0x90, 0x00, 0x03])], 'setup.pdf', { type: 'application/pdf' });
    await user.upload(document.querySelector('input[type=file]'), exe);
    expect(await screen.findByText(/not a real pdf/i)).toBeInTheDocument();
    await logout(view);
  });

  it('shows admin users and staff reports', async () => {
    let s = await loginAs('admin1');
    expect(await screen.findByRole('heading', { name: 'Users' })).toBeInTheDocument();
    expect(await screen.findByRole('combobox', { name: 'Role for student1' })).toHaveValue('STUDENT');
    await goTo(s.user, 'Document types');
    expect(await screen.findByRole('heading', { name: 'Document types' })).toBeInTheDocument();
    await logout(s.view);

    s = await loginAs('staff1');
    await goTo(s.user, 'Reports');
    expect(await screen.findByText(/total requests/i)).toBeInTheDocument();
    await waitFor(() => expect(Number(screen.getByText(/total requests/i).nextSibling.textContent)).toBeGreaterThan(0));
    await logout(s.view);
  });
});
