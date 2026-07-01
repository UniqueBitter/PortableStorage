package ltd.mc233.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import ltd.mc233.Config;
import ltd.mc233.PortableStorageMod;
import ltd.mc233.StorageService;

// 栏位拓展器: 右键扩容随身仓库(容量为 0 时 +24 起步, 之后每次 +6), 封顶 Config.maxCapacity, 消耗一个。
public class ItemCapVoucher extends Item {

    public ItemCapVoucher() {
        setUnlocalizedName("portablestorage_cap_voucher");
        setTextureName(PortableStorageMod.MODID + ":cap_voucher");
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // 自动模式(默认)下靠"进背包自动消耗", 右键不生效; 仅在关闭自动时右键手动使用。
        if (Config.autoConsumeVoucher) return stack;
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) player;
            int delta = StorageService.useVoucher(p);
            if (delta > 0) {
                stack.stackSize--; // 仅在真正生效时消耗
                p.addChatMessage(
                    new ChatComponentText("§a随身仓库容量 +" + delta + " → 当前 " + StorageService.getCapacity(p) + " 种"));
            } else {
                p.addChatMessage(new ChatComponentText("§e随身仓库容量已达上限 (" + Config.maxCapacity + " 种)"));
            }
        }
        return stack;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add("§7右键使用: 首次 +24 栏位, 之后每次 +6");
        list.add("§7上限: " + Config.maxCapacity + " 个栏位 (指令可突破)");
    }
}
