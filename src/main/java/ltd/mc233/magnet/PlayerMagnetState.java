package ltd.mc233.magnet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public final class PlayerMagnetState {

    private static final String PERSIST = "PlayerPersisted";
    private static final String KEY = "psMagnetMode";

    private PlayerMagnetState() {}

    // get 在服务端主线程(磁铁tick)调用, set 在 netty handler 线程(切档)调用,
    // 两者都读写玩家 PlayerPersisted NBT(底层 HashMap), 用同一把锁串行化, 避免并发结构性修改。
    public static int get(EntityPlayer p) {
        synchronized (PlayerMagnetState.class) {
            return tag(p).getInteger(KEY);
        }
    }

    public static void set(EntityPlayer p, int mode) {
        synchronized (PlayerMagnetState.class) {
            tag(p).setInteger(KEY, ((mode % 3) + 3) % 3);
        }
    }

    private static NBTTagCompound tag(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persist;
        if (data.hasKey(PERSIST)) {
            persist = data.getCompoundTag(PERSIST);
        } else {
            persist = new NBTTagCompound();
            data.setTag(PERSIST, persist);
        }
        return persist;
    }
}
