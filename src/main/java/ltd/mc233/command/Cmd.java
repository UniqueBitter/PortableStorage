package ltd.mc233.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 把一个方法标成一条斜杠命令。方法签名二选一:
// void xxx(EntityPlayerMP p, String[] args) —— 仅玩家可用(非玩家自动报错)
// void xxx(ICommandSender s, String[] args) —— 控制台/命令方块也可用
// 抛 WrongUsageException 即显示用法。用 AnnotatedCommand.registerAll(event, holder) 一次性注册整个类。
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cmd {

    String name();

    String[] aliases() default {};

    int level() default 2; // 所需权限等级(2=OP); 纯客户端类命令可设 0

    String usage() default "";

    String[] tab() default {}; // 首个参数的 Tab 补全候选(可选)
}
