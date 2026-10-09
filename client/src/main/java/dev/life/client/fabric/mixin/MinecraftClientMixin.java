package dev.life.client.fabric.mixin;

import dev.life.client.fabric.LifeTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    /** Swap the vanilla main menu for the Life one. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen life$titleScreen(Screen screen) {
        if (screen instanceof TitleScreen && !(screen instanceof LifeTitleScreen) && dev.life.client.fabric.LifeFabric.vanillaTitle) return screen;
        return screen instanceof TitleScreen ? new LifeTitleScreen() : screen;
    }

    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void life$windowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("Life Client 1.21.11");
    }
}
