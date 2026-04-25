package com.heypixel.heypixelmod.obsoverlay.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.IRC;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IRCManager {

    private static final String BMWIRC_PREFIX = "§e[BMWIRC] ";

    private static Process ircProcess;
    private static Thread outputReaderThread;
    private static final String EXE_NAME = "bmw.exe";
    private static final Path EXE_PATH = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(), EXE_NAME);
    private static final Path COMMAND_FILE_PATH = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "irc_command.txt");

    private final Set<String> ircUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> announcedUsers = ConcurrentHashMap.newKeySet();

    private final TimeHelper messageTimer = new TimeHelper();
    private final Random random = new Random();
    private final List<String> welcomeMessages = List.of(
            "asdf", "xsfg", "ihoe", "hsgo", "fghh", "pasf", "oiht"
    );

    private static IRCManager instance;

    private IRCManager() {}

    public static IRCManager getInstance() {
        if (instance == null) {
            instance = new IRCManager();
        }
        return instance;
    }

    public void resetAnnouncedUsers() {
        announcedUsers.clear();
        displayBmwIrcMessage("§a已重置本局已发现的用户列表。");
    }

    private void displayBmwIrcMessage(String message) {
        IRC ircModule = (IRC) Naven.getInstance().getModuleManager().getModule(IRC.class);
        if (ircModule != null && ircModule.isBmwIrcEnabled()) {
            ChatUtils.addChatMessage(BMWIRC_PREFIX + message);
        }
    }

    public void start() {
        if (isProcessRunning()) {
            displayBmwIrcMessage("§e后端进程已在运行。");
            return;
        }

        displayBmwIrcMessage("§c正在连接服务器...");

        try {
            Files.deleteIfExists(COMMAND_FILE_PATH);
            extractExeFromResources();

            ProcessBuilder pb = new ProcessBuilder(EXE_PATH.toString());
            pb.redirectErrorStream(true);
            ircProcess = pb.start();

            outputReaderThread = new Thread(() -> {
                IRC ircModule = (IRC) Naven.getInstance().getModuleManager().getModule(IRC.class);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(ircProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && ircProcess.isAlive()) {
                        final String finalLine = line;

                        Minecraft.getInstance().execute(() -> {
                            boolean isStatusMessage = false;

                            if (finalLine.contains("[STATUS]") && finalLine.contains("Connected to remote BMW IRC server")) {
                                displayBmwIrcMessage("§a连接成功，已注册用户...");
                                isStatusMessage = true;
                                createUser();
                            } else if (finalLine.contains("[ERROR]") && finalLine.contains("Failed to connect")) {
                                displayBmwIrcMessage("§c连接失败");
                                isStatusMessage = true;
                            }

                            if (ircModule != null && ircModule.isDebugEnabled() && !isStatusMessage) {
                                ChatUtils.addChatMessage("§8[Py] §7" + finalLine);
                            }

                            handlePythonOutput(finalLine);
                        });
                    }
                } catch (IOException e) {
                    // 忽略
                }
                Minecraft.getInstance().execute(() -> displayBmwIrcMessage("§c与后端断开连接."));
            });
            outputReaderThread.setDaemon(true);
            outputReaderThread.start();

        } catch (IOException e) {
            displayBmwIrcMessage("§c启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        sendCommand("EXIT");
        try {
            if (ircProcess != null) {
                ircProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (ircProcess != null && ircProcess.isAlive()) {
            ircProcess.destroyForcibly();
        }
        ircProcess = null;
        try {
            Files.deleteIfExists(COMMAND_FILE_PATH);
        } catch (IOException e) { /* 忽略 */ }

        ircUsers.clear();
        announcedUsers.clear();
        displayBmwIrcMessage("§c已停止后端.");
    }

    public void sendMessage(String message) {
        sendCommand("SEND_MSG:" + message);
    }

    public void createUser() {
        if (Minecraft.getInstance().player == null) {
            displayBmwIrcMessage("§c无法创建用户：玩家实体不存在（可能未进入游戏）。");
            return;
        }
        String playerName = Minecraft.getInstance().player.getGameProfile().getName();
        String serverName = "Heypixel";

        // [修改] 在用户名前面拼接前缀
        String displayName = "§e[Naven-Reload]§r" + playerName;

        sendCommand("CREATE_USER:" + displayName + ":" + serverName);
    }

    public void requestUserList() {
        sendCommand("LIST_USERS");
    }

    private void sendCommand(String command) {
        if (!isProcessRunning()) {
            return;
        }
        try {
            IRC ircModule = (IRC) Naven.getInstance().getModuleManager().getModule(IRC.class);
            if (ircModule != null && ircModule.isDebugEnabled()) {
                ChatUtils.addChatMessage("§8[Java] §7Writing command to file: '" + command + "'");
            }
            try (BufferedWriter writer = Files.newBufferedWriter(COMMAND_FILE_PATH, StandardCharsets.UTF_8)) {
                writer.write(command);
            }
        } catch (IOException e) {
            displayBmwIrcMessage("§c发送指令时出错: " + e.getMessage());
        }
    }

    public static void killAllBmwProcesses() {
        IRCManager manager = IRCManager.getInstance();
        manager.displayBmwIrcMessage("§e正在尝试关闭代理端...");

        String processName = "bmw.exe";
        String command = "taskkill /F /IM " + processName;

        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                manager.displayBmwIrcMessage("§a成功关闭代理端");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }

    private static String unescapeUnicode(String unicodeStr) {
        if (unicodeStr == null) return null;
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(unicodeStr);
        StringBuffer sb = new StringBuffer(unicodeStr.length());
        while (matcher.find()) {
            matcher.appendReplacement(sb, Character.toString((char) Integer.parseInt(matcher.group(1), 16)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void handlePythonOutput(String line) {
        if (line.startsWith("RECV:")) {
            String jsonStrRaw = line.substring(5);
            String jsonStr = unescapeUnicode(jsonStrRaw);
            IRC ircModule = (IRC) Naven.getInstance().getModuleManager().getModule(IRC.class);

            try {
                JsonObject messageJson = JsonParser.parseString(jsonStr).getAsJsonObject();
                String func = messageJson.get("func").getAsString();

                switch (func) {
                    case "send_msg":
                        String sender = messageJson.get("name").getAsString();
                        String msg = messageJson.get("msg").getAsString();
                        displayBmwIrcMessage("§a" + sender + "§f: " + msg);
                        break;

                    case "user_list":
                        JsonArray usersArray = messageJson.getAsJsonArray("users");
                        ircUsers.clear();
                        for (JsonElement userElement : usersArray) {
                            String username = userElement.getAsString();
                            ircUsers.add(ChatFormatting.stripFormatting(username));
                        }
                        checkVisiblePlayersForAnnouncement();
                        break;

                    case "create_user":
                        String createdUser = messageJson.get("name").getAsString();
                        ircUsers.add(ChatFormatting.stripFormatting(createdUser));

                        // [修改] 判断注册成功的逻辑，改为包含判断，因为名字加了前缀
                        if (Minecraft.getInstance().player != null && createdUser.contains(Minecraft.getInstance().player.getGameProfile().getName())) {
                            displayBmwIrcMessage("§a注册成功: " + createdUser);
                        }
                        break;

                    case "user_join":
                        String joinedUser = messageJson.get("name").getAsString();
                        ircUsers.add(ChatFormatting.stripFormatting(joinedUser));
                        checkVisiblePlayersForAnnouncement();
                        break;

                    case "remove_user":
                    case "user_leave":
                        if (messageJson.has("name")) {
                            String leftUser = messageJson.get("name").getAsString();
                            String cleanLeftUser = ChatFormatting.stripFormatting(leftUser);
                            ircUsers.remove(cleanLeftUser);
                            announcedUsers.remove(cleanLeftUser);
                        } else {
                            ircUsers.clear();
                            announcedUsers.clear();
                            displayBmwIrcMessage("§c所有用户已断开连接.");
                        }
                        break;

                    case "user_list_response":
                        JsonArray responseUsers = messageJson.getAsJsonArray("users");
                        List<String> userList = new ArrayList<>();

                        ircUsers.clear();
                        for (JsonElement userElement : responseUsers) {
                            String username = userElement.getAsString();
                            userList.add(username);
                            ircUsers.add(ChatFormatting.stripFormatting(username));
                        }
                        Collections.sort(userList);

                        displayBmwIrcMessage("§fOnline Users (" + userList.size() + "):");
                        String usersString = String.join("§7, §a", userList);
                        if (!usersString.isEmpty()) {
                            if (ircModule != null && ircModule.isBmwIrcEnabled()) {
                                ChatUtils.addChatMessage("§a" + usersString);
                            }
                        }

                        checkVisiblePlayersForAnnouncement();
                        break;
                }
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    public boolean isIrcUser(String name) {
        String cleanInputName = ChatFormatting.stripFormatting(name);
        if (cleanInputName == null) return false;
        for (String ircUserName : ircUsers) {
            if (cleanInputName.equalsIgnoreCase(ircUserName)) {
                return true;
            }
        }
        return false;
    }

    public void announceUser(String name) {
        String cleanName = ChatFormatting.stripFormatting(name);
        if (cleanName != null && announcedUsers.add(cleanName)) {
            displayBmwIrcMessage("§bBMWUser: §a" + name);
            if (messageTimer.delay(10000.0)) {
                String randomMessage = welcomeMessages.get(random.nextInt(welcomeMessages.size()));
                String finalMessage = randomMessage + ">" + "发现BMWClient用户: " + cleanName + " ---来自Naven-Reload";
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.connection != null) {
                    mc.player.connection.sendChat(finalMessage);
                }
                messageTimer.reset();
            }
        }
    }

    public List<String> getVisibleIrcUsers() {
        if (Minecraft.getInstance().level == null) {
            return List.of();
        }
        return Minecraft.getInstance().level.players().stream()
                .map(player -> player.getGameProfile().getName())
                .filter(this::isIrcUser)
                .collect(Collectors.toList());
    }

    public boolean isProcessRunning() {
        return ircProcess != null && ircProcess.isAlive();
    }

    private void extractExeFromResources() throws IOException {
        String resourcePath = "/assets/heypixel/irc/" + EXE_NAME;

        try {
            Files.deleteIfExists(EXE_PATH);
        } catch (IOException e) {
            displayBmwIrcMessage("§c警告: 无法删除旧版 backend，可能文件被占用。");
        }

        try (InputStream in = Naven.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new FileNotFoundException("bmw.exe not found in resources at: " + resourcePath);
            }
            Files.copy(in, EXE_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void checkVisiblePlayersForAnnouncement() {
        if (Minecraft.getInstance().level == null) return;
        for (Player player : Minecraft.getInstance().level.players()) {
            String playerName = player.getGameProfile().getName();
            if (isIrcUser(playerName)) {
                announceUser(playerName);
            }
        }
    }
}