package ltd.mc233;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

// /storage cap <get|add|set> [玩家] [数量] —— 查询/增加/设置随身仓库容量(能存多少种物品)。
// 给任务奖励 / CustomNPCs 对话调用, 实现"逐步解锁/扩容"。别名 /psstorage。
public class StorageCapCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "storage";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List getCommandAliases() {
        return Arrays.asList("psstorage");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/storage <cap <get|add|set> [玩家] [数量] | lock|unlock [玩家] | autovoucher [on|off]>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) throw new WrongUsageException(getCommandUsage(sender));

        // /storage lock|unlock [玩家] —— 锁定/解锁该玩家打开随身仓库的能力(状态开关)。
        if (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock")) {
            boolean lock = args[0].equalsIgnoreCase("lock");
            EntityPlayerMP t = args.length >= 2 ? getPlayer(sender, args[1]) : getCommandSenderAsPlayer(sender);
            StorageService.setLocked(t, lock);
            if (lock && t.openContainer instanceof ltd.mc233.inventory.ContainerPortableStorage) t.closeScreen();
            sender.addChatMessage(
                new ChatComponentText("§e" + t.getCommandSenderName() + " 随身仓库: " + (lock ? "§c已锁定(无法打开)" : "§a已解锁")));
            return;
        }

        // /storage autovoucher [on|off] —— 切换"栏位拓展器进背包自动使用"(写回 config, 重启记住)。
        if (args[0].equalsIgnoreCase("autovoucher")) {
            boolean v = args.length >= 2 ? (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true"))
                : !Config.autoConsumeVoucher;
            Config.setAutoConsumeVoucher(v);
            sender.addChatMessage(new ChatComponentText("§e栏位拓展器自动使用: " + (v ? "§a开(进背包即用)" : "§c关(改为右键手动)")));
            return;
        }

        if (!args[0].equalsIgnoreCase("cap")) throw new WrongUsageException(getCommandUsage(sender));
        String sub = args.length >= 2 ? args[1].toLowerCase() : "get";

        if (sub.equals("get")) {
            EntityPlayerMP t = args.length >= 3 ? getPlayer(sender, args[2]) : getCommandSenderAsPlayer(sender);
            int used = StorageService.getUsed(StorageProvider.keyFor(t));
            int cap = StorageService.getCapacity(t);
            sender.addChatMessage(
                new ChatComponentText("§e" + t.getCommandSenderName() + " 仓库容量: " + used + "/" + cap + " 种"));
            return;
        }

        // add / set 需要: [玩家] 数量, 或 数量(对自己)
        EntityPlayerMP target;
        int n;
        if (args.length >= 4) {
            target = getPlayer(sender, args[2]);
            n = parseInt(sender, args[3]);
        } else if (args.length == 3) {
            target = getCommandSenderAsPlayer(sender);
            n = parseInt(sender, args[2]);
        } else {
            throw new WrongUsageException("/storage cap " + sub + " [玩家] <数量>");
        }

        if (sub.equals("add")) {
            StorageService.addCapacity(target, n);
        } else if (sub.equals("set")) {
            StorageService.setCapacity(target, n);
        } else {
            throw new WrongUsageException("/storage cap <get|add|set> [玩家] [数量]");
        }
        int cap = StorageService.getCapacity(target);
        target.addChatMessage(new ChatComponentText("§a随身仓库容量已更新: §e" + cap + " 种"));
        if (sender != target) {
            sender.addChatMessage(
                new ChatComponentText("§a已设置 " + target.getCommandSenderName() + " 仓库容量为 " + cap + " 种"));
        }
    }
}
