package ltd.mc233.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class MagnetRouterTest {

    @Test
    public void modeOffTakesNothing() {
        MagnetRouter.Split s = MagnetRouter.route(0, 10, 64);
        assertEquals(0, s.toInv);
        assertEquals(0, s.toStorage);
    }

    @Test
    public void modeInventoryFitsAll() {
        MagnetRouter.Split s = MagnetRouter.route(1, 10, 64);
        assertEquals(10, s.toInv);
        assertEquals(0, s.toStorage);
    }

    @Test
    public void modeInventoryOverflowsToStorage() {
        MagnetRouter.Split s = MagnetRouter.route(1, 10, 4);
        assertEquals(4, s.toInv);
        assertEquals(6, s.toStorage);
    }

    @Test
    public void modeStorageAllToStorage() {
        MagnetRouter.Split s = MagnetRouter.route(2, 10, 64);
        assertEquals(0, s.toInv);
        assertEquals(10, s.toStorage);
    }
}
