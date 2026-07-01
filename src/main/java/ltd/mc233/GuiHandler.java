package ltd.mc233;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;
import ltd.mc233.client.GuiPortableStorage;
import ltd.mc233.inventory.ContainerPortableStorage;

public class GuiHandler implements IGuiHandler {

    public static final int GUI_STORAGE = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return new ContainerPortableStorage(player);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return new GuiPortableStorage(new ContainerPortableStorage(player));
    }
}
