package dev.life.client.fabric;

import dev.life.client.core.ui.LifeMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class LifeMenuScreen extends Screen {
    private final Screen parent;
    // in a world Right Shift opens the HUD editor first (MODS button in the middle goes to the modules)
    private final LifeMenu menu = new LifeMenu(dev.life.client.core.Life.platform != null && dev.life.client.core.Life.platform.inWorld());


    public LifeMenuScreen(Screen parent) {
        super(Text.literal("Life"));
        this.parent = parent;
    }

    /** Opens on the Waypoints tab, ready to type the name of a waypoint that was just added. */
    public void nameWaypoint(dev.life.client.core.module.Features.Waypoints.Point pt) {
        menu.nameWaypoint(pt);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        FabricRender r = LifeFabric.RENDER.with(context);
        if (client.world == null) {
            if (!r.wallpaper(width, height)) renderPanoramaBackground(context, delta);
        } else if (!menu.editingHud() && dev.life.client.core.Life.get(dev.life.client.core.module.Features.Client.class).blur.on()) {
            // the world behind is blurred by a post effect (see init); a dim on top (a light haze for Panels)
            context.fill(0, 0, width, height, 0x40000000);
        }
        menu.setRender(r);
        try {
            menu.render(r, mouseX, mouseY);
        } catch (Throwable t) {           // a Life bug closes the menu instead of crashing the game
            t.printStackTrace();
            close();
        }
    }

    private boolean blurOn;

    /** Blur behind the modules: Minecraft's own blur post effect on the world while the menu is open. */
    @Override
    protected void init() {
        super.init();
        try {
            var gr = (dev.life.client.fabric.mixin.GameRendererInvoker) client.gameRenderer;
            if (client.world != null && gr.life$getPostProcessorId() == null
                    && dev.life.client.core.Life.get(dev.life.client.core.module.Features.Client.class).blur.on()) {
                gr.life$setPostProcessor(net.minecraft.util.Identifier.ofVanilla("blur"));
                blurOn = true;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void removed() {
        super.removed();
        if (!blurOn) return;
        blurOn = false;
        try {
            var gr = (dev.life.client.fabric.mixin.GameRendererInvoker) client.gameRenderer;
            net.minecraft.util.Identifier cur = gr.life$getPostProcessorId();
            if (cur != null && cur.getPath().equals("blur") && cur.getNamespace().equals("minecraft")) gr.life$clearPostProcessor();
        } catch (Throwable ignored) {}
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // keep the HUD visible behind the menu (no blur)
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = mouseX(), mouseY = mouseY();   // (Click's x/y have no yarn names; read the mouse directly)
        int button = click.button();
        menu.mouseClicked(mouseX, mouseY, button);
        if (menu.closeRequested) close();
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        menu.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        double mouseX = mouseX(), mouseY = mouseY();   // (Click's x/y have no yarn names; read the mouse directly)
        menu.mouseDragged(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        menu.mouseScrolled(mouseX, mouseY, verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.getKeycode();
        int k = switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> LifeMenu.KEY_ESCAPE;
            case GLFW.GLFW_KEY_BACKSPACE -> LifeMenu.KEY_BACKSPACE;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> LifeMenu.KEY_ENTER;
            default -> LifeMenu.KEY_OTHER;
        };
        boolean typing = menu.typing();
        if (k == LifeMenu.KEY_OTHER && !typing && LifeFabric.MENU.matchesKey(input)) {
            close();
            return true;
        }
        if (menu.keyPressed(k, keyCode)) close();
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (!input.isValidChar()) return true;
        for (char chr : input.asString().toCharArray()) menu.charTyped(chr);
        return true;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        dev.life.client.core.Life.save();
        client.setScreen(parent);
    }

    private double mouseX() {
        return client.mouse.getScaledX(client.getWindow());
    }

    private double mouseY() {
        return client.mouse.getScaledY(client.getWindow());
    }
}
