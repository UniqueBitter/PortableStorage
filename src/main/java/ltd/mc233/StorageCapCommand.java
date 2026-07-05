package ltd.mc233;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
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
        return "/storage <cap <get|add|set|default> [玩家] [数量] | lock|unlock [玩家] | autovoucher [on|off]>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    // 按 Tab 时游戏会调这个方法要"候选词"。返回 null = 不补全(父类默认就是 null, 所以之前没补全)。
    // 按现在打到第几个词(args.length), 给不同候选。getListOfStringsMatchingLastWord 会自动只留下"和已输入前缀匹配"的。
    @SuppressWarnings("rawtypes")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        // 第 1 个词: 四个子命令
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "cap", "lock", "unlock", "autovoucher");
        }
        // 第 2 个词: 看第 1 个词是什么, 给对应候选
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("cap"))
                return getListOfStringsMatchingLastWord(args, "get", "add", "set", "default");
            if (args[0].equalsIgnoreCase("autovoucher")) return getListOfStringsMatchingLastWord(args, "on", "off");
            if (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock"))
                return getListOfStringsMatchingLastWord(
                    args,
                    MinecraftServer.getServer()
                        .getAllUsernames()); // 补在线玩家名
        }
        // 第 3 个词: /storage cap <get|add|set> [玩家] → 补玩家名
        if (args.length == 3 && args[0].equalsIgnoreCase("cap")) {
            return getListOfStringsMatchingLastWord(
                args,
                MinecraftServer.getServer()
                    .getAllUsernames());
        }
        return null;
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

        // /storage cap default [数量] —— 查询/设置"新玩家默认起始容量"(存进本存档的仓库 DB, 不用配置文件)。
        if (sub.equals("default")) {
            if (args.length >= 3) {
                int n = Math.max(0, parseInt(sender, args[2]));
                StorageService.setDefaultCapacity(n);
                sender.addChatMessage(new ChatComponentText("§a新玩家默认起始容量已设为 §e" + n + " §a种(随存档保存)"));
            } else {
                sender.addChatMessage(
                    new ChatComponentText("§e当前新玩家默认起始容量: §a" + StorageService.getDefaultCapacity() + " §e种"));
            }
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
