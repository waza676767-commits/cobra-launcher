// Velo for Windows: everything the user sees. Tabs are <webview> elements; settings, pins, history
// and the saved session live in localStorage.
const { ipcRenderer } = require('electron');

const $ = (s) => document.querySelector(s);
const el = (tag, cls, html) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (html != null) e.innerHTML = html;
  return e;
};
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const store = {
  load(key, fallback) {
    try { return JSON.parse(localStorage.getItem('velo.' + key)) ?? fallback; } catch { return fallback; }
  },
  save(key, value) { localStorage.setItem('velo.' + key, JSON.stringify(value)); },
};

const icon = (d) => `<svg viewBox="0 0 24 24"><path d="${d}"/></svg>`;
const I = {
  back: icon('M19 12H5M12 19l-7-7 7-7'), forward: icon('M5 12h14M12 5l7 7-7 7'),
  reload: icon('M21 4v6h-6M19.5 14.5a8 8 0 1 1-1.9-8.3L21 10'), close: icon('M18 6L6 18M6 6l12 12'),
  home: icon('M3 10l9-7 9 7v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM9 21v-8h6v8'), plus: icon('M12 5v14M5 12h14'),
  search: icon('M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16zM21 21l-4.3-4.3'),
  lock: icon('M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V7a4 4 0 0 1 8 0v4'),
  shield: icon('M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'),
  ai: icon('M10 3l1.8 4.9a2 2 0 0 0 1.2 1.2L18 11l-4.9 1.8a2 2 0 0 0-1.2 1.2L10 19l-1.8-4.9a2 2 0 0 0-1.2-1.2L2 11l4.9-1.8a2 2 0 0 0 1.2-1.2L10 3zM19 15l.7 1.8 1.8.7-1.8.7L19 20l-.7-1.8-1.8-.7 1.8-.7L19 15z'),
  history: icon('M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3.5 2'),
  settings: icon('M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6'),
  pin: icon('M12 17v5M9 10.8a2 2 0 0 1-1.1 1.8l-1.8.9A2 2 0 0 0 5 15.2V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.8a2 2 0 0 0-1.1-1.8l-1.8-.9a2 2 0 0 1-1.1-1.8V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z'),
  split: icon('M4 4h16v16H4zM12 4v16'),
};

// ── State ──────────────────────────────────────────────────────────────────────────────────────
const S = Object.assign({
  theme: 'system', accent: '', style: 'round', wallpaper: 0, adBlock: true, privateSearch: false,
  ramLimit: 0, restore: 'ask', vpnOn: false, proxy: '', saveHistory: true, aiSplit: false,
  showClock: true, showStats: true, showPins: true,
}, store.load('settings', {}));
let pins = store.load('pins', [
  { title: 'DuckDuckGo', url: 'https://duckduckgo.com/' }, { title: 'Wikipedia', url: 'https://www.wikipedia.org/' },
  { title: 'YouTube', url: 'https://www.youtube.com/' }, { title: 'Reddit', url: 'https://www.reddit.com/' },
]);
let history = store.load('history', []);
let blockedTotal = store.load('blocked', 0);
let tabs = [];
let activeId = null;
let nextId = 1;
let memoryMb = 0;

const ACCENTS = ['', '#3d7bff', '#7c5cff', '#ff4f8b', '#f2463d', '#ff8a3d', '#f5b301', '#22b866', '#12b5a6', '#0ea5e9'];
const WALLS = [
  null,
  { dark: true, css: 'radial-gradient(at 15% 12%,#3d7bff 0,transparent 55%),radial-gradient(at 95% 38%,#7c5cff 0,transparent 50%),radial-gradient(at 25% 95%,#12b5a6 0,transparent 55%),#0b1026' },
  { dark: true, css: 'radial-gradient(at 85% 8%,#ff8a3d 0,transparent 55%),radial-gradient(at 10% 50%,#ff4f8b 0,transparent 50%),radial-gradient(at 90% 100%,#7c5cff 0,transparent 50%),#2a0e3a' },
  { dark: true, css: 'radial-gradient(at 10% 18%,#0ea5e9 0,transparent 50%),radial-gradient(at 95% 60%,#12b5a6 0,transparent 50%),radial-gradient(at 35% 100%,#3d7bff 0,transparent 50%),#031b34' },
  { dark: true, css: 'radial-gradient(at 90% 12%,#22b866 0,transparent 50%),radial-gradient(at 5% 60%,#a3e63588 0,transparent 40%),radial-gradient(at 65% 100%,#0f766e 0,transparent 55%),#06180f' },
  { dark: false, css: 'radial-gradient(at 10% 10%,#c7d7ff 0,transparent 55%),radial-gradient(at 95% 45%,#e9d5ff 0,transparent 50%),radial-gradient(at 30% 100%,#cffafe 0,transparent 55%),#f3f5fb' },
  { dark: false, css: 'radial-gradient(at 90% 8%,#ffd3b8 0,transparent 55%),radial-gradient(at 8% 50%,#ffc2d9 0,transparent 50%),radial-gradient(at 85% 100%,#ffe9a8 0,transparent 50%),#fff6f0' },
];
const AI = [
  { id: 'chatgpt', label: 'ChatGPT', url: 'https://chatgpt.com/' },
  { id: 'gemini', label: 'Gemini', url: 'https://gemini.google.com/app' },
  { id: 'claude', label: 'Claude', url: 'https://claude.ai/new' },
];

const saveSettings = () => { store.save('settings', S); pushConfig(); };
const pushConfig = () => ipcRenderer.invoke('config', {
  adBlock: S.adBlock, privateSearch: S.privateSearch, proxy: S.vpnOn ? S.proxy : '',
  allow: store.load('allow', []),
});

// ── Addresses ──────────────────────────────────────────────────────────────────────────────────
const FILE_ENDINGS = new Set(['js', 'txt', 'json', 'xml', 'html', 'css', 'png', 'jpg', 'gif', 'pdf', 'zip', 'exe', 'doc', 'docx', 'mp3', 'mp4', 'csv']);
const TRACKING = new Set(['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'utm_id', 'fbclid', 'gclid', 'dclid', 'msclkid', 'mc_cid', 'mc_eid', 'igshid', 'yclid', 'twclid', 'ttclid', '_hsenc', '_hsmi', 'mkt_tok', 'srsltid']);

function isDark() {
  return S.theme === 'dark' || (S.theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
}

function searchUrl(q) {
  let u = 'https://duckduckgo.com/?q=' + encodeURIComponent(q.trim());
  if (isDark()) u += '&kae=d';
  if (S.privateSearch) u += '&kd=1&kg=p&k1=-1';
  return u;
}

function looksLikeUrl(text) {
  if (!text || /\s/.test(text)) return false;
  if (/^https?:\/\//i.test(text) || /^localhost(:\d+)?(\/|$)/i.test(text) || /^\d{1,3}(\.\d{1,3}){3}(:\d+)?(\/|$)/.test(text)) return true;
  if (!/^([a-z0-9-]+\.)+[a-z]{2,}(:\d{1,5})?([/?#].*)?$/i.test(text)) return false;
  const host = text.split(/[/?#]/)[0];
  return !(host === text && FILE_ENDINGS.has(host.split('.').pop().toLowerCase()));
}

function resolve(input) {
  const text = input.trim();
  if (!text) return '';
  if (/^https?:\/\//i.test(text)) return S.privateSearch ? stripTracking(text) : text;
  if (looksLikeUrl(text)) return (/^(localhost|\d)/i.test(text) ? 'http://' : 'https://') + text;
  return searchUrl(text);
}

function stripTracking(url) {
  try {
    const u = new URL(url);
    [...u.searchParams.keys()].forEach((k) => { if (TRACKING.has(k.toLowerCase())) u.searchParams.delete(k); });
    return u.toString();
  } catch { return url; }
}

const hostOf = (url) => { try { return new URL(url).hostname.replace(/^www\./, ''); } catch { return ''; } };
const searchQuery = (url) => {
  try { const u = new URL(url); return u.hostname.endsWith('duckduckgo.com') ? u.searchParams.get('q') : null; } catch { return null; }
};

// ── Tabs ───────────────────────────────────────────────────────────────────────────────────────
const active = () => tabs.find((t) => t.id === activeId);

function newTab(url, select = true) {
  const tab = { id: nextId++, url: '', title: '', favicon: '', view: null, home: true, loading: false, sleeping: false, lastActive: Date.now() };
  const at = tabs.findIndex((t) => t.id === activeId);
  tabs.splice(at < 0 ? tabs.length : at + 1, 0, tab);
  if (select) selectTab(tab.id);
  if (url) load(tab, url);
  render();
  return tab;
}

function createView(tab) {
  const view = document.createElement('webview');
  view.setAttribute('partition', 'persist:web');
  view.setAttribute('allowpopups', '');
  tab.view = view;
  tab.sleeping = false;
  $('#views').appendChild(view);
  view.addEventListener('did-start-loading', () => { tab.loading = true; render(); });
  view.addEventListener('did-stop-loading', () => { tab.loading = false; render(); saveSession(); });
  view.addEventListener('page-title-updated', (e) => { tab.title = e.title; recordHistory(tab); render(); });
  view.addEventListener('page-favicon-updated', (e) => { tab.favicon = e.favicons[0] || ''; render(); });
  const onNav = (e) => {
    if (e.isMainFrame === false) return;
    tab.url = e.url;
    if (tab.id === activeId && document.activeElement !== $('#addr')) showAddress();
    recordHistory(tab);
    render();
    saveSession();
  };
  view.addEventListener('did-navigate', onNav);
  view.addEventListener('did-navigate-in-page', onNav);
  view.addEventListener('found-in-page', (e) => {
    $('#findcount').textContent = e.result.matches ? `${e.result.activeMatchOrdinal}/${e.result.matches}` : '0/0';
  });
  view.addEventListener('did-fail-load', (e) => {
    if (e.isMainFrame && e.errorCode !== -3) toast(`This page didn't load (${e.errorDescription})`);
  });
  return view;
}

function load(tab, input) {
  const url = resolve(input);
  if (!url) return;
  tab.home = false;
  tab.url = url;
  tab.title = '';
  if (!tab.view) createView(tab).src = url; else tab.view.loadURL(url).catch(() => {});
  render();
  saveSession();
}

function selectTab(id) {
  activeId = id;
  const tab = active();
  if (!tab) return;
  tab.lastActive = Date.now();
  if (!tab.home && !tab.view && tab.url) createView(tab).src = tab.url; // wake a sleeping tab
  closeFind();
  showAddress();
  render();
  saveSession();
}

function closeTab(id) {
  const i = tabs.findIndex((t) => t.id === id);
  if (i < 0) return;
  tabs[i].view?.remove();
  tabs.splice(i, 1);
  if (!tabs.length) newTab();
  else if (id === activeId) selectTab(tabs[Math.min(i, tabs.length - 1)].id);
  render();
  saveSession();
}

function goHome() {
  const tab = active();
  if (!tab) return;
  tab.home = true;
  showAddress();
  render();
  saveSession();
}

/** Unloads a background page to free its memory; it reloads when the tab is opened again. */
function sleepTab(tab) {
  if (!tab.view || tab.id === activeId) return false;
  tab.view.remove();
  tab.view = null;
  tab.sleeping = true;
  tab.loading = false;
  return true;
}

function sleepOldest() {
  const candidates = tabs.filter((t) => t.view && t.id !== activeId).sort((a, b) => a.lastActive - b.lastActive);
  return candidates.length ? sleepTab(candidates[0]) : false;
}

// ── Drawing ────────────────────────────────────────────────────────────────────────────────────
function showAddress() {
  const tab = active();
  $('#addr').value = !tab || tab.home ? '' : searchQuery(tab.url) ?? tab.url;
}

function render() {
  const tab = active();
  const home = !tab || tab.home;
  const strip = $('#tabs');
  strip.innerHTML = '';
  for (const t of tabs) {
    const node = el('div', 'tab' + (t.id === activeId ? ' active' : '') + (t.sleeping ? ' sleeping' : ''));
    const title = t.home ? 'Start page' : t.title || hostOf(t.url) || 'Loading…';
    node.innerHTML = (t.favicon && !t.home ? `<img src="${esc(t.favicon)}">` : '<span class="dot"></span>') +
      `<span class="t">${esc(title)}</span><span class="x">${I.close}</span>`;
    node.title = title;
    node.onclick = (e) => (e.target.closest('.x') ? closeTab(t.id) : selectTab(t.id));
    node.onauxclick = (e) => { if (e.button === 1) closeTab(t.id); };
    strip.appendChild(node);
  }
  for (const t of tabs) t.view?.classList.toggle('show', t.id === activeId && !t.home);
  $('#start').classList.toggle('show', home);
  if (home) renderStart();

  const view = !home && tab.view;
  let canForward = false;
  try { canForward = !!view && view.canGoForward(); } catch { /* not attached yet */ }
  $('#back').disabled = home;
  $('#forward').disabled = home ? !(tab && tab.url) : !canForward;
  $('#reload').disabled = home;
  $('#home').disabled = home;
  $('#reload').innerHTML = !home && tab.loading ? I.close : I.reload;
  $('#lock').innerHTML = home || searchQuery(tab.url) != null ? I.search : I.lock;
  $('#lock').style.opacity = home || tab.url.startsWith('https://') || searchQuery(tab.url) != null ? 1 : 0.35;
  $('#addrwrap').style.setProperty('--pw', !home && tab.loading ? '70%' : '0%');
  $('#addrwrap').style.setProperty('--prog', 'color-mix(in srgb, var(--accent) 12%, transparent)');
  $('#pin').disabled = home;
  $('#pin').style.color = !home && pins.some((p) => p.url === tab.url) ? 'var(--accent)' : 'var(--dim)';
  $('#shield').className = 'chip' + (S.adBlock ? ' on' : '');
  $('#shield').innerHTML = I.shield + (S.adBlock ? blockedTotal.toLocaleString() : 'Off');
  $('#vpn').hidden = !S.proxy;
  $('#vpn').className = 'chip' + (S.vpnOn ? ' on' : '');
  $('#vpn').textContent = S.vpnOn ? 'VPN on' : 'VPN off';
}

function applyTheme() {
  const dark = isDark();
  document.body.className = [dark ? 'dark' : '', S.style === 'round' ? '' : S.style].join(' ').trim();
  if (S.accent) {
    document.body.style.setProperty('--accent', S.accent);
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(S.accent.slice(i, i + 2), 16));
    document.body.style.setProperty('--on', 0.299 * r + 0.587 * g + 0.114 * b > 150 ? '#0b0b0f' : '#fff');
  } else {
    document.body.style.removeProperty('--accent');
    document.body.style.removeProperty('--on');
  }
  ipcRenderer.send('overlay', dark ? '#f4f4f6' : '#0b0b0f');
}

function logoSvg() {
  const L = window.VELO_LOGO;
  return `<svg class="logo" viewBox="0 0 ${L.w} ${L.h}"><path fill="currentColor" fill-rule="evenodd" d="${L.d}"/></svg>`;
}

function renderStart() {
  const start = $('#start');
  const wall = WALLS[S.wallpaper] || null;
  const custom = S.wallpaper === -1 && store.load('wallpaper', '');
  start.style.background = custom ? `center/cover url("${custom}")` : wall ? wall.css : 'var(--bg)';
  const onDark = custom || (wall && wall.dark);
  const vars = custom || wall
    ? `--sp:${onDark ? '#fff' : '#0b0b0f'};--spd:${onDark ? 'rgba(255,255,255,.75)' : '#45454f'};--card:${onDark ? 'rgba(255,255,255,.13)' : 'rgba(255,255,255,.62)'};--cardb:${onDark ? 'rgba(255,255,255,.16)' : 'rgba(255,255,255,.7)'}`
    : '';
  const hour = new Date().getHours();
  const hello = hour < 5 ? 'Good night' : hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : hour < 23 ? 'Good evening' : 'Good night';
  const now = new Date();
  start.innerHTML = `<div class="sp" style="${vars}">
    ${logoSvg()}<div class="hello">${hello}</div>
    <div class="search" id="spsearch">${I.search}<span>Search DuckDuckGo</span></div>
    ${S.showPins ? `<div class="pins">${pins.map((p, i) => `<div class="pinx" data-i="${i}" title="Right-click to remove"><div class="ic"><img src="https://icons.duckduckgo.com/ip3/${esc(hostOf(p.url))}.ico" onerror="this.replaceWith(document.createTextNode('${esc(p.title[0] || '•').toUpperCase()}'))"></div><span>${esc(p.title)}</span></div>`).join('')}</div>` : ''}
    <div class="widgets">
      ${S.showClock ? `<div class="widget"><small>${now.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}</small><b>${now.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</b></div>` : ''}
      ${S.showStats ? `<div class="widget"><small>ads and trackers blocked</small><b>${S.adBlock ? blockedTotal.toLocaleString() : 'Off'}</b></div>` : ''}
    </div></div>`;
  $('#spsearch').onclick = () => $('#addr').focus();
  start.querySelectorAll('.pinx').forEach((node) => {
    const pin = pins[+node.dataset.i];
    node.onclick = () => load(active(), pin.url);
    node.oncontextmenu = () => { pins = pins.filter((p) => p !== pin); store.save('pins', pins); render(); toast('Removed from your start page'); };
  });
}

function toast(text) {
  const t = $('#toast');
  t.textContent = text;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { t.hidden = true; }, 2400);
}

// ── History and session ────────────────────────────────────────────────────────────────────────
function recordHistory(tab) {
  if (!S.saveHistory || !/^https?:/.test(tab.url)) return;
  if (S.privateSearch && searchQuery(tab.url) != null) return;
  const title = tab.title || hostOf(tab.url);
  if (history[0] && history[0].url === tab.url) Object.assign(history[0], { title, time: Date.now() });
  else history.unshift({ url: tab.url, title, time: Date.now() });
  if (history.length > 3000) history.length = 3000;
  store.save('history', history);
}

function saveSession() {
  store.save('session', {
    tabs: tabs.map((t) => ({ url: t.home ? '' : t.url, title: t.home ? '' : t.title })),
    active: tabs.findIndex((t) => t.id === activeId),
  });
}

function restore(session) {
  const real = session.tabs.filter((t) => t.url);
  if (tabs.length === 1 && tabs[0].home && !tabs[0].url) { tabs = []; activeId = null; }
  let pick = null;
  real.forEach((saved) => {
    // Restored tabs start asleep and load only when opened.
    const tab = { id: nextId++, url: saved.url, title: saved.title, favicon: '', view: null, home: false, loading: false, sleeping: true, lastActive: 0 };
    tabs.push(tab);
    if (saved === session.tabs[session.active]) pick = tab;
  });
  selectTab((pick || tabs[tabs.length - 1]).id);
}

// ── Dialogs, drawer ────────────────────────────────────────────────────────────────────────────
function dialog(html, wire) {
  const d = $('#dialog');
  d.innerHTML = `<div class="dlg">${html}</div>`;
  d.hidden = false;
  $('#scrim').hidden = false;
  wire(d, () => { d.hidden = true; if ($('#drawer').hidden) $('#scrim').hidden = true; });
}

function openDrawer(build) {
  const d = $('#drawer');
  d.hidden = false;
  $('#scrim').hidden = false;
  build(d);
}

function closeDrawer() {
  $('#drawer').hidden = true;
  if ($('#dialog').hidden) $('#scrim').hidden = true;
}

const sw = (key) => `<button class="sw ${S[key] ? 'on' : ''}" data-sw="${key}"></button>`;
const row = (title, sub, right, cls = '') => `<div class="row ${cls}"><div>${title}${sub ? `<small>${sub}</small>` : ''}</div>${right || ''}</div>`;

function openSettings() {
  openDrawer((d) => {
    const ram = [0, 1000, 1500, 2000, 3000, 4000, 6000, 8000];
    d.innerHTML = `<h2>Settings <button class="ib" id="dclose">${I.close}</button></h2>
      <h3>THEME</h3><div class="card"><div class="row"><div class="seg" style="flex:1" data-seg="theme">${['system', 'light', 'dark'].map((v) => `<button data-v="${v}" class="${S.theme === v ? 'on' : ''}">${v[0].toUpperCase() + v.slice(1)}</button>`).join('')}</div></div></div>
      <h3>BUTTON COLOUR</h3><div class="card"><div class="swatches">${ACCENTS.map((c) => `<button data-accent="${c}" class="${S.accent === c ? 'on' : ''}" style="background:${c || 'linear-gradient(135deg,#0b0b0f 50%,#f4f4f6 50%)'}"></button>`).join('')}<input type="color" id="customAccent" value="${S.accent || '#3d7bff'}" title="Custom colour" style="width:34px;height:34px;border:0;background:none;padding:0"></div></div>
      <h3>STYLE</h3><div class="card"><div class="row"><div class="seg" style="flex:1" data-seg="style">${['round', 'soft', 'sharp'].map((v) => `<button data-v="${v}" class="${S.style === v ? 'on' : ''}">${v[0].toUpperCase() + v.slice(1)}</button>`).join('')}</div></div></div>
      <h3>START PAGE WALLPAPER</h3><div class="card"><div class="walls">${WALLS.map((w, i) => `<button data-wall="${i}" class="${S.wallpaper === i ? 'on' : ''}" style="background:${w ? w.css : 'var(--bg)'}"></button>`).join('')}<button data-wall="-1" class="${S.wallpaper === -1 ? 'on' : ''}" title="Your picture" style="display:grid;place-items:center;background:var(--hi)">${I.plus}</button></div>
        ${row('Pinned pages', '', sw('showPins'))}${row('Clock', '', sw('showClock'))}${row('Privacy stats', '', sw('showStats'))}</div>
      <h3>PRIVACY</h3><div class="card">
        ${row('Private search', 'Searches stay out of history, tracking tags are stripped from links, and sites are told not to sell or share your data.', sw('privateSearch'))}
        ${row('Block ads and trackers', `${blockedTotal.toLocaleString()} blocked so far.`, sw('adBlock'))}
        ${row('Save browsing history', '', sw('saveHistory'))}
        ${row('<span style="color:var(--danger)">Clear browsing data</span>', 'History, cookies, cached files and site storage.', '', 'click" id="clearData')}</div>
      <h3>VPN</h3><div class="card">
        ${row('Route Velo through a server', 'Covers Velo only. Pages stay blocked if the server is unreachable.', sw('vpnOn'))}
        <div class="row"><input class="field" id="proxy" placeholder="socks5://host:port or http://host:port" value="${esc(S.proxy)}"></div></div>
      <h3>MEMORY</h3><div class="card"><div class="row"><div>RAM limit<small id="ramInfo"></small></div><b id="ramLabel"></b></div>
        <div class="row"><input type="range" id="ram" min="0" max="${ram.length - 1}" step="1" value="${Math.max(0, ram.indexOf(S.ramLimit))}"></div>
        ${row('Sleep background tabs now', 'Frees their memory. They reload when you open them.', '', 'click" id="sleepNow')}</div>
      <h3>WHEN VELO OPENS</h3><div class="card"><div class="row"><div class="seg" style="flex:1" data-seg="restore">${[['ask', 'Ask me'], ['always', 'Restore tabs'], ['never', 'Start fresh']].map(([v, l]) => `<button data-v="${v}" class="${S.restore === v ? 'on' : ''}">${l}</button>`).join('')}</div></div></div>`;
    const ramText = () => {
      $('#ramLabel').textContent = S.ramLimit ? (S.ramLimit / 1000) + ' GB' : `Auto (${(autoLimit() / 1000).toFixed(1)} GB)`;
      $('#ramInfo').textContent = `Using ${memoryMb.toLocaleString()} MB · ${tabs.filter((t) => t.view).length} of ${tabs.length} tabs awake`;
    };
    ramText();
    $('#dclose').onclick = closeDrawer;
    d.querySelectorAll('[data-sw]').forEach((b) => (b.onclick = () => {
      const key = b.dataset.sw;
      if (key === 'vpnOn' && !S.proxy.trim()) return toast('Enter a server address first');
      S[key] = !S[key];
      b.classList.toggle('on', S[key]);
      saveSettings();
      render();
    }));
    d.querySelectorAll('[data-seg] button').forEach((b) => (b.onclick = () => {
      S[b.parentElement.dataset.seg] = b.dataset.v;
      [...b.parentElement.children].forEach((c) => c.classList.toggle('on', c === b));
      saveSettings(); applyTheme(); render();
    }));
    d.querySelectorAll('[data-accent]').forEach((b) => (b.onclick = () => {
      S.accent = b.dataset.accent;
      d.querySelectorAll('[data-accent]').forEach((c) => c.classList.toggle('on', c === b));
      saveSettings(); applyTheme();
    }));
    $('#customAccent').oninput = (e) => { S.accent = e.target.value; saveSettings(); applyTheme(); };
    d.querySelectorAll('[data-wall]').forEach((b) => (b.onclick = () => {
      const i = +b.dataset.wall;
      const done = () => { S.wallpaper = i; d.querySelectorAll('[data-wall]').forEach((c) => c.classList.toggle('on', c === b)); saveSettings(); render(); };
      if (i !== -1) return done();
      const pick = Object.assign(document.createElement('input'), { type: 'file', accept: 'image/*' });
      pick.onchange = () => {
        const reader = new FileReader();
        reader.onload = () => { try { store.save('wallpaper', reader.result); done(); } catch { toast('That picture is too large'); } };
        if (pick.files[0]) reader.readAsDataURL(pick.files[0]);
      };
      pick.click();
    }));
    $('#proxy').onchange = (e) => { S.proxy = e.target.value.trim(); if (!S.proxy) S.vpnOn = false; saveSettings(); render(); };
    $('#ram').oninput = (e) => { S.ramLimit = ram[+e.target.value]; saveSettings(); ramText(); };
    $('#sleepNow').onclick = () => { tabs.forEach(sleepTab); render(); ramText(); toast('Background tabs are asleep'); };
    $('#clearData').onclick = () => dialog(
      '<h2>Clear browsing data?</h2><p>Removes history, cookies (you will be signed out of sites), cached files and site storage.</p><div class="acts"><button class="btn soft" id="no">Cancel</button><button class="btn danger" id="yes">Clear</button></div>',
      (dlg, close) => {
        dlg.querySelector('#no').onclick = close;
        dlg.querySelector('#yes').onclick = async () => {
          history = []; store.save('history', history);
          await ipcRenderer.invoke('clear-data', { cookies: true, cache: true, storage: true });
          close(); toast('Browsing data cleared');
        };
      });
  });
}

function openHistory() {
  openDrawer((d) => {
    const draw = (filter = '') => {
      const f = filter.toLowerCase();
      const list = history.filter((h) => !f || h.url.toLowerCase().includes(f) || h.title.toLowerCase().includes(f)).slice(0, 300);
      $('#hlist').innerHTML = list.length ? list.map((h) => `<div class="hrow" data-u="${esc(h.url)}"><div><b>${esc(h.title)}</b><small>${new Date(h.time).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })} · ${esc(hostOf(h.url))}</small></div><button class="ib" data-del="${h.time}">${I.close}</button></div>`).join('')
        : '<p style="text-align:center;color:var(--dim);padding:40px 0">Nothing here yet.</p>';
      d.querySelectorAll('.hrow').forEach((r) => (r.onclick = (e) => {
        const del = e.target.closest('[data-del]');
        if (del) { history = history.filter((h) => String(h.time) !== del.dataset.del); store.save('history', history); return draw($('#hfilter').value); }
        closeDrawer(); load(active(), r.dataset.u);
      }));
    };
    d.innerHTML = `<h2>History <button class="ib" id="dclose">${I.close}</button></h2>
      <div style="display:flex;gap:8px"><input class="field" id="hfilter" placeholder="Search history"><button class="btn soft" id="hclear" style="color:var(--danger)">Clear all</button></div>
      <div id="hlist" style="margin-top:12px"></div>`;
    $('#dclose').onclick = closeDrawer;
    $('#hfilter').oninput = (e) => draw(e.target.value);
    $('#hclear').onclick = () => { history = []; store.save('history', history); draw(); toast('History cleared'); };
    draw();
  });
}

// ── Assistant ──────────────────────────────────────────────────────────────────────────────────
// Each assistant is its own page that stays loaded, so conversations keep running side by side.
const aiViews = {};
let aiShown = store.load('aiShown', ['chatgpt']);

function renderAi() {
  const panel = $('#aipanel');
  if (!panel.firstChild) {
    panel.innerHTML = `<div class="aihead"><div class="seg"></div><button class="ib" id="aisplit" title="Show two at once">${I.split}</button><button class="ib" id="aiclose">${I.close}</button></div><div class="aiviews"></div>`;
    $('#aiclose').onclick = () => { panel.hidden = true; };
    $('#aisplit').onclick = () => { S.aiSplit = !S.aiSplit; if (!S.aiSplit) aiShown = aiShown.slice(-1); saveSettings(); renderAi(); };
  }
  const seg = panel.querySelector('.seg');
  seg.innerHTML = AI.map((a) => `<button data-ai="${a.id}" class="${aiShown.includes(a.id) ? 'on' : ''}">${a.label}</button>`).join('');
  seg.querySelectorAll('button').forEach((b) => (b.onclick = () => {
    const id = b.dataset.ai;
    if (!S.aiSplit) aiShown = [id];
    else if (aiShown.includes(id)) { if (aiShown.length > 1) aiShown = aiShown.filter((x) => x !== id); }
    else aiShown = [...aiShown, id].slice(-2);
    store.save('aiShown', aiShown);
    renderAi();
  }));
  $('#aisplit').style.color = S.aiSplit ? 'var(--accent)' : 'var(--dim)';
  panel.classList.toggle('wide', S.aiSplit && aiShown.length > 1);
  for (const a of AI) {
    if (aiShown.includes(a.id) && !aiViews[a.id]) {
      const v = document.createElement('webview');
      v.setAttribute('partition', 'persist:ai');
      v.setAttribute('allowpopups', '');
      v.src = a.url;
      aiViews[a.id] = v;
      panel.querySelector('.aiviews').appendChild(v);
    }
    aiViews[a.id]?.classList.toggle('show', aiShown.includes(a.id));
  }
}

// ── Address bar ────────────────────────────────────────────────────────────────────────────────
let suggestIndex = -1;

function showSuggestions() {
  const text = $('#addr').value.trim().toLowerCase();
  const box = $('#suggest');
  if (!text) return box.classList.remove('open');
  const seen = new Set();
  const hits = history.filter((h) => (h.url.toLowerCase().includes(text) || h.title.toLowerCase().includes(text)) && !seen.has(h.url) && seen.add(h.url)).slice(0, 6);
  box.innerHTML = `<div class="sg" data-u="${esc($('#addr').value)}"><span>${looksLikeUrl($('#addr').value.trim()) ? 'Open' : 'Search DuckDuckGo for'} “${esc($('#addr').value)}”</span></div>` +
    hits.map((h) => `<div class="sg" data-u="${esc(h.url)}"><span>${esc(h.title)}</span><small>${esc(hostOf(h.url))}</small></div>`).join('');
  box.classList.add('open');
  suggestIndex = -1;
  box.querySelectorAll('.sg').forEach((n) => (n.onmousedown = (e) => { e.preventDefault(); submitAddress(n.dataset.u); }));
}

function submitAddress(value) {
  $('#suggest').classList.remove('open');
  $('#addr').blur();
  if (value.trim()) load(active() || newTab(), value);
}

function openFind() { $('#findbar').hidden = false; $('#findinput').focus(); $('#findinput').select(); }
function closeFind() {
  if ($('#findbar').hidden) return;
  $('#findbar').hidden = true;
  try { active()?.view?.stopFindInPage('clearSelection'); } catch { /* view not ready */ }
}

// ── Wiring ─────────────────────────────────────────────────────────────────────────────────────
function autoLimit() { return Math.max(1500, Math.min(6000, (navigator.deviceMemory || 8) * 1024 / 2)); }

function init() {
  $('#newtab').innerHTML = I.plus; $('#back').innerHTML = I.back; $('#forward').innerHTML = I.forward;
  $('#home').innerHTML = I.home; $('#ai').innerHTML = I.ai; $('#historyBtn').innerHTML = I.history;
  $('#settingsBtn').innerHTML = I.settings; $('#pin').innerHTML = I.pin; $('#findclose').innerHTML = I.close;

  $('#newtab').onclick = () => { newTab(); $('#addr').focus(); };
  $('#back').onclick = () => { const t = active(); if (t.view && t.view.canGoBack()) t.view.goBack(); else goHome(); };
  $('#forward').onclick = () => { const t = active(); if (t.home && t.url) { t.home = false; showAddress(); render(); } else t.view?.goForward(); };
  $('#reload').onclick = () => { const t = active(); if (t.loading) t.view?.stop(); else t.view?.reload(); };
  $('#home').onclick = goHome;
  $('#ai').onclick = () => { const p = $('#aipanel'); p.hidden = !p.hidden; if (!p.hidden) renderAi(); };
  $('#historyBtn').onclick = openHistory;
  $('#settingsBtn').onclick = openSettings;
  $('#shield').onclick = () => { S.adBlock = !S.adBlock; saveSettings(); render(); active()?.view?.reload(); };
  $('#vpn').onclick = () => { S.vpnOn = !S.vpnOn; saveSettings(); render(); toast(S.vpnOn ? 'VPN on' : 'VPN off'); };
  $('#pin').onclick = () => {
    const t = active();
    const had = pins.some((p) => p.url === t.url);
    pins = had ? pins.filter((p) => p.url !== t.url) : [...pins, { title: t.title || hostOf(t.url), url: t.url }];
    store.save('pins', pins); render(); toast(had ? 'Removed from your start page' : 'Pinned to your start page');
  };
  $('#scrim').onclick = () => { if ($('#dialog').hidden) closeDrawer(); };

  const addr = $('#addr');
  addr.onfocus = () => addr.select();
  addr.oninput = showSuggestions;
  addr.onblur = () => { $('#suggest').classList.remove('open'); showAddress(); };
  addr.onkeydown = (e) => {
    const items = [...document.querySelectorAll('#suggest.open .sg')];
    if (e.key === 'Enter') submitAddress(suggestIndex >= 0 && items[suggestIndex] ? items[suggestIndex].dataset.u : addr.value);
    else if (e.key === 'Escape') addr.blur();
    else if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && items.length) {
      e.preventDefault();
      suggestIndex = (suggestIndex + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
      items.forEach((n, i) => n.classList.toggle('sel', i === suggestIndex));
    }
  };
  $('#findinput').oninput = (e) => { const v = active()?.view; if (v && e.target.value) v.findInPage(e.target.value); };
  $('#findinput').onkeydown = (e) => {
    if (e.key === 'Enter' && e.target.value) active()?.view?.findInPage(e.target.value, { forward: !e.shiftKey, findNext: true });
    if (e.key === 'Escape') closeFind();
  };
  $('#findclose').onclick = closeFind;

  const shortcut = (key) => ({
    t: () => { newTab(); addr.focus(); }, w: () => closeTab(activeId), l: () => addr.focus(),
    f: () => { if (active() && !active().home) openFind(); }, h: openHistory,
  }[key]?.());
  addEventListener('keydown', (e) => { if (e.ctrlKey && 'twlfh'.includes(e.key.toLowerCase())) { e.preventDefault(); shortcut(e.key.toLowerCase()); } });
  ipcRenderer.on('shortcut', (_e, key) => shortcut(key));
  ipcRenderer.on('open-tab', (_e, url) => newTab(url));
  ipcRenderer.on('blocked', () => { blockedTotal++; store.save('blocked', blockedTotal); clearTimeout(init.t); init.t = setTimeout(render, 400); });
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => { applyTheme(); render(); });

  applyTheme();
  pushConfig();

  // Tabs from last time: ask before bringing them back, unless told otherwise in Settings.
  const previous = store.load('session', null);
  const real = previous ? previous.tabs.filter((t) => t.url) : [];
  newTab();
  if (real.length && S.restore === 'always') restore(previous);
  else if (real.length && S.restore === 'ask') {
    store.save('pending', previous);
    dialog(`<h2>Restore your tabs?</h2><p>Velo saved the ${real.length === 1 ? 'page' : real.length + ' tabs'} you had open last time.</p>
      <div class="card">${real.slice(0, 4).map((t) => `<div class="row"><div>${esc(t.title || hostOf(t.url))}<small>${esc(hostOf(t.url))}</small></div></div>`).join('')}</div>
      ${real.length > 4 ? `<p style="margin-top:10px">and ${real.length - 4} more</p>` : ''}
      <div class="acts"><button class="btn soft" id="no">Start fresh</button><button class="btn" id="yes">Restore</button></div>`,
    (dlg, close) => {
      dlg.querySelector('#no').onclick = close;
      dlg.querySelector('#yes').onclick = () => { close(); restore(previous); toast(real.length === 1 ? 'Tab restored' : real.length + ' tabs restored'); };
    });
  }

  // Memory limit: measure Velo's real memory and sleep the least recently used tabs when over.
  setInterval(async () => {
    memoryMb = await ipcRenderer.invoke('memory');
    if (memoryMb > (S.ramLimit || autoLimit()) && sleepOldest()) render();
  }, 5000);
  setInterval(() => { if (active()?.home) renderStart(); }, 30000);
}

init();
