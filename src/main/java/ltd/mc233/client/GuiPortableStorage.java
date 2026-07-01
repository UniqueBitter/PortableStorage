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
    public static final int VIS_ROWS = 5;
    public static final int WINDOW = 90;
    private static final int SLOT = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 20;
    private static final int BTN_W = 11;
    private static final int BTN_H = 11;
    private static final int BTN_GAP = 13; // 相邻按钮的纵向间距(按钮高 + 2px 缝)

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
    private int curTab = -1; // -1=全部, 0=未分类, >=1=自定义
    private int[] tabIds = new int[0];
    private String[] tabNames = new String[0];
    private GuiTextField renameField; // 右键标签时的行内改名输入框
    private int renamingTab = 0; // 0=未在改名; >=1=正在改名的标签id
    private int renameX, renameY;
    private static final int RENAME_W = 140, RENAME_H = 20;
    private IconButton sortByBtn;
    private IconButton dirBtn;
    private IconButton searchBtn;
    private IconButton restockBtn;
    private boolean draggingBar = false;
    private int dragRow = -1; // 拖动中客户端预测的目标行(让滑块跟手, 不被异步数据包回弹)
    private long lastScrollMs = 0; // 拖动时的请求节流
    private long lastTypeMs = 0;
    private final RenderItem renderItem = new RenderItem();

    public GuiPortableStorage(Container c) {
        super(c);
        this.xSize = 195;
        this.ySize = 210; // 比物品栏多出底部一条, 放容量计数
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
        this.buttonList.add(new IconButton(1, bx, by, BTN_W, 0)); // 收纳
        this.sortByBtn = new IconButton(2, bx, by + BTN_GAP, BTN_W, sortByIcon());
        this.dirBtn = new IconButton(3, bx, by + BTN_GAP * 2, BTN_W, dirIcon());
        this.searchBtn = new IconButton(4, bx, by + BTN_GAP * 3, BTN_W, searchIcon());
        this.restockBtn = new IconButton(5, bx, by + BTN_GAP * 4, BTN_W, restockIcon());
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
        if (p != null) {
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
            this.capUsed = p.capUsed;
            this.capTotal = p.capTotal;
            this.curTab = p.curTab;
            this.tabIds = p.tabIds != null ? p.tabIds : new int[0];
            this.tabNames = p.tabNames != null ? p.tabNames : new String[0];
            if (!this.draggingBar) this.rowOffset = p.offset / COLS; // 拖动中不让异步包回弹滑块
            this.sortBy = p.sort / 2;
            this.sortDesc = (p.sort % 2 == 1);
            this.sortAdopted = true;
            updateButtons();
            // 一次性把本页物品栈构建好并缓存(含 NBT 解压), 之后每帧直接取用。
            this.stackCache = new ItemStack[this.ids.length];
            for (int i = 0; i < this.stackCache.length; i++) this.stackCache[i] = buildStackRaw(i);
        }
        // 输入防抖后搜索(随输入更新结果)。
        if (this.lastTypeMs != 0 && System.currentTimeMillis() - this.lastTypeMs > 150) {
            this.lastTypeMs = 0;
            requestWindow(0);
        }
    }

    @Override
    protected void keyTyped(char ch, int key) {
        // 改名模式: 回车确认, Esc 取消, 其余交给输入框。
        if (this.renamingTab >= 1) {
            if (key == Keyboard.KEY_RETURN) endRename(true);
            else if (key == Keyboard.KEY_ESCAPE) endRename(false);
            else if (this.renameField != null) this.renameField.textboxKeyTyped(ch, key);
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
        int tabHit = hitTabSlot(mx, my);
        if (tabHit != Integer.MIN_VALUE) {
            int[] order = tabOrder();
            if (tabHit == order.length) { // + 新建
                if (btn == 0) NetworkHandler.sendTabAction(0, 0, null);
                return;
            }
            int tid = order[tabHit];
            if (btn == 1 && isShiftKeyDown() && tid >= 1) { // Shift+右键 删除自定义标签
                NetworkHandler.sendTabAction(1, tid, null);
                return;
            }
            if (btn == 1 && tid >= 1) { // 右键 改名
                beginRename(tid, tabHit);
                return;
            }
            ItemStack held = this.mc.thePlayer.inventory.getItemStack();
            if (btn == 0 && held != null) { // 左键+手持物品 → 归类到该标签
                NetworkHandler.sendTabAction(3, tid, null);
                return;
            }
            if (btn == 0) { // 左键空手 → 切换视图
                this.curTab = tid;
                requestWindow(0);
            }
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
        int relX = mx - this.guiLeft - GRID_X;
        int relY = my - this.guiTop - GRID_Y;
        if (relX < 0 || relY < 0) return;
        int col = relX / SLOT;
        int row = relY / SLOT;
        if (col >= COLS || row >= VIS_ROWS) return;
        int idx = row * COLS + col;
        ItemStack held = this.mc.thePlayer.inventory.getItemStack();
        boolean onItem = idx < this.ids.length;
        // 右键点仓库物品: 始终"取一个到光标"。可连续右键不断累加同种(手上已拿同种则 +1, 异种则不动),
        // 不再因为手上有东西就改成"存回去", 解决"取一个放一个"的来回切换。
        if (btn == 1 && onItem) {
            NetworkHandler.sendAction(1, this.ids[idx], 0, this.keyword, this.rowOffset * COLS);
            return;
        }
        // 左键且手上拿着东西 → 整组存入仓库。
        if (held != null) {
            if (btn == 0) NetworkHandler.sendAction(3, 0L, 0, this.keyword, this.rowOffset * COLS);
            return;
        }
        // 空手左键点物品: shift 直接进背包, 否则取一组到光标。
        if (onItem) {
            int action = isShiftKeyDown() ? 2 : 0;
            NetworkHandler.sendAction(action, this.ids[idx], 0, this.keyword, this.rowOffset * COLS);
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long timeSinceClick) {
        super.mouseClickMove(mx, my, btn, timeSinceClick);
        if (this.draggingBar) scrollToMouse(my);
    }

    @Override
    protected void mouseMovedOrUp(int mx, int my, int which) {
        super.mouseMovedOrUp(mx, my, which);
        if (which == 0) {
            // 松手时补一发最终位置(拖动中因节流可能没请求到位)
            if (this.draggingBar && this.dragRow >= 0 && this.dragRow != this.rowOffset) requestWindow(this.dragRow);
            this.draggingBar = false;
            this.dragRow = -1;
        }
    }

    private boolean inScrollbar(int mx, int my) {
        int barX = this.guiLeft + GRID_X + COLS * SLOT + 3;
        int barTop = this.guiTop + GRID_Y;
        int barH = VIS_ROWS * SLOT;
        return mx >= barX && mx < barX + 10 && my >= barTop && my < barTop + barH;
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

    private Slot slotUnderMouse(int mx, int my) {
        for (int i = 0; i < this.inventorySlots.inventorySlots.size(); i++) {
            Slot s = (Slot) this.inventorySlots.inventorySlots.get(i);
            int sx = this.guiLeft + s.xDisplayPosition;
            int sy = this.guiTop + s.yDisplayPosition;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) return s;
        }
        return null;
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) {
            NetworkHandler.sendAction(4, 0L, 0, this.keyword, this.rowOffset * COLS);
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

    private void drawSlot(int ix, int iy) {
        int x = ix - 1;
        int y = iy - 1;
        drawRect(x, y, x + 18, y + 18, 0xFF8B8B8B);
        drawRect(x, y, x + 18, y + 1, 0xFF373737);
        drawRect(x, y, x + 1, y + 18, 0xFF373737);
        drawRect(x, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        drawRect(x + 17, y, x + 18, y + 18, 0xFFFFFFFF);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float pt, int mx, int my) {
        drawDefaultBackground();
        int l = this.guiLeft;
        int t = this.guiTop;
        drawRect(l, t, l + this.xSize, t + this.ySize, 0xFFC6C6C6);
        drawRect(l, t, l + this.xSize, t + 1, 0xFFFFFFFF);
        drawRect(l, t, l + 1, t + this.ySize, 0xFFFFFFFF);
        drawRect(l + this.xSize - 1, t, l + this.xSize, t + this.ySize, 0xFF555555);
        drawRect(l, t + this.ySize - 1, l + this.xSize, t + this.ySize, 0xFF555555);
        if (this.search != null) this.search.drawTextBox();
        for (int row = 0; row < VIS_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                drawSlot(l + GRID_X + col * SLOT, t + GRID_Y + row * SLOT);
            }
        }
        // 玩家背包槽位背景(坐标须与容器一致: top=116)
        for (int i = 0; i < this.inventorySlots.inventorySlots.size(); i++) {
            Slot s = (Slot) this.inventorySlots.inventorySlots.get(i);
            drawSlot(l + s.xDisplayPosition, t + s.yDisplayPosition);
        }
        int barX = l + GRID_X + COLS * SLOT + 3;
        int barTop = t + GRID_Y;
        int barH = VIS_ROWS * SLOT;
        drawRect(barX, barTop, barX + 10, barTop + barH, 0xFF555555);
        drawRect(barX + 1, barTop + 1, barX + 9, barTop + barH - 1, 0xFF8B8B8B);
        int maxRow = maxRowOffset();
        // 拖动中用客户端预测行画滑块 → 跟手不回弹; 平时用服务端确认的 rowOffset。
        int shownRow = (this.draggingBar && this.dragRow >= 0) ? this.dragRow : this.rowOffset;
        int handleY = barTop + (maxRow == 0 ? 0 : (int) ((barH - 16) * (shownRow / (double) maxRow)));
        drawRect(barX, handleY, barX + 10, handleY + 16, 0xFFC6C6C6);
        drawRect(barX, handleY, barX + 10, handleY + 1, 0xFFFFFFFF);
        drawRect(barX, handleY, barX + 1, handleY + 16, 0xFFFFFFFF);
        drawRect(barX + 9, handleY, barX + 10, handleY + 16, 0xFF373737);
        drawRect(barX, handleY + 15, barX + 10, handleY + 16, 0xFF373737);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        this.fontRendererObj.drawString("随身仓库", 8, 8, 0x404040);
        // 容量计数放到底部状态栏(不再和搜索框重叠): 满了标红提示。
        String cap = "容量 " + this.capUsed + " / " + this.capTotal;
        int capColor = (this.capTotal > 0 && this.capUsed >= this.capTotal) ? 0xAA0000 : 0x606060;
        this.fontRendererObj.drawString(cap, 8, this.ySize - 14, capColor);
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
                    abbrev(this.counts[i]));
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
        // 仓库格悬停 tooltip
        int relX = mx - this.guiLeft - GRID_X;
        int relY = my - this.guiTop - GRID_Y;
        if (relX >= 0 && relY >= 0) {
            int col = relX / SLOT;
            int row = relY / SLOT;
            if (col < COLS && row < VIS_ROWS) {
                int idx = row * COLS + col;
                if (idx < n) {
                    ItemStack stack = buildStack(idx);
                    if (stack != null) {
                        @SuppressWarnings("unchecked")
                        List<String> tip = stack
                            .getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
                        tip.add("§7数量: " + this.counts[idx]);
                        drawHoveringText(tip, mx - this.guiLeft, my - this.guiTop, this.fontRendererObj);
                        GL11.glDisable(GL11.GL_LIGHTING);
                    }
                }
            }
        }
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
        drawTabs();
        if (this.renamingTab >= 1 && this.renameField != null) {
            int px = this.renameX, py = this.renameY;
            // 物品用高 zLevel + 深度测试渲染, 会盖住 z=0 的 drawRect。关掉深度测试, 让改名弹窗强制画在最上层。
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            // 标题 + 干净的输入面板(实心底 + 描边), 让改名像个小弹窗、输入法候选也有空间。
            drawRect(px - 4, py - 15, px + RENAME_W + 4, py + RENAME_H + 4, 0xF01A1A1A); // 面板底
            drawRect(px - 4, py - 15, px + RENAME_W + 4, py - 14, 0xFF5A5A5A); // 顶边
            drawRect(px - 4, py + RENAME_H + 3, px + RENAME_W + 4, py + RENAME_H + 4, 0xFF5A5A5A); // 底边
            this.fontRendererObj.drawString("重命名标签  §7回车确定 / Esc取消", px, py - 12, 0xFFFFFF);
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
            l.add(tip);
            drawHoveringText(l, mx, my, this.fontRendererObj);
        }
    }

    // ===== 右侧标签列 =====
    private static final int TAB_SZ = 11, TAB_STEP = 13; // 与左侧按钮一致 (BTN_W/BTN_GAP)

    private int tabColX() {
        return this.guiLeft + this.xSize - 1; // 吸附在面板右边缘(贴主体)
    }

    private static final int MAX_TABS = 10;

    // 显示顺序的标签 id: [全部(-1), 自定义...]。未分类(0)不再单列, 未归类物品在"全部"里可见。
    private int[] tabOrder() {
        int[] o = new int[1 + this.tabIds.length];
        o[0] = -1;
        for (int i = 0; i < this.tabIds.length; i++) o[1 + i] = this.tabIds[i];
        return o;
    }

    private boolean hasPlus() {
        return this.tabIds.length < MAX_TABS;
    }

    private void beginRename(int tid, int orderIndex) {
        this.renamingTab = tid;
        // 居中于仓库网格区: 输入框更大、周围留白, 输入法候选框有空间, 打字更舒服。
        this.renameX = this.guiLeft + GRID_X + (COLS * SLOT - RENAME_W) / 2;
        this.renameY = this.guiTop + GRID_Y + 30;
        this.renameField = new GuiTextField(
            this.fontRendererObj,
            this.renameX + 3,
            this.renameY + 3,
            RENAME_W - 6,
            RENAME_H - 6);
        this.renameField.setMaxStringLength(16);
        this.renameField.setEnableBackgroundDrawing(false); // 用自绘面板, 关掉它自带的黑框避免双框
        String cur = "";
        for (int i = 0; i < this.tabIds.length; i++) {
            if (this.tabIds[i] == tid && this.tabNames[i] != null && !this.tabNames[i].startsWith("标签"))
                cur = this.tabNames[i];
        }
        this.renameField.setText(cur);
        this.renameField.setFocused(true);
    }

    private void endRename(boolean save) {
        if (save && this.renamingTab >= 1 && this.renameField != null) {
            String t = this.renameField.getText()
                .trim();
            if (!t.isEmpty()) NetworkHandler.sendTabAction(2, this.renamingTab, t);
        }
        this.renamingTab = 0;
        this.renameField = null;
    }

    private boolean inRenameBox(int mx, int my) {
        return this.renamingTab >= 1 && mx >= this.renameX
            && mx < this.renameX + RENAME_W
            && my >= this.renameY
            && my < this.renameY + RENAME_H;
    }

    // 自定义标签按"位置"编号(1..N); 已改名(非默认"标签N")则显示名字首字。
    private String tabLabelAt(int orderIndex) {
        int tid = tabOrder()[orderIndex];
        for (int i = 0; i < this.tabIds.length; i++) {
            if (this.tabIds[i] == tid) {
                String nm = this.tabNames[i];
                if (nm != null && !nm.isEmpty() && !nm.startsWith("标签")) return nm.substring(0, 1);
            }
        }
        return String.valueOf(orderIndex); // orderIndex 即 1-based 位置
    }

    private void drawTabs() {
        int x = tabColX();
        int[] order = tabOrder();
        int y = this.guiTop + 4;
        for (int i = 0; i < order.length; i++) {
            boolean active = order[i] == this.curTab;
            if (order[i] == -1) drawTabButton(x, y, null, active, true); // 全部 = 网格图标
            else drawTabButton(x, y, tabLabelAt(i), active, false);
            y += TAB_STEP;
        }
        if (hasPlus()) drawTabButton(x, y, "+", false, false); // 满 10 个不再显示 +
    }

    private void drawTabButton(int x, int y, String label, boolean active, boolean gridIcon) {
        int w = TAB_SZ, h = TAB_SZ;
        int fill = active ? 0xFFE0CC66 : 0xFF8B8B8B; // 激活=暖金
        drawRect(x, y, x + w, y + h, fill);
        drawRect(x, y, x + w, y + 1, 0xFFFFFFFF);
        drawRect(x, y, x + 1, y + h, 0xFFFFFFFF);
        drawRect(x, y + h - 1, x + w, y + h, 0xFF373737);
        drawRect(x + w - 1, y, x + w, y + h, 0xFF373737);
        int fg = active ? 0xFF3A2A00 : 0xFF303030;
        if (gridIcon) { // "全部" = 2x2 小方格(适配 11px 按钮)
            drawRect(x + 2, y + 2, x + 5, y + 5, fg);
            drawRect(x + 6, y + 2, x + 9, y + 5, fg);
            drawRect(x + 2, y + 6, x + 5, y + 9, fg);
            drawRect(x + 6, y + 6, x + 9, y + 9, fg);
        } else {
            int tw = this.fontRendererObj.getStringWidth(label);
            this.fontRendererObj.drawString(label, x + (w - tw) / 2 + 1, y + 2, fg & 0xFFFFFF);
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
        if (tid == -1) return "全部";
        // 改过名 → 显示名字; 否则按位置显示"标签 N"(和按钮编号一致)。
        for (int i = 0; i < this.tabIds.length; i++) {
            if (this.tabIds[i] == tid) {
                String nm = this.tabNames[i];
                if (nm != null && !nm.isEmpty() && !nm.startsWith("标签")) return nm;
            }
        }
        return "标签 " + hit;
    }

    // 左侧悬浮按钮的提示文字
    private String leftButtonTip(int mx, int my) {
        int bx = this.guiLeft - BTN_W - 2;
        if (mx < bx || mx >= bx + BTN_W) return null;
        int rel = my - (this.guiTop + 4);
        if (rel < 0) return null;
        int idx = rel / BTN_GAP;
        if (rel % BTN_GAP > BTN_H) return null;
        switch (idx) {
            case 0:
                return "一键收纳(中键锁定的格不收)";
            case 1:
                return "排序: " + (this.sortBy == 1 ? "数量" : this.sortBy == 2 ? "类型" : this.sortBy == 3 ? "时间" : "名字");
            case 2:
                return this.sortDesc ? "降序" : "升序";
            case 3:
                return Config.autoFocusSearch ? "打开即可输入搜索(点此关闭自动聚焦)" : "需点搜索框后输入(点此开启自动聚焦)";
            case 4:
                return this.restockOn ? "自动补货: 开(只补中键锁定的格, 减少即从仓库补满)" : "自动补货: 关";
            default:
                return null;
        }
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
