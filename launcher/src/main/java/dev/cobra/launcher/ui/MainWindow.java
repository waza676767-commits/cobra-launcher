package dev.cobra.launcher.ui;

import dev.cobra.launcher.auth.Account;
import dev.cobra.launcher.auth.MicrosoftAuth;
import dev.cobra.launcher.core.BuildInfo;
import dev.cobra.launcher.core.Http;
import dev.cobra.launcher.core.Paths;
import dev.cobra.launcher.core.Profiles;
import dev.cobra.launcher.core.Settings;
import dev.cobra.launcher.game.GameLauncher;
import dev.cobra.launcher.game.GameVersion;
import dev.cobra.launcher.game.Installer;
import dev.cobra.launcher.game.Playtime;
import dev.cobra.launcher.ui.pages.AnalyticsPage;
import dev.cobra.launcher.ui.pages.ContentPage;
import dev.cobra.launcher.ui.pages.HomePage;
import dev.cobra.launcher.ui.pages.SettingsPage;
import dev.cobra.launcher.content.Modrinth;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MainWindow {
    public static final int W = 1120, H = 700;
    private static MainWindow instance;

    public final JFrame frame = new JFrame(BuildInfo.NAME);
    private final Settings settings = Settings.get();
    private final boolean translucent;
    private final Root root = new Root();
    private final TitleBar titleBar = new TitleBar();
    private final List<Page> pages = new ArrayList<>();
    private final NavSidebar sidebar;
    /** The previous look's round rail (Settings → Look → Launcher look → Legacy). */
    private final Rail rail;

    private final PageHost host = new PageHost();
    private final Overlays.Launch launchOverlay = new Overlays.Launch();
    private final Overlays.Login login = new Overlays.Login();
    private final Overlays.Choice choice = new Overlays.Choice();
    private final Overlays.Toast toast = new Overlays.Toast();
    private final Overlays.ProfileEditor profileEditor = new Overlays.ProfileEditor();
    private final ClickFx clickFx = new ClickFx(frame);
    private Intro intro;
    private final List<Runnable> stateListeners = new ArrayList<>();

    private Account account = Account.load();
    private BufferedImage face;
    private GameVersion version;
    private volatile Process running;
    private JComponent popup, catcher;

    public static MainWindow get() { return instance; }

    public MainWindow() {
        instance = this;
        Anim.enabled = settings.animations;
        Theme.setLight(false);                               // Abyss is dark only
        version = Profiles.current().gameVersion();

        // in sidebar order: Play (Home, Mods, Texture packs, Profiles), Library, App
        pages.add(settings.legacyGui ? new dev.cobra.launcher.ui.pages.LegacyHomePage() : new HomePage());
        pages.add(new ContentPage(Modrinth.Kind.MODS));
        pages.add(new ContentPage(Modrinth.Kind.PACKS));
        pages.add(new dev.cobra.launcher.ui.pages.ProfilesPage());
        pages.add(new dev.cobra.launcher.ui.pages.AccessoriesPage());
        pages.add(new dev.cobra.launcher.ui.pages.RecordingsPage());
        pages.add(new AnalyticsPage());
        pages.add(new SettingsPage());
        pages.add(new dev.cobra.launcher.ui.pages.LogsPage());
        sidebar = new NavSidebar(pages, this::showPage, settings.sidebarExpanded, () -> { root.doLayout(); root.repaint(); });
        rail = new Rail(pages, this::showPage);


        translucent = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT);
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { quit(); }
        });
        frame.setIconImages(dev.cobra.launcher.core.AppIcon.windowIcons());

        root.add(titleBar);
        root.add(sidebar);
        root.add(rail);
        sidebar.setVisible(!settings.legacyGui);
        rail.setVisible(settings.legacyGui);

        root.add(host);
        for (Page p : pages) {
            p.setVisible(false);
            host.add(p);
        }
        frame.setContentPane(root);
        JLayeredPane lp = frame.getLayeredPane();
        lp.add(launchOverlay, JLayeredPane.MODAL_LAYER);
        lp.add(login, Integer.valueOf(JLayeredPane.MODAL_LAYER + 1));
        lp.add(choice, Integer.valueOf(JLayeredPane.MODAL_LAYER + 2));
        lp.add(profileEditor, Integer.valueOf(JLayeredPane.MODAL_LAYER + 1));
        Glass.attach(root);
        Wallpaper.attach(root);
        lp.add(toast, JLayeredPane.POPUP_LAYER);
        lp.add(clickFx, JLayeredPane.DRAG_LAYER);
        if (settings.animations) {
            intro = new Intro();
            lp.add(intro, Integer.valueOf(JLayeredPane.DRAG_LAYER - 1));
        }
        frame.setMinimumSize(new Dimension(900, 700));
        frame.setSize(W, H);
        frame.setLocationRelativeTo(null);
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutOverlays();
                applyShape();
            }
        });
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                if (popup != null) { closePopup(); return true; }
                if (login.isVisible()) { login.close(); return true; }
                if (profileEditor.isVisible()) { profileEditor.close(); return true; }
            }
            return false;
        });

        showPage(0);
        loadFace();
        layoutOverlays();
    }

    /** Ctrl+1…9 switches pages, Ctrl+Enter launches, Ctrl+, opens Settings, Ctrl+F searches settings. */
    private void installShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED || !e.isControlDown() || !frame.isActive()) return false;
            int k = e.getKeyCode();
            if (k >= java.awt.event.KeyEvent.VK_1 && k <= java.awt.event.KeyEvent.VK_9 && k - java.awt.event.KeyEvent.VK_1 < pages.size()) {
                showPage(k - java.awt.event.KeyEvent.VK_1);
                return true;
            }
            if (k == java.awt.event.KeyEvent.VK_ENTER) {
                launch();
                return true;
            }
            if (k == java.awt.event.KeyEvent.VK_COMMA) {
                for (int i = 0; i < pages.size(); i++) if (pages.get(i).title().equals("Settings")) showPage(i);
                return true;
            }
            return false;
        });
    }

    /** The launcher's name: yours (Settings → Launcher → Launcher name) or Abyss. */
    public static String displayName() {
        String n = dev.cobra.launcher.core.Settings.get().launcherName;
        return n == null || n.isBlank() ? "Abyss" : n.trim();
    }

    /** Applies a new launcher name to the window title and the sidebar. */
    public void applyName() {
        frame.setTitle(displayName() + " Launcher");
        sidebar.repaint();
    }

    public void show() {
        FreezeWatch.start();
        applyName();
        installShortcuts();
        dev.cobra.launcher.game.GameVersion.refresh(null);     // every Minecraft release, kept up to date
        Theme.setLight(settings.lightMode);
        frame.setVisible(true);
        applyShape();
        LockScreen.showIfLocked(frame.getRootPane(), () -> new Timer(500, e -> {   // password first (if set)
            ((Timer) e.getSource()).stop();
            speedCheck();
            syncCosmeticsFromGame();
            betaNotice();
        }).start());
    }

    /** Rounded corners without a translucent window: a window shape keeps the content pane opaque,
     *  so Swing never repaints a child over an uncleared background (that was the flicker). */
    private void applyShape() {
        if (!translucent) return;
        if ("linux".equals(dev.cobra.launcher.core.Paths.OS_NAME)) {
            frame.setShape(null);
            return;
        }
        try {
            frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 28, 28));
        } catch (Throwable ignored) {}
    }

    private void layoutOverlays() {
        Dimension d = frame.getSize();
        launchOverlay.setBounds(0, 0, d.width, d.height);
        clickFx.setBounds(0, 0, d.width, d.height);
        if (intro != null && intro.getParent() != null) intro.setBounds(0, 0, d.width, d.height);
        login.setBounds(0, 0, d.width, d.height);
        choice.setBounds(0, 0, d.width, d.height);
        profileEditor.setBounds(0, 0, d.width, d.height);
        profileEditor.doLayout();
        Dimension t = toast.wanted();
        toast.setBounds((d.width - t.width) / 2, d.height - t.height - 28, t.width, t.height);
        login.doLayout();
    }

    // ------------------------------------------------------------ navigation

    public void showPage(int i) {
        sidebar.setSelected(i);
        rail.setSelected(i);

        SwingUtilities.invokeLater(() -> { root.doLayout(); root.repaint(); });   // Home uses the full frame
        root.repaint();   // the glass sheet behind pages shows/hides with Home
        Page from = null;
        for (Page p : pages) if (p.isVisible()) from = p;
        Page to = pages.get(i);
        if (from == to) {
            to.onShow();
            return;
        }
        if (from != null && Anim.enabled && host.isShowing()) {
            host.transition(from, to);
        } else {
            for (Page p : pages) p.setVisible(p == to);
            to.onShow();
            to.reveal();
            host.doLayout();
        }
    }

    public void openSettings() {
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).title().equals("Settings")) showPage(i);
    }

    public int pageCount() { return pages.size(); }

    public Page pageAt(int i) { return pages.get(i); }

    public String pageTitle(int i) { return pages.get(i).title(); }

    // ------------------------------------------------------------ state

    public GameVersion version() { return version; }

    public void setVersion(GameVersion v) {
        version = v;
        Profiles.Profile p = Profiles.current();
        p.version = v.id;
        Profiles.save();
        settings.lastVersion = v.id;
        settings.save();
        fireState();
    }

    // ------------------------------------------------------------ profiles

    public Profiles.Profile profile() { return Profiles.current(); }

    public void selectProfile(Profiles.Profile p) {
        Profiles.select(p);
        version = p.gameVersion();
        fireState();
        sidebar.repaint();
        host.fadeIn();
        for (Page pg : pages) if (pg.isVisible()) pg.onShow();
    }

    public void editProfile(Profiles.Profile p) {
        profileEditor.open(p, saved -> {
            if (saved != null) selectProfile(saved);
            else {
                version = Profiles.current().gameVersion();
                fireState();
                sidebar.repaint();
            }
        });
    }

    /** Picks a picture as your own launcher icon and logo. */
    public void chooseAppIcon(Runnable after) {
        Path src = FilePicker.one("Launcher icon", "Pictures", "png", "jpg", "jpeg", "gif", "bmp");
        if (src == null) return;
        try {
            dev.cobra.launcher.core.AppIcon.set(src);
            applyAppIcon();
            toast("Launcher icon set: " + src.getFileName());
            if (after != null) after.run();
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    public void resetAppIcon(Runnable after) {
        try {
            dev.cobra.launcher.core.AppIcon.reset();
            applyAppIcon();
            toast("Back to the Abyss icon.");
            if (after != null) after.run();
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    private void applyAppIcon() {
        frame.setIconImages(dev.cobra.launcher.core.AppIcon.windowIcons());
        frame.repaint();
    }

    /** Opens a file picker and imports an image / GIF / video as the wallpaper. */
    public void chooseWallpaper(Runnable after) {
        Path src = FilePicker.one("Wallpaper: picture, GIF or video", "Pictures and videos",
                "png", "jpg", "jpeg", "gif", "bmp", "webp", "mp4", "webm", "mkv", "mov", "avi");
        if (src == null) return;
        toast(Wallpaper.isVideo(src) ? "Converting " + src.getFileName() + " to an animated wallpaper" : "Setting wallpaper");
        Thread t = new Thread(() -> {
            try {
                Wallpaper.importFile(src, msg -> {});
                SwingUtilities.invokeLater(() -> {
                    Glass.invalidate();
                    frame.repaint();
                    toast("Wallpaper set: " + src.getFileName());
                    if (after != null) after.run();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> toast(e.getMessage()));
            }
        }, "cobra-wallpaper-import");
        t.setDaemon(true);
        t.start();
    }

    public void clearWallpaper() {
        Wallpaper.clear();
        Glass.invalidate();
        frame.repaint();
    }

    public void setWallpaperDim(int dim) {
        settings.wallpaperDim = dim;
        settings.save();
        Wallpaper.touch();
        frame.repaint();
    }

    /** Dragging the slider fires many changes: save each, rebuild the glass at most every 120 ms. */
    private final Timer frostTimer = new Timer(120, e -> {
        Glass.invalidate();
        frame.repaint();
    });

    public void setFrost(int frost) {
        settings.frost = frost;
        settings.save();
        frostTimer.setRepeats(false);
        frostTimer.restart();
    }

    public Account account() { return account; }

    public BufferedImage face() { return face; }

    public boolean running() { return running != null; }

    public void onStateChange(Runnable r) { stateListeners.add(r); }

    private void fireState() {
        for (Runnable r : stateListeners) r.run();
        titleBar.doLayout();
        titleBar.repaint();
    }

    /**
     * Super optimization: VulkanMod (and friends) at launch, see Installer. The launcher's look
     * stays the same (the blur is always on in Abyss; Lite mode is the light launcher).
     */
    public void setSuperOptimization(boolean on) {
        settings.superOptimization = on;
        settings.save();
        Anim.enabled = settings.animations;
        Wallpaper.touch();
        Glass.invalidate();
        root.doLayout();
        frame.repaint();
        toast(on ? "Super optimization on: VulkanMod is added the next time you launch."
                : "Super optimization off: VulkanMod is removed the next time you launch.");
    }

    /**
     * More optimization: no blur, no glass, no animations, a plain single-colour background instead
     * of the wallpaper. Turning it off restores your animations setting.
     */
    public void setMoreOptimization(boolean on) {
        if (on && !settings.moreOptimization) {
            settings.savedAnimationsLite = settings.animations;
            settings.animations = false;
        } else if (!on && settings.moreOptimization) {
            if (settings.savedAnimationsLite != null) settings.animations = settings.savedAnimationsLite;
            settings.savedAnimationsLite = null;
        }
        settings.moreOptimization = on;
        settings.save();
        Anim.enabled = settings.animations;
        Glass.invalidate();
        root.doLayout();
        frame.repaint();
        toast(on ? "More optimization on: plain colours, no blur, no animations." : "More optimization off: your look is back.");
    }

    /**
     * First start on a slower PC: times a few full redraws of the window; if they're slow (or the
     * PC has few cores), More optimization is turned on so the launcher stays smooth. Once only.
     */
    private void speedCheck() {
        if (settings.speedChecked || settings.moreOptimization) return;
        settings.speedChecked = true;
        settings.save();
        int cores = Runtime.getRuntime().availableProcessors();
        long ms;
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(Math.max(1, frame.getWidth()), Math.max(1, frame.getHeight()),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            for (int i = 0; i < 4; i++) frame.getRootPane().paint(g);   // warm-up (the first draws are always slow)
            long t0 = System.nanoTime();
            for (int i = 0; i < 4; i++) frame.getRootPane().paint(g);
            ms = (System.nanoTime() - t0) / 4_000_000;
            g.dispose();
        } catch (Exception e) {
            return;
        }
        if (ms > 120 && cores <= 4 || ms > 250 || cores <= 2) {      // clearly slow, not just a busy moment
            setMoreOptimization(true);
            toast("Your PC is on the slower side, so More optimization is on for a smoother launcher (Settings → Performance).");
        }
    }

    /** Cosmetics changed in game (Right Shift) are written to config/cobra/cosmetics.txt: take them over. */
    public void syncCosmeticsFromGame() {
        try {
            java.nio.file.Path f = dev.cobra.launcher.core.Profiles.gameDir(dev.cobra.launcher.core.Profiles.current(), GameVersion.MODERN)
                    .resolve("config").resolve("cobra").resolve("cosmetics.txt");
            if (!java.nio.file.Files.isRegularFile(f)) return;
            long t = java.nio.file.Files.getLastModifiedTime(f).toMillis();
            if (t <= settings.cosSyncedAt) return;
            settings.applyCosmeticsCode(java.nio.file.Files.readString(f).trim());
            settings.cosSyncedAt = t;
            settings.save();
            for (Page p : pages) if (p.isVisible()) p.onShow();
        } catch (Exception ignored) {}
    }

    /** First start: the beta notice (shown once). */
    private void betaNotice() {
        if (settings.betaNoticeSeen) return;
        choice.askMarked("Warning",
                "This client is still in the beta version. If you experience any bug, please report it to lucawascookin.\n"
                        + "Abyss Launcher used some artificial intelligence for some of the code!\n"
                        + "Shoutout to Mili, mrgetpeaced, initialls, kris, and everyone else for helping me out.",
                "lucawascookin", java.util.List.of("Got it"), 0, i -> {
                    settings.betaNoticeSeen = true;
                    settings.save();
                    passwordQuestion();
                });
    }

    /** First start, after the notice: would you like a password? */
    private void passwordQuestion() {
        if (settings.passwordAsked || dev.cobra.launcher.core.AppLock.enabled()) return;
        settings.passwordAsked = true;
        settings.save();
        SwingUtilities.invokeLater(() -> choice.ask("Set a password?",
                "You can protect Abyss Launcher with a password, so nobody else on this PC can open it. You can change this later in Settings.",
                java.util.List.of("Set a password", "No thanks"), 0, i -> {
                    if (i == 0) LockScreen.showSetup(frame.getRootPane(), null);
                }));
    }

    /** Asks for some text (a link, a name…) with one button. */
    public void askText(String title, String body, String placeholder, String button, java.util.function.Consumer<String> onText) {
        choice.askText(title, body, placeholder, button, onText);
    }

    /** Asks a question with one button per option; {@code onPick} gets the option's index. */
    public void ask(String title, String body, java.util.List<String> options, int primary, java.util.function.IntConsumer onPick) {
        choice.ask(title, body, options, primary, onPick);
    }

    public void openLogin(Runnable after) {
        login.open(acc -> {
            account = acc;
            loadFace();
            fireState();
            toast("Signed in as " + acc.name);
            if (after != null) after.run();
        });
    }

    public void signOut() {
        Account.delete();
        account = null;
        face = null;
        fireState();
        toast("Signed out");
    }

    public void setAnimations(boolean on) {
        Anim.enabled = on;
        settings.animations = on;
        settings.save();
    }

    public void saveSidebar() {
        settings.sidebarExpanded = sidebar.expanded();
        settings.save();
    }

    private void loadFace() {
        Account a = account;
        if (a == null || a.skinUrl == null) return;
        Thread t = new Thread(() -> {
            try {
                Path cache = Paths.CACHE.resolve("skin-" + a.uuid + ".png");
                byte[] bytes;
                if (Files.exists(cache) && System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis() < 86_400_000L) {
                    bytes = Files.readAllBytes(cache);
                } else {
                    bytes = Http.bytes(a.skinUrl);
                    Files.write(cache, bytes);
                }
                BufferedImage skin = ImageIO.read(new ByteArrayInputStream(bytes));
                BufferedImage f = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = f.createGraphics();
                g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
                g.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, null);
                g.dispose();
                SwingUtilities.invokeLater(() -> { face = f; fireState(); });
            } catch (Exception ignored) {}
        }, "cobra-face");
        t.setDaemon(true);
        t.start();
    }

    public static void drawFace(Graphics2D g, BufferedImage face, int x, int y, int size) {
        if (face == null) {
            Theme.fill(g, x, y, size, size, size * 0.28, Theme.RAISED);
            Icons.paint(g, "user", x + size * 0.18, y + size * 0.18, size * 0.64, Theme.SOFT);
            return;
        }
        Theme.roundedImage(g, face, x, y, size, size, size * 0.56, false);
    }

    // ------------------------------------------------------------ popups & toasts

    public void toast(String msg) { toast(msg, null, null); }

    public void toast(String msg, String action, Runnable act) {
        toast.show(msg, action, act);
        layoutOverlays();
    }

    public void showPopup(JComponent c, Rectangle bounds) {
        closePopup();
        JLayeredPane lp = frame.getLayeredPane();
        catcher = new JComponent() {};
        catcher.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { closePopup(); }
        });
        catcher.setBounds(0, 0, frame.getWidth(), frame.getHeight());
        FadeBox box = new FadeBox(c);
        box.setBounds(bounds);
        lp.add(catcher, Integer.valueOf(JLayeredPane.POPUP_LAYER - 1));
        lp.add(box, JLayeredPane.POPUP_LAYER);
        popup = box;
        box.fade.over(1, 160, null);
        lp.revalidate();
        lp.repaint();
    }

    public void closePopup() {
        JLayeredPane lp = frame.getLayeredPane();
        if (popup != null) lp.remove(popup);
        if (catcher != null) lp.remove(catcher);
        popup = catcher = null;
        lp.repaint();
    }

    public static void openUri(String uri) {
        if (uri.startsWith("file:")) {             // local things: file manager / default app, never the browser
            try {
                openPath(Path.of(java.net.URI.create(uri)));
                return;
            } catch (Exception ignored) {}
        }
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(java.net.URI.create(uri));
                    return;
                }
            } catch (Exception ignored) {}
            try {
                new ProcessBuilder(Paths.OS_NAME.equals("osx") ? "open" : "xdg-open", uri).start();
            } catch (Exception ignored) {}
        }, "cobra-open").start();
    }

    /**
     * Opens a folder in the normal file manager (Explorer on Windows) or a file in its default app.
     * Browsing a file: URI made some Windows PCs open a browser or an odd file window instead.
     */
    public static void openPath(Path p) {
        try {
            Files.createDirectories(Files.isDirectory(p) || !Files.exists(p) && !p.toString().contains(".") ? p : p.getParent());
        } catch (Exception ignored) {}
        new Thread(() -> {
            String path = p.toAbsolutePath().toString();
            try {
                if ("windows".equals(Paths.OS_NAME)) {
                    if (Files.isDirectory(p)) new ProcessBuilder("explorer.exe", path).start();
                    else Desktop.getDesktop().open(p.toFile());
                    return;
                }
                if (Paths.OS_NAME.equals("osx")) {
                    new ProcessBuilder("open", path).start();
                    return;
                }
                new ProcessBuilder("xdg-open", path).start();
            } catch (Exception e) {
                try {
                    Desktop.getDesktop().open(p.toFile());
                } catch (Exception ignored) {}
            }
        }, "cobra-open").start();
    }

    // ------------------------------------------------------------ launch

    public void launch() {
        if (running != null) {
            toast("Minecraft is already running.");
            return;
        }
        boolean offline = settings.offline;
        if (!offline && account == null) {
            openLogin(this::launch);
            return;
        }
        GameVersion gv = version;
        Account acc = offline
                ? Account.offline(!settings.offlineName.isBlank() ? settings.offlineName : account != null ? account.name : "Player")
                : account;
        launchOverlay.show(null);
        Thread t = new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> launchOverlay.status("Signing in", -1));
                Account fresh = MicrosoftAuth.ensureFresh(settings.effectiveClientId(), acc);
                if (!fresh.offline()) SwingUtilities.invokeLater(() -> account = fresh);
                Installer.Prepared prep = Installer.prepare(gv, (s, f) -> SwingUtilities.invokeLater(() -> launchOverlay.status(s, f)));
                Wallpaper.exportForGame(prep.gameDir);
                dev.cobra.launcher.core.Accessories.exportForGame(prep.gameDir);   // skin + cape for Cobra Client
                SwingUtilities.invokeLater(() -> launchOverlay.status("Starting Minecraft " + gv.id, -1));
                Process p = GameLauncher.start(gv, prep, fresh, settings);
                running = p;
                dev.cobra.launcher.core.DiscordPresence.playing(prep.gameDir);
                long start = System.currentTimeMillis();
                ScheduledExecutorService cp = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread th = new Thread(r, "cobra-playtime");
                    th.setDaemon(true);
                    return th;
                });
                cp.scheduleAtFixedRate(() -> Playtime.checkpoint(start, gv.id), 1, 1, TimeUnit.MINUTES);
                SwingUtilities.invokeLater(() -> {
                    fireState();
                    Timer hold = new Timer(700, e -> launchOverlay.hide(() -> {
                        if (!prep.cobraBundled && !gv.vanilla()) toast("Abyss Client isn't available for " + gv.id + " yet. Launched Fabric with your mods.");
                        if (prep.notice != null) toast(prep.notice);
                        if (!settings.keepOpen) frame.setState(Frame.ICONIFIED);
                    }));
                    hold.setRepeats(false);
                    hold.start();
                });
                p.onExit().thenAccept(proc -> {
                    cp.shutdownNow();
                    Playtime.finish(start, gv.id);
                    running = null;
                    dev.cobra.launcher.core.DiscordPresence.idle();
                    int code = proc.exitValue();
                    long secs = (System.currentTimeMillis() - start) / 1000;
                    SwingUtilities.invokeLater(() -> {
                        frame.setState(Frame.NORMAL);
                        frame.toFront();
                        syncCosmeticsFromGame();            // what you changed in game shows here too
                        fireState();
                        if (code != 0 && code != 130 && code != 143) {
                            toast("Minecraft closed with error " + code + (secs < 60 ? " right after starting." : "."),
                                    "Open log", () -> openPath(GameLauncher.logFile(gv)));
                        }
                    });
                });
            } catch (MicrosoftAuth.AuthException e) {
                SwingUtilities.invokeLater(() -> launchOverlay.hide(() -> {
                    if (e.getMessage().contains("Sign in again") || e.getMessage().contains("session expired")) {
                        // a borrowed Lunar / Prism / … session ran out: switch to Cobra's own Microsoft sign-in
                        Account.delete();
                        account = null;
                        fireState();
                        toast(e.getMessage(), "Sign in", () -> openLogin(this::launch));
                    } else toast(e.getMessage());
                }));
            } catch (Exception e) {
                e.printStackTrace();
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                SwingUtilities.invokeLater(() -> launchOverlay.hide(() -> toast("Launch failed: " + msg)));
            }
        }, "cobra-launch");
        t.setDaemon(true);
        t.start();
    }

    private void quit() {
        if (running != null) {
            // keep the game running; the launcher can close safely
            Playtime.checkpoint(System.currentTimeMillis(), version.id);
        }
        settings.saveNow();
        frame.dispose();
        System.exit(0);
    }

    // ================================================================ parts

    /** Gap between the window edge and the frame. */
    static final int FRAME = 8;

    private final class Root extends JPanel {
        private BufferedImage bg, scrim;
        private boolean bgLight, scrimLight;
        private String bgKey = "";

        /**
         * Readability over wallpapers: darker along the top (title bar, page headings) and at the
         * edges, clear in the middle. White instead of black in Light mode. Cached per size/theme.
         */
        private BufferedImage scrim(int w, int h) {
            boolean light = Theme.isLight();
            if (scrim != null && scrim.getWidth() == w && scrim.getHeight() == h && scrimLight == light) return scrim;
            scrim = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            scrimLight = light;
            Graphics2D b = scrim.createGraphics();
            Color c0 = light ? new Color(250, 248, 246, 0) : new Color(0, 0, 0, 0);
            Color top = light ? new Color(250, 248, 246, 150) : new Color(0, 0, 0, 150);
            Color edge = light ? new Color(250, 248, 246, 110) : new Color(0, 0, 0, 120);
            b.setPaint(new GradientPaint(0, 0, top, 0, 170, c0));
            b.fillRect(0, 0, w, 170);
            b.setPaint(new RadialGradientPaint(new Point.Double(w * 0.55, h * 0.48), (float) (Math.hypot(w, h) * 0.62),
                    new float[]{0.55f, 1f}, new Color[]{c0, edge}));
            b.fillRect(0, 0, w, h);
            b.dispose();
            return scrim;
        }

        Root() {
            super(null);
            setOpaque(true);
            setBackground(Theme.BLACK);
        }

        @Override
        public void doLayout() {
            int w = getWidth(), h = getHeight();
            // one frame with a rail on the left; the title pill (clock, account, window buttons) top right
            int in = FRAME + 12;
            int nav = settings.legacyGui ? Rail.WIDTH : sidebar.currentWidth();
            sidebar.setBounds(in, in, sidebar.currentWidth(), h - 2 * in);
            rail.setBounds(in, in, Rail.WIDTH, h - 2 * in);
            int x = in + nav + (settings.legacyGui ? 12 : 14);
            titleBar.setBounds(x, in, w - x - in, 48);
            boolean home = pages.get(0).isVisible();
            if (home) host.setBounds(x, in, w - x - in, h - 2 * in);              // Home arranges itself round the pill
            else host.setBounds(x + 6, in + 62, w - x - in - 12, h - 2 * in - 68);
            host.doLayout();
        }

        private String frameKey;
        private java.awt.image.BufferedImage frameImage;

        /** Background, scrim and the glass frame (or the classic sheet). */
        private void paintFrame(Graphics2D g, int w, int h) {
            Glass.paintBackground(g, w, h);
            if (Wallpaper.active() && !Glass.lite()) g.drawImage(scrim(w, h), 0, 0, null);
            Glass.surface(g, this, FRAME, FRAME, w - 2 * FRAME, h - 2 * FRAME, 30, 0);

        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Rectangle shape = new Rectangle(0, 0, w, h);
            if (Wallpaper.active() && !Glass.on()) {
                Wallpaper.paint(g, w, h);
                g.drawImage(scrim(w, h), 0, 0, null);
                    g.dispose();
                return;
            }
            if (Glass.on()) {
                // the background + frame only change with the window size, the wallpaper or the look:
                // draw them once into an image and reuse it (most repaints are just a hover)
                boolean live = Wallpaper.active() && Wallpaper.animated() && !Glass.lite();
                var cs = settings;
                String fk = w + "x" + h + "|" + Wallpaper.version() + "|" + Theme.isLight() + "|" + cs.theme + "|" + cs.style + "|" + cs.glassLook
                        + "|" + cs.frost + "|" + cs.glassTint + "|" + cs.glassBorder + "|" + cs.roundness + "|" + Glass.lite()
                        + "|" + pages.get(0).isVisible() + "|" + Theme.gradientKey() + "|" + cs.wallpaperDim;
                if (!live && fk.equals(frameKey) && frameImage != null) {
                    g.drawImage(frameImage, 0, 0, null);
                    g.dispose();
                    return;
                }
                Graphics2D target = g;
                if (!live) {
                    if (frameImage == null || frameImage.getWidth() != w || frameImage.getHeight() != h) {
                        frameImage = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    }
                    target = Theme.aa(frameImage.createGraphics());
                    target.setComposite(AlphaComposite.Clear);
                    target.fillRect(0, 0, w, h);
                    target.setComposite(AlphaComposite.SrcOver);
                }
                paintFrame(target, w, h);
                if (!live) {
                    target.dispose();
                    frameKey = fk;
                    g.drawImage(frameImage, 0, 0, null);
                }
                g.dispose();
                return;
            }
            if (false) {
                Glass.paintBackground(g, w, h);
                if (Wallpaper.active() && !Glass.lite()) g.drawImage(scrim(w, h), 0, 0, null);
                Glass.surface(g, this, FRAME, FRAME, w - 2 * FRAME, h - 2 * FRAME, 30, 0);
                    g.dispose();
                return;
            }
            // solid background: the radial gradient is slow to rasterise, so draw it once per size/theme
            String key = Theme.isLight() + "|" + Theme.gradientKey();
            if (bg == null || bg.getWidth() != w || bg.getHeight() != h || !key.equals(bgKey)) {
                bg = getGraphicsConfiguration().createCompatibleImage(w, h, Transparency.OPAQUE);
                bgKey = key;
                Graphics2D b = Theme.aa(bg.createGraphics());
                if (Theme.paintGradient(b, w, h)) {     // your own gradient (Settings → Appearance)
                    b.dispose();
                    g.drawImage(bg, 0, 0, null);
                    g.dispose();
                    return;
                }
                b.setColor(Theme.BLACK);
                b.fill(shape);
                float[] fr = {0f, 0.55f, 1f};
                Color[] c = {Theme.GLOW, Theme.EDGE, Theme.BLACK};
                b.setPaint(new RadialGradientPaint(new Point.Double(w * 0.6, h * 0.34), (float) (h * 0.95), fr, c));
                b.fill(shape);
                b.setPaint(new GradientPaint(0, h * 0.6f, Theme.alpha(Theme.BLACK, 0), 0, h, Theme.alpha(Theme.PANEL, 0.35)));
                b.fill(shape);
                b.dispose();
            }
            g.drawImage(bg, 0, 0, null);
            g.dispose();
        }
    }

    private final class TitleBar extends JComponent {
        private Point drag;
        private final Components.IconButton min = new Components.IconButton("minimize", 18, () -> frame.setState(Frame.ICONIFIED));
        private final Components.IconButton close = new Components.IconButton("close", 18, MainWindow.this::quit);
        private final AccountChip chip = new AccountChip();
        private final Clock clock = new Clock();

        TitleBar() {
            setLayout(null);
            add(min);
            add(close);
            add(chip);
            add(clock);
            close.hoverColor(Theme.DANGER);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { drag = e.getPoint(); }
                @Override public void mouseDragged(MouseEvent e) {
                    if (drag == null) return;
                    Point p = frame.getLocation();
                    frame.setLocation(p.x + e.getX() - drag.x, p.y + e.getY() - drag.y);
                }
                @Override public void mouseReleased(MouseEvent e) { drag = null; }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        /** Width of the pill on the right (Home lines its right column up with it). */
        static final int PILL = 380;

        @Override
        public void doLayout() {
            int w = getWidth();
            int cw = chip.getPreferredSize().width;
            close.setBounds(w - 42, 7, 34, 34);
            min.setBounds(w - 78, 7, 34, 34);
            chip.setBounds(w - 92 - cw, 6, cw, 36);
            clock.setBounds(w - PILL + 16, 4, 150, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth();
            Theme.gloss(g, w - PILL, 0, PILL, 48, 24, 0);              // the top-right pill
            g.dispose();
        }
    }

    private final class AccountChip extends Components.Interactive {
        AccountChip() {
            onClick(() -> {
                if (account == null) {
                    openLogin(null);
                    return;
                }
                JComponent menu = new JComponent() {};
                menu.setLayout(null);
                Components.Button out = new Components.Button("Sign out", "logout", Components.Variant.GHOST, () -> { closePopup(); signOut(); });
                Components.Button set = new Components.Button("Account settings", "settings", Components.Variant.GHOST, () -> { closePopup(); openSettings(); });
                JComponent card = new Components.Card(null, 16).solid();
                card.add(set);
                card.add(out);
                set.setBounds(10, 10, 200, 38);
                out.setBounds(10, 54, 200, 38);
                menu.add(card);
                card.setBounds(0, 0, 220, 102);
                Point p = SwingUtilities.convertPoint(this, 0, getHeight() + 8, frame.getLayeredPane());
                showPopup(menu, new Rectangle(p.x + getWidth() - 220, p.y, 220, 102));
            });
        }

        @Override
        public Dimension getPreferredSize() {
            String name = account == null ? "Sign in" : account.name;
            return new Dimension(getFontMetrics(Theme.font(Theme.MEDIUM, 13.5f)).stringWidth(name) + 56, 36);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, h / 2.0, hover.get(), 0.3);
            drawFace(g, account == null ? null : face, 6, 6, 24);
            Theme.left(g, account == null ? "Sign in" : account.name, Theme.font(Theme.MEDIUM, 13.5f), Theme.TEXT, 40, 0, h);
            g.dispose();
        }
    }

    /**
     * Holds the pages. Switching pages shows the new page right away and fades/lifts it in as one
     * piece through Swing's normal painting: no snapshots, no manual child painting, so nothing
     * can leave ghost copies behind and it costs nothing extra.
     */
    private static final class PageHost extends JPanel {
        private final Anim.Tween fade = new Anim.Tween(this, 1);

        PageHost() {
            super(null);
            setOpaque(false);
        }

        void fadeIn() {
            fade.set(0);
            fade.over(1, 260, null);
        }

        void transition(Page from, Page to) {
            from.setVisible(false);
            to.setVisible(true);
            to.setBounds(0, 0, getWidth(), getHeight());
            to.doLayout();
            to.validate();
            to.onShow();
            fade.set(0);
            fade.over(1, 260, this::repaint);
        }

        @Override
        public void doLayout() {
            for (Component c : getComponents()) {
                c.setBounds(0, 0, getWidth(), getHeight());
                c.doLayout();
            }
        }

        @Override
        protected void paintChildren(Graphics g0) {
            double a = fade.get();
            if (a >= 0.999) {
                super.paintChildren(g0);
                return;
            }
            double e = 1 - Math.pow(1 - a, 3);
            Graphics2D g = (Graphics2D) g0.create();
            g.translate(0, (int) Math.round(12 * (1 - e)));
            g.setComposite(Theme.fade(Math.max(0.01, e)));
            super.paintChildren(g);
            g.dispose();
        }
    }

    /** Wraps a popup so it fades in as one piece (children included). */
    private static final class FadeBox extends JPanel {
        final Anim.Tween fade = new Anim.Tween(this, 0);

        FadeBox(JComponent content) {
            super(null);
            setOpaque(false);
            add(content);
        }

        @Override
        public void doLayout() {
            for (Component c : getComponents()) c.setBounds(0, 0, getWidth(), getHeight());
        }

        @Override
        public void paint(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setComposite(Theme.fade(Math.max(0.01, fade.get())));
            super.paint(g);
            g.dispose();
        }
    }

    public static File defaultDir() {
        return Paths.ROOT.toFile();
    }
}
