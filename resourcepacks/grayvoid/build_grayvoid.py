#!/usr/bin/env python3
"""Build the Grayvoid resource pack (Minecraft Java Edition 1.21.11, pack format 75).

Usage:
    python3 build_grayvoid.py <1.21.11 client.jar> <pack icon image> <output.zip>

Vanilla textures are read straight from the client jar so every derived texture
(totem, armor, glint, glitter) keeps the exact vanilla shapes.
"""
import io
import json
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw

JAR, ICON, OUT = sys.argv[1:4]
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


def put_png(path, im):
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    files["assets/minecraft/" + path] = buf.getvalue()


def put_json(path, obj):
    key = path if path in ("pack.mcmeta",) else "assets/minecraft/" + path
    files[key] = (json.dumps(obj, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def flat(color, size=16):
    return Image.new("RGBA", (size, size), color)


def border(draw, x0, y0, x1, y1, color, width=1):
    for i in range(width):
        draw.rectangle([x0 + i, y0 + i, x1 - i, y1 - i], outline=color)


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

# ---------------------------------------------------------------- respawn anchor
def anchor_pattern():
    """(x, y) -> shade multiplier. Band on top, one stepped tooth, corner pillars."""
    px = {(x, y): 1.0 for x in range(16) for y in (0, 1)}            # charge band
    px.update({(x, y): 1.0 for x in range(7, 12) for y in (2, 3, 4)})  # tooth
    px.update({(x, 5): 1.0 for x in range(8, 11)})
    px.update({(x, y): 1.0 for x in range(0, 3) for y in range(11, 16)})  # pillars
    px.update({(x, y): 1.0 for x in range(13, 16) for y in range(10, 16)})
    px.update({(3, y): 0.72 for y in range(12, 16)})                 # pillar shading
    px.update({(12, y): 0.72 for y in range(11, 16)})
    return px


def anchor_side(value):
    im = flat(OBSIDIAN)
    for p, k in anchor_pattern().items():
        v = int(value * k)
        im.putpixel(p, (v, v, v, 255))
    return im


put_png("textures/block/respawn_anchor_side0.png", anchor_side(46))   # unlit: faint arches
for level, value in ((1, 214), (2, 228), (3, 242), (4, 255)):
    put_png(f"textures/block/respawn_anchor_side{level}.png", anchor_side(value))

# Charged top: black rim, centre pulses from dark to white.
PULSE = 12
top = Image.new("RGBA", (16, 16 * PULSE), OBSIDIAN)
d = ImageDraw.Draw(top)
for f in range(PULSE):
    b = int(28 + (240 - 28) * (0.5 - 0.5 * np.cos(2 * np.pi * f / PULSE)))
    core = int(b * 0.88)
    d.rectangle([2, 2 + 16 * f, 13, 13 + 16 * f], fill=(b, b, b, 255))
    d.rectangle([5, 5 + 16 * f, 10, 10 + 16 * f], fill=(core, core, core, 255))
put_png("textures/block/respawn_anchor_top.png", top)
put_json("textures/block/respawn_anchor_top.png.mcmeta", {"animation": {"frametime": 2, "interpolate": True}})

top_off = flat(OBSIDIAN)
ImageDraw.Draw(top_off).rectangle([2, 2, 13, 13], fill=(20, 18, 22, 255))
put_png("textures/block/respawn_anchor_top_off.png", top_off)
put_png("textures/block/respawn_anchor_bottom.png", flat(OBSIDIAN))

# ---------------------------------------------------------------- end crystal (entity)
# Vanilla is 128x64 (2x of the 64x32 model UV). We go 8x (512x256) so the
# wire is a hairline: 1px on a 64px cube face.
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
        border(d, x0, y0, x1, y1, WHITE)
put_png("textures/entity/end_crystal/end_crystal.png", crystal)

# ---------------------------------------------------------------- end crystal (item)
icon = Image.new("RGBA", (32, 32), CLEAR)
d = ImageDraw.Draw(icon)
front = (3, 10, 21, 28)
back = (10, 3, 28, 21)
d.rectangle(front, outline=WHITE)
d.rectangle(back, outline=WHITE)
for (ax, ay), (bx, by) in (
    ((3, 10), (10, 3)), ((21, 10), (28, 3)), ((3, 28), (10, 21)), ((21, 28), (28, 21)),
):
    d.line([ax, ay, bx, by], fill=WHITE)
core = [(15, 11), (20, 16), (15, 21), (10, 16)]
d.line(core + [core[0]], fill=(255, 255, 255, 255))
put_png("textures/item/end_crystal.png", icon)

# ---------------------------------------------------------------- totem of undying
TOTEM_MAP = {
    (160, 91, 35): 255, (133, 64, 15): 246,                       # outline -> white rim
    (253, 251, 238): 238, (248, 238, 165): 224, (234, 219, 132): 208,
    (209, 167, 93): 172, (197, 135, 66): 142,
    (65, 230, 127): 84, (0, 187, 70): 52,                         # eyes
}
totem = vanilla("textures/item/totem_of_undying.png")
out = Image.new("RGBA", totem.size, CLEAR)
for y in range(totem.height):
    for x in range(totem.width):
        r, g, b, a = totem.getpixel((x, y))
        if a:
            v = TOTEM_MAP.get((r, g, b), int(0.299 * r + 0.587 * g + 0.114 * b))
            out.putpixel((x, y), (v, v, v, a))
put_png("textures/item/totem_of_undying.png", out)

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
# Vanilla fire geometry, but the texture is only a short white outline along
# the bottom edge. The four side planes draw the outer rim at the block edges;
# the four tilted floor planes draw the uneven inner dividers. Result: a low
# wireframe "tray" on the ground instead of flames.
F = 64
FIRE_TOP_ROW = 58


def fire_texture():
    im = Image.new("RGBA", (F, F), CLEAR)
    ImageDraw.Draw(im).rectangle([0, FIRE_TOP_ROW, F - 1, F - 1], outline=WHITE)
    return im


for n in (0, 1):
    put_png(f"textures/block/fire_{n}.png", fire_texture())
    # Must ship our own .mcmeta: vanilla's lists 32 frames this texture lacks.
    put_json(f"textures/block/fire_{n}.png.mcmeta", {"animation": {"frametime": 2}})

# No dark smoke column over every fire block.
put_png("textures/particle/grayvoid_blank.png", Image.new("RGBA", (8, 8), CLEAR))
put_json("particles/large_smoke.json", {"textures": ["minecraft:grayvoid_blank"]})

# ---------------------------------------------------------------- enchantment glint
# Worn armor gets a strong neutral-white sheen (the bright enemies in the
# video); item icons keep only a faint shimmer so netherite stays dark.
for name, gain in (("enchanted_glint_armor", 1.1), ("enchanted_glint_item", 0.45)):
    g = np.array(vanilla(f"textures/misc/{name}.png")).astype(np.float32)
    v = np.clip(g[..., :3].max(axis=2) * gain, 0, 255).astype(np.uint8)
    px = np.dstack([v, v, v, np.full_like(v, 255)])
    put_png(f"textures/misc/{name}.png", Image.fromarray(px, "RGBA"))

# ---------------------------------------------------------------- netherite armor
A = 4  # 64x32 -> 256x128 so the outline is a hairline
ARMOR_BOXES = [
    (0, 0, 8, 8, 8),     # head
    (32, 0, 8, 8, 8),    # hat layer
    (16, 16, 8, 12, 4),  # body
    (40, 16, 4, 12, 4),  # arms
    (0, 16, 4, 12, 4),   # legs
]
for layer in ("humanoid", "humanoid_leggings"):
    src = vanilla(f"textures/entity/equipment/{layer}/netherite.png")
    big = np.array(src.resize((src.width * A, src.height * A), Image.NEAREST))
    lum = (0.299 * big[..., 0] + 0.587 * big[..., 1] + 0.114 * big[..., 2])
    lum = np.clip(lum * 1.3 + 10, 0, 255).astype(np.uint8)
    out = np.dstack([lum, lum, lum, big[..., 3]])
    opaque = big[..., 3] > 0
    for box in ARMOR_BOXES:
        for x, y, w, h in box_faces(*box):
            x0, y0, x1, y1 = x * A, y * A, (x + w) * A, (y + h) * A
            face = opaque[y0:y1, x0:x1]
            if not face.any():
                continue
            edge = np.zeros_like(face)
            edge[0, :] = edge[-1, :] = True
            edge[:, 0] = edge[:, -1] = True
            pad = np.pad(face, 1, constant_values=False)
            edge |= ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
            edge &= face
            region = out[y0:y1, x0:x1]
            region[edge] = (255, 255, 255, 255)
    put_png(f"textures/entity/equipment/{layer}/netherite.png", Image.fromarray(out, "RGBA"))

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
