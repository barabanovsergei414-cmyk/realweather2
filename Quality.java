package dev.realweather.config;

/** Профили качества: меняют только стоимость визуализации, но не физику. */
public enum Quality {
    //   шагов  дальность  октав  свет  частиц  масштаб шага  шагов в кучевых
    LOW(64, 2200f, 3, 2, 2, 0.050f, 10),
    MEDIUM(96, 3200f, 4, 3, 4, 0.035f, 16),
    HIGH(128, 4500f, 5, 3, 8, 0.025f, 24),
    ULTRA(192, 6500f, 5, 4, 14, 0.018f, 32);

    public final int steps;
    public final float maxDist;
    public final int oct;
    public final int lightSteps;
    public final int particles;
    public final float stepScale;
    public final int cloudSteps;

    Quality(int steps, float maxDist, int oct, int lightSteps, int particles, float stepScale, int cloudSteps) {
        this.steps = steps;
        this.maxDist = maxDist;
        this.oct = oct;
        this.lightSteps = lightSteps;
        this.particles = particles;
        this.stepScale = stepScale;
        this.cloudSteps = cloudSteps;
    }
}
