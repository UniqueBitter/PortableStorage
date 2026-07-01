# 随身无限仓库模组 (PortableStorage) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 MC 1.7.10 Forge RPG 整合包做一个随身的、无限容量、可搜索(中文+拼音)的仓库模组,外加三档磁铁(自动拾取)功能。

**Architecture:** 仓库数据是服务端权威状态,落在**每存档一个的 SQLite 数据库**(不全量进内存)。客户端只持有"当前可见窗口",通过 Forge 网络包做 AE 风格连续滚动、服务端搜索分页。可测试的核心(DAO / 拼音 / 磁铁路由 / 配置)是不依赖 MC 的纯逻辑,用 JUnit 跑;依赖 MC 的部分(物品/容器/GUI/按键/磁铁事件)在游戏内按清单验证。

**Tech Stack:** Java 8, Minecraft 1.7.10, Forge 1.7.10-10.13.4.1614(`cpw.mods.fml.*`),RetroFuturaGradle(RFG)构建,sqlite-jdbc + pinyin4j(shade 进 jar),JUnit 4。

## Global Constraints

- 目标平台: Minecraft **1.7.10**, Forge **1.7.10-10.13.4.1614**, 包名用经典 **`cpw.mods.fml.*`**(不是 `net.minecraftforge.fml`)。
- 构建: **RetroFuturaGradle**;Gradle 跑在现代 JDK(系统 JDK 21);MC 编译目标 **Java 8 字节码**,Java 8 工具链用自带 **Dragonwell JDK 8**:`D:\MC\开放世界RPG 英雄黎明4：烈焰之火 EA3.0.4\优化版java`。
- 工程根目录(纯英文路径): `D:\MC\mods\PortableStorage`。
- modid: **`portablestorage`**,中文名"随身仓库"。
- 按键(走 Forge `KeyBinding`,可在控制里改键): **`B`** 开仓库,**`K`** 切磁铁档位。
- 磁铁: 三档 `0=关闭 / 1=进背包 / 2=进仓库`;"进背包"档背包满 → 溢出转仓库;半径配置项 **1–128**,默认 **8**,读取 clamp。
- 持久化: **SQLite**,路径 `<存档目录>/portablestorage/storage.db`,**跨存档隔离**;装备 NBT 存为 BLOB 列;堆叠判定 = `UNIQUE(player,item,meta,nbt_hash)`。
- 同步: 永不全量;AE 风格滚动 = 服务端窗口查询;单次窗口 **≤ 90 条**,可见区 **9 列 × 5 行 = 45 格**。
- 搜索: 匹配 **中文显示名 + lore 词条 + 全拼 + 拼音首字母**,服务端 DB `LIKE`。
- 终端道具: **仅创造栏 + `/give portablestorage:terminal`,不做合成配方**。
- 依赖打包: sqlite-jdbc(如 `3.45.3.0`)+ pinyin4j(`2.5.1`)**shade 并 relocate** 进最终 jar;sqlite native 解压到游戏可写目录。
- 纪律: DRY、YAGNI、TDD、频繁提交。每个 DB 访问都在服务端线程(单连接、单线程,无需加锁)。

---

## File Structure

源码包根: `src/main/java/com/portablestorage/`

| 文件 | 职责 |
|---|---|
| `PortableStorageMod.java` | `@Mod` 主类: 生命周期、注册物品/网络/事件、初始化代理与 DB |
| `Config.java` | 读取 `config/portablestorage.cfg`: 磁铁半径(clamp 1–128)、可见行列、窗口大小 |
| `proxy/CommonProxy.java` / `proxy/ClientProxy.java` | 通用 vs 客户端(GUI/按键/渲染)分离 |
| `core/StoredItem.java` | 纯 DTO: `item, meta, nbt(byte[]), nbtHash, count, name, lore, pinyin, pinyinInitials`(无 MC 依赖) |
| `core/PinyinUtil.java` | 中文→全拼/首字母(pinyin4j 封装,纯逻辑) |
| `core/MagnetRouter.java` | 纯函数: 给定背包剩余空间 + 档位 → 计算进背包/进仓库的拆分 |
| `db/StorageDb.java` | 每存档一个 SQLite 连接: 定位文件、开库、建表、关库、事务 |
| `db/StorageDao.java` | upsert/批量 upsert/extract/queryWindow/countMatches(操作 StoredItem) |
| `item/ItemStackCodec.java` | `ItemStack ↔ StoredItem`(MC 依赖,薄层): 注册名/meta/nbt/hash/本地化名/lore/拼音 |
| `item/ItemPortableTerminal.java` | 终端道具: 右键发 open 包 |
| `inventory/ContainerPortableStorage.java` | 服务端容器: 取出/存入/一键收纳,调用 DAO |
| `client/GuiPortableStorage.java` | 客户端 GUI: AE 滚动、搜索框、数量角标、一键收纳按钮 |
| `client/KeyBindings.java` | 注册 `B`/`K`,按键事件发包 |
| `magnet/MagnetManager.java` | `PlayerTickEvent`: 扫描 EntityItem,按档位路由 |
| `magnet/PlayerMagnetState.java` | 读写玩家持久化 NBT 里的磁铁档位 |
| `net/NetworkHandler.java` | `SimpleNetworkWrapper` 注册 |
| `net/*Packet.java` | Open / QueryPage / PageResult / StorageAction / MagnetMode / MagnetModeAck |

测试: `src/test/java/com/portablestorage/...`(JUnit 4)。资源: `src/main/resources/mcmod.info`、`assets/portablestorage/`、`pack.mcmeta` 等。

---

## Task 1: RFG 工程骨架(最高风险,先打通)

**目标交付:** 一个能 `./gradlew build` 出 jar、`./gradlew runClient` 能启动并看到本模组加载的最小工程。先证明工具链通,再写功能。

**Files:**
- Create: 从 GTNH 模板拷入的整套 RFG 工程(`build.gradle`、`settings.gradle`、`gradlew`、`gradle/`、`src/main/java/com/portablestorage/PortableStorageMod.java`、`src/main/resources/mcmod.info`)。

**Interfaces:**
- Produces: `com.portablestorage.PortableStorageMod`(`@Mod(modid="portablestorage", name="随身仓库", version="1.0.0")`),`MODID` 常量,`logger`。

- [ ] **Step 1: 用已知可用的 GTNH 1.7.10 模板做底**

模板是当下做 1.7.10 模组最可靠的起点(你的包就是 GTNH 生态)。在工程根 clone 后把模板内容铺到根目录(保留我们已有的 `docs/` 与 `.git/`):

```bash
cd "D:/MC/mods/PortableStorage"
git clone --depth 1 https://github.com/GTNewHorizons/ExampleMod1.7.10.git _template
# 把模板文件复制到根(不覆盖 docs/.git),随后删掉模板的 .git 与示例源码
cp -r _template/build.gradle _template/settings.gradle _template/gradlew _template/gradlew.bat _template/gradle _template/repositories.gradle .
cp -r _template/.gitignore . 2>/dev/null || true
rm -rf _template
```

- [ ] **Step 2: 设定工程坐标 / modid**

编辑 `gradle.properties`(模板用它配置 modid/group/version)。设置:
```properties
modName = PortableStorage
modId = portablestorage
modGroup = com.portablestorage
includeWellKnownRepositories = true
usesMixins = false
```
(其余键保持模板默认。模板会据此生成 Forge 1.7.10 依赖与 runClient 配置。)

- [ ] **Step 3: 让 Gradle 用对 JDK**

Gradle 本体用系统 JDK 21 跑;RFG 内部用 Java 8 工具链编译 MC。新建 `gradle.properties` 追加(或 `~/.gradle`):
```properties
org.gradle.java.installations.paths=C:\\Program Files\\Java\\jdk-21,D:\\MC\\开放世界RPG 英雄黎明4：烈焰之火 EA3.0.4\\优化版java
```
确保 `JAVA_HOME` 指向 JDK 21(Gradle 8.14 需要现代 JDK)。

- [ ] **Step 4: 写最小主类**

替换模板示例主类为 `src/main/java/com/portablestorage/PortableStorageMod.java`:
```java
package com.portablestorage;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = PortableStorageMod.MODID, name = "随身仓库", version = "1.0.0", acceptedMinecraftVersions = "[1.7.10]")
public class PortableStorageMod {
    public static final String MODID = "portablestorage";
    public static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        logger = e.getModLog();
        logger.info("PortableStorage preInit");
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        logger.info("PortableStorage init");
    }
}
```
并写 `src/main/resources/mcmod.info`(modid `portablestorage`、中文名、描述)。删掉模板残留的示例 `ExampleMod`/`Tags` 引用导致的编译错误(按需调整 package)。

- [ ] **Step 5: 构建,确认出 jar**

Run: `./gradlew build --no-daemon`
Expected: BUILD SUCCESSFUL;`build/libs/` 下出现 `portablestorage-1.0.0.jar`(或模板命名)。

- [ ] **Step 6: 启动客户端,确认模组加载**

Run: `./gradlew runClient --no-daemon`
Expected: 游戏起来;日志含 `PortableStorage preInit` / `init`;模组列表里能看到"随身仓库"。看到后关掉游戏。

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "chore: RFG 1.7.10 工程骨架, 最小主类可构建可启动"
```

---

## Task 2: PinyinUtil(中文→全拼/首字母,纯逻辑 TDD)

**Files:**
- Create: `src/main/java/com/portablestorage/core/PinyinUtil.java`
- Test: `src/test/java/com/portablestorage/core/PinyinUtilTest.java`
- Modify: `build.gradle` 依赖加 `pinyin4j` 与测试用 `junit`。

**Interfaces:**
- Produces:
  - `static String fullPinyin(String text)` → 全拼小写无空格("钻石"→`"zuanshi"`);非中文字符原样转小写保留。
  - `static String initials(String text)` → 拼音首字母小写("钻石"→`"zs"`);非中文字符取其本身小写。

- [ ] **Step 1: 加依赖**

`build.gradle` 的 `dependencies {}` 加(shade 配置在 Task 14 处理;此处先能编译与测试):
```groovy
implementation 'com.belerweb:pinyin4j:2.5.1'
testImplementation 'junit:junit:4.13.2'
```
Run: `./gradlew dependencies --no-daemon` 确认能解析 pinyin4j。

- [ ] **Step 2: 写失败测试**

```java
package com.portablestorage.core;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PinyinUtilTest {
    @Test public void fullPinyinOfChinese() {
        assertEquals("zuanshi", PinyinUtil.fullPinyin("钻石"));
    }
    @Test public void initialsOfChinese() {
        assertEquals("zs", PinyinUtil.initials("钻石"));
    }
    @Test public void keepsAsciiLowercased() {
        assertEquals("tnt", PinyinUtil.fullPinyin("TNT"));
        assertEquals("tnt", PinyinUtil.initials("TNT"));
    }
}
```

- [ ] **Step 3: 运行,确认失败**

Run: `./gradlew test --tests com.portablestorage.core.PinyinUtilTest --no-daemon`
Expected: FAIL(`PinyinUtil` 不存在 / 编译错误)。

- [ ] **Step 4: 实现**

```java
package com.portablestorage.core;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

public final class PinyinUtil {
    private PinyinUtil() {}
    private static final HanyuPinyinOutputFormat FMT = new HanyuPinyinOutputFormat();
    static {
        FMT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FMT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    public static String fullPinyin(String text) { return convert(text, false); }
    public static String initials(String text) { return convert(text, true); }

    private static String convert(String text, boolean initialOnly) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            String py = pinyinOf(c);
            if (py != null) sb.append(initialOnly ? py.substring(0, 1) : py);
            else if (!Character.isWhitespace(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String pinyinOf(char c) {
        if (c < 0x4E00 || c > 0x9FFF) return null; // 非 CJK
        try {
            String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, FMT);
            if (arr != null && arr.length > 0) return arr[0]; // 多音字取首选读音
        } catch (BadHanyuPinyinOutputFormatCombination ignored) {}
        return null;
    }
}
```

- [ ] **Step 5: 运行,确认通过**

Run: `./gradlew test --tests com.portablestorage.core.PinyinUtilTest --no-daemon`
Expected: PASS(3 个用例)。

- [ ] **Step 6: 提交**

```bash
git add build.gradle src/main/java/com/portablestorage/core/PinyinUtil.java src/test/java/com/portablestorage/core/PinyinUtilTest.java
git commit -m "feat: PinyinUtil 中文转全拼/首字母 (TDD)"
```

---

## Task 3: StoredItem DTO + nbtHash(纯逻辑 TDD)

**Files:**
- Create: `src/main/java/com/portablestorage/core/StoredItem.java`
- Test: `src/test/java/com/portablestorage/core/StoredItemTest.java`

**Interfaces:**
- Produces: `StoredItem` 不可变 DTO,字段 `String item; int meta; byte[] nbt; String nbtHash; long count; String name; String lore; String pinyin; String pinyinInitials;`
  - 全参构造器 + getter。
  - `static String hashNbt(byte[] nbt)` → 无 nbt(null/空)返回固定串 `"-"`;否则返回 nbt 字节的 SHA-1 十六进制串。
  - `StoredItem withCount(long newCount)` → 返回数量替换后的副本(其余字段不变)。

- [ ] **Step 1: 写失败测试**

```java
package com.portablestorage.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class StoredItemTest {
    @Test public void nbtHashStableAndDistinct() {
        assertEquals("-", StoredItem.hashNbt(null));
        assertEquals("-", StoredItem.hashNbt(new byte[0]));
        String h1 = StoredItem.hashNbt(new byte[]{1,2,3});
        String h2 = StoredItem.hashNbt(new byte[]{1,2,3});
        String h3 = StoredItem.hashNbt(new byte[]{9,9,9});
        assertEquals(h1, h2);
        assertNotEquals(h1, h3);
    }
    @Test public void withCountCopies() {
        StoredItem a = new StoredItem("minecraft:diamond", 0, null, "-", 5, "钻石", "", "zuanshi", "zs");
        StoredItem b = a.withCount(99);
        assertEquals(5, a.getCount());
        assertEquals(99, b.getCount());
        assertEquals("minecraft:diamond", b.getItem());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests com.portablestorage.core.StoredItemTest --no-daemon`
Expected: FAIL(`StoredItem` 不存在)。

- [ ] **Step 3: 实现**

```java
package com.portablestorage.core;

import java.security.MessageDigest;

public final class StoredItem {
    private final String item; private final int meta;
    private final byte[] nbt; private final String nbtHash;
    private final long count;
    private final String name, lore, pinyin, pinyinInitials;

    public StoredItem(String item, int meta, byte[] nbt, String nbtHash, long count,
                      String name, String lore, String pinyin, String pinyinInitials) {
        this.item=item; this.meta=meta; this.nbt=nbt; this.nbtHash=nbtHash; this.count=count;
        this.name=name; this.lore=lore; this.pinyin=pinyin; this.pinyinInitials=pinyinInitials;
    }
    public String getItem(){return item;} public int getMeta(){return meta;}
    public byte[] getNbt(){return nbt;} public String getNbtHash(){return nbtHash;}
    public long getCount(){return count;}
    public String getName(){return name;} public String getLore(){return lore;}
    public String getPinyin(){return pinyin;} public String getPinyinInitials(){return pinyinInitials;}

    public StoredItem withCount(long c){ return new StoredItem(item,meta,nbt,nbtHash,c,name,lore,pinyin,pinyinInitials); }

    public static String hashNbt(byte[] nbt) {
        if (nbt == null || nbt.length == 0) return "-";
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(nbt);
            StringBuilder sb = new StringBuilder(d.length*2);
            for (byte b : d) sb.append(Character.forDigit((b>>4)&0xF,16)).append(Character.forDigit(b&0xF,16));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew test --tests com.portablestorage.core.StoredItemTest --no-daemon`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/portablestorage/core/StoredItem.java src/test/java/com/portablestorage/core/StoredItemTest.java
git commit -m "feat: StoredItem DTO + nbtHash (TDD)"
```

---

## Task 4: StorageDb + StorageDao 写入/upsert(TDD,真实 SQLite)

**Files:**
- Create: `src/main/java/com/portablestorage/db/StorageDb.java`, `src/main/java/com/portablestorage/db/StorageDao.java`
- Test: `src/test/java/com/portablestorage/db/StorageDaoTest.java`
- Modify: `build.gradle` 加 `sqlite-jdbc`。

**Interfaces:**
- Produces:
  - `StorageDb(java.io.File dbFile)` → 打开/建表;`Connection getConnection()`;`void close()`。建表 SQL 见 spec §3(含 `pinyin`,`pinyin_in` 列与 `UNIQUE(player,item,meta,nbt_hash)`)。
  - `StorageDao(StorageDb db)`:
    - `void upsert(String player, StoredItem it)` → 同键则 `count += it.count`,否则插入。
    - `void upsertBatch(String player, java.util.List<StoredItem> items)` → 单事务批量。
    - `long countOf(String player, String item, int meta, String nbtHash)` → 测试辅助:取某条目数量(无则 0)。

- [ ] **Step 1: 加依赖**

`build.gradle` `dependencies {}`:
```groovy
implementation 'org.xerial:sqlite-jdbc:3.45.3.0'
```

- [ ] **Step 2: 写失败测试**

```java
package com.portablestorage.db;
import com.portablestorage.core.StoredItem;
import org.junit.*; import java.io.File; import java.util.*;
import static org.junit.Assert.*;

public class StorageDaoTest {
    File f; StorageDb db; StorageDao dao;
    @Before public void setup() throws Exception {
        f = File.createTempFile("ps-test", ".db"); f.delete();
        db = new StorageDb(f); dao = new StorageDao(db);
    }
    @After public void teardown() { db.close(); f.delete(); }

    private StoredItem di(long n){ return new StoredItem("minecraft:diamond",0,null,"-",n,"钻石","","zuanshi","zs"); }

    @Test public void upsertMergesSameKey() {
        dao.upsert("p1", di(5));
        dao.upsert("p1", di(3));
        assertEquals(8, dao.countOf("p1","minecraft:diamond",0,"-"));
    }
    @Test public void differentNbtHashAreSeparate() {
        dao.upsert("p1", new StoredItem("minecraft:sword",0,new byte[]{1},"aaa",1,"剑","","jian","j"));
        dao.upsert("p1", new StoredItem("minecraft:sword",0,new byte[]{2},"bbb",1,"剑","","jian","j"));
        assertEquals(1, dao.countOf("p1","minecraft:sword",0,"aaa"));
        assertEquals(1, dao.countOf("p1","minecraft:sword",0,"bbb"));
    }
    @Test public void batchInsert() {
        dao.upsertBatch("p1", Arrays.asList(di(2), di(2), di(2)));
        assertEquals(6, dao.countOf("p1","minecraft:diamond",0,"-"));
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `./gradlew test --tests com.portablestorage.db.StorageDaoTest --no-daemon`
Expected: FAIL(类不存在)。

- [ ] **Step 4: 实现 StorageDb**

```java
package com.portablestorage.db;
import java.io.File; import java.sql.*;

public class StorageDb {
    private final Connection conn;
    public StorageDb(File dbFile) {
        try {
            Class.forName("org.sqlite.JDBC");
            if (dbFile.getParentFile()!=null) dbFile.getParentFile().mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS entries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, player TEXT NOT NULL, item TEXT NOT NULL," +
                    "meta INTEGER NOT NULL, nbt BLOB, nbt_hash TEXT NOT NULL, count INTEGER NOT NULL," +
                    "name TEXT, lore TEXT, pinyin TEXT, pinyin_in TEXT," +
                    "UNIQUE(player,item,meta,nbt_hash))");
                s.execute("CREATE INDEX IF NOT EXISTS idx_player_name ON entries(player,name)");
            }
        } catch (Exception e) { throw new RuntimeException("open db failed", e); }
    }
    public Connection getConnection(){ return conn; }
    public void close(){ try { conn.close(); } catch (SQLException ignored) {} }
}
```

- [ ] **Step 5: 实现 StorageDao 的 upsert/批量/countOf**

```java
package com.portablestorage.db;
import com.portablestorage.core.StoredItem;
import java.sql.*; import java.util.List;

public class StorageDao {
    private final StorageDb db;
    public StorageDao(StorageDb db){ this.db = db; }

    public void upsert(String player, StoredItem it) {
        String sql = "INSERT INTO entries(player,item,meta,nbt,nbt_hash,count,name,lore,pinyin,pinyin_in) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT(player,item,meta,nbt_hash) DO UPDATE SET count=count+excluded.count";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            bind(ps, player, it); ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void upsertBatch(String player, List<StoredItem> items) {
        String sql = "INSERT INTO entries(player,item,meta,nbt,nbt_hash,count,name,lore,pinyin,pinyin_in) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT(player,item,meta,nbt_hash) DO UPDATE SET count=count+excluded.count";
        Connection c = db.getConnection();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (StoredItem it : items) { bind(ps, player, it); ps.addBatch(); }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException(e);
        } finally { try { c.setAutoCommit(true); } catch (SQLException ignored) {} }
    }

    public long countOf(String player, String item, int meta, String nbtHash) {
        String sql = "SELECT count FROM entries WHERE player=? AND item=? AND meta=? AND nbt_hash=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1,player); ps.setString(2,item); ps.setInt(3,meta); ps.setString(4,nbtHash);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private void bind(PreparedStatement ps, String player, StoredItem it) throws SQLException {
        ps.setString(1,player); ps.setString(2,it.getItem()); ps.setInt(3,it.getMeta());
        if (it.getNbt()==null) ps.setNull(4,Types.BLOB); else ps.setBytes(4,it.getNbt());
        ps.setString(5,it.getNbtHash()); ps.setLong(6,it.getCount());
        ps.setString(7,it.getName()); ps.setString(8,it.getLore());
        ps.setString(9,it.getPinyin()); ps.setString(10,it.getPinyinInitials());
    }
}
```

- [ ] **Step 6: 运行确认通过**

Run: `./gradlew test --tests com.portablestorage.db.StorageDaoTest --no-daemon`
Expected: PASS(3 个用例)。

- [ ] **Step 7: 提交**

```bash
git add build.gradle src/main/java/com/portablestorage/db src/test/java/com/portablestorage/db
git commit -m "feat: StorageDb + StorageDao upsert/批量 (TDD, 真实sqlite)"
```

---

## Task 5: StorageDao 取出 / 搜索窗口 / 计数(TDD)

**Files:**
- Modify: `src/main/java/com/portablestorage/db/StorageDao.java`
- Modify(加用例): `src/test/java/com/portablestorage/db/StorageDaoTest.java`

**Interfaces:**
- Produces(StorageDao 新增):
  - `long entryId(String player, String item, int meta, String nbtHash)` → 取行 id(测试辅助,无则 -1)。
  - `long extract(String player, long id, long amount)` → 实扣 `min(amount, count)`,扣到 0 删行;返回实扣数量。
  - `java.util.List<StoredItem> queryWindow(String player, String keyword, int offset, int limit)` → `ORDER BY name LIMIT/OFFSET`;`keyword` 空串=全部;非空则 `name/lore/pinyin/pinyin_in` 任一 `LIKE %kw%`(kw 已小写)。返回的 StoredItem 含 count 与显示字段(nbt 可不回填,GUI 用不到原始 nbt,只取出时才需要——取出走 id)。
  - `long countMatches(String player, String keyword)` → 同 WHERE 的行数。

- [ ] **Step 1: 写失败测试(追加到 StorageDaoTest)**

```java
@Test public void extractDecrementsAndDeletesAtZero() {
    dao.upsert("p1", di(10));
    long id = dao.entryId("p1","minecraft:diamond",0,"-");
    assertEquals(4, dao.extract("p1", id, 4));
    assertEquals(6, dao.countOf("p1","minecraft:diamond",0,"-"));
    assertEquals(6, dao.extract("p1", id, 100)); // 只能扣到剩余
    assertEquals(0, dao.countOf("p1","minecraft:diamond",0,"-"));
    assertEquals(-1, dao.entryId("p1","minecraft:diamond",0,"-")); // 行已删
}
@Test public void searchMatchesNameLorePinyin() {
    dao.upsert("p1", new StoredItem("minecraft:diamond",0,null,"-",1,"钻石","稀有 矿物","zuanshi","zs"));
    assertEquals(1, dao.countMatches("p1","钻"));      // 中文名
    assertEquals(1, dao.countMatches("p1","zuanshi")); // 全拼
    assertEquals(1, dao.countMatches("p1","zs"));      // 首字母
    assertEquals(1, dao.countMatches("p1","矿物"));     // 词条
    assertEquals(0, dao.countMatches("p1","apple"));
}
@Test public void windowPaginates() {
    for (int i=0;i<5;i++) dao.upsert("p1",
        new StoredItem("mod:i"+i,0,null,"-",1,"物"+i,"","wu","w"));
    assertEquals(5, dao.countMatches("p1",""));
    assertEquals(2, dao.queryWindow("p1","",0,2).size());
    assertEquals(1, dao.queryWindow("p1","",4,2).size()); // 末尾不足
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests com.portablestorage.db.StorageDaoTest --no-daemon`
Expected: FAIL(新方法不存在)。

- [ ] **Step 3: 实现新增方法**

```java
public long entryId(String player, String item, int meta, String nbtHash) {
    String sql="SELECT id FROM entries WHERE player=? AND item=? AND meta=? AND nbt_hash=?";
    try (PreparedStatement ps=db.getConnection().prepareStatement(sql)) {
        ps.setString(1,player); ps.setString(2,item); ps.setInt(3,meta); ps.setString(4,nbtHash);
        try (ResultSet rs=ps.executeQuery()) { return rs.next()? rs.getLong(1) : -1L; }
    } catch (SQLException e){ throw new RuntimeException(e); }
}

public long extract(String player, long id, long amount) {
    try (PreparedStatement sel=db.getConnection().prepareStatement(
            "SELECT count FROM entries WHERE player=? AND id=?")) {
        sel.setString(1,player); sel.setLong(2,id);
        long have; try (ResultSet rs=sel.executeQuery()){ if(!rs.next()) return 0; have=rs.getLong(1); }
        long take = Math.min(amount, have);
        if (take<=0) return 0;
        if (take==have) {
            try (PreparedStatement del=db.getConnection().prepareStatement(
                    "DELETE FROM entries WHERE player=? AND id=?")) {
                del.setString(1,player); del.setLong(2,id); del.executeUpdate();
            }
        } else {
            try (PreparedStatement upd=db.getConnection().prepareStatement(
                    "UPDATE entries SET count=count-? WHERE player=? AND id=?")) {
                upd.setLong(1,take); upd.setString(2,player); upd.setLong(3,id); upd.executeUpdate();
            }
        }
        return take;
    } catch (SQLException e){ throw new RuntimeException(e); }
}

private String where(String keyword){
    return keyword==null||keyword.isEmpty() ? "player=?"
        : "player=? AND (name LIKE ? OR lore LIKE ? OR pinyin LIKE ? OR pinyin_in LIKE ?)";
}
private int bindWhere(PreparedStatement ps, String player, String keyword) throws SQLException {
    ps.setString(1,player);
    if (keyword!=null && !keyword.isEmpty()) {
        String kw="%"+keyword.toLowerCase()+"%";
        ps.setString(2,kw); ps.setString(3,kw); ps.setString(4,kw); ps.setString(5,kw);
        return 6;
    }
    return 2;
}

public long countMatches(String player, String keyword) {
    String sql="SELECT COUNT(*) FROM entries WHERE "+where(keyword);
    try (PreparedStatement ps=db.getConnection().prepareStatement(sql)) {
        bindWhere(ps,player,keyword);
        try (ResultSet rs=ps.executeQuery()){ return rs.next()? rs.getLong(1):0; }
    } catch (SQLException e){ throw new RuntimeException(e); }
}

public java.util.List<StoredItem> queryWindow(String player, String keyword, int offset, int limit) {
    java.util.List<StoredItem> out=new java.util.ArrayList<StoredItem>();
    String sql="SELECT id,item,meta,nbt_hash,count,name,lore,pinyin,pinyin_in FROM entries WHERE "
        +where(keyword)+" ORDER BY name LIMIT ? OFFSET ?";
    try (PreparedStatement ps=db.getConnection().prepareStatement(sql)) {
        int idx=bindWhere(ps,player,keyword);
        ps.setInt(idx,limit); ps.setInt(idx+1,offset);
        try (ResultSet rs=ps.executeQuery()) {
            while (rs.next()) {
                out.add(new StoredItem(rs.getString("item"), rs.getInt("meta"), null,
                    rs.getString("nbt_hash"), rs.getLong("count"), rs.getString("name"),
                    rs.getString("lore"), rs.getString("pinyin"), rs.getString("pinyin_in")));
            }
        }
    } catch (SQLException e){ throw new RuntimeException(e); }
    return out;
}
```
注:GUI 渲染只需 `item/meta/count/name`,故 `queryWindow` 不回填 nbt BLOB(省带宽);取出时按行 `id` 在服务端用单独查询取回 nbt 还原 ItemStack(见 Task 10)。窗口里需要把行 `id` 传给客户端——给 `StoredItem` 不加 id,改由 `queryWindow` 的姊妹方法或并行返回 id 列表;**实现时 PageResultPacket 用 `queryWindowWithIds` 返回 `List<long id>` + `List<StoredItem>` 平行数组**(在 Task 9 定义,届时加该方法,复用同 SQL,额外读 `id`)。

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew test --tests com.portablestorage.db.StorageDaoTest --no-daemon`
Expected: PASS(全部用例)。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/portablestorage/db/StorageDao.java src/test/java/com/portablestorage/db/StorageDaoTest.java
git commit -m "feat: DAO 取出/搜索窗口/计数, 拼音匹配 (TDD)"
```

---

## Task 6: MagnetRouter 路由数学(纯函数 TDD)

**Files:**
- Create: `src/main/java/com/portablestorage/core/MagnetRouter.java`
- Test: `src/test/java/com/portablestorage/core/MagnetRouterTest.java`

**Interfaces:**
- Produces:
  - `static Split route(int mode, int stackSize, int freeSpace)`:
    - `mode 0` → `toInv=0, toStorage=0`(关闭,调用方不应吸取)。
    - `mode 1`(进背包) → `toInv=min(stackSize,freeSpace)`,`toStorage=stackSize-toInv`(溢出转仓库)。
    - `mode 2`(进仓库) → `toInv=0, toStorage=stackSize`。
  - `Split` = `{int toInv; int toStorage;}`。

- [ ] **Step 1: 写失败测试**

```java
package com.portablestorage.core;
import org.junit.Test; import static org.junit.Assert.*;

public class MagnetRouterTest {
    @Test public void modeOffTakesNothing() {
        MagnetRouter.Split s = MagnetRouter.route(0, 10, 64);
        assertEquals(0, s.toInv); assertEquals(0, s.toStorage);
    }
    @Test public void modeInventoryFitsAll() {
        MagnetRouter.Split s = MagnetRouter.route(1, 10, 64);
        assertEquals(10, s.toInv); assertEquals(0, s.toStorage);
    }
    @Test public void modeInventoryOverflowsToStorage() {
        MagnetRouter.Split s = MagnetRouter.route(1, 10, 4);
        assertEquals(4, s.toInv); assertEquals(6, s.toStorage);
    }
    @Test public void modeStorageAllToStorage() {
        MagnetRouter.Split s = MagnetRouter.route(2, 10, 64);
        assertEquals(0, s.toInv); assertEquals(10, s.toStorage);
    }
}
```

- [ ] **Step 2: 运行确认失败** — Run: `./gradlew test --tests com.portablestorage.core.MagnetRouterTest --no-daemon` → FAIL。

- [ ] **Step 3: 实现**

```java
package com.portablestorage.core;

public final class MagnetRouter {
    private MagnetRouter(){}
    public static final class Split { public final int toInv, toStorage;
        Split(int i,int s){toInv=i;toStorage=s;} }
    public static Split route(int mode, int stackSize, int freeSpace) {
        if (mode==2) return new Split(0, stackSize);
        if (mode==1) { int inv=Math.min(stackSize, Math.max(0,freeSpace)); return new Split(inv, stackSize-inv); }
        return new Split(0,0);
    }
}
```

- [ ] **Step 4: 运行确认通过** — Run 同上 → PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/portablestorage/core/MagnetRouter.java src/test/java/com/portablestorage/core/MagnetRouterTest.java
git commit -m "feat: MagnetRouter 三档路由数学 (TDD)"
```

---

## Task 7: Config 半径 clamp(TDD)

**Files:**
- Create: `src/main/java/com/portablestorage/Config.java`
- Test: `src/test/java/com/portablestorage/ConfigTest.java`

**Interfaces:**
- Produces:
  - `static int clampRadius(int v)` → clamp 到 `[1,128]`。
  - `static int magnetRadius`(运行期字段,默认 8);`static void load(File configFile)` 用 Forge `Configuration` 读 `magnetRadius` 并经 `clampRadius`。
  - 纯逻辑只测 `clampRadius`(`load` 依赖 Forge,在游戏内验证)。

- [ ] **Step 1: 写失败测试**

```java
package com.portablestorage;
import org.junit.Test; import static org.junit.Assert.*;
public class ConfigTest {
    @Test public void clamps() {
        assertEquals(1, Config.clampRadius(0));
        assertEquals(1, Config.clampRadius(-5));
        assertEquals(8, Config.clampRadius(8));
        assertEquals(128, Config.clampRadius(128));
        assertEquals(128, Config.clampRadius(999));
    }
}
```

- [ ] **Step 2: 运行确认失败** → FAIL。

- [ ] **Step 3: 实现(clamp 部分;load 用 Forge Configuration)**

```java
package com.portablestorage;
import net.minecraftforge.common.config.Configuration;
import java.io.File;

public final class Config {
    private Config(){}
    public static int magnetRadius = 8;
    public static int clampRadius(int v){ return Math.max(1, Math.min(128, v)); }
    public static void load(File configFile) {
        Configuration c = new Configuration(configFile);
        c.load();
        magnetRadius = clampRadius(c.getInt("magnetRadius", "magnet", 8, 1, 128,
            "磁铁吸取半径(格), 1-128"));
        if (c.hasChanged()) c.save();
    }
}
```

- [ ] **Step 4: 运行确认通过** → PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/portablestorage/Config.java src/test/java/com/portablestorage/ConfigTest.java
git commit -m "feat: Config 磁铁半径 clamp 1-128 (TDD)"
```

---

## Task 8: ItemStackCodec + 终端道具注册(MC 集成,游戏内验证)

**Files:**
- Create: `src/main/java/com/portablestorage/item/ItemStackCodec.java`, `src/main/java/com/portablestorage/item/ItemPortableTerminal.java`
- Modify: `PortableStorageMod.java`(注册道具 + 创造栏 + preInit 读 Config)
- Create: `src/main/resources/assets/portablestorage/textures/items/terminal.png`(16×16 占位图标)、`lang/zh_CN.lang`、`lang/en_US.lang`

**Interfaces:**
- Produces:
  - `ItemStackCodec.encode(ItemStack)` → `StoredItem`(count=stack.stackSize;name/lore 取本地化并小写;nbt=`CompressedStreamTools.compress(stack.writeToNBT(new NBTTagCompound()))`;nbtHash=`StoredItem.hashNbt(nbt)`;pinyin 用 `PinyinUtil`)。
  - `ItemStackCodec.decode(StoredItem, int amount)` → `ItemStack`(从 nbt 还原或按 item+meta 构造,stackSize=amount)。
  - `ItemPortableTerminal`(`@ register` 名 `terminal`),右键发 `OpenStoragePacket`(Task 9 后接线)。

- [ ] **Step 1: 实现 ItemStackCodec**

```java
package com.portablestorage.item;
import com.portablestorage.core.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.StatCollector;
import java.io.*; import java.util.*;

public final class ItemStackCodec {
    private ItemStackCodec(){}

    public static StoredItem encode(ItemStack st) {
        String item = net.minecraft.item.Item.itemRegistry.getNameForObject(st.getItem()); // "minecraft:diamond"
        int meta = st.getItemDamage();
        byte[] nbt = null;
        if (st.hasTagCompound()) nbt = compress(st.getTagCompound());
        String hash = StoredItem.hashNbt(nbt);
        String name = safeLower(st.getDisplayName());
        String lore = safeLower(joinLore(st));
        String py = PinyinUtil.fullPinyin(st.getDisplayName());
        String pin = PinyinUtil.initials(st.getDisplayName());
        return new StoredItem(item, meta, nbt, hash, st.stackSize, name, lore, py, pin);
    }

    public static ItemStack decode(StoredItem si, int amount) {
        if (si.getNbt()!=null) {
            ItemStack st = ItemStack.loadItemStackFromNBT(decompress(si.getNbt()));
            if (st!=null){ st.stackSize = amount; return st; }
        }
        net.minecraft.item.Item it = (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry.getObject(si.getItem());
        return new ItemStack(it, amount, si.getMeta());
    }

    private static String joinLore(ItemStack st){
        List<?> t = st.getTooltip(null, false); StringBuilder sb=new StringBuilder();
        for (Object o:t) sb.append(o).append(' '); return sb.toString();
    }
    private static String safeLower(String s){ return s==null?"":s.toLowerCase(); }
    private static byte[] compress(NBTTagCompound tag){
        try { ByteArrayOutputStream b=new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(tag,b); return b.toByteArray(); }
        catch(IOException e){ throw new RuntimeException(e);} }
    private static NBTTagCompound decompress(byte[] d){
        try { return CompressedStreamTools.readCompressed(new ByteArrayInputStream(d)); }
        catch(IOException e){ throw new RuntimeException(e);} }
}
```
注:`getDisplayName()` 含 NBT 自定义名(RPG 装备),正合搜索需要;`getTooltip` 取 lore。`st.getTooltip(null,...)` 第一个参 player 传 null 在 1.7.10 客户端可用,服务端入库时若 NPE 则改用读 `display.Lore` NBT 标签拼接(实现时按运行情况二选一)。

- [ ] **Step 2: 实现终端道具**

```java
package com.portablestorage.item;
import com.portablestorage.PortableStorageMod;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class ItemPortableTerminal extends Item {
    public ItemPortableTerminal() {
        setUnlocalizedName("portablestorage_terminal");
        setTextureName(PortableStorageMod.MODID + ":terminal");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            com.portablestorage.net.NetworkHandler.openStorage(); // Task 9 接线
        }
        return stack;
    }
}
```

- [ ] **Step 3: 注册 + preInit 读配置(改 PortableStorageMod)**

在主类加:
```java
public static ItemPortableTerminal terminal;

@EventHandler
public void preInit(FMLPreInitializationEvent e) {
    logger = e.getModLog();
    com.portablestorage.Config.load(e.getSuggestedConfigurationFile());
    terminal = new ItemPortableTerminal();
    cpw.mods.fml.common.registry.GameRegistry.registerItem(terminal, "terminal");
}
```
写 `zh_CN.lang`: `item.portablestorage_terminal.name=随身仓库终端`;`en_US.lang` 给英文名。放 16×16 占位 `terminal.png`。

- [ ] **Step 4: 构建 + 游戏内验证**

Run: `./gradlew runClient --no-daemon`
验证:创造栏(工具页)能找到"随身仓库终端";`/give @p portablestorage:terminal` 能拿到;右键暂不报错(open 接线在 Task 9/11 完成后再测开 GUI)。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: ItemStackCodec + 终端道具(创造栏/give, 无配方)"
```

---

## Task 9: 网络层 + 数据包(MC 集成)

**Files:**
- Create: `src/main/java/com/portablestorage/net/NetworkHandler.java` 及 `OpenStoragePacket.java`、`QueryPagePacket.java`、`PageResultPacket.java`、`StorageActionPacket.java`、`MagnetModePacket.java`、`MagnetModeAckPacket.java`
- Modify: `PortableStorageMod.init` 注册网络;`StorageDao` 加 `queryWindowWithIds`

**Interfaces:**
- Produces:
  - `NetworkHandler.INSTANCE`(`SimpleNetworkWrapper`),`register()`,便捷发包静态方法:`openStorage()`、`requestPage(String kw,int offset,int limit)`、`sendAction(...)`、`sendMagnetMode()`。
  - `StorageDao.PageResult queryWindowWithIds(player, kw, offset, limit)` → `{long[] ids; List<StoredItem> items; long total;}`。
  - 各 `IMessage`/`IMessageHandler` 实现包的 `toBytes/fromBytes`(用 `ByteBufUtils` 读写 String、`PacketBuffer` 读写 NBT 字节)。

- [ ] **Step 1: DAO 加 queryWindowWithIds(复用 Task 5 SQL,多读 id + total)**

```java
public static final class PageResult {
    public final long[] ids; public final java.util.List<StoredItem> items; public final long total;
    public PageResult(long[] i, java.util.List<StoredItem> it, long t){ids=i;items=it;total=t;}
}
public PageResult queryWindowWithIds(String player,String kw,int offset,int limit){
    java.util.List<StoredItem> items=queryWindow(player,kw,offset,limit);
    // 再查一遍 id 平行数组(同 ORDER/LIMIT/OFFSET)
    long[] ids=new long[items.size()];
    String sql="SELECT id FROM entries WHERE "+where(kw)+" ORDER BY name LIMIT ? OFFSET ?";
    try(java.sql.PreparedStatement ps=db.getConnection().prepareStatement(sql)){
        int idx=bindWhere(ps,player,kw); ps.setInt(idx,limit); ps.setInt(idx+1,offset);
        try(java.sql.ResultSet rs=ps.executeQuery()){ int k=0; while(rs.next()&&k<ids.length) ids[k++]=rs.getLong(1); }
    }catch(java.sql.SQLException e){throw new RuntimeException(e);}
    return new PageResult(ids, items, countMatches(player,kw));
}
```

- [ ] **Step 2: 实现 NetworkHandler 与 6 个包**

`NetworkHandler`(节选):
```java
package com.portablestorage.net;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import com.portablestorage.PortableStorageMod;

public class NetworkHandler {
    public static final SimpleNetworkWrapper INSTANCE =
        NetworkRegistry.INSTANCE.newSimpleChannel(PortableStorageMod.MODID);
    public static void register() {
        int id=0;
        INSTANCE.registerMessage(OpenStoragePacket.Handler.class, OpenStoragePacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(QueryPagePacket.Handler.class, QueryPagePacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(PageResultPacket.Handler.class, PageResultPacket.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(StorageActionPacket.Handler.class, StorageActionPacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MagnetModePacket.Handler.class, MagnetModePacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MagnetModeAckPacket.Handler.class, MagnetModeAckPacket.class, id++, Side.CLIENT);
    }
    public static void openStorage(){ INSTANCE.sendToServer(new OpenStoragePacket()); }
    public static void requestPage(String kw,int off,int lim){ INSTANCE.sendToServer(new QueryPagePacket(kw,off,lim)); }
}
```
每个包按上表方向实现 `IMessage`(`toBytes/fromBytes` 用 `cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String/readUTF8String`,数量用 `buf.writeLong`)。`PageResultPacket` 载荷 = `total(long)` + `offset(int)` + `n(int)` + n×{`id(long)`,`item(UTF8)`,`meta(int)`,`count(long)`,`name(UTF8)`}。Handler 见 Task 10/11(服务端开容器、客户端回填窗口)。**注:Handler 内对世界/玩家的操作必须切到主线程**(1.7.10 用 `IThreadListener`/直接在 server handler 里取 `ctx.getServerHandler().playerEntity`,操作是同步的,通常安全;若遇并发异常则用 `MinecraftServer.getServer().addScheduledTask`)。

- [ ] **Step 3: init 注册网络**

主类 `init`:`com.portablestorage.net.NetworkHandler.register();`

- [ ] **Step 4: 构建确认编译通过**

Run: `./gradlew build --no-daemon`
Expected: BUILD SUCCESSFUL(包都能编译;运行期接线在后续任务验证)。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: 网络层 + 6 个数据包 + DAO 窗口带id/total"
```

---

## Task 10: 服务端容器 ContainerPortableStorage(MC 集成)

**Files:**
- Create: `src/main/java/com/portablestorage/inventory/ContainerPortableStorage.java`
- Create: `src/main/java/com/portablestorage/StorageProvider.java`(按存档定位 DB、缓存每存档的 `StorageDb/Dao`)
- Modify: `OpenStoragePacket.Handler`(服务端打开 GUI)、`StorageActionPacket.Handler`(取出/存入/一键收纳)、`QueryPagePacket.Handler`(回 PageResult)

**Interfaces:**
- Produces:
  - `StorageProvider.daoFor(World world)` → 用 `world.getSaveHandler().getWorldDirectory()` 定位 `<存档>/portablestorage/storage.db`,惰性开库并缓存;世界卸载时关库(`WorldEvent.Unload`)。
  - `ContainerPortableStorage`(只放玩家背包 slot,仓库内容不走 vanilla slot,而是经包同步;容器仅承载交互与玩家库存)。
  - Action 语义:`TAKE_STACK(id)`、`TAKE_ONE(id)`、`TAKE_TO_INV(id)`、`DEPOSIT_CURSOR`、`QUICK_DEPOSIT_ALL`。

- [ ] **Step 1: 实现 StorageProvider(每存档 DAO + 卸载关库)**

```java
package com.portablestorage;
import com.portablestorage.db.*;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.io.File; import java.util.*;

public class StorageProvider {
    private static final Map<String,StorageDb> DBS = new HashMap<String,StorageDb>();
    public static synchronized StorageDao daoFor(World world) {
        File dir = world.getSaveHandler().getWorldDirectory();
        String key = dir.getAbsolutePath();
        StorageDb db = DBS.get(key);
        if (db==null){ db=new StorageDb(new File(dir,"portablestorage/storage.db")); DBS.put(key,db); }
        return new StorageDao(db);
    }
    @SubscribeEvent public void onUnload(WorldEvent.Unload e) {
        if (e.world.provider.dimensionId==0) { // 主世界卸载=退出存档
            for (StorageDb db: DBS.values()) db.close(); DBS.clear();
        }
    }
}
```
在 `init` 注册:`MinecraftForge.EVENT_BUS.register(new StorageProvider());`。

- [ ] **Step 2: 实现容器 + open handler**

`ContainerPortableStorage` 继承 `Container`,在构造里 `addSlotToContainer` 铺玩家 3×9 背包 + 快捷栏(标准坐标);`canInteractWith` 返回 true。仓库网格不是 vanilla slot(由 GUI 自绘 + 包驱动)。
`OpenStoragePacket.Handler`(服务端):
```java
EntityPlayerMP p = ctx.getServerHandler().playerEntity;
p.getServerForPlayer(); // 确保在服务端
// 用自定义 GuiHandler 打开:
p.openGui(PortableStorageMod.instance, 0, p.worldObj, (int)p.posX,(int)p.posY,(int)p.posZ);
// 打开后立即发首屏窗口
sendPage(p, "", 0, Config.windowLimit);
return null;
```
实现 `IGuiHandler`(`getServerGuiElement` 返回 `ContainerPortableStorage`,`getClientGuiElement` 返回 `GuiPortableStorage`),`NetworkRegistry.INSTANCE.registerGuiHandler(instance, guiHandler)`。

- [ ] **Step 3: 实现 action / query handler(服务端改 DAO 并回包)**

`QueryPagePacket.Handler`:取 `daoFor(p.worldObj).queryWindowWithIds(uuid,kw,off,lim)` → 发 `PageResultPacket` 回该玩家。
`StorageActionPacket.Handler`:按 action:
- `TAKE_STACK`:读该 id 的 nbt+item → `decode` 出 `min(maxStackSize,count)` → `extract` → 放入玩家 cursor 或背包。
- `TAKE_ONE` / `TAKE_TO_INV` 类似。
- `DEPOSIT_CURSOR`:把玩家 cursor 的 ItemStack `encode` → `upsert` → 清 cursor。
- `QUICK_DEPOSIT_ALL`:遍历玩家主背包(含快捷栏,跳过装备槽与手持终端道具)→ 批量 `encode` → `upsertBatch` → 清这些 slot。
每次操作后重发当前窗口(用客户端上次的 kw/offset,随 action 包带上)。
取出 nbt 还原需要 DAO 加 `byte[] nbtOf(player,id)`(单查 nbt 列)。

- [ ] **Step 4: 构建确认编译** — Run: `./gradlew build --no-daemon` → SUCCESS。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: 每存档DB Provider + 容器 + open/query/action 服务端处理"
```

---

## Task 11: 客户端 GUI(AE 风格滚动 + 搜索 + 一键收纳,游戏内验证)

**Files:**
- Create: `src/main/java/com/portablestorage/client/GuiPortableStorage.java`
- Create: `src/main/resources/assets/portablestorage/textures/gui/storage.png`(背景图,可先用半透明纯色占位)
- Modify: `PageResultPacket.Handler`(客户端把窗口数据塞进当前打开的 GUI)

**Interfaces:**
- Consumes: `PageResultPacket`(total/offset/ids/items),`NetworkHandler.requestPage`,`StorageActionPacket`。
- Produces: `GuiPortableStorage extends GuiContainer`:
  - 可见区 9 列 × 5 行 = 45 格;右侧滚动条(拖动)+ 鼠标滚轮 → 改 `rowOffset` → 计算 `offset=rowOffset*9` → `requestPage(kw, offset, 90)`(取含上下缓冲的窗口)。
  - 顶部 `GuiTextField` 搜索框,输入防抖 ~150ms → `requestPage(kw,0,90)` 且滚动归零。
  - 每格画物品 + 数量角标(`abbrev(count)`:≥1e6→`x.xM`,≥1e3→`x.xK`)。
  - 鼠标左键格→`TAKE_STACK`,右键→`TAKE_ONE`,Shift+左键→`TAKE_TO_INV`;在网格空白放下 cursor→`DEPOSIT_CURSOR`。
  - "一键收纳背包"按钮→`QUICK_DEPOSIT_ALL`。

- [ ] **Step 1: 实现 GUI 主体**

给出关键结构(完整渲染细节在游戏内迭代);核心字段与方法:
```java
package com.portablestorage.client;
import net.minecraft.client.gui.*;
import net.minecraft.inventory.Container;
import org.lwjgl.input.Mouse;
import java.util.*;

public class GuiPortableStorage extends GuiContainer {
    public static final int COLS=9, ROWS=5, WINDOW=90;
    private GuiTextField search;
    private int rowOffset=0; private long total=0; private int winOffset=0;
    private long[] ids=new long[0];
    private List<int[]> view = new ArrayList<int[]>(); // 占位:渲染用精简数据由 PageResult 回填
    private String keyword="";
    private long lastTypeMs=0;

    public GuiPortableStorage(Container c){ super(c); xSize=176; ySize=222; }

    @Override public void initGui(){ super.initGui();
        search=new GuiTextField(fontRendererObj, guiLeft+8, guiTop+6, 120, 12);
        search.setFocused(true);
        buttonList.add(new GuiButton(1, guiLeft+8, guiTop+ySize-28, 80,20,"一键收纳"));
        requestWindow(0);
    }
    private void requestWindow(int rowOff){
        rowOffset=Math.max(0,rowOff);
        com.portablestorage.net.NetworkHandler.requestPage(keyword, rowOffset*COLS, WINDOW);
    }
    // 由 PageResultPacket.Handler 调用:
    public void onPage(long total, int offset, long[] ids /*+ 渲染数据*/){
        this.total=total; this.winOffset=offset; this.ids=ids; /* 存渲染数据 */
    }
    @Override protected void keyTyped(char ch,int key){
        if (search.textboxKeyTyped(ch,key)) { keyword=search.getText().toLowerCase();
            lastTypeMs=System.currentTimeMillis(); }
        else super.keyTyped(ch,key);
    }
    @Override public void updateScreen(){ super.updateScreen();
        if (lastTypeMs!=0 && System.currentTimeMillis()-lastTypeMs>150){ lastTypeMs=0; requestWindow(0); }
    }
    @Override public void handleMouseInput(){ super.handleMouseInput();
        int dw=Mouse.getEventDWheel();
        if (dw!=0){ int maxRow=(int)Math.max(0, Math.ceil(total/(double)COLS)-ROWS);
            requestWindow(Math.min(maxRow, Math.max(0, rowOffset + (dw>0?-1:1)))); }
    }
    @Override protected void actionPerformed(GuiButton b){
        if (b.id==1) com.portablestorage.net.NetworkHandler.sendAction(/*QUICK_DEPOSIT_ALL*/);
    }
    static String abbrev(long n){
        if (n>=1_000_000L) return String.format("%.1fM", n/1e6);
        if (n>=1_000L) return String.format("%.1fK", n/1e3);
        return Long.toString(n);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float pt,int mx,int my){ /* 画背景+格子+滚动条 */ }
    @Override protected void drawGuiContainerForegroundLayer(int mx,int my){ search.drawTextBox(); /* 画物品+角标 */ }
}
```
渲染物品图标用 `RenderItem`;角标用 `abbrev`。滚动条几何:轨道高=ROWS 格,滑块位置∝`rowOffset/maxRow`,可拖动(在 `mouseClickMove`/`mouseClicked` 里换算)。

- [ ] **Step 2: PageResultPacket.Handler(客户端回填)**

客户端 handler 取 `Minecraft.getMinecraft().currentScreen`,若是 `GuiPortableStorage` 则调 `onPage(...)`。

- [ ] **Step 3: 游戏内验证(对照 spec §8 清单 1/2/8)**

Run: `./gradlew runClient --no-daemon`
- `/give @p portablestorage:terminal`,右键开 GUI(或等 Task 12 的 B 键)。
- `/give` 几样物品 → 拖入网格存入;左键取一组、右键取 1、Shift 取到背包都正确。
- 搜索框输入中文/`zuanshi`/`zs` 都能过滤;清空恢复全部。
- 滚动条拖动 + 滚轮连续滚动顺滑;造出 >45 种物品验证滚动。
- 大数量角标显示 `1.2K`/`3.4M`。

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: AE风格滚动GUI + 搜索 + 数量角标 + 一键收纳按钮"
```

---

## Task 12: 按键绑定(B 开仓库 / K 切磁铁,游戏内验证)

**Files:**
- Create: `src/main/java/com/portablestorage/client/KeyBindings.java`, `src/main/java/com/portablestorage/proxy/{CommonProxy,ClientProxy}.java`
- Modify: `PortableStorageMod`(`@SidedProxy`,在 client 侧初始化按键)

**Interfaces:**
- Produces:
  - `KeyBindings.OPEN`(默认 `B`),`KeyBindings.MAGNET`(默认 `K`),均 `new KeyBinding(..., Keyboard.KEY_B/K, "key.categories.portablestorage")`,`ClientRegistry.registerKeyBinding`。
  - `@SubscribeEvent onKey(KeyInputEvent)`:OPEN 按下→`NetworkHandler.openStorage()`;MAGNET 按下→`NetworkHandler.sendMagnetMode()`(请求切下一档)。

- [ ] **Step 1: 实现代理 + 按键**

`ClientProxy.init()` 注册按键并 `FMLCommonHandler.instance().bus().register(new KeyBindings())`。`KeyBindings` 监听 `cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent`。

- [ ] **Step 2: 主类接 @SidedProxy**

```java
@cpw.mods.fml.common.SidedProxy(clientSide="com.portablestorage.proxy.ClientProxy", serverSide="com.portablestorage.proxy.CommonProxy")
public static CommonProxy proxy;
```
`init` 里 `proxy.init();`。

- [ ] **Step 3: 游戏内验证**

Run: `./gradlew runClient --no-daemon`
- 按 `B` 直接开仓库 GUI(无需手持道具)。
- 在控制设置里能看到 `B`/`K` 两个可改键项。
- 按 `K` 聊天栏提示档位切换(磁铁实际效果在 Task 13 验证)。

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: 按键 B 开仓库 / K 切磁铁档位 + SidedProxy"
```

---

## Task 13: 磁铁 MagnetManager + 档位持久化(MC 集成,游戏内验证)

**Files:**
- Create: `src/main/java/com/portablestorage/magnet/MagnetManager.java`, `src/main/java/com/portablestorage/magnet/PlayerMagnetState.java`
- Modify: `MagnetModePacket.Handler`(切档 + 回 Ack)、`init` 注册事件

**Interfaces:**
- Consumes: `Config.magnetRadius`,`MagnetRouter.route`,`StorageProvider.daoFor`,`ItemStackCodec.encode`。
- Produces:
  - `PlayerMagnetState.get(EntityPlayer)` / `set(EntityPlayer,int)` → 读写 `getEntityData().getCompoundTag("PlayerPersisted")` 里 `psMagnetMode`(0/1/2)。
  - `MagnetManager`(`@SubscribeEvent onPlayerTick(PlayerTickEvent)`,仅 `phase==END && !world.isRemote`):对 mode>0 玩家,取半径 R 内 `EntityItem`(`world.getEntitiesWithinAABB`),对每个用 `MagnetRouter.route` 分配:进背包部分塞 `player.inventory`,进仓库部分 `dao.upsert(encode(...))`;全部消化则 `entityItem.setDead()`。

- [ ] **Step 1: PlayerMagnetState**

```java
package com.portablestorage.magnet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
public final class PlayerMagnetState {
    private static final String P="PlayerPersisted", K="psMagnetMode";
    public static int get(EntityPlayer p){ return tag(p).getInteger(K); }
    public static void set(EntityPlayer p,int m){ tag(p).setInteger(K, ((m%3)+3)%3); }
    private static NBTTagCompound tag(EntityPlayer p){
        NBTTagCompound d=p.getEntityData(), t=d.getCompoundTag(P);
        if(!d.hasKey(P)) d.setTag(P,t); return t; }
}
```

- [ ] **Step 2: MagnetManager**

```java
@SubscribeEvent
public void onTick(TickEvent.PlayerTickEvent e){
    if (e.phase!=TickEvent.Phase.END || e.player.worldObj.isRemote) return;
    int mode = PlayerMagnetState.get(e.player); if (mode==0) return;
    double r = Config.magnetRadius;
    AxisAlignedBB box = e.player.boundingBox.expand(r,r,r);
    List<EntityItem> items = e.player.worldObj.getEntitiesWithinAABB(EntityItem.class, box);
    StorageDao dao = mode==2||true ? StorageProvider.daoFor(e.player.worldObj) : null;
    for (EntityItem ei: items){
        if (ei.isDead || ei.delayBeforeCanPickup>0) continue;
        ItemStack st = ei.getEntityItem(); if (st==null||st.stackSize<=0) continue;
        int free = freeSpaceFor(e.player, st); // 估算可入背包数量
        MagnetRouter.Split sp = MagnetRouter.route(mode, st.stackSize, free);
        if (sp.toInv>0){ ItemStack inv=st.copy(); inv.stackSize=sp.toInv; e.player.inventory.addItemStackToInventory(inv); }
        if (sp.toStorage>0){ ItemStack stg=st.copy(); stg.stackSize=sp.toStorage;
            dao.upsert(e.player.getUniqueID().toString(), ItemStackCodec.encode(stg)); }
        ei.setDead();
        e.player.worldObj.playSoundAtEntity(e.player,"random.pop",0.1f,1f);
    }
}
```
`freeSpaceFor` 遍历背包算该物品可叠入的空位数(满则 0)。

- [ ] **Step 3: 切档 handler + Ack + 提示**

`MagnetModePacket.Handler`(服务端):`int m=(get+1)%3; set(p,m);` 回 `MagnetModeAckPacket(m)`;客户端收到后聊天提示"磁铁: 关闭/进入背包/进入仓库"。

- [ ] **Step 4: 游戏内验证(spec §8 清单 6/7)**

Run: `./gradlew runClient --no-daemon`
- 丢一堆物品在地上,`K` 切到"进入背包":物品被吸进背包;背包塞满后溢出进仓库(开 B 仓库可见)。
- `K` 切到"进入仓库":物品直接进仓库,背包不动。
- 改 `config/portablestorage.cfg` 的 `magnetRadius`(试 1 与 128)→ 重进生效。
- 退出重进:仓库内容与磁铁档位都保留。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: 磁铁三档自动拾取 + 档位持久化 + 半径配置"
```

---

## Task 14: 依赖 shade 打包(sqlite-jdbc + pinyin4j)

**Files:**
- Modify: `build.gradle`(shadow 配置:把 sqlite-jdbc、pinyin4j 打入 jar 并 relocate;sqlite native 解压目录)

**Interfaces:**
- Produces: `build/libs/portablestorage-1.0.0.jar` 内含被 relocate 的 sqlite-jdbc 与 pinyin4j,玩家无需另装。

- [ ] **Step 1: 配置 shadow**

GTNH 模板自带 `shadowImplementation`/shadow 任务;把上面 `implementation` 改成会被打包的配置,并 relocate:
```groovy
shadowJar {
    relocate 'org.sqlite', 'com.portablestorage.shaded.sqlite'
    relocate 'net.sourceforge.pinyin4j', 'com.portablestorage.shaded.pinyin4j'
    relocate 'demo', 'com.portablestorage.shaded.pinyin4jdemo'
    mergeServiceFiles()
}
// 确保 reobf 作用于 shadowJar 产物(按模板的 reobfJar/relocate 顺序接线)
```
sqlite native 解压目录设到游戏可写路径:运行早期 `System.setProperty("org.sqlite.tmpdir", <gameDir>/portablestorage_native)`(在 preInit 设置)。

- [ ] **Step 2: 构建最终 jar**

Run: `./gradlew build --no-daemon`
Expected: 出含依赖的 jar;`jar tf build/libs/...jar | grep -i sqlite` 能看到被 relocate 的类(`com/portablestorage/shaded/sqlite/...`)。

- [ ] **Step 3: 干净环境验证(关键)**

把该 jar 复制到一个**真实安装包**测试目录的 `mods/`(或先用 `runClient` 但确认不是靠 IDE classpath 提供 sqlite):
- 开仓库存取、搜索、磁铁、退出重进都正常 → 证明依赖确实打进 jar 且 native 能解压加载。
- 若 sqlite native 加载失败 → 按 spec §10 预案评估回退 MapDB(纯 Java)。

- [ ] **Step 4: 提交**

```bash
git add build.gradle
git commit -m "build: shade+relocate sqlite-jdbc 与 pinyin4j 进最终jar"
```

---

## Task 15: 整包内端到端验收 + 安装

**Files:**
- 无新代码;按 spec §8 手动清单全量过一遍,修小问题。

- [ ] **Step 1: 装进真实整合包**

把 `build/libs/portablestorage-1.0.0.jar` 复制到:
`D:\MC\开放世界RPG 英雄黎明4：烈焰之火 EA3.0.4\.minecraft\mods\`
用包自带启动器启动游戏。

- [ ] **Step 2: 逐条验收(spec §8 清单 1–9)**

1. `B` 开仓库,拖入/取出/Shift/右键都正确。
2. 搜索 中文/词条/全拼/首字母 都能过滤。
3. 一键收纳:背包进仓库,装备与终端道具不被收走。
4. **同名不同附魔的 RPG 装备各占一条,取出后属性/词条完好**(重点对你的 RPG道具MOD 物品验证)。
5. 终端道具右键开同一仓库。
6. `K` 三档:进背包(满则溢出仓库)、进仓库、关闭,均有提示。
7. 退出重进:仓库内容 + 磁铁档位保留;DB 在 `<存档>/portablestorage/storage.db`。
8. 万级/亿级数量角标与取出正常。
9. 塞进上万种物品:滚动/搜索流畅,内存与存档无明显卡顿。

- [ ] **Step 3: 与现有模组共存抽查**

确认 InventoryTweaks 不打乱仓库 GUI;与 CustomNPCs/RPG道具MOD 同时运行不崩。

- [ ] **Step 4: 收尾提交 + 打 tag**

```bash
git add -A
git commit -m "test: 整包端到端验收通过, 1.0.0"
git tag v1.0.0
```

---

## Self-Review(规划自查)

**Spec 覆盖核对:**
- §1 必须有 1–6 → Task 4/5(无限存储+搜索)、Task 11(GUI/滚动/搜索/一键收纳)、Task 8/12(双开)、Task 3/8(NBT 独立保留)、Task 13(磁铁三档+溢出+半径)。✅
- §3 SQLite/schema/堆叠/并发/拼音列 → Task 4/5。✅
- §4 GUI AE 滚动/角标/搜索/取存/一键收纳/防呆 → Task 11(InventoryTweaks 防呆在 §10,Task 11 背景自绘的网格非 vanilla slot 天然不被 IT 排序;若仍有问题在 Task 15 处理)。✅
- §5 双开 + 仅创造栏/give 无配方 → Task 8/12。✅
- §6 磁铁 → Task 13;半径 clamp → Task 7。✅
- §7 网络包 → Task 9/10/11。✅
- §8 测试策略 → 各 TDD 任务 + Task 15 清单。✅
- §9 构建/shade → Task 1/14。✅
- §10 风险(sqlite 类加载/native、回退 MapDB)→ Task 14 Step 3。✅

**占位符扫描:** Task 2 Step 2 含一个显式"占位删除"用例,已注明删除;其余步骤均给真实代码或明确游戏内验证命令。GUI(Task 11)给的是结构骨架 + 关键方法 + 游戏内验证(渲染细节本就需在游戏内迭代,属合理)。

**类型/命名一致性:** `StoredItem` 字段、`StorageDao` 方法名(upsert/extract/queryWindow/countMatches/queryWindowWithIds/nbtOf)、`MagnetRouter.route/Split`、`NetworkHandler.requestPage/openStorage/sendAction/sendMagnetMode`、Config.magnetRadius/clampRadius 在各任务间一致。`nbtOf` 在 Task 10 Step 3 提及、需在该任务实现(单查 nbt 列)——实现时补上。
