package ltd.mc233.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ltd.mc233.ItemGlowState;

// 掉落物高亮: 在范围内的掉落物"上方"画出名字 + 数量的漂浮标签(穿墙可见)。
// 纯客户端, 默认开, /glow 可关 / 改半径。(原先还有一层 GLSL 描边发光, 已按需求移除, 只留漂浮名。)
public class ItemGlowRenderer {

    private static boolean loggedError = false;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!ItemGlowState.enabled) return;
        try {
            doRender(e.partialTicks);
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                ltd.mc233.PortableStorageMod.LOG.error("掉落物漂浮名渲染异常(已忽略, 不再重复)", t);
            }
        }
    }

    private void doRender(float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        // 收集范围内的掉落物
        EntityPlayer pl = mc.thePlayer;
        double px = pl.lastTickPosX + (pl.posX - pl.lastTickPosX) * pt;
        double py = pl.lastTickPosY + (pl.posY - pl.lastTickPosY) * pt;
        double pz = pl.lastTickPosZ + (pl.posZ - pl.lastTickPosZ) * pt;
        double r2 = (double) ItemGlowState.radius * ItemGlowState.radius;
        List<EntityItem> items = new ArrayList<EntityItem>();
        Object[] ents = mc.theWorld.loadedEntityList.toArray();
        for (Object o : ents) {
            if (!(o instanceof EntityItem)) continue;
            EntityItem it = (EntityItem) o;
            if (it.isDead || it.getEntityItem() == null) continue;
            double dx = it.posX - px, dy = it.posY - py, dz = it.posZ - pz;
            if (dx * dx + dy * dy + dz * dz <= r2) items.add(it);
        }
        drawNameTags(items, px, py, pz, pt);
    }

    // —— 漂浮名字: 世界空间 billboard, 穿墙 ——
    private void drawNameTags(List<EntityItem> items, double px, double py, double pz, float pt) {
        if (items.isEmpty()) return;
        RenderManager rm = RenderManager.instance;
        if (rm == null) return;
        FontRenderer fr = rm.getFontRenderer();
        if (fr == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            for (EntityItem it : items) {
                double rx = (it.lastTickPosX + (it.posX - it.lastTickPosX) * pt) - px;
                double ry = (it.lastTickPosY + (it.posY - it.lastTickPosY) * pt) - py;
                double rz = (it.lastTickPosZ + (it.posZ - it.lastTickPosZ) * pt) - pz;
                String text = it.getEntityItem()
                    .getDisplayName();
                if (it.getEntityItem().stackSize > 1) text = text + " §7x" + it.getEntityItem().stackSize;
                GL11.glPushMatrix();
                GL11.glTranslated(rx, ry + 0.6, rz);
                drawNameTag(fr, rm, text);
                GL11.glPopMatrix();
            }
        } finally {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void drawNameTag(FontRenderer fr, RenderManager rm, String text) {
        float scale = 0.022F;
        int w = fr.getStringWidth(text) / 2;
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.45F);
        tess.addVertex(-w - 1, -1, 0.0);
        tess.addVertex(-w - 1, 9, 0.0);
        tess.addVertex(w + 1, 9, 0.0);
        tess.addVertex(w + 1, -1, 0.0);
        tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        fr.drawString(text, -w, 0, 0xFFFFFFFF);
    }
}
