import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// 版本号: 1.0.<version.txt 里的补丁位>。覆盖掉 GTNH 默认的 git 版本, 使文件名固定为 1.0.N。
// 补丁位由构建脚本每次编译前 +1(见 version.txt), 便于区分每次编译产物。
val buildPatch = file("version.txt").takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0
version = "1.0.$buildPatch"
afterEvaluate { version = "1.0.$buildPatch" } // GTNH 插件在 afterEvaluate 设版本, 这里再覆盖一次

// CustomNPCs 软依赖: 只编译期用它的 API(libs/cnpc-api.jar 是从 CNPC.jar 抽出的纯 class 瘦身版)。
// compileOnly = 不打包进我们的 jar、运行时也不强制存在; 代码里用 Loader.isModLoaded("customnpcs") 保护。
dependencies {
    compileOnly(rfg.deobf(files("libs/cnpc-api.jar")))
}

// 构建后自动装入整合包 mods: 删掉旧的 PortableStorage jar, 再拷入这次构建的 jar。
// 挂在 build 之后, 所以 IDEA(走 Gradle 构建)编译完会自动装好。可用 -PpackModsDir=... 覆盖路径。
// 游戏开着锁住 jar 时: 只警告、不制造重复 jar、也不让构建失败。
val packModsDir = (findProperty("packModsDir") as String?) ?: "D:/MC/英雄黎明4玩家端0630/.minecraft/mods"

tasks.register("installToPack") {
    // 在配置期把需要的值抓成普通可序列化的常量(String/File), doLast 里只用这些, 满足配置缓存要求。
    val modsDirPath = packModsDir
    val libsDirFile = layout.buildDirectory.dir("libs").get().asFile
    val installedName = "PortableStorage-$version.jar"
    doLast {
        val re = Regex("(?i)portablestorage-.*\\.jar")
        val devRe = Regex(".*-(dev|sources|preshadow)\\.jar$")
        val mods = File(modsDirPath)
        if (!mods.isDirectory) {
            println("[installToPack] 整合包 mods 目录不存在, 跳过: $modsDirPath")
            return@doLast
        }
        val built = (libsDirFile.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".jar") && !devRe.matches(it.name) }
            .maxByOrNull { it.lastModified() }
        if (built == null) {
            println("[installToPack] build/libs 里没找到可安装的 jar, 跳过")
            return@doLast
        }
        try {
            (mods.listFiles() ?: emptyArray()).filter { re.matches(it.name) }.forEach { it.delete() }
            val remaining = (mods.listFiles() ?: emptyArray()).filter { re.matches(it.name) }
            if (remaining.isNotEmpty()) { // 删不掉(多半游戏开着锁住了) → 别拷, 免得两个 jar 重复崩游戏
                println("[installToPack] 旧 jar 删不掉(游戏可能开着), 为避免重复 mod 跳过安装")
                return@doLast
            }
            built.copyTo(File(mods, installedName), overwrite = true)
            println("[installToPack] 已装入整合包: $installedName")
        } catch (e: Exception) {
            println("[installToPack] 装入失败(游戏可能开着锁住了 jar): ${e.message}")
        }
    }
}

// 挂在 assemble 上(build 也会跑 assemble): IDEA 的"构建"多半跑 assemble, 这样编译完就自动装。
tasks.named("assemble") { finalizedBy("installToPack") }

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
