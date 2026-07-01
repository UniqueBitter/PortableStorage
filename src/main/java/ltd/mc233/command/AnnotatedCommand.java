package ltd.mc233.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

// 把一个带 @Cmd 的方法适配成 Forge 的 CommandBase。反射调用, 但只在 serverStarting 注册时反射一次方法,
// 不做类路径扫描 —— 避免 1.7.10 下 InvTweaks ASM 懒加载崩溃。
public class AnnotatedCommand extends CommandBase {

    private final Object holder;
    private final Method method;
    private final Cmd meta;
    private final boolean playerOnly; // 第一个参数是 EntityPlayerMP 则仅玩家可用

    private AnnotatedCommand(Object holder, Method method, Cmd meta) {
        this.holder = holder;
        this.method = method;
        this.meta = meta;
        method.setAccessible(true);
        this.playerOnly = method.getParameterTypes()[0] == EntityPlayerMP.class;
    }

    // 反射 holder 的所有 @Cmd 方法, 逐个注册为服务端命令。
    public static void registerAll(FMLServerStartingEvent event, Object holder) {
        for (Method m : holder.getClass()
            .getMethods()) {
            Cmd c = m.getAnnotation(Cmd.class);
            if (c != null) event.registerServerCommand(new AnnotatedCommand(holder, m, c));
        }
    }

    @Override
    public String getCommandName() {
        return meta.name();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List getCommandAliases() {
        return Arrays.asList(meta.aliases());
    }

    @Override
    public int getRequiredPermissionLevel() {
        return meta.level();
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return meta.usage()
            .isEmpty() ? "/" + meta.name() : meta.usage();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return (meta.tab().length > 0 && args.length == 1) ? getListOfStringsMatchingLastWord(args, meta.tab()) : null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (playerOnly && !(sender instanceof EntityPlayerMP)) {
            throw new WrongUsageException("只能由玩家使用");
        }
        try {
            method.invoke(holder, sender, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CommandException) throw (CommandException) cause; // WrongUsageException 等 → 显示用法
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
