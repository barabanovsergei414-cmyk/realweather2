package dev.realweather.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.realweather.config.RWConfig;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.realweather.storm.Storm;
import dev.realweather.storm.StormManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/** /realweather storm|tornado|intensity|clear|info (нужен уровень доступа 2). */
public final class RWCommands {
    private RWCommands() {}

    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("realweather").requires(s -> s.hasPermission(2))
                .then(Commands.literal("storm")
                        .executes(c -> spawn(c, 0.9f, false))
                        .then(Commands.argument("intensity", FloatArgumentType.floatArg(0.1f, 1f))
                                .executes(c -> spawn(c, FloatArgumentType.getFloat(c, "intensity"), false))))
                .then(Commands.literal("tornado")
                        .executes(c -> spawn(c, 1f, true))
                        .then(Commands.argument("intensity", FloatArgumentType.floatArg(0.1f, 1f))
                                .executes(c -> spawn(c, FloatArgumentType.getFloat(c, "intensity"), true))))
                .then(Commands.literal("supercell")
                        .executes(c -> spawnCm1(c, RWConfig.CAPE.get().floatValue(), RWConfig.SHEAR.get().floatValue()))
                        .then(Commands.argument("cape", IntegerArgumentType.integer(300, 6000))
                                .then(Commands.argument("shear", IntegerArgumentType.integer(0, 40))
                                        .executes(c -> spawnCm1(c, IntegerArgumentType.getInteger(c, "cape"),
                                                IntegerArgumentType.getInteger(c, "shear"))))))
                .then(Commands.literal("intensity")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 1f))
                                .executes(c -> setIntensity(c, FloatArgumentType.getFloat(c, "value")))))
                .then(Commands.literal("clear").executes(RWCommands::clear))
                .then(Commands.literal("info").executes(RWCommands::info)));
    }

    private static ServerLevel overworld(CommandContext<CommandSourceStack> c) {
        ServerLevel l = c.getSource().getLevel();
        return l.dimension() == Level.OVERWORLD ? l : null;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, float intensity, boolean tornado) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = overworld(c);
        if (level == null) {
            c.getSource().sendFailure(Component.literal("RealWeather работает только в Оверворлде."));
            return 0;
        }
        Vec3 look = p.getLookAngle();
        double lx = look.x, lz = look.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 1e-4) { lx = 1; lz = 0; len = 1; }
        lx /= len; lz /= len;
        double dist = tornado ? 320 : 420;
        double x = p.getX() + lx * dist, z = p.getZ() + lz * dist;
        float heading = (float) Math.atan2(-lz, -lx);   // движется в сторону игрока
        Storm s = StormManager.spawn(level, x, z, heading, intensity, tornado, tornado ? 0.44 : 0.14,
                RWConfig.CAPE.get().floatValue(), RWConfig.SHEAR.get().floatValue());
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Достигнут лимит штормов (maxStorms)."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(
                (tornado ? "Торнадическая суперячейка" : "Шторм") + " создан(а) в " + (int) dist
                        + " блоках впереди, интенсивность " + String.format("%.2f", intensity)), true);
        return 1;
    }

    /** Сценарий в духе CM1: тёплый пузырь у земли, дальше шторм развивается сам (торнадо через несколько минут). */
    private static int spawnCm1(CommandContext<CommandSourceStack> c, float cape, float shear) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = overworld(c);
        if (level == null) return 0;
        Vec3 look = p.getLookAngle();
        double lx = look.x, lz = look.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 1e-4) { lx = 1; lz = 0; len = 1; }
        lx /= len; lz /= len;
        double x = p.getX() + lx * 650, z = p.getZ() + lz * 650;
        float heading = (float) Math.atan2(-lz, -lx);
        Storm s = StormManager.spawn(level, x, z, heading, 1f, shear >= 10f, 0.02, cape, shear);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Достигнут лимит штормов (maxStorms)."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Суперячейка (CM1-подобный сценарий): CAPE " + (int) cape
                + " Дж/кг, сдвиг " + (int) shear + " м/с. Торнадо — "
                + (s.tornadic ? "ожидается через несколько минут" : "не ожидается (мал сдвиг)")), true);
        return 1;
    }

    private static int setIntensity(CommandContext<CommandSourceStack> c, float v) {
        ServerLevel level = overworld(c);
        if (level == null) return 0;
        for (Storm s : StormManager.get(level)) s.goal = Mth.clamp(v, 0.1f, 1f);
        c.getSource().sendSuccess(() -> Component.literal("Целевая интенсивность: " + v), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c) {
        ServerLevel level = overworld(c);
        if (level == null) return 0;
        StormManager.clear(level);
        c.getSource().sendSuccess(() -> Component.literal("Все штормы удалены."), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        ServerLevel level = overworld(c);
        if (level == null) return 0;
        List<Storm> list = StormManager.get(level);
        if (list.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Штормов нет."), false);
            return 0;
        }
        for (Storm s : list) {
            String m = String.format("#%d (%.0f, %.0f) возраст %d/%d с, интенсивность %.2f, торнадо %.2f, рост %.2f, скорость %.1f бл/с",
                    s.st.id, s.st.x, s.st.z, s.age / 20, s.life / 20, s.st.intensity, s.st.tornado, s.st.growth, s.st.speed * 20);
            c.getSource().sendSuccess(() -> Component.literal(m), false);
        }
        return list.size();
    }
}
