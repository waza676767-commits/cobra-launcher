package dev.life.client.fabric.mixin;

import dev.life.client.fabric.LifeFabric;
import dev.life.client.fabric.LifeTitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** On Minecraft's own title screen: a "Life Menu" button (top right) back to Life's main menu. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void life$backToLife(CallbackInfo ci) {
        if ((Object) this instanceof LifeTitleScreen) return;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Life Menu"), b -> {
            LifeFabric.vanillaTitle = false;
            if (this.client != null) this.client.setScreen(new LifeTitleScreen());
        }).dimensions(this.width - 106, 6, 100, 20).build());
    }
}
