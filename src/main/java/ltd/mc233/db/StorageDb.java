package ltd.mc233.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class StorageDb {

    // —— 轻量查询助手(自研, 零依赖): 把"开语句/绑参/执行/关闭/异常"这套样板收进来, 调用方只写 SQL + 绑参 + 行映射。——
    public interface Binder {

        void bind(PreparedStatement ps) throws SQLException;
    }

    public interface RowMapper<T> {

        T map(ResultSet rs) throws SQLException;
    }

    // 执行写语句(INSERT/UPDATE/DELETE)。
    public synchronized void update(String sql, Binder b) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (b != null) b.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 查询多行 → List。
    public synchronized <T> List<T> query(String sql, Binder b, RowMapper<T> m) {
        List<T> out = new ArrayList<T>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (b != null) b.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(m.map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    // 查询首行 → 映射结果; 无行则返回 def。
    public synchronized <T> T queryOne(String sql, Binder b, RowMapper<T> m, T def) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (b != null) b.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? m.map(rs) : def;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private final Connection conn;

    public StorageDb(File dbFile) {
        try {
            // 用类字面量取驱动名: shade 重定位会把 org.sqlite.JDBC 改名到
            // ltd.mc233.shadow.org.sqlite.JDBC, 类引用会被一起改写而字符串字面量不会。
            // getName() 在运行期返回(可能已重定位的)真实类名, 再 forName 触发其静态块自注册驱动。
            // dev/单元测试下未重定位, 同样得到 org.sqlite.JDBC, 行为一致。
            Class.forName(org.sqlite.JDBC.class.getName());
            if (dbFile.getParentFile() != null) dbFile.getParentFile()
                .mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL"); // WAL 下的标准提速: 写入不再每次 fsync, 崩溃至多丢最后一次事务
                s.execute("PRAGMA busy_timeout=3000"); // 偶发锁等待而非立即报错
                s.execute(
                    "CREATE TABLE IF NOT EXISTS entries ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, player TEXT NOT NULL, item TEXT NOT NULL,"
                        + "meta INTEGER NOT NULL, nbt BLOB, nbt_hash TEXT NOT NULL, count INTEGER NOT NULL,"
                        + "name TEXT, lore TEXT,"
                        + "UNIQUE(player,item,meta,nbt_hash))");
                s.execute("CREATE INDEX IF NOT EXISTS idx_player_name ON entries(player,name)");
                // 全局键值设置(随存档): 目前存"新玩家默认起始容量"(defaultCapacity), 由 /storage cap default 设。
                s.execute("CREATE TABLE IF NOT EXISTS settings (k TEXT PRIMARY KEY, v TEXT)");
                // 迁移: 老库没有 tab 列(物品归属的标签, 0=未分类), 平滑加上。幂等。
                if (!hasColumn("entries", "tab")) {
                    s.execute("ALTER TABLE entries ADD COLUMN tab INTEGER NOT NULL DEFAULT 0");
                }
                // 迁移: 换成 PinIn 搜索后 pinyin/pinyin_in 已废弃, 从老库删掉瘦身(SQLite 3.35+ 支持 DROP COLUMN)。
                dropColumnIfExists(s, "pinyin");
                dropColumnIfExists(s, "pinyin_in");
            }
        } catch (Exception e) {
            throw new RuntimeException("open db failed", e);
        }
    }

    public Connection getConnection() {
        return conn;
    }

    private void dropColumnIfExists(Statement s, String col) {
        if (!hasColumn("entries", col)) return;
        try {
            s.execute("ALTER TABLE entries DROP COLUMN " + col);
        } catch (SQLException ignored) {
            // 老 SQLite 不支持 DROP COLUMN 也无妨: 该列只是不再被读写, 留着无害。
        }
    }

    private boolean hasColumn(String table, String col) {
        try (Statement s = conn.createStatement();
            java.sql.ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (col.equalsIgnoreCase(rs.getString("name"))) return true;
            }
        } catch (SQLException ignored) {}
        return false;
    }

    public void close() {
        // 关库前把 WAL 合并回主库并截断, 退出存档后基本只剩一个 storage.db, 看着清爽。
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException ignored) {}
        try {
            conn.close();
        } catch (SQLException ignored) {}
    }
}
