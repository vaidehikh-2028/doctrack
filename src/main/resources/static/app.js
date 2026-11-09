/* DocTrack front end: a small dependency-free single-page app over the REST API. */
(() => {
  'use strict';

  const $ = (sel, root = document) => root.querySelector(sel);
  const app = $('#app');
  const state = { token: null, user: null, tab: null, staffFilter: 'SUBMITTED' };

  // ---------- utilities ----------
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmt = (iso) => iso ? new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '—';
  const label = (s) => s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : '';
  const pill = (s) => `<span class="pill s-${esc(s)}">${esc(label(s))}</span>`;
  const store = {
    get: (k) => { try { return sessionStorage.getItem(k); } catch { return null; } },
    set: (k, v) => { try { sessionStorage.setItem(k, v); } catch { /* storage unavailable */ } },
    del: (k) => { try { sessionStorage.removeItem(k); } catch { /* storage unavailable */ } },
  };

  let toastTimer;
  function toast(msg, bad = false) {
    const t = $('#toast');
    t.textContent = msg;
    t.className = 'toast' + (bad ? ' bad' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add('hidden'), 3800);
  }

  class ApiError extends Error {
    constructor(body, status) {
      super(body?.message || 'Something went wrong.');
      this.code = body?.code; this.fieldErrors = body?.fieldErrors || []; this.status = status;
    }
  }

  async function api(path, { method = 'GET', body, form } = {}) {
    const headers = {};
    if (state.token) headers.Authorization = 'Bearer ' + state.token;
    let payload;
    if (form) payload = form;
    else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
    const res = await fetch(path, { method, headers, body: payload });
    if (res.status === 401 && state.token && path !== '/api/auth/login') {
      const err = await res.json().catch(() => ({}));
      logout(err.code === 'E-AUTH-440' ? err.message : 'Please log in again.');
      throw new ApiError(err, 401);
    }
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new ApiError(data, res.status);
    return data;
  }

  async function download(number, file) {
    try {
      const res = await fetch(`/api/requests/${encodeURIComponent(number)}/files/${file.id}`,
        { headers: { Authorization: 'Bearer ' + state.token } });
      if (!res.ok) throw new ApiError(await res.json().catch(() => ({})), res.status);
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement('a');
      a.href = url; a.download = file.fileName; document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    } catch (e) { toast(e.message, true); }
  }

  function showErrors(form, err) {
    form.querySelectorAll('.field-error').forEach((n) => n.remove());
    form.querySelectorAll('.has-error').forEach((n) => n.classList.remove('has-error'));
    const box = form.querySelector('.form-alert');
    if (box) { box.textContent = err.message; box.classList.remove('hidden'); }
    for (const fe of err.fieldErrors || []) {
      const input = [...form.querySelectorAll('[data-field]')].find((n) => n.dataset.field === fe.field);
      if (!input) continue;
      const wrap = input.closest('.field');
      wrap.classList.add('has-error');
      const msg = document.createElement('div');
      msg.className = 'field-error'; msg.textContent = fe.message;
      wrap.appendChild(msg);
    }
  }

  // ---------- session ----------
  const TABS = {
    STUDENT: [['new', 'New request'], ['mine', 'My requests'], ['notifications', 'Notifications']],
    STAFF: [['queue', 'Request queue'], ['reports', 'Reports'], ['notifications', 'Notifications']],
    APPROVER: [['pending', 'Pending approvals'], ['decisions', 'My decisions'], ['notifications', 'Notifications']],
    ADMIN: [['users', 'Users'], ['types', 'Document types'], ['reports', 'Reports'], ['notifications', 'Notifications']],
  };

  function setSession(token, user) {
    state.token = token; state.user = user;
    store.set('docreq.token', token); store.set('docreq.user', JSON.stringify(user));
    $('#who').textContent = user.name;
    $('#role').textContent = user.role;
    $('#session').classList.remove('hidden');
    const tabs = $('#tabs');
    tabs.innerHTML = TABS[user.role].map(([id, name]) =>
      `<button class="tab" data-tab="${id}">${esc(name)}</button>`).join('');
    tabs.classList.remove('hidden');
    go(TABS[user.role][0][0]);
  }

  function logout(message) {
    state.token = null; state.user = null;
    store.del('docreq.token'); store.del('docreq.user');
    $('#session').classList.add('hidden');
    $('#tabs').classList.add('hidden');
    renderLogin(message);
  }

  function go(tab) {
    state.tab = tab;
    document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === tab));
    const views = { new: viewNew, mine: viewMine, notifications: viewNotifications, queue: viewQueue,
      reports: viewReports, pending: viewPending, decisions: viewDecisions, users: viewUsers, types: viewTypes };
    app.innerHTML = '<div class="empty">Loading…</div>';
    views[tab]().catch((e) => { app.innerHTML = `<div class="alert">${esc(e.message)}</div>`; });
  }

  // ---------- login ----------
  function renderLogin(message) {
    const demo = ['student1', 'staff1', 'approver1', 'admin1'];
    app.innerHTML = `
      <div class="login-wrap">
        <div class="card">
          <h1>Sign in</h1>
          <p class="subtle">Request, track and receive official institute documents.</p>
          ${message ? `<div class="alert warn">${esc(message)}</div>` : ''}
          <form id="login-form" novalidate>
            <div class="alert form-alert hidden"></div>
            <div class="field"><label for="u">Email / User ID</label>
              <input id="u" type="text" autocomplete="username" required></div>
            <div class="field"><label for="p">Password</label>
              <input id="p" type="password" autocomplete="current-password" required></div>
            <button class="btn" type="submit">Log in</button>
          </form>
          <div class="demo-users">Demo accounts (password <span class="mono">Demo@1234</span>):<br>
            ${demo.map((d) => `<button class="btn ghost small" data-demo="${d}">${d}</button>`).join('')}
          </div>
        </div>
      </div>`;
    const form = $('#login-form');
    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const u = $('#u').value.trim(), p = $('#p').value;
      if (!u || !p) return showErrors(form, { message: 'Email / User ID and Password are required.' });
      try {
        const res = await api('/api/auth/login', { method: 'POST', body: { username: u, password: p } });
        setSession(res.token, res.user);
      } catch (e) { showErrors(form, e); }
    });
    app.querySelectorAll('[data-demo]').forEach((b) => b.addEventListener('click', () => {
      $('#u').value = b.dataset.demo; $('#p').value = 'Demo@1234'; $('#p').focus();
    }));
  }

  // ---------- shared pieces ----------
  function requestTable(rows, { showStudent = false, emptyText = 'Nothing here yet.' } = {}) {
    if (!rows.length) return `<div class="empty">${esc(emptyText)}</div>`;
    return `<div class="table-wrap"><table>
      <thead><tr><th>Request</th><th>Document</th>${showStudent ? '<th>Student</th>' : ''}
        <th>Status</th><th>Submitted</th><th>Last update</th></tr></thead>
      <tbody>${rows.map((r) => `
        <tr class="clickable" data-open="${esc(r.requestNumber)}">
          <td class="mono">${esc(r.requestNumber)}</td>
          <td>${esc(r.documentType)}</td>
          ${showStudent ? `<td>${esc(r.studentName)} <span class="subtle">(${esc(r.studentUsername)})</span></td>` : ''}
          <td>${pill(r.status)} ${r.escalated ? '<span class="flag" title="Waiting longer than the SLA">● overdue</span>' : ''}</td>
          <td>${fmt(r.submittedAt)}</td>
          <td>${fmt(r.updatedAt)}</td>
        </tr>`).join('')}</tbody></table></div>`;
  }

  function bindOpen(root = app) {
    root.querySelectorAll('[data-open]').forEach((row) =>
      row.addEventListener('click', () => openDetail(row.dataset.open)));
  }

  function fieldInputs(type, values = {}) {
    return type.requiredFields.map((f, i) => `
      <div class="field"><label for="f${i}">${esc(f)} *</label>
        <input id="f${i}" type="text" data-field="${esc(f)}" data-req-field value="${esc(values[f] || '')}"></div>`).join('');
  }

  function purposeInput(value = '') {
    return `<div class="field"><label for="purpose">Purpose * <span class="counter" id="pc">${value.length} / 250</span></label>
      <textarea id="purpose" data-field="purpose" maxlength="251">${esc(value)}</textarea></div>`;
  }

  function fileInput(required, text) {
    return `<div class="field"><label for="file">${esc(text)}${required ? ' *' : ''}</label>
      <input id="file" type="file" data-field="attachment" accept=".pdf,.jpg,.jpeg,.png">
      <div class="subtle">PDF, JPG or PNG, up to 5 MB.</div></div>`;
  }

  function bindCounter(form) {
    const ta = form.querySelector('#purpose'), pc = form.querySelector('#pc');
    if (!ta) return;
    const upd = () => { pc.textContent = `${ta.value.length} / 250`; pc.classList.toggle('over', ta.value.length > 250); };
    ta.addEventListener('input', upd); upd();
  }

  function collectForm(form) {
    const fields = {};
    form.querySelectorAll('[data-req-field]').forEach((n) => { fields[n.dataset.field] = n.value; });
    const fd = new FormData();
    fd.append('purpose', form.querySelector('#purpose').value);
    fd.append('fields', JSON.stringify(fields));
    const file = form.querySelector('#file')?.files?.[0];
    if (file) fd.append('file', file);
    return fd;
  }

  // ---------- student ----------
  async function viewNew() {
    const types = await api('/api/document-types');
    app.innerHTML = `<h1>Request a document</h1>
      <p class="subtle">Choose the document you need. Requests that need approval go to the approving authority after verification.</p>
      <div class="grid cards">${types.map((t) => `
        <div class="card type-card">
          <div><h3>${esc(t.name)}</h3><div class="subtle">${esc(t.description || '')}</div></div>
          <div class="tags">
            <span class="tag">~${t.processingDays} working days</span>
            ${t.approvalRequired ? '<span class="tag">Needs approval</span>' : ''}
            ${t.attachmentRequired ? '<span class="tag">Supporting file</span>' : ''}
          </div>
          <button class="btn" data-type="${t.id}">Request</button>
        </div>`).join('') || '<div class="empty">No document types are offered right now.</div>'}</div>
      <div id="form-slot"></div>`;
    app.querySelectorAll('[data-type]').forEach((b) => b.addEventListener('click', () =>
      showRequestForm(types.find((t) => String(t.id) === b.dataset.type))));
  }

  function showRequestForm(type) {
    const slot = $('#form-slot');
    slot.innerHTML = `<div class="card"><h2>${esc(type.name)}</h2>
      <form id="req-form" novalidate>
        <div class="alert form-alert hidden"></div>
        ${fieldInputs(type)}${purposeInput()}
        ${type.attachmentRequired ? fileInput(true, 'Supporting document') : ''}
        <div class="row"><button class="btn" type="submit">Submit request</button>
          <button class="btn ghost" type="button" id="cancel">Cancel</button></div>
      </form></div>`;
    const form = $('#req-form');
    bindCounter(form);
    $('#cancel').addEventListener('click', () => { slot.innerHTML = ''; });
    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const fd = collectForm(form);
      fd.append('documentTypeId', type.id);
      const btn = form.querySelector('[type=submit]'); btn.disabled = true;
      try {
        const r = await api('/api/requests', { method: 'POST', form: fd });
        slot.innerHTML = `<div class="card"><div class="alert ok">Request submitted.</div>
          <dl class="kv"><dt>Request ID</dt><dd class="mono">${esc(r.requestNumber)}</dd>
          <dt>Status</dt><dd>${pill(r.status)}</dd><dt>Submitted</dt><dd>${fmt(r.submittedAt)}</dd></dl>
          <div class="row" style="margin-top:14px"><button class="btn" data-open="${esc(r.requestNumber)}">View request</button></div></div>`;
        bindOpen(slot);
        slot.scrollIntoView({ behavior: 'smooth' });
      } catch (e) { showErrors(form, e); btn.disabled = false; }
    });
    slot.scrollIntoView({ behavior: 'smooth' });
  }

  async function viewMine() {
    const rows = await api('/api/requests/mine');
    app.innerHTML = `<h1>My requests</h1><div class="card">${requestTable(rows,
      { emptyText: 'You have not submitted any requests yet.' })}</div>`;
    bindOpen();
  }

  async function viewNotifications() {
    const rows = await api('/api/notifications/mine');
    app.innerHTML = `<h1>Notifications</h1><div class="card">${rows.length ? `<div class="table-wrap"><table>
      <thead><tr><th>When</th><th>Type</th><th>Message</th><th>Request</th></tr></thead>
      <tbody>${rows.map((n) => `<tr><td>${fmt(n.createdAt)}</td><td>${esc(label(n.type))}</td>
        <td>${esc(n.message)}</td>
        <td>${n.requestNumber ? `<button class="link mono" data-open="${esc(n.requestNumber)}">${esc(n.requestNumber)}</button>` : ''}</td></tr>`).join('')}
      </tbody></table></div>` : '<div class="empty">No notifications.</div>'}</div>`;
    bindOpen();
  }

  // ---------- staff ----------
  async function viewQueue() {
    const filters = [['SUBMITTED', 'To verify'], ['APPROVED', 'Approved'], ['PROCESSING', 'Processing'],
      ['FORWARDED', 'With approver'], ['INCOMPLETE', 'Incomplete'], ['COMPLETED', 'Completed'], ['REJECTED', 'Rejected']];
    const rows = await api('/api/staff/requests?status=' + state.staffFilter);
    app.innerHTML = `<h1>Request queue</h1>
      <div class="chips">${filters.map(([s, n]) =>
        `<button class="chip ${s === state.staffFilter ? 'active' : ''}" data-filter="${s}">${esc(n)}</button>`).join('')}</div>
      <div class="card">${requestTable(rows, { showStudent: true, emptyText: 'No requests in this state.' })}</div>`;
    app.querySelectorAll('[data-filter]').forEach((b) => b.addEventListener('click', () => {
      state.staffFilter = b.dataset.filter; go('queue');
    }));
    bindOpen();
  }

  async function viewReports() {
    const r = await api('/api/reports/summary');
    const order = ['SUBMITTED', 'INCOMPLETE', 'FORWARDED', 'APPROVED', 'PROCESSING', 'COMPLETED', 'REJECTED'];
    app.innerHTML = `<h1>Reports</h1>
      <div class="card"><div class="stats">
        <div class="stat"><div class="num">${r.total}</div><div class="lbl">Total requests</div></div>
        <div class="stat"><div class="num">${r.averageCompletionHours == null ? '—' : r.averageCompletionHours.toFixed(1) + ' h'}</div>
          <div class="lbl">Avg. time to complete</div></div>
        <div class="stat"><div class="num">${r.escalatedOpen}</div><div class="lbl">Overdue (open)</div></div>
      </div></div>
      <div class="card"><h2>By status</h2><div class="stats">${order.map((s) =>
        `<div class="stat"><div class="num">${r.byStatus[s] ?? 0}</div><div class="lbl">${esc(label(s))}</div></div>`).join('')}</div></div>`;
  }

  // ---------- approver ----------
  async function viewPending() {
    const rows = await api('/api/approvals/pending');
    app.innerHTML = `<h1>Pending approvals</h1><div class="card">${requestTable(rows,
      { showStudent: true, emptyText: 'No requests are waiting for approval.' })}</div>`;
    bindOpen();
  }

  async function viewDecisions() {
    const rows = await api('/api/approvals/history');
    app.innerHTML = `<h1>My decisions</h1><div class="card">${rows.length ? `<div class="table-wrap"><table>
      <thead><tr><th>Request</th><th>Decision</th><th>Comments / reason</th><th>Decided</th></tr></thead>
      <tbody>${rows.map((a) => `<tr class="clickable" data-open="${esc(a.requestNumber)}">
        <td class="mono">${esc(a.requestNumber)}</td><td>${pill(a.decision)}</td>
        <td>${esc(a.comments || '')}</td><td>${fmt(a.decidedAt)}</td></tr>`).join('')}</tbody></table></div>`
      : '<div class="empty">You have not decided any requests yet.</div>'}</div>`;
    bindOpen();
  }

  // ---------- admin ----------
  async function viewUsers() {
    const users = await api('/api/admin/users');
    const roles = ['STUDENT', 'STAFF', 'APPROVER', 'ADMIN'];
    app.innerHTML = `<h1>Users</h1>
      <div class="card"><div class="table-wrap"><table>
        <thead><tr><th>User ID</th><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th></th></tr></thead>
        <tbody>${users.map((u) => `<tr>
          <td class="mono">${esc(u.username)}</td><td>${esc(u.name)}</td><td>${esc(u.email)}</td>
          <td><select data-role="${u.id}">${roles.map((r) => `<option ${r === u.role ? 'selected' : ''}>${r}</option>`).join('')}</select></td>
          <td>${u.accountStatus === 'ACTIVE' ? '<span class="pill s-APPROVED">Active</span>' : '<span class="pill s-REJECTED">Inactive</span>'}</td>
          <td><button class="btn ghost small" data-toggle="${u.id}" data-status="${u.accountStatus}">
            ${u.accountStatus === 'ACTIVE' ? 'Deactivate' : 'Activate'}</button></td></tr>`).join('')}</tbody></table></div></div>
      <div class="card"><h2>Add user</h2>
        <form id="user-form" novalidate><div class="alert form-alert hidden"></div>
          <div class="grid two">
            <div class="field"><label>User ID *</label><input type="text" name="username" data-field="username"></div>
            <div class="field"><label>Name *</label><input type="text" name="name" data-field="name"></div>
            <div class="field"><label>Email *</label><input type="email" name="email" data-field="email"></div>
            <div class="field"><label>Contact</label><input type="text" name="contactInfo" data-field="contactInfo"></div>
            <div class="field"><label>Role *</label><select name="role">${roles.map((r) => `<option>${r}</option>`).join('')}</select></div>
            <div class="field"><label>Initial password * (8+ characters)</label><input type="password" name="password" data-field="password"></div>
          </div>
          <button class="btn" type="submit">Create user</button></form></div>`;
    const patchUser = async (id, body) => {
      try { await api('/api/admin/users/' + id, { method: 'PATCH', body }); toast('User updated.'); go('users'); }
      catch (e) { toast(e.message, true); go('users'); }
    };
    app.querySelectorAll('[data-toggle]').forEach((b) => b.addEventListener('click', () =>
      patchUser(b.dataset.toggle, { accountStatus: b.dataset.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' })));
    app.querySelectorAll('[data-role]').forEach((s) => s.addEventListener('change', () =>
      patchUser(s.dataset.role, { role: s.value })));
    const form = $('#user-form');
    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const body = Object.fromEntries(new FormData(form).entries());
      try { await api('/api/admin/users', { method: 'POST', body }); toast('User created.'); go('users'); }
      catch (e) { showErrors(form, e); }
    });
  }

  async function viewTypes() {
    const types = await api('/api/admin/document-types');
    app.innerHTML = `<h1>Document types</h1>
      <div class="card"><div class="table-wrap"><table>
        <thead><tr><th>Code</th><th>Name</th><th>Required fields</th><th>File</th><th>Approval</th><th>Days</th><th>Offered</th><th></th></tr></thead>
        <tbody>${types.map((t) => `<tr>
          <td class="mono">${esc(t.code)}</td><td>${esc(t.name)}</td><td>${esc(t.requiredFields.join(', '))}</td>
          <td>${t.attachmentRequired ? 'Yes' : 'No'}</td><td>${t.approvalRequired ? 'Yes' : 'No'}</td>
          <td>${t.processingDays}</td><td>${t.active ? 'Yes' : '<span class="flag">No</span>'}</td>
          <td><button class="btn ghost small" data-edit="${t.id}">Edit</button></td></tr>`).join('')}</tbody></table></div></div>
      <div class="card" id="type-card"></div>`;
    const renderForm = (t) => {
      const card = $('#type-card');
      card.innerHTML = `<h2>${t ? 'Edit ' + esc(t.name) : 'Add document type'}</h2>
        <form id="type-form" novalidate><div class="alert form-alert hidden"></div>
          <div class="grid two">
            <div class="field"><label>Code *</label><input type="text" name="code" data-field="code" value="${esc(t?.code)}" ${t ? 'readonly' : ''}></div>
            <div class="field"><label>Name *</label><input type="text" name="name" data-field="name" value="${esc(t?.name)}"></div>
            <div class="field"><label>Required fields (comma separated)</label><input type="text" name="requiredFields" data-field="requiredFields" value="${esc(t?.requiredFields.join(', '))}"></div>
            <div class="field"><label>Typical processing days *</label><input type="number" min="1" name="processingDays" data-field="processingDays" value="${t?.processingDays ?? 3}"></div>
          </div>
          <div class="field"><label>Description</label><textarea name="description">${esc(t?.description)}</textarea></div>
          <div class="row" style="margin-bottom:14px">
            <label class="checkbox"><input type="checkbox" name="attachmentRequired" ${t?.attachmentRequired ? 'checked' : ''}> Supporting file required</label>
            <label class="checkbox"><input type="checkbox" name="approvalRequired" ${t?.approvalRequired ? 'checked' : ''}> Approval required</label>
            <label class="checkbox"><input type="checkbox" name="active" ${!t || t.active ? 'checked' : ''}> Offered to students</label>
          </div>
          <div class="row"><button class="btn" type="submit">${t ? 'Save changes' : 'Create'}</button>
            ${t ? '<button class="btn ghost" type="button" id="new-type">Add new instead</button>' : ''}</div></form>`;
      const form = $('#type-form');
      $('#new-type')?.addEventListener('click', () => renderForm(null));
      form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const fd = new FormData(form);
        const body = {
          code: fd.get('code'), name: fd.get('name'), description: fd.get('description'),
          requiredFields: String(fd.get('requiredFields') || '').split(',').map((s) => s.trim()).filter(Boolean),
          processingDays: Number(fd.get('processingDays')),
          attachmentRequired: fd.has('attachmentRequired'), approvalRequired: fd.has('approvalRequired'),
          active: fd.has('active'),
        };
        try {
          await api(t ? '/api/admin/document-types/' + t.id : '/api/admin/document-types',
            { method: t ? 'PUT' : 'POST', body });
          toast('Saved.'); go('types');
        } catch (e) { showErrors(form, e); }
      });
    };
    renderForm(null);
    app.querySelectorAll('[data-edit]').forEach((b) => b.addEventListener('click', () => {
      renderForm(types.find((t) => String(t.id) === b.dataset.edit));
      $('#type-card').scrollIntoView({ behavior: 'smooth' });
    }));
  }

  // ---------- request detail + actions ----------
  async function openDetail(number) {
    let d;
    try { d = await api('/api/requests/' + encodeURIComponent(number)); }
    catch (e) { return toast(e.message, true); }
    const role = state.user.role;
    const fieldRows = Object.entries(d.fieldValues).map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(v)}</dd>`).join('');
    const files = d.files.map((f) => {
      const canDownload = f.kind === 'ATTACHMENT' || role === 'STUDENT';
      const what = f.kind === 'ATTACHMENT' ? 'Supporting file' : 'Issued document';
      return `<li>${what}: ${canDownload
        ? `<button class="link" data-file="${f.id}">${esc(f.fileName)}</button>` : esc(f.fileName)}
        <span class="subtle">(${(f.sizeBytes / 1024).toFixed(0)} KB)</span></li>`;
    }).join('');
    const decision = d.approvals.length ? d.approvals[d.approvals.length - 1] : null;

    app.innerHTML = `
      <div class="row spread" style="margin-bottom:12px">
        <button class="btn ghost small" id="back">← Back</button>
      </div>
      <div class="grid two">
        <div class="card">
          <div class="row spread"><h1 class="mono" style="font-size:20px">${esc(d.requestNumber)}</h1>${pill(d.status)}</div>
          <dl class="kv">
            <dt>Document</dt><dd>${esc(d.documentType.name)}</dd>
            ${role !== 'STUDENT' ? `<dt>Student</dt><dd>${esc(d.student.name)} · ${esc(d.student.email)}</dd>` : ''}
            ${fieldRows}
            <dt>Purpose</dt><dd>${esc(d.purpose)}</dd>
            <dt>Submitted</dt><dd>${fmt(d.submittedAt)}</dd>
            ${d.completedAt ? `<dt>Completed</dt><dd>${fmt(d.completedAt)}</dd>` : ''}
            ${decision && decision.decision !== 'PENDING' ? `<dt>Decision</dt><dd>${pill(decision.decision)} by ${esc(decision.decidedBy)}${decision.comments ? ' — ' + esc(decision.comments) : ''}</dd>` : ''}
          </dl>
          ${d.missingInfo ? `<div class="alert warn" style="margin-top:14px"><strong>Missing information:</strong> ${esc(d.missingInfo)}</div>` : ''}
          ${files ? `<h3 style="margin-top:16px">Files</h3><ul>${files}</ul>` : ''}
          <div id="actions"></div>
        </div>
        <div class="card"><h2>History</h2><ol class="timeline">${d.history.map((h) => `
          <li><div>${pill(h.status)} <strong>${esc(h.changedBy)}</strong> <span class="subtle">(${esc(label(h.changedByRole))})</span></div>
            <div class="when">${fmt(h.changedAt)}</div>${h.remarks ? `<div>${esc(h.remarks)}</div>` : ''}</li>`).join('')}</ol></div>
      </div>`;
    $('#back').addEventListener('click', () => go(state.tab));
    app.querySelectorAll('[data-file]').forEach((b) => b.addEventListener('click', () =>
      download(d.requestNumber, d.files.find((f) => String(f.id) === b.dataset.file))));
    renderActions(d);
  }

  function renderActions(d) {
    const slot = $('#actions');
    const role = state.user.role, s = d.status, n = encodeURIComponent(d.requestNumber);
    const done = (msg) => { toast(msg); openDetail(d.requestNumber); };

    if (role === 'STAFF' && s === 'SUBMITTED') {
      slot.innerHTML = `<div class="actions"><h3>Verify request</h3>
        <form id="act" novalidate><div class="alert form-alert hidden"></div>
          <div class="field"><label>Remarks (shown in history)</label><input type="text" id="remarks" data-field="remarks"></div>
          <div class="field"><label>Missing information (only when marking Incomplete)</label>
            <textarea id="missing" data-field="missingInfo"></textarea></div>
          <div class="row">
            <button class="btn ok" type="button" id="ok">Mark verified${d.documentType.approvalRequired ? ' & forward' : ''}</button>
            <button class="btn bad" type="button" id="inc">Mark incomplete</button></div></form></div>`;
      const form = $('#act');
      const send = async (outcome) => {
        try {
          await api(`/api/staff/requests/${n}/verify`, { method: 'POST',
            body: { outcome, remarks: $('#remarks').value, missingInfo: $('#missing').value } });
          done(outcome === 'VERIFIED' ? 'Request verified.' : 'Student asked for more information.');
        } catch (e) { showErrors(form, e); }
      };
      $('#ok').addEventListener('click', () => send('VERIFIED'));
      $('#inc').addEventListener('click', () => send('INCOMPLETE'));
    } else if (role === 'STAFF' && s === 'APPROVED') {
      slot.innerHTML = `<div class="actions"><h3>Approved — start preparation</h3>
        <button class="btn" id="start">Start processing</button></div>`;
      $('#start').addEventListener('click', async () => {
        try { await api(`/api/staff/requests/${n}/start-processing`, { method: 'POST', body: {} }); done('Processing started.'); }
        catch (e) { toast(e.message, true); }
      });
    } else if (role === 'STAFF' && s === 'PROCESSING') {
      slot.innerHTML = `<div class="actions"><h3>Issue document</h3>
        <form id="act" novalidate><div class="alert form-alert hidden"></div>
          ${fileInput(true, 'Completed document')}
          <div class="field"><label>Remarks</label><input type="text" id="remarks"></div>
          <button class="btn ok" type="submit">Mark ready & notify student</button></form></div>`;
      const form = $('#act');
      form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const fd = new FormData();
        const file = $('#file').files[0];
        if (file) fd.append('file', file);
        fd.append('remarks', $('#remarks').value);
        try { await api(`/api/staff/requests/${n}/complete`, { method: 'POST', form: fd }); done('Document issued.'); }
        catch (e) { showErrors(form, e); }
      });
    } else if (role === 'APPROVER' && s === 'FORWARDED') {
      slot.innerHTML = `<div class="actions"><h3>Decision</h3>
        <form id="act" novalidate><div class="alert form-alert hidden"></div>
          <div class="field"><label>Comments (approval) or reason (required to reject)</label>
            <textarea id="note"></textarea></div>
          <div class="row"><button class="btn ok" type="button" id="ap">Approve</button>
            <button class="btn bad" type="button" id="rj">Reject</button></div></form></div>`;
      const form = $('#act');
      const send = async (decision) => {
        const note = $('#note').value;
        try {
          await api(`/api/approvals/${n}/decision`, { method: 'POST',
            body: decision === 'APPROVED' ? { decision, comments: note } : { decision, reason: note } });
          done(decision === 'APPROVED' ? 'Request approved.' : 'Request rejected.');
        } catch (e) { showErrors(form, e); }
      };
      $('#ap').addEventListener('click', () => send('APPROVED'));
      $('#rj').addEventListener('click', () => send('REJECTED'));
    } else if (role === 'STUDENT' && s === 'INCOMPLETE') {
      slot.innerHTML = `<div class="actions"><h3>Update and resubmit</h3>
        <form id="act" novalidate><div class="alert form-alert hidden"></div>
          ${fieldInputs(d.documentType, d.fieldValues)}${purposeInput(d.purpose)}
          ${d.documentType.attachmentRequired ? fileInput(false, 'Replace supporting file (optional)') : ''}
          <button class="btn" type="submit">Resubmit</button></form></div>`;
      const form = $('#act');
      bindCounter(form);
      form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        try { await api(`/api/requests/${n}/resubmit`, { method: 'POST', form: collectForm(form) }); done('Request resubmitted.'); }
        catch (e) { showErrors(form, e); }
      });
    } else if (role === 'STUDENT' && s === 'COMPLETED') {
      slot.innerHTML = `<div class="alert ok" style="margin-top:14px">Your document is ready. Download it from the Files list above.</div>`;
    }
  }

  // ---------- boot ----------
  $('#tabs').addEventListener('click', (ev) => {
    const t = ev.target.closest('.tab');
    if (t) go(t.dataset.tab);
  });
  $('#logout').addEventListener('click', () => logout());

  const saved = store.get('docreq.token');
  const savedUser = store.get('docreq.user');
  if (saved && savedUser) {
    state.token = saved;
    api('/api/auth/me')
      .then(() => setSession(saved, JSON.parse(savedUser)))
      .catch(() => logout());
  } else {
    renderLogin();
  }
})();
