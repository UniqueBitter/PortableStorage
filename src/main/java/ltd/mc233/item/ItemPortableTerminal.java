package ltd.mc233.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import ltd.mc233.PortableStorageMod;

public class ItemPortableTerminal extends Item {

    public ItemPortableTerminal() {
        setUnlocalizedName("portablestorage_terminal");
        setTextureName(PortableStorageMod.MODID + ":terminal");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            ltd.mc233.net.NetworkHandler.openStorage();
        }
        return stack;
    }
}
