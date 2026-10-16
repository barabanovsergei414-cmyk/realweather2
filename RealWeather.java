package dev.realweather;

import com.mojang.logging.LogUtils;
import dev.realweather.command.RWCommands;
import dev.realweather.config.RWConfig;
import dev.realweather.network.Net;
import dev.realweather.storm.StormManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * RealWeather: общая (серверная + клиентская) точка входа.
 * Игровая модель погоды работает на сервере, клиент только рисует и синхронизируется.
 * ВАЖНО: это упрощённая игровая симуляция, а не расчёт CM1.
 */
@Mod(RealWeather.MODID)
public class RealWeather {
    public static final String MODID = "realweather";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealWeather(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, RWConfig.SERVER_SPEC);
        modBus.addListener(Net::register);
        NeoForge.EVENT_BUS.addListener(StormManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(StormManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(RWCommands::register);
    }
}
