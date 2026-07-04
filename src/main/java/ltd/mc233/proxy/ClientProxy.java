package ltd.mc233.proxy;

import ltd.mc233.client.ItemGlowRenderer;
import ltd.mc233.client.KeyBindings;

public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        super.init();
        KeyBindings.init();
        // 掉落物高亮渲染挂到 Forge 事件总线(RenderWorldLastEvent 在该总线上)。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ItemGlowRenderer());
        // CNPC 交易界面上层画"货币背包+仓库共有多少"(DrawScreenEvent 也在 Forge 总线)。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ltd.mc233.client.TradeCurrencyOverlay());
    }
}
