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

public class ContainerPortableStorage extends Container {

    public final EntityPlayer player;

    public ContainerPortableStorage(EntityPlayer player) {
        this.player = player;
        InventoryPlayer inv = player.inventory;
        int top = 116;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, top + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inv, col, 8 + col * 18, top + 58));
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return true;
    }

    // Shift+左键背包里的物品 → 存入仓库(取出靠仓库网格点击; 此处只处理"存入背包物品")。
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack st = slot.getStack();
        if (st.getItem() instanceof ItemPortableTerminal) return null;
        if (!p.worldObj.isRemote && p instanceof EntityPlayerMP) {
            EntityPlayerMP pmp = (EntityPlayerMP) p;
            String uuid = ltd.mc233.StorageProvider.keyFor(pmp);
            if (!StorageService.depositToCurrentTab(pmp, StorageProvider.dao(), uuid, ItemStackCodec.encode(st, pmp))) {
                StorageService.warnFull(pmp); // 满了 → 物品留在背包格, 不丢
                return null;
            }
            slot.putStack(null);
            slot.onSlotChanged();
            // 客户端无法预测这次自定义存入(它那边 transferStackInSlot 不动), 会残留"虚拟实体"。
            // 主动把该格清空 + 同步整个容器, 强制客户端清掉幽灵物品。
            pmp.playerNetServerHandler
                .sendPacket(new net.minecraft.network.play.server.S2FPacketSetSlot(this.windowId, index, null));
            this.detectAndSendChanges();
            StorageService.refresh(pmp);
        }
        return null;
    }
}
