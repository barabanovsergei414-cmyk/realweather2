package dev.realweather.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Net {
    private Net() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(StormSyncPayload.TYPE, StormSyncPayload.CODEC, ClientHandlers::onStorms);
        r.playToClient(FlashPayload.TYPE, FlashPayload.CODEC, ClientHandlers::onFlash);
    }
}
