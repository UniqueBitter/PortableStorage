package ltd.mc233.magnet;

import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ltd.mc233.Config;
import ltd.mc233.StorageProvider;
import ltd.mc233.StorageService;
import ltd.mc233.core.MagnetRouter;
import ltd.mc233.db.StorageDao;
import ltd.mc233.item.ItemStackCodec;

public class MagnetManager {

    private static boolean loggedError = false;

    // 兜底: 任何异常都不让它崩游戏(尤其与其它模组的 ASM 变换器交互时), 只首次记一次日志。
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        try {
            doTick(e);
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                ltd.mc233.PortableStorageMod.LOG.error("磁铁 tick 异常(已忽略, 不再重复报告)", t);
            }
        }
    }

    private void doTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayer player = e.player;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        int mode = PlayerMagnetState.get(player);
        if (mode == 0) return;

        // 降频: 每 3 tick 扫一次实体即可(拾取延迟 ≤150ms 无感), 省去每 tick 的 AABB 实体查询。
        if (player.ticksExisted % 3 != 0) return;

        double r = Config.magnetRadius;
        AxisAlignedBB box = player.boundingBox.expand(r, r, r);
        @SuppressWarnings("unchecked")
        List<EntityItem> list = player.worldObj.getEntitiesWithinAABB(EntityItem.class, box);
        if (list.isEmpty()) return;
        String uuid = ltd.mc233.StorageProvider.keyFor(player);
        StorageDao dao = StorageProvider.dao(); // 提出循环(现已缓存复用)

        for (EntityItem ei : list) {
            if (ei == null || ei.isDead) continue;
            // 跳过永久不可拾取物(delayBeforeCanPickup=32767, 常用作展示/任务物); 普通刚掉落物只是临时延迟, 仍即时吸取。
            if (ei.delayBeforeCanPickup == 32767) continue;
            // 同样跳过被标记为不会自然消失的"持久化"展示物(部分作者用 PersistenceRequired/Invulnerable)。
            if (ei.isEntityInvulnerable()) continue;
            ItemStack st = ei.getEntityItem();
            if (st == null || st.stackSize <= 0) continue;

            int free = freeSpaceFor(player, st);
            MagnetRouter.Split sp = MagnetRouter.route(mode, st.stackSize, free);
            int taken = 0; // 实际收走的数量(进背包或进仓库); 容量满放不下的留在地上

            if (sp.toInv > 0) {
                ItemStack inv = st.copy();
                inv.stackSize = sp.toInv;
                player.inventory.addItemStackToInventory(inv);
                taken += sp.toInv - inv.stackSize; // 实际进背包的部分
                if (inv.stackSize > 0) { // 背包没全放下, 余量转入仓库(受容量限制)
                    ItemStack rem = st.copy();
                    rem.stackSize = inv.stackSize;
                    if (StorageService.deposit(player, dao, uuid, ItemStackCodec.encode(rem, player)))
                        taken += inv.stackSize;
                }
            }
            if (sp.toStorage > 0) {
                ItemStack stg = st.copy();
                stg.stackSize = sp.toStorage;
                if (StorageService.deposit(player, dao, uuid, ItemStackCodec.encode(stg, player)))
                    taken += sp.toStorage;
            }

            if (taken <= 0) continue; // 啥也没收进去(背包满 + 仓库满新种类) → 物品留在地上
            if (taken >= st.stackSize) {
                ei.setDead();
            } else {
                st.stackSize -= taken; // 只收走一部分, 剩下的留在地上
                ei.setEntityItemStack(st);
            }
            player.worldObj.playSoundAtEntity(player, "random.pop", 0.1F, 1.0F);
        }
    }

    private int freeSpaceFor(EntityPlayer player, ItemStack st) {
        int free = 0;
        int max = st.getMaxStackSize();
        ItemStack[] main = player.inventory.mainInventory;
        for (int i = 0; i < main.length; i++) {
            ItemStack slot = main[i];
            if (slot == null) {
                free += max;
            } else if (slot.isItemEqual(st) && ItemStack.areItemStackTagsEqual(slot, st)
                && slot.stackSize < slot.getMaxStackSize()) {
                    free += slot.getMaxStackSize() - slot.stackSize;
                }
        }
        return free;
    }
}
