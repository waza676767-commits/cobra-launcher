package dev.cobra.client.fabric.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    /** Sets the raw value, skipping the slider's 0..1 validation (used by Fullbright). */
    @Accessor("value")
    void cobra$setValue(Object value);
}
