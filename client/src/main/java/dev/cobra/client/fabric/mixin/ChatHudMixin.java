package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    /** Every chat/system line: feeds BedWars tracking and adds timestamps. */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true)
    private Text cobra$message(Text message) {
        if (Cobra.platform == null) return message;
        Cobra.onChat(message.getString());
        Features.ChatMod chat = Cobra.get(Features.ChatMod.class);
        if (chat.isEnabled() && chat.timestamps.on()) return Text.literal(chat.stamp()).append(message);
        return message;
    }

    /** Chat Mod: 1000 lines of history instead of 100. */
    @ModifyConstant(method = {"addVisibleMessage", "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V"},
            constant = @Constant(intValue = 100), require = 0)
    private int cobra$history(int original) {
        return Cobra.platform == null ? original : Cobra.get(Features.ChatMod.class).historySize();
    }
}
