package ltd.mc233.core;

import me.towdium.pinin.PinIn;

// 拼音模糊匹配封装(基于 Towdium 的 PinIn, 即 NEC/NEI 的同款引擎): 直接在物品名/词条上匹配, 无需预先生成拼音列, 支持中文/全拼/首字母缩写/混合。
public final class PinInUtil {

    private PinInUtil() {}

    private static final PinIn PININ = new PinIn();

    // 线程安全兜底: 搜索在网络处理线程执行, PinIn 内部有缓存, 统一加锁避免并发问题。
    public static synchronized boolean matches(String text, String search) {
        if (search == null || search.isEmpty()) return true;
        if (text == null || text.isEmpty()) return false;
        try {
            return PININ.contains(text.toLowerCase(), search.toLowerCase());
        } catch (Throwable t) {
            // 引擎异常兜底: 退回朴素子串匹配, 绝不影响仓库可用。
            return text.toLowerCase()
                .contains(search.toLowerCase());
        }
    }

    // 在 init 阶段预热(加载 PinIn 相关类), 避免其在服务端 tick 内首次懒加载触发 InvTweaks ASM 崩溃。
    public static void preload() {
        try {
            PININ.contains("测试", "cs");
        } catch (Throwable ignored) {}
    }
}
