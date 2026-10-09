package dev.life.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

/** Life's sky packs work in the background (Sky module) but aren't listed in the Resource Packs screen. */
@Mixin(ResourcePackOrganizer.class)
public abstract class ResourcePackOrganizerMixin {
    @ModifyReturnValue(method = "getEnabledPacks", at = @At("RETURN"), require = 0)
    private Stream<ResourcePackOrganizer.Pack> life$hideEnabled(Stream<ResourcePackOrganizer.Pack> packs) {
        return packs.filter(p -> !p.getName().contains("life-sky-"));
    }

    @ModifyReturnValue(method = "getDisabledPacks", at = @At("RETURN"), require = 0)
    private Stream<ResourcePackOrganizer.Pack> life$hideDisabled(Stream<ResourcePackOrganizer.Pack> packs) {
        Stream<ResourcePackOrganizer.Pack> out = packs.filter(p -> !p.getName().contains("life-sky-"));
        // Pack Organizer: available packs A to Z
        if (dev.life.client.core.Life.platform != null
                && dev.life.client.core.Life.get(dev.life.client.core.module.Features.PackOrganizer.class).isEnabled()
                && dev.life.client.core.Life.get(dev.life.client.core.module.Features.PackOrganizer.class).sort.on()) {
            out = out.sorted(java.util.Comparator.comparing((ResourcePackOrganizer.Pack p) -> p.getDisplayName().getString().replaceAll("\\u00a7.", "").toLowerCase()));
        }
        return out;
    }
}
