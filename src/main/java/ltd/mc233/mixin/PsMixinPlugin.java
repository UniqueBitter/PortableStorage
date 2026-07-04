package ltd.mc233.mixin;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * 只有装了 CustomNPCs 时才应用本 mod 的 mixin(它们都钩 noppes.* 类)。
 * 注意: mixin 插件在 FML 加载 mod 之前就运行, 此时 Loader.isModLoaded 尚不可靠,
 * 因此用"类路径上有没有 noppes 类"来判断 CNPC 是否存在。
 */
public class PsMixinPlugin implements IMixinConfigPlugin {

    private static final boolean CNPC_PRESENT = detect();

    private static boolean detect() {
        // 关键: 绝不能用 Class.forName 加载 noppes 类! 本插件在 mixin/coremod 阶段运行, 早于 CNPC 自己的类变换器就绪;
        // 那时 Class.forName 会提前加载并变换 NoppesUtilPlayer, 在变换器不全时把它污染成"坏类" → 之后 CNPC 用到就 NoClassDefFound 崩。
        // getResource 只查 .class 文件在不在, 不加载/不链接/不变换, 安全。
        return PsMixinPlugin.class.getClassLoader()
            .getResource("noppes/npcs/NoppesUtilPlayer.class") != null;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return CNPC_PRESENT;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
