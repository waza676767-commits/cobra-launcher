package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextVisitFactory.class)
public abstract class TextVisitFactoryMixin {
    @Unique private static Features.NickHider cobra$nick;

    /** Nick Hider: every string the game draws passes through here. */
    @ModifyVariable(method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String cobra$nick(String text) {
        if (Cobra.platform == null) return text;
        if (cobra$nick == null) cobra$nick = Cobra.get(Features.NickHider.class);
        return cobra$nick.apply(text);
    }
}
