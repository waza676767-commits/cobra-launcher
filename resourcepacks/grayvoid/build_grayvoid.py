#!/usr/bin/env python3
"""Build the Grayvoid resource pack (Minecraft Java Edition 1.21.11, pack format 75).

Usage:
    python3 build_grayvoid.py <1.21.11 client.jar> <pack icon image> <output.zip>

The totem, end crystal item and respawn anchor textures in ./textures/ were
copied out of the reference video: hotbar icons are read 1:1 (GUI scale 2, so
one texel = 2x2 video pixels for 16px items) and anchor faces were
perspective-rectified from close-up frames, then brightness-corrected for the
video's levels and the in-game face shading.
"""
import io
import json
import os
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw

JAR, ICON, OUT = sys.argv[1:4]
HERE = os.path.dirname(os.path.abspath(__file__))
jar = zipfile.ZipFile(JAR)

files = {}

# ---------------------------------------------------------------- palette
STONE = (125, 125, 125, 255)      # flat average of vanilla stone
OBSIDIAN = (11, 8, 12, 255)       # near-black, faint purple like the video
GLOWSTONE = (234, 234, 234, 255)
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)
# Exact RGBA signature painted on the totem glitter sprites. particle.fsh looks
# for it and draws those fragments pure white. No vanilla texture uses it.
TOTEM_MARK = (250, 251, 252, 253)


def vanilla(path):
    return Image.open(io.BytesIO(jar.read("assets/minecraft/" + path))).convert("RGBA")


def copied(name):
    return Image.open(os.path.join(HERE, "textures", name)).convert("RGBA")


def put_png(path, im):
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    files["assets/minecraft/" + path] = buf.getvalue()


def put_json(path, obj):
    key = path if path in ("pack.mcmeta",) else "assets/minecraft/" + path
    files[key] = (json.dumps(obj, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def flat(color, size=16):
    return Image.new("RGBA", (size, size), color)


def box_faces(u, v, w, h, d):
    """Vanilla box-UV face rectangles (x, y, width, height) in model texels."""
    return [
        (u + d, v, w, d),              # up
        (u + d + w, v, w, d),          # down
        (u, v + d, d, h),              # east
        (u + d, v + d, w, h),          # north
        (u + d + w, v + d, d, h),      # west
        (u + 2 * d + w, v + d, w, h),  # south
    ]


# ---------------------------------------------------------------- flat blocks
put_png("textures/block/stone.png", flat(STONE))
put_png("textures/block/obsidian.png", flat(OBSIDIAN))
put_png("textures/block/glowstone.png", flat(GLOWSTONE))

# ---------------------------------------------------------------- respawn anchor (copied from video)
# Same face on every side. Charged sides come from the 4.55s close-up; the
# unlit version is the same layout barely visible (4.4s / hotbar icon).
put_png("textures/block/respawn_anchor_side0.png", copied("respawn_anchor_side_uncharged.png"))
for level in (1, 2, 3, 4):
    put_png(f"textures/block/respawn_anchor_side{level}.png", copied("respawn_anchor_side_charged.png"))
# Charged top: white frame from 5.0s, black frame from 4.55s, interpolated pulse.
put_png("textures/block/respawn_anchor_top.png", copied("respawn_anchor_top.png"))
put_json("textures/block/respawn_anchor_top.png.mcmeta", {"animation": {"frametime": 10, "interpolate": True}})
put_png("textures/block/respawn_anchor_top_off.png", copied("respawn_anchor_top_off.png"))
put_png("textures/block/respawn_anchor_bottom.png", flat(OBSIDIAN))

# ---------------------------------------------------------------- end crystal (entity)
# 8x the 64x32 model UV: a 1-texel wire is 1/64 of a cube face. That is close
# to the hairline in the reference video and stays unbroken at fighting range.
S = 8
crystal = Image.new("RGBA", (64 * S, 32 * S), CLEAR)
d = ImageDraw.Draw(crystal)
for (u, v, w, h, dd), fill in (
    ((0, 0, 8, 8, 8), None),          # glass cubes (outer + inner)
    ((32, 0, 8, 8, 8), None),         # core cube
    ((0, 16, 12, 4, 12), OBSIDIAN),   # bedrock base (only on End pillars)
):
    for x, y, fw, fh in box_faces(u, v, w, h, dd):
        x0, y0, x1, y1 = x * S, y * S, (x + fw) * S - 1, (y + fh) * S - 1
        if fill:
            d.rectangle([x0, y0, x1, y1], fill=fill)
        d.rectangle([x0, y0, x1, y1], outline=WHITE)
put_png("textures/entity/end_crystal/end_crystal.png", crystal)

# ---------------------------------------------------------------- copied item icons
put_png("textures/item/end_crystal.png", copied("end_crystal_item.png"))
put_png("textures/item/totem_of_undying.png", copied("totem_of_undying.png"))

# ---------------------------------------------------------------- totem pop particles
for i in range(8):
    src = vanilla(f"textures/particle/glitter_{i}.png")
    a = np.array(src)[..., 3]
    px = np.zeros((*a.shape, 4), np.uint8)
    px[a > 0] = TOTEM_MARK
    put_png(f"textures/particle/glitter_{i}.png", Image.fromarray(px, "RGBA"))

MARK_GLSL = ", ".join(f"{c}.0" for c in TOTEM_MARK)
particle_fsh = f"""#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Grayvoid: totem glitter sprites are painted with this exact RGBA signature.
const vec4 GRAYVOID_TOTEM_MARK = vec4({MARK_GLSL}) / 255.0;

void main() {{
    vec4 tex = texture(Sampler0, texCoord0);
    vec4 color;
    if (all(lessThan(abs(tex - GRAYVOID_TOTEM_MARK), vec4(0.6 / 255.0)))) {{
        // Totem pop: drop the hard-coded green/yellow tint, keep the fade-out alpha.
        color = vec4(1.0, 1.0, 1.0, vertexColor.a) * ColorModulator;
    }} else {{
        color = tex * vertexColor * ColorModulator;
    }}
    if (color.a < 0.1) {{
        discard;
    }}
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}}
"""
files["assets/minecraft/shaders/core/particle.fsh"] = particle_fsh.encode()

# ---------------------------------------------------------------- fire
# Vanilla fire geometry with a texture that is a single white pixel row at the
# bottom: a 1-pixel-high wire on every fire plane, nothing above it.
F = 64  # 1 px of 64 matches the line weight in the video (~1/45 block)
fire = Image.new("RGBA", (F, F), CLEAR)
ImageDraw.Draw(fire).line([0, F - 1, F - 1, F - 1], fill=WHITE)
for n in (0, 1):
    put_png(f"textures/block/fire_{n}.png", fire)
    # Must ship our own .mcmeta: vanilla's lists 32 frames this texture lacks.
    put_json(f"textures/block/fire_{n}.png.mcmeta", {"animation": {"frametime": 2}})

# No dark smoke column over every fire block.
put_png("textures/particle/grayvoid_blank.png", Image.new("RGBA", (8, 8), CLEAR))
put_json("particles/large_smoke.json", {"textures": ["minecraft:grayvoid_blank"]})

# ---------------------------------------------------------------- enchantment glint
# Neutral instead of purple: a light sheen on worn armor, a faint one on icons.
for name, gain in (("enchanted_glint_armor", 1.1), ("enchanted_glint_item", 0.45)):
    g = np.array(vanilla(f"textures/misc/{name}.png")).astype(np.float32)
    v = np.clip(g[..., :3].max(axis=2) * gain, 0, 255).astype(np.uint8)
    px = np.dstack([v, v, v, np.full_like(v, 255)])
    put_png(f"textures/misc/{name}.png", Image.fromarray(px, "RGBA"))

# ---------------------------------------------------------------- pack metadata
GRADIENT = ["#FFFFFF", "#EDEDED", "#DADADA", "#C6C6C6", "#B2B2B2", "#9E9E9E", "#8A8A8A", "#767676"]
description = [""]
description += [{"text": ch, "color": c, "bold": True} for ch, c in zip("GRAYVOID", GRADIENT)]
description += [
    {"text": " ✦ ", "color": "#4A4A4A"},
    {"text": "crystal pvp", "color": "#6E6E6E"},
    {"text": "\nno color. ", "color": "#8C8C8C", "italic": True},
    {"text": "no mercy.", "color": "#FFFFFF", "italic": True},
]
put_json("pack.mcmeta", {
    "pack": {
        "pack_format": 75,
        "min_format": 75,
        "max_format": 75,
        "description": description,
    }
})

icon_img = Image.open(ICON).convert("RGB")
side = min(icon_img.size)
icon_img = icon_img.crop(((icon_img.width - side) // 2, (icon_img.height - side) // 2,
                          (icon_img.width + side) // 2, (icon_img.height + side) // 2))
buf = io.BytesIO()
icon_img.resize((256, 256), Image.LANCZOS).save(buf, "PNG", optimize=True)
files["pack.png"] = buf.getvalue()

# ---------------------------------------------------------------- write zip
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    for path in sorted(files):
        info = zipfile.ZipInfo(path, date_time=(2025, 12, 9, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, files[path])
print(f"wrote {OUT} ({len(files)} files)")
