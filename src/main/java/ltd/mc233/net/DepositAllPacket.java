package ltd.mc233.net;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import ltd.mc233.StorageService;

// 客户端→服务端: 按 F 键, 把背包物品全部收纳进随身仓库(锁定格不收、已归类的各回原标签、新种类进未分类)。
public class DepositAllPacket implements IMessage {

    public DepositAllPacket() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<DepositAllPacket, IMessage> {

        @Override
        public IMessage onMessage(DepositAllPacket message, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (StorageService.isLocked(p)) return null; // 随身仓库被锁 → 不收纳
            StorageService.quickDepositAll(p);
            return null;
        }
    }
}
