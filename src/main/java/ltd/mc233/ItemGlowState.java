package ltd.mc233;

// 掉落物高亮的开关与半径(纯客户端显示)。命令在服务端线程改写, 客户端渲染线程读取;
// 单人(集成服务端与客户端同一 JVM)下 volatile 即可安全共享。
public final class ItemGlowState {

    private ItemGlowState() {}

    public static volatile boolean enabled = true; // 默认开; /glow 可关
    public static volatile int radius = 24;
}
