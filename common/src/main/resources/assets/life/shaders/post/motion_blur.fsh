#version 330

// Life Client velocity motion blur.
// MotionSampler is a 1x1 texture Life rewrites every frame:
//   rg = how far the view moved this frame on screen (0.5 = still, range +/-0.25 of the screen)
//   b  = radial (zoom) blur from running forward, a = strength setting.
// Each pixel is smeared along that motion, so turning blurs sideways, looking up/down blurs
// vertically, and holding still is perfectly sharp.
uniform sampler2D InSampler;
uniform sampler2D MotionSampler;
// Colors module: r = contrast, g = saturation, b = brightness (each 0..2, 128 = normal), a = on
uniform sampler2D ColorsSampler;

in vec2 texCoord;

out vec4 fragColor;

const int SAMPLES = 24;

vec3 grade(vec3 c) {
    vec4 k = texture(ColorsSampler, vec2(0.5, 0.5));
    if (k.a < 0.5) return c;
    float contrast = k.r * 2.0, saturation = k.g * 2.0, brightness = k.b * 2.0;
    c *= brightness;
    c = (c - 0.5) * contrast + 0.5;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(l), c, saturation);
    return clamp(c, 0.0, 1.0);
}

// per-pixel noise (interleaved gradient noise): each pixel starts its taps at a slightly different
// spot, so the blur is a smooth smear instead of a few stacked copies of the picture
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec4 m = texture(MotionSampler, vec2(0.5, 0.5));
    vec2 velocity = (m.rg - vec2(128.0 / 255.0)) * 0.5 * m.a;
    // never smear more than ~6% of the screen: big flicks stay readable
    float len = length(velocity);
    if (len > 0.06) velocity *= 0.06 / len;
    float radial = m.b * 0.05 * m.a;
    vec2 fromCentre = texCoord - 0.5;
    vec3 centre = texture(InSampler, texCoord).rgb;
    if (dot(velocity, velocity) < 1.0e-7 && radial < 1.0e-4) {
        fragColor = vec4(grade(centre), 1.0);
        return;
    }
    float jitter = ign(gl_FragCoord.xy) - 0.5;
    vec3 sum = centre * 2.0;                                // the sharp image counts a bit more
    float total = 2.0;
    for (int i = 0; i < SAMPLES; i++) {
        float t = (float(i) + 0.5 + jitter) / float(SAMPLES) - 0.5;   // -0.5 .. 0.5, jittered
        float w = 1.0 - abs(t) * 1.6;                        // a soft triangle: strongest in the middle
        vec2 uv = texCoord + velocity * t + fromCentre * radial * t;
        sum += texture(InSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb * w;
        total += w;
    }
    fragColor = vec4(grade(sum / total), 1.0);
}
