package dev.cobra.client.fabric.cosmetics;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.CobraOnline;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
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
        if (wear.containsKey("antlers")) part(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.antlers(e, vc, light, CosmeticModels.colour(wear.get("antlers"))));
        if (wear.containsKey("orbit")) part(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.orbit(e, vc, light, t, CosmeticModels.colour(wear.get("orbit"))));
        if (wear.containsKey("scarf")) part(matrices, queue, m.body, 1f, (e, vc) -> CosmeticModels.scarf(e, vc, light, t, CosmeticModels.colour(wear.get("scarf"))));
        if (wear.containsKey("glasses")) part(matrices, queue, m.head, 1f, (e, vc) -> CosmeticModels.glasses(e, vc, light, CosmeticModels.colour(wear.get("glasses"))));
        if (wear.containsKey("headphones")) part(matrices, queue, m.head, 1f, (e, vc) -> CosmeticModels.headphones(e, vc, light, CosmeticModels.colour(wear.get("headphones"))));
        if (wear.containsKey("halo")) {
            int hc = CosmeticModels.haloColour(wear.get("halo"));
            part(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.halo(e, vc, BRIGHT, t, hc, 0.45f, 0xFF));
            glowPart(matrices, queue, m.head, k, (e, vc) -> CosmeticModels.halo(e, vc, BRIGHT, t, hc, 1.1f, Math.round(80 * (glow ? pulse + 0.3f : 0.6f))));
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
        // ---- your imported cape (Cobra players only)
        if (wear.containsKey("cape")) {
            MinecraftClient mc = MinecraftClient.getInstance();
            Entity ent = mc.world == null ? null : mc.world.getEntityById(state.id);
            if (ent != null) {
                boolean self = mc.player != null && ent.getUuid().equals(mc.player.getUuid());
                Identifier tex = CobraCapes.get(ent.getUuid().toString(), wear.get("cape"), self);
                if (tex != null) cape(matrices, queue, m.body, light, tex, limbDistance, t, CobraCapes.frames(tex));
            }
        }
        // ---- Cobra's own particles (always visible, whatever Minecraft's particle setting is)
        if (wear.containsKey("fx")) {
            MinecraftClient mc = MinecraftClient.getInstance();
            Entity pe = mc.world == null ? null : mc.world.getEntityById(state.id);
            boolean firstPerson = pe != null && mc.player != null && pe.getUuid().equals(mc.player.getUuid()) && mc.options.getPerspective().isFirstPerson();
            if (pe != null && !firstPerson) {
                String fx = wear.get("fx"), who = pe.getUuid().toString();
                boolean w = wear.containsKey("wings"), h = wear.containsKey("halo"), tr = wear.containsKey("trail");
                float walk = Math.min(1f, limbDistance * 1.5f);
                glowPart(matrices, queue, m.body, 1f, (e, vc) -> dev.cobra.client.core.cosmetics.CosmeticParticles.render(who, fx, w, h, tr, walk, e, vc));
            }
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
            code = Cobra.ownCode();                                  // your cosmetics + your cape
            if (!c.showOwn.on()) code = code.contains("cape:") ? code.substring(code.indexOf("cape:")) : "";
        } else {
            code = CobraOnline.cosmetics(e.getUuid().toString());   // "" unless they use Cobra
        }
        return parse(code);
    }

    /** Cosmetics code for a player entity (yours from the settings, others' from the online list). */
    public static Map<String, String> wearingOf(net.minecraft.entity.player.PlayerEntity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && e.getUuid().equals(mc.player.getUuid())) return parse(Cobra.ownCode());
        return parse(CobraOnline.cosmetics(e.getUuid().toString()));
    }

    private static final Map<String, Map<String, String>> PARSED = new HashMap<>();

    private static Map<String, String> parse(String code) {
        if (code == null || code.isEmpty()) return Map.of();
        if (PARSED.size() > 256) PARSED.clear();                     // codes change with size etc.: keep it small
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

    /**
     * A cape from the shoulders: 10 x 16 x 1 pixels like the real one, with the standard cape
     * texture layout (outside at 1,1 and inside at 12,1 on a 64 x 32 image; HD sizes work too).
     * It leans back as you walk and sways a little.
     */
    private static void cape(MatrixStack matrices, OrderedRenderCommandQueue queue, ModelPart body, int light,
                             Identifier tex, float limbDistance, float age, int frames) {
        // animated capes: one frame every 90 ms, frames stacked top to bottom in the texture
        int frame = frames <= 1 ? 0 : (int) ((System.currentTimeMillis() / 90) % frames);
        final int fr = frame, fs = Math.max(1, frames);
        matrices.push();
        body.applyTransform(matrices);
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        matrices.translate(0, 0, 2.05f);
        float lean = 0.12f + Math.min(1f, limbDistance) * 0.7f + (float) Math.sin(age * 0.09) * 0.04f;
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(lean));
        queue.submitCustom(matrices, dev.cobra.client.fabric.compat.Layers.cutout(tex), (e, vc) -> {
            capeFrame = fr;                                       // (drawn later, so set it here)
            capeFrames = fs;
            float x0 = -5, x1 = 5, y0 = 0, y1 = 16, z0 = 0, z1 = 1;
            // outside (seen from behind you)
            face(e, vc, light, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 1, 1, 11, 17, 0, 0, 1);
            // inside (against your back)
            face(e, vc, light, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 12, 1, 22, 17, 0, 0, -1);
            // edges
            face(e, vc, light, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, 0, 1, 1, 17, -1, 0, 0);
            face(e, vc, light, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 11, 1, 12, 17, 1, 0, 0);
            face(e, vc, light, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 1, 0, 11, 1, 0, -1, 0);
            face(e, vc, light, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, 11, 0, 21, 1, 0, 1, 0);
        });
        matrices.pop();
    }

    private static int capeFrame, capeFrames = 1;

    /** One textured face; u/v in pixels of a 64 x 32 cape image (of the current animation frame). */
    private static void face(MatrixStack.Entry e, VertexConsumer vc, int light,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        float fh = 32f * capeFrames, off = 32f * capeFrame;
        float U0 = u0 / 64f, V0 = (v0 + off) / fh, U1 = u1 / 64f, V1 = (v1 + off) / fh;
        vc.vertex(e, ax, ay, az).color(0xFFFFFFFF).texture(U0, V0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
        vc.vertex(e, bx, by, bz).color(0xFFFFFFFF).texture(U1, V0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
        vc.vertex(e, cx, cy, cz).color(0xFFFFFFFF).texture(U1, V1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
        vc.vertex(e, dx, dy, dz).color(0xFFFFFFFF).texture(U0, V1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }

    private interface Draw {
        void draw(CosmeticModels.M m, CosmeticModels.Out out);
    }

    /** Draws in a model part's space (1 unit = 1 pixel of the skin, times the cosmetic size). */
    private static void part(MatrixStack matrices, OrderedRenderCommandQueue queue, ModelPart p, float size, Draw d) {
        submit(matrices, queue, p, size, d, dev.cobra.client.fabric.compat.Layers.cutout(WHITE));   // opaque: no sorting flicker
    }

    /** A soft glowing shell drawn see-through on top (the glow effect). */
    private static void glowPart(MatrixStack matrices, OrderedRenderCommandQueue queue, ModelPart p, float size, Draw d) {
        submit(matrices, queue, p, size, d, dev.cobra.client.fabric.compat.Layers.translucent(WHITE));
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
