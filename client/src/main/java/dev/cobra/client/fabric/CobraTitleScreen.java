package dev.cobra.client.fabric;

import dev.cobra.client.core.ui.TitleMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Replaces the vanilla title screen (see MinecraftClientMixin). */
public final class CobraTitleScreen extends Screen {
    private final TitleMenu menu = new TitleMenu();

    public CobraTitleScreen() {
        super(Text.literal("Cobra Client"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // panorama + blur
        try {
            menu.render(CobraFabric.RENDER.with(context), mouseX, mouseY);
        } catch (Throwable t) {                       // never crash the game: fall back to Minecraft's menu
            t.printStackTrace();
            CobraFabric.vanillaTitle = true;
            client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = mouseX(), mouseY = mouseY();   // (Click's x/y have no yarn names; read the mouse directly)
        int button = click.button();
        try {
            menu.mouseClicked(mouseX, mouseY, button);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.getKeycode();
        if (CobraFabric.MENU.matchesKey(input)) {
            client.setScreen(new CobraMenuScreen(this));
            return true;
        }
        return super.keyPressed(input);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean shouldPause() { return false; }

    private double mouseX() {
        return client.mouse.getScaledX(client.getWindow());
    }

    private double mouseY() {
        return client.mouse.getScaledY(client.getWindow());
    }
}
