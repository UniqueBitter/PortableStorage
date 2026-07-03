package ltd.mc233.net;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import ltd.mc233.GuiHandler;
import ltd.mc233.PortableStorageMod;
import ltd.mc233.StorageService;

public class OpenStoragePacket implements IMessage {

    public OpenStoragePacket() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<OpenStoragePacket, IMessage> {

        @Override
        public IMessage onMessage(OpenStoragePacket message, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (StorageService.isLocked(p)) { // 被 /storage lock 锁定 → 拒绝打开
                p.addChatMessage(new net.minecraft.util.ChatComponentText("§c随身仓库当前无法打开"));
                return null;
            }
            p.openGui(
                PortableStorageMod.instance,
                GuiHandler.GUI_STORAGE,
                p.worldObj,
                (int) p.posX,
                (int) p.posY,
                (int) p.posZ);
            StorageService.syncLockedSlots(p); // 打开时同步锁定格: 补满 + 多余存入
            StorageService.sendPage(p, "", 0, -1, -2); // 打开时回到上次记忆的标签(-2=用持久化的当前标签)
            return null;
        }
    }
}
