#version 150

uniform mat4 InvViewProj;
uniform vec3 CamPos;
uniform float Time;
uniform vec3 SunDir;
uniform vec3 SunColor;
uniform vec3 ZenithColor;
uniform vec3 HorizonColor;
uniform vec3 GlowColor;
uniform vec3 AmbientColor;
uniform float Night;
uniform float Darkness;
uniform vec2 WindOffset;
uniform float FlashAmt;
uniform float CloudSteps;
uniform float AmbientCloud;

in vec2 ndc;
out vec4 fragColor;

float ss(float a, float b, float x) { return clamp((x - a) / (b - a), 0.0, 1.0); }

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x),
                   mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
                   mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p, int oct) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < oct; i++) {
        s += a * vnoise(p);
        p = p * 2.02 + vec3(17.1, 3.7, 9.2);
        a *= 0.5;
    }
    return s;
}

vec2 rot(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

vec3 tm(vec3 c) { return 1.0 - exp(-c * 1.2); }

float hgN(float c, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * c, 1.5);
}

uniform float Cover;
const float CB = 190.0;
const float CT = 330.0;

// pw: x,z — мировые координаты (+ смещение ветра), y — абсолютная высота
float cumDensity(vec3 pw, int oct) {
    float hf = (pw.y - CB) / (CT - CB);
    if (hf < 0.0 || hf > 1.0) return 0.0;
    float cov = fbm(vec3(pw.x * 0.0005, 3.7, pw.z * 0.0005), 3);
    float thr = mix(0.62, 0.40, Cover);
    float gate = ss(thr - 0.07, thr + 0.07, cov);
    if (gate <= 0.0) return 0.0;
    float core = ss(thr, thr + 0.12, cov);
    float hl = mix(0.5, 1.0, core);
    float shape = ss(0.0, 0.10, hf) * ss(hl, hl * 0.45, hf);   // плоское основание, округлая вершина
    float n = fbm(vec3(pw.x * 0.0045, pw.y * 0.0075, pw.z * 0.0045), oct);
    float d = gate * shape - (1.0 - n) * 0.62;
    return clamp(d * 2.2, 0.0, 1.0);
}

// Объёмные кучевые: ray marching по слою CB..CT, плоское основание, освещение к солнцу, серебристая кромка.
vec4 cumulus(vec3 ro, vec3 v, int steps, float jit) {
    if (abs(v.y) < 0.004) return vec4(0.0);
    float ta = (CB - ro.y) / v.y;
    float tb = (CT - ro.y) / v.y;
    float t0 = max(min(ta, tb), 0.0);
    float t1 = min(max(ta, tb), 12000.0);
    if (t1 <= t0) return vec4(0.0);
    float dt = (t1 - t0) / float(steps);
    float c = dot(v, SunDir);
    float ph = min(mix(hgN(c, -0.2), hgN(c, 0.55), 0.4), 5.0);
    vec3 acc = vec3(0.0);
    float T = 1.0;
    for (int i = 0; i < 48; i++) {
        if (i >= steps) break;
        float t = t0 + dt * (float(i) + jit);
        vec3 p = ro + v * t;
        vec3 pw = vec3(p.x + WindOffset.x, p.y, p.z + WindOffset.y);
        float d = cumDensity(pw, 4);
        if (d > 0.01) {
            float tl = cumDensity(pw + SunDir * 45.0, 2) * 45.0 + cumDensity(pw + SunDir * 120.0, 2) * 75.0;
            float E = exp(-tl * 0.035);
            float hf = (p.y - CB) / (CT - CB);
            vec3 col = SunColor * E * ph * 0.9 + AmbientColor * mix(0.5, 1.0, hf) * (0.6 + 0.4 * E);
            col = mix(col, vec3(dot(col, vec3(0.333))) * vec3(0.62, 0.65, 0.68), Darkness * 0.8);
            col = mix(col, HorizonColor, 1.0 - exp(-t * 0.0002));
            float fade = 1.0 - ss(3000.0, 9000.0, t);
            float a = 1.0 - exp(-d * 0.04 * dt * fade);
            acc += T * a * col;
            T *= 1.0 - a;
            if (T < 0.03) break;
        }
    }
    return vec4(acc, 1.0 - T);
}

void main() {
    vec4 far = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec3 v = normalize(far.xyz / far.w);
    float y = v.y;

    float t = pow(clamp(y, 0.0, 1.0), 0.42);
    vec3 col = mix(HorizonColor, ZenithColor, t);
    col = mix(col, HorizonColor * 0.85, ss(0.0, -0.25, y));

    float sd = dot(v, SunDir);
    col += SunColor * (pow(max(sd, 0.0), 12.0) * 0.18 + pow(max(sd, 0.0), 220.0) * 0.9);
    vec2 vf = normalize(v.xz + vec2(1e-5));
    vec2 sf = normalize(SunDir.xz + vec2(1e-5));
    float az = max(dot(vf, sf), 0.0);
    col += GlowColor * pow(az, 3.0) * exp(-abs(y) * 5.0) * 0.8;
    col += GlowColor * 0.15 * exp(-abs(y) * 6.0);
    col += SunColor * 8.0 * ss(0.99905, 0.99935, sd) * step(-0.02, y);

    vec3 md = -SunDir;
    float mc = dot(v, md);
    float moon = ss(0.99880, 0.99900, mc) * step(0.0, y);
    float maria = vnoise(v * 38.0);
    col += vec3(0.85, 0.90, 1.0) * moon * (0.65 + 0.35 * maria) * Night * (1.0 - Darkness);
    col += vec3(0.25, 0.30, 0.45) * pow(max(mc, 0.0), 40.0) * 0.12 * Night * step(0.0, y);

    vec3 sp = v * 220.0;
    vec3 cell = floor(sp);
    float hs = hash13(cell);
    vec3 fr = fract(sp) - 0.5;
    float star = ss(0.9965, 1.0, hs) * ss(0.35, 0.0, length(fr));
    float tw = 0.75 + 0.25 * sin(Time * 3.0 + hs * 60.0);
    col += vec3(0.9, 0.95, 1.0) * star * tw * Night * ss(0.0, 0.2, y) * (1.0 - Darkness) * (1.0 - moon);

    if (AmbientCloud > 0.5) {
        // перистые (2D, высокий слой)
        if (y > 0.015 && CamPos.y < 700.0) {
            float tc = (700.0 - CamPos.y) / y;
            vec2 xc = CamPos.xz + v.xz * tc + WindOffset * 0.6;
            float nc = fbm(vec3(xc.x * 0.0007, xc.y * 0.0030, Time * 0.003), 3);
            float al = ss(0.52, 0.85, nc) * 0.45 * ss(0.02, 0.25, y) * (1.0 - Darkness);
            col = mix(col, SunColor * 0.9 + AmbientColor * 0.6, al);
        }
        // кучевые (объёмные)
        float jit = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        vec4 cu = cumulus(CamPos, v, int(CloudSteps), jit);
        col = col * (1.0 - cu.a) + cu.rgb;
    }

    col += vec3(0.50, 0.60, 0.85) * FlashAmt * 0.35 * ss(-0.1, 0.6, y);
    fragColor = vec4(tm(col), 1.0);
}
