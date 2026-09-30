package dev.cobra.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

/** Cobra's sky packs work in the background (Sky module) but aren't listed in the Resource Packs screen. */
@Mixin(ResourcePackOrganizer.class)
public abstract class ResourcePackOrganizerMixin {
    @ModifyReturnValue(method = "getEnabledPacks", at = @At("RETURN"), require = 0)
    private Stream<ResourcePackOrganizer.Pack> cobra$hideEnabled(Stream<ResourcePackOrganizer.Pack> packs) {
        return packs.filter(p -> !p.getName().contains("cobra-sky-"));
    }

    @ModifyReturnValue(method = "getDisabledPacks", at = @At("RETURN"), require = 0)
    private Stream<ResourcePackOrganizer.Pack> cobra$hideDisabled(Stream<ResourcePackOrganizer.Pack> packs) {
        Stream<ResourcePackOrganizer.Pack> out = packs.filter(p -> !p.getName().contains("cobra-sky-"));
        // Pack Organizer: available packs A to Z
        if (dev.cobra.client.core.Cobra.platform != null
                && dev.cobra.client.core.Cobra.get(dev.cobra.client.core.module.Features.PackOrganizer.class).isEnabled()
                && dev.cobra.client.core.Cobra.get(dev.cobra.client.core.module.Features.PackOrganizer.class).sort.on()) {
            out = out.sorted(java.util.Comparator.comparing((ResourcePackOrganizer.Pack p) -> p.getDisplayName().getString().replaceAll("\\u00a7.", "").toLowerCase()));
        }
        return out;
    }
}
