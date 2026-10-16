package dev.realweather.client;

import dev.realweather.MathUtil;
import dev.realweather.network.StormSyncPayload;
import dev.realweather.storm.StormState;
import dev.realweather.tornado.TornadoParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Клиентское хранилище штормов: экстраполяция положения, история для радара, вспышки и отложенный гром. */
public final class ClientStorms {
    private ClientStorms() {}

    public static final class Entry {
        public final StormState st;
        public final long recv;

        Entry(StormState st, long recv) { this.st = st; this.recv = recv; }

        public double x(double now) { return st.x + st.dirX * st.speed * (now - recv); }
        public double z(double now) { return st.z + st.dirZ * st.speed * (now - recv); }
        public double tx(double now) { return x(now) + st.tOffX; }
        public double tz(double now) { return z(now) + st.tOffZ; }
    }

    public static final class Snapshot {
        public final long time;
        public final List<StormState> storms;

        Snapshot(long time, List<StormState> storms) { this.time = time; this.storms = storms; }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final List<Snapshot> HISTORY = new ArrayList<>();
    private static final List<double[]> THUNDER = new ArrayList<>();   // x,y,z,playAt,volume
    public static TornadoParams params = TornadoParams.DEFAULT;
    private static double flashT0 = -1e9;
    private static double flashX, flashY, flashZ;

    public static List<Entry> entries() { return ENTRIES; }

    public static boolean active() { return !ENTRIES.isEmpty(); }

    public static void clear() {
        ENTRIES.clear();
        HISTORY.clear();
        THUNDER.clear();
        flashT0 = -1e9;
        params = TornadoParams.DEFAULT;
    }

    public static void update(StormSyncPayload p) {
        Level lvl = Minecraft.getInstance().level;
        if (lvl == null) return;
        long gt = lvl.getGameTime();
        params = p.params();
        ENTRIES.clear();
        for (StormState s : p.storms()) ENTRIES.add(new Entry(s, gt));
    }

    public static Snapshot live(double now) {
        List<StormState> list = new ArrayList<>();
        for (Entry e : ENTRIES) list.add(e.st.at(now - e.recv));
        return new Snapshot((long) now, list);
    }

    public static List<Snapshot> history() { return HISTORY; }

    /** Вызывается каждый клиентский тик в Оверворлде. */
    public static void tick(ClientLevel level) {
        long gt = level.getGameTime();
        if (gt % 100 == 0 && active()) {
            HISTORY.add(live(gt));
            while (HISTORY.size() > 36) HISTORY.remove(0);
        }
        if (!THUNDER.isEmpty()) {
            Iterator<double[]> it = THUNDER.iterator();
            while (it.hasNext()) {
                double[] t = it.next();
                if (gt >= t[3]) {
                    level.playLocalSound(t[0], t[1], t[2], SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                            (float) t[4], 0.8f + level.random.nextFloat() * 0.3f, false);
                    it.remove();
                }
            }
        }
    }

    public static void addFlash(float x, float y, float z, boolean intraCloud) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        double now = level.getGameTime();
        flashT0 = now;
        flashX = x; flashY = y; flashZ = z;
        if (intraCloud) {
            double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double delay = d / 17.0;            // ~343 м/с при 20 тиках/с
            float vol = (float) Mth.clamp(8.0 - d / 100.0, 1.0, 8.0);
            if (THUNDER.size() < 16) THUNDER.add(new double[]{x, y, z, now + delay, vol});
        }
    }

    /** Яркость вспышки 0..1 (короткое мерцание). */
    public static float flashAmount(double now) {
        double dt = (now - flashT0) / 20.0;
        if (dt < 0 || dt > 1.2) return 0f;
        double v = Math.exp(-dt * 5.0) * (0.65 + 0.35 * Math.sin(dt * 60.0));
        return (float) Math.max(0.0, v);
    }

    public static double flashX() { return flashX; }
    public static double flashY() { return flashY; }
    public static double flashZ() { return flashZ; }

    /** Затемнение неба над точкой (под башней и под наковальней). */
    public static float darknessAt(double x, double z, double now) {
        float best = 0f;
        for (Entry e : ENTRIES) {
            StormState s = e.st;
            double R = s.radius;
            double cx = e.x(now), cz = e.z(now);
            float k = s.intensity * MathUtil.smooth(0.05f, 0.5f, s.growth);
            double d1 = Math.hypot(x - cx, z - cz);
            float a = k * MathUtil.smooth((float) (R * 3.5), (float) (R * 0.8), (float) d1);
            double d2 = Math.hypot(x - (cx + s.dirX * R * 1.5), z - (cz + s.dirZ * R * 1.5));
            float b = k * 0.8f * MathUtil.smooth((float) (R * 3.4), (float) (R * 0.6), (float) d2);
            best = Math.max(best, Math.max(a, b));
        }
        return Mth.clamp(best, 0f, 1f);
    }

    /** Локальный уровень дождя под дождевым ядром. */
    public static float rainAt(double x, double z, double now) {
        float best = 0f;
        for (Entry e : ENTRIES) {
            StormState s = e.st;
            double R = s.radius;
            double cx = e.x(now) + s.dirX * R * 0.45, cz = e.z(now) + s.dirZ * R * 0.45;
            double d = Math.hypot(x - cx, z - cz);
            float f = s.intensity * s.rain * MathUtil.smooth((float) (R * 0.7), (float) (R * 0.3), (float) d);
            best = Math.max(best, f);
        }
        return Mth.clamp(best, 0f, 1f);
    }
}
