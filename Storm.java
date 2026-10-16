package dev.realweather.storm;

import dev.realweather.MathUtil;
import dev.realweather.config.RWConfig;
import dev.realweather.network.FlashPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Суперячейка по сценарию идеализированного CM1: тёплый пузырь → башенка кучевого → зрелый апдрафт с наковальней →
 * мезоциклон (wall cloud) → циклические торнадо → распад. Среда задаётся CAPE и сдвигом ветра.
 * ВАЖНО: это игровая модель в духе CM1, а не численное решение уравнений CM1.
 */
public final class Storm {
    public final StormState st = new StormState();
    public final RandomSource rnd = RandomSource.create();
    public long age;
    public long life;
    public float goal;
    public float instability = 0.85f;
    public final float cape, shear;
    public final boolean tornadic;
    public float baseHeading;
    public boolean dead;

    private final float mesoPot;
    private final float[] cStart = new float[3], cDur = new float[3], cAmp = new float[3];
    private int cycles;
    private float tgtGrowth, tgtInt, tgtMeso, tgtTor, tgtShape;

    public Storm(int id, double x, double z, float heading, float intensity, boolean tornadic,
                 double startFraction, float cape, float shear) {
        st.id = id;
        st.x = x;
        st.z = z;
        this.baseHeading = heading;
        this.cape = cape;
        this.shear = shear;
        this.goal = Mth.clamp(intensity * Mth.clamp(cape / 3000f, 0.35f, 1.1f), 0.1f, 1f);
        this.mesoPot = Math.min(1f, Mth.clamp(shear / 25f, 0f, 1.2f) * Mth.clamp(cape / 2500f, 0.4f, 1.2f));
        this.tornadic = tornadic;

        st.radius = (float) (200.0 * RWConfig.RADIUS_SCALE.get() * (0.9 + 0.2 * rnd.nextFloat()));
        st.baseY = 140f + rnd.nextFloat() * 25f;
        st.topY = st.baseY + (float) ((250.0 + 60.0 * rnd.nextFloat()) * RWConfig.TOWER_SCALE.get());
        st.speed = (float) ((0.14 + 0.08 * rnd.nextFloat()) * RWConfig.SPEED_SCALE.get());
        life = (long) (RWConfig.LIFETIME_MINUTES.get() * 1200.0 * (0.8 + 0.4 * rnd.nextFloat()));
        age = (long) (life * startFraction);

        // циклический мезоциклогенез: до трёх торнадо подряд (как в CM1)
        float pot = Mth.clamp(RWConfig.TORNADO_INTENSITY.get().floatValue() * (0.5f + 0.5f * mesoPot), 0.1f, 1f);
        if (tornadic) {
            cStart[0] = 0.44f + 0.08f * rnd.nextFloat();
            cDur[0] = 0.12f + 0.06f * rnd.nextFloat();
            cAmp[0] = pot * (0.75f + 0.25f * rnd.nextFloat());
            cycles = 1;
            for (int i = 1; i < 3; i++) {
                float s = cStart[i - 1] + cDur[i - 1] + 0.03f + 0.04f * rnd.nextFloat();
                float chance = (i == 1 ? 0.55f : 0.3f) * Math.min(1f, shear / 20f);
                if (s < 0.78f && rnd.nextFloat() < chance) {
                    cStart[i] = s;
                    cDur[i] = 0.08f + 0.05f * rnd.nextFloat();
                    cAmp[i] = cAmp[i - 1] * 0.85f;
                    cycles++;
                } else break;
            }
        }

        targets((float) age / life);
        st.growth = tgtGrowth;
        st.intensity = tgtInt;
        st.meso = tgtMeso;
        st.tornado = tgtTor;
        st.shape = tgtShape;
        applyHeading((float) age / life);
        st.rain = (float) (RWConfig.RAIN_INTENSITY.get() * MathUtil.smooth(0.15f, 0.5f, st.growth));
    }

    /** Целевые значения по стадии жизни t∈[0,1]. */
    private void targets(float t) {
        float decay = 1f - MathUtil.smooth(0.72f, 1f, t);
        float g = MathUtil.smooth(0f, 0.05f, t) * 0.12f      // тёплый пузырь
                + MathUtil.smooth(0.05f, 0.2f, t) * 0.38f    // растущий кучевой
                + MathUtil.smooth(0.2f, 0.38f, t) * 0.50f;   // зрелый апдрафт + наковальня
        tgtGrowth = g * (0.4f + 0.6f * decay);
        tgtInt = goal * instability * (float) Math.pow(decay, 0.7) * MathUtil.smooth(0.03f, 0.3f, t);
        tgtMeso = MathUtil.smooth(0.25f, 0.45f, t) * mesoPot * decay;
        float tor = 0f, shp = 0.3f;
        for (int i = 0; i < cycles; i++) {
            float b = MathUtil.smooth(cStart[i], cStart[i] + 0.025f, t)
                    * (1f - MathUtil.smooth(cStart[i] + cDur[i] * 0.7f, cStart[i] + cDur[i], t));
            if (b * cAmp[i] > tor) {
                tor = b * cAmp[i];
                float rope = MathUtil.smooth(cStart[i] + cDur[i] * 0.5f, cStart[i] + cDur[i], t);
                shp = MathUtil.lerp(0.8f, 0.1f, rope);
            }
        }
        tgtTor = tor;
        tgtShape = shp * (0.85f + 0.15f * (float) Math.sin(age * 0.0007 + st.id));
    }

    private void applyHeading(float t) {
        // правый мувер: траектория отклоняется вправо от среднего ветра по мере роста мезоциклона
        float h = baseHeading + 0.5f * MathUtil.smooth(0.1f, 0.4f, t) * Math.min(1f, shear / 20f);
        st.dirX = Mth.cos(h);
        st.dirZ = Mth.sin(h);
    }

    public void tick(ServerLevel level) {
        age++;
        float t = (float) age / (float) life;
        if (age % 40 == 0) sampleEnvironment(level);
        targets(t);

        st.growth += (tgtGrowth - st.growth) * 0.05f;
        st.intensity += (tgtInt - st.intensity) * 0.03f;
        st.meso += (tgtMeso - st.meso) * 0.01f;
        st.tornado += (tgtTor - st.tornado) * 0.012f;
        st.tornado = Mth.clamp(st.tornado, 0f, 1f);
        st.shape += (tgtShape - st.shape) * 0.01f;

        baseHeading += (rnd.nextFloat() - 0.5f) * 0.004f;
        applyHeading(t);
        st.x += st.dirX * st.speed;
        st.z += st.dirZ * st.speed;

        float rainTarget = (float) (RWConfig.RAIN_INTENSITY.get() * MathUtil.smooth(0.15f, 0.5f, st.growth));
        st.rain += (rainTarget - st.rain) * 0.02f;

        float r = st.radius;
        float wob = Mth.sin(age * 0.011f + st.id) * r * 0.03f;
        float px = -st.dirZ, pz = st.dirX;
        st.tOffX = -st.dirX * r * 0.10f - px * r * 0.10f + px * wob;
        st.tOffZ = -st.dirZ * r * 0.10f - pz * r * 0.10f + pz * wob;

        if (age % 10 == 0) updateGround(level);
        tickLightning(level);
        if (age >= life) dead = true;
    }

    private void sampleEnvironment(ServerLevel level) {
        Biome biome = level.getBiome(BlockPos.containing(st.x, level.getSeaLevel(), st.z)).value();
        float temp = biome.getBaseTemperature();
        float f = Mth.clamp(0.35f + 0.65f * Mth.clamp(temp, 0f, 1f), 0.25f, 1f);
        if (!biome.hasPrecipitation()) f *= 0.65f;
        float tod = level.getTimeOfDay(1f);
        float heating = 0.8f + 0.2f * Mth.cos(tod * (float) (Math.PI * 2));
        instability += (Mth.clamp(f * heating, 0.2f, 1f) - instability) * 0.5f;
    }

    private void updateGround(ServerLevel level) {
        int bx = Mth.floor(st.tornadoX()), bz = Mth.floor(st.tornadoZ());
        if (level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            st.groundY += (h - st.groundY) * 0.25f;
        } else {
            st.groundY += (level.getSeaLevel() - st.groundY) * 0.02f;
        }
    }

    private void tickLightning(ServerLevel level) {
        float rate = RWConfig.LIGHTNING_RATE.get().floatValue();
        float p = rate / 1200f * st.intensity * st.intensity * MathUtil.smooth(0.3f, 0.6f, st.growth);
        if (rnd.nextFloat() >= p) return;
        float r = st.radius;
        boolean ground = RWConfig.GROUND_STRIKES.get() && rnd.nextFloat() > 0.7f;
        if (!ground) {
            double x = st.x + (rnd.nextFloat() - 0.5f) * r * 1.2f;
            double z = st.z + (rnd.nextFloat() - 0.5f) * r * 1.2f;
            double y = st.baseY + (st.topY - st.baseY) * (0.25 + 0.6 * rnd.nextFloat()) * Math.max(st.growth, 0.1f);
            PacketDistributor.sendToPlayersInDimension(level, new FlashPayload((float) x, (float) y, (float) z, true));
        } else {
            double cx = st.x + st.dirX * r * 0.45, cz = st.z + st.dirZ * r * 0.45;
            double ang = rnd.nextDouble() * Math.PI * 2, rad = Math.sqrt(rnd.nextDouble()) * r * 0.5;
            double x = cx + Math.cos(ang) * rad, z = cz + Math.sin(ang) * rad;
            int bx = Mth.floor(x), bz = Mth.floor(z);
            if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) return;
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(x, y, z);
                bolt.setVisualOnly(!RWConfig.LIGHTNING_DAMAGE.get());
                level.addFreshEntity(bolt);
            }
            PacketDistributor.sendToPlayersInDimension(level, new FlashPayload((float) x, (float) (y + 90), (float) z, false));
        }
    }
}
