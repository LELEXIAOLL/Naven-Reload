package com.heypixel.heypixelmod.obsoverlay.ui.hudeditor;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.text.CustomTextRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Collection;

public class HUDEditor {
    private static final Minecraft mc = Minecraft.getInstance();
    private static HUDEditor instance;
    private final Map<String, HUDElement> hudElements = new HashMap<>();

    private HUDElement draggingElement = null;
    private double dragStartX = 0, dragStartY = 0;
    private double elementStartX = 0, elementStartY = 0;
    private boolean editMode = false;

    private static final Color COLOR_DEFAULT = new Color(0, 255, 0);
    private static final Color COLOR_HOVER = new Color(255, 255, 0);
    private static final Color COLOR_DRAGGING = new Color(255, 0, 0);
    private float breathingAlpha = 1.0f;

    public HUDEditor() {
        instance = this;
        initializeHUDElements();
        Naven.getInstance().getEventManager().register(this);
    }

    public static HUDEditor getInstance() {
        if (instance == null) {
            instance = new HUDEditor();
        }
        return instance;
    }
    private void initializeHUDElements() {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        hudElements.put("arraylist", new HUDElement("arraylist", "ArrayList", screenWidth - 100, 2, 100, 200));
        hudElements.put("targethud", new HUDElement("targethud", "TargetHUD", screenWidth / 2.0F + 10.0F, screenHeight / 2.0F + 10.0F, 160, 50));
        hudElements.put("lingdong", new HUDElement("lingdong", "LingDong", screenWidth / 2.0F, screenHeight * 0.08f, 250, 90));
        hudElements.put("potioneffects", new HUDElement("potioneffects", "EffectDisplay", 10, screenHeight / 2.0F - 50, 120, 100));
        hudElements.put("scoreboard", new HUDElement("scoreboard", "ScoreBoard",
                screenWidth - 150, 20, 120, 200));
        hudElements.put("watermark", new HUDElement("watermark", "Watermark", 5, 5, 100, 20));
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        boolean shouldEdit = mc.screen instanceof ChatScreen;
        if (shouldEdit != editMode) {
            editMode = shouldEdit;
        }
        if (editMode) {
            renderEditMode(event);
        }
    }

    private void updateBreathingAlpha() {
        long time = System.currentTimeMillis() % 2000L;
        float progress = (float) time / 2000.0f;
        this.breathingAlpha = 0.3f + 0.7f * ((float)Math.sin(progress * 2.0f * Math.PI) * 0.5f + 0.5f);
    }

    // --- 修改：startDragging，为灵动岛使用中心点检测 ---
    private void startDragging(double mouseX, double mouseY) {
        for (HUDElement element : hudElements.values()) {
            boolean isHovering;
            // 特殊处理灵动岛的悬停检测
            if (element.name.equals("lingdong")) {
                double leftX = element.x - element.width / 2.0;
                isHovering = mouseX >= leftX && mouseX <= leftX + element.width &&
                        mouseY >= element.y && mouseY <= element.y + element.height;
            } else {
                isHovering = element.isHovering(mouseX, mouseY);
            }

            if (isHovering) {
                draggingElement = element;
                dragStartX = mouseX;
                dragStartY = mouseY;
                elementStartX = element.x;
                elementStartY = element.y;
                break;
            }
        }
    }

    private void stopDragging() {
        if (draggingElement != null) {
            draggingElement = null;
            Naven.getInstance().getFileManager().save();
        }
    }

    private void updateDragging() {
        if (draggingElement != null) {
            double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            draggingElement.x = elementStartX + (mouseX - dragStartX);
            draggingElement.y = elementStartY + (mouseY - dragStartY);
        }
    }

    // --- 修改：renderEditMode，为灵动岛使用中心点绘制边框 ---
    private void renderEditMode(EventRender2D event) {
        updateDragging();
        updateBreathingAlpha();

        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        handleMouseInput(mouseX, mouseY);

        CustomTextRenderer font = Fonts.opensans;

        for (HUDElement element : hudElements.values()) {
            double renderX = element.x;
            double renderY = element.y;

            // 如果是灵动岛，计算其左上角坐标用于渲染
            if (element.name.equals("lingdong")) {
                renderX = element.x - element.width / 2.0;
            }

            boolean isHovering;
            if (element.name.equals("lingdong")) {
                isHovering = mouseX >= renderX && mouseX <= renderX + element.width &&
                        mouseY >= renderY && mouseY <= renderY + element.height;
            } else {
                isHovering = element.isHovering(mouseX, mouseY);
            }

            boolean isDragging = element == draggingElement;

            if (isDragging || isHovering) {
                Color baseColor = isDragging ? COLOR_DRAGGING : (isHovering ? COLOR_HOVER : COLOR_DEFAULT);
                float alpha = (isDragging || isHovering) ? 1.0f : this.breathingAlpha;
                int finalColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int)(alpha * 255)).getRGB();

                // 使用计算出的 renderX, renderY 来绘制
                drawElementBorderAndInfo(event.getGuiGraphics().pose(), element, (float)renderX, (float)renderY, finalColor, font);
            } else {
                // 即使不悬停也要绘制默认状态的边框
                int finalColor = new Color(COLOR_DEFAULT.getRed(), COLOR_DEFAULT.getGreen(), COLOR_DEFAULT.getBlue(), (int)(this.breathingAlpha * 255)).getRGB();
                drawElementBorderAndInfo(event.getGuiGraphics().pose(), element, (float)renderX, (float)renderY, finalColor, font);
            }
        }
    }

    private void drawElementBorderAndInfo(PoseStack poseStack, HUDElement element, float x, float y, int color, CustomTextRenderer font) {
        float width = (float)element.width;
        float height = (float)element.height;

        if (width <= 0 || height <= 0) return;

        RenderUtils.drawThickRectBorder(poseStack, x, y, width, height, 1.5f, color);

        // 信息文本现在显示存储的锚点坐标
        String infoText = String.format("%s | X: %.0f | Y: %.0f", element.displayName, element.x, element.y);
        float textWidth = font.getWidth(infoText, 0.3);
        float textHeight = (float)font.getHeight(true, 0.3);
        float padding = 2.0f;

        float bgWidth = textWidth + padding * 2;
        float bgHeight = textHeight + padding;
        float bgX = x;
        float bgY = y - bgHeight;

        RenderUtils.fill(poseStack, bgX, bgY, bgX + bgWidth, bgY + bgHeight, color);
        font.render(poseStack, infoText, bgX + padding, bgY + padding / 2, Color.WHITE, true, 0.3);
    }


    private void handleMouseInput(double mouseX, double mouseY) {
        boolean mousePressed = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (mousePressed && draggingElement == null) {
            startDragging(mouseX, mouseY);
        } else if (!mousePressed && draggingElement != null) {
            stopDragging();
        }
    }

    public HUDElement getHUDElement(String name) {
        return hudElements.get(name);
    }

    public Collection<HUDElement> getAllElements() {
        return hudElements.values();
    }

    public boolean isEditMode() {
        return editMode;
    }

    public static class HUDElement {
        public String name, displayName;
        public double x, y, width, height;

        public HUDElement(String name, String displayName, double x, double y, double width, double height) {
            this.name = name;
            this.displayName = displayName;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean isHovering(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}