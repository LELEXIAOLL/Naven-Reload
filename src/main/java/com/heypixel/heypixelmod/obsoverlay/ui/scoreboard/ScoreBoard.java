package com.heypixel.heypixelmod.obsoverlay.ui.scoreboard;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.Scoreboard;
import com.heypixel.heypixelmod.obsoverlay.ui.hudeditor.HUDEditor;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.text.CustomTextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.awt.Color;
import java.util.Collections;
import java.util.List;

public class ScoreBoard {

    // 基础尺寸常量
    private static final float BASE_PADDING = 3.5f;
    private static final float BASE_TEXT_SCALE = 0.30f;
    private static final float BASE_LINE_SPACING = 2.0f;
    private static final Minecraft mc = Minecraft.getInstance();

    public static class ScoreboardLine {
        public final Component textComponent;
        public final int score;

        public ScoreboardLine(Component textComponent, int score) {
            this.textComponent = textComponent;
            this.score = score;
        }
    }

    public static void renderCustomScoreboard(GuiGraphics guiGraphics, String title, List<ScoreboardLine> scores, String footer) {
        HUDEditor.HUDElement scoreboardElement = HUDEditor.getInstance().getHUDElement("scoreboard");
        Scoreboard scoreboardModule = (Scoreboard) Naven.getInstance().getModuleManager().getModule(Scoreboard.class);
        if (scoreboardElement == null || scoreboardModule == null) return;

        Font font = mc.font;

        // --- 1. 获取缩放比例并计算动态尺寸 ---
        float scale = scoreboardModule.size.getCurrentValue();
        float padding = BASE_PADDING * scale;
        float textScale = BASE_TEXT_SCALE * scale;
        float lineSpacing = BASE_LINE_SPACING * scale;

        Collections.reverse(scores);

        // --- 2. 重新计算尺寸 ---
        float textHeight = font.lineHeight * textScale;
        float totalHeight = (textHeight + lineSpacing) * (scores.size() + 2) + padding * 2;

        float maxContentWidth = font.width(title) * textScale;
        for (ScoreboardLine line : scores) {
            float lineWidth = (font.width(line.textComponent) + font.width(" " + line.score)) * textScale;
            maxContentWidth = Math.max(maxContentWidth, lineWidth);
        }
        float footerWidth = font.width(footer) * textScale;
        maxContentWidth = Math.max(maxContentWidth, footerWidth);

        float totalWidth = maxContentWidth + padding * 2;

        // --- 3. 获取坐标 ---
        float x = (float) scoreboardElement.x;
        float y = (float) scoreboardElement.y;

        // --- 4. 绘制内容 ---
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 100);

        float currentY = y + padding;

        // 绘制标题
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(textScale, textScale, 1);
        guiGraphics.drawString(font, title, (int)((x + (totalWidth - font.width(title) * textScale) / 2) / textScale), (int)(currentY / textScale), Color.WHITE.getRGB());
        guiGraphics.pose().popPose();
        currentY += textHeight + lineSpacing;

        // 绘制分割线
        RenderUtils.fill(guiGraphics.pose(), x + padding, currentY, x + totalWidth - padding, currentY + 0.5f * scale, new Color(255, 255, 255, 90).getRGB());
        currentY += lineSpacing;

        // 绘制Scores
        for (ScoreboardLine line : scores) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(textScale, textScale, 1);

            guiGraphics.drawString(font, line.textComponent, (int)((x + padding) / textScale), (int)(currentY / textScale), -1);

            String scoreString = String.valueOf(line.score);
            float scoreWidth = font.width(scoreString) * textScale;
            guiGraphics.drawString(font, scoreString, (int)((x + totalWidth - padding - scoreWidth) / textScale), (int)(currentY / textScale), Color.WHITE.getRGB());

            guiGraphics.pose().popPose();
            currentY += textHeight + lineSpacing;
        }

        // 绘制另一个分割线
        RenderUtils.fill(guiGraphics.pose(), x + padding, currentY, x + totalWidth - padding, currentY + 0.5f * scale, new Color(255, 255, 255, 90).getRGB());
        currentY += lineSpacing;

        // 绘制Footer
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(textScale, textScale, 1);
        guiGraphics.drawString(font, footer, (int)((x + (totalWidth - footerWidth) / 2) / textScale), (int)(currentY / textScale), Color.WHITE.getRGB());
        guiGraphics.pose().popPose();

        guiGraphics.pose().popPose();

        // --- 5. 更新HUDEditor和模块中的尺寸和位置 ---
        scoreboardElement.width = totalWidth;
        scoreboardElement.height = totalHeight;
        scoreboardModule.updateRenderData(x, y, totalWidth, totalHeight);
    }
}