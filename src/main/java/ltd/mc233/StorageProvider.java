package ltd.mc233;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ltd.mc233.db.StorageDao;
import ltd.mc233.db.StorageDb;

public class StorageProvider {

    private static final Map<String, StorageDb> DBS = new HashMap<String, StorageDb>();
    private static final Map<String, StorageDao> DAOS = new HashMap<String, StorageDao>(); // 每个 db 复用一个无状态 DAO, 免得热路径反复 new

    // 仓库按玩家 UUID 区分(每个玩家自己的随身仓库)。
    public static String keyFor(net.minecraft.entity.player.EntityPlayer p) {
        return p.getUniqueID()
            .toString();
    }

    public static synchronized StorageDao dao() {
        File dir = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getWorldDirectory();
        String key = dir.getAbsolutePath();
        StorageDao dao = DAOS.get(key);
        if (dao == null) {
            StorageDb db = new StorageDb(new File(dir, "portablestorage/storage.db"));
            DBS.put(key, db);
            dao = new StorageDao(db);
            DAOS.put(key, dao);
        }
        return dao;
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (!e.world.isRemote && e.world.provider.dimensionId == 0) {
            synchronized (StorageProvider.class) {
                for (StorageDb db : DBS.values()) db.close();
                DBS.clear();
                DAOS.clear();
            }
        }
    }
}
