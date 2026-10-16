package dev.realweather.tornado;

import dev.realweather.config.RWConfig;
import net.minecraft.network.FriendlyByteBuf;

/** Параметры вихря, которые сервер рассылает клиентам (клиент применяет их к своему игроку и радару). */
public record TornadoParams(float spin, float inflow, float lift, float influence, float turbulence,
                            float playerMult, boolean affectCreative, boolean resetFall,
                            float anvil, boolean hail) {

    public static final TornadoParams DEFAULT =
            new TornadoParams(1f, 1f, 1f, 70f, 0.25f, 0.6f, false, true, 1f, true);

    public static TornadoParams fromConfig() {
        return new TornadoParams(
                RWConfig.SPIN.get().floatValue(), RWConfig.INFLOW.get().floatValue(),
                RWConfig.LIFT.get().floatValue(), RWConfig.INFLUENCE_RADIUS.get().floatValue(),
                RWConfig.TURBULENCE.get().floatValue(), RWConfig.PLAYER_MULT.get().floatValue(),
                RWConfig.AFFECT_CREATIVE.get(), RWConfig.RESET_FALL.get(),
                RWConfig.ANVIL_SCALE.get().floatValue(), RWConfig.HAIL.get());
    }

    public void write(FriendlyByteBuf b) {
        b.writeFloat(spin); b.writeFloat(inflow); b.writeFloat(lift); b.writeFloat(influence);
        b.writeFloat(turbulence); b.writeFloat(playerMult);
        b.writeBoolean(affectCreative); b.writeBoolean(resetFall);
        b.writeFloat(anvil); b.writeBoolean(hail);
    }

    public static TornadoParams read(FriendlyByteBuf b) {
        return new TornadoParams(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readBoolean());
    }
}
