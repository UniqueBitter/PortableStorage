package ltd.mc233;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import ltd.mc233.command.Cmd;
import ltd.mc233.core.StoredItem;
import ltd.mc233.db.StorageDao;
import ltd.mc233.item.ItemStackCodec;

// 所有斜杠命令 = 一个带 @Cmd 的方法。serverStarting 里 AnnotatedCommand.registerAll(event, new ModCommands()) 一次注册全部。
public class ModCommands {

    private static void msg(ICommandSender s, String text) {
        s.addChatMessage(new ChatComponentText(text));
    }

    @Cmd(name = "fly", usage = "/fly - 切换飞行")
    public void fly(EntityPlayerMP p, String[] a) {
        boolean on = !p.capabilities.allowFlying;
        p.capabilities.allowFlying = on;
        if (!on) p.capabilities.isFlying = false;
        p.sendPlayerAbilities();
        msg(p, "飞行: " + (on ? "§a开" : "§c关"));
    }

    @Cmd(name = "flyspeed", usage = "/flyspeed <倍数> - 飞行速度倍数(1=默认, 最大50)")
    public void flySpeed(EntityPlayerMP p, String[] a) {
        if (a.length < 1) throw new WrongUsageException("/flyspeed <倍数>");
        float mult;
        try {
            mult = Float.parseFloat(a[0]);
        } catch (NumberFormatException e) {
            throw new WrongUsageException("请输入一个数字, 例如 /flyspeed 5");
        }
        mult = Math.max(0.1F, Math.min(50F, mult));
        p.capabilities.setFlySpeed(0.05F * mult);
        p.sendPlayerAbilities();
        msg(p, "§a飞行速度: §e" + mult + "§a 倍");
    }

    @Cmd(name = "setcount", aliases = "count", usage = "/setcount <数量> - 把手中物品数量改为该值(1-1000000)")
    public void setCount(EntityPlayerMP p, String[] a) {
        if (a.length < 1) throw new WrongUsageException("/setcount <数量>");
        int n;
        try {
            n = Integer.parseInt(a[0]);
        } catch (NumberFormatException e) {
            throw new WrongUsageException("请输入一个数字, 例如 /setcount 64");
        }
        n = Math.max(1, Math.min(1000000, n));
        ItemStack held = p.inventory.getCurrentItem();
        if (held == null) {
            msg(p, "§c手里没有拿物品");
            return;
        }
        held.stackSize = n;
        p.inventory.setInventorySlotContents(p.inventory.currentItem, held);
        p.inventoryContainer.detectAndSendChanges();
        msg(p, "§a已把手中物品数量改为 §e" + n);
    }

    @Cmd(name = "repair", usage = "/repair [all|eternal] - 修复手中物品(all=全部, eternal=不可破坏)", tab = { "all", "eternal" })
    public void repair(EntityPlayerMP p, String[] a) {
        if (a.length >= 1 && a[0].equalsIgnoreCase("eternal")) {
            ItemStack held = p.inventory.getCurrentItem();
            if (held == null) {
                msg(p, "§c手里没拿物品");
                return;
            }
            NBTTagCompound tag = held.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                held.setTagCompound(tag);
            }
            if (tag.getBoolean("Unbreakable")) {
                tag.removeTag("Unbreakable");
                msg(p, "§e已取消不可破坏");
            } else {
                tag.setBoolean("Unbreakable", true);
                held.setItemDamage(0);
                msg(p, "§b已设为不可破坏(永久耐久)");
            }
            p.inventoryContainer.detectAndSendChanges();
            return;
        }
        if (a.length >= 1 && a[0].equalsIgnoreCase("all")) {
            int c = repairArray(p.inventory.mainInventory) + repairArray(p.inventory.armorInventory);
            p.inventoryContainer.detectAndSendChanges();
            msg(p, "§a已修复 §e" + c + " §a件装备");
            return;
        }
        ItemStack held = p.inventory.getCurrentItem();
        if (held == null) {
            msg(p, "§c手里没拿物品");
            return;
        }
        if (!held.isItemStackDamageable()) {
            msg(p, "§7该物品没有耐久, 无需修复");
            return;
        }
        if (held.getItemDamage() == 0) {
            msg(p, "§7手中物品耐久已满");
            return;
        }
        held.setItemDamage(0);
        p.inventoryContainer.detectAndSendChanges();
        msg(p, "§a已修复手中物品");
    }

    private static int repairArray(ItemStack[] arr) {
        int c = 0;
        for (ItemStack st : arr) {
            if (st != null && st.isItemStackDamageable() && st.getItemDamage() > 0) {
                st.setItemDamage(0);
                c++;
            }
        }
        return c;
    }

    @Cmd(name = "god", usage = "/god - 切换无敌")
    public void god(EntityPlayerMP p, String[] a) {
        boolean on = !GodHandler.isGod(p);
        GodHandler.setGod(p, on);
        msg(p, "无敌: " + (on ? "§a开" : "§c关"));
    }

    @Cmd(name = "reload", aliases = { "psreload", "psreindex" }, usage = "/reload - 重建随身仓库搜索索引")
    public void reload(EntityPlayerMP p, String[] a) {
        String uuid = StorageProvider.keyFor(p);
        StorageDao dao = StorageProvider.dao();
        StorageDao.PageResult all = dao.allWithIds(uuid);
        int done = 0, skipped = 0;
        for (int i = 0; i < all.items.size(); i++) {
            StoredItem si = all.items.get(i);
            ItemStack st = ItemStackCodec.decode(si, 1);
            if (st == null) {
                skipped++;
                continue;
            }
            try {
                StoredItem fresh = ItemStackCodec.encode(st, p);
                dao.updateIndex(uuid, all.ids[i], fresh.getName(), fresh.getLore());
                done++;
            } catch (Throwable t) {
                skipped++;
            }
        }
        StorageService.refresh(p);
        msg(p, "§a搜索索引重建完成: §e" + done + " §a项已更新" + (skipped > 0 ? "§7, " + skipped + " 项跳过(物品失效)" : ""));
    }

    // 纯客户端显示, 无需玩家/高权限 → 用 ICommandSender 签名(非 playerOnly), level 0。
    @Cmd(name = "glow", aliases = { "itemglow", "loot" }, level = 0, usage = "/glow [半径] - 附近掉落物上方显示名字(默认开, 无参切换开关)")
    public void glow(ICommandSender s, String[] a) {
        if (a.length >= 1) {
            try {
                ItemGlowState.radius = Math.max(4, Math.min(128, Integer.parseInt(a[0])));
                ItemGlowState.enabled = true;
            } catch (NumberFormatException e) {
                msg(s, "§c用法: /glow [半径数字]");
                return;
            }
        } else {
            ItemGlowState.enabled = !ItemGlowState.enabled;
        }
        msg(s, ItemGlowState.enabled ? "§a掉落物名字: 开 §7(半径 " + ItemGlowState.radius + " 格)" : "§e掉落物名字: 关");
    }
}
