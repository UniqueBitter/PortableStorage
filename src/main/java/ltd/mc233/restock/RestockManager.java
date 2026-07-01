package ltd.mc233.restock;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ltd.mc233.Config;
import ltd.mc233.StorageProvider;
import ltd.mc233.core.StoredItem;
import ltd.mc233.db.StorageDao;
import ltd.mc233.inventory.ContainerPortableStorage;
import ltd.mc233.item.ItemPortableTerminal;
import ltd.mc233.item.ItemStackCodec;

/**
 * 自动补货: 快捷栏(0-8)某个物品数量比上一 tick 减少时, 从随身仓库取同种物品补满该格(顶到上限)。
 * 仓库有多少补多少(库存不足就把剩余全部补进去)。打开仓库界面时不触发。可在 GUI 用按钮按玩家开关。
 */
public class RestockManager {

    // 每个玩家上一 tick 的快捷栏快照(副本), 用于检测"刚被用尽"的格子。
    private static final Map<String, ItemStack[]> LAST = new HashMap<String, ItemStack[]>();
    private static boolean loggedError = false;

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        try {
            doTick(e);
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                ltd.mc233.PortableStorageMod.LOG.error("自动补充 tick 异常(已忽略, 不再重复报告)", t);
            }
        }
    }

    private void doTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayer player = e.player;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayerMP)) return;
        if (!Config.autoRestock) return; // 全局总开关(配置)
        if (!ltd.mc233.StorageService.isRestockOn(player)) return; // 每玩家开关
        // 仓库界面打开时不补充(玩家正在存取/整理)
        if (player.openContainer instanceof ContainerPortableStorage) return;
        // 降频: 每 3 tick 跑一次即可(补货延迟 ≤150ms, 无感), 快照与比较都基于上次运行, 逻辑仍成立。
        if (player.ticksExisted % 3 != 0) return;

        String uuid = ltd.mc233.StorageProvider.keyFor(player);
        long locked = ltd.mc233.StorageService.getLockedSlots((EntityPlayerMP) player);
        ItemStack[] main = player.inventory.mainInventory;
        ItemStack[] prev = LAST.get(uuid);
        StorageDao dao = null;
        boolean changed = false;

        // 覆盖整个背包(0-35, 含快捷栏), 但只补"锁定的格"。锁定位掩码索引就是背包槽位索引。
        for (int i = 0; i < main.length && i < 64; i++) {
            if ((locked & (1L << i)) == 0) continue;
            ItemStack cur = main[i];
            ItemStack was = (prev != null && i < prev.length) ? prev[i] : null;
            if (was == null || was.stackSize <= 0) continue;
            if (was.getItem() instanceof ItemPortableTerminal) continue;

            // 判定该格"目标物品减少了": 要么变空, 要么同种物品但数量减少。换成别的物品则不补。
            int curCount;
            if (cur == null) {
                curCount = 0;
            } else if (sameItem(cur, was) && cur.stackSize < was.stackSize) {
                curCount = cur.stackSize;
            } else {
                continue; // 没减少, 或换了物品
            }

            ItemStack tmpl = cur != null ? cur : was; // 用来确定物品种类与上限
            int max = tmpl.getMaxStackSize();
            int need = max - curCount;
            if (need <= 0) continue;

            if (dao == null) dao = StorageProvider.dao();
            StoredItem key = ItemStackCodec.encode(was, player);
            long id = dao.entryId(uuid, key.getItem(), key.getMeta(), key.getNbtHash());
            if (id < 0) continue; // 仓库没库存 → 不补(该格自然消耗)
            StoredItem si = dao.findById(uuid, id);
            if (si == null) continue;
            long took = dao.extract(uuid, id, need); // 取 min(need, 库存): 库存不足就全取出
            if (took <= 0) continue;
            if (cur == null) {
                main[i] = ItemStackCodec.decode(si, (int) took);
            } else {
                cur.stackSize += (int) took;
            }
            changed = true;
        }

        // 记录本 tick 快照: 只复制"锁定格"(其余格子从不参与比较, 无需拷贝), 大幅减少每 tick 的 ItemStack 拷贝。
        ItemStack[] snap = new ItemStack[main.length];
        for (int i = 0; i < main.length && i < 64; i++) {
            if ((locked & (1L << i)) == 0) continue;
            snap[i] = main[i] == null ? null : main[i].copy();
        }
        LAST.put(uuid, snap);

        if (changed) ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }
}
