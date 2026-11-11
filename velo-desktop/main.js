// Velo for Windows: main process. Owns the window, ad blocking, privacy headers, the VPN proxy
// and memory measurement. Everything the user sees lives in ui/.
const { app, BrowserWindow, session, ipcMain, Menu } = require('electron');
const path = require('path');
const fs = require('fs');

let win;
let config = { adBlock: true, privateSearch: false, proxy: '', allow: [] };
let blocked = 0;

const hosts = new Set(
  fs.readFileSync(path.join(__dirname, 'adblock_hosts.txt'), 'utf8')
    .split('\n').map((s) => s.trim()).filter((s) => s && !s.startsWith('#'))
);
const PATH_PATTERNS = ['/pagead/', 'adsbygoogle.js', '/fbevents.js', 'facebook.com/tr?', '/gampad/ads', '/prebid.js'];

// A listed host also covers its subdomains.
function listed(host) {
  let h = host;
  for (;;) {
    if (hosts.has(h)) return true;
    const dot = h.indexOf('.');
    if (dot < 0) return false;
    h = h.slice(dot + 1);
    if (!h.includes('.')) return false;
  }
}

function hostOf(url) {
  try { return new URL(url).hostname.toLowerCase(); } catch { return ''; }
}

function shouldBlock(details) {
  if (!config.adBlock || details.resourceType === 'mainFrame') return false;
  if (!/^https?:/.test(details.url)) return false;
  const host = hostOf(details.url);
  const page = hostOf(details.referrer || (details.webContents && details.webContents.getURL()) || '');
  if (!host || host === page) return false;
  if (config.allow.includes(page.replace(/^www\./, ''))) return false;
  return listed(host) || PATH_PATTERNS.some((p) => details.url.includes(p));
}

// Pages live in their own storage partition, apart from Velo's own settings.
const webSession = () => session.fromPartition('persist:web');

function applyProxy() {
  const rules = config.proxy.trim();
  // No direct fallback: if the server is down pages fail rather than expose the real address.
  return webSession().setProxy(rules ? { proxyRules: rules } : { mode: 'direct' });
}

function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 820, minWidth: 720, minHeight: 480,
    backgroundColor: '#0d0d10',
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#00000000', symbolColor: '#f4f4f6', height: 42 },
    icon: path.join(__dirname, 'ui', 'icon.png'),
    webPreferences: { nodeIntegration: true, contextIsolation: false, webviewTag: true, spellcheck: true },
  });
  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'ui', 'index.html'));
}

app.whenReady().then(() => {
  // Present as a normal Chrome so sites (and sign-in pages) treat Velo as a full browser.
  app.userAgentFallback = app.userAgentFallback.replace(/\s(Velo|velo|Electron)\/\S+/g, '');

  const ses = webSession();
  ses.webRequest.onBeforeRequest((details, done) => {
    if (shouldBlock(details)) {
      blocked++;
      if (win && !win.isDestroyed()) win.webContents.send('blocked', blocked);
      done({ cancel: true });
    } else {
      done({});
    }
  });
  ses.webRequest.onBeforeSendHeaders((details, done) => {
    if (config.privateSearch) {
      details.requestHeaders['Sec-GPC'] = '1';
      details.requestHeaders['DNT'] = '1';
    }
    done({ requestHeaders: details.requestHeaders });
  });

  const ai = aiSession();
  ai.setUserAgent(FIREFOX_UA);
  ai.setPreloads([path.join(__dirname, 'ui', 'ai-preload.js')]);
  ai.webRequest.onBeforeSendHeaders((details, done) => {
    for (const name of Object.keys(details.requestHeaders)) {
      if (/^sec-ch-ua/i.test(name)) delete details.requestHeaders[name];
    }
    done({ requestHeaders: details.requestHeaders });
  });

  ipcMain.handle('config', async (_e, next) => {
    const proxyChanged = next.proxy !== config.proxy;
    config = { ...config, ...next };
    if (proxyChanged) await applyProxy();
    return true;
  });
  // Real memory use of every Velo process, in megabytes.
  ipcMain.handle('memory', () =>
    Math.round(app.getAppMetrics().reduce((sum, p) => sum + p.memory.workingSetSize, 0) / 1024));
  ipcMain.handle('clear-data', async (_e, what) => {
    if (what.cookies) await ses.clearStorageData({ storages: ['cookies'] });
    if (what.cache) await ses.clearCache();
    if (what.storage) await ses.clearStorageData({ storages: ['localstorage', 'indexdb', 'serviceworkers', 'cachestorage'] });
    return true;
  });
  ipcMain.on('overlay', (_e, color) => {
    if (win && win.setTitleBarOverlay) win.setTitleBarOverlay({ color: '#00000000', symbolColor: color, height: 42 });
  });

  createWindow();
});

// Google refuses sign-in from Chromium-based embedded browsers but accepts Firefox, so the
// assistant panel (its own storage partition) presents itself as Firefox throughout.
const FIREFOX_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0';
const aiSession = () => session.fromPartition('persist:ai');

// Links that ask for a new window open as a tab instead.
app.on('web-contents-created', (_e, contents) => {
  if (contents.getType() !== 'webview') return;
  // Keyboard shortcuts must work while a page has focus too.
  contents.on('before-input-event', (event, input) => {
    if (input.type !== 'keyDown' || !(input.control || input.meta)) return;
    const key = input.key.toLowerCase();
    if (['t', 'w', 'l', 'f', 'h'].includes(key) && win && !win.isDestroyed()) {
      event.preventDefault();
      win.webContents.send('shortcut', key);
    }
  });
  contents.setWindowOpenHandler(({ url }) => {
    // Sign-in pop-ups from an assistant stay in its own panel so the login lands in the right place.
    if (contents.session === aiSession()) {
      contents.loadURL(url);
      return { action: 'deny' };
    }
    if (win && !win.isDestroyed()) win.webContents.send('open-tab', url);
    return { action: 'deny' };
  });
});

app.on('window-all-closed', () => app.quit());
