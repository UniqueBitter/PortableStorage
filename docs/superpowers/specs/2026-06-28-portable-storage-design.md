# 随身无限仓库模组 设计文档 (PortableStorage)

- 日期: 2026-06-28
- 目标游戏: 《开放世界RPG 英雄黎明4：烈焰之火 EA3.0.4》
- 平台: Minecraft **1.7.10**, Forge **1.7.10-10.13.4.1614** (经典 `cpw.mods.fml.*` 包名)
- 类型: 独立客户端/服务端通用模组, 玩家无需另装前置(sqlite-jdbc / pinyin4j 已 shade 进 jar)
- 安装包目录: `D:\MC\开放世界RPG 英雄黎明4：烈焰之火 EA3.0.4\.minecraft\mods\`
- 开发工程目录: `D:\MC\mods\PortableStorage\`

---

## 1. 目标与范围

给单机 RPG 玩家一个"随身的、无限容量、可搜索"的仓库(对标 Applied Energistics 的 ME 终端手感),
外加一个磁铁(自动拾取)功能。**不是** AE 那套能量/信道/基建——是纯随身、即开即用。

### 必须有 (In scope)
1. 无限容量的随身仓库 (按玩家 UUID, 存档内持久化)。
2. AE 风格终端 GUI: 滚动物品网格 + 数量角标 + **顶部搜索框实时过滤**。
3. 两种打开方式: 快捷键 `B` (默认, 可改键) + 一个"仓库终端"道具(手持右键)。
4. 存取: 手动拖拽 + 点击取出 + **「一键收纳背包」按钮**。
5. NBT 感知: 同物品但 NBT(名字/词条/附魔/属性)不同 → 各存一条, 完整保留, 绝不丢数据。
6. 磁铁(自动拾取)功能, 快捷键 `K` 三档循环: 关闭 / 进入背包 / 进入仓库。
   - "进入背包"档背包满了 → 溢出自动转存仓库。
   - 吸取半径**可调, 范围 1–128 格**(配置文件), 默认 8。

### 不做 (Out of scope, YAGNI)
- 能量/信道/合成自动化等 AE 重度机制。
- 磁铁过滤白/黑名单 (留作以后)。
- 多人服务器专门优化 (结构按 UUID 设计, 开 LAN 不会坏, 但不专门测)。
- 跨存档共享仓库 (刻意按存档隔离, 防止串数据)。

---

## 2. 架构总览

模组在单机下也跑一个集成服务器。仓库数据是**权威状态, 存在服务端的 SQLite 数据库**;
**数据不全量常驻内存**——客户端只看"当前页/搜索结果"这一小批, 一切增删/查询通过 Forge 网络包走服务端。
这是为了应对"条目种类极多"时的内存/存档压力(详见 §3)。

```
[客户端]                              [集成服务器]
 按键 B / 道具右键  ──open包──────▶   打开 Container, 请求第0页
 GUI 渲染可见窗口/搜索◀─page包───────   查 DB(窗口+服务端搜索), 只下发可见窗口
 滚动条/滚轮/搜索词  ──query包─────▶   带 offset+关键词取下一段窗口
 点击/拖拽/一键收纳  ──action包────▶   改 DB(upsert/扣减), 回发受影响页
 按键 K 切磁铁档     ──mode包──────▶   改该玩家磁铁档位
                                       每 tick 扫描掉落物 → upsert 进 DB / 背包
```

### 组件清单 (每个职责单一)

| 组件 | 类型 | 职责 | 依赖 |
|---|---|---|---|
| `PortableStorageMod` | @Mod 主类 | 注册物品/网络/按键/事件, 初始化代理与 DB | Forge |
| `StorageDao` | DAO | 仓库数据访问层: upsert/扣减/分页查询/服务端搜索, 封装 SQLite | sqlite-jdbc |
| `StorageDb` | DB 管理 | 每存档一个 .db 连接的开/关、建表、事务、定位文件 | sqlite-jdbc |
| `StorageEntry` | 数据类 | 一条 = ItemStack 模板(物品+meta+NBT) + `long count`(传输/显示用) | — (纯逻辑, 可单测) |
| `ItemStackCodec` | 工具 | ItemStack ↔ (registryName, meta, nbtBlob, nbtHash, 显示名, lore) 互转 | — (可单测) |
| `ContainerPortableStorage` | Container | 服务端容器: 处理取出/存入/一键收纳, 调用 DAO | StorageDao |
| `GuiPortableStorage` | GuiContainer | 客户端: 当前页网格渲染、数量角标、搜索框、翻页、按钮 | 同步来的"当前页" |
| `ItemPortableTerminal` | Item | "仓库终端"道具, 右键发 open 包 | — |
| `MagnetManager` | 服务端 tick 事件 | 扫描范围内 EntityItem, 按玩家档位收取(批量 upsert) | StorageDao |
| `PlayerMagnetState` | 数据 | 每玩家磁铁档位(0/1/2), 随玩家持久化 | — |
| `KeyBindings` | 客户端 | 注册 `B`/`K`, 发包 | — |
| `NetworkHandler` | SimpleNetworkWrapper | 各类包的注册与收发 | — |
| `ClientProxy/CommonProxy` | 代理 | 分离客户端(GUI/按键/渲染)与通用注册 | — |

---

## 3. 数据模型与持久化(SQLite, 不全量进内存)

### 为什么用 SQLite 而非一整块 NBT
- **数量大不爆内存**: 同种物品的数量用一个整数列(`BIGINT`)存, 一千万个圆石也只占一行, 几乎不耗内存。
- **种类极多才耗内存**: 真正吃内存的是"不同物品/不同 NBT 的条目数"。一整块 NBT 持久化(`WorldSavedData`)会把
  **所有条目常驻内存** + **每次自动存档整块重新序列化** → 种类极多时内存峰值/卡顿, 甚至 OOM。
- **SQLite 方案**: 条目落在磁盘 `.db` 文件, 内存里只放"玩家当前看到的那一页 + 当前一笔操作", 存档不再整块序列化。
  能稳妥支撑百万级条目。**装备自身的 NBT 仍完整保留**(压成一列 BLOB), 取出后属性/词条不丢。

### DB 文件位置(每存档隔离)
- 路径: `<存档目录>/portablestorage/storage.db`(随存档走, 不跨存档串数据)。
- 每个存档一个连接; 单机单线程访问(见并发说明)。世界加载时开库建表, 卸载/关服时关库。

### 表结构 `entries`
```sql
CREATE TABLE IF NOT EXISTS entries (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  player    TEXT    NOT NULL,            -- 玩家 UUID(为多人/LAN 预留)
  item      TEXT    NOT NULL,            -- 注册名, 如 "minecraft:diamond"(GameRegistry 取)
  meta      INTEGER NOT NULL,            -- itemDamage / 子ID
  nbt       BLOB,                        -- 序列化后的 ItemStack NBT(无则 NULL)→ 保留装备属性
  nbt_hash  TEXT    NOT NULL,            -- nbt 的哈希(无 nbt 时为固定值), 做相等判定/去重
  count     INTEGER NOT NULL,            -- 持有量, BIGINT
  name      TEXT,                        -- 缓存的本地化显示名(小写), 供搜索
  lore      TEXT,                        -- 缓存的 lore 词条拼接(小写), 供搜索
  pinyin    TEXT,                        -- 显示名的全拼(小写无空格, 如"钻石"→"zuanshi"), 供拼音搜索
  pinyin_in TEXT,                        -- 显示名的拼音首字母(如"钻石"→"zs"), 供首字母搜索
  UNIQUE(player, item, meta, nbt_hash)
);
CREATE INDEX IF NOT EXISTS idx_player_name ON entries(player, name);
```
- **堆叠判定 = `UNIQUE(player,item,meta,nbt_hash)`**: 注册名、meta、NBT 哈希全相同才合并 →
  同名不同属性的 RPG 装备 `nbt_hash` 不同 → 各占一行, 绝不混淆。

### DAO 操作(全部可单测)
- `insert(player, ItemStack)` → `INSERT ... ON CONFLICT(player,item,meta,nbt_hash) DO UPDATE SET count=count+?`(upsert)。
- `insertBatch(player, List<ItemStack>)` → 一个事务内批量 upsert(磁铁每 tick 用, 减少 IO)。
- `extract(player, id, amount)` → `UPDATE count=count-?`; 归零则 `DELETE`; 返回取出的 ItemStack(用 nbt 列还原)。
- `queryPage(player, keyword, offset, limit)` → 服务端搜索+分页, **同时匹配 中文名/词条/全拼/首字母**:
  `WHERE player=? AND (name LIKE ? OR lore LIKE ? OR pinyin LIKE ? OR pinyin_in LIKE ?) ORDER BY name LIMIT ? OFFSET ?`
  (keyword 已转小写, `%kw%`)。打中文匹配 name/lore, 打拼音匹配 pinyin/pinyin_in, 一个 OR 全覆盖, 不用判断输入类型。
- `countMatches(player, keyword)` → 给客户端算滚动条范围(同样的 WHERE 条件)。

### 显示名/lore/拼音 缓存
- 入库时由服务端用 `StatCollector.translateToLocal(unlocalizedName)` 取本地化名, 连同 lore **转小写**存进 `name`/`lore`。
- **拼音**: 用拼音库(pinyin4j)把显示名转成全拼(`zuanshi`, 无空格小写)与首字母(`zs`)存进 `pinyin`/`pinyin_in`。
  - 多音字: 取首选读音即可(够用); 非中文字符(英文/数字)原样保留, 使纯英文物品名照样能搜。
- 单机服务端与客户端同语言, 中文名/词条/拼音都在 DB 层 `LIKE` 匹配, 搜索在服务端完成。

### 并发
- 所有 DB 访问都在**服务端主线程**(容器交互、磁铁 tick 均在服务端线程)→ 单连接、单线程, 不需要加锁, 避开 SQLite 并发坑。
- 磁铁批量写用事务, 容器操作即时提交。

### PlayerMagnetState
- 档位 0=关闭 / 1=进背包 / 2=进仓库。
- 持久化: 存在玩家实体 `EntityPlayer` 的持久化 NBT (`getEntityData().getCompoundTag("PlayerPersisted")`),
  保证死亡/重连不丢档位。(这点数据极小, 用 NBT 没问题。)

---

## 4. 终端 GUI 行为

- 布局: 顶部搜索框; 中部固定可见区(如 9 列 × 5 行 = 45 格)的物品网格; 右侧 **AE 风格滚动条**; 底部"一键收纳背包"按钮; 再下方玩家背包。
- **AE 风格连续滚动(本条是这次新增/重点)**:
  - **不是**翻页按钮, 而是和 AE 的 ME 终端一样: **右侧滚动条可拖动 + 鼠标滚轮**连续滚动。
  - 滚动条范围 = `ceil(总匹配数 / 列数) - 可见行数`(总匹配数由服务端 `countMatches` 给出)。
  - 客户端只在内存里维护一个**可见行 ± 缓冲(预取上下各几行)的小窗口**;
    滚动改变行偏移时, 若超出已缓存窗口, 就按新偏移向服务端取下一段(`QueryPagePacket(keyword, offset, limit)`)。
  - 取数有 ~轻微节流/预取, 滚动手感顺滑; 任何时刻客户端内存只有这一小窗, 不随仓库总量增长。
  - 即"外表是 AE 那种顺滑滚动, 底层是按需窗口查询" —— 滚动条丝滑, 内存恒定二者兼得。
- **数量角标**: 大数缩写显示 (如 `1.2K`, `3.4M`), 完整数值在 tooltip。
- **搜索框**: 输入即过滤, 大小写不敏感, 子串匹配 **显示名(中文) + lore 词条 + 拼音(全拼/首字母)**;
  例: 搜「钻石」可输入 `钻石` / `zuanshi` / `zs` 任意一种。**走服务端 DB 搜索**(避免全量同步),
  输入做 ~150ms 防抖, 重新查询并把滚动位置归零; 服务端只回发当前可见窗口。手感接近即时, 又不把全部条目塞进客户端内存。
- **取出交互**:
  - 左键条目 → 取出一组(最多 maxStackSize)到鼠标光标。
  - 右键条目 → 取出 1 个。
  - Shift+左键 → 直接塞进玩家背包。
- **存入交互**: 把光标上的物品拖/放到网格区域 → 全部存入。
- **一键收纳背包按钮**: 把玩家背包(含快捷栏)所有物品存入仓库; **跳过**身上装备槽与"仓库终端"道具本身。
- **排序**: 默认按显示名升序; (排序切换留作以后, 先不做)。
- 防呆: InventoryTweaks 不应介入本 GUI 的网格(必要时实现 `IInventoryTweaksIgnore` 或对应标记)。

---

## 5. 打开方式

- **快捷键 `B`** (默认, 玩家可在"控制"里改键): 发 open 包 → 服务端开容器。
- **仓库终端道具** `ItemPortableTerminal`: 手持右键 → 发 open 包(开同一个仓库)。
  - 获取: **仅创造模式物品栏可取 + `/give portablestorage:terminal` 命令给**; **不做合成配方**。
  - 因为按键 `B` 已能随时开仓库, 道具属可选便利项。

---

## 6. 磁铁(自动拾取)

- **快捷键 `K`**: 每按一次档位 +1 循环 (0→1→2→0), 切换时聊天栏提示当前档(中文)。
- 档位语义:
  - **0 关闭**: 不吸取。
  - **1 进入背包**: 范围内掉落物收进玩家背包; 背包放不下的部分 → **自动转存仓库**(溢出不落地)。
  - **2 进入仓库**: 范围内掉落物**直接进随身仓库**(无限, 永不满)。
- 实现: `MagnetManager` 监听服务端 `PlayerTickEvent`, 对档位>0 的玩家, 取其周围半径 R 内
  可拾取的 `EntityItem`, 按档位路由后 `setDead()` 移除实体; 给个拾取音效/经验照常。
- **范围 R**: 可调, 范围 **1–128 格**(配置文件 `config/portablestorage.cfg`), 默认 8;
  读取时 clamp 到 [1,128]。半径越大每 tick 扫描越费性能, 大半径时对 EntityItem 查询做合理上限。
- NBT 物品同样完整保留(走 `StorageData.insert`, 复用堆叠规则)。

---

## 7. 网络包 (Forge SimpleNetworkWrapper)

| 包 | 方向 | 载荷 | 作用 |
|---|---|---|---|
| `OpenStoragePacket` | C→S | — | 请求打开仓库容器 |
| `QueryPagePacket` | C→S | 关键词 + offset + limit | 按滚动位置请求一段窗口 |
| `PageResultPacket` | S→C | 窗口条目(item,meta,nbt,count,显示名) + offset + 总匹配数 | 下发当前可见窗口(含总数, 供滚动条) |
| `StorageActionPacket` | C→S | 操作类型 + 条目id + 数量 | 取出/存入/一键收纳 |
| `MagnetModePacket` | C→S | — (或目标档位) | 切换/上报磁铁档位 |
| `MagnetModeAckPacket` | S→C | 当前档位 | 回传确认, 供 HUD/提示 |

- **同步策略**: 永不全量同步。AE 风格滚动: 按滚动位置请求"可见窗口 + 上下缓冲"(如可见 45 格, 取 limit≈90 含缓冲);
  操作完成后服务端只回发当前窗口。单包小、客户端内存恒定。
- `PageResultPacket` 单包注意不超过 1.7.10 的包大小上限, 单次窗口条目数限制在合理范围(默认 ≤ 90 条)。

---

## 8. 质量保证 (测试策略)

- **TDD 覆盖纯逻辑核心** (无需启动 MC, JUnit 即可):
  - `StorageDao`(对一个临时 .db 跑): upsert 合并同条目、meta/nbt_hash 不同 → 各占一行(重点: RPG 装备不混淆)。
  - DAO 数量加减: extract 扣减、归零删行、`count` 用 BIGINT 大数不溢出(存 10 亿级再读出一致)。
  - DAO 持久化往返: 写入 → 关库 → 重开 → 条目/count/nbt 完全一致(含 long 精度、nbt BLOB 还原)。
  - DAO 搜索分页: `queryPage` 关键词匹配 name/lore/拼音、大小写不敏感、offset/limit 正确、`countMatches` 准确。
  - 拼音转换: "钻石"→全拼 `zuanshi` / 首字母 `zs`; 含英文数字混合名原样保留; 同一条目搜中文/全拼/首字母都命中。
  - `ItemStackCodec`: ItemStack ↔ (item,meta,nbt,nbt_hash,name,lore) 往返一致; 相同 NBT 同 hash、不同 NBT 不同 hash。
  - 磁铁路由逻辑(纯函数): 给定背包剩余空间 + 档位 → 计算"进背包多少/转仓库多少"。
  - 注: 依赖 MC 类(ItemStack/NBT)的测试用 1.7.10 deobf 依赖在 test 源集跑; DAO 测试用真实 sqlite-jdbc 跑临时库;
    纯算术/路由逻辑尽量抽离成不依赖 MC 的函数, 优先覆盖。
- **手动游戏内验证清单** (GUI/按键/磁铁/同步):
  1. 按 `B` 开仓库, 拖入/取出/Shift取出/右键取1 均正确。
  2. 搜索: 中文名 / 词条 / 全拼(zuanshi) / 首字母(zs) 都能正确过滤。
  3. 一键收纳: 背包清空进仓库, 装备与终端道具不被收走。
  4. 同名不同附魔的装备各占一条, 取出后属性完好。
  5. 道具右键开的是同一个仓库。
  6. `K` 三档循环 + 提示; 进背包档背包满后溢出进仓库; 进仓库档直接入库。
  7. 退出重进游戏: 仓库内容与磁铁档位都保留(DB 文件在 `<存档>/portablestorage/storage.db`)。
  8. 大数量(>9999, 万级/亿级)角标显示与取出正常。
  9. 海量种类压测: 用命令/磁铁塞进上万种不同物品, 翻页/搜索流畅, 内存不暴涨, 存档不卡。

---

## 9. 构建与交付

- 构建系统: **RetroFuturaGradle (RFG)** + Gradle 8.14, Java 工具链指向自带
  **JDK 8 (Dragonwell)**: `D:\MC\开放世界RPG 英雄黎明4：烈焰之火 EA3.0.4\优化版java\`。
- 依赖: Forge `1.7.10-10.13.4.1614`, MCP mappings (stable 12), **sqlite-jdbc**(Java8 兼容版, 如 3.45.x),
  **pinyin4j**(中文转拼音, 纯 Java 无 native)。
- **打包**: 用 shadow 把 sqlite-jdbc 与 pinyin4j **shade 进最终 jar**(sqlite 含 win-x64 原生库), 玩家无需另装依赖。
  注意 relocate 以免与其它模组的同名库冲突; sqlite native 解压目录设到游戏可写路径。
- 产物: `./gradlew build` → `build/libs/PortableStorage-<ver>.jar` (已重混淆为 SRG, 内含 sqlite)。
- 交付: 把 jar 复制到包的 `mods/` 目录即可游玩。
- 模组元数据: modid `portablestorage`, 中文名"随身仓库"; `mcmod.info` 写好。

---

## 10. 风险与对策

| 风险 | 对策 |
|---|---|
| 1.7.10 重混淆/Forge 版本不匹配导致加载失败 | RFG 严格按 1614 版本; 装包前先用同版本 deobf 跑 runClient 验证 |
| 中文路径影响 Gradle/Java 工具链 | 工程放纯英文路径 `D:\MC\mods\PortableStorage\` |
| 一键收纳/磁铁误吞装备或终端道具 | 显式跳过装备槽与终端道具; 单测 + 手动验证清单第3/4条 |
| 条目过多/同步包过大 | 永不全量同步: 服务端分页+搜索, 每页 ≤90 条, 内存恒定 |
| **sqlite-jdbc 在 Forge LaunchClassLoader 下加载/解原生库失败** | 用正确 classloader `Class.forName` 驱动; native 解压到游戏可写目录; **预案: 若实测不稳定, 回退 MapDB(纯 Java 无 native)** |
| DB 文件随存档/损坏 | 每存档独立 .db; 开启 WAL; 写操作事务化; 关服时正常关库 |
| InventoryTweaks 干扰终端 GUI | 实现忽略标记 |
| `B`/`K` 与其它模组按键冲突 | 走 Forge KeyBinding(可在控制里改键), 默认值可改 |

---

## 11. 已定决策汇总

- 路线: 自定义轻量模组。
- 打开: `B` 快捷键 + 仓库终端道具, 两者都要。
- 终端道具获取: **仅创造栏 + `/give`, 不做合成配方**。
- 存取: 拖拽 + 一键收纳按钮。
- GUI: **AE 风格连续滚动**(滚动条 + 滚轮), 底层窗口查询。
- 搜索: 匹配 **中文名 + 词条 + 拼音(全拼 zuanshi / 首字母 zs)**, 服务端 DB 搜索。
- 特殊物品: 按 NBT 区分, 每种独立保留。
- 磁铁: `K` 三档(关/进背包/进仓库), 半径可调 1–128 格(默认8), 档位持久化。
- 进背包档背包满: 溢出转存仓库。
- **持久化: 仓库用 SQLite 数据库**(每存档一个 .db, **跨存档隔离不共享**, 不全量进内存, 支撑海量条目, 避免 OOM/存档卡);
  装备自身 NBT 作为一列 BLOB 完整保留; 磁铁档位存玩家持久化 NBT。
- 同步: 永不全量, 服务端窗口查询 + 服务端搜索, 单次 ≤90 条。
