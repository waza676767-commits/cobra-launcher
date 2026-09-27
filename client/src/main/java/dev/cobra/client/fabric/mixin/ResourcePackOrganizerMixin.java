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
        return packs.filter(p -> !p.getName().contains("cobra-sky-"));
    }
}
