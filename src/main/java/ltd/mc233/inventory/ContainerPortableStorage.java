package ltd.mc233.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import ltd.mc233.StorageProvider;
import ltd.mc233.StorageService;
import ltd.mc233.item.ItemPortableTerminal;
import ltd.mc233.item.ItemStackCodec;

/**
 * 界面打开时, 服务端这半边的"逻辑对象"。它只管界面里玩家背包那 36 格
 * (上面 27 格主背包 + 下面 9 格快捷栏)的点击和移动。
 * 仓库那一大片物品格不归它管 —— 那是客户端自己画的(见 GuiPortableStorage)。
 *
 * (Bukkit 里你几乎不碰这层, 菜单点击走 InventoryClickEvent 就行;
 * Forge 自定义界面得自己继承 Container、摆好槽位、接管点击。)
 */
public class ContainerPortableStorage extends Container {

    public final EntityPlayer player;

    // 把玩家背包的 36 个格子摆进界面(坐标对齐原版大箱子的背包位置)。
    public ContainerPortableStorage(EntityPlayer player) {
        this.player = player;
        InventoryPlayer inv = player.inventory;
        int top = 139; // 背包在界面里的起始高度(和原版大箱子对齐: 主背包 y=139, 快捷栏 y=197)
        // 上面 3 行 × 9 列 = 主背包 27 格
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, top + row * 18));
        // 最下面一行 9 格 = 快捷栏
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inv, col, 8 + col * 18, top + 58));
    }

    // 一直返回 true: 随身仓库不像箱子有距离限制, 走多远都能开着。
    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return true;
    }

    // 关界面时同步一下锁定格(补满 + 把背包里多余的同种物品存进仓库)。只在服务端做。
    @Override
    public void onContainerClosed(EntityPlayer p) {
        super.onContainerClosed(p);
        if (!p.worldObj.isRemote && p instanceof EntityPlayerMP) StorageService.syncLockedSlots((EntityPlayerMP) p);
    }

    // 玩家在背包里 Shift+左键某个物品时会调到这里 —— 我们把它存进仓库。
    // (从仓库往外取不走这里, 那是点仓库网格触发的自定义包。)
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack st = slot.getStack();
        if (st.getItem() instanceof ItemPortableTerminal) return null; // 终端道具本身不收
        if (!p.worldObj.isRemote && p instanceof EntityPlayerMP) { // 只在服务端处理
            EntityPlayerMP pmp = (EntityPlayerMP) p;
            String uuid = ltd.mc233.StorageProvider.keyFor(pmp);
            if (!StorageService.depositToCurrentTab(pmp, StorageProvider.dao(), uuid, ItemStackCodec.encode(st, pmp))) {
                StorageService.warnFull(pmp); // 仓库满了 → 物品留在背包不动
                return null;
            }
            slot.putStack(null); // 存成功了, 清空这个背包格
            slot.onSlotChanged();
            // 这次存入是我们自定义的, 客户端并不知道, 屏幕上会残留一个"假物品"(幽灵)。
            // 所以主动发个包告诉客户端"这格已经空了", 再整体同步一遍, 把幽灵清掉。
            pmp.playerNetServerHandler
                .sendPacket(new net.minecraft.network.play.server.S2FPacketSetSlot(this.windowId, index, null));
            this.detectAndSendChanges();
            StorageService.refresh(pmp); // 刷新仓库界面(数量变了)
        }
        return null;
    }
}
