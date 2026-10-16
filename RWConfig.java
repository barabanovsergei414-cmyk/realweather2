package dev.realweather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Серверная конфигурация (синхронизируется с клиентом через пакет). Все значения ограничены диапазонами. */
public final class RWConfig {
    public static final ModConfigSpec SERVER_SPEC;

    public static final ModConfigSpec.IntValue MAX_STORMS;
    public static final ModConfigSpec.BooleanValue NATURAL_STORMS;
    public static final ModConfigSpec.DoubleValue NATURAL_CHANCE_PER_MIN;
    public static final ModConfigSpec.DoubleValue RADIUS_SCALE;
    public static final ModConfigSpec.DoubleValue CAPE;
    public static final ModConfigSpec.DoubleValue SHEAR;
    public static final ModConfigSpec.DoubleValue TOWER_SCALE;
    public static final ModConfigSpec.DoubleValue ANVIL_SCALE;
    public static final ModConfigSpec.DoubleValue SPEED_SCALE;
    public static final ModConfigSpec.DoubleValue LIFETIME_MINUTES;
    public static final ModConfigSpec.DoubleValue TORNADO_CHANCE;
    public static final ModConfigSpec.DoubleValue TORNADO_INTENSITY;
    public static final ModConfigSpec.DoubleValue INFLUENCE_RADIUS;
    public static final ModConfigSpec.DoubleValue SPIN;
    public static final ModConfigSpec.DoubleValue INFLOW;
    public static final ModConfigSpec.DoubleValue LIFT;
    public static final ModConfigSpec.DoubleValue TURBULENCE;
    public static final ModConfigSpec.DoubleValue PLAYER_MULT;
    public static final ModConfigSpec.DoubleValue ENTITY_MULT;
    public static final ModConfigSpec.DoubleValue CORE_DAMAGE;
    public static final ModConfigSpec.BooleanValue AFFECT_CREATIVE;
    public static final ModConfigSpec.BooleanValue RESET_FALL;
    public static final ModConfigSpec.DoubleValue RAIN_INTENSITY;
    public static final ModConfigSpec.BooleanValue HAIL;
    public static final ModConfigSpec.DoubleValue LIGHTNING_RATE;
    public static final ModConfigSpec.BooleanValue GROUND_STRIKES;
    public static final ModConfigSpec.BooleanValue LIGHTNING_DAMAGE;
    public static final ModConfigSpec.IntValue SYNC_INTERVAL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("storms");
        MAX_STORMS = b.comment("Максимум одновременных штормов в измерении").defineInRange("maxStorms", 3, 1, 4);
        NATURAL_STORMS = b.comment("Естественное появление штормов (по умолчанию выкл. — только команды)").define("naturalStorms", false);
        NATURAL_CHANCE_PER_MIN = b.comment("Шанс появления шторма в минуту (если naturalStorms=true)").defineInRange("naturalChancePerMinute", 0.05, 0.0, 1.0);
        CAPE = b.comment("CM1-подобная среда: CAPE, Дж/кг (энергия неустойчивости; больше = мощнее восходящий поток)").defineInRange("cape", 2500.0, 300.0, 6000.0);
        SHEAR = b.comment("CM1-подобная среда: сдвиг ветра 0-6 км, м/с (больше = мезоциклон и торнадо вероятнее)").defineInRange("shear", 20.0, 0.0, 40.0);
        RADIUS_SCALE = b.comment("Масштаб размера шторма").defineInRange("radiusScale", 1.0, 0.4, 3.0);
        TOWER_SCALE = b.comment("Масштаб высоты грозовой башни").defineInRange("towerScale", 1.0, 0.5, 1.6);
        ANVIL_SCALE = b.comment("Масштаб длины/ширины наковальни").defineInRange("anvilScale", 1.0, 0.3, 2.0);
        SPEED_SCALE = b.comment("Скорость движения шторма").defineInRange("speedScale", 1.0, 0.1, 4.0);
        LIFETIME_MINUTES = b.comment("Время жизни шторма (минуты реального времени)").defineInRange("lifetimeMinutes", 16.0, 2.0, 90.0);
        RAIN_INTENSITY = b.comment("Интенсивность осадков").defineInRange("rainIntensity", 1.0, 0.0, 1.0);
        HAIL = b.comment("Град в сильных штормах (клиентские частицы)").define("hail", true);
        b.pop();

        b.push("tornado");
        TORNADO_CHANCE = b.comment("Вероятность, что шторм станет торнадическим").defineInRange("tornadoChance", 0.6, 0.0, 1.0);
        TORNADO_INTENSITY = b.comment("Множитель интенсивности торнадо").defineInRange("tornadoIntensity", 1.0, 0.1, 1.5);
        INFLUENCE_RADIUS = b.comment("Радиус влияния на сущности (блоки, при средней силе)").defineInRange("influenceRadius", 70.0, 15.0, 250.0);
        SPIN = b.comment("Скорость вращения").defineInRange("spin", 1.0, 0.1, 3.0);
        INFLOW = b.comment("Сила притока к оси").defineInRange("inflow", 1.0, 0.0, 3.0);
        LIFT = b.comment("Сила восходящего потока").defineInRange("lift", 1.0, 0.0, 3.0);
        TURBULENCE = b.comment("Турбулентность").defineInRange("turbulence", 0.25, 0.0, 1.0);
        PLAYER_MULT = b.comment("Множитель воздействия на игроков (0 = выкл.)").defineInRange("playerMultiplier", 0.6, 0.0, 2.0);
        ENTITY_MULT = b.comment("Множитель воздействия на мобов и предметы (0 = выкл.)").defineInRange("entityMultiplier", 1.0, 0.0, 3.0);
        CORE_DAMAGE = b.comment("Урон у ядра вихря (хп каждые 0.5 с при полной силе, 0 = выкл.)").defineInRange("coreDamage", 1.0, 0.0, 10.0);
        AFFECT_CREATIVE = b.comment("Действует ли вихрь на игроков в творческом режиме").define("affectCreative", false);
        RESET_FALL = b.comment("Сбрасывать урон от падения внутри вихря").define("resetFallDamage", true);
        b.pop();

        b.push("lightning");
        LIGHTNING_RATE = b.comment("Вспышек в минуту при максимальной интенсивности").defineInRange("flashesPerMinute", 25.0, 0.0, 120.0);
        GROUND_STRIKES = b.comment("Разряды в землю (иначе только внутриоблачные вспышки)").define("groundStrikes", true);
        LIGHTNING_DAMAGE = b.comment("Разряды наносят урон и поджигают (иначе только визуальные)").define("lightningDamage", false);
        b.pop();

        b.push("network");
        SYNC_INTERVAL = b.comment("Период синхронизации с клиентами, тики").defineInRange("syncIntervalTicks", 10, 2, 40);
        b.pop();

        SERVER_SPEC = b.build();
    }

    private RWConfig() {}
}
