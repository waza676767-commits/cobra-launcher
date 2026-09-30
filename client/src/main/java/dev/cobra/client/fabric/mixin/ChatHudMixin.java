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
        String plain = message.getString();
        Cobra.onChat(plain);
        Features.ChatMod chat = Cobra.get(Features.ChatMod.class);
        if (!chat.isEnabled()) return message;
        Text out = message;
        // Better Chat: the same line again becomes one line with (x2), (x3)...
        if (chat.stack.on() && plain.equals(cobra$last) && !messages.isEmpty()) {
            cobra$repeat++;
            messages.remove(0);
            refresh();
            out = message.copy().append(Text.literal(" \u00a77(x" + cobra$repeat + ")"));
        } else {
            cobra$repeat = 1;
        }
        cobra$last = plain;
        if (chat.timestamps.on()) out = Text.literal(chat.stamp()).append(out);
        return out;
    }

    @org.spongepowered.asm.mixin.Unique private static String cobra$last = "";
    @org.spongepowered.asm.mixin.Unique private static int cobra$repeat = 1;

    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private java.util.List<net.minecraft.client.gui.hud.ChatHudLine> messages;

    @org.spongepowered.asm.mixin.Shadow
    public abstract void refresh();

    /** Chat Mod: 1000 lines of history instead of 100. */
    @ModifyConstant(method = {"addVisibleMessage", "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V"},
            constant = @Constant(intValue = 100), require = 0)
    private int cobra$history(int original) {
        return Cobra.platform == null ? original : Cobra.get(Features.ChatMod.class).historySize();
    }
}
