package ltd.mc233.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class StoredItemTest {

    @Test
    public void nbtHashStableAndDistinct() {
        assertEquals("-", StoredItem.hashNbt(null));
        assertEquals("-", StoredItem.hashNbt(new byte[0]));
        String h1 = StoredItem.hashNbt(new byte[] { 1, 2, 3 });
        String h2 = StoredItem.hashNbt(new byte[] { 1, 2, 3 });
        String h3 = StoredItem.hashNbt(new byte[] { 9, 9, 9 });
        assertEquals(h1, h2);
        assertNotEquals(h1, h3);
    }

    @Test
    public void withCountCopies() {
        StoredItem a = new StoredItem("minecraft:diamond", 0, null, "-", 5, "钻石", "");
        StoredItem b = a.withCount(99);
        assertEquals(5, a.getCount());
        assertEquals(99, b.getCount());
        assertEquals("minecraft:diamond", b.getItem());
    }
}
