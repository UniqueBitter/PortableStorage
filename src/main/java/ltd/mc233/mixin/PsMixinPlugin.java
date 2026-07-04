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

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 直接放行, 不做 CNPC 检测: 我们的 mixin 只钩 noppes.* 类, 没装 CNPC 时这些类根本不加载, 靠 @Pseudo 本 mixin
        // 就不会应用(也不报错)。而各种检测都不靠谱: Class.forName 会提前把 noppes 类污染→崩; getResource 这阶段查不到;
        // Loader.isModLoaded 此时也不可靠。所以交给 @Pseudo 兜底最稳。
        return true;
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
