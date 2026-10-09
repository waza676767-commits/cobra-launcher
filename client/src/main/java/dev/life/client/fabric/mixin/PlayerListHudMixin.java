package dev.life.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.life.client.core.Life;
import dev.life.client.core.LifeOnline;
import dev.life.client.core.module.Features;
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

/** A small Life mark before the names of players who use Life Client (Tab list). */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Unique
    private static Style life$font;

    /** Style that draws "\ue000" as the Life mark (life:font/icons.json); plain text if that fails. */
    @Unique
    private static Style life$font() {
        if (life$font == null) {
            try {
                java.lang.reflect.Constructor<?> c = StyleSpriteSource.Font.class.getDeclaredConstructors()[0];
                c.setAccessible(true);
                life$font = Style.EMPTY.withFont((StyleSpriteSource) c.newInstance(Identifier.of("life", "icons")));
            } catch (Throwable t) {
                life$font = Style.EMPTY;
            }
        }
        return life$font;
    }

    @ModifyReturnValue(method = "getPlayerName", at = @At("RETURN"), require = 0)
    private Text life$icon(Text name, PlayerListEntry entry) {
        if (Life.platform == null || entry == null || !Life.get(Features.Client.class).tabIcon.on()) return name;
        String id = entry.getProfile().id().toString();
        if (!LifeOnline.isLife(id)) return name;
        Style st = life$font();
        MutableText out = st == Style.EMPTY ? Text.literal("\u2726") : Text.literal("\ue000").setStyle(st);
        return out.append(Text.literal(" ").setStyle(Style.EMPTY)).append(name);
    }
}
