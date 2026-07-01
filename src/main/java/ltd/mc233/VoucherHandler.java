package ltd.mc233;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ltd.mc233.item.ItemCapVoucher;

// 栏位拓展器进入背包 → 自动使用并消耗(Config.autoConsumeVoucher, 默认开)。每 5 tick 扫一次背包, 覆盖拾取/给予/磁铁等所有入包途径。
public class VoucherHandler {

    private static boolean loggedError = false;

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        try {
            doTick(e);
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                PortableStorageMod.LOG.error("扩容券自动使用异常(已忽略, 不再重复报告)", t);
            }
        }
    }

    private void doTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!Config.autoConsumeVoucher) return;
        EntityPlayer player = e.player;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayerMP)) return;
        if (player.ticksExisted % 5 != 0) return; // 降频

        EntityPlayerMP p = (EntityPlayerMP) player;
        ItemStack[] inv = p.inventory.mainInventory;
        boolean changed = false;
        for (int i = 0; i < inv.length; i++) {
            ItemStack st = inv[i];
            if (st == null || !(st.getItem() instanceof ItemCapVoucher)) continue;
            int applied = 0, totalDelta = 0;
            for (int k = 0; k < st.stackSize; k++) {
                int d = StorageService.useVoucher(p);
                if (d <= 0) break; // 到上限, 剩余的不消耗、留在背包
                applied++;
                totalDelta += d;
            }
            if (applied <= 0) continue;
            st.stackSize -= applied;
            if (st.stackSize <= 0) inv[i] = null;
            changed = true;
            p.addChatMessage(
                new ChatComponentText(
                    "§a栏位拓展器自动使用 §e" + applied
                        + " §a个 → 容量 +"
                        + totalDelta
                        + ", 当前 "
                        + StorageService.getCapacity(p)
                        + " 种"));
        }
        if (changed) p.inventoryContainer.detectAndSendChanges();
    }
}
