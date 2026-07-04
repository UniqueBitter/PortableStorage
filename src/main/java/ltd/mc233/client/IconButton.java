package ltd.mc233.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

// 自绘的"凸起"木质小按钮 + 居中图标(取自 icons.png 的第 icon 个 16px 格, 按钮内缩放显示)。
public class IconButton extends GuiButton {

    private static final ResourceLocation ICONS = new ResourceLocation("portablestorage", "textures/gui/icons.png");

    // 木质配色(与 generic_54 箱子皮肤呼应)。
    private static final int WOOD_LIGHT = 0xFFCBA76A; // 高光/上左
    private static final int WOOD_FILL = 0xFF9A6A38; // 常态填充
    private static final int WOOD_HOVER = 0xFFB07E44; // 悬停填充
    private static final int WOOD_OFF = 0xFF6B4E2E; // 禁用填充
    private static final int WOOD_DARK = 0xFF4E3216; // 阴影/下右

    public int icon;
    public int tint = 0xFFFFFFFF; // 图标着色(ARGB), 默认白; 切换模式时可上色(如泰拉模式=绿)
    public java.util.function.Supplier<String> tip; // 悬停提示(惰性求值, 每次读实时状态; 支持 \n 多行)

    public IconButton(int id, int x, int y, int size, int icon) {
        super(id, x, y, size, size, "");
        this.icon = icon;
    }

    @Override
    public void drawButton(Minecraft mc, int mx, int my) {
        if (!this.visible) return;
        int x = this.xPosition, y = this.yPosition, w = this.width, h = this.height;
        boolean hov = mx >= x && my >= y && mx < x + w && my < y + h;
        this.field_146123_n = hov; // 供 getHoverState/点击音效使用

        // 自绘"凸起"木质按钮。
        int fill = !this.enabled ? WOOD_OFF : hov ? WOOD_HOVER : WOOD_FILL;
        drawRect(x, y, x + w, y + h, fill); // 填充
        drawRect(x, y, x + w, y + 1, WOOD_LIGHT); // 顶 高光
        drawRect(x, y, x + 1, y + h, WOOD_LIGHT); // 左 高光
        drawRect(x, y + h - 1, x + w, y + h, WOOD_DARK); // 底 阴影
        drawRect(x + w - 1, y, x + w, y + h, WOOD_DARK); // 右 阴影

        // 居中绘制图标, 缩放到按钮内(留 2px 边)。源图标 16x16。
        int iconSize = Math.max(4, w - 2);
        float sc = iconSize / 16.0F;
        int dx = x + (w - iconSize) / 2;
        int dy = y + (h - iconSize) / 2;
        mc.getTextureManager()
            .bindTexture(ICONS);
        GL11.glColor4f(
            ((this.tint >> 16) & 0xFF) / 255F,
            ((this.tint >> 8) & 0xFF) / 255F,
            (this.tint & 0xFF) / 255F,
            ((this.tint >> 24) & 0xFF) / 255F);
        // 图标本身是深色像素: 用它的 alpha 作形状、RGB 取自 glColor(tint), 于是深色图标也能画成白色/任意色。
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_PRIMARY_COLOR);
        GL11.glPushMatrix();
        GL11.glTranslatef(dx, dy, 0F);
        GL11.glScalef(sc, sc, 1F);
        this.drawTexturedModalRect(0, 0, this.icon * 16, 0, 16, 16);
        GL11.glPopMatrix();
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE); // 还原, 免得污染后续渲染
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }
}
