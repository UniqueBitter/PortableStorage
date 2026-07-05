package ltd.mc233.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ltd.mc233.compat.StorageItemSource;
import noppes.npcs.NpcMiscInventory;
import noppes.npcs.quests.QuestItem;

/**
 * 让 CNPC 的"交物品"任务把随身仓库也算进去。
 * 目标是 CNPC 自有的非混淆类/成员, 所以本文件里所有 @Shadow/@Inject 都用 remap = false。
 * NpcMiscInventory 来自未 deobf 的 cnpc-api, 直接调它的 IInventory 继承方法可能编不过(SRG 名),
 * 所以统一转成 net.minecraft.inventory.IInventory(MC 自己的、已 deobf 的接口)再调用。
 */
@Pseudo
@Mixin(QuestItem.class)
public abstract class MixinQuestItem {

    @Shadow(remap = false)
    public NpcMiscInventory items;
    @Shadow(remap = false)
    public boolean leaveItems;
    @Shadow(remap = false)
    public boolean ignoreDamage;
    @Shadow(remap = false)
    public boolean ignoreNBT;

    // CNPC 判"任务是否完成"后, 若判为未完成, 再把仓库算进去重新判一次。
    @Inject(method = "isCompleted", at = @At("RETURN"), cancellable = true, remap = false)
    private void ps$isCompleted(EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return; // CNPC 已认为完成, 不插手
        IInventory inv = this.items;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack req = inv.getStackInSlot(i);
            if (req == null) continue;
            long have = StorageItemSource.countAvailable(player, req, ignoreDamage, ignoreNBT);
            if (have < req.stackSize) return; // 有任一要求物合起来仍不够 → 维持"未完成"
        }
        cir.setReturnValue(true); // 全部要求物 背包+仓库 都够 → 判为完成
    }

    // HEAD 先算好每个要求物"背包扣完还差多少"存进 ThreadLocal, RETURN 时再从仓库补扣差额, 避免把背包已付的重复扣。
    private static final ThreadLocal<long[]> PS_SHORTFALL = new ThreadLocal<long[]>();

    @Inject(method = "handleComplete", at = @At("HEAD"), remap = false)
    private void ps$handleCompleteHead(EntityPlayer player, CallbackInfo ci) {
        // 只在服务端扣仓库(与交易同理: 客户端也跑会双扣); leaveItems 的任务不消耗物品。
        if (player.worldObj == null || player.worldObj.isRemote || leaveItems) {
            PS_SHORTFALL.remove();
            return;
        }
        IInventory inv = this.items;
        int n = inv.getSizeInventory();
        long[] shortfall = new long[n];
        for (int i = 0; i < n; i++) {
            ItemStack req = inv.getStackInSlot(i);
            if (req == null) continue;
            long inInv = StorageItemSource.countInInventory(player, req, ignoreDamage, ignoreNBT); // 扣之前的背包量
            shortfall[i] = req.stackSize - Math.min(req.stackSize, inInv); // 背包出这么多, 剩下由仓库补
        }
        PS_SHORTFALL.set(shortfall);
    }

    @Inject(method = "handleComplete", at = @At("RETURN"), remap = false)
    private void ps$handleCompleteReturn(EntityPlayer player, CallbackInfo ci) {
        if (player.worldObj == null || player.worldObj.isRemote) return; // 客户端不扣仓库
        long[] shortfall = PS_SHORTFALL.get();
        PS_SHORTFALL.remove();
        if (shortfall == null) return; // leaveItems=true 或未捕获
        IInventory inv = this.items; // items 是任务"要求物"模板, CNPC 扣的是玩家背包, 这里 HEAD→RETURN 不变
        int n = Math.min(shortfall.length, inv.getSizeInventory());
        for (int i = 0; i < n; i++) {
            ItemStack req = inv.getStackInSlot(i);
            if (req == null || shortfall[i] <= 0) continue;
            StorageItemSource.extractFromStorage(player, req, shortfall[i], ignoreDamage, ignoreNBT);
        }
    }
}
