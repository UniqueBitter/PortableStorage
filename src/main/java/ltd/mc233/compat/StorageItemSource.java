package ltd.mc233.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import ltd.mc233.StorageProvider;
import ltd.mc233.core.StoredItem;
import ltd.mc233.db.StorageDao;
import ltd.mc233.item.ItemStackCodec;
import noppes.npcs.NoppesUtilPlayer;

/**
 * CNPC 集成的胶水层: 把"随身仓库"当作玩家的额外物品来源。
 * 匹配一律复用 CNPC 自己的 NoppesUtilPlayer.compareItems, 与 CNPC 判定零分歧。
 * 只在装了 CNPC 时被加载(由 mixin/预加载守护)。
 */
public final class StorageItemSource {

    private StorageItemSource() {}

    // 用 CNPC 的 compareItems 把"仓库某行是否算作 required"包成一个谓词。
    private static StorageDao.RowMatch matcher(final ItemStack required, final boolean ignoreDamage,
        final boolean ignoreNBT) {
        return new StorageDao.RowMatch() {

            public boolean test(StoredItem it) {
                ItemStack s = ItemStackCodec.decode(it, 1); // 还原成真实 ItemStack; 对应 mod 已移除则为 null
                return s != null && NoppesUtilPlayer.compareItems(required, s, ignoreDamage, ignoreNBT);
            }
        };
    }

    // 背包里有几个符合 required(用 2 参 compareItems 逐格比, 镜像 CNPC 匹配)。
    public static long countInInventory(EntityPlayer p, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        if (p == null || required == null) return 0;
        long sum = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (ItemStack s : inv) {
            if (s != null && NoppesUtilPlayer.compareItems(required, s, ignoreDamage, ignoreNBT)) sum += s.stackSize;
        }
        return sum;
    }

    // 仓库里有几个符合 required。
    public static long countInStorage(EntityPlayer p, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        if (p == null || required == null) return 0;
        return StorageProvider.dao()
            .countMatching(StorageProvider.keyFor(p), matcher(required, ignoreDamage, ignoreNBT));
    }

    // 背包 + 仓库合计。
    public static long countAvailable(EntityPlayer p, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        return countInInventory(p, required, ignoreDamage, ignoreNBT)
            + countInStorage(p, required, ignoreDamage, ignoreNBT);
    }

    // 从仓库最多扣 need 个符合 required 的, 返回实际扣除量。
    public static long extractFromStorage(EntityPlayer p, ItemStack required, long need, boolean ignoreDamage,
        boolean ignoreNBT) {
        if (p == null || required == null || need <= 0) return 0;
        return StorageProvider.dao()
            .extractMatching(StorageProvider.keyFor(p), need, matcher(required, ignoreDamage, ignoreNBT));
    }
}
