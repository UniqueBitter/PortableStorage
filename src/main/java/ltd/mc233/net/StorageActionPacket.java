package ltd.mc233.net;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class StorageActionPacket implements IMessage {

    public int action;
    public long id;
    public int amount;
    public String keyword;
    public int offset;

    public StorageActionPacket() {}

    public StorageActionPacket(int action, long id, int amount, String keyword, int offset) {
        this.action = action;
        this.id = id;
        this.amount = amount;
        this.keyword = keyword;
        this.offset = offset;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeLong(id);
        buf.writeInt(amount);
        ByteBufUtils.writeUTF8String(buf, keyword == null ? "" : keyword);
        buf.writeInt(offset);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readInt();
        id = buf.readLong();
        amount = buf.readInt();
        keyword = ByteBufUtils.readUTF8String(buf);
        offset = buf.readInt();
    }

    public static class Handler implements IMessageHandler<StorageActionPacket, IMessage> {

        @Override
        public IMessage onMessage(StorageActionPacket message, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            ltd.mc233.StorageService
                .handleAction(p, message.action, message.id, message.amount, message.keyword, message.offset);
            return null;
        }
    }
}
