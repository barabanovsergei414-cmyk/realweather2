#version 150

uniform mat4 InvViewProj;
uniform sampler2D DepthSampler;
uniform vec3 CamPos;
uniform float Time;
uniform vec3 SunDir;
uniform vec3 SunColor;
uniform vec3 AmbientColor;
uniform vec3 HazeColor;
uniform float Steps;
uniform float StepScale;
uniform float MaxDist;
uniform float DetailOct;
uniform float LightSteps;
uniform float Density;
uniform float Contrast;
uniform float FlashAmt;
uniform vec3 FlashPos;
uniform float StormCount;
uniform float Darkness;

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

vec2 boxHit(vec3 ro, vec3 rd, vec3 bmin, vec3 bmax) {
    vec3 sg = vec3(rd.x < 0.0 ? -1.0 : 1.0, rd.y < 0.0 ? -1.0 : 1.0, rd.z < 0.0 ? -1.0 : 1.0);
    vec3 inv = sg / max(abs(rd), vec3(1e-5));
    vec3 t0 = (bmin - ro) * inv;
    vec3 t1 = (bmax - ro) * inv;
    vec3 tn = min(t0, t1);
    vec3 tf = max(t0, t1);
    return vec2(max(max(tn.x, tn.y), tn.z), min(min(tf.x, tf.y), tf.z));
}

// Wall cloud, воронка (с полосами, неровными краями, меняющейся шириной), пылевая юбка, дождевая завеса.
float extras(vec3 p, StormInfo s, int oct, out float rainD, out float dustD, out float funD) {
    rainD = 0.0;
    dustD = 0.0;
    funD = 0.0;
    float dens = 0.0;
    float R = s.R;
    float lowF = max(s.tor, s.meso * 0.7);

    // wall cloud: опускается по мере роста мезоциклона, ещё до торнадо
    float low = 18.0 + 45.0 * lowF;
    float yw = (s.base - p.y) / low;
    vec2 qw = p.xz - s.tp;
    float rw = R * 0.16 + 10.0;
    float lw = length(qw);
    if (yw > 0.0 && yw < 1.0 && lw < rw * 1.1 && s.gr > 0.3 && lowF > 0.05) {
        float mW = ss(1.0, 0.25, lw / rw) * ss(0.0, 0.15, yw) * (1.0 - ss(0.7, 1.0, yw));
        vec2 qr = rot(qw, s.phase * 2.0 / (1.0 + lw / (rw * 0.4)));
        float nW = fbm(vec3(qr.x, p.y * 2.0, qr.y) / (rw * 0.35), min(oct, 3));
        dens += clamp((mW - (1.0 - nW) * 0.5) * 2.2, 0.0, 1.0) * ss(0.3, 0.6, s.gr) * (0.3 + 0.7 * s.inten) * ss(0.05, 0.4, lowF);
    }

    if (s.tor > 0.02) {
        float reach = ss(0.08, 0.5, s.tor);
        float bottom = mix(s.gy + (s.base - s.gy) * 0.55, s.gy, reach);
        float topF = s.base - (12.0 + 30.0 * s.tor);
        float ft = (p.y - bottom) / max(topF - bottom, 1.0);
        if (ft > -0.02 && ft < 1.05) {
            float ftc = clamp(ft, 0.0, 1.0);
            vec2 sway = vec2(sin(p.y * 0.045 + Time * 0.8), cos(p.y * 0.031 + Time * 0.6))
                      * (1.5 + 9.0 * ftc * ftc) * (1.2 - 0.6 * s.tor);
            vec2 axis = s.tp + sway + s.dir * ftc * ftc * R * 0.10;
            vec2 dv = p.xz - axis;
            float dr = length(dv);
            float r0 = mix(0.8, 2.8, s.shape) * (1.0 + 4.0 * s.tor);
            float r1 = R * (0.09 + 0.13 * s.tor) * mix(0.55, 1.2, s.shape) + 8.0;
            float rf = mix(r0, r1, pow(ftc, 1.0 + 1.5 * (1.0 - s.shape * 0.6)));
            float a = atan(dv.y, dv.x) + Time * (2.2 + 3.0 * s.tor) / (1.0 + ftc * 1.5) + p.y * 0.05;
            float nf = vnoise(vec3(cos(a) * 2.5, sin(a) * 2.5, p.y * 0.07 - Time * 0.9));
            float nf2 = vnoise(vec3(cos(a * 3.0) * 6.0, sin(a * 3.0) * 6.0, p.y * 0.02 - Time * 0.6));
            float rr = dr * (1.0 + 0.40 * (nf - 0.5) + 0.20 * (nf2 - 0.5));
            float m = ss(rf, rf * 0.55, rr) * ss(-0.02, 0.06, ft) * (1.0 - ss(0.98, 1.05, ft));
            funD = m * (0.75 + 0.5 * nf + 0.35 * nf2) * ss(0.02, 0.3, s.tor) * 2.6;
            dens += funD;
        }
        // пылевая и обломочная юбка у земли
        float hg = p.y - s.gy;
        if (hg > -3.0 && hg < 60.0 * (0.5 + s.tor)) {
            vec2 dq = p.xz - s.tp;
            float dr = length(dq);
            float rd = 12.0 + R * 0.20 * s.tor;
            float md = ss(rd * (1.0 + hg * 0.02), rd * 0.15, dr) * (1.0 - ss(0.0, 50.0 * (0.4 + s.tor), hg));
            if (md > 0.01) {
                float nd = fbm(vec3(rot(dq, Time * 1.5) * 0.06, p.y * 0.05), 3);
                dustD = md * s.tor * (0.4 + nd) * 1.6;
                dens += dustD * 0.9;
            }
        }
    }

    // дождевая завеса под основанием, смещённая по направлению движения
    if (s.rain > 0.01 && p.y < s.base && p.y > s.gy - 4.0) {
        vec2 rq = p.xz - (s.c + s.dir * R * 0.45);
        float rrad = R * 0.55;
        float lr = length(rq);
        if (lr < rrad) {
            float mR = ss(1.0, 0.2, lr / rrad);
            float nr = vnoise(vec3(rq.x * 0.07, p.y * 0.012 + Time * 0.8, rq.y * 0.07));
            rainD = mR * s.rain * s.inten * (0.25 + 0.75 * nr) * 0.35 * ss(s.base, s.base - 25.0, p.y);
            dens += rainD;
        }
    }
    return dens;
}

void main() {
    vec4 far = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec3 rd = normalize(far.xyz / far.w);
    vec3 ro = vec3(0.0, CamPos.y, 0.0);   // xz — относительно камеры, y — абсолютный

    float tDepth = 1e9;
    float dsmp = texture(DepthSampler, ndc * 0.5 + 0.5).r;
    if (dsmp < 0.99999) {
        vec4 wp = InvViewProj * vec4(ndc, dsmp * 2.0 - 1.0, 1.0);
        tDepth = length(wp.xyz / wp.w);
    }

    int n = int(StormCount);
    float tmin = 1e9;
    float tmax = -1e9;
    for (int i = 0; i < 4; i++) {
        if (i >= n) break;
        StormInfo s = getStorm(i);
        float Rb = s.R * (3.0 + 4.0 * AnvilScale);
        vec3 bmin = vec3(s.c.x - Rb, s.gy - 6.0, s.c.y - Rb);
        vec3 bmax = vec3(s.c.x + Rb, s.top + s.R * 0.15, s.c.y + Rb);
        vec2 hit = boxHit(ro, rd, bmin, bmax);
        if (hit.y > hit.x && hit.y > 0.0) {
            tmin = min(tmin, max(hit.x, 0.0));
            tmax = max(tmax, hit.y);
        }
    }
    tmax = min(tmax, min(tDepth, MaxDist));
    if (tmax <= tmin) {
        fragColor = vec4(0.0);
        return;
    }

    int oct = int(DetailOct);
    int lsteps = int(LightSteps);
    float jit = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float t = tmin + jit * clamp(tmin * StepScale, 4.0, 45.0);

    vec3 acc = vec3(0.0);
    float T = 1.0;
    float cosSun = dot(rd, SunDir);
    float phase = min(mix(hgN(cosSun, -0.2), hgN(cosSun, 0.62), 0.35), 6.0);

    for (int i = 0; i < 256; i++) {
        if (float(i) >= Steps * 2.0 || t > tmax) break;
        vec3 p = ro + rd * t;

        float dens = 0.0;
        float rainS = 0.0;
        float dustS = 0.0;
        float funS = 0.0;
        float hTop = 0.0;
        float best = 0.0;
        int bi = 0;
        bool nearTor = false;
        for (int k = 0; k < 4; k++) {
            if (k >= n) break;
            StormInfo s = getStorm(k);
            if (s.tor > 0.05 && p.y < s.base && length(p.xz - s.tp) < 130.0) nearTor = true;
            float h0;
            float r0;
            float du0;
            float fu0;
            float d = cloudBody(p, s, oct, h0);
            d += extras(p, s, oct, r0, du0, fu0);
            if (d > best) {
                best = d;
                bi = k;
                hTop = h0;
            }
            dens += d;
            rainS += r0;
            dustS += du0;
            funS += fu0;
        }

        float sl = clamp(t * StepScale, 4.0, 45.0);
        if (nearTor) sl = min(sl, 3.0);       // тонкий шаг у воронки — без размазывания

        if (dens > 0.003) {
            StormInfo s = getStorm(bi);
            float tauL = 0.0;
            float lstep = 45.0;
            for (int j = 1; j <= 4; j++) {
                if (j > lsteps) break;
                float hh;
                vec3 lp = p + SunDir * (lstep * float(j) * (1.0 + 0.4 * float(j)));
                tauL += cloudBody(lp, s, 2, hh) * lstep;
            }
            float E = exp(-tauL * 0.05 * Density);
            float f = clamp(dens, 0.0, 1.0);
            float rf = clamp(rainS / max(dens, 1e-3), 0.0, 1.0);
            float df = clamp(dustS / max(dens, 1e-3), 0.0, 1.0);
            float ff = clamp(funS / max(dens, 1e-3), 0.0, 1.0);

            vec3 albedo = vec3(1.0);
            albedo = mix(albedo, vec3(0.50, 0.54, 0.58), rf * 0.8);
            albedo = mix(albedo, vec3(0.46, 0.35, 0.25), df * 0.85);
            albedo = mix(albedo, vec3(0.62, 0.64, 0.68), ff * 0.6);

            float ambF = mix(0.45, 1.0, ss(0.0, 0.8, hTop));
            ambF *= mix(1.0, 0.45, s.inten * (1.0 - hTop));
            vec3 amb = AmbientColor * ambF * mix(0.55, 1.0, E);
            vec3 sunL = SunColor * E * phase * Contrast;
            vec3 fl = vec3(0.65, 0.78, 1.0) * FlashAmt * exp(-length(p - FlashPos) / 260.0) * 3.0;

            vec3 col = albedo * (sunL + amb + fl);
            col *= mix(vec3(1.0), vec3(0.92, 1.0, 0.90), s.inten * 0.35 * (1.0 - hTop));
            col = mix(col, HazeColor, (1.0 - exp(-t * 0.00030)) * 0.6);

            float fade = 1.0 - ss(0.7 * MaxDist, MaxDist, t);
            float sigma = f * 0.06 * Density * fade;
            float a = 1.0 - exp(-sigma * sl);
            acc += T * a * col;
            T *= (1.0 - a);
            if (T < 0.02) break;
            t += sl;
        } else {
            t += sl * 1.5;
        }
    }

    fragColor = vec4(tm(acc), 1.0 - T);
}
