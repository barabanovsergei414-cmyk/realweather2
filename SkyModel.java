package dev.realweather.client;

import dev.realweather.MathUtil;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Палитра неба, рассчитываемая непрерывно из высоты солнца и грозового затемнения (никаких
 * жёстких переключений день/вечер/ночь). Те же значения идут в шейдеры неба/облаков и в цвет тумана.
 * Все цвета линейные; тонемаппинг tm(c)=1-exp(-1.2c) одинаков в шейдерах и здесь.
 */
public final class SkyModel {
    public static final SkyModel INSTANCE = new SkyModel();

    public final float[] sunDir = new float[3];
    public final float[] zenith = new float[3];
    public final float[] horizon = new float[3];
    public final float[] sunColor = new float[3];
    public final float[] ambient = new float[3];
    public final float[] glow = new float[3];
    public float day, night, twilight, darkness, sunH;

    private static final float[] NIGHT_Z = {0.004f, 0.007f, 0.022f};
    private static final float[] DAY_Z = {0.10f, 0.27f, 0.72f};
    private static final float[] NIGHT_H = {0.018f, 0.024f, 0.050f};
    private static final float[] DAY_H = {0.55f, 0.72f, 0.95f};
    private static final float[] TW_H = {0.85f, 0.34f, 0.14f};
    private static final float[] SUN_LOW = {1.0f, 0.40f, 0.15f};
    private static final float[] SUN_HIGH = {1.0f, 0.94f, 0.84f};
    private static final float[] AMB_NIGHT = {0.03f, 0.04f, 0.08f};
    private static final float[] AMB_DAY = {0.40f, 0.52f, 0.75f};
    private static final float[] STORM_Z = {0.10f, 0.12f, 0.14f};
    private static final float[] STORM_H = {0.24f, 0.27f, 0.26f};

    private SkyModel() {}

    public void update(ClientLevel level, float pt, double cx, double cz, double now) {
        float tod = level.getTimeOfDay(pt);
        double th = tod * Math.PI * 2.0;
        sunDir[0] = (float) -Math.sin(th);
        sunDir[1] = (float) Math.cos(th);
        sunDir[2] = 0f;
        sunH = sunDir[1];
        darkness = ClientStorms.darknessAt(cx, cz, now);

        day = MathUtil.smooth(-0.10f, 0.22f, sunH);
        night = 1f - MathUtil.smooth(-0.22f, 0.02f, sunH);
        float q = (sunH + 0.02f) / 0.16f;
        twilight = (float) Math.exp(-q * q);

        for (int i = 0; i < 3; i++) {
            zenith[i] = MathUtil.lerp(NIGHT_Z[i], DAY_Z[i], day);
            horizon[i] = MathUtil.lerp(NIGHT_H[i], DAY_H[i], day) + twilight * TW_H[i] * 0.55f;
            ambient[i] = MathUtil.lerp(AMB_NIGHT[i], AMB_DAY[i], day);
            glow[i] = twilight * SUN_LOW[i];
        }
        zenith[0] += twilight * 0.03f; zenith[1] += twilight * 0.02f; zenith[2] += twilight * 0.07f;
        ambient[0] += twilight * 0.22f; ambient[1] += twilight * 0.12f; ambient[2] += twilight * 0.10f;

        float sunVis = MathUtil.smooth(-0.08f, 0.06f, sunH);
        float warm = MathUtil.smooth(0.0f, 0.4f, sunH);
        for (int i = 0; i < 3; i++) {
            sunColor[i] = MathUtil.lerp(SUN_LOW[i], SUN_HIGH[i], warm) * sunVis * 1.2f;
        }

        // грозовое затемнение: небо уходит в серый, солнце гаснет
        float dk = darkness;
        float dayMix = 0.25f + 0.75f * day;
        for (int i = 0; i < 3; i++) {
            zenith[i] = MathUtil.lerp(zenith[i], STORM_Z[i] * dayMix, dk);
            horizon[i] = MathUtil.lerp(horizon[i], STORM_H[i] * (0.2f + 0.8f * day), dk * 0.85f);
            sunColor[i] *= 1f - 0.8f * dk;
            glow[i] *= 1f - dk;
            ambient[i] *= 1f - 0.45f * dk;
        }
    }

    /** Цвет тумана = тонемаппированный цвет горизонта (чтобы дальний рельеф сливался с небом). */
    public void fogColor(float[] out) {
        for (int i = 0; i < 3; i++) out[i] = 1f - (float) Math.exp(-horizon[i] * 1.2f);
    }
}
