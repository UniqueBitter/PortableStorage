package ltd.mc233.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import ltd.mc233.net.NetworkHandler;

/**
 * 这个 mod 的三个快捷键, 纯客户端。
 * 干两件事: 开局把键"登记"进游戏(玩家能在控制里改), 平时检测到按下就发个包通知服务端。
 *
 * 为什么只在客户端? 键盘只有客户端有(服务端那条线程没键盘)。
 * 所以按键检测只能在这, 检测到了再发包让服务端去干活。
 * 只由 ClientProxy 注册(见 ClientProxy.init → KeyBindings.init)。
 */
public class KeyBindings {

    // 三个键各存一个对象, 后面 isPressed() 就是问它们"刚被按了吗"。
    public static KeyBinding open; // 打开仓库
    public static KeyBinding magnet; // 切换磁铁档位
    public static KeyBinding depositAll; // 一键全部收纳

    // 开局登记这三个键。第一个参数是名字(语言文件里翻译), 第二个是默认键, 第三个是它们在控制界面里的分类。
    public static void init() {
        open = new KeyBinding("key.portablestorage.open", Keyboard.KEY_B, "key.categories.portablestorage");
        magnet = new KeyBinding("key.portablestorage.magnet", Keyboard.KEY_K, "key.categories.portablestorage");
        depositAll = new KeyBinding("key.portablestorage.depositall", Keyboard.KEY_F, "key.categories.portablestorage");
        // 登记后它们会出现在游戏"控制"设置里, 玩家可以自己改键(所以默认 B/K/F, 但不写死)。
        ClientRegistry.registerKeyBinding(open);
        ClientRegistry.registerKeyBinding(magnet);
        ClientRegistry.registerKeyBinding(depositAll);
        // 把自己注册成监听器, 这样下面的 onKey 才会在有按键时被调用。
        FMLCommonHandler.instance().bus().register(new KeyBindings());
    }

    // 每次有键盘输入, Forge 都会调这个方法。我们挨个问三个键"刚被按下没", 按了就发对应的包。
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (open.isPressed()) NetworkHandler.openStorage(); // ← 整条"开界面"链就从这行发出去
        if (magnet.isPressed()) NetworkHandler.cycleMagnet();
        if (depositAll.isPressed()) NetworkHandler.depositAll();
    }
}
