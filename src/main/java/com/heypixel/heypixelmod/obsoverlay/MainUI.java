package com.heypixel.heypixelmod.obsoverlay;

import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class MainUI extends Screen {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("heypixel", "textures/background.png");

    // --- 布局常量 ---
    private static final int PANEL_WIDTH = 220;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 35;
    private static final int TITLE_SCALE = 2;
    private static final float PANEL_CORNER_RADIUS = 10.0f;

    // --- 更新日志常量 ---
    private static final int VERSION_COLOR = Color.WHITE.getRGB();
    private static final int CONTENT_COLOR = new Color(85, 255, 255).getRGB();
    private static final float VERSION_FONT_SCALE = 0.5F;
    private static final float CONTENT_FONT_SCALE = 0.3F;
    private static final int PADDING_TOP = 10;
    private static final int PADDING_LEFT = 10;

    private boolean textureLoaded = false;
    private Button[] buttons;
    private final List<UpdateLogEntry> updateLogs = new ArrayList<>();

    public MainUI() {
        super(Component.literal("Naven-Reload Main Menu"));
        this.initUpdateLogs(); // 初始化更新日志数据
    }

    @Override
    protected void init() {
        super.init();
        textureLoaded = checkTextureLoaded();

        // 计算面板高度
        int buttonBlockHeight = (3 * BUTTON_SPACING) + BUTTON_HEIGHT;
        int topSpacing = 60;
        int bottomSpacing = 30;
        int panelHeight = topSpacing + buttonBlockHeight + bottomSpacing;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int startY = panelY + topSpacing;

        buttons = new Button[]{
                new Button(panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT, "单人游戏", this::openSingleplayer),
                new Button(panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2, startY + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT, "多人游戏", this::openMultiplayer),
                new Button(panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2, startY + BUTTON_SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT, "设置", this::openSettings),
                new Button(panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2, startY + BUTTON_SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT, "退出", this::quit)
        };
    }

    private boolean checkTextureLoaded() {
        try {
            mc.getResourceManager().getResourceOrThrow(BACKGROUND_TEXTURE);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to load background texture: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics);
        renderCenterPanel(guiGraphics);
        renderTitle(guiGraphics);
        renderUpdateLogs(guiGraphics); // 渲染更新日志

        for (Button button : buttons) {
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderCenterPanel(GuiGraphics guiGraphics) {
        int buttonBlockHeight = (3 * BUTTON_SPACING) + BUTTON_HEIGHT;
        int topSpacing = 60;
        int bottomSpacing = 30;
        int panelHeight = topSpacing + buttonBlockHeight + bottomSpacing;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - panelHeight) / 2;

        RenderUtils.drawRoundedRect(guiGraphics.pose(), panelX, panelY, PANEL_WIDTH, panelHeight, PANEL_CORNER_RADIUS, new Color(10, 10, 20, 255).getRGB());
    }

    private void renderTitle(GuiGraphics guiGraphics) {
        String title = "Naven-Reload";
        int buttonBlockHeight = (3 * BUTTON_SPACING) + BUTTON_HEIGHT;
        int topSpacing = 60;
        int bottomSpacing = 30;
        int panelHeight = topSpacing + buttonBlockHeight + bottomSpacing;
        int panelY = (this.height - panelHeight) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.width / 2f, panelY + 30, 0);
        guiGraphics.pose().scale(TITLE_SCALE, TITLE_SCALE, 1.0f);
        guiGraphics.pose().translate(-(this.font.width(title) / 2f), -(this.font.lineHeight / 2f), 0);
        guiGraphics.drawString(this.font, title, 0, 0, 0xFFFFFFFF, true);
        guiGraphics.pose().popPose();
    }

    private void drawBackground(@Nonnull GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);
        if (textureLoaded) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.blit(BACKGROUND_TEXTURE, 0, 0, 0, 0, this.width, this.height, this.width, this.height);
            RenderSystem.disableBlend();
        }
        guiGraphics.fill(0, 0, this.width, this.height, 0x50000000);
    }

    private void renderUpdateLogs(GuiGraphics guiGraphics) {
        float currentY = PADDING_TOP;

        for (UpdateLogEntry entry : updateLogs) {
            float versionRenderHeight = (float) Fonts.harmony.getHeight(true, VERSION_FONT_SCALE) + 5;
            if (currentY + versionRenderHeight > this.height) {
                break;
            }

            Fonts.harmony.render(guiGraphics.pose(), "版本 " + entry.version, (double) PADDING_LEFT, (double) currentY, new Color(VERSION_COLOR), true, VERSION_FONT_SCALE);
            currentY += versionRenderHeight;

            String[] contentLines = entry.content.split("\n");
            for (String line : contentLines) {
                float contentRenderHeight = (float) Fonts.harmony.getHeight(true, CONTENT_FONT_SCALE) + 2;
                if (currentY + contentRenderHeight > this.height) {
                    return;
                }
                Fonts.harmony.render(guiGraphics.pose(), "§b" + line, (double) (PADDING_LEFT + 5), (double) currentY, new Color(CONTENT_COLOR), true, CONTENT_FONT_SCALE);
                currentY += contentRenderHeight;
            }
            currentY += 5;
        }
    }

    private void openSingleplayer() { mc.setScreen(new SelectWorldScreen(this)); }
    private void openMultiplayer() { mc.setScreen(new JoinMultiplayerScreen(this)); }
    private void openSettings() { mc.setScreen(new OptionsScreen(this, mc.options)); }
    private void quit() { mc.stop(); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Button btn : buttons) {
                if (btn.isHovered((int) mouseX, (int) mouseY)) {
                    btn.onClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- 更新日志数据和初始化 ---
    private void initUpdateLogs() {
        this.updateLogs.add(getVersion2_5());
        this.updateLogs.add(getVersion2_4());
        this.updateLogs.add(getVersion2_2());
        this.updateLogs.add(getVersion2_1());
        this.updateLogs.add(getVersion2());
        this.updateLogs.add(getVersion1_30beta());
    }
    private UpdateLogEntry getVersion2_5() { String version = "2.4"; String content = "- 修复Notification背景问题\n- 尝试修复了OpenGL\n- BMWIRC适配最新版本"; return new UpdateLogEntry(version, content); }
    private UpdateLogEntry getVersion2_4() { String version = "2.4"; String content = "- 修复了一些小问题\n- 添加出笼(不是很好使)\n- 添加Safe类别模块(不好使)\n- Stuck更新\n- 添加ZenArrayList\n- 重写ClickGUI"; return new UpdateLogEntry(version, content); }
    private UpdateLogEntry getVersion2_2() { String version = "2.2"; String content = "- BMWIRC适配最新版本\n- 音乐播放器修复不支持VIP和无法正常播放的问题\n- HUD的WaterMark增加Mugen模式\n- 增加BNewBackTrack\n- 添加RainbowNitroTargetESP"; return new UpdateLogEntry(version, content); }
    private UpdateLogEntry getVersion2_1() { String version = "2.1"; String content = "- 添加灵动岛透明度修改\n- 修改Title，添加IRC用户名显示\n- 修改AutoTool，将SwtichEat默认状态修改为关闭\n- 添加Criticals(Jump)\n- MainUI中心背景变为纯黑色\n- AutoHitCrystal和AnchorMacro小优化\nMusicPlayer更新:\n - 修复作者获取错误的问题\n - 添加A-Z排序功能\n - 支持自定义歌单"; return new UpdateLogEntry(version, content); }
    private UpdateLogEntry getVersion2() { String version = "2.0"; String content = "- TargetHUD添加Xinxin模式\n- 添加IRC用户名和密码自动补全\n- 添加自定义MainUI\n- Logs迁移至MainUI\n- KillAura添加范围Range显示\n- 添加自定义计分板\n- 新增HUDEditor"; return new UpdateLogEntry(version, content); }
    private UpdateLogEntry getVersion1_30beta() { String version = "1.30"; String content = "- 增加IRC(测试！！！可能会有很多bug，积极反馈给LELEXIAOLL)\nBeta2:\n- IRC登陆页面增加IRC服务器在线显示\n- IRC登陆页面将提示框输入词转移至输入框左边\n- 定时IRC人数提示修改\n- 增加自动重连\n- 增加TabIRC用户显示(原版Tab,测试)\n- 增加IQboost\n- 增加NewBackTrack\n- 增加NewNoslow\nBeta3\n- 最终修复Tab列表不显示IRC用户前缀\n- SilenceFixMode增加两个新选项\n- IRC自动重连修复自动弹出登陆UI的问题\nBeta4:\n- AutoPlay添加绕绿和普通again选择\n- 添加NoFov\n- IRC添加自定义称号(找LELEXIAOLL上称号)\bBeta5:\n- SafeWalk添加OnlyBlock和OnlyBackward\n- ClickGUI完美修复\n- 灵动岛动画调整\nBeta6:\n- Scaffold重构(感谢Mystery1337的Scaffold)\n- 添加ReloadNotification\n- 添加LingDongNotification\n- 灵动岛更换字体\n- 模块Notification时间从3秒调整至2秒\n- 窗口标题优化"; return new UpdateLogEntry(version, content); }
    private static class Button {
        private final int x, y, width, height;
        private final String text;
        private final Runnable action;
        private float hoverProgress = 0.0f;
        private static final float FONT_SCALE = 1.5f;

        public Button(int x, int y, int width, int height, String text, Runnable action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.text = text;
            this.action = action;
        }

        public boolean isHovered(int mouseX, int mouseY) {
            float textWidth = mc.font.width(text) * FONT_SCALE;
            float textHeight = 8 * FONT_SCALE;
            float renderX = x + (width - textWidth) / 2;
            float renderY = y + (height - textHeight) / 2;
            return RenderUtils.isHoveringBound(mouseX, mouseY, (int) renderX - 5, (int) renderY - 5, (int) textWidth + 10, (int) textHeight + 10);
        }

        public void onClick() {
            action.run();
        }

        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHovered(mouseX, mouseY);
            float speed = 5.0f;
            hoverProgress += (hovered ? 1 : -1) * speed * (1.0f / Minecraft.getInstance().getFps());
            hoverProgress = Math.max(0.0f, Math.min(1.0f, hoverProgress));
            float easedProgress = (float) (1 - Math.pow(1 - hoverProgress, 4));

            Color baseColor = new Color(255, 255, 255, 150);
            Color hoverColor = new Color(255, 255, 255, 255);
            int textColor = interpolateColor(baseColor.getRGB(), hoverColor.getRGB(), easedProgress);

            guiGraphics.pose().pushPose();
            float textWidth = mc.font.width(text) * FONT_SCALE;
            float textHeight = 8 * FONT_SCALE;
            float textX = x + (width - textWidth) / 2;
            float textY = y + (height - textHeight) / 2;

            guiGraphics.pose().translate(textX + textWidth / 2, textY + textHeight / 2, 0);
            guiGraphics.pose().scale(FONT_SCALE, FONT_SCALE, 1.0f);
            guiGraphics.pose().translate(-(mc.font.width(text) / 2f), -4, 0);
            guiGraphics.drawString(mc.font, text, 0, 0, textColor, true);
            guiGraphics.pose().popPose();
        }

        private int interpolateColor(int color1, int color2, float progress) {
            int a1 = (color1 >> 24) & 0xFF; int r1 = (color1 >> 16) & 0xFF; int g1 = (color1 >> 8) & 0xFF; int b1 = color1 & 0xFF;
            int a2 = (color2 >> 24) & 0xFF; int r2 = (color2 >> 16) & 0xFF; int g2 = (color2 >> 8) & 0xFF; int b2 = color2 & 0xFF;
            int a = (int) (a1 + (a2 - a1) * progress);
            int r = (int) (r1 + (r2 - r1) * progress);
            int g = (int) (g1 + (g2 - g1) * progress);
            int b = (int) (b1 + (b2 - b1) * progress);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    private class UpdateLogEntry {
        public final String version;
        public final String content;
        public UpdateLogEntry(String version, String content) {
            this.version = version;
            this.content = content;
        }
    }
}