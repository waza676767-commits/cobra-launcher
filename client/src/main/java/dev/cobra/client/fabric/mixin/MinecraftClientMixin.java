package dev.cobra.client.fabric.mixin;

import dev.cobra.client.fabric.CobraTitleScreen;
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
    /** Swap the vanilla main menu for the Cobra one. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen cobra$titleScreen(Screen screen) {
        if (screen instanceof TitleScreen && !(screen instanceof CobraTitleScreen) && dev.cobra.client.fabric.CobraFabric.vanillaTitle) return screen;
        return screen instanceof TitleScreen ? new CobraTitleScreen() : screen;
    }

    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void cobra$windowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("Cobra Client 1.21.11");
    }
}
