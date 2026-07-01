package ltd.mc233.db;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ltd.mc233.core.StoredItem;

public class StorageDaoTest {

    File f;
    StorageDb db;
    StorageDao dao;

    @Before
    public void setup() throws Exception {
        f = File.createTempFile("ps-test", ".db");
        f.delete();
        db = new StorageDb(f);
        dao = new StorageDao(db);
    }

    @After
    public void teardown() {
        db.close();
        f.delete();
    }

    private StoredItem di(long n) {
        return new StoredItem("minecraft:diamond", 0, null, "-", n, "钻石", "");
    }

    @Test
    public void upsertMergesSameKey() {
        dao.upsert("p1", di(5));
        dao.upsert("p1", di(3));
        assertEquals(8, dao.countOf("p1", "minecraft:diamond", 0, "-"));
    }

    @Test
    public void differentNbtHashAreSeparate() {
        dao.upsert("p1", new StoredItem("minecraft:sword", 0, new byte[] { 1 }, "aaa", 1, "剑", ""));
        dao.upsert("p1", new StoredItem("minecraft:sword", 0, new byte[] { 2 }, "bbb", 1, "剑", ""));
        assertEquals(1, dao.countOf("p1", "minecraft:sword", 0, "aaa"));
        assertEquals(1, dao.countOf("p1", "minecraft:sword", 0, "bbb"));
    }

    @Test
    public void batchInsert() {
        dao.upsertBatch("p1", Arrays.asList(di(2), di(2), di(2)));
        assertEquals(6, dao.countOf("p1", "minecraft:diamond", 0, "-"));
    }

    @Test
    public void extractDecrementsAndDeletesAtZero() {
        dao.upsert("p1", di(10));
        long id = dao.entryId("p1", "minecraft:diamond", 0, "-");
        assertEquals(4, dao.extract("p1", id, 4));
        assertEquals(6, dao.countOf("p1", "minecraft:diamond", 0, "-"));
        assertEquals(6, dao.extract("p1", id, 100));
        assertEquals(0, dao.countOf("p1", "minecraft:diamond", 0, "-"));
        assertEquals(-1, dao.entryId("p1", "minecraft:diamond", 0, "-"));
    }

    @Test
    public void searchMatchesNameLorePinyin() {
        dao.upsert("p1", new StoredItem("minecraft:diamond", 0, null, "-", 1, "钻石", "稀有 矿物"));
        assertEquals(1, dao.countMatches("p1", "钻"));
        assertEquals(1, dao.countMatches("p1", "zuanshi"));
        assertEquals(1, dao.countMatches("p1", "zs"));
        assertEquals(1, dao.countMatches("p1", "矿物"));
        assertEquals(0, dao.countMatches("p1", "apple"));
    }

    @Test
    public void windowPaginates() {
        for (int i = 0; i < 5; i++) dao.upsert("p1", new StoredItem("mod:i" + i, 0, null, "-", 1, "物" + i, ""));
        assertEquals(5, dao.countMatches("p1", ""));
        assertEquals(
            2,
            dao.queryWindow("p1", "", 0, 2)
                .size());
        assertEquals(
            1,
            dao.queryWindow("p1", "", 4, 2)
                .size());
    }
}
