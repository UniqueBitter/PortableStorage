package ltd.mc233.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import ltd.mc233.core.StoredItem;

public class StorageDao {

    private final StorageDb db;

    public StorageDao(StorageDb db) {
        this.db = db;
    }

    private static final String UPSERT_SQL = "INSERT INTO entries(player,item,meta,nbt,nbt_hash,count,name,lore) "
        + "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(player,item,meta,nbt_hash) DO UPDATE SET count=count+excluded.count";

    public void upsert(String player, StoredItem it) {
        db.update(UPSERT_SQL, ps -> bind(ps, player, it));
    }

    public void upsertBatch(String player, List<StoredItem> items) {
        synchronized (db) {
            String sql = UPSERT_SQL;
            Connection c = db.getConnection();
            try {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    for (StoredItem it : items) {
                        bind(ps, player, it);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {}
                throw new RuntimeException(e);
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }
    }

    // 已用容量 = 该玩家的条目行数(每行 = 一种已堆叠物品)。
    public long countTypes(String player) {
        return db.queryOne(
            "SELECT COUNT(*) FROM entries WHERE player=?",
            ps -> ps.setString(1, player),
            rs -> rs.getLong(1),
            0L);
    }

    public long countOf(String player, String item, int meta, String nbtHash) {
        return db.queryOne(
            "SELECT count FROM entries WHERE player=? AND item=? AND meta=? AND nbt_hash=?",
            ps -> bindKey(ps, player, item, meta, nbtHash),
            rs -> rs.getLong(1),
            0L);
    }

    private void bind(PreparedStatement ps, String player, StoredItem it) throws SQLException {
        ps.setString(1, player);
        ps.setString(2, it.getItem());
        ps.setInt(3, it.getMeta());
        if (it.getNbt() == null) ps.setNull(4, Types.BLOB);
        else ps.setBytes(4, it.getNbt());
        ps.setString(5, it.getNbtHash());
        ps.setLong(6, it.getCount());
        ps.setString(7, it.getName());
        ps.setString(8, it.getLore());
    }

    public long entryId(String player, String item, int meta, String nbtHash) {
        return db.queryOne(
            "SELECT id FROM entries WHERE player=? AND item=? AND meta=? AND nbt_hash=?",
            ps -> bindKey(ps, player, item, meta, nbtHash),
            rs -> rs.getLong(1),
            -1L);
    }

    // 绑定 (player,item,meta,nbt_hash) 这组常用主键条件。
    private static void bindKey(PreparedStatement ps, String player, String item, int meta, String nbtHash)
        throws SQLException {
        ps.setString(1, player);
        ps.setString(2, item);
        ps.setInt(3, meta);
        ps.setString(4, nbtHash);
    }

    public long extract(String player, long id, long amount) {
        synchronized (db) {
            try (PreparedStatement sel = db.getConnection()
                .prepareStatement("SELECT count FROM entries WHERE player=? AND id=?")) {
                sel.setString(1, player);
                sel.setLong(2, id);
                long have;
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) return 0;
                    have = rs.getLong(1);
                }
                long take = Math.min(amount, have);
                if (take <= 0) return 0;
                if (take == have) {
                    try (PreparedStatement del = db.getConnection()
                        .prepareStatement("DELETE FROM entries WHERE player=? AND id=?")) {
                        del.setString(1, player);
                        del.setLong(2, id);
                        del.executeUpdate();
                    }
                } else {
                    try (PreparedStatement upd = db.getConnection()
                        .prepareStatement("UPDATE entries SET count=count-? WHERE player=? AND id=?")) {
                        upd.setLong(1, take);
                        upd.setString(2, player);
                        upd.setLong(3, id);
                        upd.executeUpdate();
                    }
                }
                return take;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // tabId: <0 = 全部(不按标签过滤); >=0 = 仅该标签。关键词匹配改为 Java 侧 PinIn 拼音模糊匹配, SQL 只按玩家/标签过滤。
    private String whereTab(int tabId) {
        return tabId >= 0 ? "player=? AND tab=?" : "player=?";
    }

    private int bindTab(PreparedStatement ps, String player, int tabId) throws SQLException {
        ps.setString(1, player);
        if (tabId >= 0) {
            ps.setInt(2, tabId);
            return 3;
        }
        return 2;
    }

    private StoredItem read(ResultSet rs) throws SQLException {
        return new StoredItem(
            rs.getString("item"),
            rs.getInt("meta"),
            rs.getBytes("nbt"),
            rs.getString("nbt_hash"),
            rs.getLong("count"),
            rs.getString("name"),
            rs.getString("lore"));
    }

    // 拼音/中文/英文模糊匹配: 直接对物品名+词条用 PinIn 匹配(支持全拼/首字母/中英混合)。
    private boolean matchKeyword(String name, String lore, String keyword) {
        if (keyword == null || keyword.isEmpty()) return true;
        String text = (name == null ? "" : name) + " " + (lore == null ? "" : lore);
        return ltd.mc233.core.PinInUtil.matches(text, keyword);
    }

    // 把某种物品(player+item+meta+nbt_hash)归到某标签。
    public void setTab(String player, String item, int meta, String nbtHash, int tabId) {
        db.update("UPDATE entries SET tab=? WHERE player=? AND item=? AND meta=? AND nbt_hash=?", ps -> {
            ps.setInt(1, tabId);
            ps.setString(2, player);
            ps.setString(3, item);
            ps.setInt(4, meta);
            ps.setString(5, nbtHash);
        });
    }

    // 删除标签时: 把该标签里的物品全部移回未分类(0)。
    public void clearTab(String player, int tabId) {
        db.update("UPDATE entries SET tab=0 WHERE player=? AND tab=?", ps -> {
            ps.setString(1, player);
            ps.setInt(2, tabId);
        });
    }

    // 向后兼容的便捷重载(tabId=-1 表示全部, 不按标签过滤)。
    public long countMatches(String player, String keyword) {
        return countMatches(player, keyword, -1);
    }

    public List<StoredItem> queryWindow(String player, String keyword, int offset, int limit) {
        return queryWindow(player, keyword, offset, limit, 0, -1);
    }

    public List<StoredItem> queryWindow(String player, String keyword, int offset, int limit, int sort) {
        return queryWindow(player, keyword, offset, limit, sort, -1);
    }

    public PageResult queryWindowWithIds(String player, String keyword, int offset, int limit) {
        return queryWindowWithIds(player, keyword, offset, limit, 0, -1);
    }

    public PageResult queryWindowWithIds(String player, String keyword, int offset, int limit, int sort) {
        return queryWindowWithIds(player, keyword, offset, limit, sort, -1);
    }

    public long countMatches(String player, String keyword, int tabId) {
        synchronized (db) {
            long n = 0;
            String sql = "SELECT name,lore FROM entries WHERE " + whereTab(tabId);
            try (PreparedStatement ps = db.getConnection()
                .prepareStatement(sql)) {
                bindTab(ps, player, tabId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (matchKeyword(rs.getString("name"), rs.getString("lore"), keyword)) n++;
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return n;
        }
    }

    // 排序编码: by=sort/2 (0名字 1数量 2类型 3时间), 降序=(sort%2==1)。次级一律以 name 收尾保证稳定。
    private String orderBy(int sort) {
        int by = (sort < 0 ? 0 : sort) / 2;
        String d = (sort >= 0 && sort % 2 == 1) ? " DESC" : " ASC";
        switch (by) {
            case 1:
                return " ORDER BY count" + d + ", name ASC";
            case 2:
                return " ORDER BY item" + d + ", meta" + d + ", name ASC";
            case 3:
                return " ORDER BY id" + d; // 时间: 按放入顺序(插入 id), 升序=先放的在前
            default:
                return " ORDER BY name" + d;
        }
    }

    // 具体标签(>=1)内关闭自动排序: 按放入顺序(插入 id)排; "全部"(-1)用名字/数量/类型排序。
    private String orderByFor(int sort, int tabId) {
        return tabId >= 1 ? " ORDER BY id ASC" : orderBy(sort);
    }

    public List<StoredItem> queryWindow(String player, String keyword, int offset, int limit, int sort, int tabId) {
        return queryWindowWithIds(player, keyword, offset, limit, sort, tabId).items;
    }

    public static final class PageResult {

        public final long[] ids;
        public final List<StoredItem> items;
        public final long total;

        public PageResult(long[] ids, List<StoredItem> items, long total) {
            this.ids = ids;
            this.items = items;
            this.total = total;
        }
    }

    public StoredItem findById(String player, long id) {
        return db.queryOne("SELECT item,meta,nbt,nbt_hash,count,name,lore FROM entries WHERE player=? AND id=?", ps -> {
            ps.setString(1, player);
            ps.setLong(2, id);
        }, this::read, null);
    }

    // 取出某玩家全部条目(含 id), 供搜索索引重建使用。条目数通常几十~几百, 一次性载入无碍。
    public PageResult allWithIds(String player) {
        List<Entry> rows = db.query(
            "SELECT id,item,meta,nbt,nbt_hash,count,name,lore FROM entries WHERE player=?",
            ps -> ps.setString(1, player),
            rs -> new Entry(rs.getLong("id"), read(rs)));
        long[] ids = new long[rows.size()];
        List<StoredItem> items = new java.util.ArrayList<StoredItem>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ids[i] = rows.get(i).id;
            items.add(rows.get(i).item);
        }
        return new PageResult(ids, items, ids.length);
    }

    private static final class Entry {

        final long id;
        final StoredItem item;

        Entry(long id, StoredItem item) {
            this.id = id;
            this.item = item;
        }
    }

    // 只更新搜索索引列(名字/词条), 不动物品本体(item/meta/nbt/count)。
    public void updateIndex(String player, long id, String name, String lore) {
        db.update("UPDATE entries SET name=?,lore=? WHERE player=? AND id=?", ps -> {
            ps.setString(1, name);
            ps.setString(2, lore);
            ps.setString(3, player);
            ps.setLong(4, id);
        });
    }

    // 取该玩家(+标签)全部条目, 按 SQL 排序; 关键词用 PinIn 在 Java 侧过滤; 再取 [offset, offset+limit) 窗口。
    // total = 匹配到的条目总数。单玩家条目量不大(几十~几百), 一次性载入无碍。
    public PageResult queryWindowWithIds(String player, String keyword, int offset, int limit, int sort, int tabId) {
        synchronized (db) {
            List<StoredItem> outItems = new java.util.ArrayList<StoredItem>();
            java.util.List<Long> outIds = new java.util.ArrayList<Long>();
            int matched = 0;
            String sql = "SELECT id,item,meta,nbt,nbt_hash,count,name,lore FROM entries WHERE " + whereTab(tabId)
                + orderByFor(sort, tabId);
            try (PreparedStatement ps = db.getConnection()
                .prepareStatement(sql)) {
                bindTab(ps, player, tabId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (!matchKeyword(rs.getString("name"), rs.getString("lore"), keyword)) continue;
                        if (matched >= offset && outItems.size() < limit) {
                            outIds.add(rs.getLong("id"));
                            outItems.add(read(rs));
                        }
                        matched++;
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            long[] ids = new long[outIds.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = outIds.get(i);
            return new PageResult(ids, outItems, matched);
        }
    }
}
