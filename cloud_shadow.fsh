#version 150

uniform mat4 InvViewProj;
uniform sampler2D DepthSampler;
uniform vec3 CamPos;
uniform vec3 SunDir;
uniform vec2 WindOffset;
uniform float StormCount;
uniform float ShadowStrength;
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

uniform float AnvilScale;
uniform float SD[80];

struct StormInfo {
    vec2 c;
    float base;
    float top;
    float inten;
    float gr;
    vec2 dir;
    float tor;
    float gy;
    float R;
    float phase;
    vec2 tp;
    float rain;
    float shape;
    float meso;
};

StormInfo getStorm(int i) {
    int o = i * 20;
    StormInfo s;
    s.c = vec2(SD[o], SD[o + 1]);
    s.base = SD[o + 2];
    s.top = SD[o + 3];
    s.inten = SD[o + 4];
    s.gr = SD[o + 5];
    s.dir = vec2(SD[o + 6], SD[o + 7]);
    s.tor = SD[o + 8];
    s.gy = SD[o + 9];
    s.R = SD[o + 10];
    s.phase = SD[o + 11];
    s.tp = vec2(SD[o + 12], SD[o + 13]);
    s.rain = SD[o + 14];
    s.shape = SD[o + 15];
    s.meso = SD[o + 16];
    return s;
}

// Тело суперячейки: широкое основание, цветная капуста башни, плоская широкая наковальня с мамматусами.
float cloudBody(vec3 p, StormInfo s, int oct, out float hOut) {
    hOut = 0.0;
    float R = s.R;
    float span = (s.top - s.base) * max(s.gr, 0.04);
    float h = (p.y - s.base) / span;
    hOut = clamp(h, 0.0, 1.0);
    if (h < -0.02 || h > 1.1 || s.gr < 0.02) return 0.0;

    vec2 q = p.xz - s.c;
    vec2 perp = vec2(-s.dir.y, s.dir.x);
    float hh = clamp(h, 0.0, 1.0);

    vec2 qt = q - s.dir * (hh * hh * R * 0.45);
    float widen = 0.55 + 0.45 * min(s.gr * 1.5, 1.0);
    float rt = R * mix(0.95, 0.60, ss(0.0, 0.35, hh)) * widen * (1.0 + 0.25 * ss(0.55, 0.95, hh));
    rt *= mix(1.0, 0.55, ss(0.9, 1.1, h));
    float mT = ss(1.0, 0.12, length(qt) / rt) * ss(-0.02, 0.08, h) * (1.0 - ss(0.95, 1.10, h));

    float mA = 0.0;
    float ag = ss(0.45, 0.95, s.gr);
    if (ag > 0.01) {
        float a = max(R * (0.8 + 3.0 * AnvilScale) * ag, 1.0);
        float b0 = max(R * (0.7 + 1.6 * AnvilScale) * ag, 1.0);
        float along = dot(q, s.dir);
        float pc = dot(q, perp);
        float u = (along - a * 0.55 - R * 0.2) / a;
        float b = b0 * (0.45 + 0.55 * ss(-0.6, 0.8, u));
        float e = length(vec2(u, pc / b));
        float mm = vnoise(vec3(q * 0.02, 1.0));
        float hL = mix(0.80, 0.60, clamp(1.0 - e, 0.0, 1.0)) + (mm - 0.5) * 0.07;
        float hU = 0.98 - 0.07 * e;
        float lay = ss(hL, hL + 0.08, h) * (1.0 - ss(hU - 0.07, hU, h));
        mA = ss(1.0, 0.08, e) * lay;
    }

    float mask = max(mT, mA);
    if (mask < 0.002) return 0.0;

    vec2 qr = rot(q, s.phase / (1.0 + length(q) / (R * 0.6)));
    vec3 nc = vec3(qr.x, p.y * 1.5, qr.y) / (R * 0.4);
    nc += (vnoise(nc * 0.7 + vec3(3.0)) - 0.5) * 0.8;
    float n1 = fbm(nc, oct);
    float d = mask - (1.0 - n1) * 0.5;
    if (oct > 2) d -= (1.0 - fbm(nc * 3.7 + vec3(5.2, 1.3, 8.8), oct - 1)) * 0.22 * (1.0 - 0.5 * mask);
    return clamp(d * 2.4, 0.0, 1.0);
}

// Тени облаков на земле: от точки поверхности (по буферу глубины) луч к солнцу сквозь кучевые и штормовые облака.
void main() {
    float dsmp = texture(DepthSampler, ndc * 0.5 + 0.5).r;
    float sunY = SunDir.y;
    if (dsmp >= 0.99999 || sunY < 0.05 || ShadowStrength < 0.01) {
        fragColor = vec4(0.0);
        return;
    }
    vec4 wp = InvViewProj * vec4(ndc, dsmp * 2.0 - 1.0, 1.0);
    vec3 pr = wp.xyz / wp.w;
    float dist = length(pr);
    float fadeD = 1.0 - ss(1200.0, 1900.0, dist);
    if (fadeD <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 pa = vec3(pr.x, CamPos.y + pr.y, pr.z);        // xz — от камеры, y — абсолютный
    float jit = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));

    float tau = 0.0;
    if (AmbientCloud > 0.5) {
        vec3 pw0 = vec3(CamPos.x + pr.x + WindOffset.x, pa.y, CamPos.z + pr.z + WindOffset.y);
        float t0 = max((CB - pa.y) / sunY, 0.0);
        float t1 = (CT - pa.y) / sunY;
        if (t1 > t0) {
            float len = t1 - t0;
            for (int i = 0; i < 6; i++) {
                float t = t0 + len * (float(i) + jit) / 6.0;
                tau += cumDensity(pw0 + SunDir * t, 3) * len / 6.0 * 0.03;
            }
        }
    }

    int n = int(StormCount);
    for (int k = 0; k < 4; k++) {
        if (k >= n) break;
        StormInfo s = getStorm(k);
        float tEnd = min((s.top - pa.y) / sunY, 3500.0);
        if (tEnd <= 0.0) continue;
        vec2 endXZ = pa.xz + SunDir.xz * tEnd;
        // грубая отсечка: ось луча далеко от шторма
        vec2 mid = (pa.xz + endXZ) * 0.5 - s.c;
        float reach = s.R * 7.0 + tEnd * 0.5;
        if (length(mid) > reach) continue;
        for (int i = 0; i < 8; i++) {
            float t = tEnd * (float(i) + jit) / 8.0;
            float hh;
            tau += cloudBody(pa + SunDir * t, s, 2, hh) * (tEnd / 8.0) * 0.045;
        }
    }

    float shade = (1.0 - exp(-tau)) * 0.62 * ShadowStrength * ss(0.05, 0.3, sunY) * fadeD;
    fragColor = vec4(0.01, 0.015, 0.035, shade);
}
