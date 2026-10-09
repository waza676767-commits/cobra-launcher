package dev.life.client.fabric.mixin;

import dev.life.client.fabric.ItemPhysicsState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every dropped item's render state the Item Physics tilt (see {@link ItemPhysicsState}). */
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsState {
    @Unique private boolean life$active;
    @Unique private float life$p, life$r, life$y;

    @Override
    public void life$setPhysics(boolean active, float pitch, float roll, float yaw) {
        life$active = active;
        life$p = pitch;
        life$r = roll;
        life$y = yaw;
    }

    @Override public boolean life$physics() { return life$active; }
    @Override public float life$pitch() { return life$p; }
    @Override public float life$roll() { return life$r; }
    @Override public float life$yaw() { return life$y; }
}
