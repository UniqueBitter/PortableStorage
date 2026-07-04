package ltd.mc233.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import ltd.mc233.net.NetworkHandler;

public class KeyBindings {

    public static KeyBinding open;
    public static KeyBinding magnet;
    public static KeyBinding depositAll;

    public static void init() {
        open = new KeyBinding("key.portablestorage.open", Keyboard.KEY_B, "key.categories.portablestorage");
        magnet = new KeyBinding("key.portablestorage.magnet", Keyboard.KEY_K, "key.categories.portablestorage");
        depositAll = new KeyBinding("key.portablestorage.depositall", Keyboard.KEY_F, "key.categories.portablestorage");
        ClientRegistry.registerKeyBinding(open);
        ClientRegistry.registerKeyBinding(magnet);
        ClientRegistry.registerKeyBinding(depositAll);
        FMLCommonHandler.instance()
            .bus()
            .register(new KeyBindings());
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (open.isPressed()) NetworkHandler.openStorage();
        if (magnet.isPressed()) NetworkHandler.cycleMagnet();
        if (depositAll.isPressed()) NetworkHandler.depositAll();
    }
}
