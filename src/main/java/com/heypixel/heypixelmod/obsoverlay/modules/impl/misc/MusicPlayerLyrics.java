package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.MusicPlayerManager;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.List;

@ModuleInfo(
        name = "MusicPlayerLyrics",
        description = "Renders song lyrics on screen",
        category = Category.MISC
)
public class MusicPlayerLyrics extends Module {

    // --- 值设置 ---
    public BooleanValue renderLyrics = ValueBuilder.create(this, "Render")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();
    public FloatValue lyricsX = ValueBuilder.create(this, "LyricsX")
            .setMinFloatValue(0.00F)
            .setMaxFloatValue(1.00F)
            .setDefaultFloatValue(0.50F)
            .setFloatStep(0.01F)
            .build()
            .getFloatValue();
    public FloatValue lyricsY = ValueBuilder.create(this, "LyricsY")
            .setMinFloatValue(0.00F)
            .setMaxFloatValue(1.00F)
            .setDefaultFloatValue(0.15F)
            .setFloatStep(0.01F)
            .build()
            .getFloatValue();
    public FloatValue lyricsSize = ValueBuilder.create(this, "Size")
            .setMinFloatValue(0.1F)
            .setMaxFloatValue(2.0F)
            .setDefaultFloatValue(1.0F)
            .setFloatStep(0.01F)
            .build()
            .getFloatValue();
    public FloatValue lineSpacing = ValueBuilder.create(this, "LineSpacing")
            .setMinFloatValue(0.1F)
            .setMaxFloatValue(5.0F)
            .setDefaultFloatValue(1.0F)
            .setFloatStep(0.05F)
            .build()
            .getFloatValue();

    // --- 颜色控制滑动条 ---
    public FloatValue colorR = ValueBuilder.create(this, "Color R")
            .setMinFloatValue(0F)
            .setMaxFloatValue(255F)
            .setDefaultFloatValue(66F)
            .setFloatStep(1F)
            .build()
            .getFloatValue();

    public FloatValue colorG = ValueBuilder.create(this, "Color G")
            .setMinFloatValue(0F)
            .setMaxFloatValue(255F)
            .setDefaultFloatValue(133F)
            .setFloatStep(1F)
            .build()
            .getFloatValue();

    public FloatValue colorB = ValueBuilder.create(this, "Color B")
            .setMinFloatValue(0F)
            .setMaxFloatValue(255F)
            .setDefaultFloatValue(244F)
            .setFloatStep(1F)
            .build()
            .getFloatValue();


    // --- 动画相关字段 ---
    private int lastLyricIndex = -1;
    private int animationTicksRemaining = 0;
    private static final int ANIMATION_DURATION_TICKS = 5;
    private int scrollDirection = 0;

    @EventTarget
    public void onRenderLyrics(EventRender2D event) {
        if (!renderLyrics.getCurrentValue() || !MusicPlayerManager.isPlaying() || MusicPlayerManager.getCurrentLyrics().isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        List<MusicPlayerManager.LyricLine> lyrics = MusicPlayerManager.getCurrentLyrics();
        int currentIndex = MusicPlayerManager.getCurrentLyricIndex();

        if (currentIndex < 0) {
            return;
        }

        // --- 动画控制逻辑 ---
        if (currentIndex != lastLyricIndex) {
            if (lastLyricIndex != -1 && Math.abs(currentIndex - lastLyricIndex) == 1) {
                animationTicksRemaining = ANIMATION_DURATION_TICKS;
                scrollDirection = (currentIndex > lastLyricIndex) ? 1 : -1;
            }
            lastLyricIndex = currentIndex;
        }

        boolean isAnimating = animationTicksRemaining > 0;
        float progress = 1.0f;
        if (isAnimating) {
            progress = (float)animationTicksRemaining / ANIMATION_DURATION_TICKS;
            animationTicksRemaining--;
        }
        float easedProgress = 1.0f - (float)Math.pow(1.0f - (1.0f - progress), 3);


        // --- 渲染准备 ---
        float centerX = screenWidth * lyricsX.getCurrentValue();
        float centerY = screenHeight * (1.0f - lyricsY.getCurrentValue());

        // ================== 核心修复点: 使用 (int) 进行类型转换 ==================
        final Color colorCurrent = new Color(
                (int) colorR.getCurrentValue(),
                (int) colorG.getCurrentValue(),
                (int) colorB.getCurrentValue()
        );
        // ======================================================================
        final Color colorOther = Color.WHITE;

        // --- 动态计算布局 ---
        float[] animatedLineHeights = new float[5];
        String[] lineTexts = new String[5];
        float[] animatedScales = new float[5];

        for (int i = -2; i <= 2; i++) {
            int arrayPos = i + 2;
            int lyricIndex = currentIndex + i;

            if (lyricIndex >= 0 && lyricIndex < lyrics.size()) {
                lineTexts[arrayPos] = lyrics.get(lyricIndex).text;
                float targetScale = getScaleForLine(i);
                float initialScale = 0.0f;

                if (isAnimating) {
                    int initialOffset = i + scrollDirection;
                    if (initialOffset >= -2 && initialOffset <= 2) {
                        initialScale = getScaleForLine(initialOffset);
                    }
                } else {
                    initialScale = targetScale;
                }
                float currentScale = initialScale + (targetScale - initialScale) * easedProgress;
                animatedScales[arrayPos] = currentScale;
                animatedLineHeights[arrayPos] = (float) Fonts.harmony.getHeight(true, currentScale);
            } else {
                lineTexts[arrayPos] = null;
            }
        }

        float totalHeight = 0;
        for (float h : animatedLineHeights) {
            if (h > 0) {
                totalHeight += (h * lineSpacing.getCurrentValue());
            }
        }
        float halfTotalHeight = totalHeight / 2.0f;
        float currentY = centerY - halfTotalHeight;

        // --- 执行渲染 ---
        for (int i = 0; i < 5; i++) {
            if (lineTexts[i] == null) {
                continue;
            }
            int offset = i - 2;
            float scale = animatedScales[i];
            Color color = (offset == 0) ? colorCurrent : colorOther;
            String text = lineTexts[i];
            float originalHeight = animatedLineHeights[i];
            float spacedHeight = originalHeight * lineSpacing.getCurrentValue();
            float drawY = currentY + (spacedHeight - originalHeight) / 2;
            float textWidth = Fonts.harmony.getWidth(text, scale);
            float textX = centerX - (textWidth / 2);

            if (scale > 0.01f) {
                Fonts.harmony.render(graphics.pose(), text, textX, drawY, color, true, scale);
            }
            currentY += spacedHeight;
        }
    }

    private float getScaleForLine(int offset) {
        float baseScale;
        switch (Math.abs(offset)) {
            case 0: baseScale = 1.2f; break;
            case 1: baseScale = 1.0f; break;
            case 2: baseScale = 0.8f; break;
            default: baseScale = 0.0f;
        }
        return baseScale * lyricsSize.getCurrentValue();
    }
}