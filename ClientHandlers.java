package dev.realweather.network;

import dev.realweather.client.ClientStorms;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Обработчики на клиенте. Класс не инициализируется на dedicated server (обработчик там не вызывается). */
public final class ClientHandlers {
    private ClientHandlers() {}

    public static void onStorms(StormSyncPayload p, IPayloadContext ctx) {
        ClientStorms.update(p);
    }

    public static void onFlash(FlashPayload p, IPayloadContext ctx) {
        ClientStorms.addFlash(p.x(), p.y(), p.z(), p.intraCloud());
    }
}
