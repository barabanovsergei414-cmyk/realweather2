package dev.realweather.tornado;

import dev.realweather.config.RWConfig;
import dev.realweather.storm.Storm;
import dev.realweather.storm.StormState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Серверная физика торнадо для мобов и предметов.
 * Движение ИГРОКОВ в Minecraft клиент-авторитарно, поэтому силы на игрока применяет клиент
 * (ClientEvents) по тем же формулам из синхронизированных данных; сервер отвечает за урон у ядра.
 */
public final class TornadoPhysics {
    private TornadoPhysics() {}

    private static boolean affects(Entity e) {
        if (!e.isAlive() || e.isSpectator() || e.isPassenger()) return false;
        if (e instanceof EnderDragon || e instanceof WitherBoss) return false;
        return e instanceof LivingEntity || e instanceof ItemEntity || e instanceof FallingBlockEntity;
    }

    public static void apply(ServerLevel level, Storm storm, TornadoParams p, long time) {
        StormState st = storm.st;
        if (st.tornado < 0.03f) return;
        double tx = st.tornadoX(), tz = st.tornadoZ(), gy = st.groundY;
        double rInf = p.influence() * (0.5 + st.tornado);
        double height = Math.max(st.baseY - gy, 20.0);
        AABB box = new AABB(tx - rInf, gy - 8, tz - rInf, tx + rInf, gy + height, tz + rInf);
        double coreR = 3.0 + 7.0 * st.tornado;
        double entityMult = RWConfig.ENTITY_MULT.get();
        double coreDamage = RWConfig.CORE_DAMAGE.get();
        double[] v = new double[3];

        List<Entity> list = level.getEntities((Entity) null, box, TornadoPhysics::affects);
        for (Entity e : list) {
            double dx = e.getX() - tx, dz = e.getZ() - tz;
            double d2 = dx * dx + dz * dz;
            if (d2 > rInf * rInf) continue;

            if (coreDamage > 0 && time % 10 == 0 && d2 < (coreR * 1.6) * (coreR * 1.6) && e instanceof LivingEntity le
                    && !(e instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator()))) {
                le.hurt(level.damageSources().generic(), (float) (coreDamage * st.tornado));
            }
            if (e instanceof net.minecraft.world.entity.player.Player) continue; // игроков двигает клиент
            if (entityMult <= 0) continue;

            WindField.tornado(dx, dz, e.getY() - gy, st.tornado, p.spin(), p.inflow(), p.lift(), p.influence(),
                    p.turbulence(), height, time, v);
            double k = Mth.clamp(0.12 * entityMult, 0.0, 0.4);
            Vec3 c = e.getDeltaMovement();
            double nx = c.x + (v[0] - c.x) * k;
            double nz = c.z + (v[2] - c.z) * k;
            // компенсация гравитации, чтобы восходящий поток реально поднимал
            double ny = c.y + (v[1] - c.y) * k + (v[1] > 0.02 ? 0.0784 * Mth.clamp(v[1] / 0.25, 0.0, 1.0) : 0.0);
            if (!Double.isFinite(nx) || !Double.isFinite(ny) || !Double.isFinite(nz)) continue;
            double hs = Math.sqrt(nx * nx + nz * nz);
            if (hs > 1.6) { nx *= 1.6 / hs; nz *= 1.6 / hs; }
            ny = Mth.clamp(ny, -1.0, 1.0);
            e.setDeltaMovement(nx, ny, nz);
            e.hurtMarked = true;
            if (p.resetFall()) e.resetFallDistance();
        }
    }
}
