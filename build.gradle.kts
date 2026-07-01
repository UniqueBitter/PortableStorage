import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// 从 shade 进来的依赖里剔除 Java 9 多版本类 (META-INF/versions/**) 与多余元数据。
// 1.7.10 FML 的 ASM 5.0.3 无法解析 Java 9 类文件, 若保留会在模组发现阶段抛
// IllegalArgumentException 导致整个 jar 被忽略 / 模组不加载。
tasks.named<ShadowJar>("shadowJar") {
    exclude("META-INF/versions/**")
    exclude("META-INF/maven/**")
    exclude("META-INF/native-image/**")
    exclude("module-info.class")

    // sqlite-jdbc 默认把二十来种平台的原生库(.so/.dll, 各 ~1MB, 且几乎不可压缩)全打进来 → 体积暴涨。
    // 只保留主流桌面平台: Windows/Linux/Mac 的 x86_64 + Mac/Linux 的 aarch64。其余全部剔除。
    listOf(
        "Linux-Android", "Linux-Musl", "FreeBSD",
        "Linux/ppc64", "Linux/arm", "Linux/armv6", "Linux/armv7", "Linux/x86",
        "Windows/aarch64", "Windows/armv7", "Windows/x86",
    ).forEach { exclude("org/sqlite/native/$it/**") }

    // 削掉未被引用到的依赖类 —— 主要是 PinIn 传递依赖 fastutil(43MB), 只保留 PinIn 实际用到的少数类。
    // 项目自身的类始终保留; 仅裁剪依赖。排除 sqlite-jdbc 不参与 minimize:
    // 它通过反射/驱动机制加载类, minimize 会误删导致运行期 ClassNotFound(当初关全局 minimize 的原因)。
    minimize {
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
    }
}
