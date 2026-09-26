package dev.cobra.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.CobraOnline;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** A small Cobra mark before the names of players who use Cobra Client (Tab list). */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Unique
    private static Style cobra$font;

    /** Style that draws "\ue000" as the Cobra mark (cobra:font/icons.json); plain text if that fails. */
    @Unique
    private static Style cobra$font() {
        if (cobra$font == null) {
            try {
                java.lang.reflect.Constructor<?> c = StyleSpriteSource.Font.class.getDeclaredConstructors()[0];
                c.setAccessible(true);
                cobra$font = Style.EMPTY.withFont((StyleSpriteSource) c.newInstance(Identifier.of("cobra", "icons")));
            } catch (Throwable t) {
                cobra$font = Style.EMPTY;
            }
        }
        return cobra$font;
    }

    @ModifyReturnValue(method = "getPlayerName", at = @At("RETURN"), require = 0)
    private Text cobra$icon(Text name, PlayerListEntry entry) {
        if (Cobra.platform == null || entry == null || !Cobra.get(Features.Client.class).tabIcon.on()) return name;
        String id = entry.getProfile().id().toString();
        if (!CobraOnline.isCobra(id)) return name;
        Style st = cobra$font();
        MutableText out = st == Style.EMPTY ? Text.literal("\u2726") : Text.literal("\ue000").setStyle(st);
        return out.append(Text.literal(" ").setStyle(Style.EMPTY)).append(name);
    }
}
