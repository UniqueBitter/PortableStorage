# CNPC 仓库集成 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 CustomNPCs 的物品任务与交易把随身仓库也算作玩家的物品来源(判定计入、扣除时背包优先仓库补差)。

**Architecture:** 纯算术(背包优先补差)抽成可单测的 `DeductPlan`;DAO 增加"按谓词计数/扣除"两个可单测方法;CNPC 胶水层 `StorageItemSource` 用 CNPC 自己的 `NoppesUtilPlayer.compareItems` 当匹配谓词,保证与 CNPC 零分歧;通过 Mixin 钩 `QuestItem`(任务)与中心工具 `NoppesUtilPlayer`(交易及其它 role)。

**Tech Stack:** Java 8, Minecraft 1.7.10, Forge 10.13.4.1614, RetroFuturaGradle(GTNH), JUnit 4, SpongeMixin(经 UniMixins), SQLite(既有)。

## Global Constraints

- 目标平台锁定 **MC 1.7.10 / Forge 10.13.4.1614 / Java 8**,构建 RetroFuturaGradle(GTNH convention)。
- **不 git 提交**:用户在 IDEA 自行提交。每个 Task 末尾是一个 review 检查点,把改动留在工作区,**不执行 `git commit`**。
- **CNPC 是软依赖**:未装 CustomNPCs 时游戏必须正常加载 —— 靠 `PsMixinPlugin` 类路径探测 + `@Pseudo` 守护,钩 `noppes.*` 的 mixin 在无 CNPC 时整体不应用。
- **匹配一律复用** `noppes.npcs.NoppesUtilPlayer.compareItems(...)`,**绝不用内部 `nbt_hash`** 与 CNPC 样板比对(见 spec 匹配语义)。
- **InvTweaks ASM 陷阱**:任何在服务端 tick 上下文首次被加载的类,必须加入 `PortableStorageMod.preloadTickClasses()` 预加载。本计划新增的 `ltd.mc233.core.DeductPlan` 与(仅在装了 CNPC 时)`ltd.mc233.compat.StorageItemSource` 都要预加载。
- **扣除顺序**:背包优先,仓库补差。
- **跑测**:`./gradlew test`;单个:`./gradlew test --tests "ltd.mc233.core.DeductPlanTest"`。

---

## 文件结构(先锁定分工)

- 新增 `src/main/java/ltd/mc233/core/DeductPlan.java` — 纯算术:给定需求/背包量/仓库量,算各出多少、是否满足。零依赖,可单测。
- 改 `src/main/java/ltd/mc233/db/StorageDao.java` — 增 `RowMatch` 谓词接口 + `countMatching` / `extractMatching`(按谓词跨行计数/扣除)。可单测。
- 新增 `src/main/java/ltd/mc233/compat/StorageItemSource.java` — CNPC 胶水:用 `compareItems` 构造谓词,提供背包/仓库计数与仓库扣除。依赖 `noppes.*`,只在装了 CNPC 时加载。
- 改 `src/main/java/ltd/mc233/PortableStorageMod.java` — 预加载新类(InvTweaks 陷阱)。
- 改 `gradle.properties` — 开启 mixin。
- 新增 `src/main/java/ltd/mc233/mixin/PsMixinPlugin.java` — mixin 条件加载插件(无 CNPC 则不应用)。
- 新增 `src/main/java/ltd/mc233/mixin/MixinQuestItem.java` — 任务:钩 `isCompleted` / `handleComplete`。
- 新增 `src/main/java/ltd/mc233/mixin/MixinNoppesUtilPlayer.java` — 交易:钩中心 `compareItems(player,...)` / `consumeItem(player,...)`。
- 测试:`src/test/java/ltd/mc233/core/DeductPlanTest.java`、`src/test/java/ltd/mc233/db/StorageDaoMatchTest.java`。

---

### Task 1: DeductPlan(背包优先、仓库补差 —— 纯算术)

**Files:**
- Create: `src/main/java/ltd/mc233/core/DeductPlan.java`
- Test: `src/test/java/ltd/mc233/core/DeductPlanTest.java`

**Interfaces:**
- Consumes: 无。
- Produces: `DeductPlan.Split plan(long need, long invHave, long storeHave)`;`Split` 含 `public final long fromInv, fromStorage; public final boolean satisfied;`。

- [ ] **Step 1: 写失败测试**

`src/test/java/ltd/mc233/core/DeductPlanTest.java`:
```java
package ltd.mc233.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class DeductPlanTest {

    @Test
    public void invEnoughUsesNoStorage() {
        DeductPlan.Split s = DeductPlan.plan(5, 10, 0);
        assertEquals(5, s.fromInv);
        assertEquals(0, s.fromStorage);
        assertTrue(s.satisfied);
    }

    @Test
    public void storageCoversRemainder() {
        DeductPlan.Split s = DeductPlan.plan(10, 4, 20);
        assertEquals(4, s.fromInv);
        assertEquals(6, s.fromStorage);
        assertTrue(s.satisfied);
    }

    @Test
    public void combinedStillNotEnough() {
        DeductPlan.Split s = DeductPlan.plan(10, 3, 4);
        assertEquals(3, s.fromInv);
        assertEquals(4, s.fromStorage);
        assertFalse(s.satisfied);
    }

    @Test
    public void exactlyEnoughAcrossBoth() {
        DeductPlan.Split s = DeductPlan.plan(10, 6, 4);
        assertEquals(6, s.fromInv);
        assertEquals(4, s.fromStorage);
        assertTrue(s.satisfied);
    }

    @Test
    public void zeroNeedIsSatisfied() {
        DeductPlan.Split s = DeductPlan.plan(0, 5, 5);
        assertEquals(0, s.fromInv);
        assertEquals(0, s.fromStorage);
        assertTrue(s.satisfied);
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew test --tests "ltd.mc233.core.DeductPlanTest"`
Expected: 编译失败 / `cannot find symbol DeductPlan`。

- [ ] **Step 3: 写最小实现**

`src/main/java/ltd/mc233/core/DeductPlan.java`:
```java
package ltd.mc233.core;

// 背包优先、仓库补差的纯算术。need=需求量, invHave=背包里有的, storeHave=仓库里有的。
// 仿 MagnetRouter 的 Split 风格, 零依赖, 便于单测。
public final class DeductPlan {

    private DeductPlan() {}

    public static final class Split {

        public final long fromInv, fromStorage;
        public final boolean satisfied; // 背包+仓库合起来是否够 need

        Split(long fromInv, long fromStorage, boolean satisfied) {
            this.fromInv = fromInv;
            this.fromStorage = fromStorage;
            this.satisfied = satisfied;
        }
    }

    public static Split plan(long need, long invHave, long storeHave) {
        if (need <= 0) return new Split(0, 0, true);
        long fromInv = Math.min(need, Math.max(0, invHave));
        long remain = need - fromInv;
        long fromStorage = Math.min(remain, Math.max(0, storeHave));
        boolean satisfied = (fromInv + fromStorage) >= need;
        return new Split(fromInv, fromStorage, satisfied);
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew test --tests "ltd.mc233.core.DeductPlanTest"`
Expected: PASS(5 个测试全绿)。

- [ ] **Step 5: 检查点** — 改动留在工作区,交用户在 IDEA review/提交。**不执行 git commit。**

---

### Task 2: StorageDao 按谓词计数/扣除

**Files:**
- Modify: `src/main/java/ltd/mc233/db/StorageDao.java`
- Test: `src/test/java/ltd/mc233/db/StorageDaoMatchTest.java`

**Interfaces:**
- Consumes: 既有 `StorageDao`(`upsert`、`extract`、`read(ResultSet)`、`db.getConnection()`)。
- Produces:
  - `interface StorageDao.RowMatch { boolean test(StoredItem it); }`
  - `long countMatching(String player, RowMatch m)` — 合计所有 `m.test`为真的行的 count。
  - `long extractMatching(String player, long need, RowMatch m)` — 从匹配行按 id 顺序累计扣除,最多 `need`,归零行删除;返回实际扣除量。

- [ ] **Step 1: 写失败测试**

`src/test/java/ltd/mc233/db/StorageDaoMatchTest.java`:
```java
package ltd.mc233.db;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ltd.mc233.core.StoredItem;

public class StorageDaoMatchTest {

    File f;
    StorageDb db;
    StorageDao dao;

    @Before
    public void setup() throws Exception {
        f = File.createTempFile("ps-match", ".db");
        f.delete();
        db = new StorageDb(f);
        dao = new StorageDao(db);
    }

    @After
    public void teardown() {
        db.close();
        f.delete();
    }

    // 假谓词: 按注册名判定(不依赖 MC/CNPC), 只为验证跨行聚合/扣减的算术。
    private static StorageDao.RowMatch itemIs(final String name) {
        return new StorageDao.RowMatch() {
            public boolean test(StoredItem it) {
                return it.getItem().equals(name);
            }
        };
    }

    @Test
    public void countMatchingSumsAcrossRows() {
        dao.upsert("p1", new StoredItem("minecraft:coin", 0, null, "-", 5, "币", ""));
        dao.upsert("p1", new StoredItem("minecraft:coin", 1, null, "b", 3, "币", ""));
        dao.upsert("p1", new StoredItem("minecraft:gem", 0, null, "-", 9, "宝石", ""));
        assertEquals(8, dao.countMatching("p1", itemIs("minecraft:coin")));
        assertEquals(9, dao.countMatching("p1", itemIs("minecraft:gem")));
        assertEquals(0, dao.countMatching("p1", itemIs("minecraft:nope")));
    }

    @Test
    public void extractMatchingPullsAcrossRowsUpToNeed() {
        dao.upsert("p1", new StoredItem("minecraft:coin", 0, null, "-", 5, "币", ""));
        dao.upsert("p1", new StoredItem("minecraft:coin", 1, null, "b", 5, "币", ""));
        assertEquals(7, dao.extractMatching("p1", 7, itemIs("minecraft:coin")));
        assertEquals(3, dao.countMatching("p1", itemIs("minecraft:coin")));
    }

    @Test
    public void extractMatchingCappedByAvailable() {
        dao.upsert("p1", new StoredItem("minecraft:coin", 0, null, "-", 4, "币", ""));
        assertEquals(4, dao.extractMatching("p1", 100, itemIs("minecraft:coin")));
        assertEquals(0, dao.countMatching("p1", itemIs("minecraft:coin")));
    }

    @Test
    public void extractMatchingZeroNeedNoop() {
        dao.upsert("p1", new StoredItem("minecraft:coin", 0, null, "-", 4, "币", ""));
        assertEquals(0, dao.extractMatching("p1", 0, itemIs("minecraft:coin")));
        assertEquals(4, dao.countMatching("p1", itemIs("minecraft:coin")));
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew test --tests "ltd.mc233.db.StorageDaoMatchTest"`
Expected: 编译失败 / `cannot find symbol RowMatch / countMatching`。

- [ ] **Step 3: 在 StorageDao 里加实现**

在 `StorageDao` 类体内(如放在 `keySet` 方法附近)新增:
```java
    // 行谓词: 判定某仓库行是否算作"要找的物品"。生产环境由 CNPC 的 compareItems 支撑(见 StorageItemSource),
    // 单测里用简单谓词验证聚合/扣减算术。
    public interface RowMatch {

        boolean test(StoredItem it);
    }

    // 合计该玩家所有 m.test 为真的行的 count。
    public long countMatching(String player, RowMatch m) {
        synchronized (db) {
            long sum = 0;
            try (java.sql.PreparedStatement ps = db.getConnection()
                .prepareStatement("SELECT item,meta,nbt,nbt_hash,count,name,lore FROM entries WHERE player=?")) {
                ps.setString(1, player);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        StoredItem it = read(rs);
                        if (m.test(it)) sum += it.getCount();
                    }
                }
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
            return sum;
        }
    }

    // 从匹配行(按 id 升序)累计扣除, 最多 need; 归零行由 extract 删除。返回实际扣除量。
    public long extractMatching(String player, long need, RowMatch m) {
        if (need <= 0) return 0;
        synchronized (db) {
            java.util.List<Long> ids = new java.util.ArrayList<Long>();
            try (java.sql.PreparedStatement ps = db.getConnection()
                .prepareStatement("SELECT id,item,meta,nbt,nbt_hash,count,name,lore FROM entries WHERE player=? ORDER BY id")) {
                ps.setString(1, player);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (m.test(read(rs))) ids.add(rs.getLong("id"));
                    }
                }
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
            long got = 0;
            for (long id : ids) {
                if (got >= need) break;
                got += extract(player, id, need - got); // extract 内部 synchronized(db) 可重入
            }
            return got;
        }
    }
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew test --tests "ltd.mc233.db.StorageDaoMatchTest"`
Expected: PASS(4 个测试全绿)。再跑一次全量 `./gradlew test` 确认没弄坏既有测试。

- [ ] **Step 5: 检查点** — 留工作区交用户 review。**不 git commit。**

---

### Task 3: StorageItemSource(CNPC 胶水:真 compareItems 当谓词)+ 预加载

**Files:**
- Create: `src/main/java/ltd/mc233/compat/StorageItemSource.java`
- Modify: `src/main/java/ltd/mc233/PortableStorageMod.java`(`preloadTickClasses`)

**Interfaces:**
- Consumes: `DeductPlan`(Task 1)、`StorageDao.RowMatch/countMatching/extractMatching`(Task 2)、`StorageProvider.dao()/keyFor(p)`、`ItemStackCodec.decode`、`noppes.npcs.NoppesUtilPlayer.compareItems`。
- Produces(供 Task 5/6 的 Mixin 调用):
  - `long countInInventory(EntityPlayer p, ItemStack required, boolean ignoreDamage, boolean ignoreNBT)`
  - `long countInStorage(EntityPlayer p, ItemStack required, boolean ignoreDamage, boolean ignoreNBT)`
  - `long countAvailable(EntityPlayer p, ItemStack required, boolean ignoreDamage, boolean ignoreNBT)`
  - `long extractFromStorage(EntityPlayer p, ItemStack required, long need, boolean ignoreDamage, boolean ignoreNBT)`

- [ ] **Step 1: 建 StorageItemSource**

> 说明:本类无 JUnit 覆盖(依赖真实 ItemStack/CNPC),正确性由 Task 5/6 的游戏内验证保证。逻辑保持极薄:计数/扣除的算术已在 Task 1/2 测过。

`src/main/java/ltd/mc233/compat/StorageItemSource.java`:
```java
package ltd.mc233.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import ltd.mc233.StorageProvider;
import ltd.mc233.core.StoredItem;
import ltd.mc233.db.StorageDao;
import ltd.mc233.item.ItemStackCodec;
import noppes.npcs.NoppesUtilPlayer;

/**
 * CNPC 集成的胶水层: 把"随身仓库"当作玩家的额外物品来源。
 * 匹配一律复用 CNPC 自己的 NoppesUtilPlayer.compareItems, 与 CNPC 判定零分歧。
 * 只在装了 CNPC 时被加载(由 mixin/预加载守护)。
 */
public final class StorageItemSource {

    private StorageItemSource() {}

    // 用 CNPC 的 compareItems 把"仓库某行是否算作 required"包成一个谓词。
    private static StorageDao.RowMatch matcher(final ItemStack required, final boolean ignoreDamage,
        final boolean ignoreNBT) {
        return new StorageDao.RowMatch() {
            public boolean test(StoredItem it) {
                ItemStack s = ItemStackCodec.decode(it, 1); // 还原成真实 ItemStack; 对应 mod 已移除则为 null
                return s != null && NoppesUtilPlayer.compareItems(required, s, ignoreDamage, ignoreNBT);
            }
        };
    }

    // 背包里有几个符合 required(用 2 参 compareItems 逐格比, 镜像 CNPC 匹配)。
    public static long countInInventory(EntityPlayer p, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        if (p == null || required == null) return 0;
        long sum = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (ItemStack s : inv) {
            if (s != null && NoppesUtilPlayer.compareItems(required, s, ignoreDamage, ignoreNBT)) sum += s.stackSize;
        }
        return sum;
    }

    // 仓库里有几个符合 required。
    public static long countInStorage(EntityPlayer p, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        if (p == null || required == null) return 0;
        return StorageProvider.dao()
            .countMatching(StorageProvider.keyFor(p), matcher(required, ignoreDamage, ignoreNBT));
    }

    // 背包 + 仓库合计。
    public static long countAvailable(EntityPlayer p, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        return countInInventory(p, required, ignoreDamage, ignoreNBT)
            + countInStorage(p, required, ignoreDamage, ignoreNBT);
    }

    // 从仓库最多扣 need 个符合 required 的, 返回实际扣除量。
    public static long extractFromStorage(EntityPlayer p, ItemStack required, long need, boolean ignoreDamage,
        boolean ignoreNBT) {
        if (p == null || required == null || need <= 0) return 0;
        return StorageProvider.dao()
            .extractMatching(StorageProvider.keyFor(p), need, matcher(required, ignoreDamage, ignoreNBT));
    }
}
```

- [ ] **Step 2: 预加载(避开 InvTweaks ASM 陷阱)**

在 `PortableStorageMod.preloadTickClasses()` 里, `cls` 数组内加入 `DeductPlan`(无条件, 无 CNPC 依赖):
```java
        String[] cls = { "ltd.mc233.core.MagnetRouter", "ltd.mc233.core.MagnetRouter$Split",
            "ltd.mc233.core.StoredItem", "ltd.mc233.core.PinInUtil", "ltd.mc233.item.ItemStackCodec",
            "ltd.mc233.db.StorageDb", "ltd.mc233.db.StorageDao", "ltd.mc233.StorageProvider",
            "ltd.mc233.StorageService", "ltd.mc233.core.DeductPlan",
            // 拼音库(PinIn): 必须在 init 阶段预加载, 否则在服务端 tick 内首次懒加载会触发 InvTweaks ASM 变换器 NPE → 崩溃。
            "me.towdium.pinin.PinIn" };
```
并在该方法末尾(`PinInUtil.preload();` 之后)追加:仅装了 CNPC 才预加载会引用 `noppes.*` 的胶水类:
```java
        // StorageItemSource 引用 noppes.*: 只有装了 CNPC 才预加载, 否则会 NoClassDefFoundError。
        if (ltd.mc233.compat.CnpcCompat.LOADED) {
            try {
                Class.forName("ltd.mc233.compat.StorageItemSource", true, getClass().getClassLoader());
            } catch (Throwable t) {
                LOG.warn("预加载 StorageItemSource 失败", t);
            }
        }
```

- [ ] **Step 3: 编译确认**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL(cnpc-api.jar 为 compileOnly, `noppes.*` 编译期可见)。

- [ ] **Step 4: 检查点** — 留工作区交用户 review。**不 git commit。**

---

### Task 4: 开启 Mixin 环境 + 条件加载插件

**Files:**
- Modify: `gradle.properties`
- Create: `src/main/java/ltd/mc233/mixin/PsMixinPlugin.java`

**Interfaces:**
- Produces: 可用的 mixin 环境;`mixinsPackage = ltd.mc233.mixin`;`PsMixinPlugin` 在无 CNPC 时令本 mod 所有 mixin 不应用。

- [ ] **Step 1: 改 gradle.properties**

设置(定位到对应行改值):
```
usesMixins = true
mixinsPackage = ltd.mc233.mixin
mixinPlugin = ltd.mc233.mixin.PsMixinPlugin
```
其余 mixin 相关项保持默认(`separateMixinSourceSet` 留空 → mixin 与 main 同源集)。`usesMixins=true` 会自动引入 UniMixins 依赖并生成 `mixins.portablestorage.json`。

- [ ] **Step 2: 建条件加载插件**

`src/main/java/ltd/mc233/mixin/PsMixinPlugin.java`:
```java
package ltd.mc233.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
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
        try {
            Class.forName("noppes.npcs.NoppesUtilPlayer", false, PsMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
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
```

- [ ] **Step 3: 构建确认 mixin 环境就绪**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL;产物 jar 里含 `mixins.portablestorage.json`(此时 mixins 列表为空也可)。若 `mixinsPackage` 指向的包不存在会构建失败 —— `PsMixinPlugin` 所在的 `ltd.mc233.mixin` 包已满足"包存在"。

- [ ] **Step 4(游戏内):无 CNPC 冒烟**

临时在无 CNPC 的环境(或确认 `runClient` 的 mods 里 CNPC 缺席的情形)`./gradlew runClient`,确认游戏正常进主菜单、无 mixin 报错。
Expected: 正常加载(`PsMixinPlugin.shouldApplyMixin` 返回 false, 不应用任何 mixin)。

- [ ] **Step 5: 检查点** — 留工作区交用户 review。**不 git commit。**

---

### Task 5: 任务集成(MixinQuestItem)

**Files:**
- Create: `src/main/java/ltd/mc233/mixin/MixinQuestItem.java`

**Interfaces:**
- Consumes: `StorageItemSource.countAvailable / extractFromStorage`(Task 3);`noppes.npcs.quests.QuestItem` 的公开字段 `items`(NpcMiscInventory)、`leaveItems`、`ignoreDamage`、`ignoreNBT`。
- Produces: 无(行为注入)。

> 目标方法(javap 确认):`boolean isCompleted(EntityPlayer)`、`void handleComplete(EntityPlayer)`。二者均为 CNPC 自有方法,**方法名不经 MCP 混淆**,`@Inject` 直接用原名。

- [ ] **Step 1: 建 MixinQuestItem**

`src/main/java/ltd/mc233/mixin/MixinQuestItem.java`:
```java
package ltd.mc233.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ltd.mc233.compat.StorageItemSource;
import noppes.npcs.NpcMiscInventory;
import noppes.npcs.quests.QuestItem;

@Pseudo
@Mixin(QuestItem.class)
public abstract class MixinQuestItem {

    @Shadow
    public NpcMiscInventory items;
    @Shadow
    public boolean leaveItems;
    @Shadow
    public boolean ignoreDamage;
    @Shadow
    public boolean ignoreNBT;

    // CNPC 判"任务是否完成"后, 若判为未完成, 再把仓库算进去重新判一次。
    @Inject(method = "isCompleted", at = @At("RETURN"), cancellable = true, remap = false)
    private void ps$isCompleted(EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return; // CNPC 已认为完成, 不插手
        for (int i = 0; i < items.getSizeInventory(); i++) {
            ItemStack req = items.getStackInSlot(i);
            if (req == null) continue;
            long have = StorageItemSource.countAvailable(player, req, ignoreDamage, ignoreNBT);
            if (have < req.stackSize) return; // 有任一要求物合起来仍不够 → 维持"未完成"
        }
        cir.setReturnValue(true); // 全部要求物 背包+仓库 都够 → 判为完成
    }

    // 交任务扣物品后, 把背包没扣够的部分从仓库补扣(尊重 leaveItems)。
    @Inject(method = "handleComplete", at = @At("RETURN"), remap = false)
    private void ps$handleComplete(EntityPlayer player, CallbackInfo ci) {
        if (leaveItems) return; // 不消耗物品的任务, 仓库也不动
        for (int i = 0; i < items.getSizeInventory(); i++) {
            ItemStack req = items.getStackInSlot(i);
            if (req == null) continue;
            // handleComplete 已从背包扣走了能扣的部分; 差额从仓库补扣。
            long inInv = StorageItemSource.countInInventory(player, req, ignoreDamage, ignoreNBT);
            long remain = req.stackSize - Math.min(req.stackSize, inInv);
            if (remain > 0) StorageItemSource.extractFromStorage(player, req, remain, ignoreDamage, ignoreNBT);
        }
    }
}
```

> 实现注意:`NpcMiscInventory` 的取物方法名(`getSizeInventory`/`getStackInSlot` 为 MCP 名,SRG 为 `func_70302_i_`/`func_70301_a`)。因 `NpcMiscInventory` 是 CNPC 类且这些是 `IInventory` 接口方法,**编译期用 MCP 名即可**;若运行期 `@Shadow`/调用报找不到,改用对应 SRG 名(实现时以 `./gradlew runClient` 报错为准调整)。`handleComplete` 里"背包已扣、量取自扣后快照"依赖 `@At("RETURN")` 时机 —— 若实测顺序不符,改用 HEAD 捕获扣前量 + ThreadLocal 传递(见 Task 6 的 consumeItem 模式)。

- [ ] **Step 2: 把 MixinQuestItem 登记进 mixin 配置**

`usesMixins=true` 时 RFG 自动扫描 `mixinsPackage` 生成配置,`MixinQuestItem` 会被收入。构建后确认 `build/.../mixins.portablestorage.json` 的 `mixins` 列表含 `MixinQuestItem`(若未自动收入,手动在生成配置的 `mixins` 数组补上类名)。

- [ ] **Step 3: 构建**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4(游戏内验证):任务认仓库**

`./gradlew runClient`(装有 CNPC),用 spec 手动清单第 1、2 条验证:
1. 建一个"收集 N 个某物品"的 item 任务;把该物品**只放仓库**(背包没有)→ 交任务应可完成。
2. 背包放一半、仓库放一半 → 可完成。合起来差一个 → **不可**完成。
3. `leaveItems=false` 的任务交完:背包优先扣光,再从仓库补扣差额;`leaveItems=true` 的任务交完仓库**不减**。
Expected: 均符合;日志无 mixin 注入失败(`@Inject` 未命中会在启动期报错,因目标是 CNPC 自有方法,通常稳定)。

- [ ] **Step 5: 检查点** — 留工作区交用户 review。**不 git commit。**

---

### Task 6: 交易集成(MixinNoppesUtilPlayer)

**Files:**
- Create: `src/main/java/ltd/mc233/mixin/MixinNoppesUtilPlayer.java`

**Interfaces:**
- Consumes: `StorageItemSource.countAvailable / countInInventory / extractFromStorage`(Task 3)。
- Produces: 无(行为注入)。

> 目标(javap 确认,`ContainerNPCTrader.canBuy` 内部调用):中心工具
> `boolean compareItems(EntityPlayer, ItemStack, boolean, boolean)`(判"背包够不够")与
> `void consumeItem(EntityPlayer, ItemStack, boolean, boolean)`(从背包扣)。钩这两个 → 交易(及其它用它们的 role)一并覆盖。CNPC 自有静态方法,名不混淆。

- [ ] **Step 1: 建 MixinNoppesUtilPlayer**

`src/main/java/ltd/mc233/mixin/MixinNoppesUtilPlayer.java`:
```java
package ltd.mc233.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ltd.mc233.compat.StorageItemSource;
import noppes.npcs.NoppesUtilPlayer;

@Pseudo
@Mixin(NoppesUtilPlayer.class)
public abstract class MixinNoppesUtilPlayer {

    // 每次消耗前记录"背包扣完还差多少", HEAD 与 RETURN 之间靠 ThreadLocal 传递。
    private static final ThreadLocal<Long> PS_REMAIN = new ThreadLocal<Long>();

    // "背包够不够"判定: CNPC 判 false 时, 把仓库算进去再判一次。
    @Inject(
        method = "compareItems(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;ZZ)Z",
        at = @At("RETURN"),
        cancellable = true,
        remap = false)
    private static void ps$compareItems(EntityPlayer player, ItemStack item, boolean ignoreDamage, boolean ignoreNBT,
        CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || item == null) return; // 背包已够, 不插手
        long have = StorageItemSource.countAvailable(player, item, ignoreDamage, ignoreNBT);
        if (have >= item.stackSize) cir.setReturnValue(true);
    }

    // 消耗前: 算出背包扣完还差多少(= 需求 - min(需求, 背包持有))。
    @Inject(
        method = "consumeItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;ZZ)V",
        at = @At("HEAD"),
        remap = false)
    private static void ps$consumeHead(EntityPlayer player, ItemStack item, boolean ignoreDamage, boolean ignoreNBT,
        CallbackInfo ci) {
        if (item == null) {
            PS_REMAIN.set(0L);
            return;
        }
        long inInv = StorageItemSource.countInInventory(player, item, ignoreDamage, ignoreNBT);
        PS_REMAIN.set(item.stackSize - Math.min(item.stackSize, inInv));
    }

    // 消耗后: CNPC 已扣背包部分, 差额从仓库补扣。
    @Inject(
        method = "consumeItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;ZZ)V",
        at = @At("RETURN"),
        remap = false)
    private static void ps$consumeReturn(EntityPlayer player, ItemStack item, boolean ignoreDamage, boolean ignoreNBT,
        CallbackInfo ci) {
        Long remain = PS_REMAIN.get();
        PS_REMAIN.remove();
        if (remain != null && remain > 0 && item != null) {
            StorageItemSource.extractFromStorage(player, item, remain, ignoreDamage, ignoreNBT);
        }
    }
}
```

- [ ] **Step 2: 确认收入 mixin 配置**

同 Task 5 Step 2:构建后确认 `mixins` 列表含 `MixinNoppesUtilPlayer`。

- [ ] **Step 3: 反编译核对扣款语义(动手前先看)**

Run: `javap -c -p -classpath libs/cnpc-api.jar noppes.npcs.NoppesUtilPlayer | less`
核对两点:① `compareItems(player,...)` 确为"背包持有 ≥ item.stackSize 才返回 true";② `consumeItem(player,...)` 确为"从背包最多移除 item.stackSize"(不足则移除现有量)。若与假设不符,调整 HEAD/RETURN 的差额算法。

- [ ] **Step 4: 构建**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5(游戏内验证):交易认仓库**

`./gradlew runClient`(装有 CNPC),用 spec 手动清单第 3、4 条:
1. 配一个 NPC 交易(货币 → 商品);货币**只放仓库** → 能买,且成交后从仓库扣款。
2. 背包有一部分货币 → 先扣背包、差额扣仓库。
3. 背包+仓库合计**不够** → 买不成、不扣任何、不给货(`canBuy` 应为 false)。
Expected: 均符合。

- [ ] **Step 6(游戏内验证):无 CNPC 回归**

在无 CNPC 环境启动一次,确认 mod 正常加载、随身仓库本体功能不受影响(`PsMixinPlugin` 令交易/任务 mixin 全不应用)。

- [ ] **Step 7: 检查点** — 留工作区交用户 review。**不 git commit。**

---

## 自查(计划 vs spec)

- **spec 覆盖**:计数/扣除算术 → Task 1/2;compareItems 匹配 → Task 3;背包优先补差 → Task 1 + Task 5/6;软依赖守护 → Task 4(PsMixinPlugin)+ Task 3(预加载守护);构建开 mixin → Task 4;任务注入 → Task 5;交易注入 → Task 6;原子性 → Task 6 的 HEAD/RETURN 差额 + Task 5;测试策略 → Task 1/2 单测 + Task 5/6 游戏内清单。全部有对应 Task。
- **占位符**:无 TBD;标注为"实现时以 runClient 报错为准调整"的两处(NpcMiscInventory 方法名 SRG、consumeItem 语义)是 mixin 目标核对的固有步骤,已给出明确的判定与回退方案,非占位。
- **类型一致**:`RowMatch`/`countMatching`/`extractMatching`(Task 2)在 Task 3 调用一致;`countAvailable`/`countInInventory`/`extractFromStorage`(Task 3)在 Task 5/6 调用一致;`DeductPlan.plan` 的 `Split` 字段名一致。
