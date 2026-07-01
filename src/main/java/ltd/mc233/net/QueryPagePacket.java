package ltd.mc233.net;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class QueryPagePacket implements IMessage {

    public String keyword;
    public int offset;
    public int limit;
    public int sort;
    public int tabId; // -1=全部, 0=未分类, >=1=自定义

    public QueryPagePacket() {}

    public QueryPagePacket(String keyword, int offset, int limit, int sort, int tabId) {
        this.keyword = keyword;
        this.offset = offset;
        this.limit = limit;
        this.sort = sort;
        this.tabId = tabId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, keyword == null ? "" : keyword);
        buf.writeInt(offset);
        buf.writeInt(limit);
        buf.writeInt(sort);
        buf.writeInt(tabId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        keyword = ByteBufUtils.readUTF8String(buf);
        offset = buf.readInt();
        limit = buf.readInt();
        sort = buf.readInt();
        tabId = buf.readInt();
    }

    public static class Handler implements IMessageHandler<QueryPagePacket, IMessage> {

        @Override
        public IMessage onMessage(QueryPagePacket message, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            ltd.mc233.StorageService.sendPage(p, message.keyword, message.offset, message.sort, message.tabId);
            return null;
        }
    }
}
