package ltd.mc233.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import noppes.npcs.ServerEventsHandler;

/**
 * 让 CNPC 击杀任务也认"玩家参与的间接击杀"。
 *
 * CNPC 原逻辑(ServerEventsHandler.invoke(LivingDeathEvent)): 只有当 伤害来源实体是玩家 时才给击杀任务记数;
 * 附魔 mod 的自定义伤害(来源实体为 null 或非玩家)、召唤物/补刀 等 → 不算 → 任务卡住完不成。
 *
 * 这里在死亡事件最前面加一手: 若来源不是玩家、但这只怪"最近该记给玩家"(func_94060_bK, 与原版经验/掉落的
 * 击杀归属同款判定), 就调 CNPC 自己的 doQuest 记一次。**只在来源非玩家时介入**, 而 CNPC 那种情况本来就不记,
 * 所以不会重复记数。
 */
@Pseudo
@Mixin(value = ServerEventsHandler.class, remap = false)
public abstract class MixinServerEventsHandler {

    @Shadow(remap = false)
    private void doQuest(EntityPlayer player, EntityLivingBase killed, boolean area) {
        throw new AssertionError(); // @Shadow: 调用会被重定向到 CNPC 的真方法
    }

    @Inject(
        method = "invoke(Lnet/minecraftforge/event/entity/living/LivingDeathEvent;)V",
        at = @At("HEAD"),
        remap = false)
    private void ps$creditIndirectPlayerKill(LivingDeathEvent event, CallbackInfo ci) {
        EntityLivingBase killed = event.entityLiving;
        if (killed == null || killed.worldObj == null || killed.worldObj.isRemote) return;
        Entity src = event.source == null ? null : event.source.getEntity();
        if (src instanceof EntityPlayer) return; // 来源就是玩家 → CNPC 自己会记, 不插手
        EntityLivingBase attacker = killed.func_94060_bK(); // 该记这次击杀的实体(原版经验/掉落归属同款)
        if (attacker instanceof EntityPlayer) {
            doQuest((EntityPlayer) attacker, killed, true);
        }
    }
}
