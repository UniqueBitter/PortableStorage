package ltd.mc233.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;

import ltd.mc233.core.StoredItem;

public final class ItemStackCodec {

    private ItemStackCodec() {}

    public static StoredItem encode(ItemStack st, EntityPlayer player) {
        String item = (String) Item.itemRegistry.getNameForObject(st.getItem());
        if (item == null) throw new IllegalArgumentException("Item not in registry: " + st.getItem());
        int meta = st.getItemDamage();

        byte[] nbt = null;
        if (st.hasTagCompound()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                CompressedStreamTools.writeCompressed(st.getTagCompound(), baos);
                nbt = baos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String nbtHash = StoredItem.hashNbt(nbt);
        long count = st.stackSize;

        String rawName = st.getDisplayName();
        if (rawName == null) rawName = "";
        String cleanName = EnumChatFormatting.getTextWithoutFormattingCodes(rawName);
        if (cleanName == null) cleanName = "";
        String name = cleanName.toLowerCase();

        // 搜索索引用"完整提示框文字"(含附魔/属性/史诗/任务物品等), 而不仅是 display.Lore。
        // getTooltip 在服务端通常可用; 万一某 mod 的提示访问客户端字段崩了, 退回只读 display.Lore。
        String loreClean;
        try {
            List<?> tip = st.getTooltip(player, false);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < tip.size(); i++) { // 跳过第0行(名字, 已单独索引)
                sb.append(tip.get(i))
                    .append(' ');
            }
            loreClean = strip(sb.toString());
        } catch (Throwable t) {
            loreClean = strip(readDisplayLore(st));
        }
        String lore = loreClean.toLowerCase();

        // 拼音匹配由 PinIn 在搜索时直接对 名字+词条 做, 无需预存拼音。
        return new StoredItem(item, meta, nbt, nbtHash, count, name, lore);
    }

    private static String strip(String s) {
        if (s == null) return "";
        String c = EnumChatFormatting.getTextWithoutFormattingCodes(s);
        return c == null ? "" : c;
    }

    private static String readDisplayLore(ItemStack st) {
        if (!st.hasTagCompound()) return "";
        NBTTagCompound tag = st.getTagCompound();
        if (!tag.hasKey("display", 10)) return "";
        NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("Lore", 9)) return "";
        NBTTagList list = display.getTagList("Lore", 8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.tagCount(); i++) sb.append(list.getStringTagAt(i))
            .append(' ');
        return sb.toString();
    }

    public static ItemStack decode(StoredItem si, int amount) {
        Object o = Item.itemRegistry.getObject(si.getItem());
        if (!(o instanceof Item)) {
            // 对应 mod 已移除/改名, 无法还原该物品。返回 null, 调用方需自行处理(不可解引用)。
            return null;
        }
        Item it = (Item) o;
        ItemStack st = new ItemStack(it, amount, si.getMeta());
        if (si.getNbt() != null) {
            try {
                NBTTagCompound tag = CompressedStreamTools.readCompressed(new ByteArrayInputStream(si.getNbt()));
                st.setTagCompound(tag);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return st;
    }
}
