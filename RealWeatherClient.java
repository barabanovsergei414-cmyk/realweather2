package dev.realweather;

import dev.realweather.client.ClientEvents;
import dev.realweather.client.StormRenderer;
import dev.realweather.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Клиентская точка входа; загружается только на физическом клиенте. */
@Mod(value = RealWeather.MODID, dist = Dist.CLIENT)
public class RealWeatherClient {
    public RealWeatherClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(StormRenderer::registerShaders);
        modBus.addListener(RealWeatherClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(StormRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onFogColor);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLogout);
    }

    private static void registerKeys(RegisterKeyMappingsEvent e) {
        ClientEvents.createKeys();
        e.register(ClientEvents.RADAR_KEY);
    }
}
