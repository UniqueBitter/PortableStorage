package ltd.mc233.client;

import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ltd.mc233.compat.CnpcCompat;
import ltd.mc233.compat.CnpcTradeOverlay;

/**
 * 客户端: 在 CNPC 商人交易界面"上层"画出每种货币"背包 + 仓库 共有多少"。
 *
 * 用 Forge 的画屏幕事件(DrawScreenEvent.Post)—— 不钩 CNPC 界面代码, 天然稳、不会崩。
 * 真正碰 noppes.* 的绘制在 CnpcTradeOverlay, 只有装了 CNPC(LOADED)才会被调用到 →
 * JVM 惰性加载, 没装 CNPC 时那个类根本不加载, 不会 NoClassDefFoundError。
 */
public class TradeCurrencyOverlay {

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!CnpcCompat.LOADED) return;
        try {
            CnpcTradeOverlay.draw(e.gui);
        } catch (Throwable t) {
            // 出错不影响界面
        }
    }
}
