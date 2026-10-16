package dev.realweather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Клиентская конфигурация (качество и визуальные параметры). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.EnumValue<Quality> QUALITY;
    public static final ModConfigSpec.BooleanValue CUSTOM_SKY;
    public static final ModConfigSpec.BooleanValue AMBIENT_CLOUDS;
    public static final ModConfigSpec.BooleanValue STORM_CLOUDS;
    public static final ModConfigSpec.DoubleValue CLOUD_DENSITY;
    public static final ModConfigSpec.DoubleValue CONTRAST;
    public static final ModConfigSpec.DoubleValue VIEW_DISTANCE;
    public static final ModConfigSpec.BooleanValue DEBRIS;
    public static final ModConfigSpec.BooleanValue CLOUD_SHADOWS;
    public static final ModConfigSpec.BooleanValue DISABLE_VANILLA_CLOUDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Главный выключатель клиентского рендера").define("enabled", true);
        QUALITY = b.comment("Профиль качества: LOW / MEDIUM / HIGH / ULTRA").defineEnum("quality", Quality.HIGH);
        CUSTOM_SKY = b.comment("Процедурное небо (замена ванильного неба в Оверворлде)").define("customSky", true);
        AMBIENT_CLOUDS = b.comment("Фоновые облака (кучевые + перистые) в небе").define("ambientClouds", true);
        STORM_CLOUDS = b.comment("Объёмные облака штормов").define("stormClouds", true);
        CLOUD_DENSITY = b.comment("Плотность штормовых облаков").defineInRange("cloudDensity", 1.0, 0.3, 2.5);
        CONTRAST = b.comment("Контраст света/тени в облаках").defineInRange("contrast", 1.0, 0.5, 1.8);
        VIEW_DISTANCE = b.comment("Дальность отрисовки штормов в блоках (ограничивается профилем качества)").defineInRange("viewDistance", 6000.0, 500.0, 8000.0);
        CLOUD_SHADOWS = b.comment("Тени облаков на земле").define("cloudShadows", true);
        DEBRIS = b.comment("Частицы обломков вокруг торнадо").define("debrisParticles", true);
        DISABLE_VANILLA_CLOUDS = b.comment("Выключать ванильные облака при входе в мир (они перекрывают наши)").define("disableVanillaClouds", true);
        SPEC = b.build();
    }

    private ClientConfig() {}
}
