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
