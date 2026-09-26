package dev.cobra.client.fabric.mixin;

import dev.cobra.client.fabric.CobraFabric;
import dev.cobra.client.fabric.CobraTitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** On Minecraft's own title screen: a "Cobra Menu" button (top right) back to Cobra's main menu. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void cobra$backToCobra(CallbackInfo ci) {
        if ((Object) this instanceof CobraTitleScreen) return;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cobra Menu"), b -> {
            CobraFabric.vanillaTitle = false;
            if (this.client != null) this.client.setScreen(new CobraTitleScreen());
        }).dimensions(this.width - 106, 6, 100, 20).build());
    }
}
