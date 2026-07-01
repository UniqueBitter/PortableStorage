package ltd.mc233.net;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

// 中键点击背包格 → 切换该格锁定状态(锁定的格不会被"一键收纳"收走)。
public class LockSlotPacket implements IMessage {

    public int slot;

    public LockSlotPacket() {}

    public LockSlotPacket(int slot) {
        this.slot = slot;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
    }

    public static class Handler implements IMessageHandler<LockSlotPacket, IMessage> {

        @Override
        public IMessage onMessage(LockSlotPacket message, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            ltd.mc233.StorageService.toggleLock(p, message.slot);
            return null;
        }
    }
}
