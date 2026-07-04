package ltd.mc233.compat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import noppes.npcs.client.gui.player.GuiNPCTrader;
import noppes.npcs.roles.RoleTrader;

/**
 * 真正引用 noppes.* 的交易界面覆盖绘制。**只允许被 TradeCurrencyOverlay 在 CNPC 存在时调用**——
 * 这样没装 CNPC 时本类不加载, 也就不解析上面的 noppes 导入。
 *
 * 行为: 鼠标悬停在某个交易的商品格上时, 在屏幕左上角显示该交易**需要的货币**"背包+仓库共有多少"
 * (双货币则各显示一行)。不悬停交易时不显示。数量复用 StorageItemSource.countAvailable(单机客户端可读集成服务器仓库)。
 */
public final class CnpcTradeOverlay {

    private CnpcTradeOverlay() {}

    private static Field slotField; // GuiContainer.theSlot(鼠标悬停的格子)
    private static Field roleField; // GuiNPCTrader.role(私有)
    private static int lastSlot = -999;
    private static long lastMs;
    private static final List<String> cached = new ArrayList<String>();

    public static void draw(GuiScreen gui) throws Exception {
        if (!(gui instanceof GuiNPCTrader)) return;
        Slot hovered = hoveredSlot(gui);
        int sn = hovered == null ? -1 : hovered.slotNumber;
        long now = System.currentTimeMillis();
        // 悬停的格子变了就立刻重算; 否则每 300ms 刷一次(防仓库数量变化)。
        if (sn != lastSlot || now - lastMs > 300) {
            lastSlot = sn;
            lastMs = now;
            recompute((GuiNPCTrader) gui, hovered);
        }
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int y = 6;
        for (String s : cached) {
            fr.drawStringWithShadow(s, 6, y, 0xFFFFFF);
            y += 10;
        }
    }

    // 反射拿 GuiContainer.theSlot(dev 是 theSlot, 生产 reobf 是 field_147006_u, 两个名都试)。
    private static Slot hoveredSlot(GuiScreen gui) throws Exception {
        if (slotField == null) {
            for (String n : new String[] { "field_147006_u", "theSlot" }) {
                try {
                    Field f = GuiContainer.class.getDeclaredField(n);
                    f.setAccessible(true);
                    slotField = f;
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (slotField == null) return null;
        }
        return (Slot) slotField.get(gui);
    }

    private static void recompute(GuiNPCTrader gui, Slot hovered) throws Exception {
        cached.clear();
        // 只在"悬停到某个交易的商品格"时显示; 空格 / 玩家背包格 都不算。
        if (hovered == null || !hovered.getHasStack() || hovered.inventory instanceof InventoryPlayer) return;
        if (roleField == null) {
            roleField = GuiNPCTrader.class.getDeclaredField("role");
            roleField.setAccessible(true);
        }
        RoleTrader role = (RoleTrader) roleField.get(gui);
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (role == null || role.inventoryCurrency == null || p == null) return;
        IInventory cc = role.inventoryCurrency;
        int idx = hovered.slotNumber; // 商品格槽位号 = 交易序号; 货币1=cc[idx], 货币2=cc[idx+18](CNPC 布局)
        Set<String> seen = new HashSet<String>(); // 两个货币格是同种就只显示一行
        addCurrency(cc, idx, role, p, seen);
        addCurrency(cc, idx + 18, role, p, seen);
    }

    private static void addCurrency(IInventory cc, int idx, RoleTrader role, EntityPlayer p, Set<String> seen) {
        if (idx < 0 || idx >= cc.getSizeInventory()) return;
        ItemStack c = cc.getStackInSlot(idx);
        if (c == null) return;
        String key = c.getUnlocalizedName() + ":" + c.getItemDamage();
        if (!seen.add(key)) return; // 同种货币只显示一次(合并重复)
        long total = StorageItemSource.countAvailable(p, c, role.ignoreDamage, role.ignoreNBT);
        cached.add(c.getDisplayName() + ": " + total);
    }
}
