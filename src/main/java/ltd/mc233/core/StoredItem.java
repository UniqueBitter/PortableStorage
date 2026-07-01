package ltd.mc233.core;

import java.security.MessageDigest;

public final class StoredItem {

    private final String item;
    private final int meta;
    private final byte[] nbt;
    private final String nbtHash;
    private final long count;
    private final String name, lore;

    public StoredItem(String item, int meta, byte[] nbt, String nbtHash, long count, String name, String lore) {
        this.item = item;
        this.meta = meta;
        this.nbt = nbt;
        this.nbtHash = nbtHash;
        this.count = count;
        this.name = name;
        this.lore = lore;
    }

    public String getItem() {
        return item;
    }

    public int getMeta() {
        return meta;
    }

    public byte[] getNbt() {
        return nbt;
    }

    public String getNbtHash() {
        return nbtHash;
    }

    public long getCount() {
        return count;
    }

    public String getName() {
        return name;
    }

    public String getLore() {
        return lore;
    }

    public StoredItem withCount(long c) {
        return new StoredItem(item, meta, nbt, nbtHash, c, name, lore);
    }

    public static String hashNbt(byte[] nbt) {
        if (nbt == null || nbt.length == 0) return "-";
        try {
            byte[] d = MessageDigest.getInstance("SHA-1")
                .digest(nbt);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                .append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
