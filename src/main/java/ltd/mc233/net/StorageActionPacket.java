package ltd.mc233.net;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端的"动作包": 在仓库界面里点了一下, 想干点啥。
 *
 * action 是动作编号(取一组 / 取一个 / 存入 / 收纳...),
 * id 是操作的是仓库里哪一行,
 * keyword 和 offset 是当前的搜索词和翻页位置 —— 带上它俩, 服务端处理完好把"同一页"重新发回来。
 */
public class StorageActionPacket implements IMessage {

    public int action; // 干啥(编号, 对应 StorageService.handleAction 里的分支)
    public long id; // 操作仓库里哪一行(那行的数据库 id)
    public int amount; // 数量(这个 mod 里客户端固定传 0, 具体给多少由服务端算)
    public String keyword; // 当前搜索词
    public int offset; // 当前翻到第几行

    public StorageActionPacket() {} // 收包用的空构造

    public StorageActionPacket(int action, long id, int amount, String keyword, int offset) {
        this.action = action;
        this.id = id;
        this.amount = amount;
        this.keyword = keyword;
        this.offset = offset;
    }

    // 发送: 把字段一个个写进字节流。
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeLong(id);
        buf.writeInt(amount);
        ByteBufUtils.writeUTF8String(buf, keyword == null ? "" : keyword);
        buf.writeInt(offset);
    }

    // 接收: 按"和写入完全一样的顺序"把字段读回来。顺序错一个, 整个包就乱了。
    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readInt();
        id = buf.readLong();
        amount = buf.readInt();
        keyword = ByteBufUtils.readUTF8String(buf);
        offset = buf.readInt();
    }

    // 服务端收到后: 把活儿转交给 StorageService 去处理。
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
