package ltd.mc233;

// 掉落物高亮的开关与半径(纯客户端显示)。命令在服务端线程改写, 客户端渲染线程读取;
// 单人(集成服务端与客户端同一 JVM)下 volatile 即可安全共享。
public final class ItemGlowState {

    private ItemGlowState() {}

    public static volatile boolean enabled = false;
    public static volatile int radius = 24;
    // 调试: 直接把剪影 FBO 糊到屏幕, 用于判断是"捕获失败"还是"着色器失败"。
    public static volatile boolean debug = false;
}
