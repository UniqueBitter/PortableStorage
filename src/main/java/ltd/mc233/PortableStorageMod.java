package ltd.mc233;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import ltd.mc233.item.ItemPortableTerminal;
import ltd.mc233.net.NetworkHandler;
import ltd.mc233.proxy.CommonProxy;

@Mod(
    modid = PortableStorageMod.MODID,
    version = PortableStorageMod.VERSION,
    name = "随身仓库",
    acceptedMinecraftVersions = "[1.7.10]")
public class PortableStorageMod {

    public static final String MODID = "portablestorage";
    public static final String VERSION = "1.0.0";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(PortableStorageMod.MODID)
    public static PortableStorageMod instance;

    @SidedProxy(clientSide = "ltd.mc233.proxy.ClientProxy", serverSide = "ltd.mc233.proxy.CommonProxy")
    public static CommonProxy proxy;

    public static ItemPortableTerminal terminal;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 把 sqlite 原生库解压到游戏目录下的可写路径, 避免系统临时目录权限问题。
        java.io.File nativeDir = new java.io.File(
            event.getModConfigurationDirectory()
                .getParentFile(),
            "portablestorage_native");
        nativeDir.mkdirs();
        System.setProperty("org.sqlite.tmpdir", nativeDir.getAbsolutePath());
        Config.load(event.getSuggestedConfigurationFile());
        terminal = new ItemPortableTerminal();
        GameRegistry.registerItem(terminal, "terminal");
        GameRegistry.registerItem(new ltd.mc233.item.ItemCapVoucher(), "cap_voucher");
        LOG.info("PortableStorage preInit (随身仓库) version " + VERSION);
    }

    @Mod.EventHandler
    public void serverStarting(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        // 所有玩家命令 = ModCommands 里带 @Cmd 的方法, 一句注册全部。
        ltd.mc233.command.AnnotatedCommand.registerAll(event, new ModCommands());
        // /storage cap(需控制台/指定他人/子命令)保留独立类。
        event.registerServerCommand(new StorageCapCommand());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkHandler.register();
        cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(instance, new GuiHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new StorageProvider());
        GodHandler god = new GodHandler();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(god); // LivingAttackEvent
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(god); // PlayerTickEvent(清负面buff)
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new ltd.mc233.magnet.MagnetManager());
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new ltd.mc233.restock.RestockManager());
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new VoucherHandler()); // 栏位拓展器进背包自动使用(Config 可关)
        preloadTickClasses();
        LOG.info("PortableStorage init");
        proxy.init();
    }

    // 在 init(非玩家tick上下文)里预加载磁铁/补充tick会用到的类。否则它们在玩家tick里首次懒加载时,
    // 会触发 InventoryTweaks 的 ASM ContainerTransformer 在该上下文 NPE → NoClassDefFoundError 崩服。
    private void preloadTickClasses() {
        String[] cls = { "ltd.mc233.core.MagnetRouter", "ltd.mc233.core.MagnetRouter$Split",
            "ltd.mc233.core.StoredItem", "ltd.mc233.core.PinInUtil", "ltd.mc233.item.ItemStackCodec",
            "ltd.mc233.db.StorageDb", "ltd.mc233.db.StorageDao", "ltd.mc233.StorageProvider",
            "ltd.mc233.StorageService", "ltd.mc233.core.DeductPlan",
            // 拼音库(PinIn): 必须在 init 阶段预加载, 否则在服务端 tick 内首次懒加载会触发 InvTweaks ASM 变换器 NPE → 崩溃。
            "me.towdium.pinin.PinIn" };
        for (String c : cls) {
            try {
                Class.forName(c, true, getClass().getClassLoader());
            } catch (Throwable t) {
                LOG.warn("预加载类失败: " + c, t);
            }
        }
        // 实际跑一遍拼音匹配, 把 PinIn 相关类在 init 阶段全部加载好。
        ltd.mc233.core.PinInUtil.preload();
        // StorageItemSource 引用 noppes.*: 只有装了 CNPC 才预加载, 否则会 NoClassDefFoundError。
        if (ltd.mc233.compat.CnpcCompat.LOADED) {
            try {
                Class.forName("ltd.mc233.compat.StorageItemSource", true, getClass().getClassLoader());
            } catch (Throwable t) {
                LOG.warn("预加载 StorageItemSource 失败", t);
            }
        }
    }
}
