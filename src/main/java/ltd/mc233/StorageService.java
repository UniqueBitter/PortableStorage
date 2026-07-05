package ltd.mc233;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.util.ChatComponentText;

import ltd.mc233.core.StoredItem;
import ltd.mc233.db.StorageDao;
import ltd.mc233.item.ItemPortableTerminal;
import ltd.mc233.item.ItemStackCodec;
import ltd.mc233.net.NetworkHandler;
import ltd.mc233.net.PageResultPacket;

public final class StorageService {

    public static final int WINDOW = 90;

    // 记录每个玩家当前的搜索词/偏移(供 shift存入等操作刷新)。当前标签与排序都持久化到玩家存档(见下)。
    private static final java.util.Map<String, String> LAST_KW = new java.util.concurrent.ConcurrentHashMap<String, String>();
    private static final java.util.Map<String, Integer> LAST_OFF = new java.util.concurrent.ConcurrentHashMap<String, Integer>();

    private StorageService() {}

    // 排序偏好持久化到玩家 PlayerPersisted NBT(随存档/重启保留)。
    // sort 编码: by = sort/2 (0名字 1数量 2类型 3时间), 降序 = (sort%2==1)。合法范围 0..7。
    private static int getSortPref(EntityPlayerMP p) {
        NBTTagCompound persist = p.getEntityData()
            .getCompoundTag("PlayerPersisted");
        int s = persist.getInteger("psSortMode");
        return (s < 0 || s > 7) ? 0 : s;
    }

    private static void setSortPref(EntityPlayerMP p, int sort) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persist = data.getCompoundTag("PlayerPersisted");
        if (!data.hasKey("PlayerPersisted")) data.setTag("PlayerPersisted", persist);
        persist.setInteger("psSortMode", (sort < 0 || sort > 7) ? 0 : sort);
    }

    // 锁定的背包格(位掩码, bit i = 背包索引 i 被锁), 持久化。锁定的格不会被"一键收纳"收走。
    public static long getLockedSlots(EntityPlayerMP p) {
        return p.getEntityData()
            .getCompoundTag("PlayerPersisted")
            .getLong("psLockedSlots");
    }

    public static void toggleLock(EntityPlayerMP p, int slot) {
        if (slot < 0 || slot > 63) return;
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persist = data.getCompoundTag("PlayerPersisted");
        if (!data.hasKey("PlayerPersisted")) data.setTag("PlayerPersisted", persist);
        long mask = persist.getLong("psLockedSlots") ^ (1L << slot);
        persist.setLong("psLockedSlots", mask);
        refresh(p);
    }

    // 自动补货开关(每玩家持久化, 默认开)。存 psRestockOff: true=关。
    public static boolean isRestockOn(net.minecraft.entity.player.EntityPlayer p) {
        return !p.getEntityData()
            .getCompoundTag("PlayerPersisted")
            .getBoolean("psRestockOff");
    }

    public static void toggleRestock(EntityPlayerMP p) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persist = data.getCompoundTag("PlayerPersisted");
        if (!data.hasKey("PlayerPersisted")) data.setTag("PlayerPersisted", persist);
        persist.setBoolean("psRestockOff", !persist.getBoolean("psRestockOff"));
        refresh(p);
    }

    // 收纳模式(每玩家持久化, 随存档记住)。存 psStackMatch: true=只补充仓库已有种类; false(默认)=全部收纳。
    public static boolean isStackMatch(net.minecraft.entity.player.EntityPlayer p) {
        return p.getEntityData()
            .getCompoundTag("PlayerPersisted")
            .getBoolean("psStackMatch");
    }

    public static void toggleStackMatch(EntityPlayerMP p) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persist = data.getCompoundTag("PlayerPersisted");
        if (!data.hasKey("PlayerPersisted")) data.setTag("PlayerPersisted", persist);
        persist.setBoolean("psStackMatch", !persist.getBoolean("psStackMatch"));
        refresh(p);
    }

    // ===== 容量制 (随身仓库 2.0) =====
    // 容量 = 能存多少"种"物品, 持久化到 PlayerPersisted.psCapacity。
    // 首次/未设(<=0) → 初始化为 max(默认容量, 当前已用种类数), 保证老数据不被锁死。
    public static int getCapacity(EntityPlayer p) {
        NBTTagCompound persist = p.getEntityData()
            .getCompoundTag("PlayerPersisted");
        // 用单独的 psCapSet 标记是否已初始化(因为容量 0 是合法值, 不能拿 0 当"未设")。
        if (!persist.getBoolean("psCapSet")) {
            int used = (int) StorageProvider.dao()
                .countTypes(StorageProvider.keyFor(p));
            int cap = Math.max(getDefaultCapacity(), used); // 老数据按现有种类数起步, 不锁死
            setCapacity(p, cap);
            return cap;
        }
        return Math.max(0, persist.getInteger("psCapacity"));
    }

    // 新玩家默认起始容量: 存在随存档的仓库 DB(settings 表), 由 /storage cap default 设 —— 不用配置文件。
    public static int getDefaultCapacity() {
        return StorageProvider.dao()
            .getSettingInt("defaultCapacity", 0);
    }

    public static void setDefaultCapacity(int n) {
        StorageProvider.dao()
            .setSettingInt("defaultCapacity", Math.max(0, n));
    }

    public static void setCapacity(EntityPlayer p, int cap) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persist = data.getCompoundTag("PlayerPersisted");
        if (!data.hasKey("PlayerPersisted")) data.setTag("PlayerPersisted", persist);
        persist.setInteger("psCapacity", Math.max(0, cap));
        persist.setBoolean("psCapSet", true);
    }

    public static void addCapacity(EntityPlayer p, int delta) {
        setCapacity(p, getCapacity(p) + delta); // 指令用: 不受 maxCapacity 上限约束, 可突破
    }

    // 扩容券等"正常途径"用: 加容量但封顶到 Config.maxCapacity(若已被指令顶到上限之上, 则保持不降)。
    public static void grantCapacity(EntityPlayer p, int delta) {
        int cur = getCapacity(p);
        int ceiling = Math.max(Config.maxCapacity, cur);
        setCapacity(p, Math.min(cur + delta, ceiling));
    }

    public static int getUsed(String uuid) {
        return (int) StorageProvider.dao()
            .countTypes(uuid);
    }

    // 仓库开启锁: true = 该玩家当前无法打开随身仓库(供地图剧情/区域限制用, /storage lock 控制)。
    public static boolean isLocked(EntityPlayerMP p) {
        return p.getEntityData()
            .getCompoundTag("PlayerPersisted")
            .getBoolean("psLocked");
    }

    public static void setLocked(EntityPlayerMP p, boolean locked) {
        persist(p).setBoolean("psLocked", locked);
    }

    // 扩容券: 容量为 0 时 +24(起步一块), 否则每次 +6。返回实际增加量(0=已达上限)。
    public static int useVoucher(EntityPlayerMP p) {
        int before = getCapacity(p);
        grantCapacity(p, before == 0 ? 24 : 6);
        return getCapacity(p) - before;
    }

    // 统一的存入入口: it 含数量。已存在的种类 → 直接累加(永远允许); 新种类 → 需 used<capacity, 否则拒绝。
    // 返回 true=已存入; false=容量已满(新种类放不下)。调用方负责在 false 时把物品留住、给反馈。
    public static boolean deposit(EntityPlayer p, StorageDao dao, String uuid, StoredItem it) {
        if (!Config.unlimitedStorage) { // 无限容量版跳过种类上限检查
            boolean exists = dao.entryId(uuid, it.getItem(), it.getMeta(), it.getNbtHash()) >= 0;
            if (!exists && dao.countTypes(uuid) >= getCapacity(p)) return false;
        }
        dao.upsert(uuid, it);
        return true;
    }

    // GUI 存入: 若当前正在看某个具体标签(>=1), 存入的物品自动归到该标签; 在"全部"/未分类视图则保持未分类。
    public static boolean depositToCurrentTab(EntityPlayerMP p, StorageDao dao, String uuid, StoredItem it) {
        if (!deposit(p, dao, uuid, it)) return false;
        int t = currentTab(p);
        if (t >= 1) dao.setTab(uuid, it.getItem(), it.getMeta(), it.getNbtHash(), t);
        return true;
    }

    // 锁定格自动同步(打开/关闭随身仓库时各调一次):
    // 1) 背包"非锁定格"里与锁定物品同种的 → 全部存入仓库(该物品只保留在锁定格 + 仓库);
    // 2) 每个非空锁定格 → 从仓库补满到该物品的满堆叠。
    // 空的锁定格不处理(无从得知该放什么); 终端不动。
    public static void syncLockedSlots(EntityPlayerMP p) {
        long locked = getLockedSlots(p);
        if (locked == 0) return;
        ItemStack[] main = p.inventory.mainInventory;
        StorageDao dao = StorageProvider.dao();
        String uuid = StorageProvider.keyFor(p);

        // 收集锁定格里的物品种类
        java.util.Set<String> lockedKeys = new java.util.HashSet<String>();
        for (int i = 0; i < main.length && i < 64; i++) {
            if ((locked & (1L << i)) == 0) continue;
            ItemStack st = main[i];
            if (st == null || st.getItem() instanceof ItemPortableTerminal) continue;
            lockedKeys.add(itemKey(ItemStackCodec.encode(st, p)));
        }
        if (lockedKeys.isEmpty()) return;

        boolean changed = false;
        // 1) 非锁定格里的同种物品 → 存入仓库(存不下则留在背包, 不丢)
        for (int i = 0; i < main.length && i < 64; i++) {
            if ((locked & (1L << i)) != 0) continue;
            ItemStack st = main[i];
            if (st == null || st.getItem() instanceof ItemPortableTerminal) continue;
            StoredItem enc = ItemStackCodec.encode(st, p);
            if (!lockedKeys.contains(itemKey(enc))) continue;
            if (deposit(p, dao, uuid, enc)) {
                main[i] = null;
                changed = true;
            }
        }
        // 2) 每个锁定格补满到该物品的满堆叠(仓库有多少补多少)
        for (int i = 0; i < main.length && i < 64; i++) {
            if ((locked & (1L << i)) == 0) continue;
            ItemStack st = main[i];
            if (st == null || st.getItem() instanceof ItemPortableTerminal) continue;
            int need = st.getMaxStackSize() - st.stackSize;
            if (need <= 0) continue;
            StoredItem enc = ItemStackCodec.encode(st, p);
            long id = dao.entryId(uuid, enc.getItem(), enc.getMeta(), enc.getNbtHash());
            if (id < 0) continue; // 仓库没这种 → 无从补
            long took = dao.extract(uuid, id, need);
            if (took > 0) {
                st.stackSize += (int) took;
                changed = true;
            }
        }
        if (changed) {
            p.inventoryContainer.detectAndSendChanges();
            if (p.openContainer != null) p.openContainer.detectAndSendChanges();
        }
    }

    private static String itemKey(StoredItem it) {
        return it.getItem() + "|" + it.getMeta() + "|" + it.getNbtHash();
    }

    // "仓库已满"提示(节流由调用方控制, 一次操作只提示一次)。
    public static void warnFull(EntityPlayer p) {
        String uuid = StorageProvider.keyFor(p);
        p.addChatMessage(
            new ChatComponentText("§c随身仓库已满 (" + getUsed(uuid) + "/" + getCapacity(p) + " 种), 放不下新种类。需扩容。"));
    }

    public static void refresh(EntityPlayerMP p) {
        String key = StorageProvider.keyFor(p);
        String kw = LAST_KW.containsKey(key) ? LAST_KW.get(key) : "";
        int off = LAST_OFF.containsKey(key) ? LAST_OFF.get(key) : 0;
        sendPage(p, kw, off, -1, currentTab(p)); // -1 = 用记忆的排序; 标签用记忆的
    }

    // sortArg: -1 表示"用玩家记忆的排序"; 0..5 表示显式设置(并记忆)。tabId: -1=全部,0=未分类,>=1=自定义。
    public static void sendPage(EntityPlayerMP p, String keyword, int offset, int sortArg, int tabId) {
        String puuid = StorageProvider.keyFor(p);
        if (tabId == -2) tabId = currentTab(p); // -2 = 用持久化的当前标签(如刚打开界面时)
        LAST_KW.put(puuid, keyword == null ? "" : keyword);
        LAST_OFF.put(puuid, offset);
        persist(p).setInteger("psLastTab", tabId); // 当前标签唯一记忆点: 持久化到存档, 重开界面/重启/重连都从这里读
        int sort;
        if (sortArg < 0) {
            sort = getSortPref(p);
        } else {
            sort = sortArg > 7 ? 0 : sortArg;
            setSortPref(p, sort);
        }
        StorageDao dao = StorageProvider.dao();
        StorageDao.PageResult r = dao.queryWindowWithIds(puuid, keyword, offset, WINDOW, sort, tabId);
        int n = r.items.size();
        long[] ids = r.ids;
        String[] items = new String[n];
        int[] metas = new int[n];
        long[] counts = new long[n];
        String[] names = new String[n];
        byte[][] nbts = new byte[n][];
        for (int i = 0; i < n; i++) {
            StoredItem si = r.items.get(i);
            items[i] = si.getItem();
            metas[i] = si.getMeta();
            counts[i] = si.getCount();
            names[i] = si.getName();
            nbts[i] = si.getNbt();
        }
        PageResultPacket pkt = new PageResultPacket(
            r.total,
            offset,
            sort,
            getLockedSlots(p),
            isRestockOn(p),
            (int) dao.countTypes(puuid),
            Config.unlimitedStorage ? -1 : getCapacity(p), // -1 = 无限容量(界面不显示上限)
            ids,
            items,
            metas,
            counts,
            names,
            nbts);
        pkt.curTab = tabId;
        pkt.stackMatch = isStackMatch(p); // 让界面开箱即显示上次记住的收纳模式
        net.minecraft.nbt.NBTTagList list = tabList(p);
        pkt.tabIds = new int[list.tagCount()];
        pkt.tabNames = new String[list.tagCount()];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            pkt.tabIds[i] = t.getInteger("Id");
            pkt.tabNames[i] = t.getString("Name");
        }
        NetworkHandler.INSTANCE.sendTo(pkt, p);
    }

    // ===== 标签 (小房间) =====
    private static int currentTab(EntityPlayerMP p) {
        NBTTagCompound ps = persist(p);
        int tab = ps.hasKey("psLastTab") ? ps.getInteger("psLastTab") : -1; // 无记录默认"全部"
        if (tab >= 1 && !tabExists(p, tab)) tab = -1; // 记忆的自定义标签已被删 → 回到全部
        return tab;
    }

    private static boolean tabExists(EntityPlayerMP p, int tabId) {
        net.minecraft.nbt.NBTTagList list = tabList(p);
        for (int i = 0; i < list.tagCount(); i++) if (list.getCompoundTagAt(i)
            .getInteger("Id") == tabId) return true;
        return false;
    }

    private static NBTTagCompound persist(EntityPlayerMP p) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound ps = data.getCompoundTag("PlayerPersisted");
        if (!data.hasKey("PlayerPersisted")) data.setTag("PlayerPersisted", ps);
        return ps;
    }

    private static net.minecraft.nbt.NBTTagList tabList(EntityPlayerMP p) {
        return persist(p).getTagList("psTabs", 10); // 10 = Compound
    }

    public static void createTab(EntityPlayerMP p, String name) {
        NBTTagCompound ps = persist(p);
        int next = ps.getInteger("psNextTab");
        if (next < 1) next = 1;
        net.minecraft.nbt.NBTTagList list = ps.getTagList("psTabs", 10);
        if (list.tagCount() >= 15) return; // 最多 15 个自定义标签
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("Id", next);
        t.setString("Name", name == null ? "" : name.trim()); // 默认空名 → 界面按位置编号显示
        list.appendTag(t);
        ps.setTag("psTabs", list);
        ps.setInteger("psNextTab", next + 1);
    }

    public static void deleteTab(EntityPlayerMP p, int tabId) {
        if (tabId < 1) return; // 全部/未分类不可删
        NBTTagCompound ps = persist(p);
        net.minecraft.nbt.NBTTagList list = ps.getTagList("psTabs", 10);
        net.minecraft.nbt.NBTTagList nl = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            if (t.getInteger("Id") != tabId) nl.appendTag(t);
        }
        ps.setTag("psTabs", nl);
        StorageProvider.dao()
            .clearTab(StorageProvider.keyFor(p), tabId); // 物品移回未分类
        if (currentTab(p) == tabId) persist(p).setInteger("psLastTab", -1); // 当前在被删页 → 切回全部
    }

    public static void renameTab(EntityPlayerMP p, int tabId, String name) {
        if (tabId < 1 || name == null
            || name.trim()
                .isEmpty())
            return;
        NBTTagCompound ps = persist(p);
        net.minecraft.nbt.NBTTagList list = ps.getTagList("psTabs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            if (t.getInteger("Id") == tabId) {
                t.setString("Name", name.trim());
                break;
            }
        }
        ps.setTag("psTabs", list);
    }

    // 把光标上的物品归到某标签: 先存入(受容量限制), 再打标签, 清空光标。
    public static void assignCursorToTab(EntityPlayerMP p, int tabId) {
        ItemStack held = p.inventory.getItemStack();
        if (held == null) return;
        StorageDao dao = StorageProvider.dao();
        String uuid = StorageProvider.keyFor(p);
        StoredItem enc = ItemStackCodec.encode(held, p);
        if (!deposit(p, dao, uuid, enc)) {
            warnFull(p);
            return;
        }
        dao.setTab(uuid, enc.getItem(), enc.getMeta(), enc.getNbtHash(), Math.max(0, tabId));
        p.inventory.setItemStack(null);
        p.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-1, -1, null));
    }

    public static void handleTabAction(EntityPlayerMP p, int op, int tabId, String name) {
        switch (op) {
            case 0:
                createTab(p, name);
                break;
            case 1:
                deleteTab(p, tabId);
                break;
            case 2:
                renameTab(p, tabId, name);
                break;
            case 3:
                assignCursorToTab(p, tabId);
                break;
            case 4:
                reorderTab(p, tabId, parseIntOr(name, 0));
                break;
            default:
                break;
        }
        refresh(p);
    }

    private static int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    // 把某自定义标签移动到第 pos 个位置(1-based, 仅在自定义标签之间排序)。
    public static void reorderTab(EntityPlayerMP p, int tabId, int pos) {
        if (tabId < 1 || pos < 1) return;
        NBTTagCompound ps = persist(p);
        net.minecraft.nbt.NBTTagList list = ps.getTagList("psTabs", 10);
        java.util.List<NBTTagCompound> rest = new java.util.ArrayList<NBTTagCompound>();
        NBTTagCompound moving = null;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            if (moving == null && t.getInteger("Id") == tabId) moving = t;
            else rest.add(t);
        }
        if (moving == null) return;
        int idx = Math.max(0, Math.min(rest.size(), pos - 1)); // 1-based → 0-based, 越界夹取
        rest.add(idx, moving);
        net.minecraft.nbt.NBTTagList nl = new net.minecraft.nbt.NBTTagList();
        for (NBTTagCompound t : rest) nl.appendTag(t);
        ps.setTag("psTabs", nl);
    }

    public static void handleAction(EntityPlayerMP p, int action, long id, int amount, String keyword, int offset) {
        StorageDao dao = StorageProvider.dao();
        String uuid = StorageProvider.keyFor(p);
        switch (action) {
            case 0: // TAKE_STACK -> 取一组到鼠标光标
                takeToCursor(p, dao, uuid, id, -1);
                break;
            case 1: // TAKE_ONE -> 取1个到鼠标光标
                takeToCursor(p, dao, uuid, id, 1);
                break;
            case 2: // TAKE_TO_INV -> Shift: 取一组直接进背包
                takeToInventory(p, dao, uuid, id, -1);
                break;
            case 3: // DEPOSIT_CURSOR
                depositCursor(p, dao, uuid);
                break;
            case 4: // QUICK_DEPOSIT_ALL -> 全部收纳(非锁定的都收)
                quickDepositAll(p, dao, uuid);
                break;
            case 5: // QUICK_STACK -> 仅补充仓库已有种类(泰拉瑞亚式)
                quickStack(p, dao, uuid);
                break;
            case 6: // TOGGLE_STACK_MODE -> 切换收纳模式(全部 ↔ 仅补充已有), 持久化, 不执行收纳
                toggleStackMatch(p);
                break;
            default:
                break;
        }
        sendPage(p, keyword, offset, -1, currentTab(p)); // -1 = 用记忆的排序; 标签保持当前
    }

    // 取到鼠标光标(AE 风格): 光标空则放新堆叠; 光标已持同种物品则在容量内追加; 异种则不动。
    private static void takeToCursor(EntityPlayerMP p, StorageDao dao, String uuid, long id, int requested) {
        StoredItem si = dao.findById(uuid, id);
        if (si == null) return;
        ItemStack probe = ItemStackCodec.decode(si, 1);
        if (probe == null) return;
        int maxStack = probe.getMaxStackSize();
        ItemStack cursor = p.inventory.getItemStack();
        int want;
        if (cursor == null) {
            want = requested < 0 ? maxStack : Math.min(requested, maxStack);
        } else {
            if (!cursor.isItemEqual(probe) || !ItemStack.areItemStackTagsEqual(cursor, probe)) return;
            int room = maxStack - cursor.stackSize;
            if (room <= 0) return;
            want = requested < 0 ? room : Math.min(requested, room);
        }
        if (want <= 0) return;
        long took = dao.extract(uuid, id, want);
        if (took <= 0) return;
        if (cursor == null) {
            p.inventory.setItemStack(ItemStackCodec.decode(si, (int) took));
        } else {
            cursor.stackSize += (int) took;
            p.inventory.setItemStack(cursor);
        }
        p.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-1, -1, p.inventory.getItemStack()));
    }

    private static void takeToInventory(EntityPlayerMP p, StorageDao dao, String uuid, long id, int requested) {
        StoredItem si = dao.findById(uuid, id);
        if (si == null) return;
        // 先探测能否还原(未知物品对应mod已移除): 探测非空才动DB, 避免extract后无法给出而丢物。
        ItemStack probe = ItemStackCodec.decode(si, 1);
        if (probe == null) return;
        int maxStack = probe.getMaxStackSize();
        int want = requested < 0 ? maxStack : Math.min(requested, maxStack);
        long took = dao.extract(uuid, id, want);
        if (took <= 0) return;
        ItemStack give = ItemStackCodec.decode(si, (int) took);
        boolean added = p.inventory.addItemStackToInventory(give);
        if (!added || give.stackSize > 0) {
            int leftover = give.stackSize;
            if (leftover > 0) {
                StoredItem back = si.withCount(leftover);
                dao.upsert(uuid, back);
            }
        }
        p.inventoryContainer.detectAndSendChanges();
        if (p.openContainer != null) p.openContainer.detectAndSendChanges();
    }

    private static void depositCursor(EntityPlayerMP p, StorageDao dao, String uuid) {
        ItemStack held = p.inventory.getItemStack();
        if (held == null) return;
        if (!depositToCurrentTab(p, dao, uuid, ItemStackCodec.encode(held, p))) {
            warnFull(p); // 满了 → 物品留在光标, 不丢
            return;
        }
        p.inventory.setItemStack(null);
        p.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-1, -1, p.inventory.getItemStack()));
    }

    // 按 F 键的入口(不依赖界面): 全部收纳背包 → 刷新界面 + 反馈条数。
    public static void quickDepositAll(EntityPlayerMP p) {
        int moved = quickDepositAll(p, StorageProvider.dao(), StorageProvider.keyFor(p));
        refresh(p); // 界面开着则刷新
        if (moved > 0) p.addChatMessage(new ChatComponentText("§a已收纳 §e" + moved + " §a组物品到随身仓库"));
    }

    // 返回实际收纳(移入仓库)的格子数。
    private static int quickDepositAll(EntityPlayerMP p, StorageDao dao, String uuid) {
        long locked = getLockedSlots(p);
        boolean full = false;
        int moved = 0;
        // 逐个存入(而非批量), 以便容量随新种类逐件递增地判断; 放不下的留在背包不丢。
        // 用 deposit(非 depositToCurrentTab): 已归类的合并时保留原标签(各回各家), 新种类进未分类, 不硬塞当前标签。
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack st = p.inventory.mainInventory[i];
            if (st == null) continue;
            if (st.getItem() instanceof ItemPortableTerminal) continue;
            if ((locked & (1L << i)) != 0) continue; // 锁定的格不收
            if (deposit(p, dao, uuid, ItemStackCodec.encode(st, p))) {
                p.inventory.mainInventory[i] = null;
                moved++;
            } else {
                full = true;
            }
        }
        if (full) warnFull(p);
        p.inventoryContainer.detectAndSendChanges();
        if (p.openContainer != null) p.openContainer.detectAndSendChanges();
        return moved;
    }

    // 泰拉瑞亚式"快速堆叠"(按键入口, 不依赖界面): 委托给内部实现后手动刷新界面。
    public static void quickStack(EntityPlayerMP p) {
        quickStack(p, StorageProvider.dao(), StorageProvider.keyFor(p));
        refresh(p); // 界面开着则刷新(界面动作路径由 sendPage 负责刷新, 这里给按键路径补一次)
    }

    // 只把背包(9-35, 不含快捷栏)里"仓库已有种类"的物品存入(补充已有堆叠);
    // 仓库没有的种类、快捷栏、锁定格、终端都不动。不占新容量, 合并进原有行(保持其所在标签)。
    private static void quickStack(EntityPlayerMP p, StorageDao dao, String uuid) {
        long locked = getLockedSlots(p);
        ItemStack[] main = p.inventory.mainInventory;
        java.util.Set<String> existing = dao.keySet(uuid); // 一次查出所有已有种类, 免得逐件查库
        java.util.List<StoredItem> toStore = new java.util.ArrayList<StoredItem>();
        java.util.List<Integer> slots = new java.util.ArrayList<Integer>();
        for (int i = 9; i < main.length; i++) { // 跳过快捷栏 0-8
            ItemStack st = main[i];
            if (st == null) continue;
            if (st.getItem() instanceof ItemPortableTerminal) continue;
            if ((locked & (1L << i)) != 0) continue; // 锁定格不收
            StoredItem enc = ItemStackCodec.encode(st, p);
            if (!existing.contains(itemKey(enc))) continue; // 仓库没这种 → 跳过
            toStore.add(enc);
            slots.add(i);
        }
        if (toStore.isEmpty()) return;
        dao.upsertBatch(uuid, toStore); // 一个事务批量合并(保持原标签, 无需容量检查)
        for (int idx : slots) main[idx] = null;
        p.inventoryContainer.detectAndSendChanges();
        if (p.openContainer != null) p.openContainer.detectAndSendChanges();
    }
}
