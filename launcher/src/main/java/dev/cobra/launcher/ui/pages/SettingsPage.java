package dev.cobra.launcher.ui.pages;

import dev.cobra.launcher.core.Paths;
import dev.cobra.launcher.core.Settings;
import dev.cobra.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class SettingsPage extends Page {
    private final Settings s = Settings.get();
    private final Components.Stack stack = new Components.Stack(14);
    private final JScrollPane scroll = Components.scroll(stack);
    private final Components.Button accountButton;

    public SettingsPage() {
        add(scroll);

        // ---------------------------------------------------------------- Game
        Components.Slider ram = new Components.Slider(1024, roundDown(Settings.maxRamMb()), 512, s.ramMb,
                v -> String.format("%.1f GB", v / 1024.0), v -> { s.ramMb = v.intValue(); s.save(); });

        JPanel size = new JPanel(null);
        size.setOpaque(false);
        Components.Input width = new Components.Input(String.valueOf(s.width), "1280", null);
        Components.Input height = new Components.Input(String.valueOf(s.height), "720", null);
        width.onChange(() -> { s.width = parse(width.getText(), 1280); s.save(); });
        height.onChange(() -> { s.height = parse(height.getText(), 720); s.save(); });
        Components.Text x = new Components.Text("×", Theme.font(Theme.REGULAR, 15f), Theme.MUTED);
        size.add(width);
        size.add(x);
        size.add(height);
        width.setBounds(0, 0, 92, 40);
        x.setBounds(102, 0, 14, 40);
        height.setBounds(124, 0, 92, 40);

        Components.Toggle fullscreen = new Components.Toggle(s.fullscreen, v -> { s.fullscreen = v; s.save(); });
        Components.Input jvm = new Components.Input(s.jvmArgs, "e.g. -XX:+UseZGC", null);
        jvm.onChange(() -> { s.jvmArgs = jvm.getText(); s.save(); });

        Components.Toggle[] vulkanRef = new Components.Toggle[1];
        vulkanRef[0] = new Components.Toggle(s.superOptimization, v -> {
            if (v) {
                vulkanRef[0].setOn(false, false);   // only after the warning is accepted
                MainWindow.get().ask("Super optimization (Vulkan)",
                        "Adds VulkanMod: a new graphics engine with much higher FPS on many PCs.\n"
                        + "It does NOT work together with: Sodium, Iris / shaders, OptiFine, Embeddium, Indium, "
                        + "Continuity, Distant Horizons, Nvidium, Canvas, Immersive Portals, Replay Mod and other "
                        + "mods that change rendering. Remove those, or keep this off.\n"
                        + "While it's on, the launcher also saves power: Solid style, no animations, still wallpaper. "
                        + "Turning it off puts everything back.",
                        List.of("Turn it on anyway"), 0, i -> {
                            vulkanRef[0].setOn(true, false);
                            MainWindow.get().setSuperOptimization(true);
                            refilter();
                        });
            } else {
                MainWindow.get().setSuperOptimization(false);
                refilter();
            }
        });
        Components.Toggle vulkan = vulkanRef[0];
        Components.Toggle lite = new Components.Toggle(s.moreOptimization, v -> MainWindow.get().setMoreOptimization(v));
        stack.add(new Section("Performance", new Row[]{
                new Row("More optimization", "Plain colours instead of wallpaper and glass, no blur, no animations. The lightest launcher.", lite, 50),
                new Row("Super optimization (Vulkan)", "VulkanMod + EntityCulling, FerriteCore, MoreCulling, Clumps. Also turns off costly launcher effects. Off undoes it all.", vulkan, 50)}));
        stack.add(new Section("Game", new Row[]{
                new Row("Memory", "Maximum RAM Minecraft may use. 4–6 GB suits most setups.", ram, 380),
                new Row("Window size", "Starting size of the game window.", size, 216),
                new Row("Fullscreen", "Start the game in fullscreen.", fullscreen, 50),
                new Row("Java arguments", "Extra JVM flags, added after the defaults.", jvm, 380)}));

        // ---------------------------------------------------------- Appearance
        Components.Toggle lightMode = new Components.Toggle(s.lightMode, v -> MainWindow.get().setLight(v));
        Components.Segmented style = new Components.Segmented(List.of("Solid", "Glass"), "glass".equals(s.style) ? 1 : 0,
                i -> {
                    MainWindow.get().setStyle(i == 1 ? "glass" : "solid");
                    refilter();           // glass-only options come and go
                });
        Components.Slider frost = new Components.Slider(0, 100, 5, s.frost, v -> Math.round(v) + "%",
                v -> MainWindow.get().setFrost(v.intValue()));
        Components.Toggle anim = new Components.Toggle(s.animations, v -> MainWindow.get().setAnimations(v));
        WallpaperRow wallpaperRow = new WallpaperRow();
        Components.Slider dim = new Components.Slider(0, 90, 5, s.wallpaperDim, v -> Math.round(v) + "%",
                v -> MainWindow.get().setWallpaperDim(v.intValue()));
        stack.add(new Section("Wallpaper & colours", new Row[]{
                wallpaperRow,
                new AccentRow(),
                gradientRow(s),
                gradientColorsRow(s),
                gradientStrengthRow(s),
                new Row("Tint wallpaper", "Apply the custom gradient to your wallpaper.", new Components.Toggle(s.wallpaperTint, v -> {
                    s.wallpaperTint = v; s.save(); Wallpaper.touch(); restyle();
                }), 50),
                new IconRow(),
                new Row("Wallpaper dim", "Darkens the wallpaper so text stays readable (lightens in Light mode).", dim, 300)}));
        Components.Segmented gui = new Components.Segmented(List.of("New", "Classic"), "classic".equals(s.launcherGui) ? 1 : 0,
                i -> MainWindow.get().setLauncherGui(i == 1 ? "classic" : "new"));
        stack.add(new Section("Look", new Row[]{
                new Row("Launcher GUI", "New: the dashboard with the round rail. Classic: the previous look with the wide side panel.", gui, 250),
                new Row("Light mode", "Flips the palette: white surfaces, black text and buttons. Also used in game.", lightMode, 50),
                new Row("Style", "Glass: clear panels with a shine. Solid: darker, more blurred panels, calmer to read.", style, 250),
                new Row("Glass look", "Clear: sharp glass that bends at the edges. Frosted: soft and calm.", new Components.Segmented(
                        List.of("Frosted", "Clear"), "liquid".equals(s.glassLook) ? 1 : 0, i -> {
                            s.glassLook = i == 1 ? "liquid" : "frosted";
                            s.save();
                            Glass.invalidate();
                            MainWindow.get().frame.repaint();
                        }), 250).when(() -> "glass".equals(Settings.get().style)),
                new Row("Glass frost", "How much the glass blurs what's behind it. Low = clearer, more liquid.", frost, 300)
                        .when(() -> "glass".equals(Settings.get().style)),
                new Row("Animations", "Fades, sliding and transitions. Turn off for instant switching.", anim, 50)}));

        // ------------------------------------------------------------ Launcher
        Components.Toggle keep = new Components.Toggle(s.keepOpen, v -> { s.keepOpen = v; s.save(); });
        // --------------------------------------------------------------- Developer (Swipecz only)
        dev.cobra.launcher.auth.Account me = dev.cobra.launcher.auth.Account.load();
        if (me != null && me.name != null && me.name.equalsIgnoreCase("Swipecz")) {
            Components.Button publish = new Components.Button("Publish update", "download", Components.Variant.PRIMARY, null);
            publish.onClick(() -> {
                MainWindow.get().toast("Asking GitHub to build and publish…");
                new Thread(() -> {
                    String msg = dev.cobra.launcher.core.Updater.publish();
                    SwingUtilities.invokeLater(() -> MainWindow.get().toast(msg));
                }, "cobra-publish").start();
            });
            stack.add(new Section("Developer", new Row[]{
                    new Row("Publish update to everyone", "Builds the code on GitHub and releases it. Everyone gets it (Linux and Windows) the next time they reopen the launcher. Build "
                            + dev.cobra.launcher.core.Updater.BUILD + (dev.cobra.launcher.core.Updater.REPO.isEmpty() ? "" : " · " + dev.cobra.launcher.core.Updater.REPO) + ".", publish, 190)}));
        }

        Components.Button importBtn = new Components.Button("Import", "download", Components.Variant.GHOST, SettingsPage::importProfile);
        Components.Button lockBtn = new Components.Button(dev.cobra.launcher.core.AppLock.enabled() ? "Change" : "Set", "user", Components.Variant.GHOST, null);
        Components.Button unlockBtn = new Components.Button("Remove", "close", Components.Variant.GHOST, null);
        lockBtn.onClick(() -> LockScreen.showSetup(MainWindow.get().frame.getRootPane(), () -> {
            lockBtn.setText("Change");
            unlockBtn.setEnabled(dev.cobra.launcher.core.AppLock.enabled());
        }));
        unlockBtn.setEnabled(dev.cobra.launcher.core.AppLock.enabled());
        unlockBtn.onClick(() -> {
            try {
                dev.cobra.launcher.core.AppLock.remove();
            } catch (Exception ignored) {}
            lockBtn.setText("Set");
            unlockBtn.setEnabled(false);
            MainWindow.get().toast("Password removed.");
        });
        JPanel lockBox = new JPanel(null);
        lockBox.setOpaque(false);
        lockBox.add(lockBtn);
        lockBox.add(unlockBtn);
        lockBtn.setBounds(0, 0, 110, 40);
        unlockBtn.setBounds(118, 0, 112, 40);
        Components.Button updateBtn = new Components.Button("Update", "download", Components.Variant.PRIMARY, null);
        updateBtn.onClick(() -> {
            updateBtn.setEnabled(false);
            MainWindow.get().toast("Checking for a newer Cobra…");
            new Thread(() -> {
                String msg = dev.cobra.launcher.core.Updater.checkNow();
                SwingUtilities.invokeLater(() -> {
                    updateBtn.setEnabled(true);
                    MainWindow.get().toast(msg);
                });
            }, "cobra-check-update").start();
        });
        stack.add(new Section("Launcher", new Row[]{
                new Row("Launcher password", "Asked when Cobra Launcher opens, so nobody else on this PC can use it.", lockBox, 230),
                new Row("Update to the newest", "Gets the latest Cobra Launcher and Client (build " + dev.cobra.launcher.core.Updater.BUILD
                        + "). It's used the next time you open the launcher.", updateBtn, 150),
                new Row("Import a profile", "From Lunar, Dawn, Prism, MultiMC, Modrinth App, CurseForge, ATLauncher or Minecraft: options, mods, packs.", importBtn, 150),
                new Row("Keep launcher open", "Stay on screen while you play instead of minimizing.", keep, 50)}));

        // ------------------------------------------------------------- Account
        accountButton = new Components.Button("", null, Components.Variant.GHOST, null);
        accountButton.onClick(() -> {
            MainWindow mw = MainWindow.get();
            if (mw.account() == null) mw.openLogin(null);
            else mw.signOut();
        });
        Components.Toggle offline = new Components.Toggle(s.offline, v -> {
            s.offline = v;
            s.save();
            MainWindow.get().toast(v ? "No account: launches offline, singleplayer only." : "Signed-in play is back on.");
        });
        Components.Input offlineName = new Components.Input(s.offlineName, "Player name for offline play", null);
        offlineName.onChange(() -> { s.offlineName = offlineName.getText().trim(); s.save(); });
        stack.add(new Section("Account", new Row[]{
                new AccountRow(accountButton),
                new Row("No account", "Skip sign-in and play offline. Singleplayer only, servers are disabled.", offline, 50),
                new Row("Offline name", "Name used in No account mode.", offlineName, 240)}));

        // --------------------------------------------------------------- Discord
        Components.Toggle rpc = new Components.Toggle(s.discordRpc, v -> {
            s.discordRpc = v;
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
            if (v && !dev.cobra.launcher.core.DiscordPresence.enabled())
                MainWindow.get().toast("Paste your Discord application ID below to turn it on (see README).");
        });
        Components.Toggle showServer = new Components.Toggle(s.discordShowServer, v -> {
            s.discordShowServer = v;
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
        });
        Components.Input appId = new Components.Input(s.discordAppId, "Discord application ID", null);
        appId.onChange(() -> {
            s.discordAppId = appId.getText().trim();
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
        });
        Components.Input dDetails = new Components.Input(s.discordDetails, "Automatic (e.g. Playing Minecraft 1.21.11)", null);
        dDetails.onChange(() -> {
            s.discordDetails = dDetails.getText();
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
        });
        Components.Input dState = new Components.Input(s.discordState, "Automatic (e.g. On 65.109.88.105)", null);
        dState.onChange(() -> {
            s.discordState = dState.getText();
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
        });
        Components.Toggle dTime = new Components.Toggle(s.discordShowTime, v -> {
            s.discordShowTime = v;
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
        });
        Components.Toggle dProfile = new Components.Toggle(s.discordShowProfile, v -> {
            s.discordShowProfile = v;
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
        });
        Components.Toggle dActivity = new Components.Toggle(s.discordShowActivity, v -> {
            s.discordShowActivity = v;
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
        });
        Components.Toggle dSmall = new Components.Toggle(s.discordSmallIcon, v -> {
            s.discordSmallIcon = v;
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
        });
        Components.Toggle dButton = new Components.Toggle(s.discordButton, v -> {
            s.discordButton = v;
            s.save();
            dev.cobra.launcher.core.DiscordPresence.refresh();
        });
        stack.add(new Section("Discord", new Row[]{
                new Row("Rich Presence", "Shows \"Playing Cobra Client\" on your Discord profile, with what you're doing.", rpc, 50),
                new Row("Show server", "Adds the server you're on (e.g. \"On mc.eclypse.net\"). Off shows just \"Multiplayer\".", showServer, 50),
                new Row("Top line", "Your own text instead of the automatic one. Empty = automatic.", dDetails, 300),
                new Row("Bottom line", "Your own text instead of what you're doing. Empty = automatic.", dState, 300),
                new Row("Show what you're doing", "In the menus / Playing singleplayer / On a server. Off hides the second line.", dActivity, 50),
                new Row("Status icon", "The small round icon (launcher, menus, singleplayer, server) on the Cobra logo.", dSmall, 50),
                new Row("Show play time", "The \"elapsed\" timer on your status.", dTime, 50),
                new Row("Show profile name", "Adds the Cobra profile you're playing.", dProfile, 50),
                new Row("\"Get Cobra Client\" button", "A button on your status so friends can download it.", dButton, 50)}));

        // --------------------------------------------------------------- Files
        Components.Button open = new Components.Button("Open folder", "folder", Components.Variant.GHOST, () -> MainWindow.openPath(Paths.ROOT));
        stack.add(new Section("Files", new Row[]{
                new Row("Game files", Paths.ROOT.toString(), open, 150)}));

        // order: what people change most first
        String[] order = {"Account", "Performance", "Game", "Wallpaper & colours", "Look", "Discord", "Launcher", "Files", "Developer"};
        List<Component> sections = new java.util.ArrayList<>(List.of(stack.getComponents()));
        stack.removeAll();
        for (String t : order) {
            for (Component c : sections) if (c instanceof Section sec && sec.title.equals(t)) stack.add(c);
        }
        for (Component c : sections) if (c.getParent() == null) stack.add(c);

        search.onChange(() -> {
            query = search.getText();
            refilter();
        });
        add(search);
        instance = this;
        refilter();

        SwingUtilities.invokeLater(() -> MainWindow.get().onStateChange(this::updateAccount));
        updateAccount();
    }

    private final Components.Input search = new Components.Input("", "Search settings", "search");
    private static String query = "";
    private static SettingsPage instance;

    /** Re-applies the search and the "only with…" conditions. */
    public static void refilter() {
        SettingsPage p = instance;
        if (p == null) return;
        for (Component c : p.stack.getComponents()) if (c instanceof Section sec) sec.filter(query);
        p.scroll.getVerticalScrollBar().setValue(0);   // results start at the top
        p.stack.revalidate();
        p.stack.doLayout();
        p.stack.repaint();
    }

    /** For the Profiles page's Import button. */
    public static void importProfileNow() {
        importProfile();
    }

    /** Settings → Import a profile: pick the launcher, then the profile. */
    private static void importProfile() {
        List<dev.cobra.launcher.core.ProfileImport.Source> found = dev.cobra.launcher.core.ProfileImport.detect();
        if (found.isEmpty()) {
            MainWindow.get().toast("No other launchers found on this PC (Prism, MultiMC, Modrinth App, CurseForge, ATLauncher, Minecraft).");
            return;
        }
        List<String> names = new java.util.ArrayList<>();
        for (var src : found) names.add(src.name() + "  (" + src.profiles().size() + ")");
        MainWindow.get().ask("Import a profile", "Which launcher is your profile in?", names, -1, i -> {
            var src = found.get(i);
            List<String> profs = new java.util.ArrayList<>();
            for (var f : src.profiles()) profs.add(f.name());
            SwingUtilities.invokeLater(() -> MainWindow.get().ask("Import from " + src.name(),
                    "Its options.txt, mods, resource packs, shader packs and configs are copied into a new Cobra profile. "
                            + "Mods made for another Minecraft version may need updating.",
                    profs, -1, j -> {
                        var f = src.profiles().get(j);
                        MainWindow.get().toast("Importing " + f.name() + "…");
                        new Thread(() -> {
                            try {
                                var p = dev.cobra.launcher.core.ProfileImport.importInto(f);
                                SwingUtilities.invokeLater(() -> {
                                    dev.cobra.launcher.core.Profiles.select(p);
                                    MainWindow.get().frame.repaint();
                                    MainWindow.get().toast("Imported \"" + f.name() + "\" as a new profile.");
                                });
                            } catch (Exception ex) {
                                SwingUtilities.invokeLater(() -> MainWindow.get().toast("Import failed: " + ex.getMessage()));
                            }
                        }, "cobra-import").start();
                    }));
        });
    }

    private void updateAccount() {
        MainWindow mw = MainWindow.get();
        boolean in = mw != null && mw.account() != null;
        accountButton.setText(in ? "Sign out" : "Sign in");
        stack.repaint();
    }

    private static int roundDown(int mb) {
        return Math.max(2048, mb / 512 * 512);
    }

    private static int parse(String t, int def) {
        try {
            return Math.max(0, Integer.parseInt(t.trim()));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void onShow() {
        stack.animateIn();
    }

    @Override public String title() { return "Settings"; }
    @Override public String icon() { return "settings"; }

    @Override
    public void doLayout() {
        search.setBounds(Math.max(260, getWidth() - 280), 8, Math.min(280, getWidth() - 260), 40);
        scroll.setBounds(0, 76, getWidth() + 10, getHeight() - 76);
        stack.doLayout();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.left(g, "Settings", Theme.font(Theme.REGULAR, 34f), Theme.TEXT, 0, 0, 42);
        Theme.left(g, "Changes save automatically and apply the next time you launch.", Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, 44, 22);
        g.dispose();
    }

    private static class Row extends JComponent {
        final String title, desc;
        final JComponent control;
        final int controlW;
        /** Shown only while this holds (e.g. glass options only with the Glass style). */
        java.util.function.BooleanSupplier showIf;

        Row when(java.util.function.BooleanSupplier c) {
            showIf = c;
            return this;
        }

        boolean matches(String q) {
            if (showIf != null && !showIf.getAsBoolean()) return false;
            if (q == null || q.isBlank()) return true;
            String t = (title + " " + desc).toLowerCase();
            for (String word : q.toLowerCase().trim().split("\\s+")) if (!t.contains(word)) return false;
            return true;
        }

        Row(String title, String desc, JComponent control, int controlW) {
            this.title = title;
            this.desc = desc;
            this.control = control;
            this.controlW = controlW;
            setLayout(null);
            if (control != null) add(control);
        }

        int height() { return 72; }

        @Override
        public void doLayout() {
            if (control == null) return;
            int ch = control instanceof Components.Toggle ? 28 : 40;
            control.setBounds(getWidth() - controlW - 22, (getHeight() - ch) / 2, controlW, ch);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            Theme.left(g, title, Theme.font(Theme.MEDIUM, 14.5f), Theme.TEXT, 22, 14, 22);
            g.setFont(Theme.font(Theme.REGULAR, 12.5f));
            int max = getWidth() - controlW - 70;
            Theme.left(g, Theme.ellipsize(desc, g.getFontMetrics(), max), Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 22, 36, 20);
            g.dispose();
        }
    }

    /** Wallpaper: shows the current file, Choose… and Remove. */
    private static final class WallpaperRow extends Row {
        private final Components.Button choose, remove;

        WallpaperRow() {
            super("Wallpaper", "", new JPanel(null), 250);
            JPanel box = (JPanel) control;
            box.setOpaque(false);
            choose = new Components.Button("Choose", "folder", Components.Variant.GHOST, null);
            remove = new Components.Button("Remove", "trash", Components.Variant.GHOST, null);
            choose.onClick(() -> MainWindow.get().chooseWallpaper(this::repaint));
            remove.onClick(() -> {
                MainWindow.get().clearWallpaper();
                repaint();
            });
            box.add(choose);
            box.add(remove);
            choose.setBounds(0, 0, 120, 40);
            remove.setBounds(130, 0, 120, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            Settings st = Settings.get();
            String desc = st.wallpaperType.isEmpty() ? "Image, GIF or video (MP4, WebM, MKV) behind the launcher and the in-game menu."
                    : (st.wallpaperType.equals("video") ? "Animated: " : "Image: ") + st.wallpaperName;
            Theme.left(g, "Wallpaper", Theme.font(Theme.MEDIUM, 14.5f), Theme.TEXT, 22, 14, 22);
            g.setFont(Theme.font(Theme.REGULAR, 12.5f));
            Theme.left(g, Theme.ellipsize(desc, g.getFontMetrics(), getWidth() - 320), Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 22, 36, 20);
            g.dispose();
        }
    }

    private static void restyle() {
        Theme.applyAccent();
        Glass.invalidate();
        MainWindow.get().frame.repaint();
    }

    private static Integer parseHex(String t) {
        String h = t.trim().replace("#", "");
        if (!h.matches("[0-9a-fA-F]{6}")) return null;
        return Integer.parseInt(h, 16);
    }

    private static Row gradientRow(dev.cobra.launcher.core.Settings s) {
        Components.Toggle t = new Components.Toggle(s.gradient, v -> {
            s.gradient = v;
            s.save();
            restyle();
        });
        return new Row("Background gradient", "Your own two colours: the whole background, or a tint over your wallpaper.", t, 50);
    }

    private static Row gradientStrengthRow(dev.cobra.launcher.core.Settings s) {
        Components.Slider sl = new Components.Slider(0, 100, 5, s.gradientStrength, v -> Math.round(v) + "%", v -> {
            s.gradientStrength = (int) Math.round(v);
            s.save();
            Wallpaper.touch();
            restyle();
        });
        return new Row("Gradient over wallpaper", "How strongly the gradient tints your wallpaper.", sl, 300);
    }

    private static Row gradientColorsRow(dev.cobra.launcher.core.Settings s) {
        JPanel box = new JPanel(null);
        box.setOpaque(false);
        Components.Input a = new Components.Input(String.format("#%06X", s.gradientA & 0xFFFFFF), "#1B1B3A", null);
        Components.Input b = new Components.Input(String.format("#%06X", s.gradientB & 0xFFFFFF), "#0B0B0D", null);
        int[] angles = {135, 90, 45, 0};
        int idx = 0;
        for (int i = 0; i < angles.length; i++) if (angles[i] == s.gradientAngle) idx = i;
        Components.Segmented dir = new Components.Segmented(java.util.List.of("\u2198", "\u2193", "\u2197", "\u2192"), idx, i -> {
            s.gradientAngle = angles[i];
            s.save();
            restyle();
        });
        a.onChange(() -> {
            Integer c = parseHex(a.getText());
            if (c != null) {
                s.gradientA = c;
                s.save();
                restyle();
            }
        });
        b.onChange(() -> {
            Integer c = parseHex(b.getText());
            if (c != null) {
                s.gradientB = c;
                s.save();
                restyle();
            }
        });
        Swatch sa = new Swatch(() -> s.gradientA, rgb -> {
            s.gradientA = rgb;
            a.setText(String.format("#%06X", rgb));
            s.save();
            restyle();
        });
        Swatch sb = new Swatch(() -> s.gradientB, rgb -> {
            s.gradientB = rgb;
            b.setText(String.format("#%06X", rgb));
            s.save();
            restyle();
        });
        box.add(sa);
        box.add(a);
        box.add(sb);
        box.add(b);
        box.add(dir);
        sa.setBounds(0, 6, 28, 28);
        a.setBounds(32, 0, 92, 40);
        sb.setBounds(132, 6, 28, 28);
        b.setBounds(164, 0, 92, 40);
        dir.setBounds(264, 0, 150, 40);
        return new Row("Gradient colours", "Click a circle for the colour wheel, or type hex codes; then the direction.", box, 414);
    }

    /** A colour circle that opens the colour wheel. */
    private static final class Swatch extends JComponent {
        private final java.util.function.IntSupplier get;

        Swatch(java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
            this.get = get;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    ColorWheel.open(Swatch.this, get.getAsInt(), rgb -> {
                        set.accept(rgb);
                        repaint();
                    });
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            g.setColor(new Color(get.getAsInt() & 0xFFFFFF));
            g.fillOval(2, 2, getWidth() - 4, getHeight() - 4);
            g.setColor(Theme.alpha(Theme.TEXT, 0.35));
            g.drawOval(2, 2, getWidth() - 5, getHeight() - 5);
            g.dispose();
        }
    }

    /** Accent colour: preset swatches plus your own hex colour. */
    private static final class AccentRow extends Row {
        private final Components.Input hex;
        private int hover = -1;

        AccentRow() {
            super("Accent colour", "", new JPanel(null), 190);
            dev.cobra.launcher.core.Settings s = dev.cobra.launcher.core.Settings.get();
            JPanel box = (JPanel) control;
            box.setOpaque(false);
            hex = new Components.Input(String.format("#%06X", s.accentCustom & 0xFFFFFF), "#7C5CFF", null);
            hex.onChange(() -> {
                Integer c = parseHex(hex.getText());
                if (c == null) return;
                s.accentCustom = c;
                s.accent = "Custom";
                s.save();
                restyle();
                repaint();
            });
            box.add(hex);
            hex.setBounds(80, 0, 110, 40);
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                    int h = swatchAt(e.getX(), e.getY());
                    if (h != hover) {
                        hover = h;
                        repaint();
                    }
                }
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    int i = swatchAt(e.getX(), e.getY());
                    if (i < 0) return;
                    s.accent = Theme.ACCENTS[i];
                    s.save();
                    restyle();
                    repaint();
                    if (Theme.ACCENTS[i].equals("Custom")) {        // any colour: the wheel
                        JComponent anchor = AccentRow.this;
                        ColorWheel.open(anchor, s.accentCustom, rgb -> {
                            s.accentCustom = rgb;
                            s.accent = "Custom";
                            s.save();
                            hex.setText(String.format("#%06X", rgb));
                            restyle();
                            repaint();
                        });
                    }
                }

                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = -1;
                    repaint();
                }
            });
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }

        private int swatchX(int i) { return 24 + i * 30; }

        private int swatchAt(int x, int y) {
            if (y < 42 || y > 64) return -1;
            for (int i = 0; i < Theme.ACCENTS.length; i++) if (x >= swatchX(i) - 2 && x <= swatchX(i) + 24) return i;
            return -1;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, 76);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            dev.cobra.launcher.core.Settings s = dev.cobra.launcher.core.Settings.get();
            Theme.left(g, "Accent colour", Theme.font(Theme.MEDIUM, 14.5f), Theme.TEXT, 22, 8, 22);
            g.setFont(Theme.font(Theme.REGULAR, 12.5f));
            Theme.left(g, "Launch button, selection, switches and sliders. The last circle opens a colour wheel.", Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 22, 26, 16);
            int[] rgb = {0, 0x3DDC84, 0x3EA6FF, 0x8B6CFF, 0xFF5C8A, 0xFF8A3D, 0xF5C542, 0x5EEAD4, 0xFFFFFF, 0x111111, s.accentCustom & 0xFFFFFF};
            for (int i = 0; i < Theme.ACCENTS.length; i++) {
                int x = swatchX(i), y = 44;
                boolean sel = Theme.ACCENTS[i].equals(s.accent), hov = i == hover;
                if (i == 0) {   // classic: ivory and charcoal halves
                    g.setColor(new Color(0xF8F5F2));
                    g.fillArc(x, y, 22, 22, 90, 180);
                    g.setColor(new Color(0x2D2D2D));
                    g.fillArc(x, y, 22, 22, 270, 180);
                } else {
                    g.setColor(new Color(rgb[i]));
                    g.fillOval(x, y, 22, 22);
                }
                g.setColor(Theme.alpha(Theme.TEXT, sel ? 0.95 : hov ? 0.5 : 0.18));
                g.setStroke(new BasicStroke(sel ? 2f : 1f));
                g.drawOval(x - (sel ? 3 : 0), y - (sel ? 3 : 0), 22 + (sel ? 6 : 0), 22 + (sel ? 6 : 0));
            }
            g.dispose();
        }
    }

    /** Launcher icon: your own picture as the app icon and logo, or the Cobra star. */
    private static final class IconRow extends Row {
        IconRow() {
            super("Launcher icon", "", new JPanel(null), 250);
            JPanel box = (JPanel) control;
            box.setOpaque(false);
            Components.Button choose = new Components.Button("Choose", "folder", Components.Variant.GHOST, null);
            Components.Button reset = new Components.Button("Reset", "trash", Components.Variant.GHOST, null);
            choose.onClick(() -> MainWindow.get().chooseAppIcon(this::repaint));
            reset.onClick(() -> MainWindow.get().resetAppIcon(this::repaint));
            box.add(choose);
            box.add(reset);
            choose.setBounds(0, 0, 120, 40);
            reset.setBounds(130, 0, 120, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            boolean mine = dev.cobra.launcher.core.AppIcon.custom() != null;
            Theme.logo(g, 34, 30, 26, 1);
            Theme.left(g, "Launcher icon", Theme.font(Theme.MEDIUM, 14.5f), Theme.TEXT, 58, 14, 22);
            String desc = mine ? "Your own picture: window, taskbar, app menu and the logo in here."
                    : "Use any picture as the launcher's icon and logo. Reset brings Cobra back.";
            g.setFont(Theme.font(Theme.REGULAR, 12.5f));
            Theme.left(g, Theme.ellipsize(desc, g.getFontMetrics(), getWidth() - 360), Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 58, 36, 20);
            g.dispose();
        }
    }

    /** Crosshair picture for Cobra Client (Custom Crosshair → Style: Image). */
    private static final class CrosshairRow extends Row {
        private java.awt.image.BufferedImage preview;

        CrosshairRow() {
            super("Crosshair image", "", new JPanel(null), 250);
            JPanel box = (JPanel) control;
            box.setOpaque(false);
            Components.Button choose = new Components.Button("Choose", "folder", Components.Variant.GHOST, null);
            Components.Button reset = new Components.Button("Remove", "trash", Components.Variant.GHOST, null);
            choose.onClick(() -> {
                FileDialog fd = new FileDialog(MainWindow.get().frame, "Crosshair picture (PNG with a transparent background works best)", FileDialog.LOAD);
                fd.setVisible(true);
                if (fd.getFile() == null) return;
                try {
                    dev.cobra.launcher.core.Accessories.importCrosshair(java.nio.file.Path.of(fd.getDirectory(), fd.getFile()));
                    preview = null;
                    MainWindow.get().toast("Crosshair set. In game: Custom Crosshair → Style: Image.");
                } catch (Exception e) {
                    MainWindow.get().toast(e.getMessage());
                }
                repaint();
            });
            reset.onClick(() -> {
                try {
                    dev.cobra.launcher.core.Accessories.removeCrosshair();
                } catch (Exception ignored) {}
                preview = null;
                repaint();
            });
            box.add(choose);
            box.add(reset);
            choose.setBounds(0, 0, 120, 40);
            reset.setBounds(130, 0, 120, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            java.nio.file.Path f = dev.cobra.launcher.core.Accessories.CROSSHAIR;
            boolean has = java.nio.file.Files.exists(f);
            if (has && preview == null) {
                try {
                    preview = javax.imageio.ImageIO.read(f.toFile());
                } catch (Exception ignored) {}
            }
            if (has && preview != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(preview, 22, 18, 24, 24, null);
            } else {
                Icons.paint(g, "crosshair", 22, 18, 24, Theme.SOFT);
            }
            Theme.left(g, "Crosshair image", Theme.font(Theme.MEDIUM, 14.5f), Theme.TEXT, 58, 14, 22);
            String desc = has ? "Used in game with Custom Crosshair → Style: Image."
                    : "Your own crosshair picture for Cobra Client (PNG with transparency is best).";
            g.setFont(Theme.font(Theme.REGULAR, 12.5f));
            Theme.left(g, Theme.ellipsize(desc, g.getFontMetrics(), getWidth() - 360), Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 58, 36, 20);
            g.dispose();
        }
    }

    private static final class AccountRow extends Row {
        AccountRow(JComponent button) {
            super("", "", button, 120);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            MainWindow mw = MainWindow.get();
            boolean in = mw != null && mw.account() != null;
            MainWindow.drawFace(g, in ? mw.face() : null, 22, 16, 40);
            Theme.left(g, in ? mw.account().name : "Not signed in", Theme.font(Theme.MEDIUM, 14.5f), Theme.TEXT, 76, 14, 22);
            Theme.left(g, in ? "Microsoft account" : "Sign in to launch the game.", Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 76, 36, 20);
            g.dispose();
        }
    }

    private static final class Section extends JComponent {
        private final String title;
        private final Row[] rows;

        Section(String title, Row[] rows) {
            this.title = title;
            this.rows = rows;
            setLayout(null);
            for (Row r : rows) add(r);
        }

        /** Shows the rows that match the search (all when the section's own name matches). */
        void filter(String q) {
            boolean titleHit = q != null && !q.isBlank() && title.toLowerCase().contains(q.toLowerCase().trim());
            int shown = 0;
            for (Row r : rows) {
                boolean on = titleHit ? r.matches("") : r.matches(q);
                r.setVisible(on);
                if (on) shown++;
            }
            setVisible(shown > 0);
        }

        @Override
        public Dimension getPreferredSize() {
            int h = 44;
            for (Row r : rows) if (r.isVisible()) h += r.height();
            return new Dimension(100, h + 6);
        }

        @Override
        public void doLayout() {
            int y = 44;
            for (Row r : rows) {
                if (!r.isVisible()) continue;
                r.setBounds(0, y, getWidth(), r.height());
                r.doLayout();
                y += r.height();
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 20);
            Theme.left(g, title, Theme.font(Theme.BOLD, 15f), Theme.TEXT, 22, 12, 26);
            g.setColor(Theme.LINE);
            int y = 44;
            for (Row r : rows) {
                if (!r.isVisible()) continue;
                g.fillRect(22, y, w - 44, 1);
                y += r.height();
            }
            g.dispose();
        }
    }
}
