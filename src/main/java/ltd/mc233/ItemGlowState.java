package ltd.mc233;

// 掉落物高亮的开关与半径(纯客户端显示)。命令在服务端线程写、渲染线程读, 单人同一 JVM 下 volatile 即可安全共享。
public final class ItemGlowState {

    private ItemGlowState() {}

    public static volatile boolean enabled = true; // 默认开; /glow 可关
    public static volatile int radius = 24;
}
