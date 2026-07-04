# 随身仓库 (PortableStorage)

Minecraft **1.7.10 Forge** 的随身大仓库 mod。随时随地打开一个可搜索、可分类、容量可无限的仓库,把背包整理得干干净净。为「英雄黎明4」RPG 整合包定制,同一份代码也能通过配置切换成通用大背包。

- **包名**:`ltd.mc233` ・ **MODID**:`portablestorage`
- **构建**:RetroFuturaGradle + GTNH gtnhconvention(Java 8 目标)

---

## 主要功能

### 仓库本体
- **随身访问**:按键(默认 `B`)或使用「仓库终端」物品,任何地方打开。
- **无限容量**(默认):能存无限种物品。也可在配置里改回 **RPG 容量制**(逐步解锁、靠「栏位拓展器」扩容)。
- **拼音搜索**:基于 PinIn,支持中文名/lore 的拼音、首字母模糊匹配。
- **排序**:名字 / 数量 / 类型 / 时间(放入顺序),升降序可切。所有视图(含标签页)都生效。

### 标签分类(右侧标签列)
- **全部** / **未分类**(右键「全部」格切换) + 最多 **15 个自定义标签**。
- **右键**标签 = 改名 ・ **Shift+右键** = 删除 ・ **中键** = 输入数字改排序。
- **手持物品左键点标签** = 把该物品归到此标签(点「全部」= 取消归类)。
- 存入时:已归类的物品**保留原标签**,不会被「当前正看着哪个标签」冲掉。

### 收纳 / 取出
- **全部收纳**(左侧下载图标):把背包非锁定物品全存入;**已归类的各回原标签,新种类进未分类**。
- **匹配已有物品**(右键该按钮切换):只补充**仓库已有种类**的物品(泰拉瑞亚式快速堆叠)。
- **F 键一键全部收纳**:不用开界面,任何地方按 `F` 全部收纳。
- **取出**:左键取一组 / Shift+左键直接进背包 / 右键取一个;**Shift+左键在仓库里划过 = 批量取出**。

### 格子锁定 & 自动化
- **中键锁定背包格**:锁定的格不会被「全部收纳」收走。
- **自动补货**:锁定的快捷栏格数量减少时,自动从仓库补满。
- **锁定格同步**:开/关仓库时,锁定格自动补满、背包里的同种多余物品自动存入。
- **磁铁**(默认 `K` 切档):范围自动拾取。

---

## 指令

| 指令 | 说明 |
|---|---|
| `/storage cap <get\|add\|set> [玩家] [数量]` | 查询/增加/设置容量(RPG 容量制用) |
| `/storage lock\|unlock [玩家]` | 锁定/解锁某玩家打开仓库的能力 |
| `/storage autovoucher [on\|off]` | 栏位拓展器进背包是否自动使用 |
| `/reload`(别名 `psreload`/`psreindex`) | 重建搜索索引 |
| `/fly` `/god` `/repair` `/glow` 等 | 若干便捷指令(注解式 `@Cmd` 框架) |

## 按键(游戏设置→按键→「随身仓库」分类里可改)

| 键 | 功能 |
|---|---|
| `B` | 打开随身仓库 |
| `F` | 一键全部收纳 |
| `K` | 切换磁铁档位 |

## 配置(`config/portablestorage.cfg`)

| 分类.键 | 默认 | 说明 |
|---|---|---|
| `storage.unlimitedStorage` | `true` | 无限容量;设 `false` 恢复 RPG 容量门槛 |
| `storage.defaultCapacity` | `0` | 起始容量(容量制下) |
| `storage.maxCapacity` | `999` | 扩容券能提升到的上限 |
| `storage.autoConsumeVoucher` | `true` | 栏位拓展器进背包自动使用 |
| `general.autoRestock` | `true` | 自动补货 |
| `magnet.magnetRadius` | `32` | 磁铁半径(格) |
| `client.autoFocusSearch` | `true` | 打开界面自动聚焦搜索框 |

---

## 构建 & 安装

```bash
./gradlew build        # 构建, 产物在 build/libs/portablestorage-1.0.N.jar
```

- **版本号**:`1.0.<version.txt 里的数>`,每次构建脚本 `+1`,便于区分产物。
- **自动装包**:`build`/`assemble` 完成后自动把新 jar 装进整合包 `mods/`(先删旧的同名 mod)。
  - 路径可覆盖:`./gradlew build -PpackModsDir=某整合包/mods`
  - 游戏开着时 jar 被锁 → 只警告跳过,不制造重复 jar、不让构建失败。
  - IDEA 需在 *Build Tools → Gradle* 里把「Build and run using」设为 **Gradle** 才会触发。

### CustomNPCs 软依赖
- 编译期依赖 `libs/cnpc-api.jar`(从 CNPC.jar 抽出的纯 class 瘦身版),`compileOnly`,**不打包进产物**。
- 运行时用 `CnpcCompat.LOADED`(`Loader.isModLoaded("customnpcs")`)保护,**没装 CNPC 的地图也能正常加载**。

---

## 分支

- **`main`**:英雄黎明4 定制版,木质箱子(generic_54)背景 + 木色 UI。
- **`general-backpack`**:通用版,原灰色 UI,功能与 main 一致(不带木质背景/按钮配色)。

更新记录见 [CHANGELOG.md](CHANGELOG.md)。
