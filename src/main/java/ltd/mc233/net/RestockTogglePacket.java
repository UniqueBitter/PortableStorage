package ltd.mc233.net;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

// 切换"自动补货"开关(每玩家持久化)。
public class RestockTogglePacket implements IMessage {

    public RestockTogglePacket() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<RestockTogglePacket, IMessage> {

        @Override
        public IMessage onMessage(RestockTogglePacket message, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            ltd.mc233.StorageService.toggleRestock(p);
            return null;
        }
    }
}
