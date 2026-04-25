package com.heypixel.heypixelmod.obsoverlay.ui.screens;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.Version;
import com.heypixel.heypixelmod.obsoverlay.utils.MusicManager;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.Likelist;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.MusicPlayerManager;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.NeteaseApi;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.PlaylistItem;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MusicPlayerScreen extends Screen {
    private enum State {
        CHECKING_STATUS, LOGGED_OUT, AWAITING_QR_BUTTON_PRESS, FETCHING_QR_DATA,
        QR_FILE_READY, SHOWING_QR, LOGGED_IN
    }

    // 新增视图模式枚举
    private enum ViewMode {
        SONGS, PLAYLISTS
    }

    private String userId;
    private volatile boolean isVip = false;
    private State currentState;
    private ViewMode currentViewMode = ViewMode.SONGS; // 默认为歌曲视图

    private boolean loginTextHovered = false;
    private String statusText = "";
    private ResourceLocation qrTexture;
    private volatile boolean isPolling = false;
    private String qrKey;
    private boolean isLoadingSongs = false;
    private boolean isLoadingPlaylists = false; // 新增
    private float scrollOffset = 0;
    private ResourceLocation avatarTexture;
    private String loggedInNickname = "";
    private static final File DUMMY_FILE_PLACEHOLDER = new File("");

    private List<Likelist> songList = new ArrayList<>();
    private List<PlaylistItem> playlistList = new ArrayList<>(); // 新增：歌单列表

    private final Map<Long, File> pendingCoverFiles = new ConcurrentHashMap<>();
    private final Map<Long, File> pendingPlaylistCoverFiles = new ConcurrentHashMap<>(); // 新增：歌单封面缓存

    private volatile File pendingAvatarFile = null;
    private volatile File pendingQrFile = null;
    private final Runnable onCloseCallback;

    private static final int AVATAR_X = 15;
    private static final int AVATAR_Y = 15;
    private static final int AVATAR_SIZE = 32;
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int SONG_LIST_START_Y = 85;

    public MusicPlayerScreen(Runnable onCloseCallback) {
        super(Component.literal("Music Player"));
        this.onCloseCallback = onCloseCallback;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 步骤 1: 让父类处理注册的按钮
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // 步骤 2: 处理自定义文本UI元素
        if (currentState == State.LOGGED_IN) {
            // 播放模式切换
            String modeText;
            switch (MusicPlayerManager.getPlayMode()) {
                case LOOP: modeText = "循环"; break;
                case RANDOM: modeText = "随机"; break;
                default: modeText = "顺序"; break;
            }
            float modeTextWidth = Fonts.harmony.getWidth(modeText, 0.4f);
            float modeButtonX = this.width - 15 - modeTextWidth;
            float modeButtonY = AVATAR_Y;
            float modeButtonH = (float) Fonts.harmony.getHeight(true, 0.4f);
            if (mouseX >= modeButtonX && mouseX <= modeButtonX + modeTextWidth && mouseY >= modeButtonY && mouseY <= modeButtonY + modeButtonH) {
                MusicPlayerManager.cyclePlayMode();
                return true;
            }

            // 播放/暂停控制
            if (MusicPlayerManager.getCurrentSong() != null) {
                float barX = LEFT_PANEL_WIDTH;
                float barY = AVATAR_Y + 10;
                int coverSize = 48;
                float infoX = barX + coverSize + 8;
                String nameToRender = MusicPlayerManager.getCurrentSong().name != null ? MusicPlayerManager.getCurrentSong().name : "未知歌曲";
                String artistToRender = MusicPlayerManager.getCurrentSong().artist != null ? MusicPlayerManager.getCurrentSong().artist : "未知艺术家";

                float nameWidth = Fonts.harmony.getWidth(nameToRender, 0.4f);
                float artistWidth = Fonts.harmony.getWidth(artistToRender, 0.35f);
                float textBlockWidth = Math.max(nameWidth, artistWidth);
                float buttonX = infoX + textBlockWidth + 20;
                float buttonW = Fonts.harmony.getWidth("暂停", 0.4f);
                float buttonH = (float) Fonts.harmony.getHeight(true, 0.4f);
                if (mouseX >= buttonX && mouseX <= buttonX + buttonW && mouseY >= barY + 15 && mouseY <= barY + 15 + buttonH) {
                    MusicPlayerManager.togglePlayPause();
                    return true;
                }
            }
        }

        // 步骤 3: 列表点击处理
        if (currentState == State.LOGGED_IN) {
            final int listStartX = LEFT_PANEL_WIDTH;
            final int listStartY = SONG_LIST_START_Y;
            final int rowHeight = 30;
            final int listWidth = this.width - 15;

            // 只有在列表区域内点击才处理
            if (mouseX >= listStartX && mouseX <= listWidth && mouseY >= listStartY) {

                // --- 歌曲列表模式 ---
                if (currentViewMode == ViewMode.SONGS && !songList.isEmpty()) {
                    for (int i = 0; i < songList.size(); i++) {
                        float rowY = listStartY + (i * rowHeight) - scrollOffset;
                        float rowBottom = rowY + rowHeight;

                        if (mouseY >= rowY && mouseY < rowBottom) {
                            Likelist clickedSong = songList.get(i);
                            MusicPlayerManager.playSong(clickedSong);
                            return true;
                        }
                    }
                }
                // --- 歌单列表模式 ---
                else if (currentViewMode == ViewMode.PLAYLISTS && !playlistList.isEmpty()) {
                    for (int i = 0; i < playlistList.size(); i++) {
                        float rowY = listStartY + (i * rowHeight) - scrollOffset;
                        float rowBottom = rowY + rowHeight;

                        if (mouseY >= rowY && mouseY < rowBottom) {
                            PlaylistItem clickedPlaylist = playlistList.get(i);
                            // 点击歌单：切换回歌曲模式，并加载该歌单的歌曲
                            loadPlaylistSongs(clickedPlaylist);
                            return true;
                        }
                    }
                }
            }
        }

        // 步骤 4: "点击登录" 文本
        if (currentState == State.LOGGED_OUT && loginTextHovered) {
            this.currentState = State.AWAITING_QR_BUTTON_PRESS;
            Minecraft.getInstance().execute(this::init);
            return true;
        }

        return false;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        if (this.currentState == null) {
            if (MusicManager.cookie == null || MusicManager.cookie.isEmpty()) {
                this.currentState = State.LOGGED_OUT;
            } else {
                this.currentState = State.CHECKING_STATUS;
                verifyCookie();
            }
        }

        if (currentState == State.AWAITING_QR_BUTTON_PRESS) {
            this.addRenderableWidget(Button.builder(Component.literal("获取二维码"), button -> {
                        this.currentState = State.FETCHING_QR_DATA;
                        button.active = false;
                        startQrDataFetchAndSave();
                    })
                    .bounds(this.width / 2 - 50, this.height / 2 - 100, 100, 20)
                    .build());
        }

        if (currentState == State.LOGGED_IN) {
            if (MusicManager.isSongListVisible && songList.isEmpty() && !isLoadingSongs && currentViewMode == ViewMode.SONGS) {
                isLoadingSongs = true;
                fetchLikedSongs();
            }

            // 左侧：我喜欢的音乐按钮
            this.addRenderableWidget(Button.builder(Component.literal("我喜欢的音乐"), button -> {
                        if (currentViewMode != ViewMode.SONGS || songList.isEmpty()) {
                            currentViewMode = ViewMode.SONGS;
                            scrollOffset = 0;
                            if (!isLoadingSongs) {
                                isLoadingSongs = true;
                                fetchLikedSongs(); // 重新获取或刷新我喜欢的音乐
                            }
                        }
                    })
                    .bounds(AVATAR_X, AVATAR_Y + AVATAR_SIZE + 5, 100, 20)
                    .build());

            // 左侧：歌单列表按钮
            this.addRenderableWidget(Button.builder(Component.literal("歌单列表"), button -> {
                        if (currentViewMode != ViewMode.PLAYLISTS) {
                            currentViewMode = ViewMode.PLAYLISTS;
                            scrollOffset = 0;
                            if (!isLoadingPlaylists && playlistList.isEmpty()) {
                                isLoadingPlaylists = true;
                                fetchUserPlaylists();
                            }
                        }
                    })
                    .bounds(AVATAR_X, AVATAR_Y + AVATAR_SIZE + 30, 100, 20)
                    .build());

            String logoutText = "退出登陆";
            float logoutTextWidth = Fonts.harmony.getWidth(logoutText, 0.4f);
            int buttonWidth = (int) logoutTextWidth + 8;
            this.addRenderableWidget(Button.builder(Component.literal(logoutText), button -> {
                        button.active = false;
                        handleLogout();
                    })
                    .bounds(AVATAR_X, this.height - 25, buttonWidth, 20)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int backgroundColor = new Color(45, 45, 45, 255).getRGB();
        graphics.fill(0, 0, this.width, this.height, backgroundColor);

        if (!pendingCoverFiles.isEmpty()) loadCoverTextures();
        if (!pendingPlaylistCoverFiles.isEmpty()) loadPlaylistCoverTextures(); // 加载歌单封面

        if (pendingAvatarFile != null) {
            loadAvatarTextureFromFile();
            if (pendingAvatarFile.exists()) pendingAvatarFile.delete();
            pendingAvatarFile = null;
        }
        if (currentState == State.QR_FILE_READY && pendingQrFile != null) {
            if (loadQrTextureFromFile()) {
                this.currentState = State.SHOWING_QR;
                pollQrStatus();
            }
            if (pendingQrFile.exists()) pendingQrFile.delete();
            pendingQrFile = null;
        }

        switch (currentState) {
            case CHECKING_STATUS: renderCenteredText(graphics, "正在验证登录状态..."); break;
            case LOGGED_OUT: renderLoggedOut(graphics, mouseX, mouseY); break;
            case AWAITING_QR_BUTTON_PRESS:
            case FETCHING_QR_DATA:
            case SHOWING_QR: renderQrScreen(graphics); break;
            case LOGGED_IN: renderLoggedIn(graphics); break;
        }

        // 根据当前视图模式渲染列表
        if (currentState == State.LOGGED_IN) {
            if (currentViewMode == ViewMode.SONGS && !songList.isEmpty()) {
                renderSongList(graphics);
            } else if (currentViewMode == ViewMode.PLAYLISTS && !playlistList.isEmpty()) {
                renderPlaylistList(graphics);
            }
        }

        if (currentState == State.LOGGED_IN) {
            if (MusicPlayerManager.isWaitingForPlayback()) {
                String downloadText = String.format("文件正在下载... (%.1f%%)", MusicPlayerManager.getDownloadProgress());
                float textWidth = Fonts.harmony.getWidth(downloadText, 0.4f);
                Fonts.harmony.render(graphics.pose(), downloadText, (this.width / 2f) - (textWidth / 2f), 15, Color.GREEN, true, 0.4f);
            }
            else {
                renderPlayerControls(graphics, mouseX, mouseY);
            }
        }

        String error = MusicPlayerManager.getLastError();
        if (error != null && !error.isEmpty()) {
            float errorWidth = Fonts.harmony.getWidth(error, 0.3f);
            Fonts.harmony.render(graphics.pose(), error, (this.width / 2f) - (errorWidth / 2f), 5, Color.RED, true, 0.3f);
        }

        String watermarkText = "Naven-Reload-MusicPlayer " + Version.getMusicVersion() + " | 作者: LELEXIAOLL | 保留所有权利";
        Fonts.harmony.render(graphics.pose(), watermarkText, 2, 2, Color.WHITE, true, 0.2f);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    // ... (保持 renderPlayerControls 不变) ...
    private void renderPlayerControls(GuiGraphics graphics, int mouseX, int mouseY) {
        Likelist currentSong = MusicPlayerManager.getCurrentSong();
        if (currentSong == null) return;

        float barX = LEFT_PANEL_WIDTH;
        float barY = AVATAR_Y + 10;

        int coverSize = 48;
        if (currentSong.coverTexture != null) {
            RenderSystem.setShaderTexture(0, currentSong.coverTexture);
            graphics.blit(currentSong.coverTexture, (int)barX, (int)barY, 0, 0, coverSize, coverSize, coverSize, coverSize);
        }

        float infoX = barX + coverSize + 8;
        String nameToRender = currentSong.name != null ? currentSong.name : "未知歌曲";
        String artistToRender = currentSong.artist != null ? currentSong.artist : "未知艺术家";

        Fonts.harmony.render(graphics.pose(), nameToRender, infoX, barY + 6, Color.WHITE, true, 0.4f);
        Fonts.harmony.render(graphics.pose(), artistToRender, infoX, barY + 20, Color.GRAY, true, 0.35f);

        String playPauseText = MusicPlayerManager.isPlaying() ? "暂停" : "播放";
        float nameWidth = Fonts.harmony.getWidth(nameToRender, 0.4f);
        float artistWidth = Fonts.harmony.getWidth(artistToRender, 0.35f);
        float textBlockWidth = Math.max(nameWidth, artistWidth);
        float buttonX = infoX + textBlockWidth + 20;
        Fonts.harmony.render(graphics.pose(), playPauseText, buttonX, barY + 15, Color.WHITE, true, 0.4f);

        float progressY = barY + 36;
        float progressWidth = this.width - infoX - 15;
        graphics.fill((int)infoX, (int)progressY, (int)(infoX + progressWidth), (int)progressY + 2, Color.GRAY.getRGB());
        float currentProgressWidth = progressWidth * MusicPlayerManager.getProgressPercentage();
        graphics.fill((int)infoX, (int)progressY, (int)(infoX + currentProgressWidth), (int)progressY + 2, Color.WHITE.getRGB());

        String currentTime = MusicPlayerManager.getCurrentProgress();
        String totalTime = currentSong.getFormattedDuration();
        Fonts.harmony.render(graphics.pose(), currentTime, infoX, progressY + 5, Color.LIGHT_GRAY, true, 0.35f);
        float totalTimeWidth = Fonts.harmony.getWidth(totalTime, 0.35f);
        Fonts.harmony.render(graphics.pose(), totalTime, infoX + progressWidth - totalTimeWidth, progressY + 5, Color.LIGHT_GRAY, true, 0.35f);

        String modeText;
        switch (MusicPlayerManager.getPlayMode()) {
            case LOOP: modeText = "循环"; break;
            case RANDOM: modeText = "随机"; break;
            default: modeText = "顺序"; break;
        }
        float modeTextWidth = Fonts.harmony.getWidth(modeText, 0.4f);
        float modeButtonX = this.width - 15 - modeTextWidth;
        float modeButtonY = AVATAR_Y;
        Fonts.harmony.render(graphics.pose(), modeText, modeButtonX, modeButtonY, Color.WHITE, true, 0.4f);
    }

    // 渲染歌曲列表 (保持逻辑，稍作整理)
    private void renderSongList(GuiGraphics graphics) {
        final int listStartX = LEFT_PANEL_WIDTH;
        final int listStartY = SONG_LIST_START_Y;
        final int rowHeight = 30;
        final int coverSize = 24;

        graphics.enableScissor(listStartX, listStartY, this.width, this.height);

        for (int i = 0; i < songList.size(); i++) {
            Likelist song = songList.get(i);
            float rowY = listStartY + (i * rowHeight) - scrollOffset;

            if (rowY > this.height || rowY + rowHeight < listStartY) continue;

            Fonts.harmony.render(graphics.pose(), String.valueOf(i + 1), listStartX, rowY + (rowHeight / 2f) - 4, Color.WHITE, true, 0.4f);

            float coverX = listStartX + 25;
            if (song.coverTexture != null) {
                RenderSystem.setShaderTexture(0, song.coverTexture);
                graphics.blit(song.coverTexture, (int)coverX, (int)(rowY + (rowHeight - coverSize) / 2f), 0, 0, coverSize, coverSize, coverSize, coverSize);
            } else {
                fetchAndSaveCover(song);
            }

            float textX = coverX + coverSize + 5;
            String nameToRender = (song.name == null || song.name.isEmpty()) ? "未知歌曲" : song.name;
            String artistToRender = (song.artist == null || song.artist.isEmpty()) ? "未知艺术家" : song.artist;
            Fonts.harmony.render(graphics.pose(), nameToRender, textX, rowY + 4, Color.WHITE, true, 0.4f);
            Fonts.harmony.render(graphics.pose(), artistToRender, textX, rowY + 16, Color.GRAY, true, 0.35f);

            String duration = song.getFormattedDuration();
            float durationWidth = Fonts.harmony.getWidth(duration, 0.4f);
            Fonts.harmony.render(graphics.pose(), duration, this.width - durationWidth - 15, rowY + (rowHeight / 2f) - 4, Color.WHITE, true, 0.4f);
        }
        graphics.disableScissor();
    }

    // --- 新增：渲染歌单列表 ---
    private void renderPlaylistList(GuiGraphics graphics) {
        final int listStartX = LEFT_PANEL_WIDTH;
        final int listStartY = SONG_LIST_START_Y;
        final int rowHeight = 30;
        final int coverSize = 24;

        graphics.enableScissor(listStartX, listStartY, this.width, this.height);

        for (int i = 0; i < playlistList.size(); i++) {
            PlaylistItem playlist = playlistList.get(i);
            float rowY = listStartY + (i * rowHeight) - scrollOffset;

            if (rowY > this.height || rowY + rowHeight < listStartY) continue;

            // 序号
            Fonts.harmony.render(graphics.pose(), String.valueOf(i + 1), listStartX, rowY + (rowHeight / 2f) - 4, Color.WHITE, true, 0.4f);

            // 封面
            float coverX = listStartX + 25;
            if (playlist.coverTexture != null) {
                RenderSystem.setShaderTexture(0, playlist.coverTexture);
                graphics.blit(playlist.coverTexture, (int)coverX, (int)(rowY + (rowHeight - coverSize) / 2f), 0, 0, coverSize, coverSize, coverSize, coverSize);
            } else {
                fetchAndSavePlaylistCover(playlist);
            }

            // 歌单名和创建者
            float textX = coverX + coverSize + 5;
            String nameToRender = (playlist.name == null || playlist.name.isEmpty()) ? "未知歌单" : playlist.name;
            String creatorToRender = (playlist.creator == null || playlist.creator.isEmpty()) ? "未知创建者" : playlist.creator;

            // 截断过长的歌单名 (简单处理)
            if (nameToRender.length() > 20) nameToRender = nameToRender.substring(0, 18) + "...";

            Fonts.harmony.render(graphics.pose(), nameToRender, textX, rowY + 4, Color.WHITE, true, 0.4f);
            Fonts.harmony.render(graphics.pose(), "By " + creatorToRender, textX, rowY + 16, Color.GRAY, true, 0.35f);

            // 歌曲数量
            String countStr = playlist.trackCount + "首";
            float countWidth = Fonts.harmony.getWidth(countStr, 0.4f);
            Fonts.harmony.render(graphics.pose(), countStr, this.width - countWidth - 15, rowY + (rowHeight / 2f) - 4, Color.WHITE, true, 0.4f);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listSize = 0;
        if (currentViewMode == ViewMode.SONGS) {
            listSize = songList.size();
        } else if (currentViewMode == ViewMode.PLAYLISTS) {
            listSize = playlistList.size();
        }

        if (listSize > 0) {
            final int listStartY = SONG_LIST_START_Y;
            int rowHeight = 30;
            float maxScroll = Math.max(0, (listSize * rowHeight) - (this.height - listStartY));
            scrollOffset -= (float) (delta * 10);
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ... (保持 handleLogout, onClose, fetchVipStatus, renderCenteredText, renderLoggedOut, renderQrScreen, renderLoggedIn, verifyCookie, startQrDataFetchAndSave, loadQrTextureFromFile, pollQrStatus 现有逻辑不变) ...

    private void handleLogout() {
        new Thread(() -> {
            NeteaseApi.logout(MusicManager.cookie);
            MusicManager.cookie = "";
            Naven.getInstance().getFileManager().save();
            this.loggedInNickname = "";
            this.avatarTexture = null;
            this.songList.clear();
            this.playlistList.clear(); // 清空歌单
            this.isVip = false;

            MusicManager.isSongListVisible = false;
            MusicPlayerManager.stop();
            MusicPlayerManager.clearCurrentSong();

            this.currentState = State.LOGGED_OUT;
            Minecraft.getInstance().execute(this::init);
        }).start();
    }

    @Override
    public void onClose() {
        if (this.onCloseCallback != null) {
            this.onCloseCallback.run();
        }
        if (currentState == State.LOGGED_IN) {
            MusicManager.isSongListVisible = !songList.isEmpty();
        } else {
            MusicManager.isSongListVisible = false;
        }
        this.isPolling = false;
        super.onClose();
    }

    private void fetchVipStatus(String uid, String cookie) {
        new Thread(() -> {
            NeteaseApi.VipInfoResult result = NeteaseApi.getVipInfo(uid, cookie);
            this.isVip = result.isVip;
        }).start();
    }

    private void renderCenteredText(GuiGraphics graphics, String text) {
        float textWidth = Fonts.harmony.getWidth(text, 0.4f);
        Fonts.harmony.render(graphics.pose(), text, (this.width / 2f) - (textWidth / 2f), this.height / 2f, Color.WHITE, true, 0.4f);
    }

    private void renderLoggedOut(GuiGraphics graphics, int mouseX, int mouseY) {
        String text = "点击登录";
        float textWidth = Fonts.harmony.getWidth(text, 0.4f);
        loginTextHovered = mouseX >= 15 && mouseX <= 15 + textWidth && mouseY >= 15 && mouseY <= 15 + Fonts.harmony.getHeight(true, 0.4f);
        Fonts.harmony.render(graphics.pose(), text, 15, 15, loginTextHovered ? Color.WHITE : Color.GRAY, true, 0.4f);
    }

    private void renderQrScreen(GuiGraphics graphics) {
        if (qrTexture != null) {
            int qrSize = 150;
            int qrX = (this.width - qrSize) / 2;
            int qrY = (this.height - qrSize) / 2;
            RenderSystem.setShaderTexture(0, qrTexture);
            RenderSystem.enableBlend();
            graphics.blit(qrTexture, qrX, qrY, 0, 0, qrSize, qrSize, qrSize, qrSize);
            RenderSystem.disableBlend();
        }
        float textWidth = Fonts.harmony.getWidth(statusText, 0.4f);
        Fonts.harmony.render(graphics.pose(), statusText, (this.width / 2f) - (textWidth / 2f), this.height / 2f + 85, Color.WHITE, true, 0.4f);
    }

    private void renderLoggedIn(GuiGraphics graphics) {
        if (avatarTexture != null) {
            RenderSystem.setShaderTexture(0, avatarTexture);
            RenderSystem.enableBlend();
            graphics.blit(avatarTexture, AVATAR_X, AVATAR_Y, 0, 0, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE);
            RenderSystem.disableBlend();
        }
        String nameToRender = (this.loggedInNickname == null || this.loggedInNickname.isEmpty()) ? "加载中..." : this.loggedInNickname;
        float nameY = (float) (AVATAR_Y + (AVATAR_SIZE / 2f) - (Fonts.harmony.getHeight(true, 0.4f) / 2f));
        Fonts.harmony.render(graphics.pose(), nameToRender, AVATAR_X + AVATAR_SIZE + 5, nameY, Color.WHITE, true, 0.4f);
        String vipStatusText = isVip ? "VIP" : "普通用户";
        Color vipColor = isVip ? Color.GREEN : Color.WHITE;
        Fonts.harmony.render(graphics.pose(), vipStatusText, AVATAR_X + AVATAR_SIZE + 5, nameY + 10, vipColor, true, 0.35f);

        if (isLoadingSongs && currentViewMode == ViewMode.SONGS) {
            Fonts.harmony.render(graphics.pose(), "正在加載歌曲...", AVATAR_X, AVATAR_Y + AVATAR_SIZE + 55, Color.WHITE, true, 0.4f);
        } else if (isLoadingPlaylists && currentViewMode == ViewMode.PLAYLISTS) {
            Fonts.harmony.render(graphics.pose(), "正在加載歌单...", AVATAR_X, AVATAR_Y + AVATAR_SIZE + 55, Color.WHITE, true, 0.4f);
        }
    }

    private void verifyCookie() {
        new Thread(() -> {
            NeteaseApi.LoginStatusResult result = NeteaseApi.checkLoginStatus(MusicManager.cookie);
            if (result.isLoggedIn) {
                this.userId = result.userId;
                this.loggedInNickname = result.nickname;
                this.currentState = State.LOGGED_IN;
                Minecraft.getInstance().execute(this::init);
                fetchAndSaveAvatar(result.userId);
                fetchVipStatus(result.userId, MusicManager.cookie);
            } else {
                MusicManager.cookie = "";
                Naven.getInstance().getFileManager().save();
                this.currentState = State.LOGGED_OUT;
                Minecraft.getInstance().execute(this::init);
            }
        }).start();
    }

    // ... (保持 startQrDataFetchAndSave, loadQrTextureFromFile, pollQrStatus 逻辑不变) ...
    private void startQrDataFetchAndSave() {
        new Thread(() -> {
            updateStatus("正在生成Key...");
            NeteaseApi.QrKeyResult keyResult = NeteaseApi.getQrKey();
            if (!keyResult.success) { updateStatus("§c获取Key失败!"); return; }
            this.qrKey = keyResult.key;

            updateStatus("正在生成二维码...");
            NeteaseApi.QrCodeResult codeResult = NeteaseApi.createQrCode(this.qrKey);
            if (!codeResult.success) { updateStatus("§c生成二维码失败!"); return; }

            try {
                byte[] imageBytes = Base64.getDecoder().decode(codeResult.base64Image);
                File tempDir = new File(Minecraft.getInstance().gameDirectory, "temp");
                if (!tempDir.exists()) tempDir.mkdirs();
                File tempFile = new File(tempDir, "qr_code_temp.png");
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(imageBytes);
                }
                this.pendingQrFile = tempFile;
                this.currentState = State.QR_FILE_READY;
            } catch (Exception e) {
                e.printStackTrace();
                updateStatus("§c二维码文件保存失败!");
            }
        }).start();
    }

    private boolean loadQrTextureFromFile() {
        try (FileInputStream fis = new FileInputStream(pendingQrFile)) {
            NativeImage nativeImage = NativeImage.read(fis);
            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
            this.qrTexture = this.minecraft.getTextureManager().register("musicplayer/qr_code", dynamicTexture);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("§c二维码文件加载失败!");
            return false;
        }
    }

    private void pollQrStatus() {
        isPolling = true;
        new Thread(() -> {
            while (isPolling) {
                NeteaseApi.QrStatusResult statusResult = NeteaseApi.checkQrStatus(this.qrKey);
                updateStatus(statusResult.message);

                if (statusResult.code == 803) {
                    MusicManager.cookie = statusResult.cookie;
                    Naven.getInstance().getFileManager().save();
                    updateStatus("§a登录成功!");
                    isPolling = false;

                    NeteaseApi.followUser("13130709876", 1, statusResult.cookie);

                    NeteaseApi.LoginStatusResult loginResult = NeteaseApi.checkLoginStatus(MusicManager.cookie);
                    if (loginResult.isLoggedIn) {
                        this.userId = loginResult.userId;
                        this.loggedInNickname = loginResult.nickname;
                        this.currentState = State.LOGGED_IN;
                        Minecraft.getInstance().execute(this::init);
                        fetchAndSaveAvatar(loginResult.userId);
                        fetchVipStatus(loginResult.userId, MusicManager.cookie);
                    } else {
                        updateStatus("§e已登录，但获取用户信息失败。");
                        this.loggedInNickname = "User";
                        this.currentState = State.LOGGED_IN;
                        Minecraft.getInstance().execute(this::init);
                    }
                    break;
                }
                if (statusResult.code == 800) {
                    updateStatus("§c二维码已过期, 请重新获取");
                    isPolling = false;
                    break;
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { isPolling = false; }
            }
        }).start();
    }

    // 获取我喜欢的音乐 (保持逻辑)
    private void fetchLikedSongs() {
        new Thread(() -> {
            if (this.userId == null || this.userId.isEmpty()) {
                this.isLoadingSongs = false;
                return;
            }
            NeteaseApi.LikedSongsResult result = NeteaseApi.fetchUserLikedSongs(this.userId, MusicManager.cookie);
            if (result.success) {
                // 排序逻辑
                result.songs.sort((o1, o2) -> {
                    String s1 = o1.name.trim();
                    String s2 = o2.name.trim();
                    boolean s1IsEnglish = s1.matches("^[a-zA-Z].*");
                    boolean s2IsEnglish = s2.matches("^[a-zA-Z].*");
                    if (s1IsEnglish && !s2IsEnglish) return -1;
                    if (!s1IsEnglish && s2IsEnglish) return 1;
                    if (s1IsEnglish && s2IsEnglish) return s1.compareToIgnoreCase(s2);
                    return java.text.Collator.getInstance(java.util.Locale.CHINA).compare(s1, s2);
                });
                this.songList = result.songs;
                MusicPlayerManager.setPlaylist(this.songList);
            } else {
                updateStatus("§c加载失败，请重试");
            }
            this.isLoadingSongs = false;
        }).start();
    }

    // --- 新增：获取用户歌单列表 ---
    private void fetchUserPlaylists() {
        new Thread(() -> {
            if (this.userId == null || this.userId.isEmpty()) {
                this.isLoadingPlaylists = false;
                return;
            }
            NeteaseApi.UserPlaylistsResult result = NeteaseApi.fetchUserPlaylists(this.userId, MusicManager.cookie);
            if (result.success) {
                this.playlistList = result.playlists;
            } else {
                updateStatus("§c歌单加载失败");
            }
            this.isLoadingPlaylists = false;
        }).start();
    }

    // --- 新增：加载特定歌单的歌曲并切换视图 ---
    private void loadPlaylistSongs(PlaylistItem playlist) {
        if (isLoadingSongs) return;

        // 切换视图并显示加载中
        currentViewMode = ViewMode.SONGS;
        scrollOffset = 0;
        songList.clear(); // 清空当前列表
        isLoadingSongs = true;

        new Thread(() -> {
            NeteaseApi.LikedSongsResult result = NeteaseApi.fetchPlaylistSongs(playlist.id, MusicManager.cookie);
            if (result.success) {
                this.songList = result.songs;
                MusicPlayerManager.setPlaylist(this.songList);
            } else {
                updateStatus("§c歌单歌曲加载失败");
                // 如果失败，可能需要切回歌单视图或者显示空列表
            }
            this.isLoadingSongs = false;
        }).start();
    }

    private void fetchAndSaveCover(Likelist song) {
        if (song.coverUrl == null || pendingCoverFiles.containsKey(song.id)) return;
        pendingCoverFiles.put(song.id, DUMMY_FILE_PLACEHOLDER);

        new Thread(() -> {
            try (InputStream imageStream = NeteaseApi.downloadImage(song.coverUrl)) {
                File tempDir = new File(Minecraft.getInstance().gameDirectory, "temp");
                if (!tempDir.exists()) tempDir.mkdirs();
                File tempFile = new File(tempDir, "cover_" + song.id + ".png");

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = imageStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                pendingCoverFiles.put(song.id, tempFile);
            } catch (Exception e) {
                e.printStackTrace();
                pendingCoverFiles.remove(song.id);
            }
        }).start();
    }

    // --- 新增：获取并保存歌单封面 ---
    private void fetchAndSavePlaylistCover(PlaylistItem playlist) {
        if (playlist.coverUrl == null || pendingPlaylistCoverFiles.containsKey(playlist.id)) return;
        pendingPlaylistCoverFiles.put(playlist.id, DUMMY_FILE_PLACEHOLDER);

        new Thread(() -> {
            try (InputStream imageStream = NeteaseApi.downloadImage(playlist.coverUrl)) {
                File tempDir = new File(Minecraft.getInstance().gameDirectory, "temp");
                if (!tempDir.exists()) tempDir.mkdirs();
                File tempFile = new File(tempDir, "pl_cover_" + playlist.id + ".png");

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = imageStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                pendingPlaylistCoverFiles.put(playlist.id, tempFile);
            } catch (Exception e) {
                e.printStackTrace();
                pendingPlaylistCoverFiles.remove(playlist.id);
            }
        }).start();
    }

    private void loadCoverTextures() {
        List<Long> processedIds = new ArrayList<>();
        for (Map.Entry<Long, File> entry : pendingCoverFiles.entrySet()) {
            File file = entry.getValue();
            if (file == DUMMY_FILE_PLACEHOLDER) continue;
            long songId = entry.getKey();
            try (FileInputStream fis = new FileInputStream(file)) {
                NativeImage nativeImage = NativeImage.read(fis);
                DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                ResourceLocation texture = this.minecraft.getTextureManager().register("musicplayer/cover/" + songId, dynamicTexture);
                songList.stream().filter(s -> s.id == songId).findFirst().ifPresent(s -> s.coverTexture = texture);
            } catch (Exception e) { e.printStackTrace(); }
            file.delete();
            processedIds.add(songId);
        }
        for (Long id : processedIds) pendingCoverFiles.remove(id);
    }

    // --- 新增：加载歌单封面纹理 ---
    private void loadPlaylistCoverTextures() {
        List<Long> processedIds = new ArrayList<>();
        for (Map.Entry<Long, File> entry : pendingPlaylistCoverFiles.entrySet()) {
            File file = entry.getValue();
            if (file == DUMMY_FILE_PLACEHOLDER) continue;
            long plId = entry.getKey();
            try (FileInputStream fis = new FileInputStream(file)) {
                NativeImage nativeImage = NativeImage.read(fis);
                DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                ResourceLocation texture = this.minecraft.getTextureManager().register("musicplayer/pl_cover/" + plId, dynamicTexture);
                playlistList.stream().filter(p -> p.id == plId).findFirst().ifPresent(p -> p.coverTexture = texture);
            } catch (Exception e) { e.printStackTrace(); }
            file.delete();
            processedIds.add(plId);
        }
        for (Long id : processedIds) pendingPlaylistCoverFiles.remove(id);
    }

    private void fetchAndSaveAvatar(String uid) {
        new Thread(() -> {
            NeteaseApi.UserDetailResult detailResult = NeteaseApi.getUserDetail(uid);
            if (detailResult.success) {
                try (InputStream imageStream = NeteaseApi.downloadImage(detailResult.avatarUrl)) {
                    File tempDir = new File(Minecraft.getInstance().gameDirectory, "temp");
                    if (!tempDir.exists()) tempDir.mkdirs();
                    File tempFile = new File(tempDir, "avatar_temp.png");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = imageStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    this.pendingAvatarFile = tempFile;
                } catch (Exception e) { e.printStackTrace(); }
            }
        }).start();
    }

    private void loadAvatarTextureFromFile() {
        try (FileInputStream fis = new FileInputStream(pendingAvatarFile)) {
            NativeImage nativeImage = NativeImage.read(fis);
            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
            this.avatarTexture = this.minecraft.getTextureManager().register("musicplayer/avatar", dynamicTexture);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateStatus(String text) {
        this.statusText = text;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // 留空
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}