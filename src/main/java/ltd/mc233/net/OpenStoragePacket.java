package ltd.mc233.net;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import ltd.mc233.GuiHandler;
import ltd.mc233.PortableStorageMod;
import ltd.mc233.StorageService;

/**
 * 一个"信号包": 客户端发给服务端, 意思是"我要开随身仓库"。
 *
 * 玩家在客户端按了 B, 但仓库数据在服务端、客户端够不着,
 * 所以只能发个信儿过去, 让服务端来开界面。
 * 这个包里不带任何数据 —— 谁发的, 服务端自己就知道。
 */
public class OpenStoragePacket implements IMessage {

    // 收包时框架会先 new 一个空包再往里填, 所以必须留一个空构造。
    public OpenStoragePacket() {}

    // 这个包没数据, 所以读和写都空着。(带数据的包才在这两个方法里写字段。)
    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    // 服务端收到"要开仓库"后, 就在这里干活。
    public static class Handler implements IMessageHandler<OpenStoragePacket, IMessage> {

        @Override
        public IMessage onMessage(OpenStoragePacket message, MessageContext ctx) {
            // 拿到是哪个玩家发来的。
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (StorageService.isLocked(p)) { // 被 /storage lock 锁住了 → 不给开, 回一句提示
                p.addChatMessage(new net.minecraft.util.ChatComponentText("§c随身仓库当前无法打开"));
                return null;
            }
            // 这行就是"开界面": 服务端建好逻辑、客户端弹出画面, 一次搞定(相当于 Bukkit 的 openInventory)。
            p.openGui(
                PortableStorageMod.instance,
                GuiHandler.GUI_STORAGE,
                p.worldObj,
                (int) p.posX,
                (int) p.posY,
                (int) p.posZ);
            StorageService.syncLockedSlots(p); // 顺手同步锁定格: 补满 + 把背包里多余的同种物品存进仓库
            StorageService.sendPage(p, "", 0, -1, -2); // 再把仓库第一页内容发给客户端(刚开的空壳界面本身没数据)
            return null;
        }
    }
}
