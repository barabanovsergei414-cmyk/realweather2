package dev.realweather.storm;

import net.minecraft.network.FriendlyByteBuf;

/** Синхронизируемое состояние одной грозовой ячейки (общее для сервера и клиента). */
public final class StormState {
    public int id;
    public double x, z;
    public float dirX = 1f, dirZ = 0f;
    /** Блоков за тик. */
    public float speed;
    public float baseY = 150f, topY = 400f, radius = 120f;
    public float intensity, growth, tornado, shape, meso;
    /** Смещение торнадо от центра ячейки. */
    public float tOffX, tOffZ;
    public float groundY = 64f;
    public float rain;

    public double tornadoX() { return x + tOffX; }

    public double tornadoZ() { return z + tOffZ; }

    public StormState copy() {
        StormState s = new StormState();
        s.id = id; s.x = x; s.z = z; s.dirX = dirX; s.dirZ = dirZ; s.speed = speed;
        s.baseY = baseY; s.topY = topY; s.radius = radius; s.intensity = intensity; s.growth = growth;
        s.tornado = tornado; s.shape = shape; s.meso = meso; s.tOffX = tOffX; s.tOffZ = tOffZ; s.groundY = groundY; s.rain = rain;
        return s;
    }

    /** Копия, экстраполированная на dt тиков вперёд по скорости. */
    public StormState at(double dt) {
        StormState s = copy();
        s.x += dirX * speed * dt;
        s.z += dirZ * speed * dt;
        return s;
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeDouble(x);
        b.writeDouble(z);
        b.writeFloat(dirX); b.writeFloat(dirZ); b.writeFloat(speed);
        b.writeFloat(baseY); b.writeFloat(topY); b.writeFloat(radius);
        b.writeFloat(intensity); b.writeFloat(growth); b.writeFloat(tornado); b.writeFloat(shape);
        b.writeFloat(tOffX); b.writeFloat(tOffZ); b.writeFloat(groundY); b.writeFloat(rain); b.writeFloat(meso);
    }

    public static StormState read(FriendlyByteBuf b) {
        StormState s = new StormState();
        s.id = b.readVarInt();
        s.x = b.readDouble();
        s.z = b.readDouble();
        s.dirX = b.readFloat(); s.dirZ = b.readFloat(); s.speed = b.readFloat();
        s.baseY = b.readFloat(); s.topY = b.readFloat(); s.radius = b.readFloat();
        s.intensity = b.readFloat(); s.growth = b.readFloat(); s.tornado = b.readFloat(); s.shape = b.readFloat();
        s.tOffX = b.readFloat(); s.tOffZ = b.readFloat(); s.groundY = b.readFloat(); s.rain = b.readFloat(); s.meso = b.readFloat();
        return s;
    }
}
