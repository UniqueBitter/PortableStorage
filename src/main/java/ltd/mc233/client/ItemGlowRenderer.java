package ltd.mc233.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ltd.mc233.ItemGlowState;

// 复刻 Minecraft 1.9 的"发光"描边(Glowing effect)。原版做法, 在 1.7.10 上等价实现:
// 1) 把要发光的实体单独画进一个独立帧缓冲(silhouette FBO) —— 用实体"自己的渲染器"画, 所以自转/上下浮动/
// 3D 模型都与真实物品逐帧一致(这正是面片描边做不到、会"跟不上旋转"的根因)。
// 2) 不与地形做深度比较 → 轮廓始终完整 → 穿墙可见。
// 3) 跑一个 GLSL 边缘检测后处理: 物品覆盖区域之外、但紧邻覆盖区域的像素 = 边 → 输出描边色; 其余丢弃。
// 得到处处等宽、锐利的轮廓。
// 4) 把描边合成回主画面。
// 纯客户端; 任何异常/着色器不支持都安全降级(关闭功能并提示一次), 绝不崩游戏。
public class ItemGlowRenderer {

    private static boolean loggedError = false;
    private boolean disabled = false; // 着色器或 FBO 不可用时永久降级
    private boolean warned = false;

    private Framebuffer fbo; // 轮廓剪影缓冲
    private int fboW, fboH;
    private int program; // 边缘检测着色器程序
    private int uTexel, uThick, uColor; // uniform 位置

    // 描边参数(可调): 颜色(默认纯白, 同 1.9 无队伍时的发光色)与厚度(屏幕像素)。
    private static final float COL_R = 1.0F, COL_G = 1.0F, COL_B = 1.0F;
    private static final float THICKNESS = 2.6F;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!ItemGlowState.enabled || disabled) return;
        try {
            doRender(e.partialTicks);
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                ltd.mc233.PortableStorageMod.LOG.error("掉落物发光渲染异常(已忽略, 不再重复)", t);
            }
        }
    }

    private void doRender(float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!OpenGlHelper.framebufferSupported || !OpenGlHelper.shadersSupported) {
            warnOnce(mc, "§e[发光] 你的显卡/驱动不支持着色器, 发光描边已关闭。");
            disabled = true;
            return;
        }

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

        // 先在主画面、世界矩阵下画好漂浮名字(穿墙)。
        drawNameTags(items, px, py, pz, pt);
        if (items.isEmpty()) return;

        if (!ensureResources(mc)) {
            disabled = true;
            return;
        }

        // —— 1) 把这些物品的实体渲染进轮廓 FBO(只取覆盖/alpha, 颜色无所谓) ——
        renderSilhouettes(mc, items, pt);

        // —— 2) 回到主帧缓冲, 用边缘检测着色器把轮廓合成上去 ——
        compositeOutline(mc);
    }

    private void renderSilhouettes(Minecraft mc, List<EntityItem> items, float pt) {
        RenderManager rm = RenderManager.instance;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        fbo.bindFramebuffer(true);
        // 关键: MC 世界渲染期间会用 glColorMask 关闭 alpha 写入以保护主缓冲 alpha。
        // 若不强制打开, 剪影的 alpha 写不进 FBO → 边缘检测全 0 → 整片不显示。这是"啥也没有"的根因。
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        // 世界投影/模型视图矩阵此刻仍是当前相机的 → 实体会落在与主画面相同的屏幕位置。
        GL11.glEnable(GL11.GL_DEPTH_TEST); // FBO 自带深度, 仅物品自遮挡; 不含地形 → 穿墙
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F); // 透明像素不写入 → 干净剪影
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_FOG);
        RenderHelper.enableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            for (EntityItem it : items) {
                double x = (it.lastTickPosX + (it.posX - it.lastTickPosX) * pt) - rm.renderPosX;
                double y = (it.lastTickPosY + (it.posY - it.lastTickPosY) * pt) - rm.renderPosY;
                double z = (it.lastTickPosZ + (it.posZ - it.lastTickPosZ) * pt) - rm.renderPosZ;
                rm.renderEntityWithPosYaw(it, x, y, z, it.rotationYaw, pt);
            }
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                ltd.mc233.PortableStorageMod.LOG.error("掉落物发光: 剪影渲染异常(已跳过)", t);
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glPopAttrib();
            mc.getFramebuffer()
                .bindFramebuffer(true);
        }
    }

    private void compositeOutline(Minecraft mc) {
        int w = mc.displayWidth, h = mc.displayHeight;
        float uMax = (float) fbo.framebufferWidth / (float) fbo.framebufferTextureWidth;
        float vMax = (float) fbo.framebufferHeight / (float) fbo.framebufferTextureHeight;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, w, h, 0.0D, -1.0D, 1.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            // 关掉光照贴图纹理单元, 避免它影响合成。
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColorMask(true, true, true, true);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fbo.framebufferTexture);

            if (ItemGlowState.debug) {
                // 调试模式: 不用着色器, 直接把剪影缓冲糊到屏幕(看捕获是否成功)。
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                drawQuad(w, h, uMax, vMax);
            } else {
                GL20.glUseProgram(program);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "tex"), 0);
                GL20.glUniform2f(uTexel, 1.0F / fbo.framebufferTextureWidth, 1.0F / fbo.framebufferTextureHeight);
                GL20.glUniform1f(uThick, THICKNESS);
                GL20.glUniform3f(uColor, COL_R, COL_G, COL_B);
                drawQuad(w, h, uMax, vMax);
                GL20.glUseProgram(0);
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    // 全屏四边形, 采样剪影 FBO 纹理(FBO 原点在左下, 故 v 翻转)。
    private void drawQuad(int w, int h, float uMax, float vMax) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(0, h, 0, 0, 0);
        t.addVertexWithUV(w, h, 0, uMax, 0);
        t.addVertexWithUV(w, 0, 0, uMax, vMax);
        t.addVertexWithUV(0, 0, 0, 0, vMax);
        t.draw();
    }

    // 创建/更新 FBO 与着色器; 返回是否可用。
    private boolean ensureResources(Minecraft mc) {
        int w = mc.displayWidth, h = mc.displayHeight;
        if (fbo == null || fboW != w || fboH != h) {
            if (fbo != null) fbo.deleteFramebuffer();
            fbo = new Framebuffer(w, h, true);
            fbo.setFramebufferFilter(GL11.GL_NEAREST);
            fboW = w;
            fboH = h;
        }
        if (program == 0) {
            program = buildProgram();
            if (program == 0) {
                warnOnce(mc, "§e[发光] 着色器编译失败, 发光描边已关闭。");
                return false;
            }
            uTexel = GL20.glGetUniformLocation(program, "texel");
            uThick = GL20.glGetUniformLocation(program, "thick");
            uColor = GL20.glGetUniformLocation(program, "col");
        }
        return fbo.framebufferObject >= 0 && program != 0;
    }

    private int buildProgram() {
        final String vsrc = "#version 120\n" + "void main(){\n"
            + "  gl_Position = ftransform();\n"
            + "  gl_TexCoord[0] = gl_MultiTexCoord0;\n"
            + "}\n";
        final String fsrc = "#version 120\n" + "uniform sampler2D tex;\n"
            + "uniform vec2 texel;\n"
            + "uniform float thick;\n"
            + "uniform vec3 col;\n"
            + "void main(){\n"
            + "  vec2 uv = gl_TexCoord[0].st;\n"
            + "  float c = texture2D(tex, uv).a;\n"
            + "  if (c > 0.5) discard;\n" // 物品内部 → 不画(保留原画面)
            + "  float m = 0.0;\n"
            + "  for (int i = 0; i < 16; i++){\n"
            + "    float a = float(i) * 0.392699;\n" // 2*pi/16
            + "    vec2 o = vec2(cos(a), sin(a)) * texel * thick;\n"
            + "    m = max(m, texture2D(tex, uv + o).a);\n"
            + "  }\n"
            + "  if (m < 0.5) discard;\n" // 附近无物品 → 透明
            + "  gl_FragColor = vec4(col, 1.0);\n"
            + "}\n";
        int vs = compile(GL20.GL_VERTEX_SHADER, vsrc);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, fsrc);
        if (vs == 0 || fs == 0) return 0;
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vs);
        GL20.glAttachShader(prog, fs);
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            ltd.mc233.PortableStorageMod.LOG.error("发光着色器链接失败: " + GL20.glGetProgramInfoLog(prog, 2048));
            GL20.glDeleteProgram(prog);
            return 0;
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return prog;
    }

    private int compile(int type, String src) {
        int sh = GL20.glCreateShader(type);
        GL20.glShaderSource(sh, src);
        GL20.glCompileShader(sh);
        if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            ltd.mc233.PortableStorageMod.LOG.error("发光着色器编译失败: " + GL20.glGetShaderInfoLog(sh, 2048));
            GL20.glDeleteShader(sh);
            return 0;
        }
        return sh;
    }

    private void warnOnce(Minecraft mc, String msg) {
        if (warned) return;
        warned = true;
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(msg));
        }
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
