package ltd.mc233;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

// 无敌(/god): 在 LivingAttackEvent 最早阶段取消该玩家受到的所有伤害(含坠落/火/虚空等)。
public class GodHandler {

    public static boolean isGod(EntityPlayer p) {
        return p.getEntityData()
            .getCompoundTag("PlayerPersisted")
            .getBoolean("psGod");
    }

    public static void setGod(EntityPlayer p, boolean on) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persist = data.getCompoundTag("PlayerPersisted");
        if (!data.hasKey("PlayerPersisted")) data.setTag("PlayerPersisted", persist);
        persist.setBoolean("psGod", on);
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.entityLiving instanceof EntityPlayer && isGod((EntityPlayer) e.entityLiving)) {
            e.setCanceled(true);
        }
    }

    // god 攻击者 → 秒杀: 把最终伤害设为目标生命上限+1(此时已过护甲/抗性计算, 直接扣血 → 必死)。
    // 近战和自己射出的箭都算(间接伤害源 getEntity() 返回射手)。
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.source == null || e.entityLiving == null) return;
        if (e.source.getEntity() instanceof EntityPlayer && isGod((EntityPlayer) e.source.getEntity())) {
            e.ammount = e.entityLiving.getMaxHealth() + 1.0F;
        }
    }

    // 无敌时: 每 tick 补满饱食度 + 清除所有负面药水效果(中毒/凋零/缓慢/虚弱/失明等)。
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayer p = e.player;
        if (p == null || p.worldObj == null || p.worldObj.isRemote) return;
        if (!isGod(p)) return;
        // 补满饥饿条 + 饱和度(addStats(20,1): 食物封顶到 20, 饱和度也拉到 20)。放在下面"无药水就return"之前, 否则平时没药水会被跳过。
        p.getFoodStats()
            .addStats(20, 1.0F);
        if (p.getActivePotionEffects()
            .isEmpty()) return;
        List<Integer> remove = new ArrayList<Integer>();
        for (Object o : p.getActivePotionEffects()) {
            PotionEffect pe = (PotionEffect) o;
            Potion pot = Potion.potionTypes[pe.getPotionID()];
            if (pot != null && pot.isBadEffect()) remove.add(pe.getPotionID());
        }
        for (Integer id : remove) p.removePotionEffect(id);
    }
}
