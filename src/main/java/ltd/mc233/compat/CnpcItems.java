package ltd.mc233.compat;

import net.minecraft.item.Item;

import noppes.npcs.items.ItemBattleAxe;
import noppes.npcs.items.ItemBroadSword;
import noppes.npcs.items.ItemClaw;
import noppes.npcs.items.ItemCrossbow;
import noppes.npcs.items.ItemDagger;
import noppes.npcs.items.ItemGlaive;
import noppes.npcs.items.ItemGun;

/**
 * 真正引用 noppes.* 的实现类。**只允许被 {@link CnpcCompat} 在 LOADED=true 时调用**——
 * 这样没装 CNPC 时 JVM 不会加载本类, 也就不会去解析上面这些 noppes 导入。
 */
final class CnpcItems {

    private CnpcItems() {}

    static boolean isNpcWeapon(Item item) {
        return item instanceof ItemBattleAxe || item instanceof ItemBroadSword
            || item instanceof ItemDagger
            || item instanceof ItemGlaive
            || item instanceof ItemClaw
            || item instanceof ItemCrossbow
            || item instanceof ItemGun;
    }
}
