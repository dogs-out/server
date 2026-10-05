/* Dogs Out admin page. Plain JS, no build step; talks to /admin/api with the
 * token from the normal /auth/login. Every piece of user-supplied text goes
 * through esc() before it reaches the DOM. */
(() => {
  'use strict';

  const TOKEN_KEY = 'dogsout-admin-token';
  const $ = sel => document.querySelector(sel);
  const state = { tab: 'overview', reportFilter: 'OPEN', timers: [] };

  // ── Helpers ───────────────────────────────────────────────────────────
  function esc(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  const fmt = n => (n ?? 0).toLocaleString('de-CH');
  function ago(iso) {
    if (!iso) return '—';
    const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
    if (s < 60) return 'just now';
    if (s < 3600) return `${Math.round(s / 60)} min ago`;
    if (s < 86400) return `${Math.round(s / 3600)} h ago`;
    return `${Math.round(s / 86400)} d ago`;
  }
  const when = iso => iso ? new Date(iso).toLocaleString('de-CH', { dateStyle: 'medium', timeStyle: 'short' }) : '—';
  const platformPill = p => p === 'IOS' ? '<span class="pill ios">iOS</span>'
    : p === 'ANDROID' ? '<span class="pill android">Android</span>' : '<span class="pill">unknown</span>';

  function token() { try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; } }
  function setToken(t) { try { t ? sessionStorage.setItem(TOKEN_KEY, t) : sessionStorage.removeItem(TOKEN_KEY); } catch { /* private mode */ } }

  async function api(path, opts = {}) {
    const res = await fetch(path, {
      ...opts,
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token()}`, ...(opts.headers || {}) },
    });
    const renewed = res.headers.get('X-Refreshed-Token');
    if (renewed) setToken(renewed);
    if (res.status === 401 || res.status === 403) { signOut('Your session has ended or this account is not an admin.'); throw new Error('auth'); }
    if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
    return res.status === 204 ? null : res.json();
  }

  // ── Login ─────────────────────────────────────────────────────────────
  function showLogin(msg) {
    $('#app').classList.add('hidden');
    $('#login').classList.remove('hidden');
    $('#login-error').textContent = msg || '';
  }

  function signOut(msg) {
    setToken(null);
    state.timers.forEach(clearInterval);
    state.timers = [];
    showLogin(msg);
  }

  $('#login-form').addEventListener('submit', async e => {
    e.preventDefault();
    $('#login-error').textContent = '';
    try {
      const res = await fetch('/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: $('#email').value.trim(), password: $('#password').value }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) { $('#login-error').textContent = body.message || 'Sign-in failed.'; return; }
      setToken(body.token);
      $('#password').value = '';
      await start();
    } catch {
      $('#login-error').textContent = 'Could not reach the server.';
    }
  });

  $('#logout').addEventListener('click', () => signOut());

  async function start() {
    let me;
    try {
      const res = await fetch('/users/me', { headers: { Authorization: `Bearer ${token()}` } });
      if (!res.ok) throw new Error();
      me = await res.json();
    } catch { return showLogin(); }
    if (me.role !== 'ADMIN') { setToken(null); return showLogin('This account is not an admin.'); }
    $('#who').textContent = me.name;
    $('#login').classList.add('hidden');
    $('#app').classList.remove('hidden');
    showTab(state.tab);
    refreshReportCount();
    state.timers.push(setInterval(refreshReportCount, 60000));
  }

  // ── Tabs ──────────────────────────────────────────────────────────────
  $('#tabs').addEventListener('click', e => {
    const b = e.target.closest('button[data-tab]');
    if (b) showTab(b.dataset.tab);
  });

  let liveTimer = null;
  function showTab(tab) {
    state.tab = tab;
    document.querySelectorAll('#tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
    document.querySelectorAll('main > section').forEach(s => s.classList.toggle('hidden', s.id !== `tab-${tab}`));
    clearInterval(liveTimer);
    const render = { overview: renderOverview, reports: renderReports, users: renderUsers, online: renderOnline, server: renderServer }[tab];
    render();
    // Live tabs refresh themselves; the others reload when reopened.
    if (tab === 'online') liveTimer = setInterval(renderOnline, 20000);
    if (tab === 'overview') liveTimer = setInterval(renderOverview, 60000);
  }

  async function refreshReportCount() {
    try {
      const open = await api('/admin/api/reports?status=OPEN');
      const el = $('#reports-count');
      el.textContent = open.length;
      el.classList.toggle('hidden', open.length === 0);
    } catch { /* shown on the tab itself */ }
  }

  // ── Charts ────────────────────────────────────────────────────────────
  /** series: [{ values, color, label }]; stacked bars if stack, else lines. */
  function chart(labels, series, { stack = false, height = 170, fmtY = v => fmt(v) } = {}) {
    const W = 600, H = height, L = 34, B = 18, T = 6;
    const n = labels.length;
    const totals = labels.map((_, i) => stack ? series.reduce((a, s) => a + (s.values[i] || 0), 0)
      : Math.max(...series.map(s => s.values[i] || 0)));
    const max = Math.max(1, ...totals);
    const nice = niceMax(max);
    const x = i => L + (W - L) * (n === 1 ? 0.5 : i / (n - 1));
    const bw = Math.max(2, (W - L) / n - 2);
    const y = v => T + (H - T - B) * (1 - v / nice);
    let g = '';
    for (let k = 0; k <= 4; k++) {
      const v = nice * k / 4, yy = y(v);
      g += `<line class="grid" x1="${L}" x2="${W}" y1="${yy}" y2="${yy}"/><text x="${L - 6}" y="${yy + 3}" text-anchor="end">${esc(fmtY(v))}</text>`;
    }
    const step = Math.ceil(n / 8);
    labels.forEach((d, i) => {
      // Every step-th day, plus the last one when it is not crowding the one before.
      const isLast = i === n - 1;
      if (i % step === 0 || (isLast && (n - 1) % step >= step / 2)) g += `<text x="${stack ? L + (W - L) * (i + 0.5) / n : x(i)}" y="${H - 4}" text-anchor="middle">${esc(shortDay(d))}</text>`;
    });
    if (stack) {
      labels.forEach((_, i) => {
        let acc = 0;
        series.forEach(s => {
          const v = s.values[i] || 0;
          if (!v) return;
          const y0 = y(acc), y1 = y(acc + v);
          g += `<rect x="${L + (W - L) * i / n + 1}" y="${y1}" width="${bw}" height="${Math.max(0, y0 - y1)}" rx="2" fill="${s.color}"><title>${esc(labels[i])} · ${esc(s.label)}: ${v}</title></rect>`;
          acc += v;
        });
      });
    } else {
      series.forEach(s => {
        const pts = s.values.map((v, i) => `${x(i)},${y(v || 0)}`).join(' ');
        g += `<polyline points="${pts}" fill="none" stroke="${s.color}" stroke-width="2" stroke-linejoin="round"/>`;
        s.values.forEach((v, i) => { g += `<circle cx="${x(i)}" cy="${y(v || 0)}" r="6" fill="transparent"><title>${esc(labels[i])} · ${esc(s.label)}: ${esc(fmtY(v || 0))}</title></circle>`; });
      });
    }
    const legend = series.length > 1 ? `<div class="legend">${series.map(s => `<span><i style="background:${s.color}"></i>${esc(s.label)}</span>`).join('')}</div>` : '';
    return `${legend}<svg class="chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" role="img">${g}</svg>`;
  }
  function niceMax(v) {
    const p = Math.pow(10, Math.floor(Math.log10(v)));
    for (const m of [1, 2, 2.5, 5, 10]) if (m * p >= v) return m * p;
    return 10 * p;
  }
  function shortDay(d) {
    if (typeof d === 'number') return new Date(d * 1000).toLocaleTimeString('de-CH', { hour: '2-digit', minute: '2-digit' });
    const [, m, day] = d.split('-');
    return `${+day}.${+m}.`;
  }
  const css = name => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

  // ── Overview ──────────────────────────────────────────────────────────
  async function renderOverview() {
    const root = $('#tab-overview');
    if (!root.innerHTML) root.innerHTML = '<p class="muted">Loading…</p>';
    let o, ts, funnel;
    try {
      [o, ts, funnel] = await Promise.all([api('/admin/api/overview'), api('/admin/api/timeseries?days=30'), api('/admin/api/funnel')]);
    } catch (e) { if (e.message !== 'auth') root.innerHTML = `<div class="empty">Could not load: ${esc(e.message)}</div>`; return; }
    const u = o.users, a = o.activity, c = o.content;
    const plat = Object.fromEntries(o.platforms.map(p => [p.platform, p.users]));
    const known = (plat.IOS || 0) + (plat.ANDROID || 0);
    const pct = v => known ? Math.round(100 * v / known) : 0;
    const tile = (label, value, sub = '', cls = '') => `<div class="tile ${cls}"><div class="label">${label}</div><div class="value">${value}</div><div class="sub">${sub}</div></div>`;

    root.innerHTML = `
      <div class="section-head"><h2>Right now</h2><span class="muted small">Updated ${new Date().toLocaleTimeString('de-CH')}</span></div>
      <div class="tiles">
        ${tile('<span class="live-dot"></span>Online now', fmt(a.onlineNow), 'app open')}
        ${tile('Active 15 min', fmt(a.active15m))}
        ${tile('Active 24 h', fmt(a.active24h))}
        ${tile('Active 7 days', fmt(a.active7d))}
        ${tile('Active 30 days', fmt(a.active30d))}
        ${tile('Open reports', fmt(c.openReports), '', c.openReports > 0 ? 'alert' : '')}
      </div>

      <div class="section-head"><h2>Users</h2><span class="muted small">Real accounts only — seed and demo accounts are left out</span></div>
      <div class="tiles">
        ${tile('Total', fmt(u.total), `${fmt(u.verified)} confirmed`)}
        ${tile('New today', fmt(u.newToday), `yesterday ${fmt(u.newYesterday)}`)}
        ${tile('New 7 days', fmt(u.new7d))}
        ${tile('With a dog', fmt(u.withDog))}
        ${tile('Sitters', fmt(u.sitters), `${fmt(u.lookingForSitter)} looking for one`)}
        ${tile('No location', fmt(u.noLocation), 'see nobody in Discover', u.noLocation > 0 ? 'alert' : '')}
      </div>

      <div class="section-head"><h2>Last 30 days</h2></div>
      <div class="grid2">
        <div class="card"><h3>New users per day</h3>${chart(ts.days, [{ values: ts.signups, color: css('--accent'), label: 'Sign-ups' }], { stack: true })}</div>
        <div class="card"><h3>Active users per day</h3>${chart(ts.days, [
          { values: ts.activeIos, color: css('--ios'), label: 'iOS' },
          { values: ts.activeAndroid, color: css('--android'), label: 'Android' },
          { values: ts.activeUsers.map((v, i) => v - ts.activeIos[i] - ts.activeAndroid[i]), color: css('--other'), label: 'Unknown' },
        ], { stack: true })}<p class="muted small">Counted from 5 Oct 2026, when activity tracking started.</p></div>
        <div class="card"><h3>Swipes and matches per day</h3>${chart(ts.days, [
          { values: ts.swipes, color: css('--other'), label: 'Swipes' },
          { values: ts.matches, color: css('--accent'), label: 'Matches (by first like)' },
        ])}</div>
        <div class="card"><h3>Chat messages per day</h3>${chart(ts.days, [{ values: ts.messages, color: css('--accent'), label: 'Messages' }], { stack: true })}</div>
      </div>

      <div class="section-head"><h2>Platforms</h2><span class="muted small">From each user's last request; older app versions are recognised by their network client</span></div>
      <div class="grid2">
        <div class="card">
          <div class="split">
            <div style="width:${pct(plat.IOS || 0)}%;background:var(--ios)"></div>
            <div style="width:${pct(plat.ANDROID || 0)}%;background:var(--android)"></div>
          </div>
          <div class="legend">
            <span><i style="background:var(--ios)"></i>iOS ${fmt(plat.IOS || 0)} (${pct(plat.IOS || 0)}%)</span>
            <span><i style="background:var(--android)"></i>Android ${fmt(plat.ANDROID || 0)} (${pct(plat.ANDROID || 0)}%)</span>
            <span><i style="background:var(--other)"></i>Not seen since tracking began ${fmt(plat.UNKNOWN || 0)}</span>
          </div>
        </div>
        <div class="card"><h3>App versions in use</h3>
          ${o.versions.length ? `<table><tr><th>Platform</th><th>Version</th><th>Users</th></tr>${o.versions.map(v => `<tr><td>${platformPill(v.platform)}</td><td class="mono">${esc(v.version)}</td><td>${fmt(v.users)}</td></tr>`).join('')}</table>` : '<p class="muted small">Nothing yet — fills in as people open the app.</p>'}
        </div>
      </div>

      <div class="section-head"><h2>Where new users stop</h2><span class="muted small">How many real users reached each step</span></div>
      <div class="card">${funnelBars(funnel)}</div>

      <div class="section-head"><h2>Content</h2></div>
      <div class="tiles">
        ${tile('Dogs', fmt(c.dogs), `${fmt(c.dogsWithoutPhoto)} without a photo`)}
        ${tile('Swipes', fmt(c.swipes))}
        ${tile('Matches', fmt(c.matches))}
        ${tile('Messages', fmt(c.messages))}
        ${tile('Upcoming playdates', fmt(c.upcomingPlaydates))}
        ${tile('Open sitting jobs', fmt(c.openSittingJobs))}
        ${tile('Open SOS alerts', fmt(c.openSosAlerts), '', c.openSosAlerts > 0 ? 'alert' : '')}
      </div>`;
  }

  const FUNNEL_LABELS = {
    registered: 'Registered', emailConfirmed: 'Email confirmed', locationSet: 'Location set',
    profilePhoto: 'Profile photo', dogAdded: 'Dog added', swiped: 'Swiped once', matched: 'Got a match', messaged: 'Sent a message',
  };
  function funnelBars(steps) {
    const top = Math.max(1, steps[0]?.users || 0);
    return steps.map(s => `
      <div class="bar-row"><span>${esc(FUNNEL_LABELS[s.step] || s.step)}</span>
        <div class="bar-track"><div class="bar-fill" style="width:${100 * s.users / top}%"></div></div>
        <span class="num">${fmt(s.users)} <span class="muted small">${Math.round(100 * s.users / top)}%</span></span></div>`).join('');
  }

  // ── Reports ───────────────────────────────────────────────────────────
  async function renderReports() {
    const root = $('#tab-reports');
    const filters = ['OPEN', 'RESOLVED', 'DISMISSED', 'ALL'];
    root.innerHTML = `
      <div class="section-head"><h2>Reports</h2>
        <div class="filters">${filters.map(f => `<button data-f="${f}" class="${f === state.reportFilter ? 'active' : ''}">${f[0] + f.slice(1).toLowerCase()}</button>`).join('')}</div>
        <span class="muted small">Apple expects reports to be handled within 24 hours.</span></div>
      <div id="report-list"><p class="muted">Loading…</p></div>`;
    root.querySelector('.filters').addEventListener('click', e => {
      const b = e.target.closest('button[data-f]');
      if (b) { state.reportFilter = b.dataset.f; renderReports(); }
    });
    let list;
    try { list = await api(`/admin/api/reports?status=${state.reportFilter}`); } catch (e) { return; }
    const box = $('#report-list');
    if (!list.length) { box.innerHTML = `<div class="empty">${state.reportFilter === 'OPEN' ? 'No open reports. 🎉' : 'Nothing here.'}</div>`; return; }
    box.innerHTML = list.map(r => `
      <article class="report ${r.status === 'OPEN' ? 'is-open' : ''}" data-id="${r.id}">
        <div class="head">
          <span class="pill ${r.status === 'OPEN' ? 'open' : ''}">${esc(r.status)}</span>
          <span class="pill">${esc(r.kind)}</span>
          <strong><a href="#" data-user="${r.reportedId}">${esc(r.reportedName)}</a></strong>
          <span class="muted small">reported by <a href="#" data-user="${r.reporterId}">${esc(r.reporterName)}</a> · ${when(r.createdAt)} (${ago(r.createdAt)})</span>
        </div>
        <div><span class="reason">${esc(r.reason)}</span>${r.message ? ` — <span>${esc(r.message)}</span>` : ''}</div>
        <details><summary>What was reported</summary><pre>${esc(r.details)}</pre></details>
        ${r.handledAt ? `<div class="muted small">Handled ${when(r.handledAt)} by ${esc(r.handledBy)}</div>` : ''}
        <textarea placeholder="Note (optional)">${esc(r.adminNote || '')}</textarea>
        <div class="actions">
          ${r.status === 'OPEN'
            ? `<button class="btn primary" data-act="RESOLVED">Mark handled</button><button class="btn" data-act="DISMISSED">Dismiss</button>`
            : `<button class="btn" data-act="OPEN">Reopen</button><button class="btn" data-act="save">Save note</button>`}
          <span style="flex:1"></span>
          <button class="btn" data-user="${r.reportedId}">Open profile</button>
        </div>
      </article>`).join('');
    box.onclick = async e => {
      const userLink = e.target.closest('[data-user]');
      if (userLink) { e.preventDefault(); return openUser(+userLink.dataset.user); }
      const btn = e.target.closest('button[data-act]');
      if (!btn) return;
      const card = btn.closest('.report');
      const note = card.querySelector('textarea').value;
      const status = btn.dataset.act === 'save' ? null : btn.dataset.act;
      btn.disabled = true;
      try {
        await api(`/admin/api/reports/${card.dataset.id}`, { method: 'PATCH', body: JSON.stringify({ status, note }) });
        refreshReportCount();
        renderReports();
      } catch { btn.disabled = false; }
    };
  }

  // ── Users ─────────────────────────────────────────────────────────────
  async function renderUsers(q = '') {
    const root = $('#tab-users');
    if (!root.querySelector('#user-q')) {
      root.innerHTML = `
        <div class="section-head"><h2>Users</h2><span class="muted small">Search by name, email or id. Newest first.</span></div>
        <input type="search" id="user-q" placeholder="Search…" style="max-width:360px">
        <div class="table-wrap" id="user-list" style="margin-top:12px"></div>`;
      let t;
      $('#user-q').addEventListener('input', e => { clearTimeout(t); t = setTimeout(() => renderUsers(e.target.value), 250); });
    }
    let list;
    try { list = await api(`/admin/api/users${q ? `?q=${encodeURIComponent(q)}` : ''}`); } catch { return; }
    const box = $('#user-list');
    if (!list.length) { box.innerHTML = '<div class="empty">No users found.</div>'; return; }
    box.innerHTML = `<table>
      <tr><th>ID</th><th>Name</th><th>Email</th><th>Joined</th><th>Last active</th><th>Platform</th><th>Reports</th></tr>
      ${list.map(u => `<tr class="click" data-user="${u.id}">
        <td class="mono">${u.id}</td>
        <td>${esc(u.name)} ${u.role === 'ADMIN' ? '<span class="pill">admin</span>' : ''} ${u.seed ? '<span class="pill seed">seed</span>' : ''}</td>
        <td class="small">${esc(u.email)}${u.verified ? '' : ' <span class="pill">unconfirmed</span>'}</td>
        <td class="small">${when(u.created_at)}</td>
        <td class="small">${ago(u.last_active)}</td>
        <td>${platformPill(u.platform)}</td>
        <td>${u.reports ? `<span class="pill open">${u.reports}</span>` : ''}</td></tr>`).join('')}
    </table>`;
    box.onclick = e => { const r = e.target.closest('[data-user]'); if (r) openUser(+r.dataset.user); };
  }

  async function openUser(id) {
    const rootEl = $('#drawer-root');
    rootEl.innerHTML = '<div class="drawer-bg"></div><aside class="drawer"><p class="muted">Loading…</p></aside>';
    const close = () => { rootEl.innerHTML = ''; };
    rootEl.querySelector('.drawer-bg').onclick = close;
    let u;
    try { u = await api(`/admin/api/users/${id}`); } catch (e) { rootEl.querySelector('.drawer').innerHTML = `<p>Could not load: ${esc(e.message)}</p>`; return; }
    const c = u.counts;
    const yes = v => v ? 'yes' : 'no';
    rootEl.querySelector('.drawer').innerHTML = `
      <button class="btn close" id="drawer-close">Close</button>
      <h2 style="margin:0 0 4px">${esc(u.name)} <span class="muted small mono">#${u.id}</span></h2>
      <p class="muted small" style="margin:0 0 12px">${esc(u.email)} · ${esc(u.auth_provider)} · joined ${when(u.created_at)}</p>
      <div class="photos">${u.photos.map(p => `<a href="${esc(p)}" target="_blank" rel="noopener"><img src="${esc(p)}" alt=""></a>`).join('') || '<span class="muted small">No photos</span>'}</div>
      <dl class="kv">
        <dt>Last active</dt><dd>${when(u.last_active)} (${ago(u.last_active)})</dd>
        <dt>Platform</dt><dd>${platformPill(u.platform)} <span class="mono">${esc(u.version || '')}</span></dd>
        <dt>Bio</dt><dd>${esc(u.bio || '—')}</dd>
        <dt>Email confirmed</dt><dd>${yes(u.verified)}</dd>
        <dt>Location set</dt><dd>${yes(u.has_location)}</dd>
        <dt>Roles</dt><dd>${[u.has_dog !== false && 'dog owner', u.is_sitter && 'sitter', u.looking_for_sitter && 'looking for sitter'].filter(Boolean).join(', ') || '—'}</dd>
        <dt>Activity</dt><dd>${fmt(c.swipes)} swipes · ${fmt(c.matches)} matches · ${fmt(c.messagesSent)} messages</dd>
        <dt>Blocked by</dt><dd>${fmt(c.blockedBy)} users</dd>
        <dt>Reports against</dt><dd>${fmt(c.reportsAgainst)}</dd>
      </dl>
      <h3 style="margin:18px 0 6px;font-size:14px">Dogs</h3>
      ${u.dogs.length ? u.dogs.map(d => `<div class="card" style="margin-bottom:8px"><strong>${esc(d.name)}</strong> <span class="muted small">${esc(d.breed || '')}</span>
        <div class="photos">${d.photos.map(p => `<img src="${esc(p)}" alt="">`).join('') || '<span class="pill open">no photo</span>'}</div>
        ${d.bio ? `<div class="small">${esc(d.bio)}</div>` : ''}</div>`).join('') : '<p class="muted small">No dogs.</p>'}
      <h3 style="margin:18px 0 6px;font-size:14px">Reports against this user</h3>
      ${u.reports.length ? u.reports.map(r => `<div class="small" style="margin-bottom:6px"><span class="pill ${r.status === 'OPEN' ? 'open' : ''}">${esc(r.status)}</span> ${when(r.createdAt)} — <strong>${esc(r.reason)}</strong> by ${esc(r.reporterName)}${r.message ? `: ${esc(r.message)}` : ''}</div>`).join('') : '<p class="muted small">None.</p>'}
      <h3 style="margin:24px 0 6px;font-size:14px;color:var(--danger)">Delete account</h3>
      <p class="small muted">Deletes the account and everything in it, exactly like the user deleting it themselves. Cannot be undone. Type the name to confirm.</p>
      <input id="del-confirm" placeholder="${esc(u.name)}" style="max-width:260px">
      <button class="btn danger" id="del-btn" disabled style="margin-left:6px">Delete account</button>
      <div class="error" id="del-error"></div>`;
    $('#drawer-close').onclick = close;
    const input = $('#del-confirm'), btn = $('#del-btn');
    input.oninput = () => { btn.disabled = input.value.trim() !== u.name; };
    btn.onclick = async () => {
      btn.disabled = true;
      try {
        await api(`/admin/users/${u.id}`, { method: 'DELETE' });
        close();
        showTab(state.tab);
      } catch (e) { $('#del-error').textContent = `Could not delete: ${e.message}`; btn.disabled = false; }
    };
  }

  // ── Online ────────────────────────────────────────────────────────────
  async function renderOnline() {
    const root = $('#tab-online');
    let o;
    try { o = await api('/admin/api/online'); } catch { return; }
    const table = rows => `<table><tr><th>Name</th><th>Platform</th><th>Version</th><th>Last request</th></tr>
      ${rows.map(u => `<tr class="click" data-user="${u.id}"><td>${esc(u.name)}</td><td>${platformPill(u.platform)}</td><td class="mono">${esc(u.version || '')}</td><td class="small">${ago(u.last_active)}</td></tr>`).join('')}</table>`;
    root.innerHTML = `
      <div class="section-head"><h2><span class="live-dot"></span>App open right now: ${o.online.length}</h2><span class="muted small">Refreshes every 20 seconds</span></div>
      <div class="card table-wrap">${o.online.length ? table(o.online) : '<p class="muted small">Nobody has the app open at the moment.</p>'}</div>
      <div class="section-head"><h2>Used in the last 15 minutes: ${o.recent.length}</h2></div>
      <div class="card table-wrap">${o.recent.length ? table(o.recent) : '<p class="muted small">Nobody.</p>'}</div>`;
    root.onclick = e => { const r = e.target.closest('[data-user]'); if (r) openUser(+r.dataset.user); };
  }

  // ── Server ────────────────────────────────────────────────────────────
  async function renderServer(hours = 24) {
    const root = $('#tab-server');
    root.innerHTML = '<p class="muted">Loading…</p>';
    let s;
    try { s = await api(`/admin/api/server?hours=${hours}`); } catch { return; }
    const j = s.jvm;
    const tile = (label, value, sub = '') => `<div class="tile"><div class="label">${label}</div><div class="value">${value}</div><div class="sub">${sub}</div></div>`;
    let railway;
    if (!s.railway.configured) {
      railway = `<div class="notice"><strong>Railway metrics are not connected yet.</strong> To show CPU, memory and network here:
        <ol><li>Railway → your avatar → Account Settings → Tokens → create a token.</li>
        <li>Railway → DogsOut - Server → server → Variables → add <span class="mono">RAILWAY_API_TOKEN</span> with that token.</li>
        <li>Railway redeploys the server; reload this page.</li></ol></div>`;
    } else {
      const block = (name, m) => {
        if (!m || m.error) return `<div class="card"><h3>${name}</h3><p class="muted small">${esc(m?.error || 'No data')}</p></div>`;
        const t = (m.CPU_USAGE || []).map(p => p[0]);
        const series = key => (m[key] || []).map(p => p[1]);
        return `<div class="card"><h3>${name} · CPU (vCPU)</h3>${chart(t, [{ values: series('CPU_USAGE'), color: css('--accent'), label: 'CPU' }], { fmtY: v => v.toFixed(2) })}</div>
          <div class="card"><h3>${name} · Memory (GB)</h3>${chart(t, [{ values: series('MEMORY_USAGE_GB'), color: css('--accent'), label: 'Memory' }], { fmtY: v => v.toFixed(2) })}</div>
          <div class="card"><h3>${name} · Network (MB per sample)</h3>${chart(t, [
            { values: series('NETWORK_RX_GB').map(v => v * 1024), color: css('--accent'), label: 'In' },
            { values: series('NETWORK_TX_GB').map(v => v * 1024), color: css('--android'), label: 'Out' },
          ], { fmtY: v => v.toFixed(1) })}</div>`;
      };
      railway = `<div class="grid2">${block('Server', s.railway.server)}${block('Database', s.railway.database)}</div>`;
    }
    root.innerHTML = `
      <div class="section-head"><h2>Server process</h2></div>
      <div class="tiles">
        ${tile('Memory in use', `${fmt(j.heapUsedMb)} MB`, `of ${fmt(j.heapMaxMb)} MB heap`)}
        ${tile('Load average', j.loadAverage >= 0 ? j.loadAverage.toFixed(2) : '—', `${j.cpus} CPU`)}
        ${tile('Threads', fmt(j.threads))}
        ${tile('Live connections', fmt(j.socketConnections), 'app sockets')}
        ${tile('Up for', j.uptimeMinutes < 120 ? `${j.uptimeMinutes} min` : `${Math.round(j.uptimeMinutes / 60)} h`, 'since last deploy')}
        ${tile('Database size', esc(s.databaseSize || '—'))}
      </div>
      <div class="section-head"><h2>Railway</h2>
        <div class="filters">${[6, 24, 168].map(h => `<button data-h="${h}" class="${h === hours ? 'active' : ''}">${h === 168 ? '7 days' : `${h} h`}</button>`).join('')}</div></div>
      ${railway}`;
    const f = root.querySelector('.filters');
    if (f) f.onclick = e => { const b = e.target.closest('button[data-h]'); if (b) renderServer(+b.dataset.h); };
  }

  // ── Boot ──────────────────────────────────────────────────────────────
  if (token()) start(); else showLogin();
})();
