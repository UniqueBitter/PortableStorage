# 随身仓库 2.0 — 容量制 + 标签分区 + 渐进解锁

状态: 已通过设计评审 (2026-06-30)。本文件是实现依据。

## 背景与目标

当前随身仓库是"开局无限"的 AE 式存储。地图主创希望背包**逐步解锁**、有成长感。
本次把模型改为:**一个有限容量、可扩容的仓库;用标签把内容分区("一个房间隔成小房间");容量全局共享。**

非目标 (v1 不做): 标签内手动拖动重排;跨存档共享;标签也随进度解锁 (已决定:只锁容量,标签自由建)。

## 核心模型

- **容量 (capacity)**: 每位玩家一个整数,表示能存放多少**种**物品。每种物品占 1 格,数量无限 (像 AE 存储元件)。
- **"已用" (used)**: 该玩家仓库表中的**行数** (每行 = 一种已堆叠的物品)。`used <= capacity`。
- **标签 (tab / 小房间)**: 把物品分区的整理工具。每件物品**归属于恰好一个标签**;默认进「未分类」。
  - 「全部」: 虚拟视图 (tabId = -1),汇总所有标签、按当前排序显示。**保留原有自动排序总览。**
  - 「未分类」: 真实标签 (tabId = 0),系统固定,不可删。新存入、以及被删标签里的物品都归到这。
  - 自定义标签: tabId = 1..N,玩家自由建/删/改名。
- 容量**全局共享**: 所有标签里的种类总数算同一个 `used`;标签本身不占容量、不设单独上限。

## 容量与解锁

### 起始容量与"老数据不锁死" (迁移)
- 默认起始容量 `DEFAULT_CAPACITY = 0` (config 可调): 新玩家从零开始,靠任务/扩容券逐步解锁。
- 首次为某玩家初始化容量时: `capacity = max(DEFAULT_CAPACITY, 当前已用种类数)`。保证现有存档的老数据一格都不丢、不被锁 (按现有种类数起步,处于"已满"状态,需扩容才能放新种类)。
- 初始化标记用单独的 `psCapSet` 布尔 (因为 0 是合法容量,不能用 0 当"未设")。

### 满了的行为
- 放入**新种类**且 `used >= capacity` → 拒绝,提示「仓库已满 (X/Y),无法放入新种类。需扩容。」
- 往**已有种类**里加数量 → 永远允许 (数量无限)。
- 超容的历史数据 → 只读保留,绝不删;只是在降到容量以下前不能再放新种类。

### 解锁方式 (两者都要)
1. **指令** (接任务 / CustomNPCs 对话奖励):
   - `/storage cap get [玩家]` — 查询 (默认自己)。
   - `/storage cap add <玩家> <n>` — 加 n 容量 (权限 2)。
   - `/storage cap set <玩家> <n>` — 设为 n (权限 2)。
2. **扩容道具**「背包扩容券」 `portablestorage:cap_voucher`:
   - **每个 +5 容量** (单一档位)。
   - 右键 (服务端 `onItemRightClick`) 消耗 1 个 → 给玩家 +5 容量 → 提示新容量。

## 标签操作 (自由,不锁)

- **新建**: 点右侧标签列底部的 `+` → 新建标签 (默认名「标签N」) 并切过去。
- **删除**: **Shift + 右键**点某个自定义标签 → 删除它,其中物品全部移回「未分类」 (不丢)。「全部」「未分类」不可删。
- **改名**: 右键自定义标签 → 进入行内文本框改名 (回车确认)。
- **归类**: 光标拿着某物品,**拖到某个标签按钮上松手** → 该种物品归到那个标签 (`UPDATE tab`)。
- **切换**: 左键点标签 → 切换当前视图;搜索/排序在「全部」视图照常,在具体标签视图也可用 (只在该标签内搜/排)。

## UI 布局

```
左侧按钮            仓库格子            右侧标签列
 [收纳]        ┌──────────────┐       [全部]   ← 不可删
 [排序]        │ 随身仓库 [搜索]│       [材料]
 [方向]        │ ▦▦▦▦▦▦      │       [装备]
 [搜索]        │ ▦▦▦▦▦▦      │       [药水]
 [补货]        │ ▦▦▦▦▦▦  滚动│       [ + ]    ← 新建
              └──────────────┘
   容量计数「已用 X / 总 Y」显示在标题行或底部。
```
- 右侧标签列是面板外缘的一竖排小按钮 (和左侧按钮列对称),复用 IconButton 风格。
- 当前激活标签高亮。

## 数据模型与底层

### 数据库
- 仓库表新增列 `tab INTEGER NOT NULL DEFAULT 0`。启动时 `ALTER TABLE ... ADD COLUMN tab` (若不存在);用 `PRAGMA table_info` 判断幂等。
- 查询: 「全部」忽略 `tab`;具体标签 `WHERE tab = ?`。
- 归类: `UPDATE storage SET tab=? WHERE player=? AND hash=?`。
- 删标签: `UPDATE storage SET tab=0 WHERE player=? AND tab=?`。
- `used` = `SELECT COUNT(*) FROM storage WHERE player=?`。

### 玩家存档 (NBT PlayerPersisted)
- `psCapacity` (int): 当前容量。
- `psTabs` (NBTTagList): 每项 {id:int, name:string}。顺序即列表顺序。tabId 自增,删除不复用以避免悬挂归属 (悬挂的会在删除时归 0)。

### 网络包
- **PageRequest** 扩展: 增加 `tabId` 字段 (-1=全部, 0=未分类, k=自定义)。
- **PageResultPacket** 扩展: 增加 `capacityUsed`、`capacityTotal`、以及标签列表 (id+name) 用于客户端渲染右侧标签。
- **新增 TabActionPacket** (客户端→服务端): 动作枚举 {CREATE, DELETE, RENAME, ASSIGN},带参数 (tabId / name / itemHash)。服务端处理后刷新当前页。

## 受影响的文件 (预估)
- `db/StorageDao`, `db/StorageDb`: 加 `tab` 列、迁移、按 tab 查询、归类、删标签、count(used)。
- `StorageService`: 容量校验 (拦截新种类)、标签 CRUD、归类;sendPage 带容量+标签;扩容接口。
- `StorageProvider` / 玩家 NBT 读写: psCapacity、psTabs。
- `net/PageResultPacket`、`net/PageRequestPacket` (或等价)、新增 `net/TabActionPacket`、`NetworkHandler` 注册。
- `client/GuiPortableStorage`: 右侧标签列渲染与交互 (切换/新建/Shift右键删/改名/拖拽归类)、容量计数显示。
- `client/IconButton` 或新 `TabButton`: 标签按钮。
- 新增 `item/ItemCapVoucher` + 注册 + 语言 + (复用 icons 或新贴图)。
- 新增 `StorageCapCommand` (`/storage cap ...`) + 注册。
- `inventory/ContainerPortableStorage`、`restock`、`magnet`: 存入路径统一走容量校验 (新种类被拒时不吞物品、给反馈)。
- `Config`: DEFAULT_CAPACITY、扩容券档位数值。

## 实现阶段 (建议顺序,每阶段可单独构建+进游戏验证)
1. **容量后端**: psCapacity + 迁移起始值 + used 统计 + 存入新种类校验 (先不动 UI,满了用聊天提示)。
2. **容量 UI + 解锁**: GUI 显示 X/Y;`/storage cap` 指令;扩容券物品。
3. **标签后端**: `tab` 列迁移 + psTabs + 按 tab 查询 + CRUD + 归类 + 包扩展。
4. **标签 UI**: 右侧标签列 + 切换/新建/删/改名/拖拽归类。

## 风险
- 容量校验必须覆盖所有存入入口 (Shift存入、光标存入、磁铁、补货),且新种类被拒时**不能吞掉物品**。
- 新增的 item / 任何会在 tick 内首次加载的类,必须按 [[invtweaks-asm-preload]] 预加载,否则 InvTweaks ASM 崩溃。
- DB 迁移要幂等,且对已有 `.db` 平滑加列。
```
