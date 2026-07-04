package ltd.mc233;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;
import ltd.mc233.client.GuiPortableStorage;
import ltd.mc233.inventory.ContainerPortableStorage;

/**
 * 开一个界面时, 客户端和服务端各要一个对象, 这个类就负责把它俩分别造出来。
 * 服务端那个管逻辑(存取物品), 客户端那个管画面。
 * 玩家一调 openGui, Forge 就来这里要这两个对象。
 *
 * (在 Bukkit 里 openInventory 一行就够了; 这里界面是自己画的, 才要分两端造。)
 */
public class GuiHandler implements IGuiHandler {

    // 界面编号。这个 mod 就一个界面, 固定用 0。
    public static final int GUI_STORAGE = 0;

    // 服务端要的对象: 仓库容器, 只管背包那 36 格的逻辑。
    // (仓库那一片物品格子不在这里 —— 它是客户端自己画的, 在 GuiPortableStorage 里。)
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return new ContainerPortableStorage(player);
    }

    // 客户端要的对象: 界面本体, 负责把东西画出来。
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return new GuiPortableStorage(new ContainerPortableStorage(player));
    }
}
