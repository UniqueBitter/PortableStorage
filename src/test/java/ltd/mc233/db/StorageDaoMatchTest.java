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
                return it.getItem()
                    .equals(name);
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
