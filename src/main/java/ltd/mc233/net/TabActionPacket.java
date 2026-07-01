package ltd.mc233.net;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

// 客户端→服务端: 标签操作。op: 0=新建(name) 1=删除(tabId) 2=改名(tabId,name) 3=把光标物品归到(tabId)。
public class TabActionPacket implements IMessage {

    public int op;
    public int tabId;
    public String name;

    public TabActionPacket() {}

    public TabActionPacket(int op, int tabId, String name) {
        this.op = op;
        this.tabId = tabId;
        this.name = name;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(op);
        buf.writeInt(tabId);
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        op = buf.readInt();
        tabId = buf.readInt();
        name = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<TabActionPacket, IMessage> {

        @Override
        public IMessage onMessage(TabActionPacket message, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            ltd.mc233.StorageService.handleTabAction(p, message.op, message.tabId, message.name);
            return null;
        }
    }
}
