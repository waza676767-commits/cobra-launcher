package dev.life.launcher.ui;

import dev.life.launcher.auth.Account;
import dev.life.launcher.auth.MicrosoftAuth;
import dev.life.launcher.auth.PrismImport;
import dev.life.launcher.auth.LauncherImport;
import dev.life.launcher.core.Profiles;
import dev.life.launcher.game.GameVersion;
import dev.life.launcher.core.Settings;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

public final class Overlays {
    private Overlays() {}

    private static final MouseAdapter BLOCK = new MouseAdapter() {};

    // =============================================================== Launch

    /** Full-window fade with the Life logo, status line and progress while the game is prepared. */
    public static final class Launch extends JComponent {
        private final Anim.Tween fade = new Anim.Tween(this, 0);
        private final Anim.Tween progress = new Anim.Tween(this, 0).rate(10);
        private String status = "";
        private boolean indeterminate = true;
        private final Timer spin = new Timer(16, e -> repaint());

        public Launch() {
            setOpaque(false);
            setVisible(false);
            addMouseListener(BLOCK);
            addMouseMotionListener(BLOCK);
        }

        public void show(Runnable shown) {
            status = "";
            indeterminate = true;
            progress.set(0);
            setVisible(true);
            spin.start();
            fade.over(1, 260, shown);
        }

        public void status(String s, double fraction) {
            status = s == null ? "" : s;
            indeterminate = fraction < 0;
            if (!indeterminate) progress.to(fraction);
            repaint();
        }

        public void hide(Runnable after) {
            fade.over(0, 420, () -> {
                setVisible(false);
                spin.stop();
                if (after != null) after.run();
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            double a = fade.get();
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.alpha(Theme.BLACK, 0.94 * a));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, 28, 28));   // anti-aliased corners
            g.clip(new Rectangle(0, 0, w, h));
            double cx = w / 2.0, cy = h / 2.0 - 24;
            // soft halo behind the logo
            float[] fr = {0f, 1f};
            Color[] cols = {Theme.alpha(Theme.TEXT, 0.09 * a), Theme.alpha(Theme.TEXT, 0)};
            g.setPaint(new RadialGradientPaint(new Point.Double(cx, cy), 190f, fr, cols));
            g.fillOval((int) cx - 190, (int) cy - 190, 380, 380);
            double breathe = indeterminate ? 0.035 * Math.sin(System.nanoTime() / 1e9 * 3.2) : 0;
            double size = 150 * (0.9 + 0.1 * a + breathe);
            Theme.logo(g, cx, cy, size, a);

            double by = cy + 118;
            if (!status.isEmpty()) Theme.center(g, status, Theme.font(Theme.REGULAR, 13.5f), Theme.alpha(Theme.SOFT, a), 0, by - 34, w, 22);
            double bw = 220;
            Theme.fill(g, cx - bw / 2, by, bw, 3, 1.5, Theme.alpha(Theme.LINE_2, a));
            if (indeterminate) {
                double t = (System.nanoTime() / 1e9) % 1.4 / 1.4;
                double segW = 70, x = cx - bw / 2 - segW + (bw + segW) * t;
                Graphics2D gc = (Graphics2D) g.create();
                gc.clip(new Rectangle.Double(cx - bw / 2, by, bw, 3));
                Theme.fill(gc, x, by, segW, 3, 1.5, Theme.alpha(Theme.TEXT, a));
                gc.dispose();
            } else {
                Theme.fill(g, cx - bw / 2, by, bw * progress.get(), 3, 1.5, Theme.alpha(Theme.TEXT, a));
            }
            g.dispose();
        }
    }

    // ================================================================ Login

    /** Microsoft sign-in with the device-code flow: shows the code, opens the browser, waits. */
    /** Base for full-window modal cards: dims the window and fades card + buttons together. */
    abstract static class Modal extends JComponent {
        protected final Anim.Tween fade = new Anim.Tween(this, 0);

        Modal() {
            setLayout(null);
            setOpaque(false);
            setVisible(false);
            addMouseListener(BLOCK);
            addMouseMotionListener(BLOCK);
        }

        protected void appear() {
            setVisible(true);
            fade.set(0);
            fade.over(1, 200, null);
        }

        protected void disappear(Runnable after) {
            fade.over(0, 180, () -> {
                setVisible(false);
                if (after != null) after.run();
            });
        }

        abstract Rectangle card();

        @Override
        public void paint(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setComposite(Theme.fade(Math.max(0.01, fade.get())));
            super.paint(g);
            g.dispose();
        }

        protected void paintCard(Graphics2D g) {
            g.setColor(Theme.alpha(Theme.BLACK, 0.72));
            g.fillRect(0, 0, getWidth(), getHeight());
            Rectangle c = card();
            if (Glass.on()) {
                Glass.surface(g, this, c.x, c.y, c.width, c.height, 24, 0.5);
            } else {
                Theme.fill(g, c.x, c.y, c.width, c.height, 24, new GradientPaint(0, c.y, Theme.mix(Theme.PANEL, Theme.TEXT, 0.06), 0, c.y + c.height, Theme.mix(Theme.PANEL, Theme.BLACK, 0.4)));
                Theme.stroke(g, c.x, c.y, c.width, c.height, 24, Theme.LINE_2, 1f);
            }
        }
    }

    /**
     * Sign-in: 1) reuse the account you're signed into in Prism Launcher (no Azure app needed),
     * 2) Microsoft in the browser, 3) Microsoft with a code. 2 and 3 need your own Azure client ID.
     */
    public static final class Login extends Modal {
        private enum State { CHOOSE, OPENING, WAITING, CODE, LIVE, FINISHING, ERROR }

        private State state = State.CHOOSE;
        private String code = "", codeUrl = "", authUrl = "", error = "";
        private volatile boolean cancelled;
        private Consumer<Account> onDone;
        private final Components.Button prism, browser, device, cancel, reopen, pasteGo;
        private final Components.Input paste = new Components.Input("", "Paste the address of the blank page here", "link");
        /** Watches the clipboard while the no-setup sign-in is open, so copying the address is enough. */
        private final Timer clip = new Timer(500, e -> checkClipboard());
        private String lastClip = "";
        private final Components.Button[] others = new Components.Button[LauncherImport.Source.values().length];
        private final Timer dots = new Timer(400, e -> repaint());

        public Login() {
            prism = new Components.Button("Use my Prism Launcher account", "user", Components.Variant.GHOST, this::startPrism);
            prism.font(Theme.font(Theme.MEDIUM, 14f)).radius(14);
            browser = new Components.Button("Sign in with Microsoft", "external", Components.Variant.PRIMARY,
                    () -> startLink());
            browser.radius(14);
            device = new Components.Button("Sign in with a code", null, Components.Variant.GHOST, this::startDeviceCode);
            device.radius(14);
            reopen = new Components.Button("Open browser again", "external", Components.Variant.GHOST, () -> {
                if (!authUrl.isEmpty()) MainWindow.openUri(authUrl);
            });
            reopen.radius(14);
            pasteGo = new Components.Button("Continue", null, Components.Variant.PRIMARY, () -> submitLive(paste.getText()));
            pasteGo.radius(14);
            paste.addActionListener(e -> submitLive(paste.getText()));
            add(paste);
            add(pasteGo);
            cancel = new Components.Button("Cancel", null, Components.Variant.GHOST, this::close);
            cancel.radius(14);
            for (JComponent b : new JComponent[]{prism, browser, device, reopen, cancel}) add(b);
            for (LauncherImport.Source s : LauncherImport.Source.values()) {
                Components.Button b = new Components.Button(s.label, null, Components.Variant.GHOST, () -> startImport(s));
                b.radius(14);
                others[s.ordinal()] = b;
                add(b);
            }
        }

        public void open(Consumer<Account> done) {
            this.onDone = done;
            cancelled = false;
            error = "";
            dots.start();
            set(State.CHOOSE);
            appear();
        }

        private boolean hasClientId() {
            return !Settings.get().effectiveClientId().isBlank();
        }

        private void set(State s) {
            state = s;
            boolean choose = s == State.CHOOSE || s == State.ERROR;
            prism.setVisible(choose);
            for (Components.Button b : others) b.setVisible(false);   // Lunar / Dawn / Fast: their sessions can't be renewed, removed
            browser.setVisible(choose);
            device.setVisible(choose);
            browser.setEnabled(true);                 // no client ID: the no-setup Microsoft sign-in
            device.setVisible(false);                 // the one-click sign-in replaced the Azure-app options
            reopen.setVisible(s == State.WAITING || s == State.LIVE || s == State.CODE);
            paste.setVisible(s == State.LIVE);
            pasteGo.setVisible(s == State.LIVE);
            if (s == State.LIVE) clip.start();
            else clip.stop();
            cancel.setText(s == State.CHOOSE ? "Cancel" : s == State.ERROR ? "Close" : "Back");
            doLayout();
            repaint();
        }

        private void startPrism() {
            error = "";
            set(State.FINISHING);
            run("life-login-prism", () -> {
                Account acc = PrismImport.load();
                acc.save();
                done(acc);
            });
        }

        private void startImport(LauncherImport.Source s) {
            error = "";
            set(State.FINISHING);
            run("life-login-" + s.id, () -> {
                Account acc = LauncherImport.load(s);
                acc.save();
                done(acc);
            });
        }

        private void startBrowser() {
            error = "";
            set(State.OPENING);
            String clientId = Settings.get().effectiveClientId();
            run("life-login", () -> {
                MicrosoftAuth.MsTokens ms = MicrosoftAuth.browserSignIn(clientId, url -> {
                    authUrl = url;
                    MainWindow.openUri(url);
                    SwingUtilities.invokeLater(() -> set(State.WAITING));
                }, () -> cancelled);
                finish(ms);
            });
        }

        /** No Azure app needed: browser sign-in, then the blank page's address comes back via clipboard or paste. */
        private void startLive() {
            error = "";
            authUrl = MicrosoftAuth.liveAuthorizeUrl();
            paste.setText("");
            try {   // don't react to whatever was already on the clipboard
                Object c = Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                lastClip = c == null ? "" : c.toString();
            } catch (Exception ex) {
                lastClip = "";
            }
            MainWindow.openUri(authUrl);
            set(State.LIVE);
        }

        private void checkClipboard() {
            if (state != State.LIVE) return;
            try {
                Object c = Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                String s = c == null ? "" : c.toString();
                if (s.equals(lastClip)) return;
                lastClip = s;
                if (s.contains("oauth20_desktop.srf")) {
                    paste.setText(s.trim());
                    submitLive(s);
                }
            } catch (Exception ignored) {
                // clipboard busy / not text
            }
        }

        private void submitLive(String text) {
            if (state != State.LIVE) return;
            String err = MicrosoftAuth.liveError(text);
            if (err != null) {
                error = err.contains("access_denied") || err.contains("cancel") ? "Sign-in was cancelled in the browser." : err;
                set(State.ERROR);
                return;
            }
            String code = MicrosoftAuth.extractLiveCode(text);
            if (code == null) {
                MainWindow.get().toast("That isn't the sign-in address. Copy the whole address of the blank page.");
                return;
            }
            set(State.FINISHING);
            run("life-login-live", () -> finish(MicrosoftAuth.liveRedeem(code)));
        }

        /** One click: the browser opens with the code already typed in; the launcher notices when you're done. */
        private void startLink() {
            error = "";
            set(State.OPENING);
            run("life-login-link", () -> {
                MicrosoftAuth.DeviceCode dc = MicrosoftAuth.linkStart();
                SwingUtilities.invokeLater(() -> {
                    code = dc.userCode();
                    codeUrl = MicrosoftAuth.linkUrl(dc);
                    authUrl = codeUrl;
                    try {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
                    } catch (Exception ignored) {}
                    MainWindow.openUri(codeUrl);
                    set(State.CODE);
                });
                finish(MicrosoftAuth.linkPoll(dc, () -> cancelled));
            });
        }

        private void startDeviceCode() {
            error = "";
            set(State.OPENING);
            String clientId = Settings.get().effectiveClientId();
            run("life-login-code", () -> {
                MicrosoftAuth.DeviceCode dc = MicrosoftAuth.requestDeviceCode(clientId);
                SwingUtilities.invokeLater(() -> {
                    code = dc.userCode();
                    codeUrl = dc.verificationUri();
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
                    MainWindow.openUri(codeUrl);
                    set(State.CODE);
                });
                finish(MicrosoftAuth.poll(clientId, dc, () -> cancelled));
            });
        }

        private void finish(MicrosoftAuth.MsTokens ms) throws MicrosoftAuth.AuthException {
            SwingUtilities.invokeLater(() -> set(State.FINISHING));
            Account acc = MicrosoftAuth.minecraftLogin(ms);
            acc.save();
            done(acc);
        }

        private void done(Account acc) {
            SwingUtilities.invokeLater(() -> {
                Consumer<Account> d = onDone;
                close();
                if (d != null) d.accept(acc);
            });
        }

        private interface Step {
            void run() throws MicrosoftAuth.AuthException;
        }

        private void run(String name, Step step) {
            cancelled = false;
            Thread t = new Thread(() -> {
                try {
                    step.run();
                } catch (MicrosoftAuth.AuthException ex) {
                    if (cancelled) return;
                    SwingUtilities.invokeLater(() -> {
                        error = ex.getMessage();
                        set(State.ERROR);
                    });
                } catch (RuntimeException ex) {                // anything unexpected: show it, never hang on "loading"
                    if (cancelled) return;
                    SwingUtilities.invokeLater(() -> {
                        error = "Sign-in failed (" + ex.getClass().getSimpleName() + "). Check your internet and try again.";
                        set(State.ERROR);
                    });
                }
            }, name);
            t.setDaemon(true);
            t.start();
        }

        public void close() {
            if (state != State.CHOOSE && state != State.ERROR) {
                cancelled = true;       // "Back": stop waiting, return to the choices
                error = "";
                set(State.CHOOSE);
                return;
            }
            cancelled = true;
            dots.stop();
            clip.stop();
            disappear(null);
        }

        @Override
        Rectangle card() {
            int w = 460, h = 500;
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        @Override
        public void doLayout() {
            Rectangle c = card();
            int x = c.x + 32, bw = c.width - 64, y = c.y + 160;
            browser.setBounds(x, y, bw, 48);           // Microsoft first: one click, works for everyone
            // "or use your account from": Lunar / Dawn / Fast Client in one row
            int n = others.length, gap = 8, ow = (bw - gap * (n - 1)) / n;
            for (int i = 0; i < n; i++) others[i].setBounds(x + i * (ow + gap), y + 84, ow, 40);
            prism.setBounds(x, y + 60, bw, 44);
            device.setBounds(x, y + 190, bw, 40);
            reopen.setBounds(x, c.y + c.height - 106, bw, 40);
            paste.setBounds(x, c.y + 262, bw, 44);
            pasteGo.setBounds(x, c.y + 316, bw, 42);
            cancel.setBounds(x, c.y + c.height - 58, bw, 38);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            paintCard(g);
            Rectangle c = card();
            Theme.logo(g, c.x + 50, c.y + 50, 36, 1);
            Theme.left(g, "Sign in", Theme.font(Theme.BOLD, 20f), Theme.TEXT, c.x + 80, c.y + 32, 36);

            String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
            Font body = Theme.font(Theme.REGULAR, 13.5f), small = Theme.font(Theme.REGULAR, 12.5f);
            switch (state) {
                case CHOOSE -> {
                    g.setFont(body);
                    drawWrapped(g, "Sign in with your Microsoft account: your browser opens with everything filled in.",
                            c.x + 32, c.y + 88, c.width - 64, Theme.SOFT);
                    g.setFont(small);
                    drawWrapped(g, "Opens your browser with everything filled in. Sign in, and you're in.",
                            c.x + 32, c.y + 350, c.width - 64, Theme.MUTED);
                }
                case OPENING -> Theme.left(g, "Opening your browser" + dots, body, Theme.SOFT, c.x + 32, c.y + 100, 24);
                case WAITING -> {
                    Theme.left(g, "Finish signing in your browser" + dots, body, Theme.SOFT, c.x + 32, c.y + 92, 24);
                    Theme.left(g, "This window updates by itself when you're done.", small, Theme.MUTED, c.x + 32, c.y + 116, 22);
                }
                case CODE -> {
                    Theme.left(g, "Sign in in the browser tab that opened" + dots, body, Theme.SOFT, c.x + 32, c.y + 84, 24);
                    Theme.left(g, "The code is already filled in. This updates by itself when you're done.", small, Theme.MUTED, c.x + 32, c.y + 106, 20);
                    Theme.fill(g, c.x + 32, c.y + 128, c.width - 64, 70, 16, Theme.alpha(Theme.BLACK, 0.5));
                    Theme.stroke(g, c.x + 32, c.y + 128, c.width - 64, 70, 16, Theme.LINE_2, 1f);
                    Theme.center(g, code, Theme.tracked(Theme.BOLD, 32f, 0.16f), Theme.TEXT, c.x + 32, c.y + 128, c.width - 64, 70);
                    Theme.center(g, "Code copied to your clipboard", small, Theme.MUTED, c.x, c.y + 206, c.width, 20);
                }
                case LIVE -> {
                    g.setFont(body);
                    int yy = drawWrapped(g, "1. Sign in with your Microsoft account in the browser tab that just opened.",
                            c.x + 32, c.y + 88, c.width - 64, Theme.SOFT);
                    yy = drawWrapped(g, "2. You'll land on a blank page. Copy its address (Ctrl+L, then Ctrl+C).",
                            c.x + 32, yy + 6, c.width - 64, Theme.SOFT);
                    g.setFont(small);
                    drawWrapped(g, "Life picks it up from your clipboard by itself" + dots + " or paste it below.",
                            c.x + 32, yy + 8, c.width - 64, Theme.MUTED);
                }
                case FINISHING -> Theme.left(g, "Signing in to Minecraft" + dots, body, Theme.SOFT, c.x + 32, c.y + 100, 24);
                case ERROR -> {
                    g.setFont(body);
                    drawWrapped(g, error, c.x + 32, c.y + 88, c.width - 64, Theme.DANGER);
                }
            }
            g.dispose();
        }
    }

    // ============================================================ Profile editor

    /** Create / edit a profile: picture, name, version. */
    public static final class ProfileEditor extends Modal {
        private Profiles.Profile editing;
        private boolean isNew;
        private Consumer<Profiles.Profile> onDone;
        private final Components.Input name = new Components.Input("", "Profile name", null);
        private final Components.Button version;
        private GameVersion chosen = GameVersion.MODERN;
        private final Components.Button save, delete, cancel, pick, clearPic;
        private java.nio.file.Path pendingImage;
        private boolean clearImage;
        private java.awt.image.BufferedImage preview;

        public ProfileEditor() {
            version = new Components.Button(GameVersion.MODERN.id, "chevron-down", Components.Variant.GHOST, null);
            version.radius(12);
            version.onClick(() -> VersionMenu.open(version, 300, chosen, v -> {
                chosen = v;
                version.setText(v.id + (v.hasLife() ? "  ·  Life Client" : ""));
            }));
            save = new Components.Button("Save", null, Components.Variant.PRIMARY, this::save);
            save.radius(14).font(Theme.font(Theme.MEDIUM, 14f));
            delete = new Components.Button("Delete profile", "trash", Components.Variant.DANGER, this::delete);
            delete.radius(14);
            cancel = new Components.Button("Cancel", null, Components.Variant.GHOST, this::close);
            cancel.radius(14);
            pick = new Components.Button("Choose picture", "folder", Components.Variant.GHOST, this::pickImage);
            pick.radius(12);
            clearPic = new Components.Button("Remove", null, Components.Variant.GHOST, () -> {
                clearImage = true;
                pendingImage = null;
                preview = null;
                repaint();
            });
            clearPic.radius(12);
            for (JComponent c : new JComponent[]{name, version, save, delete, cancel, pick, clearPic}) add(c);
        }

        /** @param p profile to edit, or null to create one */
        public void open(Profiles.Profile p, Consumer<Profiles.Profile> done) {
            onDone = done;
            isNew = p == null;
            editing = p;
            pendingImage = null;
            clearImage = false;
            preview = p == null ? null : Profiles.icon(p);
            name.setText(p == null ? "" : p.name);
            GameVersion gv = p == null ? GameVersion.MODERN : p.gameVersion();
            chosen = gv;
            version.setText(gv.id + (gv.hasLife() ? "  ·  Life Client" : ""));
            delete.setVisible(!isNew && Profiles.all().size() > 1 && (p == null || !p.locked()));
            doLayout();
            appear();
            SwingUtilities.invokeLater(name::requestFocusInWindow);
        }

        private void pickImage() {
            java.nio.file.Path path = FilePicker.one("Profile picture", "Pictures", "png", "jpg", "jpeg", "gif", "bmp");
            if (path == null) return;
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(path.toFile());
                if (img == null) throw new java.io.IOException("not an image");
                pendingImage = path;
                clearImage = false;
                int side = Math.min(img.getWidth(), img.getHeight());
                preview = img.getSubimage((img.getWidth() - side) / 2, (img.getHeight() - side) / 2, side, side);
                repaint();
            } catch (Exception e) {
                MainWindow.get().toast("That file isn't a picture Life can read (PNG, JPG, GIF, BMP).");
            }
        }

        private void save() {
            GameVersion gv = chosen;
            Profiles.Profile p = editing;
            if (p == null) p = Profiles.create(name.getText(), gv);
            else {
                if (!name.getText().isBlank()) p.name = name.getText().trim();
                p.version = gv.id;
            }
            try {
                if (pendingImage != null) Profiles.setIcon(p, pendingImage);
                else if (clearImage) Profiles.clearIcon(p);
            } catch (Exception e) {
                MainWindow.get().toast("Couldn't save the picture: " + e.getMessage());
            }
            Profiles.save();
            Profiles.Profile result = p;
            disappear(() -> {
                if (onDone != null) onDone.accept(result);
            });
        }


        private void delete() {
            Profiles.Profile p = editing;
            try {
                Profiles.delete(p);
                MainWindow.get().toast("Deleted " + p.name);
            } catch (Exception e) {
                MainWindow.get().toast("Couldn't delete: " + e.getMessage());
            }
            disappear(() -> {
                if (onDone != null) onDone.accept(null);
            });
        }

        public void close() {
            disappear(null);
        }

        @Override
        Rectangle card() {
            int w = 460, h = 470;
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        @Override
        public void doLayout() {
            Rectangle c = card();
            int x = c.x + 32, bw = c.width - 64;
            pick.setBounds(x + 110, c.y + 119, 150, 36);
            clearPic.setBounds(x + 268, c.y + 119, 90, 36);
            name.setBounds(x, c.y + 226, bw, 42);
            version.setBounds(x, c.y + 310, bw, 38);
            save.setBounds(x + bw - 130, c.y + c.height - 62, 130, 44);
            cancel.setBounds(x + bw - 130 - 10 - 100, c.y + c.height - 62, 100, 44);
            delete.setBounds(x, c.y + c.height - 62, 150, 44);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            paintCard(g);
            Rectangle c = card();
            int x = c.x + 32;
            Theme.left(g, isNew ? "New profile" : "Edit profile", Theme.font(Theme.BOLD, 20f), Theme.TEXT, x, c.y + 26, 36);
            Theme.left(g, "Own mods, packs and worlds per profile.", Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, x, c.y + 58, 20);
            ProfileArt.draw(g, preview, name.getText().isBlank() ? "P" : name.getText(), x, c.y + 92, 90, true);
            Theme.left(g, "Name", Theme.font(Theme.MEDIUM, 13f), Theme.SOFT, x, c.y + 200, 22);
            Theme.left(g, "Version", Theme.font(Theme.MEDIUM, 13f), Theme.SOFT, x, c.y + 284, 22);
            g.dispose();
        }
    }

    /** Word-wrapped text; returns the y just below the last line. */
    private static int drawWrapped(Graphics2D g, String text, int x, int y, int w, Color color) {
        return drawRich(g, text, x, y, w, color, null, null);
    }

    /** Wrapped text; any word containing {@code mark} is drawn in {@code markColor} (e.g. a name in red). */
    private static int drawRich(Graphics2D g, String text, int x, int y, int w, Color color, String mark, Color markColor) {
        FontMetrics fm = g.getFontMetrics();
        int space = fm.stringWidth(" ");
        int cx = x, yy = y + fm.getAscent();
        for (String word : text.split(" ")) {
            int ww = fm.stringWidth(word);
            if (cx > x && cx + ww > x + w) {
                cx = x;
                yy += fm.getHeight() + 2;
            }
            g.setColor(mark != null && word.contains(mark) ? markColor : color);
            g.drawString(word, cx, yy);
            cx += ww + space;
        }
        return yy + fm.getDescent();
    }

    // ================================================================ Choice

    /**
     * A small question card: a title, a few lines of text and one button per option (a scrollable
     * list when there are many, e.g. profiles to import). Esc / Cancel closes it.
     */
    public static final class Choice extends Modal {
        private String title = "", body = "";
        /** A word drawn in red in the body (the beta notice's contact name), or null. */
        private String red;
        private final JPanel list = new JPanel(null);
        private final JScrollPane scroll;
        private final Components.Button cancel;
        private java.util.function.IntConsumer onPick;
        private int count;

        public Choice() {
            list.setOpaque(false);
            scroll = new JScrollPane(list);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setBorder(null);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(scroll);
            cancel = new Components.Button("Cancel", null, Components.Variant.GHOST, () -> disappear(null));
            cancel.radius(14);
            add(cancel);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "life-choice-close");
            getActionMap().put("life-choice-close", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (isVisible()) disappear(null);
                }
            });
        }

        /**
         * @param primary index of the option drawn as the main (accent) button, or -1
         */
        public void ask(String title, String body, java.util.List<String> options, int primary, java.util.function.IntConsumer onPick) {
            this.title = title;
            this.body = body;
            this.onPick = onPick;
            this.red = null;
            cancel.setVisible(true);
            field.setVisible(false);
            for (var l : field.getActionListeners()) field.removeActionListener(l);
            list.removeAll();
            count = options.size();
            for (int i = 0; i < options.size(); i++) {
                final int idx = i;
                Components.Button b = new Components.Button(options.get(i), null,
                        i == primary ? Components.Variant.PRIMARY : Components.Variant.GHOST, () -> {
                    disappear(null);
                    this.onPick.accept(idx);
                });
                b.radius(14);
                list.add(b);
            }
            doLayout();
            appear();
            repaint();
        }

        private final Components.Input field = new Components.Input("", "", null);
        {
            field.setVisible(false);
        }

        /** A question with a text box (e.g. a link) and one button; {@code onText} gets what was typed. */
        public void askText(String title, String body, String placeholder, String button, java.util.function.Consumer<String> onText) {
            if (field.getParent() == null) add(field);
            field.setText("");
            field.setPlaceholder(placeholder);
            ask(title, body, java.util.List.of(button), 0, i -> onText.accept(field.getText().trim()));
            field.setVisible(true);
            field.addActionListener(e -> {
                if (!field.isVisible()) return;
                disappear(null);
                onText.accept(field.getText().trim());
            });
            doLayout();
            SwingUtilities.invokeLater(field::requestFocusInWindow);
        }

        /** Like ask(), with one word in the text shown in red. */
        public void askMarked(String title, String body, String redWord, java.util.List<String> options, int primary, java.util.function.IntConsumer onPick) {
            ask(title, body, options, primary, onPick);
            this.red = redWord;
            cancel.setVisible(false);
            repaint();
        }

        @Override
        Rectangle card() {
            int w = 480;
            int textH = 40 + 20 * Math.max(1, (body.length() / 52) + body.split("\n").length);
            int listH = Math.min(count, 6) * 50 + (field.isVisible() ? 52 : 0);
            int h = Math.min(getHeight() - 40, 70 + textH + listH + (cancel.isVisible() ? 64 : 14));
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        @Override
        public void doLayout() {
            Rectangle c = card();
            int x = c.x + 28, bw = c.width - 56;
            int textH = 40 + 20 * Math.max(1, (body.length() / 52) + body.split("\n").length);
            int top = c.y + 30 + textH;
            if (field.isVisible()) {                  // the text box (askText) sits above the buttons
                field.setBounds(x, top, bw, 40);
                top += 52;
            }
            int listH = c.y + c.height - (cancel.isVisible() ? 64 : 14) - top;
            scroll.setBounds(x, top, bw, Math.max(0, listH));
            for (int i = 0; i < list.getComponentCount(); i++) list.getComponent(i).setBounds(0, i * 50, bw - (count > 6 ? 12 : 0), 42);
            list.setPreferredSize(new Dimension(bw - 12, count * 50));
            list.revalidate();
            cancel.setBounds(x, c.y + c.height - 54, bw, 38);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            paintCard(g);
            Rectangle c = card();
            Theme.left(g, title, Theme.font(Theme.BOLD, 20f), Theme.TEXT, c.x + 28, c.y + 22, 30);
            g.setFont(Theme.font(Theme.REGULAR, 13.5f));
            int y = c.y + 70;
            for (String para : body.split("\n")) {
                y = drawRich(g, para, c.x + 28, y, c.width - 56, Theme.SOFT, red, new Color(0xFF4D4D)) + 8;
            }
            g.dispose();
        }
    }

    // ================================================================ Toast

    public static final class Toast extends JComponent {
        private final Anim.Tween fade = new Anim.Tween(this, 0).rate(12);
        private String message = "";
        private final Components.Button action;
        private final Timer hideTimer = new Timer(4500, e -> hideToast());

        public Toast() {
            setLayout(null);
            setOpaque(false);
            setVisible(false);
            action = new Components.Button("", null, Components.Variant.SUBTLE, null);
            action.radius(10).font(Theme.font(Theme.MEDIUM, 12.5f));
            add(action);
            hideTimer.setRepeats(false);
        }

        public void show(String msg, String actionLabel, Runnable act) {
            message = msg;
            action.setVisible(actionLabel != null);
            if (actionLabel != null) {
                action.setText(actionLabel);
                action.onClick(() -> { hideToast(); act.run(); });
            }
            setVisible(true);
            fade.to(1);
            hideTimer.restart();
            getParent().doLayout();
            doLayout();
            repaint();
        }

        private void hideToast() {
            fade.to(0, () -> setVisible(false));
        }

        @Override
        public void paint(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setComposite(Theme.fade(Math.max(0.01, fade.get())));
            g.translate(0, (1 - fade.get()) * 10);
            super.paint(g);
            g.dispose();
        }

        public Dimension wanted() {
            FontMetrics fm = getFontMetrics(Theme.font(Theme.REGULAR, 13.5f));
            int w = Math.min(640, fm.stringWidth(message) + 40 + (action.isVisible() ? action.getPreferredSize().width + 12 : 0));
            return new Dimension(w, 48);
        }

        @Override
        public void doLayout() {
            if (action.isVisible()) {
                int aw = action.getPreferredSize().width;
                action.setBounds(getWidth() - aw - 8, 8, aw, getHeight() - 16);
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            if (Glass.on()) Glass.surface(g, this, 0, 0, getWidth(), getHeight(), 14, 0.5);
            else {
                Theme.fill(g, 0, 0, getWidth(), getHeight(), 14, Theme.PANEL);
                Theme.stroke(g, 0, 0, getWidth(), getHeight(), 14, Theme.LINE_2, 1f);
            }
            g.setFont(Theme.font(Theme.REGULAR, 13.5f));
            int max = getWidth() - 36 - (action.isVisible() ? action.getWidth() + 12 : 0);
            Theme.left(g, Theme.ellipsize(message, g.getFontMetrics(), max), Theme.font(Theme.REGULAR, 13.5f), Theme.TEXT, 18, 0, getHeight());
            g.dispose();
        }
    }
}
