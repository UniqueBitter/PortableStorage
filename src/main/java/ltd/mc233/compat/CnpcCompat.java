package ltd.mc233.compat;

import net.minecraft.item.Item;

import cpw.mods.fml.common.Loader;

/**
 * CustomNPCs 软依赖入口。没装 CNPC 时一切安全降级: 本类不引用任何 noppes.* 类, 所以能正常加载;
 * 真正触及 noppes API 的代码在 {@link CnpcItems} 里, 只有 LOADED=true 时才会被调用到,
 * JVM 惰性加载 → 没装 CNPC 时那个类永不加载, 不会 NoClassDefFoundError。
 */
public final class CnpcCompat {

    private CnpcCompat() {}

    /** 是否装了 CustomNPCs。所有会触及 noppes.* 的调用都要先过这道门。 */
    public static final boolean LOADED = Loader.isModLoaded("customnpcs");

    /** 是否是 CNPC 的近战武器类物品(示例, 供自动分类等用)。没装 CNPC → false。 */
    public static boolean isNpcWeapon(Item item) {
        if (!LOADED || item == null) return false;
        try {
            return CnpcItems.isNpcWeapon(item);
        } catch (Throwable t) { // 兜底: 万一 CNPC 版本不一致/类缺失, 也不拖垮我们
            return false;
        }
    }
}
