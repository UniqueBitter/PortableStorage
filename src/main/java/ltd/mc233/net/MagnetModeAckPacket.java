package ltd.mc233.net;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MagnetModeAckPacket implements IMessage {

    public int mode;

    public MagnetModeAckPacket() {}

    public MagnetModeAckPacket(int mode) {
        this.mode = mode;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(mode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readInt();
    }

    public static class Handler implements IMessageHandler<MagnetModeAckPacket, IMessage> {

        @Override
        public IMessage onMessage(MagnetModeAckPacket message, MessageContext ctx) {
            String name = message.mode == 0 ? "关闭" : message.mode == 1 ? "进入背包" : "进入仓库";
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("磁铁: " + name));
            }
            return null;
        }
    }
}
