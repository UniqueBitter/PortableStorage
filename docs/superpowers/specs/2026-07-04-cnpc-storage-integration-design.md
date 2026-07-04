# CNPC 仓库集成 — 让任务/交易也认随身仓库里的物品

- 日期: 2026-07-04
- 状态: 设计待评审
- 前置: CustomNPCs(软依赖, 已有 `compat/CnpcCompat` 守护模式)

## 背景与目标

CNPC 的**物品任务**和**交易**只检查玩家的真实背包(`player.inventory`),看不到随身仓库里的东西。
目标: 让这两处**把随身仓库也算作玩家的物品来源**——判定"够不够"时把仓库计入, 实际扣除时**背包优先、仓库补差**。

对玩家而言: 任务物品/交易货币放仓库里也能直接交/直接买, 不用先取到背包。

## 非目标 (YAGNI)

- 不改 CNPC 自带的任务/交易**编辑界面**(策划照常用点点点的 GUI 配任务/交易, 无需脚本)。
- 不做"仓库=全局物品栏"的底层伪装(只针对 CNPC 这一个 mod 集成)。
- Kill/Location/Dialog 类任务不涉及(它们不查物品)。
- 银行(RoleBank)、其它 role 暂不做。

## 核心模型 (任务/交易共用)

一个统一的**物品来源 = 背包 + 随身仓库**。两个动作:

1. **数(count)**: 某物品在 `背包数量 + 仓库数量` 合计有多少。
2. **扣(consume)**: 需要扣 N 个时, **先从背包扣, 不足的从仓库扣**; 返回是否扣满 N 个。

任务和交易都归结到这两个动作, 只是接进 CNPC 的**注入点不同**。

## 注入点 (四个 Mixin)

| 场景 | CNPC 目标方法 | 原行为 | 注入后 |
|---|---|---|---|
| 任务·判定 | `QuestItem.isCompleted(EntityPlayer)` | 背包是否集齐 → bool | 背包不足时**再加仓库数量**判定 |
| 任务·扣除 | `QuestItem.handleComplete(EntityPlayer)` | `leaveItems=false` 时从背包扣 | 背包不足部分**从仓库补扣**(尊重 `leaveItems`) |
| 交易·判付 | `ContainerNPCTrader.canBuy(int, EntityPlayer)` | 背包货币是否够 → bool | **把仓库货币计入** |
| 交易·扣款 | `ContainerNPCTrader.slotClick(...)`(`func_75144_a`)成交扣款处 | 从背包扣货币 | 背包不足部分**从仓库补扣** |

- `canBuy(int, player)` 只吃玩家、不吃格子 → 交易确实是"数背包货币", 与任务同一原语。
- 精确的扣款注入位置(`slotClick` 内哪一步移除货币)**在实现时反编译 `canBuy`/`slotClick` 字节码确认**。

**注入策略备注**(反编译发现): 任务与交易走的中心方法不同, 影响钩法:
- **交易**走中心工具 `NoppesUtilPlayer.compareItems(EntityPlayer, ItemStack, ZZ)`(判有没有)
  + `consumeItem(EntityPlayer, ItemStack, ZZ)`(扣除)。**推荐直接钩这两个中心方法**:
  一处即覆盖交易(乃至银行等其它 role), 比钩 `canBuy`/`slotClick` 更省更稳。
  代价: 需确认没有别的"只想数不想动仓库"的调用方(实现时排查 compareItems 的调用点)。
- **任务** `QuestItem.isCompleted` **自己遍历背包槽**、用双参 `compareItems(ItemStack,ItemStack,ZZ)`,
  **不走**上面的中心方法 → 任务仍需单独钩 `isCompleted` / `handleComplete`。
- 无论哪种钩法, "仓库这行算不算一种"的判定都复用双参 `compareItems`(见匹配语义), 保证零分歧。

## 仓库 API (新增, 供 Mixin 调)

放在 mod 主代码(非 Mixin 包), Mixin 通过它读写仓库, 复用现有 `StorageProvider`/`StorageDao`:

```
long countInStorage(EntityPlayer p, ItemStack template, boolean ignoreDamage, boolean ignoreNBT)
long extractFromStorage(EntityPlayer p, ItemStack template, long need, boolean ignoreDamage, boolean ignoreNBT)
```

- `template` = CNPC 要求的物品样板; 匹配规则见下。
- `extractFromStorage` 最多扣 `need` 个, 返回实际扣了几个; 扣减走 `StorageDao.extract`(归零删行)。

## 匹配语义 (正确性重点 — 方案已定: 复用 CNPC 自己的匹配函数)

**已确认**(反编译 cnpc-api.jar): CNPC 所有物品匹配都走同一个静态函数
`NoppesUtilPlayer.compareItems(ItemStack 要求, ItemStack 候选, boolean ignoreDamage, boolean ignoreNBT) -> boolean`
(`QuestItem.isCompleted`、`ContainerNPCTrader.canBuy` 都调它); 消耗走
`NoppesUtilPlayer.consumeItem(EntityPlayer, ItemStack, boolean, boolean)`。

**方案**: 仓库侧**不自己写匹配规则**, 直接复用 `compareItems` 当唯一判定。
- `countInStorage` = 遍历该玩家仓库行, 每行 `ItemStackCodec.decode` 成 ItemStack,
  调 `compareItems(要求, 该行, ignoreDamage, ignoreNBT)` 判是否算一种, 命中的累加其 `count`。
- `extractFromStorage` 同理: 逐行 compareItems 命中 → 从其 count 扣。
- 因为用的是 CNPC 同一个函数, **CNPC 在背包里认的, 仓库里必然也认** —— 从根上杜绝"数得到扣不掉"。
- `ignoreDamage`/`ignoreNBT` 直接把 CNPC 该处的标志透传进来, 我们无需理解其细节。

**关键红线**: **不要用内部 `nbt_hash` 去和 CNPC 样板比对。** `nbt_hash` 按序列化字节算,
逻辑相等但 NBT 键序不同的物品可能得到不同 hash; 而 `compareItems` 做的是值比较。
故仓库匹配一律"逐行 decode + compareItems", 绝不走 hash 查。

**decode 保真**: `ItemStackCodec.decode` 是 `encode` 的精确逆(item/meta 一致, NBT 经
`CompressedStreamTools` 原样还原) → decode 出的堆与原物 `isItemEqual`+`areItemStackTagsEqual` 相等。
它是新的 Java 对象, 但 MC 判物品只看值不看对象引用, 无影响。

## 扣除顺序与原子性

- **顺序**: 背包优先, 仓库补差(已确认)。
- **原子性**: "判定够 → 实际扣满"必须一致。扣款/交任务时: 先算背包能出多少, 剩余向仓库 `extract`; 只有背包扣的 + 仓库扣的 = 需求量, 才算成交。任一步失败要能回退, 不能出现"货币少扣却给了货"(白拿)或"扣了却没给"(吞币)。
- 所有仓库访问都在**服务端主线程**(CNPC 交易/任务判定均在服务端), 与现有仓库单线程模型一致, 无并发问题。

## 软依赖守护

- Mixin 目标类是 `noppes.*` → 用 `@Pseudo` + `required = false`, 并让 mixin config 仅在 CNPC 存在时应用(IMixinConfigPlugin 里 `Loader.isModLoaded("customnpcs")` 判断, 或 `@Restriction`)。
- **没装 CNPC 时**: mixin 不应用, mod 照常加载, 沿用 `CnpcCompat.LOADED` 的降级精神。

## 构建改动

- `gradle.properties`: `usesMixins = true`; 设 `mixinsPackage`(如 `ltd.mc233.mixin`); 视需要设 `mixinPlugin`(条件加载插件)。
- 开启后构建自动拉 UniMixins 依赖(GTNH RFG 约定)。
- 新增 `src/main/resources/mixins.portablestorage.json`(或按 separateMixinSourceSet 约定)。

## 受影响 / 新增文件

- 新增 `ltd/mc233/compat/StorageItemSource.java`(或类似): 上面两个 API + 匹配/扣除逻辑。
- 新增 `ltd/mc233/mixin/MixinQuestItem.java`: 注入 `isCompleted`、`handleComplete`。
- 新增 `ltd/mc233/mixin/MixinContainerNPCTrader.java`: 注入 `canBuy`、`slotClick` 扣款处。
- 新增 `mixins.portablestorage.json` + (可选)`ltd/mc233/mixin/MixinPlugin.java`(条件加载)。
- 改 `gradle.properties`(开 mixin)。
- 复用: `StorageProvider`、`StorageDao`(不改或仅小增查询)。

## 风险与对策

| 风险 | 对策 |
|---|---|
| **CNPC 版本变化致方法签名对不上** | 绑定当前 cnpc-api.jar 版本; Mixin 用 `require = 1` 让注入失败即报错(而非静默失效); 文档记录目标方法签名 |
| 匹配规则与 CNPC 不一致 → 数得到扣不掉 | 严格镜像 `ignoreDamage/ignoreNBT`; 实现时读 `canBuy`/`isCompleted` 字节码核对 |
| 扣款非原子 → 白拿/吞币 | 先算后扣、失败回退(见"原子性"); 单元测试覆盖"背包+仓库刚好够/差一个"边界 |
| `slotClick` 扣款点难定位 | 实现第一步先反编译 `ContainerNPCTrader` 定位货币移除调用, 再决定 `@Inject`/`@Redirect` 方式 |
| Mixin 拖垮无 CNPC 环境 | 条件加载 + `@Pseudo required=false`; 无 CNPC 的启动路径纳入手动验证 |

## 测试策略

- **纯逻辑单测**(不启动 MC): 把"背包优先、仓库补差"的计算抽成不依赖 CNPC 的纯函数
  `plan(need, invHave, storeHave) -> (fromInv, fromStore, satisfied)`, 覆盖: 背包够 / 仓库补 / 合起来仍不够 / 刚好够。
- **仓库 API 单测**: 对临时 .db 验证 `countInStorage`/`extractFromStorage` 在 ignoreDamage/ignoreNBT 各组合下正确。
- **手动游戏内验证**:
  1. 物品任务: 物品只在仓库 → 能交; 背包+仓库各一半 → 能交; 合起来差一个 → 不能交。
  2. `leaveItems=true` 的任务交完仓库物品**不减**; `false` 则背包优先扣、仓库补差。
  3. 交易: 货币只在仓库 → 能买且从仓库扣; 背包有一部分 → 先扣背包再扣仓库。
  4. 买不起(合计不够)→ 不扣任何、不给货。
  5. **卸载 CNPC** → mod 正常加载、随身仓库功能不受影响。

## 实现阶段 (建议顺序)

1. **仓库 API + 纯逻辑单测**(不碰 CNPC/Mixin): `StorageItemSource` + `plan(...)` + 测试。
2. **开 Mixin 环境**: 改 gradle.properties, 建 mixin 包 + json + 条件加载, 空 Mixin 能加载。
3. **任务集成**: `MixinQuestItem` 注入 `isCompleted`/`handleComplete`, 游戏内验证。
4. **交易集成**: 反编译定位扣款点 → `MixinContainerNPCTrader` 注入 `canBuy`/`slotClick`, 游戏内验证。
