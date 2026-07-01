package ltd.mc233;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class Config {

    private Config() {}

    private static File file; // 记住配置文件, 供运行时改写(如搜索自动聚焦开关)。

    public static int magnetRadius = 32;
    public static boolean autoRestock = true;
    // 随身仓库起始容量(能存多少"种"物品)。默认 0: 新玩家从零开始, 靠任务/扩容券解锁。
    // 首次为玩家初始化时取 max(本值, 其当前种类数), 所以已有存档的老数据不会被锁死(按现有种类数起步)。
    public static int defaultCapacity = 0;
    // 正常途径(扩容券)能把容量提升到的上限。指令 /storage cap 可突破此上限。
    public static int maxCapacity = 999;
    // 栏位拓展器进入背包时是否自动使用(默认开)。关闭则改为右键手动使用。
    public static boolean autoConsumeVoucher = true;
    // 打开随身仓库界面时是否自动聚焦搜索框(可立即打字)。客户端偏好, 持久化到 config 文件(含重启)。
    public static boolean autoFocusSearch = true;

    private static final String CAT_CLIENT = "client";
    private static final String KEY_AUTOFOCUS = "autoFocusSearch";
    private static final String CMT_AUTOFOCUS = "打开随身仓库界面时自动聚焦搜索框, 可立即打字(在界面里点放大镜按钮也能切换)";
    private static final String CAT_STORAGE = "storage";
    private static final String KEY_AUTOVOUCHER = "autoConsumeVoucher";
    private static final String CMT_AUTOVOUCHER = "栏位拓展器进入背包时自动使用(默认开); 关闭则改为右键手动使用";

    public static int clampRadius(int v) {
        return Math.max(1, Math.min(128, v));
    }

    public static void load(File configFile) {
        file = configFile;
        Configuration c = new Configuration(configFile);
        c.load();
        magnetRadius = clampRadius(c.getInt("magnetRadius", "magnet", 32, 1, 128, "磁铁吸取半径(格), 1-128"));
        autoRestock = c.getBoolean("autoRestock", "general", true, "快捷栏物品用尽时, 自动从随身仓库补充同种物品(打开仓库界面时不触发)");
        defaultCapacity = c
            .getInt("defaultCapacity", "storage", 0, 0, 100000, "随身仓库起始容量(能存多少种物品), 默认0=从零解锁; 老存档会取 max(本值, 现有种类数)");
        maxCapacity = c.getInt("maxCapacity", "storage", 999, 1, 100000, "扩容券等正常途径能提升到的容量上限; 指令可突破");
        autoConsumeVoucher = c.getBoolean(KEY_AUTOVOUCHER, CAT_STORAGE, true, CMT_AUTOVOUCHER);
        autoFocusSearch = c.getBoolean(KEY_AUTOFOCUS, CAT_CLIENT, true, CMT_AUTOFOCUS);
        if (c.hasChanged()) {
            c.save();
        }
    }

    // 运行时切换"扩容券进背包自动使用", 并立即写回 config 文件(重启后仍记住)。
    public static void setAutoConsumeVoucher(boolean v) {
        autoConsumeVoucher = v;
        if (file == null) return;
        Configuration c = new Configuration(file);
        c.load();
        c.get(CAT_STORAGE, KEY_AUTOVOUCHER, true, CMT_AUTOVOUCHER)
            .set(v);
        if (c.hasChanged()) c.save();
    }

    // 运行时切换搜索自动聚焦, 并立即写回 config 文件, 使其在重启后仍然记住。
    public static void setAutoFocusSearch(boolean v) {
        autoFocusSearch = v;
        if (file == null) return;
        Configuration c = new Configuration(file);
        c.load();
        c.get(CAT_CLIENT, KEY_AUTOFOCUS, true, CMT_AUTOFOCUS)
            .set(v);
        if (c.hasChanged()) {
            c.save();
        }
    }
}
