package dev.cobra.client.fabric;

import dev.cobra.client.core.module.Features;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Item Physics: dropped items behave like real objects instead of floating and spinning.
 * <ul>
 *   <li>Flat items (tools, ingots, food...) fall over and lie flat on the ground.</li>
 *   <li>Blocks sit on one of their faces, each at its own random turn.</li>
 *   <li>While flying through the air they tumble (faster the faster they move) and, when they
 *   land, they roll onto the nearest side instead of snapping.</li>
 *   <li>In water flat items float on their back and rock gently with the waves.</li>
 *   <li>No bobbing, no endless spinning. Stacks of several items lie in a little pile.</li>
 * </ul>
 * Each item's tumble is remembered by entity id between frames (forgotten a few seconds after it's
 * gone), so the animation is smooth at any frame rate.
 */
public final class ItemPhysicsFx {
    private ItemPhysicsFx() {}

    private static final class Spin {
        float pitch, roll;
        float lastAge = Float.NaN;
        long seen;
    }

    private static final Map<Integer, Spin> SPINS = new HashMap<>();
    private static long lastSweep;
    private static final Random JITTER = new Random();

    /** Called when the render state is filled in (render thread): works out this frame's tilt. */
    public static void update(ItemEntity entity, ItemEntityRenderState state, Features.ItemPhysics ip) {
        if (!(((Object) state) instanceof ItemPhysicsState ps)) return;
        if (!ip.isEnabled() || state.itemRenderState.isEmpty()) {
            ps.cobra$setPhysics(false, 0, 0, 0);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSweep > 2000) {                                   // forget items long gone
            lastSweep = now;
            for (Iterator<Spin> it = SPINS.values().iterator(); it.hasNext(); ) if (now - it.next().seen > 4000) it.remove();
        }
        boolean flat = isFlat(state.itemRenderState);
        boolean ground = entity.isOnGround(), water = entity.isTouchingWater();
        float age = state.age;
        float rest = flat ? 90f : 0f;                                   // flat items lie down, blocks stand
        Spin s = SPINS.get(entity.getId());
        if (s == null) {
            s = new Spin();
            // a freshly thrown item starts upright (like vanilla) and tumbles; one that was already
            // lying there when it came into view starts at rest
            s.pitch = ground || water ? rest : 0f;
            s.roll = 0f;
            SPINS.put(entity.getId(), s);
        }
        float dt = Float.isNaN(s.lastAge) ? 0f : Math.max(0f, Math.min(4f, age - s.lastAge));   // in ticks
        s.lastAge = age;
        s.seen = now;
        double spin = ip.airSpin.get();
        if (!ground && !water && spin > 0) {
            Vec3d v = entity.getVelocity();
            double speed = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);   // blocks per tick
            float rate = (float) ((9 + Math.min(1.2, speed) * 34) * spin);  // degrees per tick
            s.pitch += rate * dt;
            if (!flat) s.roll += rate * 0.55f * dt;
        } else {
            // settle onto the nearest resting side, easing in (about a quarter of a second)
            float k = 1f - (float) Math.exp(-dt * 0.55);
            float target = nearest(s.pitch, flat ? 180f : 90f, rest);
            s.pitch += (target - s.pitch) * k;
            if (!flat) s.roll += (nearest(s.roll, 90f, 0f) - s.roll) * k;
            else s.roll += (0f - s.roll) * k;
        }
        s.pitch = wrap(s.pitch);
        s.roll = wrap(s.roll);
        float pitch = s.pitch, roll = s.roll;
        if (water && flat) {                                            // floating: rocking on the waves
            pitch += (float) Math.sin(age * 0.11 + state.uniqueOffset) * 7f;
            roll += (float) Math.sin(age * 0.08 + state.uniqueOffset * 2) * 5f;
        }
        float yaw = (float) Math.toDegrees(state.uniqueOffset);         // every item its own (still) turn
        ps.cobra$setPhysics(true, pitch, roll, yaw);
    }

    private static boolean isFlat(ItemRenderState item) {
        Box b = item.getModelBoundingBox();
        return b == null || b.maxZ - b.minZ <= 0.0625;
    }

    /** The resting angle (base + n * step) nearest to {@code a}. */
    private static float nearest(float a, float step, float base) {
        return base + Math.round((a - base) / step) * step;
    }

    /** Keeps an angle within ±360 (so it never grows without bound), a whole number of turns at a time. */
    private static float wrap(float a) {
        while (a > 360f) a -= 360f;
        while (a < -360f) a += 360f;
        return a;
    }

    /** Draws the item(s) lying the way {@link #update} decided, in place of the vanilla floating item. */
    public static void render(ItemEntityRenderState state, ItemPhysicsState ps, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        ItemRenderState item = state.itemRenderState;
        Box box = item.getModelBoundingBox();
        if (box == null) box = new Box(-0.125, -0.125, -0.02, 0.125, 0.125, 0.02);
        float w = (float) (box.maxX - box.minX), h = (float) (box.maxY - box.minY), d = (float) (box.maxZ - box.minZ);
        float cx = (float) ((box.minX + box.maxX) / 2), cy = (float) ((box.minY + box.maxY) / 2), cz = (float) ((box.minZ + box.maxZ) / 2);
        int count = Math.max(1, state.renderedAmount);
        boolean flat = d <= 0.0625f;
        float spacing = flat ? Math.max(d * 1.5f, 0.012f) : 0f;
        float depth = flat ? spacing * (count - 1) + d : d;
        double p = Math.toRadians(ps.cobra$pitch()), r = Math.toRadians(ps.cobra$roll());
        // how far the turned item reaches below its middle: lift it by that so it rests on the ground
        float half = (float) (Math.abs(Math.sin(r) * Math.cos(p)) * w / 2 + Math.abs(Math.cos(r) * Math.cos(p)) * h / 2 + Math.abs(Math.sin(p)) * depth / 2);
        float pile = !flat && count > 1 ? 0.06f : 0f;

        matrices.push();
        matrices.translate(0f, half + pile + 0.004f, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ps.cobra$yaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ps.cobra$pitch()));
        if (ps.cobra$roll() != 0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ps.cobra$roll()));
        matrices.translate(-cx, -cy, -cz);
        JITTER.setSeed(state.seed);
        if (flat) {
            // a flat stack: the copies lie on top of each other, each nudged a little to the side
            matrices.translate(0f, 0f, -spacing * (count - 1) / 2f);
            for (int i = 0; i < count; i++) {
                matrices.push();
                if (i > 0) {
                    float jx = (JITTER.nextFloat() * 2f - 1f) * 0.15f * 0.5f, jy = (JITTER.nextFloat() * 2f - 1f) * 0.15f * 0.5f;
                    matrices.translate(jx, jy, 0f);
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((JITTER.nextFloat() * 2f - 1f) * 25f));
                }
                item.render(matrices, queue, state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);
                matrices.pop();
                matrices.translate(0f, 0f, spacing);
            }
        } else {
            item.render(matrices, queue, state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);
            for (int i = 1; i < count; i++) {                        // a little heap of blocks
                matrices.push();
                float jx = (JITTER.nextFloat() * 2f - 1f) * 0.15f, jy = (JITTER.nextFloat() * 2f - 1f) * 0.1f, jz = (JITTER.nextFloat() * 2f - 1f) * 0.15f;
                matrices.translate(jx, jy, jz);
                item.render(matrices, queue, state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);
                matrices.pop();
            }
        }
        matrices.pop();
    }
}
