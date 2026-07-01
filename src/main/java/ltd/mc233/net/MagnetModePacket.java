package ltd.mc233.net;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import ltd.mc233.magnet.PlayerMagnetState;

public class MagnetModePacket implements IMessage {

    public MagnetModePacket() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<MagnetModePacket, IMessage> {

        @Override
        public IMessage onMessage(MagnetModePacket message, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            int m = (PlayerMagnetState.get(p) + 1) % 3;
            PlayerMagnetState.set(p, m);
            NetworkHandler.INSTANCE.sendTo(new MagnetModeAckPacket(m), p);
            return null;
        }
    }
}
