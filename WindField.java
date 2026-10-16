package dev.realweather.tornado;

import dev.realweather.MathUtil;

/**
 * Единое векторное поле ветра торнадо. Используется сервером (физика сущностей),
 * клиентом (движение игрока, частицы) и радаром (радиальная скорость) — одни и те же формулы.
 * Единицы: блоков за тик. out = {vx, vy, vz}.
 */
public final class WindField {
    private WindField() {}

    /**
     * @param dx,dz  смещение от оси вихря
     * @param h      высота над землёй
     * @param s      сила 0..1
     * @param height высота вихря (до облачной базы)
     * @param time   время в тиках (для турбулентности)
     */
    public static void tornado(double dx, double dz, double h, double s,
                               double spin, double inflow, double lift, double influence,
                               double turb, double height, double time, double[] out) {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (!(r >= 0.0) || Double.isInfinite(r)) r = 0.0;
        double rr = Math.max(r, 0.25);                 // защита от деления на ноль
        double ex = dx / rr, ez = dz / rr;
        s = Math.max(0.0, Math.min(1.0, s));
        double rInf = Math.max(influence * (0.5 + s), 1.0);
        double w = 1.0 - MathUtil.smoothd(0.35 * rInf, rInf, rr);   // плавное окно влияния
        double rc = 3.0 + 7.0 * s;                                   // радиус ядра
        double vmax = (0.15 + 0.75 * s) * spin;

        // тангенциальная скорость: твёрдое вращение внутри ядра, спад снаружи (профиль типа Ранкина)
        double vt = rr < rc ? vmax * rr / rc : vmax * Math.pow(rc / rr, 0.55);
        double turbF = 1.0 + turb * 0.35 * Math.sin(time * 0.21 + rr * 0.35 + h * 0.11);
        vt *= w * turbF;

        // радиальный приток, сильнее у поверхности
        double hg = Math.max(h, 0.0);
        double surf = Math.exp(-hg / 30.0);
        double vin = vmax * 0.45 * inflow * (0.35 + 0.65 * surf) * w * MathUtil.smoothd(0.0, rc, rr);

        // восходящий поток: сосредоточен у ядра, затухает у верха вихря
        double core = Math.exp(-Math.pow(rr / (rc * 3.0), 2.0));
        double topFade = 1.0 - MathUtil.smoothd(0.7 * height, Math.max(height, 1.0), hg);
        double vup = lift * s * (0.10 + 0.55 * core) * w * MathUtil.smoothd(-2.0, 6.0, h) * topFade;

        double jx = turb * 0.05 * s * w * Math.sin(time * 0.37 + h * 0.2);
        double jz = turb * 0.05 * s * w * Math.cos(time * 0.31 + h * 0.17);

        // тангенциальное направление (против часовой стрелки при виде сверху): (-ez, ex)
        double vx = vt * (-ez) - vin * ex + jx;
        double vz = vt * ex - vin * ez + jz;
        double vy = vup;

        if (!Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz)) {
            vx = 0; vy = 0; vz = 0;
        }
        double hs = Math.sqrt(vx * vx + vz * vz);
        if (hs > 1.6) { vx *= 1.6 / hs; vz *= 1.6 / hs; }
        vy = Math.max(-1.0, Math.min(1.0, vy));
        out[0] = vx; out[1] = vy; out[2] = vz;
    }

    /** Широкая вращающаяся область мезоциклона (для радара): профиль Ранкина, блоков за тик. */
    public static void mesocyclone(double dx, double dz, double radius, double strength, double[] out) {
        double rr = Math.max(Math.sqrt(dx * dx + dz * dz), 0.5);
        double rc = radius * 0.35;
        double vmax = 0.14 * strength;
        double vt = rr < rc ? vmax * rr / rc : vmax * Math.pow(rc / rr, 0.7);
        vt *= 1.0 - MathUtil.smoothd(radius * 0.9, radius * 2.2, rr);
        out[0] = vt * (-dz / rr);
        out[1] = 0;
        out[2] = vt * (dx / rr);
    }
}
