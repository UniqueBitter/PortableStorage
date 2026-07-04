package ltd.mc233.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ltd.mc233.compat.StorageItemSource;
import noppes.npcs.NoppesUtilPlayer;

/**
 * 让 CNPC 的交易(及其它用到这两个静态方法的 role)把随身仓库也算进去。
 * 目标是 CNPC 自有的非混淆静态方法, 所以本文件里所有 @Inject 都用 remap = false,
 * 且 handler 必须是 static(注入静态方法)。
 * compareItems 有重载(2 参/4 参), 用完整方法描述符区分, 只钩 4 参(EntityPlayer, ItemStack, boolean, boolean)版本。
 */
@Mixin(NoppesUtilPlayer.class)
public abstract class MixinNoppesUtilPlayer {

    // 每次消耗前记录"背包扣完还差多少", HEAD 与 RETURN 之间靠 ThreadLocal 传递。
    private static final ThreadLocal<Long> PS_REMAIN = new ThreadLocal<Long>();

    // "背包够不够"判定: CNPC 判 false 时, 把仓库算进去再判一次。
    @Inject(
        method = "compareItems(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;ZZ)Z",
        at = @At("RETURN"),
        cancellable = true,
        remap = false)
    private static void ps$compareItems(EntityPlayer player, ItemStack item, boolean ignoreDamage, boolean ignoreNBT,
        CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || item == null) return; // 背包已够, 不插手
        long have = StorageItemSource.countAvailable(player, item, ignoreDamage, ignoreNBT);
        if (have >= item.stackSize) cir.setReturnValue(true);
    }

    // 消耗前: 算出背包扣完还差多少(= 需求 - min(需求, 背包持有))。
    @Inject(
        method = "consumeItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;ZZ)V",
        at = @At("HEAD"),
        remap = false)
    private static void ps$consumeHead(EntityPlayer player, ItemStack item, boolean ignoreDamage, boolean ignoreNBT,
        CallbackInfo ci) {
        if (item == null) {
            PS_REMAIN.set(0L);
            return;
        }
        long inInv = StorageItemSource.countInInventory(player, item, ignoreDamage, ignoreNBT);
        PS_REMAIN.set(item.stackSize - Math.min(item.stackSize, inInv));
    }

    // 消耗后: CNPC 已扣背包部分, 差额从仓库补扣。
    @Inject(
        method = "consumeItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;ZZ)V",
        at = @At("RETURN"),
        remap = false)
    private static void ps$consumeReturn(EntityPlayer player, ItemStack item, boolean ignoreDamage, boolean ignoreNBT,
        CallbackInfo ci) {
        Long remain = PS_REMAIN.get();
        PS_REMAIN.remove();
        if (remain != null && remain > 0 && item != null) {
            StorageItemSource.extractFromStorage(player, item, remain, ignoreDamage, ignoreNBT);
        }
    }
}
