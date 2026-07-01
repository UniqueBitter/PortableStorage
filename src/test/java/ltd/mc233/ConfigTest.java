package ltd.mc233;

import static org.junit.Assert.*;

import org.junit.Test;

public class ConfigTest {

    @Test
    public void clamps() {
        assertEquals(1, Config.clampRadius(0));
        assertEquals(1, Config.clampRadius(-5));
        assertEquals(8, Config.clampRadius(8));
        assertEquals(128, Config.clampRadius(128));
        assertEquals(128, Config.clampRadius(999));
    }
}
