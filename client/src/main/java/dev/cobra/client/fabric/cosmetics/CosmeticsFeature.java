package dev.cobra.client.fabric.cosmetics;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.CobraOnline;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import dev.cobra.client.core.cosmetics.CosmeticModels;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws Cobra cosmetics on players: cat ears, wings, halo, cat tail, katana, big feet and boxing
 * gloves. Built from simple shaded boxes that follow the player's head, body, arms and legs.
 * Shown for you (your settings) and for other Cobra players (what the Cobra online list says they
 * wear); everyone else sees a normal player.
 */
public final class CosmeticsFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier WHITE = Identifier.of("cobra", "textures/misc/white.png");

    public CosmeticsFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (Cobra.platform == null || state.invisible) return;
        Map<String, String> wear;
        try {
            wear = wearing(state);
        } catch (Throwable t) {
            return;
        }
        if (wear.isEmpty()) return;
        PlayerEntityModel m = getContextModel();
        float t = state.age;
        boolean glow = wear.containsKey("glow");
        int lit = glow ? BRIGHT : light;
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.12);
        float size = 1f;
        try {
            if (wear.containsKey("size")) size = Math.max(0.6f, Math.min(1.6f, Integer.parseInt(wear.get("size")) / 100f));
        } catch (NumberFormatException ignored) {}
        final float k = size;

        // ---- head
        if (wear.containsKey("ears")) part(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.catEars(e, vc, light, CosmeticModels.colour(wear.get("ears"))));
        if (wear.containsKey("bunny")) part(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.bunnyEars(e, vc, light, CosmeticModels.colour(wear.get("bunny")), t));
        if (wear.containsKey("horns")) part(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.horns(e, vc, glow ? BRIGHT : light, CosmeticModels.colour(wear.get("horns"))));
        if (wear.containsKey("hat")) part(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.hat(e, vc, light, wear.get("hat")));
        if (wear.containsKey("glasses")) part(matrices, queue, m.head, 1f, (e, vc) -> CosmeticModels.glasses(e, vc, light, CosmeticModels.colour(wear.get("glasses"))));
        if (wear.containsKey("headphones")) part(matrices, queue, m.head, 1f, (e, vc) -> CosmeticModels.headphones(e, vc, light, CosmeticModels.colour(wear.get("headphones"))));
        if (wear.containsKey("halo")) {
            boolean red = "red".equals(wear.get("halo"));
            part(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.halo(e, vc, BRIGHT, t, red, 0.45f, 0xFF));
            glowPart(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.halo(e, vc, BRIGHT, t, red, 1.1f, Math.round(80 * (glow ? pulse + 0.3f : 0.6f))));
        }
        // ---- back
        if (wear.containsKey("wings")) {
            int c = CosmeticModels.colour(wear.get("wings"));
            String style = wear.getOrDefault("wingstyle", "feather");
            part(matrices, queue, m.body, k, (e, vc) -> CosmeticModels.wings(e, vc, lit, t, c, style, false, 0));
            if (glow) glowPart(matrices, queue, m.body, k, (e, vc) -> CosmeticModels.wings(e, vc, BRIGHT, t, c, style, true, pulse));
        }
        if (wear.containsKey("tail")) {
            String tv = wear.get("tail");
            part(matrices, queue, m.body, k, (e, vc) -> CosmeticModels.tail(e, vc, light, t, tv));
        }
        if (wear.containsKey("backpack")) part(matrices, queue, m.body, 1f, (e, vc) -> CosmeticModels.backpack(e, vc, light, CosmeticModels.colour(wear.get("backpack"))));
        if (wear.containsKey("katana")) {
            part(matrices, queue, m.body, k, (e, vc) -> CosmeticModels.katana(e, vc, light));
            if (glow) glowPart(matrices, queue, m.body, k, (e, vc) -> CosmeticModels.katanaGlow(e, vc, pulse));
        }
        // ---- hands and feet
        if (wear.containsKey("gloves")) {
            int c = CosmeticModels.colour(wear.get("gloves"));
            part(matrices, queue, m.rightArm, 1f, (e, vc) -> CosmeticModels.glove(e, vc, light, c, -1));
            part(matrices, queue, m.leftArm, 1f, (e, vc) -> CosmeticModels.glove(e, vc, light, c, 1));
        }
        if (wear.containsKey("feet")) {
            part(matrices, queue, m.rightLeg, 1f, (e, vc) -> CosmeticModels.foot(e, vc, light));
            part(matrices, queue, m.leftLeg, 1f, (e, vc) -> CosmeticModels.foot(e, vc, light));
        }
    }

    /** Cosmetics for this player: yours from your settings, others' from the online list. */
    private static Map<String, String> wearing(PlayerEntityRenderState state) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return Map.of();
        Entity e = mc.world.getEntityById(state.id);
        if (e == null) return Map.of();
        String code;
        if (mc.player != null && e.getUuid().equals(mc.player.getUuid())) {
            Features.Cosmetics c = Cobra.get(Features.Cosmetics.class);
            if (!c.showOwn.on()) return Map.of();
            code = c.serialize();
        } else {
            code = CobraOnline.cosmetics(e.getUuid().toString());   // "" unless they use Cobra
        }
        return parse(code);
    }

    private static final Map<String, Map<String, String>> PARSED = new HashMap<>();

    private static Map<String, String> parse(String code) {
        if (code == null || code.isEmpty()) return Map.of();
        return PARSED.computeIfAbsent(code, k -> {
            Map<String, String> out = new HashMap<>();
            for (String part : k.split(",")) {
                if (part.isEmpty()) continue;
                int i = part.indexOf(':');
                out.put(i < 0 ? part : part.substring(0, i), i < 0 ? "" : part.substring(i + 1));
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ drawing helpers

    private interface Draw {
        void draw(CosmeticModels.M m, CosmeticModels.Out out);
    }

    /** Draws in a model part's space (1 unit = 1 pixel of the skin, times the cosmetic size). */
    private static void part(MatrixStack matrices, OrderedRenderCommandQueue queue, ModelPart p, float size, Draw d) {
        submit(matrices, queue, p, size, d, RenderLayers.entityCutoutNoCull(WHITE));   // opaque: no sorting flicker
    }

    /** A soft glowing shell drawn see-through on top (the glow effect). */
    private static void glowPart(MatrixStack matrices, OrderedRenderCommandQueue queue, ModelPart p, float size, Draw d) {
        submit(matrices, queue, p, size, d, RenderLayers.entityTranslucent(WHITE));
    }

    private static void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, ModelPart p, float size, Draw d,
                               net.minecraft.client.render.RenderLayer layer) {
        matrices.push();
        p.applyTransform(matrices);
        matrices.scale(size / 16f, size / 16f, size / 16f);
        queue.submitCustom(matrices, layer, (entry, vc) -> d.draw(CosmeticModels.M.identity(), (pts, nx, ny, nz, argb, light) -> {
            for (int i = 0; i < 4; i++) {
                vc.vertex(entry, pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2]).color(argb).texture(i == 2 || i == 3 ? 1 : 0, i == 1 || i == 2 ? 1 : 0)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
            }
        }));
        matrices.pop();
    }

    private static final int BRIGHT = CosmeticModels.BRIGHT;
}
