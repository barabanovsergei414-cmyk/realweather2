package dev.realweather.storm;

import dev.realweather.config.RWConfig;
import dev.realweather.network.StormSyncPayload;
import dev.realweather.tornado.TornadoParams;
import dev.realweather.tornado.TornadoPhysics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Единый серверный источник погодных данных: хранит штормы и тикает их в Оверворлде. */
public final class StormManager {
    private static final Map<ResourceKey<Level>, List<Storm>> STORMS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Boolean> SENT_EMPTY = new HashMap<>();
    private static int nextId = 1;

    private StormManager() {}

    public static List<Storm> get(ServerLevel level) {
        return STORMS.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
    }

    public static void onServerStopped(ServerStoppedEvent e) {
        STORMS.clear();
        SENT_EMPTY.clear();
        nextId = 1;
    }

    public static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        List<Storm> list = get(level);
        long gt = level.getGameTime();

        naturalSpawn(level, list);
        boolean removed = false;
        if (!list.isEmpty()) {
            TornadoParams params = TornadoParams.fromConfig();
            Iterator<Storm> it = list.iterator();
            while (it.hasNext()) {
                Storm s = it.next();
                s.tick(level);
                if (s.dead || !playerNear(level, s)) {
                    it.remove();
                    removed = true;
                    continue;
                }
                TornadoPhysics.apply(level, s, params, gt);
            }
        }
        boolean empty = list.isEmpty();
        boolean due = gt % RWConfig.SYNC_INTERVAL.get() == 0;
        if (!empty && (due || removed)) {
            sync(level, list);
            SENT_EMPTY.put(level.dimension(), false);
        } else if (empty && !Boolean.TRUE.equals(SENT_EMPTY.get(level.dimension()))) {
            sync(level, list);
            SENT_EMPTY.put(level.dimension(), true);
        }
    }

    private static boolean playerNear(ServerLevel level, Storm s) {
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - s.st.x, dz = p.getZ() - s.st.z;
            if (dx * dx + dz * dz < 3000.0 * 3000.0) return true;
        }
        return false;
    }

    private static void naturalSpawn(ServerLevel level, List<Storm> list) {
        if (!RWConfig.NATURAL_STORMS.get() || list.size() >= RWConfig.MAX_STORMS.get()) return;
        if (level.getGameTime() % 20 != 0) return;
        if (level.random.nextDouble() > RWConfig.NATURAL_CHANCE_PER_MIN.get() / 60.0) return;
        List<ServerPlayer> ps = level.players();
        if (ps.isEmpty()) return;
        ServerPlayer pl = ps.get(level.random.nextInt(ps.size()));
        double ang = level.random.nextDouble() * Math.PI * 2;
        double dist = 700 + level.random.nextDouble() * 500;
        double x = pl.getX() + Math.cos(ang) * dist, z = pl.getZ() + Math.sin(ang) * dist;
        float heading = (float) Math.atan2(pl.getZ() - z, pl.getX() - x) + (level.random.nextFloat() - 0.5f);
        spawn(level, x, z, heading, 0.5f + 0.5f * level.random.nextFloat(),
                level.random.nextDouble() < RWConfig.TORNADO_CHANCE.get(), 0.0,
                1200f + 2800f * level.random.nextFloat(), 8f + 22f * level.random.nextFloat());
    }

    public static Storm spawn(ServerLevel level, double x, double z, float heading, float intensity,
                              boolean tornadic, double startFraction, float cape, float shear) {
        List<Storm> list = get(level);
        if (list.size() >= RWConfig.MAX_STORMS.get()) return null;
        Storm s = new Storm(nextId++, x, z, heading, intensity, tornadic, startFraction, cape, shear);
        list.add(s);
        sync(level, list);
        SENT_EMPTY.put(level.dimension(), false);
        return s;
    }

    public static void clear(ServerLevel level) {
        List<Storm> list = get(level);
        list.clear();
        sync(level, list);
        SENT_EMPTY.put(level.dimension(), true);
    }

    public static void sync(ServerLevel level, List<Storm> list) {
        List<StormState> states = new ArrayList<>(list.size());
        for (Storm s : list) states.add(s.st.copy());
        PacketDistributor.sendToPlayersInDimension(level, new StormSyncPayload(TornadoParams.fromConfig(), states));
    }
}
