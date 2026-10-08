package dev.cobra.client.fabric;

/**
 * Extra data Item Physics keeps on each dropped item's render state (added by
 * {@code ItemEntityRenderStateMixin}): how far it is tipped over and its resting turn, worked out
 * from the real item entity (on the ground? in water? how fast?) when the state is filled in.
 */
public interface ItemPhysicsState {
    void cobra$setPhysics(boolean active, float pitch, float roll, float yaw);

    boolean cobra$physics();

    float cobra$pitch();

    float cobra$roll();

    float cobra$yaw();
}
