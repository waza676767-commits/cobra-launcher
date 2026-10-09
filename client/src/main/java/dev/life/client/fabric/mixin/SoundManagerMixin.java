package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7 Sounds: the attack sounds that came after 1.8 (sweep, crit, strong, weak, knockback) don't play. */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void life$oldSounds(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
        if (Life.platform == null || sound == null || sound.getId() == null) return;
        if (Life.get(Features.OldSounds.class).mute(sound.getId().getPath())) {
            SoundSystem.PlayResult[] all = SoundSystem.PlayResult.values();
            cir.setReturnValue(all[all.length - 1]);   // "not started"
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void life$oldSoundsDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        if (Life.platform == null || sound == null || sound.getId() == null) return;
        if (Life.get(Features.OldSounds.class).mute(sound.getId().getPath())) ci.cancel();
    }
}
