package dev.realweather.network;

import dev.realweather.RealWeather;
import dev.realweather.storm.StormState;
import dev.realweather.tornado.TornadoParams;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Полный список штормов измерения + параметры вихря. Рассылается редко (см. syncIntervalTicks). */
public record StormSyncPayload(TornadoParams params, List<StormState> storms) implements CustomPacketPayload {
    public static final Type<StormSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealWeather.MODID, "storm_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StormSyncPayload> CODEC =
            StreamCodec.ofMember(StormSyncPayload::write, StormSyncPayload::read);

    public void write(RegistryFriendlyByteBuf buf) {
        params.write(buf);
        buf.writeVarInt(storms.size());
        for (StormState s : storms) s.write(buf);
    }

    public static StormSyncPayload read(RegistryFriendlyByteBuf buf) {
        TornadoParams p = TornadoParams.read(buf);
        int n = Math.min(buf.readVarInt(), 16);
        List<StormState> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(StormState.read(buf));
        return new StormSyncPayload(p, list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
