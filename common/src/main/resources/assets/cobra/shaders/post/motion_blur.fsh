#version 330

// Cobra Client velocity motion blur.
// MotionSampler is a 1x1 texture Cobra rewrites every frame:
//   rg = how far the view moved this frame on screen (0.5 = still, range +/-0.25 of the screen)
//   b  = radial (zoom) blur from running forward, a = strength setting.
// Each pixel is smeared along that motion, so turning blurs sideways, looking up/down blurs
// vertically, and holding still is perfectly sharp.
uniform sampler2D InSampler;
uniform sampler2D MotionSampler;

in vec2 texCoord;

out vec4 fragColor;

const int SAMPLES = 20;

void main() {
    vec4 m = texture(MotionSampler, vec2(0.5, 0.5));
    vec2 velocity = (m.rg - vec2(128.0 / 255.0)) * 0.5 * m.a;
    float radial = m.b * 0.08 * m.a;
    vec2 fromCentre = texCoord - 0.5;
    if (dot(velocity, velocity) < 1.0e-7 && radial < 1.0e-4) {
        fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
        return;
    }
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < SAMPLES; i++) {
        float t = float(i) / float(SAMPLES - 1) - 0.5;   // -0.5 .. 0.5, centred on the pixel
        float w = exp(-t * t * 8.0);                      // gaussian: soft, no ghost copies
        vec2 uv = texCoord + velocity * t + fromCentre * radial * t;
        sum += texture(InSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb * w;
        total += w;
    }
    fragColor = vec4(sum / total, 1.0);
}
