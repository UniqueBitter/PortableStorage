package ltd.mc233.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import ltd.mc233.Config;
import ltd.mc233.net.NetworkHandler;
import ltd.mc233.net.PageResultPacket;

public class GuiPortableStorage extends GuiContainer {

    public static final int COLS = 9;
    public static final int VIS_ROWS = 6; // 与 generic_54 箱子一致: 6 行
    public static final int WINDOW = 108;
    private static final int SLOT = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18; // generic_54 箱子首格 y=18
    private static final int BTN_W = 11;
    private static final int BTN_GAP = 13; // 相邻按钮的纵向间距(按钮高 + 2px 缝)
    // 仓库背景贴图(取自整合包的 generic_54 木质箱子皮肤, 256x256, GUI 区 176x222)。
    private static final net.minecraft.util.ResourceLocation BG = new net.minecraft.util.ResourceLocation(
        "portablestorage",
        "textures/gui/storage.png");
    // 木质配色(与背景贴图呼应): 用于标签/滚动条等自绘控件。
    private static final int WOOD_LIGHT = 0xFFCBA76A; // 高光
    private static final int WOOD_FILL = 0xFF9A6A38; // 常态填充
    private static final int WOOD_DARK = 0xFF4E3216; // 阴影
    private static final int WOOD_GOLD = 0xFFE8C36A; // 激活/高亮
    private static final int WOOD_TRACK = 0xFF6B4A28; // 滚动条槽内

    private static volatile PageResultPacket pending;

    private GuiTextField search;
    private int rowOffset = 0;
    private long total = 0;
    private long[] ids = new long[0];
    private String[] items = new String[0];
    private int[] metas = new int[0];
    private long[] counts = new long[0];
    private String[] names = new String[0];
    private byte[][] nbts = new byte[0][];
    private ItemStack[] stackCache = new ItemStack[0]; // 收到数据包时构建一次, 供每帧渲染/tooltip 复用
    private long lockMask = 0;
    private String keyword = "";
    private int sortBy = 0; // 0名字 1数量 2类型 3时间
    private boolean sortDesc = false; // false升序 true降序
    private boolean sortAdopted = false;
    private boolean restockOn = true;
    private int capUsed = 0;
    private int capTotal = 0;
    // 当前标签由服务端持久化(psLastTab)统一记忆; 客户端不自存, 开界面时发 -2 向服务端要记忆值, 收到即回填。
    private int curTab = -2; // -2=向服务端要记忆的标签; -1=全部, 0=未分类, >=1=自定义
    // "全部"格的子模式偏好(纯客户端 UI 状态): -1=全部, 0=未分类。右键切换; 访问其他标签后再看"全部"格仍保留此模式(会话内)。
    private static int allMode = -1;
    private int[] tabIds = new int[0];
    private String[] tabNames = new String[0];
    private int[] tabOrderCache = { -1 }; // 标签显示顺序[全部,自定义...], 仅收包(tabIds 变化)时重建, 避免每帧分配数组
    private String[] countLabels = new String[0]; // 每格数量的缩写文本, 收包时算好, 避免每帧 String.format
    private GuiTextField renameField; // 右键标签改名 / 中键标签改序号 共用的行内输入框
    private int renamingTab = 0; // 0=未在编辑; >=1=正在编辑的标签id
    private boolean reorderMode = false; // true=正在输入序号(而非改名)
    private int renameX, renameY;
    private static final int RENAME_W = 140, RENAME_H = 20;
    private IconButton stackBtn;
    // 收纳模式: false=全部收纳(非锁定的都收), true=仅补充仓库已有种类(泰拉瑞亚式, 不收快捷栏)。右键按钮切换。
    // 会话内保留(static): 关掉界面再开仍是上次选的模式。
    private static boolean stackMode = false;
    private IconButton sortByBtn;
    private IconButton dirBtn;
    private IconButton searchBtn;
    private IconButton restockBtn;
    private boolean draggingBar = false;
    private boolean draggingWithdraw = false; // Shift+左键在仓库里划过批量取出: 取出的格先空着, 松手/松 Shift 才刷新重排
    private int dragRow = -1; // 拖动中客户端预测的目标行(让滑块跟手, 不被异步数据包回弹)
    private long lastScrollMs = 0; // 拖动时的请求节流
    private long lastTypeMs = 0;
    private final RenderItem renderItem = new RenderItem();

    public GuiPortableStorage(Container c) {
        super(c);
        this.xSize = 176; // 与 generic_54 箱子 GUI 区一致
        this.ySize = 222;
    }

    public static void deliver(PageResultPacket p) {
        pending = p;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.search = new GuiTextField(this.fontRendererObj, this.guiLeft + 64, this.guiTop + 6, 106, 12);
        this.search.setFocused(Config.autoFocusSearch);
        this.search.setCanLoseFocus(true);
        // 一列悬浮图标按钮在面板左侧外缘。
        int bx = this.guiLeft - BTN_W - 2;
        this.buttonList.clear();
        int by = this.guiTop + 4;
        this.stackBtn = new IconButton(1, bx, by, BTN_W, 0);
        this.stackBtn.tip = () -> (this.stackMode ? "§a匹配已有物品" : "§c全部收纳") + "\n§7左键收纳,右键切换模式";
        this.sortByBtn = new IconButton(2, bx, by + BTN_GAP, BTN_W, sortByIcon());
        this.sortByBtn.tip = () -> "排序: "
            + (this.sortBy == 1 ? "数量" : this.sortBy == 2 ? "类型" : this.sortBy == 3 ? "时间" : "名字");
        this.dirBtn = new IconButton(3, bx, by + BTN_GAP * 2, BTN_W, dirIcon());
        this.dirBtn.tip = () -> this.sortDesc ? "降序" : "升序";
        this.searchBtn = new IconButton(4, bx, by + BTN_GAP * 3, BTN_W, searchIcon());
        this.searchBtn.tip = () -> Config.autoFocusSearch ? "自动聚焦:开" : "自动聚焦:关";
        this.restockBtn = new IconButton(5, bx, by + BTN_GAP * 4, BTN_W, restockIcon());
        this.restockBtn.tip = () -> this.restockOn ? "自动补货: 开(只补中键锁定的格)" : "自动补货: 关";
        this.buttonList.add(this.stackBtn);
        this.buttonList.add(this.sortByBtn);
        this.buttonList.add(this.dirBtn);
        this.buttonList.add(this.searchBtn);
        this.buttonList.add(this.restockBtn);
        requestWindow(0);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    private int encodeSort() {
        return this.sortBy * 2 + (this.sortDesc ? 1 : 0);
    }

    private int sortByIcon() {
        return this.sortBy == 1 ? 2 : this.sortBy == 2 ? 3 : this.sortBy == 3 ? 10 : 1; // 名/数/类/时间
    }

    private int dirIcon() {
        return this.sortDesc ? 5 : 4; // 升/降
    }

    private int searchIcon() {
        return Config.autoFocusSearch ? 6 : 7;
    }

    private int restockIcon() {
        return this.restockOn ? 8 : 9;
    }

    private void updateButtons() {
        if (this.stackBtn != null) {
            // 全部收纳=原色白; 仅补充已有=绿色(呼应"仓库已有的绿字物品才收")。
            this.stackBtn.tint = this.stackMode ? 0xFF6CE06C : 0xFFFFFFFF;
        }
        if (this.sortByBtn != null) this.sortByBtn.icon = sortByIcon();
        if (this.dirBtn != null) this.dirBtn.icon = dirIcon();
        if (this.searchBtn != null) this.searchBtn.icon = searchIcon();
        if (this.restockBtn != null) this.restockBtn.icon = restockIcon();
    }

    private void requestWindow(int rowOff) {
        int maxRow = maxRowOffset();
        if (rowOff < 0) rowOff = 0;
        if (rowOff > maxRow) rowOff = maxRow;
        this.rowOffset = rowOff;
        NetworkHandler
            .requestPage(this.keyword, rowOff * COLS, WINDOW, this.sortAdopted ? encodeSort() : -1, this.curTab);
    }

    private int maxRowOffset() {
        int rows = (int) Math.ceil(this.total / (double) COLS);
        int m = rows - VIS_ROWS;
        return m < 0 ? 0 : m;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.renameField != null) this.renameField.updateCursorCounter();
        PageResultPacket p = pending;
        // 拖动批量取出期间冻结刷新: 已取出的格保持空着, 松手后由 endWithdrawDrag 统一重排。
        if (p != null && !this.draggingWithdraw) {
            pending = null;
            this.total = p.total;
            this.ids = p.ids;
            this.items = p.items;
            this.metas = p.metas;
            this.counts = p.counts;
            this.names = p.names;
            this.nbts = p.nbts;
            this.lockMask = p.lockMask;
            this.restockOn = p.restock;
            this.stackMode = p.stackMatch; // 收纳模式由服务端记住, 开箱即恢复上次的选择
            this.capUsed = p.capUsed;
            this.capTotal = p.capTotal;
            this.curTab = p.curTab;
            if (p.curTab <= 0) allMode = p.curTab; // 处于"全部/未分类"时同步"全部"格的子模式偏好
            this.tabIds = p.tabIds != null ? p.tabIds : new int[0];
            this.tabNames = p.tabNames != null ? p.tabNames : new String[0];
            rebuildTabOrder();
            if (!this.draggingBar) this.rowOffset = p.offset / COLS; // 拖动中不让异步包回弹滑块
            this.sortBy = p.sort / 2;
            this.sortDesc = (p.sort % 2 == 1);
            this.sortAdopted = true;
            updateButtons();
            // 一次性把本页物品栈 + 数量缩写构建好并缓存(含 NBT 解压), 之后每帧直接取用。
            this.stackCache = new ItemStack[this.ids.length];
            this.countLabels = new String[this.ids.length];
            for (int i = 0; i < this.stackCache.length; i++) {
                this.stackCache[i] = buildStackRaw(i);
                this.countLabels[i] = abbrev(this.counts[i]);
            }
        }
        // 输入防抖后搜索(随输入更新结果)。
        if (this.lastTypeMs != 0 && System.currentTimeMillis() - this.lastTypeMs > 150) {
            this.lastTypeMs = 0;
            requestWindow(0);
        }
    }

    @Override
    protected void keyTyped(char ch, int key) {
        // 改名/改序号模式: 回车确认, Esc 取消, 其余交给输入框。改序号时只收数字。
        if (this.renamingTab >= 1) {
            if (key == Keyboard.KEY_RETURN) endRename(true);
            else if (key == Keyboard.KEY_ESCAPE) endRename(false);
            else if (this.renameField != null) {
                if (this.reorderMode && ch >= ' ' && !Character.isDigit(ch)) return; // 序号只允许数字
                this.renameField.textboxKeyTyped(ch, key);
            }
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            super.keyTyped(ch, key);
            return;
        }
        // 回车: 手动模式下执行搜索。
        if (key == Keyboard.KEY_RETURN) {
            this.keyword = this.search.getText()
                .toLowerCase();
            this.lastTypeMs = 0;
            requestWindow(0);
            return;
        }
        if (this.search.textboxKeyTyped(ch, key)) {
            String t = this.search.getText()
                .toLowerCase();
            if (!t.equals(this.keyword)) {
                this.keyword = t;
                this.lastTypeMs = System.currentTimeMillis();
            }
        } else {
            super.keyTyped(ch, key);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            requestWindow(this.rowOffset + (dw > 0 ? -1 : 1));
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        // 改名输入框激活时: 框内点击交给输入框, 框外点击=确认并关闭。
        if (this.renamingTab >= 1) {
            if (inRenameBox(mx, my)) {
                this.renameField.mouseClicked(mx, my, btn);
                return;
            }
            endRename(true);
            return;
        }
        // 右侧标签列点击
        if (handleTabColumnClick(mx, my, btn)) return;
        // 右键收纳按钮: 切换收纳模式(全部 ↔ 仅补充已有), 不执行。
        if (btn == 1 && over(this.stackBtn, mx, my)) {
            sendGridAction(6, 0L); // 切换收纳模式(服务端持久化, 回包会刷新按钮显示)
            playClick();
            return;
        }
        // 中键: 锁定/解锁鼠标下的背包格(锁定的不被一键收纳收走)。
        if (btn == 2) {
            Slot s = slotUnderMouse(mx, my);
            if (s != null) {
                NetworkHandler.lockSlot(s.getSlotIndex());
                return;
            }
        }
        // 左键点滚动条 → 跳到该位置并开始拖动
        if (btn == 0 && inScrollbar(mx, my)) {
            this.draggingBar = true;
            scrollToMouse(my);
            return;
        }
        super.mouseClicked(mx, my, btn);
        if (this.search != null) this.search.mouseClicked(mx, my, btn);
        int idx = gridIndexAt(mx, my);
        if (idx < 0) return;
        ItemStack held = this.mc.thePlayer.inventory.getItemStack();
        boolean onItem = idx < this.ids.length;
        // 右键点仓库物品: 始终"取一个到光标"。可连续右键不断累加同种(手上已拿同种则 +1, 异种则不动),
        // 不再因为手上有东西就改成"存回去", 解决"取一个放一个"的来回切换。
        if (btn == 1 && onItem) {
            sendGridAction(1, this.ids[idx]);
            return;
        }
        // 左键且手上拿着东西 → 整组存入仓库。
        if (held != null) {
            if (btn == 0) sendGridAction(3, 0L);
            return;
        }
        if (btn == 0 && onItem) {
            if (isShiftKeyDown()) { // Shift+左键: 取出到背包, 并进入"划过批量取出"模式
                this.draggingWithdraw = true;
                withdrawAt(idx);
            } else { // 空手左键 → 取一组到光标
                sendGridAction(0, this.ids[idx]);
            }
        }
    }

    // 鼠标在仓库网格上对应的物品下标; 不在网格上/超出返回 -1。
    private int gridIndexAt(int mx, int my) {
        int relX = mx - this.guiLeft - GRID_X;
        int relY = my - this.guiTop - GRID_Y;
        if (relX < 0 || relY < 0) return -1;
        int col = relX / SLOT, row = relY / SLOT;
        if (col >= COLS || row >= VIS_ROWS) return -1;
        return row * COLS + col;
    }

    // 取出该格物品到背包, 并把该格本地置空(拖动中冻结刷新, 保持空着)。已空/越界则跳过, 避免重复取。
    private void withdrawAt(int idx) {
        if (idx < 0 || idx >= this.stackCache.length || this.stackCache[idx] == null) return;
        sendGridAction(2, this.ids[idx]);
        this.stackCache[idx] = null;
    }

    // 结束批量取出: 解除冻结, 丢弃拖动中攒下的刷新, 按当前排序统一重排。
    private void endWithdrawDrag() {
        if (!this.draggingWithdraw) return;
        this.draggingWithdraw = false;
        pending = null;
        requestWindow(this.rowOffset);
    }

    // 处理右侧标签列的点击。命中并处理返回 true; 没点在标签列上返回 false(交给后续逻辑)。
    private boolean handleTabColumnClick(int mx, int my, int btn) {
        int tabHit = hitTabSlot(mx, my);
        if (tabHit == Integer.MIN_VALUE) return false;
        int[] order = tabOrder();
        if (tabHit == order.length) { // + 新建
            if (btn == 0) NetworkHandler.sendTabAction(0, 0, null);
            return true;
        }
        int tid = order[tabHit];
        boolean holding = this.mc.thePlayer.inventory.getItemStack() != null;
        if (btn == 2) { // 中键: 自定义标签 → 输入序号改排序
            if (tid >= 1) beginReorder(tid, tabHit);
            return true;
        }
        if (btn == 1) { // 右键
            if (tid == -1) { // "全部"格右键: 切换其子模式(全部 ↔ 未分类)并切过去; 子模式会话内保留
                if (!holding) {
                    allMode = (allMode == 0 ? -1 : 0);
                    switchTab(allMode);
                }
            } else if (tid >= 1) { // 自定义标签: Shift 删除, 否则改名
                if (isShiftKeyDown()) NetworkHandler.sendTabAction(1, tid, null);
                else beginRename(tid, tabHit);
            }
            return true;
        }
        if (btn == 0) {
            if (holding) NetworkHandler.sendTabAction(3, tid, null); // 手持物品 → 归类到该标签(点"全部"=取消归类)
            else switchTab(tid == -1 ? allMode : tid); // 左键: "全部"格去其当前子模式, 其余去该标签
        }
        return true;
    }

    // 切到某标签并请求数据。服务端会把它写进 psLastTab, 成为唯一的"当前标签"记忆。
    private void switchTab(int tab) {
        this.curTab = tab;
        requestWindow(0);
    }

    private void playClick() {
        this.mc.getSoundHandler()
            .playSound(
                net.minecraft.client.audio.PositionedSoundRecord
                    .func_147674_a(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long timeSinceClick) {
        super.mouseClickMove(mx, my, btn, timeSinceClick);
        if (this.draggingBar) {
            scrollToMouse(my);
            return;
        }
        if (this.draggingWithdraw) {
            if (btn == 0 && isShiftKeyDown()) withdrawAt(gridIndexAt(mx, my)); // 划过谁取谁
            else endWithdrawDrag(); // 松开 Shift → 结束并重排
        }
    }

    @Override
    protected void mouseMovedOrUp(int mx, int my, int which) {
        super.mouseMovedOrUp(mx, my, which);
        if (which == 0) {
            // 松手时补一发最终位置(拖动中因节流可能没请求到位)
            if (this.draggingBar && this.dragRow >= 0 && this.dragRow != this.rowOffset) requestWindow(this.dragRow);
            this.draggingBar = false;
            this.dragRow = -1;
            endWithdrawDrag(); // 松开左键 → 结束批量取出并重排
        }
    }

    // 滚动条 X: 贴图是定宽 176 的箱子, 面板内没位置, 放到面板右外缘。
    private int scrollBarX() {
        return this.guiLeft + this.xSize + 2;
    }

    private boolean inScrollbar(int mx, int my) {
        int barX = scrollBarX();
        int barTop = this.guiTop + GRID_Y;
        return inRect(mx, my, barX, barTop, 10, VIS_ROWS * SLOT);
    }

    private void scrollToMouse(int my) {
        int maxRow = maxRowOffset();
        if (maxRow <= 0) return;
        int barTop = this.guiTop + GRID_Y;
        int barH = VIS_ROWS * SLOT;
        double frac = (my - barTop - 8) / (double) (barH - 16);
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        int row = (int) Math.round(frac * maxRow);
        this.dragRow = row; // 滑块立即跟到这里(客户端预测)
        // 节流: 快速拖动时不逐行狂发请求(约 60ms 一次), 松手时再补最终位置。
        long now = System.currentTimeMillis();
        if (row != this.rowOffset && now - this.lastScrollMs >= 60) {
            this.lastScrollMs = now;
            requestWindow(row);
        }
    }

    // 点 (mx,my) 是否落在矩形 [x, x+w) × [y, y+h) 内。各处命中判定共用。
    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // 鼠标是否压在某个(可见)按钮上。
    private static boolean over(GuiButton b, int mx, int my) {
        return b != null && b.visible && inRect(mx, my, b.xPosition, b.yPosition, b.width, b.height);
    }

    // 仓库物品操作统一入口: 带上当前搜索词与页偏移, 免得每处都重复这串尾参。
    private void sendGridAction(int action, long id) {
        NetworkHandler.sendAction(action, id, 0, this.keyword, this.rowOffset * COLS);
    }

    private Slot slotUnderMouse(int mx, int my) {
        for (int i = 0; i < this.inventorySlots.inventorySlots.size(); i++) {
            Slot s = (Slot) this.inventorySlots.inventorySlots.get(i);
            if (inRect(mx, my, this.guiLeft + s.xDisplayPosition, this.guiTop + s.yDisplayPosition, 16, 16)) return s;
        }
        return null;
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) {
            // 左键执行当前模式: 全部收纳(4) 或 仅补充已有(5)。右键切换模式在 mouseClicked 处理。
            sendGridAction(this.stackMode ? 5 : 4, 0L);
        } else if (b.id == 2) {
            this.sortBy = (this.sortBy + 1) % 4;
            updateButtons();
            requestWindow(0);
        } else if (b.id == 3) {
            this.sortDesc = !this.sortDesc;
            updateButtons();
            requestWindow(0);
        } else if (b.id == 4) {
            Config.setAutoFocusSearch(!Config.autoFocusSearch); // 切换并写回 config 文件(重启后记住)
            if (this.search != null) this.search.setFocused(Config.autoFocusSearch);
            updateButtons();
        } else if (b.id == 5) {
            this.restockOn = !this.restockOn; // 乐观更新, 服务端会回传确认
            updateButtons();
            NetworkHandler.toggleRestock();
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float pt, int mx, int my) {
        drawDefaultBackground();
        int l = this.guiLeft;
        int t = this.guiTop;
        // 木质箱子贴图作背景(自带仓库格 + 玩家背包格), 所以不再自绘面板/格子。
        GL11.glColor4f(1F, 1F, 1F, 1F);
        this.mc.getTextureManager()
            .bindTexture(BG);
        drawTexturedModalRect(l, t, 0, 0, this.xSize, this.ySize);
        if (this.search != null) this.search.drawTextBox();
        // 滚动条画在面板右外缘(贴图定宽, 里面没位置)。
        int barX = scrollBarX();
        int barTop = t + GRID_Y;
        int barH = VIS_ROWS * SLOT;
        drawRect(barX, barTop, barX + 10, barTop + barH, WOOD_DARK); // 槽边
        drawRect(barX + 1, barTop + 1, barX + 9, barTop + barH - 1, WOOD_TRACK); // 槽内
        int maxRow = maxRowOffset();
        // 拖动中用客户端预测行画滑块 → 跟手不回弹; 平时用服务端确认的 rowOffset。
        int shownRow = (this.draggingBar && this.dragRow >= 0) ? this.dragRow : this.rowOffset;
        int handleY = barTop + (maxRow == 0 ? 0 : (int) ((barH - 16) * (shownRow / (double) maxRow)));
        drawRect(barX, handleY, barX + 10, handleY + 16, WOOD_GOLD); // 滑块(木金)
        drawRect(barX, handleY, barX + 10, handleY + 1, WOOD_LIGHT);
        drawRect(barX, handleY, barX + 1, handleY + 16, WOOD_LIGHT);
        drawRect(barX + 9, handleY, barX + 10, handleY + 16, WOOD_DARK);
        drawRect(barX, handleY + 15, barX + 10, handleY + 16, WOOD_DARK);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        this.fontRendererObj.drawString("随身仓库", 8, 6, 0xFFFFFFFF); // 纯白, 不加阴影(阴影会发灰)
        // 容量计数在仓库格与背包之间的空隙: 无限=显示已存种类; 有限=显示还能存多少。小一号白字, 满了标红。
        // capTotal < 0 = 无限容量模式(哨兵), 显示"无限"; 否则显示剩余可存种类数。
        String cap = this.capTotal < 0 ? "还可存 无限" : ("还可存 " + Math.max(0, this.capTotal - this.capUsed) + " 种");
        int capColor = (this.capTotal >= 0 && this.capUsed >= this.capTotal) ? 0xFFFF5555 : 0xFFFFFFFF;
        GL11.glPushMatrix();
        GL11.glScalef(0.75F, 0.75F, 1F); // 小一号
        this.fontRendererObj.drawString(cap, (int) (8 / 0.75F), (int) (130 / 0.75F), capColor);
        GL11.glPopMatrix();
        int n = Math.min(this.ids.length, VIS_ROWS * COLS);
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < n; i++) {
            int x = GRID_X + (i % COLS) * SLOT;
            int y = GRID_Y + (i / COLS) * SLOT;
            ItemStack stack = buildStack(i);
            if (stack != null) {
                this.renderItem
                    .renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, x, y);
                this.renderItem.renderItemOverlayIntoGUI(
                    this.fontRendererObj,
                    this.mc.getTextureManager(),
                    stack,
                    x,
                    y,
                    this.countLabels[i]);
            }
        }
        RenderHelper.disableStandardItemLighting();
        // 锁定标记(金色边框): 先关深度测试, 否则会被更高 zLevel 渲染的物品图标(如药水瓶)挡住。
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < this.inventorySlots.inventorySlots.size(); i++) {
            Slot s = (Slot) this.inventorySlots.inventorySlots.get(i);
            if ((this.lockMask & (1L << s.getSlotIndex())) != 0) drawLockMarker(s.xDisplayPosition, s.yDisplayPosition);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        // drawRect 会把 GL 颜色留在金色。原版随后(本方法返回后)渲染"鼠标光标上拿着的物品"时不会重置颜色,
        // 于是该物品图标会继承这个金色+alpha, 看起来发虚/透明。这里还原成不透明白色, 避免污染后续渲染。
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        // 仓库格悬停 tooltip 移到 drawScreen 末尾画(见 drawHoveredStorageTooltip), 以盖在标签列等 UI 之上。
    }

    // 仓库格悬停 → 物品 tooltip。在 drawScreen 最后画, 确保盖在标签列/滚动条等所有 UI 之上(不再被标签遮住)。
    private void drawHoveredStorageTooltip(int mx, int my) {
        int col = (mx - this.guiLeft - GRID_X) / SLOT;
        int row = (my - this.guiTop - GRID_Y) / SLOT;
        if (mx - this.guiLeft - GRID_X < 0 || my - this.guiTop - GRID_Y < 0) return;
        if (col >= COLS || row >= VIS_ROWS) return;
        int idx = row * COLS + col;
        if (idx >= Math.min(this.ids.length, VIS_ROWS * COLS)) return;
        ItemStack stack = buildStack(idx);
        if (stack == null) return;
        @SuppressWarnings("unchecked")
        List<String> tip = stack.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
        tip.add("§7数量: " + this.counts[idx]);
        drawHoveringText(tip, mx, my, this.fontRendererObj);
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    // 锁定标记: 整格淡金高亮 + 右上角小挂锁徽标(带深色底, 任何物品上都清晰)。配合关深度测试画在最上层。
    private void drawLockMarker(int x, int y) {
        final int gold = 0xFFFFD24D; // 亮金
        final int dark = 0xFF231A00; // 深金描边/锁孔
        // 1) 整格淡金高亮, 柔和提示"已锁定"
        drawRect(x, y, x + 16, y + 16, 0x33FFD24D);
        // 2) 右上角挂锁徽标的深色圆底, 保证在亮色物品上也读得清
        drawRect(x + 8, y, x + 16, y + 9, 0xC8202020);
        // 3) 挂锁: 锁环(方拱) + 锁体 + 锁孔
        int bx = x + 9, by = y + 1;
        drawRect(bx + 1, by, bx + 5, by + 1, gold); // 锁环顶
        drawRect(bx + 1, by, bx + 2, by + 4, gold); // 锁环左
        drawRect(bx + 4, by, bx + 5, by + 4, gold); // 锁环右
        drawRect(bx, by + 3, bx + 6, by + 8, gold); // 锁体
        drawRect(bx + 2, by + 5, bx + 4, by + 7, dark); // 锁孔
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        super.drawScreen(mx, my, pt);
        // GuiContainer 渲染物品后会把 GL_LIGHTING 留成开启, 否则之后画的白字会被压暗成灰色。这里先关掉。
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        drawTabs();
        if (this.renamingTab >= 1 && this.renameField != null) {
            int px = this.renameX, py = this.renameY;
            // 物品用高 zLevel + 深度测试渲染, 会盖住 z=0 的 drawRect。关掉深度测试, 让改名弹窗强制画在最上层。
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            // 标题 + 干净的输入面板(实心底 + 描边), 让改名像个小弹窗、输入法候选也有空间。
            drawRect(px - 4, py - 15, px + RENAME_W + 4, py + RENAME_H + 4, 0xF01A1A1A); // 面板底
            drawRect(px - 4, py - 15, px + RENAME_W + 4, py - 14, 0xFF5A5A5A); // 顶边
            drawRect(px - 4, py + RENAME_H + 3, px + RENAME_W + 4, py + RENAME_H + 4, 0xFF5A5A5A); // 底边
            String title = this.reorderMode ? ("输入新序号 1-" + this.tabIds.length + "  §7回车确定 / Esc取消")
                : "重命名标签  §7回车确定 / Esc取消";
            this.fontRendererObj.drawString(title, px, py - 12, 0xFFFFFF);
            drawRect(px, py, px + RENAME_W, py + RENAME_H, 0xFF000000); // 输入框底
            drawRect(px + 1, py + 1, px + RENAME_W - 1, py + RENAME_H - 1, 0xFF2B2B2B);
            this.renameField.drawTextBox();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F); // drawRect 留下的颜色还原, 避免污染后续渲染
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        String tip = leftButtonTip(mx, my);
        if (tip == null) tip = tabTip(mx, my);
        if (tip != null) {
            List<String> l = new ArrayList<String>();
            for (String line : tip.split("\n")) l.add(line);
            drawHoveringText(l, mx, my, this.fontRendererObj);
        } else {
            drawHoveredStorageTooltip(mx, my); // 仓库物品 tooltip 最后画, 盖在标签列之上
        }
    }

    // ===== 右侧标签列 =====
    private static final int TAB_SZ = 11, TAB_STEP = 13; // 与左侧按钮一致 (BTN_W/BTN_GAP)

    private int tabColX() {
        return this.guiLeft + this.xSize + 14; // 面板右外缘: 先滚动条(+2..+12)再标签列
    }

    private static final int MAX_TABS = 15;

    // 显示顺序的标签 id: [全部(-1), 自定义...]。未分类(0)不占标签位, 用"全部"上右键切换查看。
    // 每帧多处调用, 返回缓存(收包时由 rebuildTabOrder 重建), 不再每次分配。
    private int[] tabOrder() {
        return this.tabOrderCache;
    }

    private void rebuildTabOrder() {
        int[] o = new int[1 + this.tabIds.length];
        o[0] = -1; // 全部
        for (int i = 0; i < this.tabIds.length; i++) o[1 + i] = this.tabIds[i];
        this.tabOrderCache = o;
    }

    private boolean hasPlus() {
        return this.tabIds.length < MAX_TABS;
    }

    // 打开标签的行内输入框(改名/改序号共用)。居中于仓库网格区, 输入法候选框有空间。
    private void openTabInput(int tid, boolean reorder, String initial, int maxLen) {
        this.renamingTab = tid;
        this.reorderMode = reorder;
        this.renameX = this.guiLeft + GRID_X + (COLS * SLOT - RENAME_W) / 2;
        this.renameY = this.guiTop + GRID_Y + 30;
        this.renameField = new GuiTextField(
            this.fontRendererObj,
            this.renameX + 3,
            this.renameY + 3,
            RENAME_W - 6,
            RENAME_H - 6);
        this.renameField.setMaxStringLength(maxLen);
        this.renameField.setEnableBackgroundDrawing(false); // 用自绘面板, 关掉它自带的黑框避免双框
        this.renameField.setText(initial);
        this.renameField.setFocused(true);
    }

    private void beginRename(int tid, int orderIndex) {
        String cur = "";
        for (int i = 0; i < this.tabIds.length; i++) {
            if (this.tabIds[i] == tid && this.tabNames[i] != null && !this.tabNames[i].startsWith("标签"))
                cur = this.tabNames[i];
        }
        openTabInput(tid, false, cur, 16);
    }

    // 中键标签 → 输入新序号(1-based, 自定义标签间排序)。orderIndex 即当前序号。
    private void beginReorder(int tid, int orderIndex) {
        openTabInput(tid, true, String.valueOf(orderIndex), 2);
    }

    private void endRename(boolean save) {
        if (save && this.renamingTab >= 1 && this.renameField != null) {
            String t = this.renameField.getText()
                .trim();
            if (!t.isEmpty()) NetworkHandler.sendTabAction(this.reorderMode ? 4 : 2, this.renamingTab, t);
        }
        this.renamingTab = 0;
        this.reorderMode = false;
        this.renameField = null;
    }

    private boolean inRenameBox(int mx, int my) {
        return this.renamingTab >= 1 && inRect(mx, my, this.renameX, this.renameY, RENAME_W, RENAME_H);
    }

    // 自定义标签按位置编号(1..N), 已改名则显示名字首字。(全部/未分类走图标, 不经过这里)
    private String tabLabelAt(int orderIndex) {
        int tid = tabOrder()[orderIndex];
        for (int i = 0; i < this.tabIds.length; i++) {
            if (this.tabIds[i] == tid) {
                String nm = this.tabNames[i];
                if (nm != null && !nm.isEmpty() && !nm.startsWith("标签")) return nm.substring(0, 1);
            }
        }
        return String.valueOf(orderIndex); // 顺序为[全部,自定义...], 自定义从 index 1 起 → 编号即 index
    }

    private void drawTabs() {
        int x = tabColX();
        int[] order = tabOrder();
        int y = this.guiTop + 4;
        for (int i = 0; i < order.length; i++) {
            boolean isAll = order[i] == -1;
            // "全部"在 全部(-1)/未分类(0) 两种视图下都算激活(未分类是它的子视图, 右键切换)。
            boolean active = isAll ? this.curTab <= 0 : order[i] == this.curTab;
            drawTabButton(x, y, isAll ? null : tabLabelAt(i), active, isAll);
            y += TAB_STEP;
        }
        if (hasPlus()) drawTabButton(x, y, "+", false, false); // 满 MAX_TABS 个不再显示 +
    }

    private void drawTabButton(int x, int y, String label, boolean active, boolean gridIcon) {
        int w = TAB_SZ, h = TAB_SZ;
        int fill = active ? WOOD_GOLD : WOOD_FILL; // 激活=亮金木, 常态=棕木
        drawRect(x, y, x + w, y + h, fill);
        drawRect(x, y, x + w, y + 1, WOOD_LIGHT);
        drawRect(x, y, x + 1, y + h, WOOD_LIGHT);
        drawRect(x, y + h - 1, x + w, y + h, WOOD_DARK);
        drawRect(x + w - 1, y, x + w, y + h, WOOD_DARK);
        int icon = active ? 0xFF3A2A00 : 0xFFFFFFFF; // 网格图标: 激活=深棕(金底上可读), 常态=白
        if (gridIcon) { // "全部" = 2x2 小方格(适配 11px 按钮)
            drawRect(x + 2, y + 2, x + 5, y + 5, icon);
            drawRect(x + 6, y + 2, x + 9, y + 5, icon);
            drawRect(x + 2, y + 6, x + 5, y + 9, icon);
            drawRect(x + 6, y + 6, x + 9, y + 9, icon);
        } else {
            int tw = this.fontRendererObj.getStringWidth(label);
            // 标签文字纯白, 不加阴影(阴影会让字发灰)。
            this.fontRendererObj.drawString(label, x + (w - tw) / 2 + 1, y + 2, 0xFFFFFFFF);
        }
    }

    // 返回标签列中鼠标所在行 (0..order.length-1 = 标签, order.length = 加号若有), 不在列上则 Integer.MIN_VALUE。
    private int hitTabSlot(int mx, int my) {
        int x = tabColX();
        if (mx < x || mx >= x + TAB_SZ) return Integer.MIN_VALUE;
        int rel = my - (this.guiTop + 4);
        if (rel < 0) return Integer.MIN_VALUE;
        int idx = rel / TAB_STEP;
        if (rel % TAB_STEP > TAB_SZ) return Integer.MIN_VALUE;
        int maxIdx = tabOrder().length - 1 + (hasPlus() ? 1 : 0);
        if (idx > maxIdx) return Integer.MIN_VALUE;
        return idx;
    }

    private String tabTip(int mx, int my) {
        int hit = hitTabSlot(mx, my);
        if (hit == Integer.MIN_VALUE) return null;
        int[] order = tabOrder();
        if (hit == order.length) return "新建标签";
        int tid = order[hit];
        // "全部"格: 提示随当前视图变化, 并说明右键可切换到"未分类"。
        if (tid == -1) return allMode == 0 ? "未分类 · 右键切回全部" : "全部 · 右键看未分类";
        // 改过名 → 显示名字; 否则按位置显示"标签 N"(和按钮编号一致)。
        for (int i = 0; i < this.tabIds.length; i++) {
            if (this.tabIds[i] == tid) {
                String nm = this.tabNames[i];
                if (nm != null && !nm.isEmpty() && !nm.startsWith("标签")) return nm;
            }
        }
        return "标签 " + hit; // 顺序[全部,自定义...], 自定义从 index 1 起 → 编号即 hit
    }

    // 左侧悬浮按钮的提示文字: 找到鼠标下、带 tip 的按钮直接返回其提示(与按钮顺序解耦)。
    private String leftButtonTip(int mx, int my) {
        for (Object o : this.buttonList) {
            if (!(o instanceof IconButton)) continue;
            IconButton b = (IconButton) o;
            if (b.tip != null && over(b, mx, my)) return b.tip.get();
        }
        return null;
    }

    // 每帧多次调用(渲染 + 悬停tooltip), 直接返回收到数据包时构建好的缓存, 避免每帧重复解压 NBT。
    private ItemStack buildStack(int i) {
        return (i >= 0 && i < this.stackCache.length) ? this.stackCache[i] : null;
    }

    private ItemStack buildStackRaw(int i) {
        Object o = Item.itemRegistry.getObject(this.items[i]);
        if (!(o instanceof Item)) return null;
        ItemStack st = new ItemStack((Item) o, 1, this.metas[i]);
        byte[] nbt = (this.nbts != null && i < this.nbts.length) ? this.nbts[i] : null;
        if (nbt != null) {
            try {
                st.setTagCompound(CompressedStreamTools.readCompressed(new ByteArrayInputStream(nbt)));
            } catch (IOException ignored) {
                // 损坏的 NBT 退化为无属性显示
            }
        }
        return st;
    }

    static String abbrev(long n) {
        if (n >= 1000000L) return String.format("%.1fM", n / 1000000.0);
        if (n >= 1000L) return String.format("%.1fK", n / 1000.0);
        return Long.toString(n);
    }
}
