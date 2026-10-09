package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextVisitFactory.class)
public abstract class TextVisitFactoryMixin {
    @Unique private static Features.NickHider life$nick;

    /** Nick Hider: every string the game draws passes through here. */
    @ModifyVariable(method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String life$nick(String text) {
        if (Life.platform == null) return text;
        if (life$nick == null) life$nick = Life.get(Features.NickHider.class);
        return life$nick.apply(text);
    }
}
