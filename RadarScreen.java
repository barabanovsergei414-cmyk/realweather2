package dev.realweather.client.radar;

import dev.realweather.client.ClientStorms;
import dev.realweather.storm.StormState;
import dev.realweather.tornado.TornadoParams;
import dev.realweather.tornado.WindField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Погодный радар: круглый экран с вращающимся лучом, зернистостью эха, кольцами дальности, азимутами,
 * подсказкой под курсором, списком ячеек и предупреждением о торнадо.
 * Данные берутся из состояния погодного движка; радиальная скорость считается из поля ветра (WindField).
 * Радар стоит в позиции игрока, север сверху.
 */
public class RadarScreen extends Screen {
    private static final int GRID = 96;
    private static final int[] RANGES = {500, 1000, 2000, 4000, 8000};
    private static final int[] DBZ_COLORS = {
            0xFF04E9E7, 0xFF019FF4, 0xFF0300F4, 0xFF02FD02, 0xFF01C501, 0xFF008E00, 0xFFFDF802,
            0xFFE5BC00, 0xFFFD9500, 0xFFFD0000, 0xFFD40000, 0xFFBC0000, 0xFFF800FD, 0xFF9854C6};
    private static final double TWO_PI = Math.PI * 2.0;

    private int mode = 0;          // 0 — отражаемость, 1 — радиальная скорость
    private int rangeIdx = 2;
    private int frameOffset = 0;   // 0 — онлайн, N — N кадров назад (по 5 с)
    private int cs, mapX, mapY, mapPx;

    public RadarScreen() {
        super(Component.translatable("screen.realweather.radar"));
    }

    @Override
    protected void init() {
        cs = Math.max(3, (Math.min(width, height) - 80) / GRID);
        mapPx = cs * GRID;
        mapX = Math.max(20, (width - mapPx) / 2 - 90);
        mapY = (height - mapPx) / 2 + 8;
        int bx = mapX + mapPx + 18, by = mapY;
        addRenderableWidget(Button.builder(Component.translatable("radar.mode.refl"), b -> {
            mode ^= 1;
            b.setMessage(Component.translatable(mode == 0 ? "radar.mode.refl" : "radar.mode.vel"));
        }).bounds(bx, by, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("radar.zoom_in"),
                b -> rangeIdx = Math.max(0, rangeIdx - 1)).bounds(bx, by + 24, 73, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("radar.zoom_out"),
                b -> rangeIdx = Math.min(RANGES.length - 1, rangeIdx + 1)).bounds(bx + 77, by + 24, 73, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("radar.older"),
                b -> frameOffset = Math.min(ClientStorms.history().size(), frameOffset + 1)).bounds(bx, by + 48, 73, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("radar.newer"),
                b -> frameOffset = Math.max(0, frameOffset - 1)).bounds(bx + 77, by + 48, 73, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("radar.live"),
                b -> frameOffset = 0).bounds(bx, by + 72, 150, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        rangeIdx = Mth.clamp(rangeIdx + (sy > 0 ? -1 : 1), 0, RANGES.length - 1);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private List<StormState> source(double now) {
        if (frameOffset == 0) return ClientStorms.live(now).storms;
        List<ClientStorms.Snapshot> h = ClientStorms.history();
        if (h.isEmpty()) return List.of();
        return h.get(Mth.clamp(h.size() - frameOffset, 0, h.size() - 1)).storms;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        double now = mc.level.getGameTime() + pt;
        g.fill(0, 0, width, height, 0xEE03070A);

        List<StormState> storms = source(now);
        double range = RANGES[rangeIdx];
        double px = mc.player.getX(), pz = mc.player.getZ();
        TornadoParams prm = ClientStorms.params;
        double[] w = new double[3];
        double half = GRID / 2.0;
        double sweep = ((now % 80.0) / 80.0) * TWO_PI;     // оборот луча за 4 секунды

        // подложка круга
        for (int gy = 0; gy < GRID; gy++) {
            for (int gx = 0; gx < GRID; gx++) {
                double nx = (gx + 0.5 - half) / half, ny = (gy + 0.5 - half) / half;
                if (nx * nx + ny * ny > 1.0) continue;
                g.fill(mapX + gx * cs, mapY + gy * cs, mapX + gx * cs + cs, mapY + gy * cs + cs, 0xFF07130D);
            }
        }

        // эхо
        for (int gy = 0; gy < GRID; gy++) {
            for (int gx = 0; gx < GRID; gx++) {
                double nx = (gx + 0.5 - half) / half, ny = (gy + 0.5 - half) / half;
                if (nx * nx + ny * ny > 1.0) continue;
                double wx = px + nx * range, wz = pz + ny * range;
                float dbz = sample(storms, wx, wz);
                if (dbz < 8f) continue;
                int col;
                if (mode == 0) {
                    col = DBZ_COLORS[Mth.clamp((int) ((dbz - 8f) / 4.5f), 0, DBZ_COLORS.length - 1)];
                } else {
                    col = velocityColor(radialVelocity(storms, wx, wz, px, pz, prm, w));
                }
                // яркость затухает позади вращающегося луча
                double bearing = Math.atan2(nx, -ny);
                if (bearing < 0) bearing += TWO_PI;
                double age = (((sweep - bearing) % TWO_PI) + TWO_PI) % TWO_PI / TWO_PI;
                col = shade(col, (float) (0.55 + 0.45 * (1.0 - age)));
                g.fill(mapX + gx * cs, mapY + gy * cs, mapX + gx * cs + cs, mapY + gy * cs + cs, col);
            }
        }

        int cx = mapX + mapPx / 2, cy = mapY + mapPx / 2;
        double rad = mapPx / 2.0;
        // кольца, азимутальные линии, подписи
        for (int k = 1; k <= 4; k++) {
            ring(g, cx, cy, rad * k / 4.0, 0x3040FF80);
            g.drawString(font, String.valueOf((int) (range * k / 4.0)), cx + 3, (int) (cy - rad * k / 4.0) + 1, 0x60A0FFC0, false);
        }
        ring(g, cx, cy, rad, 0xFF3A8F5A);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4.0;
            line(g, cx, cy, cx + (int) (Math.sin(ang) * rad), cy - (int) (Math.cos(ang) * rad), 0x2040FF80);
        }
        g.drawString(font, "N", cx - 3, mapY - 11, 0xFFFFFF, false);
        g.drawString(font, "S", cx - 3, mapY + mapPx + 3, 0xFFFFFF, false);
        g.drawString(font, "W", mapX - 10, cy - 4, 0xFFFFFF, false);
        g.drawString(font, "E", mapX + mapPx + 4, cy - 4, 0xFFFFFF, false);
        // луч
        line(g, cx, cy, cx + (int) (Math.sin(sweep) * rad), cy - (int) (Math.cos(sweep) * rad), 0xC060FF90);
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFFFFFFFF);

        // маркеры
        StormState nearest = null;
        double nd = Double.MAX_VALUE;
        for (StormState s : storms) {
            double dx = s.x - px, dz = s.z - pz;
            double d = Math.hypot(dx, dz);
            if (d < nd) { nd = d; nearest = s; }
            int sx = cx + (int) (dx / range * rad), sy = cy + (int) (dz / range * rad);
            if (inCircle(sx, sy, cx, cy, rad)) {
                g.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFFFFFFFF);
                g.drawString(font, isSupercell(s) ? "SC" : "TS", sx + 5, sy - 4, 0xFFFFFF, false);
                line(g, sx, sy, sx + (int) (s.dirX * 24), sy + (int) (s.dirZ * 24), 0xFFFFFFFF);
            }
            if (s.tornado > 0.12f) {
                int tx = cx + (int) ((s.tornadoX() - px) / range * rad);
                int ty = cy + (int) ((s.tornadoZ() - pz) / range * rad);
                if (inCircle(tx, ty, cx, cy, rad)) {
                    g.fill(tx - 4, ty - 4, tx + 5, ty + 5, 0xFFFF2020);
                    g.fill(tx - 2, ty - 2, tx + 3, ty + 3, 0xFFFFFF00);
                    g.drawString(font, "TVS", tx + 7, ty - 4, 0xFF6060, false);
                }
            }
        }
        // след
        List<ClientStorms.Snapshot> hist = ClientStorms.history();
        for (int i = 0; i < hist.size(); i++) {
            int a = 0x30 + 0x60 * i / Math.max(1, hist.size());
            for (StormState s : hist.get(i).storms) {
                int sx = cx + (int) ((s.x - px) / range * rad), sy = cy + (int) ((s.z - pz) / range * rad);
                if (inCircle(sx, sy, cx, cy, rad)) g.fill(sx, sy, sx + 2, sy + 2, (a << 24) | 0xFFFFFF);
            }
        }

        drawLegend(g);
        drawHeader(g, mc, storms.size(), range);
        drawStormList(g, storms, px, pz);
        drawWarning(g, storms, px, pz);
        drawHover(g, mx, my, storms, px, pz, range, prm, w, cx, cy, rad);
        super.render(g, mx, my, pt);
    }

    private static boolean isSupercell(StormState s) { return s.growth > 0.5f && s.intensity > 0.6f; }

    private static boolean inCircle(int x, int y, int cx, int cy, double r) {
        double dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    // ------------------------------------------------------------ панели

    private void drawHeader(GuiGraphics g, Minecraft mc, int count, double range) {
        long dt = mc.level.getDayTime();
        int hours = (int) (((dt / 1000) + 6) % 24);
        int mins = (int) ((dt % 1000) * 60 / 1000);
        String view = frameOffset == 0 ? "LIVE" : "T-" + (frameOffset * 5) + " s";
        g.drawString(font, String.format("День %d, %02d:%02d  |  %s  |  радиус %d бл.  |  ячеек: %d",
                dt / 24000 + 1, hours, mins, view, (int) range, count), mapX, mapY - 24, 0xFFFFFF, false);
    }

    private void drawLegend(GuiGraphics g) {
        int lx = mapX + mapPx + 18, ly = mapY + 104;
        g.drawString(font, mode == 0 ? "Отражаемость, dBZ" : "Радиальная скорость, м/с", lx, ly, 0xFFFFFF, false);
        if (mode == 0) {
            for (int i = 0; i < DBZ_COLORS.length; i++) {
                g.fill(lx, ly + 12 + i * 9, lx + 14, ly + 20 + i * 9, DBZ_COLORS[i]);
                g.drawString(font, String.valueOf(8 + (int) (i * 4.5f)), lx + 20, ly + 12 + i * 9, 0xCCCCCC, false);
            }
        } else {
            float[] marks = {-30, -20, -10, 0, 10, 20, 30};
            for (int i = 0; i < marks.length; i++) {
                g.fill(lx, ly + 12 + i * 11, lx + 14, ly + 21 + i * 11, velocityColor(marks[i]));
                g.drawString(font, (marks[i] > 0 ? "+" : "") + (int) marks[i], lx + 20, ly + 12 + i * 11, 0xCCCCCC, false);
            }
            g.drawString(font, "зелёный — к радару, красный — от", lx, ly + 12 + marks.length * 11 + 2, 0x999999, false);
        }
    }

    private void drawStormList(GuiGraphics g, List<StormState> storms, double px, double pz) {
        int lx = mapX + mapPx + 18, ly = mapY + mapPx - 60;
        g.drawString(font, "Ячейки:", lx, ly, 0xFFFFFF, false);
        int i = 0;
        for (StormState s : storms) {
            if (i >= 4) break;
            double dx = s.x - px, dz = s.z - pz;
            int brg = (int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)));
            if (brg < 0) brg += 360;
            String line = String.format("#%d %s %.0f м  %d°  I%.2f%s", s.id, isSupercell(s) ? "SC" : "TS",
                    Math.hypot(dx, dz), brg, s.intensity, s.tornado > 0.12f ? "  TVS" : (s.meso > 0.4f ? "  MESO" : ""));
            g.drawString(font, line, lx, ly + 11 + i * 11, s.tornado > 0.12f ? 0xFF6060 : 0xCCCCCC, false);
            i++;
        }
        if (i == 0) g.drawString(font, "нет (/realweather supercell)", lx, ly + 11, 0x777777, false);
    }

    private void drawWarning(GuiGraphics g, List<StormState> storms, double px, double pz) {
        for (StormState s : storms) {
            if (s.tornado < 0.15f) continue;
            double dx = s.tornadoX() - px, dz = s.tornadoZ() - pz;
            double d = Math.hypot(dx, dz);
            if (d > 2500) continue;
            int brg = (int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)));
            if (brg < 0) brg += 360;
            // скорость сближения (блоков/с)
            double closing = -((s.dirX * s.speed * 20.0) * (dx / Math.max(d, 1.0)) + (s.dirZ * s.speed * 20.0) * (dz / Math.max(d, 1.0)));
            closing = -closing;
            String eta = closing > 0.3 ? String.format("  ~%d с", (int) (d / closing)) : "";
            int bw = 330, bx = mapX + mapPx + 18 - 10, by = mapY - 46;
            if (((int) (System.currentTimeMillis() / 400)) % 2 == 0) g.fill(bx, by, bx + bw, by + 16, 0xFFB00000);
            else g.fill(bx, by, bx + bw, by + 16, 0xFF700000);
            g.drawString(font, String.format("ПРЕДУПРЕЖДЕНИЕ О ТОРНАДО: %.0f м, азимут %d°%s", d, brg, eta), bx + 4, by + 4, 0xFFFFFF, false);
            return;
        }
    }

    private void drawHover(GuiGraphics g, int mx, int my, List<StormState> storms, double px, double pz, double range,
                           TornadoParams prm, double[] w, int cx, int cy, double rad) {
        if (!inCircle(mx, my, cx, cy, rad)) return;
        double wx = px + (mx - cx) / rad * range, wz = pz + (my - cy) / rad * range;
        double d = Math.hypot(wx - px, wz - pz);
        int brg = (int) Math.round(Math.toDegrees(Math.atan2(wx - px, -(wz - pz))));
        if (brg < 0) brg += 360;
        float dbz = sample(storms, wx, wz);
        float v = dbz >= 8f ? radialVelocity(storms, wx, wz, px, pz, prm, w) : 0f;
        String t = String.format("%.0f м  %d°  %s  %s", d, brg, dbz >= 8f ? String.format("%.0f dBZ", dbz) : "нет эха",
                dbz >= 8f ? String.format("%+.0f м/с", v) : "");
        int tw = font.width(t);
        g.fill(mx + 8, my - 14, mx + 12 + tw, my - 2, 0xCC000000);
        g.drawString(font, t, mx + 10, my - 12, 0xFFFFFF, false);
    }

    // ------------------------------------------------------------ модель радара

    private static float sample(List<StormState> storms, double x, double z) {
        float dbz = reflectivity(storms, x, z);
        if (dbz < 6f) return dbz;
        // зернистость конвективных ядер (не меняет положения штормов)
        float n = vnoise(x / 34.0, z / 34.0) * 0.65f + vnoise(x / 13.0, z / 13.0) * 0.35f;
        return dbz + (n - 0.5f) * 14f * Mth.clamp(dbz / 25f, 0f, 1f);
    }

    private static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    private static float vnoise(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        float fx = (float) (x - ix), fz = (float) (z - iz);
        fx = fx * fx * (3f - 2f * fx);
        fz = fz * fz * (3f - 2f * fz);
        float a = Mth.lerp(fx, hash(ix, iz), hash(ix + 1, iz));
        float b = Mth.lerp(fx, hash(ix, iz + 1), hash(ix + 1, iz + 1));
        return Mth.lerp(fz, a, b);
    }

    /** Отражаемость (dBZ): дождевое ядро, hook-эхо у торнадо, сигнатура обломков, слабая наковальня. */
    static float reflectivity(List<StormState> storms, double x, double z) {
        float best = 0f;
        for (StormState s : storms) {
            double R = s.radius;
            double cx = s.x + s.dirX * R * 0.45, cz = s.z + s.dirZ * R * 0.45;
            double d = Math.hypot(x - cx, z - cz) / (R * 0.55);
            float dbz = (float) ((10.0 + 50.0 * s.intensity * Math.max(s.rain, 0.2f)) * Math.exp(-d * d * 1.2));
            if (s.tornado > 0.08f || s.meso > 0.2f) {
                double k = Math.max(s.tornado, s.meso * 0.6);
                double hx = s.tornadoX() - s.dirX * R * 0.05, hz = s.tornadoZ() - s.dirZ * R * 0.05;
                double dh = Math.hypot(x - hx, z - hz) / (R * 0.16);
                dbz = Math.max(dbz, (float) ((28.0 + 32.0 * k) * Math.exp(-dh * dh)));
            }
            if (s.tornado > 0.08f) {
                double dd = Math.hypot(x - s.tornadoX(), z - s.tornadoZ()) / (R * 0.03 + 6.0);
                dbz = Math.max(dbz, (float) (68.0 * s.tornado * Math.exp(-dd * dd)));
            }
            double ax = x - (s.x + s.dirX * R * 1.8), az = z - (s.z + s.dirZ * R * 1.8);
            double along = ax * s.dirX + az * s.dirZ, perp = -ax * s.dirZ + az * s.dirX;
            double da = (along / (R * 2.6)) * (along / (R * 2.6)) + (perp / (R * 1.3)) * (perp / (R * 1.3));
            dbz = Math.max(dbz, (float) ((6.0 + 14.0 * s.intensity * s.growth) * Math.exp(-da)));
            best = Math.max(best, dbz);
        }
        return best;
    }

    /** Радиальная скорость, м/с (+ от радара, − к радару): движение ячейки + мезоциклон + торнадо из поля ветра. */
    static float radialVelocity(List<StormState> storms, double x, double z, double rx, double rz,
                                TornadoParams prm, double[] w) {
        double rdx = x - rx, rdz = z - rz;
        double rl = Math.max(Math.hypot(rdx, rdz), 1.0);
        double ux = rdx / rl, uz = rdz / rl;
        double vx = 0, vz = 0;
        for (StormState s : storms) {
            double R = s.radius;
            double mask = Math.exp(-Math.pow(Math.hypot(x - (s.x + s.dirX * R * 0.5), z - (s.z + s.dirZ * R * 0.5)) / (R * 1.6), 2.0));
            vx += s.dirX * s.speed * mask;
            vz += s.dirZ * s.speed * mask;
            WindField.mesocyclone(x - s.x, z - s.z, R, Math.max(s.meso, 0.2f * s.growth) * s.intensity, w);
            vx += w[0]; vz += w[2];
            if (s.tornado > 0.05f) {
                WindField.tornado(x - s.tornadoX(), z - s.tornadoZ(), 5.0, s.tornado, prm.spin(), prm.inflow(), 0.0,
                        prm.influence(), 0.0, Math.max(s.baseY - s.groundY, 20.0), 0.0, w);
                vx += w[0]; vz += w[2];
            }
        }
        return (float) ((vx * ux + vz * uz) * 20.0);
    }

    static int velocityColor(float v) {
        float a = Mth.clamp(Math.abs(v) / 35f, 0f, 1f);
        if (Math.abs(v) < 2f) return 0xFF6A6A6A;
        int c = (int) (70 + 185 * a);
        return v < 0 ? (0xFF000000 | ((c / 5) << 16) | (c << 8) | (c / 5)) : (0xFF000000 | (c << 16) | ((c / 6) << 8) | (c / 6));
    }

    private static int shade(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f), gg = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
        return 0xFF000000 | (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(gg, 0, 255) << 8) | Mth.clamp(b, 0, 255);
    }

    private static void ring(GuiGraphics g, int cx, int cy, double r, int color) {
        int steps = (int) (r * 2.6);
        for (int i = 0; i < steps; i++) {
            double a = i * TWO_PI / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + (n == 0 ? 0 : (x1 - x0) * i / n), y = y0 + (n == 0 ? 0 : (y1 - y0) * i / n);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
