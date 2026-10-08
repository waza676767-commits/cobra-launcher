package dev.cobra.client.fabric.mixin;

import dev.cobra.client.fabric.ItemPhysicsState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every dropped item's render state the Item Physics tilt (see {@link ItemPhysicsState}). */
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsState {
    @Unique private boolean cobra$active;
    @Unique private float cobra$p, cobra$r, cobra$y;

    @Override
    public void cobra$setPhysics(boolean active, float pitch, float roll, float yaw) {
        cobra$active = active;
        cobra$p = pitch;
        cobra$r = roll;
        cobra$y = yaw;
    }

    @Override public boolean cobra$physics() { return cobra$active; }
    @Override public float cobra$pitch() { return cobra$p; }
    @Override public float cobra$roll() { return cobra$r; }
    @Override public float cobra$yaw() { return cobra$y; }
}
