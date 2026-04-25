package com.heypixel.heypixelmod.obsoverlay.utils.musicplayer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.heypixel.heypixelmod.obsoverlay.utils.MusicManager;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;

public class MusicPlayerManager {

    private static Process playerProcess;
    private static Thread outputReaderThread;

    private static final String EXE_NAME = "music_player.exe";
    private static final Path EXE_PATH = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(), EXE_NAME);
    private static final Path COMMAND_FILE_PATH = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "music_command.txt");

    public enum PlayMode { SEQUENTIAL, LOOP, RANDOM }

    private static Likelist currentSong;
    private static volatile boolean isPlaying = false;
    private static volatile boolean isPaused = false;
    private static volatile long songSwitchTime = 0;
    private static String lastError = "";
    private static volatile long currentPlaybackPositionMs = 0;
    private static volatile float downloadProgress = 0.0f;
    // ================== 新增歌词相关字段 ==================
    private static final Pattern LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");
    private static List<LyricLine> currentLyrics = Collections.synchronizedList(new ArrayList<>());
    private static volatile int currentLyricIndex = -1;
    // ===================================================

    private static PlayMode currentPlayMode = PlayMode.SEQUENTIAL;
    private static List<Likelist> currentPlaylist = new ArrayList<>();
    private static final Random random = new Random();

    public static void setup() {
        String resourcePath = "/assets/heypixel/musicplayer/" + EXE_NAME;
        try (InputStream in = MusicPlayerManager.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.err.println("[MusicPlayer] FATAL: Could not find player executable in Mod JAR!");
                lastError = "Core file missing, please check mod integrity.";
                return;
            }
            Files.copy(in, EXE_PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[MusicPlayer] Failed to deploy player executable:");
            e.printStackTrace();
        }
    }

    public static void start() {
        if (playerProcess != null && playerProcess.isAlive()) return;
        if (!Files.exists(EXE_PATH)) {
            lastError = "Player core not found! Please restart the game.";
            return;
        }
        try {
            Files.deleteIfExists(COMMAND_FILE_PATH);
            ProcessBuilder pb = new ProcessBuilder(EXE_PATH.toString());
            pb.directory(Minecraft.getInstance().gameDirectory);
            pb.redirectErrorStream(true);
            playerProcess = pb.start();
            outputReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(playerProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && playerProcess.isAlive()) {
                        final String finalLine = line;
                        Minecraft.getInstance().execute(() -> handlePythonOutput(finalLine));
                    }
                } catch (IOException e) { /* Ignore */ }
            });
            outputReaderThread.setDaemon(true);
            outputReaderThread.start();
        } catch (IOException e) {
            e.printStackTrace();
            lastError = "Failed to start player process: " + e.getMessage();
        }
    }

    public static void stop() {
        sendCommand("EXIT");
        try {
            if (playerProcess != null) playerProcess.waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (playerProcess != null && playerProcess.isAlive()) {
            String processName = "music_player.exe";
            String command = "taskkill /F /IM " + processName;
            try {
                Runtime.getRuntime().exec(command).waitFor();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        playerProcess = null;
        try {
            Files.deleteIfExists(COMMAND_FILE_PATH);
        } catch (IOException e) { /* Ignore */ }
    }

    private static void sendCommand(String command) {
        if (playerProcess == null || !playerProcess.isAlive()) return;
        try (BufferedWriter writer = Files.newBufferedWriter(COMMAND_FILE_PATH, StandardCharsets.UTF_8)) {
            writer.write(command);
        } catch (IOException e) {
            e.printStackTrace();
            lastError = "Failed to send command to player!";
        }
    }

    private static void handlePythonOutput(String line) {
        if (!line.startsWith("{")) return;
        try {
            JsonObject statusJson = JsonParser.parseString(line).getAsJsonObject();
            String status = statusJson.get("status").getAsString().toUpperCase();

            if (!status.equals("PROGRESS_UPDATE") && !status.equals("DOWNLOAD_PROGRESS")) {
                lastError = "";
            }

            switch (status) {
                case "PLAYING":
                case "RESUMED":
                    isPlaying = true;
                    isPaused = false;
                    songSwitchTime = 0;
                    downloadProgress = 0.0f; // 重置下载进度
                    break;

                case "PAUSED":
                    isPlaying = true;
                    isPaused = true;
                    break;

                case "PROGRESS_UPDATE":
                    if (statusJson.has("data") && statusJson.getAsJsonObject("data").has("position_ms")) {
                        long positionMs = statusJson.getAsJsonObject("data").get("position_ms").getAsLong();
                        currentPlaybackPositionMs = positionMs;
                        updateLyricIndex(positionMs);
                    }
                    break;

                case "DOWNLOAD_PROGRESS":
                    if (statusJson.has("data") && statusJson.getAsJsonObject("data").has("percentage")) {
                        downloadProgress = statusJson.getAsJsonObject("data").get("percentage").getAsFloat();
                    }
                    break;

                case "STOPPED":
                case "FINISHED":
                case "DOWNLOAD_INTERRUPTED":
                    isPlaying = false;
                    isPaused = false;
                    songSwitchTime = 0;
                    currentPlaybackPositionMs = 0;
                    downloadProgress = 0.0f;
                    currentLyricIndex = -1;
                    if (status.equals("FINISHED")) {
                        playNextSong();
                    } else if (!status.equals("DOWNLOAD_INTERRUPTED")) {
                        currentSong = null;
                    }
                    break;
                case "ERROR":
                    String message = statusJson.getAsJsonObject("data").get("message").getAsString();
                    lastError = "Player Error: " + message;
                    isPlaying = false;
                    isPaused = false;
                    currentSong = null;
                    songSwitchTime = 0;
                    currentPlaybackPositionMs = 0;
                    downloadProgress = 0.0f; // 重置下载进度
                    break;
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
    }

    private static void playNextSong() {
        if (currentSong == null || currentPlaylist == null || currentPlaylist.isEmpty()) {
            currentSong = null;
            return;
        }
        switch (currentPlayMode) {
            case LOOP: playSong(currentSong); break;
            case SEQUENTIAL:
                int currentIndex = -1;
                for (int i = 0; i < currentPlaylist.size(); i++) {
                    if (currentPlaylist.get(i).id == currentSong.id) {
                        currentIndex = i;
                        break;
                    }
                }
                if (currentIndex != -1) {
                    int nextIndex = (currentIndex + 1) % currentPlaylist.size();
                    playSong(currentPlaylist.get(nextIndex));
                } else {
                    playSong(currentPlaylist.get(0));
                }
                break;
            case RANDOM:
                if (currentPlaylist.size() <= 1) {
                    playSong(currentPlaylist.get(0));
                } else {
                    int nextIndex = random.nextInt(currentPlaylist.size());
                    if (currentPlaylist.get(nextIndex).id == currentSong.id) {
                        nextIndex = (nextIndex + 1) % currentPlaylist.size();
                    }
                    playSong(currentPlaylist.get(nextIndex));
                }
                break;
        }
    }

    public static void playSong(Likelist song) {
        currentSong = song;
        isPlaying = true;
        isPaused = false;
        songSwitchTime = System.currentTimeMillis();
        currentPlaybackPositionMs = 0;
        downloadProgress = 0.0f;
        currentLyrics.clear();
        currentLyricIndex = -1;

        new Thread(() -> {
            NeteaseApi.LyricResult lyricResult = NeteaseApi.getLyric(song.id);
            if (lyricResult.success) {
                parseLrc(lyricResult.lrc);
            }
            String cookie = MusicManager.cookie;
            NeteaseApi.SongUrlResult urlResult = NeteaseApi.getSongUrl(song.id, cookie);

            if (urlResult.success && urlResult.url != null) {
                sendCommand("PLAY:" + urlResult.url);
            } else {
                System.err.println("[MusicPlayer] Failed to get URL for song " + song.id + ", fallback to ID.");
                sendCommand("PLAY:" + song.id);
            }
        }).start();
    }

    private static void parseLrc(String lrcText) {
        List<LyricLine> parsed = new ArrayList<>();
        if (lrcText == null || lrcText.isEmpty()) {
            return;
        }

        for (String line : lrcText.split("\n")) {
            Matcher matcher = LRC_PATTERN.matcher(line);
            if (matcher.matches()) {
                try {
                    long minutes = Long.parseLong(matcher.group(1));
                    long seconds = Long.parseLong(matcher.group(2));
                    long millis = Long.parseLong(matcher.group(3));
                    if (matcher.group(3).length() == 2) {
                        millis *= 10;
                    }

                    long totalTime = minutes * 60 * 1000 + seconds * 1000 + millis;
                    String text = matcher.group(4).trim();

                    if (!text.isEmpty()) {
                        parsed.add(new LyricLine(totalTime, text));
                    }
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }

        parsed.sort((l1, l2) -> Long.compare(l1.time, l2.time));
        System.out.println("[DEBUG] Parsed " + parsed.size() + " lyric lines.");

        currentLyrics.clear();
        currentLyrics.addAll(parsed);
    }

    private static void updateLyricIndex(long currentMs) {
        if (currentLyrics.isEmpty()) {
            currentLyricIndex = -1;
            return;
        }

        // 这是一个简单的线性搜索，对于歌词来说足够高效
        int newIndex = -1;
        for (int i = 0; i < currentLyrics.size(); i++) {
            if (currentMs >= currentLyrics.get(i).time) {
                newIndex = i;
            } else {
                break; // 因为列表是排序的，后续的行时间只会更大
            }
        }
        currentLyricIndex = newIndex;
    }

    public static List<LyricLine> getCurrentLyrics() {
        return currentLyrics;
    }

    public static int getCurrentLyricIndex() {
        return currentLyricIndex;
    }

    public static class LyricLine {
        public final long time;
        public final String text;

        public LyricLine(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    public static void togglePlayPause() {
        if (currentSong == null || songSwitchTime > 0) return;
        sendCommand(isPaused ? "RESUME" : "PAUSE");
    }

    public static void clearCurrentSong() {
        currentSong = null;
        isPlaying = false;
        isPaused = false;
        currentPlaybackPositionMs = 0;
        songSwitchTime = 0;
        downloadProgress = 0.0f;
    }

    public static void setPlaylist(List<Likelist> playlist) {
        currentPlaylist = playlist;
    }

    public static PlayMode getPlayMode() {
        return currentPlayMode;
    }

    public static void cyclePlayMode() {
        currentPlayMode = PlayMode.values()[(currentPlayMode.ordinal() + 1) % PlayMode.values().length];
    }

    public static Likelist getCurrentSong() { return currentSong; }
    public static boolean isPlaying() { return isPlaying && !isPaused; }
    public static String getLastError() { return lastError; }

    // ================== 新增Getter ==================
    public static float getDownloadProgress() {
        return downloadProgress;
    }
    // ============================================

    public static String getCurrentProgress() {
        if (currentSong == null) return "00:00";
        long elapsed = currentPlaybackPositionMs;
        long seconds = (elapsed / 1000) % 60;
        long minutes = (elapsed / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static float getProgressPercentage() {
        if (currentSong == null || currentSong.duration == 0) return 0f;
        return Math.min(1.0f, (float) currentPlaybackPositionMs / currentSong.duration);
    }

    public static boolean isWaitingForPlayback() {
        return songSwitchTime > 0 && (System.currentTimeMillis() - songSwitchTime < 15000); // 增加超时到15秒
    }
}