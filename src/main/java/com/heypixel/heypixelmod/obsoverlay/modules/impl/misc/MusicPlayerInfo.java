package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.StencilUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.Likelist;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.MusicPlayerManager;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

@ModuleInfo(
        name = "MusicPlayerInfo",
        description = "Displays a box with current song information",
        category = Category.MISC
)
public class MusicPlayerInfo extends Module {

    // --- 值设置 (与之前保持不变) ---
    public FloatValue infoX = ValueBuilder.create(this, "Position X")
            .setMinFloatValue(0.00F)
            .setMaxFloatValue(1.00F)
            .setDefaultFloatValue(0.02F)
            .setFloatStep(0.01F)
            .build()
            .getFloatValue();

    public FloatValue infoY = ValueBuilder.create(this, "Position Y")
            .setMinFloatValue(0.00F)
            .setMaxFloatValue(1.00F)
            .setDefaultFloatValue(0.10F)
            .setFloatStep(0.01F)
            .build()
            .getFloatValue();

    public FloatValue infoSize = ValueBuilder.create(this, "Size")
            .setMinFloatValue(0.5F)
            .setMaxFloatValue(1.35F)
            .setDefaultFloatValue(1.0F)
            .setFloatStep(0.05F)
            .build()
            .getFloatValue();

    @EventTarget
    public void onRenderInfo(EventRender2D event) {
        if (!this.isEnabled() || MusicPlayerManager.getCurrentSong() == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Likelist currentSong = MusicPlayerManager.getCurrentSong();

        // --- 尺寸和位置计算 ---
        float scale = infoSize.getCurrentValue();
        float screenWidth = graphics.guiWidth();
        float screenHeight = graphics.guiHeight();
        float baseWidth = 220f;
        float baseHeight = 65f;
        float padding = 5f;
        float cornerRadius = 8f;
        float coverSize = baseHeight - (padding * 2);
        float width = baseWidth * scale;
        float height = baseHeight * scale;
        float scaledPadding = padding * scale;
        float scaledCoverSize = coverSize * scale;
        float scaledRadius = cornerRadius * scale;
        float x = screenWidth * infoX.getCurrentValue();
        float y = screenHeight * infoY.getCurrentValue();

        // --- 渲染背景 ---
        int backgroundColor = new Color(0, 0, 0, 120).getRGB();
        RenderUtils.drawRoundedRect(graphics.pose(), x, y, width, height, scaledRadius, backgroundColor);

        boolean isDownloading = MusicPlayerManager.isWaitingForPlayback();
        String originalName = (currentSong.name == null || currentSong.name.isEmpty()) ? "未知歌曲" : currentSong.name;
        String artistToRender = (currentSong.artist == null || currentSong.artist.isEmpty()) ? "未知艺术家" : currentSong.artist;
        String nameToRender;

        float fontScaleTitle = 0.4f * scale;
        float fontScaleArtist = 0.35f * scale;

        if (isDownloading) {
            // --- 下载状态 ---
            float textX = x + scaledPadding;
            float availableTextWidth = width - (scaledPadding * 2);

            // 截断过长的歌曲名
            if (Fonts.harmony.getWidth(originalName, fontScaleTitle) > availableTextWidth) {
                String ellipsis = "...";
                float ellipsisWidth = Fonts.harmony.getWidth(ellipsis, fontScaleTitle);
                String tempName = originalName;
                while (tempName.length() > 0 && Fonts.harmony.getWidth(tempName, fontScaleTitle) + ellipsisWidth > availableTextWidth) {
                    tempName = tempName.substring(0, tempName.length() - 1);
                }
                nameToRender = tempName + ellipsis;
            } else {
                nameToRender = originalName;
            }

            // 渲染文字
            float titleY = y + scaledPadding + 2f * scale;
            float artistY = (float) (titleY + (Fonts.harmony.getHeight(true, fontScaleTitle)) + (2f * scale));
            Fonts.harmony.render(graphics.pose(), nameToRender, textX, titleY, Color.WHITE, true, fontScaleTitle);
            Fonts.harmony.render(graphics.pose(), artistToRender, textX, artistY, Color.LIGHT_GRAY, true, fontScaleArtist);

            // 渲染下载提示
            String downloadText = "正在下载...";
            float fontScaleDownload = 0.35f * scale;
            float downloadY = (float) (y + height - scaledPadding - (Fonts.harmony.getHeight(true, fontScaleDownload)));
            Fonts.harmony.render(graphics.pose(), downloadText, textX, downloadY, Color.GREEN, true, fontScaleDownload);

        } else {
            // --- 正常播放状态 ---

            // ================== 核心修改点: 移除圆角渲染，直接绘制封面 ==================
            if (currentSong.coverTexture != null) {
                float coverX = x + scaledPadding;
                float coverY = y + scaledPadding;

                // 重置颜色状态并直接绘制图片
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.setShaderTexture(0, currentSong.coverTexture);
                RenderSystem.enableBlend();
                graphics.blit(currentSong.coverTexture, (int)coverX, (int)coverY, 0, 0,
                        (int)scaledCoverSize, (int)scaledCoverSize, (int)scaledCoverSize, (int)scaledCoverSize);
                RenderSystem.disableBlend();
            }
            // =======================================================================

            float textX = x + scaledPadding + scaledCoverSize + scaledPadding;
            float availableTextWidth = width - (scaledPadding * 3) - scaledCoverSize;

            // 截断过长的歌曲名
            if (Fonts.harmony.getWidth(originalName, fontScaleTitle) > availableTextWidth) {
                String ellipsis = "...";
                float ellipsisWidth = Fonts.harmony.getWidth(ellipsis, fontScaleTitle);
                String tempName = originalName;
                while (tempName.length() > 0 && Fonts.harmony.getWidth(tempName, fontScaleTitle) + ellipsisWidth > availableTextWidth) {
                    tempName = tempName.substring(0, tempName.length() - 1);
                }
                nameToRender = tempName + ellipsis;
            } else {
                nameToRender = originalName;
            }

            // 渲染文字
            float titleY = y + scaledPadding + 2f * scale;
            float artistY = (float) (titleY + (Fonts.harmony.getHeight(true, fontScaleTitle)) + (2f * scale));
            Fonts.harmony.render(graphics.pose(), nameToRender, textX, titleY, Color.WHITE, true, fontScaleTitle);
            Fonts.harmony.render(graphics.pose(), artistToRender, textX, artistY, Color.LIGHT_GRAY, true, fontScaleArtist);

            // 渲染进度条和时间
            float progressWidth = width - (scaledPadding * 3) - scaledCoverSize;
            float progressX = textX;
            float progressY = y + height - scaledPadding - (5f * scale);
            float progressHeight = 4f * scale;
            RenderUtils.fillBound(graphics.pose(), progressX, progressY, progressWidth, progressHeight, new Color(80, 80, 80).getRGB());
            float currentProgressWidth = progressWidth * MusicPlayerManager.getProgressPercentage();
            if (currentProgressWidth > 0) {
                RenderUtils.fillBound(graphics.pose(), progressX, progressY, currentProgressWidth, progressHeight, Color.WHITE.getRGB());
            }

            String currentTime = MusicPlayerManager.getCurrentProgress();
            String totalTime = currentSong.getFormattedDuration();
            String timeText = currentTime + " / " + totalTime;
            float fontScaleTime = 0.3f * scale;
            float timeTextWidth = Fonts.harmony.getWidth(timeText, fontScaleTime);
            float timeTextX = x + width - scaledPadding - timeTextWidth;
            float timeTextY = (float) (progressY - (Fonts.harmony.getHeight(true, fontScaleTime)) - (2f * scale));
            Fonts.harmony.render(graphics.pose(), timeText, timeTextX, timeTextY, Color.WHITE, true, fontScaleTime);
        }
    }
}