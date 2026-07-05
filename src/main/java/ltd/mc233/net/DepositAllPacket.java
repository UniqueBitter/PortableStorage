package ltd.mc233.net;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import ltd.mc233.StorageService;

// 客户端→服务端: 按 F 键收纳背包, 收纳方式跟随当前收纳模式(全部收纳 / 仅补充仓库已有种类)。锁定格不收。
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
            // F 键跟随当前收纳模式: 匹配已有→只补充仓库已有种类; 否则→全部收纳。
            if (StorageService.isStackMatch(p)) StorageService.quickStack(p);
            else StorageService.quickDepositAll(p);
            return null;
        }
    }
}
