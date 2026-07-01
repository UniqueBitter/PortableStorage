package ltd.mc233.net;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import ltd.mc233.PortableStorageMod;

public final class NetworkHandler {

    private NetworkHandler() {}

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE
        .newSimpleChannel(PortableStorageMod.MODID);

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(OpenStoragePacket.Handler.class, OpenStoragePacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(QueryPagePacket.Handler.class, QueryPagePacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(PageResultPacket.Handler.class, PageResultPacket.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(StorageActionPacket.Handler.class, StorageActionPacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MagnetModePacket.Handler.class, MagnetModePacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MagnetModeAckPacket.Handler.class, MagnetModeAckPacket.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(LockSlotPacket.Handler.class, LockSlotPacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(RestockTogglePacket.Handler.class, RestockTogglePacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(TabActionPacket.Handler.class, TabActionPacket.class, id++, Side.SERVER);
    }

    public static void openStorage() {
        INSTANCE.sendToServer(new OpenStoragePacket());
    }

    public static void requestPage(String kw, int off, int lim, int sort, int tabId) {
        INSTANCE.sendToServer(new QueryPagePacket(kw, off, lim, sort, tabId));
    }

    public static void sendTabAction(int op, int tabId, String name) {
        INSTANCE.sendToServer(new TabActionPacket(op, tabId, name));
    }

    public static void sendAction(int action, long id, int amount, String kw, int off) {
        INSTANCE.sendToServer(new StorageActionPacket(action, id, amount, kw, off));
    }

    public static void cycleMagnet() {
        INSTANCE.sendToServer(new MagnetModePacket());
    }

    public static void lockSlot(int slot) {
        INSTANCE.sendToServer(new LockSlotPacket(slot));
    }

    public static void toggleRestock() {
        INSTANCE.sendToServer(new RestockTogglePacket());
    }
}
