package ltd.mc233.net;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PageResultPacket implements IMessage {

    public long total;
    public int offset;
    public int sort;
    public long lockMask;
    public boolean restock;
    public int capUsed;
    public int capTotal;
    public int curTab; // 当前视图标签 (-1=全部, 0=未分类, >=1=自定义)
    public int[] tabIds; // 自定义标签 id (>=1)
    public String[] tabNames;
    public long[] ids;
    public String[] items;
    public int[] metas;
    public long[] counts;
    public String[] names;
    public byte[][] nbts;

    public PageResultPacket() {}

    public PageResultPacket(long total, int offset, int sort, long lockMask, boolean restock, int capUsed, int capTotal,
        long[] ids, String[] items, int[] metas, long[] counts, String[] names, byte[][] nbts) {
        this.total = total;
        this.offset = offset;
        this.sort = sort;
        this.lockMask = lockMask;
        this.restock = restock;
        this.capUsed = capUsed;
        this.capTotal = capTotal;
        this.ids = ids;
        this.items = items;
        this.metas = metas;
        this.counts = counts;
        this.names = names;
        this.nbts = nbts;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int n = ids == null ? 0 : ids.length;
        buf.writeLong(total);
        buf.writeInt(offset);
        buf.writeInt(sort);
        buf.writeLong(lockMask);
        buf.writeBoolean(restock);
        buf.writeInt(capUsed);
        buf.writeInt(capTotal);
        buf.writeInt(curTab);
        int tn = tabIds == null ? 0 : tabIds.length;
        buf.writeInt(tn);
        for (int i = 0; i < tn; i++) {
            buf.writeInt(tabIds[i]);
            ByteBufUtils.writeUTF8String(buf, tabNames[i] == null ? "" : tabNames[i]);
        }
        buf.writeInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeLong(ids[i]);
            ByteBufUtils.writeUTF8String(buf, items[i]);
            buf.writeInt(metas[i]);
            buf.writeLong(counts[i]);
            ByteBufUtils.writeUTF8String(buf, names[i]);
            byte[] nbt = nbts == null ? null : nbts[i];
            if (nbt == null) {
                buf.writeInt(-1);
            } else {
                buf.writeInt(nbt.length);
                buf.writeBytes(nbt);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        total = buf.readLong();
        offset = buf.readInt();
        sort = buf.readInt();
        lockMask = buf.readLong();
        restock = buf.readBoolean();
        capUsed = buf.readInt();
        capTotal = buf.readInt();
        curTab = buf.readInt();
        int tn = buf.readInt();
        tabIds = new int[tn];
        tabNames = new String[tn];
        for (int i = 0; i < tn; i++) {
            tabIds[i] = buf.readInt();
            tabNames[i] = ByteBufUtils.readUTF8String(buf);
        }
        int n = buf.readInt();
        ids = new long[n];
        items = new String[n];
        metas = new int[n];
        counts = new long[n];
        names = new String[n];
        nbts = new byte[n][];
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readLong();
            items[i] = ByteBufUtils.readUTF8String(buf);
            metas[i] = buf.readInt();
            counts[i] = buf.readLong();
            names[i] = ByteBufUtils.readUTF8String(buf);
            int len = buf.readInt();
            if (len < 0) {
                nbts[i] = null;
            } else {
                nbts[i] = new byte[len];
                buf.readBytes(nbts[i]);
            }
        }
    }

    public static class Handler implements IMessageHandler<PageResultPacket, IMessage> {

        @Override
        public IMessage onMessage(PageResultPacket message, MessageContext ctx) {
            ltd.mc233.client.GuiPortableStorage.deliver(message);
            return null;
        }
    }
}
