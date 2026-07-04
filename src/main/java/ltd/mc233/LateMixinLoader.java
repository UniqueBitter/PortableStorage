package ltd.mc233;

import java.util.Collections;
import java.util.List;

import cpw.mods.fml.common.Loader;
import io.github.tox1cozz.mixinbooterlegacy.ILateMixinLoader;
import io.github.tox1cozz.mixinbooterlegacy.LateMixin;

/**
 * 用 late-mixin 机制注册我们钩 CNPC 的那套 mixin(见 mixins.portablestorage.late.json)。
 *
 * ★必须放在 mixin 包(ltd.mc233.mixin)之外: 那个包被声明为 mixin 专用, 里面的类只能被"织"进目标、
 * 不能当普通类直接加载; 而本类要被 MixinBooterLegacy 扫描后实例化, 所以放在 ltd.mc233 顶层。
 *
 * 为什么用 late: 早加载(coremod/tweak 阶段)时 mixin 框架会立刻去加载 @Mixin 的目标类
 * (noppes 的 QuestItem/NoppesUtilPlayer)做校验, 但那时 CNPC 还没就绪 → 加载失败 →
 * 这些类被"污染"缓存成坏类 → 之后 CNPC 用到就崩。late 则等 FML 把各 mod 都装好后再排队应用,
 * 那时 noppes 的类已就绪, 不会污染。
 *
 * 软依赖: shouldMixinConfigQueue 在晚阶段判 CNPC 是否存在(此刻 Loader.isModLoaded 才可靠),
 * 没装 CNPC 就不排队 → 根本不碰 noppes 类。
 */
@LateMixin
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.portablestorage.late.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        return Loader.isModLoaded("customnpcs");
    }
}
