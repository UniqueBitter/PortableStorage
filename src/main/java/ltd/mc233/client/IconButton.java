package ltd.mc233.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

// 自绘的"凸起"浅灰小按钮 + 居中图标(取自 icons.png 的第 icon 个 16px 格, 按钮内缩放显示)。
public class IconButton extends GuiButton {

    private static final ResourceLocation ICONS = new ResourceLocation("portablestorage", "textures/gui/icons.png");

    public int icon;

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

        // 自绘干净的"凸起"浅灰按钮, 不依赖资源包可能改坏的 vanilla widgets 贴图。
        int fill = !this.enabled ? 0xFF6E6E6E : hov ? 0xFFB6B6B6 : 0xFF8B8B8B;
        drawRect(x, y, x + w, y + h, fill); // 填充
        drawRect(x, y, x + w, y + 1, 0xFFFFFFFF); // 顶 高光
        drawRect(x, y, x + 1, y + h, 0xFFFFFFFF); // 左 高光
        drawRect(x, y + h - 1, x + w, y + h, 0xFF373737); // 底 阴影
        drawRect(x + w - 1, y, x + w, y + h, 0xFF373737); // 右 阴影

        // 居中绘制图标, 缩放到按钮内(留 2px 边)。源图标 16x16。
        int iconSize = Math.max(4, w - 2);
        float sc = iconSize / 16.0F;
        int dx = x + (w - iconSize) / 2;
        int dy = y + (h - iconSize) / 2;
        mc.getTextureManager()
            .bindTexture(ICONS);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPushMatrix();
        GL11.glTranslatef(dx, dy, 0F);
        GL11.glScalef(sc, sc, 1F);
        this.drawTexturedModalRect(0, 0, this.icon * 16, 0, 16, 16);
        GL11.glPopMatrix();
    }
}
