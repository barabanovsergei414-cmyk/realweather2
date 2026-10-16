package dev.realweather.network;

import dev.realweather.RealWeather;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Вспышка молнии: позиция и тип (true = внутри облака, гром приходит с задержкой от расстояния). */
public record FlashPayload(float x, float y, float z, boolean intraCloud) implements CustomPacketPayload {
    public static final Type<FlashPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealWeather.MODID, "flash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlashPayload> CODEC =
            StreamCodec.ofMember(FlashPayload::write, FlashPayload::read);

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeFloat(z);
        buf.writeBoolean(intraCloud);
    }

    public static FlashPayload read(RegistryFriendlyByteBuf buf) {
        return new FlashPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
