package dev.realweather;

import net.minecraft.util.Mth;

/** Небольшие математические помощники (smoothstep и т.п.). */
public final class MathUtil {
    private MathUtil() {}

    /** smoothstep; работает и при e0 > e1 (обратная кривая). */
    public static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static double smoothd(double e0, double e1, double x) {
        double t = Mth.clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
