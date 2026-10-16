package dev.realweather.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.realweather.client.radar.RadarScreen;
import dev.realweather.config.ClientConfig;
import dev.realweather.config.Quality;
import dev.realweather.storm.StormState;
import dev.realweather.tornado.TornadoParams;
import dev.realweather.tornado.WindField;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

/** Клиентские события: движение игрока в вихре, локальный дождь, частицы, радар, цвет тумана. */
public final class ClientEvents {
    public static KeyMapping RADAR_KEY;
    private static final float[] FOG = new float[3];
    private static final double[] WIND = new double[3];

    private ClientEvents() {}

    public static void createKeys() {
        RADAR_KEY = new KeyMapping("key.realweather.radar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
                "key.categories.realweather");
    }

    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
        ClientStorms.clear();
        if (ClientConfig.DISABLE_VANILLA_CLOUDS.get()) {
            Minecraft.getInstance().options.cloudStatus().set(CloudStatus.OFF);
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientStorms.clear();
    }

    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        while (RADAR_KEY != null && RADAR_KEY.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new RadarScreen());
        }
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        if (level.dimension() != Level.OVERWORLD) return;
        ClientStorms.tick(level);
        if (!ClientStorms.active() || !ClientConfig.ENABLED.get()) return;
        double now = level.getGameTime();
        applyPlayerWind(mc.player, now);
        weatherLevels(mc.player, level, now);
        particles(mc.player, level, now);
    }

    /** Силы на игрока: клиент авторитарен за своё движение, данные и формулы — серверные. */
    private static void applyPlayerWind(Player p, double now) {
        TornadoParams prm = ClientStorms.params;
        if (prm.playerMult() <= 0f || p.isSpectator() || p.getAbilities().flying) return;
        if (p.isCreative() && !prm.affectCreative()) return;
        for (ClientStorms.Entry en : ClientStorms.entries()) {
            StormState st = en.st;
            if (st.tornado < 0.03f) continue;
            double dx = p.getX() - en.tx(now), dz = p.getZ() - en.tz(now);
            double rInf = prm.influence() * (0.5 + st.tornado);
            if (dx * dx + dz * dz > rInf * rInf) continue;
            double h = p.getY() - st.groundY;
            double height = Math.max(st.baseY - st.groundY, 20.0);
            if (h < -8 || h > height) continue;
            WindField.tornado(dx, dz, h, st.tornado, prm.spin(), prm.inflow(), prm.lift(), prm.influence(),
                    prm.turbulence(), height, now, WIND);
            double k = Mth.clamp(0.10 * prm.playerMult(), 0.0, 0.35);
            Vec3 v = p.getDeltaMovement();
            double nx = v.x + (WIND[0] - v.x) * k;
            double nz = v.z + (WIND[2] - v.z) * k;
            double ny = v.y + (WIND[1] - v.y) * k
                    + (WIND[1] > 0.02 ? 0.0784 * Mth.clamp(WIND[1] / 0.25, 0.0, 1.0) : 0.0);
            if (!Double.isFinite(nx) || !Double.isFinite(ny) || !Double.isFinite(nz)) continue;
            double hs = Math.sqrt(nx * nx + nz * nz);
            if (hs > 1.6) { nx *= 1.6 / hs; nz *= 1.6 / hs; }
            ny = Mth.clamp(ny, -1.0, 1.0);
            p.setDeltaMovement(nx, ny, nz);
            if (prm.resetFall()) p.resetFallDistance();
        }
    }

    /** Локальный дождь/гроза: ванильные уровни зависят от близости к дождевому ядру. */
    private static void weatherLevels(Player p, ClientLevel level, double now) {
        float r = ClientStorms.rainAt(p.getX(), p.getZ(), now);
        float maxInt = 0f;
        for (ClientStorms.Entry en : ClientStorms.entries()) maxInt = Math.max(maxInt, en.st.intensity);
        level.setRainLevel(r);
        level.setThunderLevel(maxInt > 0.5f ? r : 0f);
    }

    private static void particles(Player p, ClientLevel level, double now) {
        Quality q = ClientConfig.QUALITY.get();
        TornadoParams prm = ClientStorms.params;
        var rnd = level.random;

        for (ClientStorms.Entry en : ClientStorms.entries()) {
            StormState st = en.st;
            double R = st.radius;
            // град: только в сильной ячейке и только рядом с игроком, ограниченное число частиц
            if (prm.hail() && st.intensity > 0.75f) {
                double cx = en.x(now) + st.dirX * R * 0.45, cz = en.z(now) + st.dirZ * R * 0.45;
                if (Math.hypot(p.getX() - cx, p.getZ() - cz) < R * 0.35) {
                    int n = Math.max(1, q.particles / 3);
                    for (int i = 0; i < n; i++) {
                        level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SNOWBALL)),
                                p.getX() + (rnd.nextDouble() - 0.5) * 24, p.getY() + 12 + rnd.nextDouble() * 6,
                                p.getZ() + (rnd.nextDouble() - 0.5) * 24, 0, -1.0, 0);
                    }
                }
            }
            // обломки у основания торнадо, скорость берётся из того же поля ветра
            if (ClientConfig.DEBRIS.get() && st.tornado > 0.15f) {
                double tx = en.tx(now), tz = en.tz(now);
                if (Math.hypot(p.getX() - tx, p.getZ() - tz) < 170) {
                    double rInf = prm.influence() * (0.5 + st.tornado);
                    double height = Math.max(st.baseY - st.groundY, 20.0);
                    for (int i = 0; i < q.particles; i++) {
                        double ang = rnd.nextDouble() * Math.PI * 2;
                        double rad = Math.sqrt(rnd.nextDouble()) * rInf * 0.35;
                        double px = tx + Math.cos(ang) * rad, pz = tz + Math.sin(ang) * rad;
                        double py = st.groundY + rnd.nextDouble() * 3.0;
                        BlockState bs = level.getBlockState(BlockPos.containing(px, st.groundY - 1.0, pz));
                        if (bs.isAir()) continue;
                        WindField.tornado(px - tx, pz - tz, py - st.groundY, st.tornado, prm.spin(), prm.inflow(),
                                prm.lift(), prm.influence(), prm.turbulence(), height, now, WIND);
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, bs), px, py, pz,
                                WIND[0] * 1.2, Math.max(WIND[1], 0.05) * 1.5, WIND[2] * 1.2);
                    }
                }
            }
        }
    }

    /** Туман на горизонте совпадает с цветом горизонта процедурного неба. */
    public static void onFogColor(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        if (!ClientConfig.ENABLED.get() || !ClientConfig.CUSTOM_SKY.get()) return;
        if (e.getCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return;
        float pt = (float) e.getPartialTick();
        Vec3 cp = e.getCamera().getPosition();
        SkyModel.INSTANCE.update(level, pt, cp.x, cp.z, level.getGameTime() + pt);
        SkyModel.INSTANCE.fogColor(FOG);
        e.setRed(FOG[0]);
        e.setGreen(FOG[1]);
        e.setBlue(FOG[2]);
    }
}
