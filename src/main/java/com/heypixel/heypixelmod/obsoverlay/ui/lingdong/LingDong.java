package com.heypixel.heypixelmod.obsoverlay.ui.lingdong;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.Version;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleManager;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.AutoPlay;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.ChestStealer;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.LongJump;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.NameProtect;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Scaffold;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.HUD;
import com.heypixel.heypixelmod.obsoverlay.ui.hudeditor.HUDEditor;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.heypixel.heypixelmod.obsoverlay.utils.StencilUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.text.CustomTextRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class LingDong {
    private static final ResourceLocation LOGO = new ResourceLocation("heypixel", "logo/logo.png");
    private static final float MIN_WIDTH = 250.0F;
    private static final float NORMAL_HEIGHT = 90.0F;
    private static final float PADDING = 6.0F;
    private static final float ITEM_PADDING = 4.0F;
    private static final float CORNER_RADIUS = 10.0F;
    private static final boolean ENABLE_BLUR = true;
    private static final boolean ENABLE_BLOOM = true;
    private static final float BLOOM_STRENGTH = 1.0F;
    private static volatile float currentX, currentY, currentW, currentH;
    private static volatile boolean isVisible = false;
    private static boolean isAnimating = false;
    private static long animationStartTime = 0;
    private static final long ANIMATION_DURATION = 300;
    private static float currentPillWidth = 0.0F, currentPillHeight = 0.0F;
    private static float startWidth, endWidth, startHeight, endHeight;
    private static boolean wasLongJumpActive, wasAutoPlayActive, wasScaffoldActive, wasBacktrackActive, wasTabActive, wasChestStealerActive;
    private static ProgressBarAnimator scaffoldProgressAnimator, backtrackProgressAnimator, chestStealerProgressAnimator;
    private static SmoothAnimationTimer autoPlayProgressTimer = new SmoothAnimationTimer(0.0f);
    private static SmoothAnimationTimer longJumpKBProgressTimer = new SmoothAnimationTimer(0.0f);
    private static SmoothAnimationTimer longJumpFireballProgressTimer = new SmoothAnimationTimer(0.0f);
    private static SmoothAnimationTimer longJumpRingAngleTimer = new SmoothAnimationTimer(0.0f);
    private static final ResourceLocation WARN_ICON = new ResourceLocation("heypixel", "textures/noti/warn.png");
    private static final ResourceLocation ERROR_ICON = new ResourceLocation("heypixel", "textures/noti/error.png");
    private static final ResourceLocation YES_ICON = new ResourceLocation("heypixel", "textures/noti/yes.png");
    private static final ResourceLocation NO_ICON = new ResourceLocation("heypixel", "textures/noti/no.png");
    private static boolean wasNotificationActive;
    private static int lastNotificationCount = 0;

    private static final Map<Character, String> TEAM_COLOR_MAP = Map.of(
            '红', "§c", '黄', "§e", '蓝', "§1", '青', "§b",
            '紫', "§5", '绿', "§a", '橙', "§6", '粉', "§d", '灰', "§7"
    );

    private static final Map<Character, Integer> TEAM_ORDER_MAP = Map.of(
            '红', 0, '黄', 1, '蓝', 2, '青', 3,
            '紫', 4, '绿', 5, '橙', 6, '粉', 7, '灰', 8
    );
    private static class ProcessedPlayer {
        final String originalName;
        final String displayName;
        final int teamOrder;

        ProcessedPlayer(String originalName, String displayName, int teamOrder) {
            this.originalName = originalName;
            this.displayName = displayName;
            this.teamOrder = teamOrder;
        }
    }

    public static void renderShaderEffects(PoseStack poseStack, String eventType) {
        if (!isVisible || currentW <= 0 || currentH <= 0) return;
        float r = Math.max(0.0F, Math.min(CORNER_RADIUS, Math.min(currentW, currentH) / 2.0F - 0.5F));
        HUD hud = (HUD) Naven.getInstance().getModuleManager().getModule(HUD.class);
        float alphaSetting = hud != null ? hud.lingDongBgAlpha.getCurrentValue() : 55.0F;
        boolean shouldBlur = alphaSetting < 255.0F;

        if ("blur".equals(eventType) && ENABLE_BLUR && shouldBlur) {
            RenderUtils.drawRoundedRect(poseStack, currentX, currentY, currentW, currentH, r, Integer.MIN_VALUE);
        }
        if ("shadow".equals(eventType) && ENABLE_BLOOM) {
            float s = Math.max(0.0F, Math.min(1.0F, BLOOM_STRENGTH));
            int bgA = (int) Math.max(0.0F, Math.min(255.0F, alphaSetting));
            int aBase = (int) (bgA * s);
            if (aBase > 0) {
                RenderUtils.drawRoundedRect(poseStack, currentX, currentY, currentW, currentH, r, new Color(0, 0, 0, aBase).getRGB());
            }
        }
    }


    public static void render(GuiGraphics guiGraphics, CustomTextRenderer font, String version, String fps, String playerName, float watermarkSize, ModuleManager moduleManager) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        HUDEditor.HUDElement lingdongElement = HUDEditor.getInstance().getHUDElement("lingdong");
        if (lingdongElement == null) {
            isVisible = false;
            return;
        }

        LongJump longJumpModule = (LongJump) moduleManager.getModule(LongJump.class);
        boolean longJumpActive = longJumpModule != null && longJumpModule.isEnabled() && longJumpModule.renderStyles.isCurrentMode("LingDong");
        Scaffold scaffoldModule = (Scaffold) moduleManager.getModule(Scaffold.class);
        boolean scaffoldActive = scaffoldModule != null && scaffoldModule.isEnabled() && scaffoldModule.scaffoldblocksstyles.isCurrentMode("LingDong");
        ChestStealer chestStealerModule = (ChestStealer) moduleManager.getModule(ChestStealer.class);
        boolean chestStealerActive = chestStealerModule != null && chestStealerModule.isEnabled() && chestStealerModule.CSRender.getCurrentValue() && chestStealerModule.CSRenderMode.isCurrentMode("LingDong") && chestStealerModule.isCSWorking();
        HUD hudModule = (HUD) moduleManager.getModule(HUD.class);
        boolean betterTabEnabled = hudModule != null && hudModule.bettertab.getCurrentValue();
        boolean isTabActive = mc.options.keyPlayerList.isDown() && betterTabEnabled;
        AutoPlay autoPlayModule = (AutoPlay) moduleManager.getModule(AutoPlay.class);
        boolean autoPlayActive = autoPlayModule != null && autoPlayModule.isEnabled() && autoPlayModule.isWaiting();
        NotificationManager notificationManager = Naven.getInstance().getNotificationManager();
        boolean lingDongNotifyEnabled = hudModule != null && hudModule.notification.getCurrentValue() && hudModule.notificationStyle.isCurrentMode("LingDong");
        List<Notification> activeNotifications = lingDongNotifyEnabled ? notificationManager.getNotifications() : new ArrayList<>();
        boolean notificationActive = !activeNotifications.isEmpty();

        float textScale = 1.1f * watermarkSize;
        float textHeight = (float) Fonts.google.getHeight(true, textScale);
        float logoSize = textHeight;
        String normalText = "Naven-Reload | " + Version.getVersion() + " | FPS: " + fps + " | " + playerName;
        float normalTextWidth = Fonts.google.getWidth(normalText, textScale);
        float normalTotalWidth = Math.max(MIN_WIDTH * watermarkSize, normalTextWidth + logoSize + PADDING * 3);
        float targetWidth, targetHeight;

        if (longJumpActive) {
            targetHeight = NORMAL_HEIGHT * 1.4f * watermarkSize;
            targetWidth = normalTotalWidth * 1.3f;
        } else if (autoPlayActive) {
            targetHeight = (textHeight * 2 + PADDING * 3 * watermarkSize);
            targetWidth = normalTotalWidth * 0.8f;
        } else if (isTabActive) {
            targetHeight = calculateTabListHeight(textScale, watermarkSize);
            targetWidth = calculateTabListWidth(textScale, watermarkSize);
        } else if (scaffoldActive) {
            targetHeight = NORMAL_HEIGHT * 1.2f * watermarkSize;
            String scaffoldTest = "Scaffold Blocks: " + scaffoldModule.getTotalBlocksInHotbar() + "  BPS: " + String.format("%.1f", scaffoldModule.getCurrentBPS());
            float scaffoldTestWidth = Fonts.google.getWidth(scaffoldTest, textScale);
            targetWidth = Math.max(scaffoldTestWidth + 150f * watermarkSize + PADDING * 6 * watermarkSize, normalTotalWidth * 1.5f);
        } else if (chestStealerActive) {
            float scaledPadding = PADDING * watermarkSize;
            List<ItemStack> chestContents = chestStealerModule.getChestInventory();
            if (chestContents == null || chestContents.isEmpty()) {
                targetHeight = NORMAL_HEIGHT * watermarkSize;
                targetWidth = normalTotalWidth;
            } else {
                int cols = 9;
                int actualRows = (int) Math.ceil((double) chestContents.size() / cols);
                if (actualRows == 0) actualRows = 1;
                float itemScaledSize = (normalTotalWidth - scaledPadding * 2.0f - (cols - 1) * ITEM_PADDING * watermarkSize) / cols;
                float gridHeight = (actualRows * itemScaledSize) + (Math.max(0, actualRows - 1) * ITEM_PADDING * watermarkSize);
                targetHeight = (textHeight * 2 + scaledPadding) + scaledPadding + gridHeight + scaledPadding + 12.0f + (itemScaledSize / 2.0f);
                targetWidth = normalTotalWidth;
            }
        } else if (notificationActive) {
            final float NOTIFICATION_HEIGHT = 90.0f * watermarkSize;
            final float NOTIFICATION_GAP = 12.0f * watermarkSize;
            int notificationCount = activeNotifications.size();
            targetHeight = (NOTIFICATION_HEIGHT * notificationCount) + (NOTIFICATION_GAP * Math.max(0, notificationCount - 1)) + (2.0f * watermarkSize);

            float maxTextWidth = 0;
            for (Notification n : activeNotifications) {
                maxTextWidth = Math.max(maxTextWidth, Fonts.googlelittle.getWidth(n.getMessage(), textScale * 1.2f));
            }
            float iconAreaWidth = 50f * watermarkSize;
            targetWidth = PADDING * 2.5f + iconAreaWidth + PADDING * 2f + maxTextWidth + PADDING * 2.5f;

        } else {
            targetHeight = NORMAL_HEIGHT * watermarkSize;
            targetWidth = normalTotalWidth;
        }
        if (notificationActive != wasNotificationActive || (notificationActive && activeNotifications.size() != lastNotificationCount) ||
                isTabActive != wasTabActive || scaffoldActive != wasScaffoldActive || chestStealerActive != wasChestStealerActive ||
                autoPlayActive != wasAutoPlayActive || longJumpActive != wasLongJumpActive)
        {
            isAnimating = true;
            animationStartTime = System.currentTimeMillis();
            startWidth = currentPillWidth;
            startHeight = currentPillHeight;
            endWidth = targetWidth;
            endHeight = targetHeight;
            wasNotificationActive = notificationActive;
            lastNotificationCount = activeNotifications.size();
            wasScaffoldActive = scaffoldActive;
            wasTabActive = isTabActive;
            wasChestStealerActive = chestStealerActive;
            wasAutoPlayActive = autoPlayActive;
            wasLongJumpActive = longJumpActive;
        }

        if (isAnimating) {
            long elapsedTime = System.currentTimeMillis() - animationStartTime;
            float progress = Math.min((float) elapsedTime / ANIMATION_DURATION, 1.0f);
            float easedProgress = 1.0f - (float) Math.pow(1.0f - progress, 3.0);
            if (progress == 1.0f) isAnimating = false;
            currentPillWidth = startWidth + (endWidth - startWidth) * easedProgress;
            currentPillHeight = startHeight + (endHeight - startHeight) * easedProgress;
        } else {
            currentPillWidth = targetWidth;
            currentPillHeight = targetHeight;
        }
        if (currentPillWidth == 0.0F) {
            currentPillWidth = targetWidth;
            currentPillHeight = targetHeight;
        }

        float centerX = (float) lingdongElement.x;
        float topY = (float) lingdongElement.y;

        float pillX = centerX - (currentPillWidth / 2.0F);
        float pillY = topY;

        currentX = pillX;
        currentY = pillY;
        currentW = currentPillWidth;
        currentH = currentPillHeight;
        isVisible = true;

        float alphaSetting = hudModule != null ? hudModule.lingDongBgAlpha.getCurrentValue() : 55.0F;
        int bgA = (int) Math.max(0.0F, Math.min(255.0F, alphaSetting));
        int backgroundColor = new Color(0, 0, 0, bgA).getRGB();

        RenderUtils.drawRoundedRect(guiGraphics.pose(), pillX, pillY, currentPillWidth, currentPillHeight, CORNER_RADIUS, backgroundColor);
        guiGraphics.enableScissor((int)pillX, (int)pillY, (int)(pillX + currentPillWidth), (int)(pillY + currentPillHeight));

        if (longJumpActive) {
            renderLongJumpView(guiGraphics, pillX, pillY, currentPillWidth, currentPillHeight, textScale, watermarkSize, longJumpModule);
        } else if (autoPlayActive) {
            renderAutoPlayView(guiGraphics, pillX, pillY, currentPillWidth, currentPillHeight, textScale, watermarkSize, autoPlayModule);
        } else if (isTabActive) {
            renderPlayerListView(guiGraphics, pillX, pillY, textScale, watermarkSize);
        } else if (scaffoldActive) {
            renderScaffoldView(guiGraphics, pillX, pillY, currentPillWidth, currentPillHeight, textScale, watermarkSize, scaffoldModule);
        } else if (chestStealerActive) {
            renderChestStealerView(guiGraphics, pillX, pillY, currentPillWidth, textScale, watermarkSize, chestStealerModule);
        } else if (notificationActive) {
            renderNotificationView(guiGraphics, pillX, pillY, currentPillWidth, currentPillHeight, textScale, watermarkSize, activeNotifications);
        } else {
            float contentY = pillY + (currentPillHeight - textHeight) / 2.0f;
            float logoX = pillX + PADDING;
            renderTexture(guiGraphics, LOGO, logoX, contentY, logoSize, logoSize);
            float textX = logoX + logoSize + PADDING;
            Fonts.google.render(guiGraphics.pose(), normalText, textX, contentY, Color.WHITE, true, textScale);
        }

        guiGraphics.disableScissor();
        lingdongElement.width = currentPillWidth;
        lingdongElement.height = currentPillHeight;
    }


    private static void renderNotificationView(GuiGraphics guiGraphics, float x, float y, float width, float height, float textScale, float watermarkSize, List<Notification> notifications) {
        if (notifications.isEmpty()) return;

        final float NOTIFICATION_HEIGHT = 90.0f * watermarkSize;
        final float NOTIFICATION_GAP = 12.0f * watermarkSize;
        float currentY = y;

        for (Notification notification : notifications) {
            String message = notification.getMessage();
            String[] parts = message.split(" ");
            String moduleName = parts.length > 0 ? parts[0] : "Module";
            ResourceLocation icon;
            switch (notification.getLevel()) {
                case SUCCESS:
                    icon = YES_ICON;
                    break;
                case WARNING:
                    icon = WARN_ICON;
                    break;
                case ERROR:
                    if (message.contains("Disable")) {
                        icon = NO_ICON;
                    } else {
                        icon = ERROR_ICON;
                    }
                    break;
                case INFO:
                default:
                    icon = message.contains("Enable") ? YES_ICON : NO_ICON;
                    break;
            }

            float iconSize = 40f * watermarkSize;
            float iconX = x + PADDING * 2.5f;
            float iconY = currentY + (NOTIFICATION_HEIGHT - iconSize) / 2;
            renderTexture(guiGraphics, icon, iconX, iconY, iconSize, iconSize);

            float textX = iconX + iconSize + PADDING * 2f;
            float titleScale = textScale * 1.1f;
            float messageScale = textScale * 0.9f;

            float titleHeight = (float) Fonts.google.getHeight(true, titleScale);
            float messageHeight = (float) Fonts.googlelittle.getHeight(true, messageScale);

            float lineSpacing = 0f;
            float totalTextHeight = titleHeight + messageHeight + lineSpacing;
            float textStartY = currentY + (NOTIFICATION_HEIGHT - totalTextHeight) / 2;

            Fonts.google.render(guiGraphics.pose(), moduleName, textX, textStartY, Color.WHITE, true, titleScale);
            Fonts.googlelittle.render(guiGraphics.pose(), message, textX, textStartY + titleHeight + lineSpacing, Color.LIGHT_GRAY, true, messageScale);
            currentY += NOTIFICATION_HEIGHT + NOTIFICATION_GAP;
        }
    }

    private static List<ProcessedPlayer> getProcessedPlayerList() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener cpl = mc.getConnection();
        if (cpl == null) return new ArrayList<>();

        HUD hudModule = (HUD) Naven.getInstance().getModuleManager().getModule(HUD.class);
        boolean bwFixEnabled = hudModule != null && hudModule.betterTabBWFix.getCurrentValue();
        NameProtect nameProtectModule = (NameProtect) Naven.getInstance().getModuleManager().getModule(NameProtect.class);
        boolean nameProtectEnabled = nameProtectModule != null && nameProtectModule.isEnabled();
        String localPlayerName = mc.player != null ? mc.player.getGameProfile().getName() : "";

        List<PlayerInfo> originalPlayers = new ArrayList<>(cpl.getOnlinePlayers());

        if (!bwFixEnabled) {
            List<ProcessedPlayer> result = new ArrayList<>();
            for (PlayerInfo playerInfo : originalPlayers) {
                String name = playerInfo.getTabListDisplayName() != null ? playerInfo.getTabListDisplayName().getString() : playerInfo.getProfile().getName();
                if (nameProtectEnabled && playerInfo.getProfile().getName().equals(localPlayerName)) {
                    name = nameProtectModule.getName();
                }
                result.add(new ProcessedPlayer(name, name, 999));
            }
            return result;
        }

        List<ProcessedPlayer> teamPlayers = new ArrayList<>();
        List<ProcessedPlayer> otherPlayers = new ArrayList<>();

        for (PlayerInfo playerInfo : originalPlayers) {
            String originalName = playerInfo.getTabListDisplayName() != null ? playerInfo.getTabListDisplayName().getString() : playerInfo.getProfile().getName();
            if (nameProtectEnabled && playerInfo.getProfile().getName().equals(localPlayerName)) {
                originalName = nameProtectModule.getName();
            }

            String displayName = originalName;
            int teamOrder = 999;
            boolean isTeamPlayer = false;

            for (int i = 0; i < originalName.length() - 1; i++) {
                char currentChar = originalName.charAt(i);
                char nextChar = originalName.charAt(i + 1);

                if (TEAM_COLOR_MAP.containsKey(currentChar) && nextChar == '队') {
                    String before = originalName.substring(0, i);
                    String colorCode = TEAM_COLOR_MAP.get(currentChar);
                    String after = originalName.substring(i);

                    displayName = before + colorCode + after;
                    teamOrder = TEAM_ORDER_MAP.get(currentChar);
                    isTeamPlayer = true;
                    break;
                }
            }

            if (isTeamPlayer) {
                teamPlayers.add(new ProcessedPlayer(originalName, displayName, teamOrder));
            } else {
                otherPlayers.add(new ProcessedPlayer(originalName, originalName, 999));
            }
        }

        teamPlayers.sort(Comparator.comparingInt(p -> p.teamOrder));
        teamPlayers.addAll(otherPlayers);

        return teamPlayers;
    }

    private static float calculateTabListWidth(float textScale, float watermarkSize) {
        List<ProcessedPlayer> players = getProcessedPlayerList();
        if (players.isEmpty()) return MIN_WIDTH * watermarkSize;

        int maxPlayersPerColumn = 15;
        int columns = (int) Math.ceil((float) players.size() / maxPlayersPerColumn);
        if (columns == 0) columns = 1;

        float scaledPadding = PADDING * watermarkSize;
        float totalWidth = 0.0F;

        for (int col = 0; col < columns; col++) {
            float maxNameWidthInColumn = 0.0F;
            int startIndex = col * maxPlayersPerColumn;
            int endIndex = Math.min((col + 1) * maxPlayersPerColumn, players.size());

            for (int i = startIndex; i < endIndex; i++) {
                String name = players.get(i).originalName;
                // 修改：使用 Fonts.harmony 替代 Fonts.google
                float width = getApproximateTextWidth(name, Fonts.harmony, textScale);
                if (width > maxNameWidthInColumn) maxNameWidthInColumn = width;
            }
            // 修改：使用 Fonts.harmony 计算 aaaa 宽度
            totalWidth += maxNameWidthInColumn + Fonts.harmony.getWidth("aaaa", textScale);
        }

        if (columns > 1) {
            totalWidth += scaledPadding * (columns - 1);
        }

        return Math.max(MIN_WIDTH * watermarkSize, totalWidth + scaledPadding * 2);
    }

    private static void renderPlayerListView(GuiGraphics guiGraphics, float pillX, float pillY, float textScale, float watermarkSize) {
        List<ProcessedPlayer> playersToRender = getProcessedPlayerList();
        if (playersToRender.isEmpty()) return;

        int maxPlayersPerColumn = 15;
        float scaledPadding = PADDING * watermarkSize;
        float fontHeight = (float) Fonts.harmony.getHeight(true, textScale);
        float currentX = pillX + scaledPadding;
        int columns = (int) Math.ceil((float) playersToRender.size() / maxPlayersPerColumn);
        if (columns == 0) columns = 1;

        for (int col = 0; col < columns; col++) {
            float maxNameWidthInColumn = 0.0F;
            int startIndex = col * maxPlayersPerColumn;
            int endIndex = Math.min((col + 1) * maxPlayersPerColumn, playersToRender.size());

            for (int i = startIndex; i < endIndex; i++) {
                String name = playersToRender.get(i).originalName;
                float width = getApproximateTextWidth(name, Fonts.harmony, textScale);
                if (width > maxNameWidthInColumn) maxNameWidthInColumn = width;
            }
            float columnWidth = maxNameWidthInColumn + Fonts.harmony.getWidth("aaaa", textScale);

            for (int i = startIndex; i < endIndex; i++) {
                String nameToRender = playersToRender.get(i).displayName;
                int row = i % maxPlayersPerColumn;
                float y = pillY + scaledPadding + row * (fontHeight + 2);
                Fonts.harmony.render(guiGraphics.pose(), nameToRender, currentX, y, Color.WHITE, true, textScale);
            }

            currentX += columnWidth;
            if (col < columns - 1) {
                currentX += scaledPadding;
            }
        }
    }

    private static void renderLongJumpView(GuiGraphics guiGraphics, float pillX, float pillY, float totalWidth, float pillHeight, float textScale, float watermarkSize, LongJump longJumpModule) {
        float scaledPadding = PADDING * watermarkSize;
        float circleRadius = pillHeight / 4.0f;
        float circleX = pillX + scaledPadding + circleRadius;
        float circleY = pillY + pillHeight / 2.0f;
        float targetAngle = (System.currentTimeMillis() % 2000L) / 2000.0f * 360.0f;
        longJumpRingAngleTimer.target = targetAngle;
        longJumpRingAngleTimer.update(true);
        RenderUtils.drawRotationalRing(guiGraphics.pose(), circleX, circleY, circleRadius, 2.5f, 270.0f, longJumpRingAngleTimer.value, Color.WHITE.getRGB());
        float midTextX = circleX + circleRadius + scaledPadding;
        float midTextY = pillY + scaledPadding;
        Fonts.google.render(guiGraphics.pose(), "LongJump", midTextX, midTextY, Color.WHITE, true, textScale * 1.0f);
        midTextY += Fonts.google.getHeight(true, textScale * 1.0f) + 4;
        Fonts.googlelittle.render(guiGraphics.pose(), "Press Z to jump & use fireball", midTextX, midTextY, Color.LIGHT_GRAY, true, textScale * 0.8f);
        midTextY += Fonts.googlelittle.getHeight(true, textScale * 0.8f);
        Fonts.googlelittle.render(guiGraphics.pose(), "Press C to release each knockback", midTextX, midTextY, Color.LIGHT_GRAY, true, textScale * 0.8f);
        float barWidth = 5.0f;
        float barInternalPadding = 1.0f;
        float barHeight = pillHeight - scaledPadding * 2 - barInternalPadding * 2;
        float barX = pillX + totalWidth - scaledPadding - barWidth;
        float barY = pillY + scaledPadding + barInternalPadding;
        RenderUtils.drawRoundedRect(guiGraphics.pose(), barX, barY, barWidth, barHeight, 2.5f, 0x80000000);
        if (longJumpModule.isReleasing()) {
            float kbProgress = (float) (longJumpModule.getTotalKnockbacks() - longJumpModule.getCurrentKnockbacks()) / (float) Math.max(1, longJumpModule.getTotalKnockbacks());
            longJumpKBProgressTimer.target = kbProgress;
            longJumpKBProgressTimer.update(true);
            float animatedBarHeight = barHeight * longJumpKBProgressTimer.value;
            RenderUtils.drawRoundedRect(guiGraphics.pose(), barX, barY + (barHeight - animatedBarHeight), barWidth, animatedBarHeight, 2.5f, Color.WHITE.getRGB());
        } else if (longJumpModule.getUsedFireballs() > 0) {
            longJumpKBProgressTimer.target = 1.0f;
            longJumpKBProgressTimer.update(true);
            float animatedBarHeight = barHeight * longJumpKBProgressTimer.value;
            RenderUtils.drawRoundedRect(guiGraphics.pose(), barX, barY + (barHeight - animatedBarHeight), barWidth, animatedBarHeight, 2.5f, Color.WHITE.getRGB());
        } else {
            longJumpKBProgressTimer.target = 0.0f;
            longJumpKBProgressTimer.update(true);
        }
        String packetsText = "Intercepting: " + longJumpModule.getPacketCount() + " packets";
        String timeText = "Time: " + String.format("%.1f", longJumpModule.getTimeElapsed()) + "s";
        String kbText = "Press C (" + longJumpModule.getCurrentKnockbacks() + "/" + longJumpModule.getTotalKnockbacks() + ")";
        float maxRightTextWidth = 0;
        maxRightTextWidth = Math.max(maxRightTextWidth, Fonts.google.getWidth(packetsText, textScale * 0.9f));
        maxRightTextWidth = Math.max(maxRightTextWidth, Fonts.google.getWidth(timeText, textScale * 0.9f));
        maxRightTextWidth = Math.max(maxRightTextWidth, Fonts.google.getWidth(kbText, textScale * 0.9f));
        float rightTextX = barX - maxRightTextWidth - scaledPadding;
        float rightTextY = pillY + scaledPadding;
        Fonts.google.render(guiGraphics.pose(), packetsText, rightTextX, rightTextY, Color.WHITE, true, textScale * 0.9f);
        rightTextY += Fonts.google.getHeight(true, textScale * 0.9f) + 2;
        Fonts.google.render(guiGraphics.pose(), timeText, rightTextX, rightTextY, Color.WHITE, true, textScale * 0.9f);
        rightTextY += Fonts.google.getHeight(true, textScale * 0.9f) + 2;
        Fonts.google.render(guiGraphics.pose(), kbText, rightTextX, rightTextY, Color.WHITE, true, textScale * 0.9f);
    }

    private static void renderAutoPlayView(GuiGraphics guiGraphics, float pillX, float pillY, float totalWidth, float pillHeight, float textScale, float watermarkSize, AutoPlay autoPlayModule) {
        float scaledPadding = PADDING * watermarkSize;
        autoPlayProgressTimer.target = autoPlayModule.getProgress();
        autoPlayProgressTimer.update(true);
        float animatedProgress = autoPlayProgressTimer.value;
        float circleRadius = pillHeight / 3.0f;
        float circleX = pillX + scaledPadding + circleRadius;
        float circleY = pillY + pillHeight / 2.0f;
        RenderUtils.drawHealthRing(guiGraphics.pose(), circleX, circleY, circleRadius, 3.0f, animatedProgress);
        float textX = circleX + circleRadius + scaledPadding;
        float titleY = circleY - (float)Fonts.google.getHeight(true, textScale);
        Fonts.google.render(guiGraphics.pose(), "AutoPlay", textX, titleY, Color.WHITE, true, textScale);
        String remainingText = "Wait " + String.format("%.1f", autoPlayModule.getRemainingSeconds()) + " second...";
        float remainingY = circleY;
        Fonts.googlelittle.render(guiGraphics.pose(), remainingText, textX, remainingY, Color.LIGHT_GRAY, true, textScale * 0.9f);
    }

    private static float calculateTabListHeight(float textScale, float watermarkSize) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener cpl = mc.getConnection();
        if (cpl == null) return NORMAL_HEIGHT * watermarkSize;
        int playerCount = cpl.getOnlinePlayers().size();
        int maxPlayersPerColumn = 15;
        int rows = Math.min(playerCount, maxPlayersPerColumn);
        return (float) (rows * (Fonts.harmony.getHeight(true, textScale) + 2) + PADDING * 2 * watermarkSize);
    }

    private static float getApproximateTextWidth(String text, CustomTextRenderer font, float scale) {
        float totalWidth = 0.0f;
        float twoEnglishCharsWidth = font.getWidth("aa", scale);
        float upperCaseWidth = font.getWidth("A", scale);
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') totalWidth += twoEnglishCharsWidth;
            else if (Character.isUpperCase(c)) totalWidth += upperCaseWidth;
            else totalWidth += font.getWidth(String.valueOf(c), scale);
        }
        return totalWidth;
    }

    private static void renderChestStealerView(GuiGraphics guiGraphics, float pillX, float pillY, float totalWidth, float textScale, float watermarkSize, ChestStealer chestStealerModule) {
        float scaledPadding = PADDING * watermarkSize;
        String title = "ChestStealer";
        float titleX = pillX + scaledPadding + 5f;
        float titleY = pillY + scaledPadding + 5f;
        Fonts.google.render(guiGraphics.pose(), title, titleX, titleY, Color.WHITE, true, textScale);
        String progressText = "Stealing: " + chestStealerModule.stolenItems + " / " + chestStealerModule.totalItemsToSteal;
        float progressTextY = (float) (titleY + Fonts.google.getHeight(true, textScale) + 2.0f);
        Fonts.google.render(guiGraphics.pose(), progressText, titleX, progressTextY, Color.WHITE, true, textScale);
        int cols = 9;
        float totalGridWidth = totalWidth - scaledPadding * 2.0f;
        float itemScaledSize = (totalGridWidth - (cols - 1) * ITEM_PADDING * watermarkSize) / cols;
        float itemScaledPadding = ITEM_PADDING * watermarkSize;
        List<ItemStack> chestContents = chestStealerModule.getChestInventory();
        if (chestContents == null) return;
        float gridStartX = pillX + scaledPadding;
        float gridStartY = (float) (progressTextY + Fonts.google.getHeight(true, textScale) + scaledPadding + 5f);
        float itemCenterOffset = (itemScaledSize - 16.0f) / 2.0f;
        for (int i = 0; i < chestContents.size(); i++) {
            ItemStack stack = chestContents.get(i);
            int row = i / cols, col = i % cols;
            float itemX = gridStartX + col * (itemScaledSize + itemScaledPadding);
            float itemY = gridStartY + row * (itemScaledSize + itemScaledPadding);
            RenderUtils.fillBound(guiGraphics.pose(), itemX, itemY, itemScaledSize, itemScaledSize, 0x40FFFFFF);
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, (int) (itemX + itemCenterOffset), (int) (itemY + itemCenterOffset));
                guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, (int) (itemX + itemCenterOffset), (int) (itemY + itemCenterOffset));
            }
        }
    }

    private static void renderScaffoldView(GuiGraphics guiGraphics, float pillX, float pillY, float totalWidth, float pillHeight, float textScale, float watermarkSize, Scaffold scaffoldModule) {
        int blocks = scaffoldModule != null ? scaffoldModule.getTotalBlocksInHotbar() : 0;
        double bps = scaffoldModule != null ? scaffoldModule.getCurrentBPS() : 0.0;
        if (scaffoldProgressAnimator == null) {
            scaffoldProgressAnimator = new ProgressBarAnimator((float) blocks, 2.0f);
        }
        scaffoldProgressAnimator.update((float) blocks);
        float animatedBlocks = scaffoldProgressAnimator.getDisplayedHealth();
        float scaledPadding = PADDING * watermarkSize;
        String scaffoldText = "Scaffold   Blocks: " + blocks + "   BPS: " + String.format("%.1f", bps);
        float scaffoldTextWidth = Fonts.google.getWidth(scaffoldText, textScale);
        float textY = (float) (pillY + (pillHeight - Fonts.google.getHeight(true, textScale)) / 2);
        float textX = pillX + scaledPadding * 3.0f;
        Fonts.google.render(guiGraphics.pose(), scaffoldText, textX, textY, Color.WHITE, true, textScale);
        float progressBarHeight = 4.0f;
        float progressBarWidth = totalWidth - (scaledPadding * 3.0f) * 2 - scaffoldTextWidth - scaledPadding;
        float progressBarX = textX + scaffoldTextWidth + scaledPadding;
        float progressBarY = (float) (pillY + (pillHeight - progressBarHeight) / 2);
        float progressBarRadius = progressBarHeight / 2.0f;
        RenderUtils.drawRoundedRect(guiGraphics.pose(), progressBarX, progressBarY, progressBarWidth, progressBarHeight, progressBarRadius, 0x80000000);
        float progress = Math.min(animatedBlocks / 64.0f, 1.0f);
        float foregroundWidth = Math.max(progressBarWidth * progress, progressBarRadius * 2.0f);
        StencilUtils.write(false);
        RenderUtils.drawRoundedRect(guiGraphics.pose(), progressBarX, progressBarY, foregroundWidth, progressBarHeight, progressBarRadius, Color.WHITE.getRGB());
        StencilUtils.erase(true);
        RenderUtils.fillBound(guiGraphics.pose(), progressBarX, progressBarY, foregroundWidth, progressBarHeight, Color.WHITE.getRGB());
        StencilUtils.dispose();
    }

    private static void renderTexture(GuiGraphics guiGraphics, ResourceLocation texture, float x, float y, float width, float height) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = guiGraphics.pose().last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix, x, y + height, 0).uv(0, 1).endVertex();
        bufferBuilder.vertex(matrix, x + width, y + height, 0).uv(1, 1).endVertex();
        bufferBuilder.vertex(matrix, x + width, y, 0).uv(1, 0).endVertex();
        bufferBuilder.vertex(matrix, x, y, 0).uv(0, 0).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static class ProgressBarAnimator {
        private float displayedProgress;
        private long lastUpdateTime;
        private final float animationSpeed;
        public ProgressBarAnimator(float initialProgress, float animationSpeed) {
            this.displayedProgress = initialProgress;
            this.lastUpdateTime = System.currentTimeMillis();
            this.animationSpeed = animationSpeed;
        }
        public void update(float targetProgress) {
            long currentTime = System.currentTimeMillis();
            long deltaTime = currentTime - this.lastUpdateTime;
            this.lastUpdateTime = currentTime;
            if (Math.abs(targetProgress - this.displayedProgress) < 0.1f) {
                this.displayedProgress = targetProgress;
                return;
            }
            float change = (targetProgress - this.displayedProgress) * (deltaTime / 1000.0f) * this.animationSpeed;
            this.displayedProgress += change;
        }
        public float getDisplayedHealth() {
            return displayedProgress;
        }
    }
}