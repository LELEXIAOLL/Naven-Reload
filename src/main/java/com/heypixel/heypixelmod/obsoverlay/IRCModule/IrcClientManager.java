package com.heypixel.heypixelmod.obsoverlay.IRCModule;

import com.heypixel.heypixelmod.obfuscation.JNICObf;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRenderTabOverlay;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.HWIDUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JNICObf
public class IrcClientManager {
    public static final IrcClientManager INSTANCE = new IrcClientManager();
    private static final String SERVER_IP = "irc服务器";
    private static final int SERVER_PORT = 3000;

    public static class User { public String ircUsername, rank, gameUsername; }
    public final User currentUser = new User();

    private final List<IrcMessageListener> listeners = new ArrayList<>();
    private final Map<String, Consumer<String[]>> userCheckCallbacks = new ConcurrentHashMap<>();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean connected = false;
    private volatile boolean connecting = false;

    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isReconnecting = false;

    private String lastSuccessfulUsername = null;
    private String lastSuccessfulPassword = null;

    public record IrcUserInfo(String ircUsername, String rank, String tag) {}
    private final Map<String, IrcUserInfo> userInfoCache = new ConcurrentHashMap<>();

    private IrcClientManager() {
        Naven.getInstance().getEventManager().register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect));
    }

    // [新增] 手动重连方法
    public void manualReconnect() {
        if (this.lastSuccessfulUsername == null || this.lastSuccessfulPassword == null) {
            ChatUtils.addChatMessage("§9[IRC] §c无可用凭据，无法执行重连。请先使用账号密码登录一次。");
            return;
        }
        if (connecting) {
            ChatUtils.addChatMessage("§9[IRC] §c当前正在连接中，请稍后...");
            return;
        }

        ChatUtils.addChatMessage("§9[IRC] §e正在执行手动重连...");
        // 标记为正在重连，防止 disconnect() 触发额外的延迟任务
        this.isReconnecting = true;

        if (this.connected) {
            disconnect();
        }
        // 立即开始重连
        attemptReconnect();
    }

    public boolean isAttemptingAutoReconnect() {
        return this.isReconnecting;
    }

    @EventTarget
    public void onRenderTab(EventRenderTabOverlay event) {
        if (!isConnected() || event.getPlayerInfo() == null) {
            return;
        }
        String gameUsername = event.getPlayerInfo().getProfile().getName();
        IrcUserInfo userInfo = getIrcUserInfo(gameUsername);
        if (userInfo != null) {
            String displayTitle;
            if (userInfo.tag() != null && !userInfo.tag().equals("NULL") && !userInfo.tag().isEmpty()) {
                displayTitle = userInfo.tag().replace('&', '§');
            } else {
                displayTitle = getDisplayRank(userInfo.rank());
            }

            MutableComponent prefix = Component.literal("§f[")
                    .append(Component.literal("§cNaven-Reload"))
                    .append(Component.literal("§9IRC"))
                    .append(Component.literal("§f-"))
                    .append(Component.literal(displayTitle))
                    .append(Component.literal("§f-"))
                    .append(Component.literal("§a" + userInfo.ircUsername()))
                    .append(Component.literal("§f]§r "));
            MutableComponent newDisplayName = prefix.append(event.getComponent());
            event.setComponent(newDisplayName);
        }
    }

    private String getDisplayRank(String rank) {
        if (rank == null) return "§7未知";
        return switch (rank) {
            case "Admin"    -> "§c管理员";
            case "Dev"      -> "§b客户端作者";
            case "Beta"     -> "§e内部用户";
            case "FreeUser" -> "§a公益用户";
            default         -> "§7" + rank;
        };
    }

    public void setLoginCredentials(String username, String password) {
        this.lastSuccessfulUsername = username;
        this.lastSuccessfulPassword = password;
    }

    private void handleIncomingMessage(String encryptedMessage) {
        try {
            String decrypted = IrcSecurityUtils.decryptAES(encryptedMessage);
            Minecraft.getInstance().execute(() -> {
                if (decrypted.startsWith("login: success")) {
                    handleLoginSuccess(decrypted);
                } else if (decrypted.startsWith("userlist_update:")) {
                    handleUserListUpdate(decrypted);
                }
                for (IrcMessageListener listener : new ArrayList<>(listeners)) {
                    listener.onIrcMessage(decrypted);
                }

                // 消息路由处理
                if (decrypted.startsWith("chat:")) {
                    handleChatMessage(decrypted);
                } else if (decrypted.startsWith("list:")) {
                    handleUserListMessage(decrypted);
                } else if (decrypted.startsWith("info:")) {
                    handleInfoMessage(decrypted);
                } else if (decrypted.startsWith("check:")) {
                    handleUserCheckResponse(decrypted);
                } else if (decrypted.startsWith("player_join:")) { // [新增]
                    handlePlayerJoin(decrypted);
                } else if (decrypted.startsWith("player_quit:")) { // [新增]
                    handlePlayerQuit(decrypted);
                }
            });
        } catch (Exception e) {
            System.err.println("[IRC] 解密或处理消息时出错: " + e.getMessage());
        }
    }

    // [新增] 处理玩家加入
    private void handlePlayerJoin(String message) {
        // 格式: player_join: 'DisplayRank' 'Username'
        Pattern pattern = Pattern.compile("player_join: '([^']*)' '([^']*)'");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String displayRank = matcher.group(1);
            String username = matcher.group(2);
            String formattedMessage = String.format("§9[IRC] §r%s - %s §a 加入了IRC服务器", displayRank, username);
            ChatUtils.addChatMessage(formattedMessage);
        }
    }

    private void handlePlayerQuit(String message) {
        Pattern pattern = Pattern.compile("player_quit: '([^']*)' '([^']*)'");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String displayRank = matcher.group(1);
            String username = matcher.group(2);
            String formattedMessage = String.format("§9[IRC] §<%s - %s §e 退出了IRC服务器", displayRank, username);
            ChatUtils.addChatMessage(formattedMessage);
        }
    }

    private void handleLoginSuccess(String message) {
        String[] parts = message.split("'");
        if (parts.length < 4) return;
        String username = parts[1];
        String rank = parts[3];
        this.currentUser.ircUsername = username;
        this.currentUser.rank = rank;
        Naven.userRank = rank;
        Naven.ircLoggedIn = true;
        if (Minecraft.getInstance().player != null) {
            String gameUsername = Minecraft.getInstance().player.getName().getString();
            String command = String.format("update: name '%s'", gameUsername);
            sendMessage(command);
        }
        if (isReconnecting) {
            ChatUtils.addChatMessage("§9[IRC] §a重连成功!");
            isReconnecting = false;
        }
    }

    private void attemptReconnect() {
        ChatUtils.addChatMessage("§9[IRC] §c正在重新连接...");
        connect(success -> {
            if (success) {
                if (this.lastSuccessfulUsername != null && this.lastSuccessfulPassword != null) {
                    ChatUtils.addChatMessage("§9[IRC] §e网络已重连，正在自动恢复会话...");
                    try {
                        String hwid = HWIDUtils.getHWID();
                        String command = String.format("login: '%s' '%s' '%s'", this.lastSuccessfulUsername, this.lastSuccessfulPassword, hwid);
                        sendMessage(command);
                    } catch (Exception e) {
                        ChatUtils.addChatMessage("§9[IRC] §c自动恢复会话失败: " + e.getMessage());
                        reconnectExecutor.schedule(this::attemptReconnect, 5, TimeUnit.SECONDS);
                    }
                } else {
                    ChatUtils.addChatMessage("§9[IRC] §a网络已重连，但无登录信息，请返回主菜单手动登录。");
                    isReconnecting = false;
                }
            } else {
                ChatUtils.addChatMessage("§9[IRC] §c重连失败!");
                reconnectExecutor.schedule(this::attemptReconnect, 5, TimeUnit.SECONDS);
            }
        });
    }

    public void connect(Consumer<Boolean> callback) {
        if (connected) {
            if (callback != null) callback.accept(true);
            return;
        }
        if (connecting) return;
        connecting = true;
        networkExecutor.submit(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                connected = true;
                startListening();
                if (callback != null) Minecraft.getInstance().execute(() -> callback.accept(true));
            } catch (IOException e) {
                if (callback != null) Minecraft.getInstance().execute(() -> callback.accept(false));
            } finally {
                connecting = false;
            }
        });
    }

    public void connect() {
        connect(null);
    }

    private void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                String encryptedLine;
                while (connected && (encryptedLine = in.readLine()) != null) {
                    handleIncomingMessage(encryptedLine);
                }
            } catch (IOException e) {
            } finally {
                disconnect();
            }
        });
        listenerThread.setName("IRC-Listener-Thread");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void startReconnectSequence() {
        if (isReconnecting || this.lastSuccessfulUsername == null) {
            return;
        }
        isReconnecting = true;
        ChatUtils.addChatMessage("§9[IRC] §c与IRC服务器意外断开连接");
        reconnectExecutor.schedule(this::attemptReconnect, 3, TimeUnit.SECONDS);
    }

    public void disconnect() {
        if (!connected) return;
        boolean wasLoggedIn = Naven.ircLoggedIn;
        Naven.ircLoggedIn = false;
        connected = false;
        try {
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
        }
        if (wasLoggedIn && !isReconnecting) {
            startReconnectSequence();
        }
    }

    private void handleUserListUpdate(String message) {
        String payload = message.substring(16);
        Map<String, IrcUserInfo> newCache = new ConcurrentHashMap<>();
        if (payload.isEmpty()) {
            this.userInfoCache.clear();
            return;
        }
        String[] users = payload.split(";");
        for (String userString : users) {
            String[] parts = userString.split(",", 4);
            if (parts.length == 4) {
                String gameUsername = parts[0];
                String ircUsername = parts[1];
                String rank = parts[2];
                String tag = parts[3];
                newCache.put(gameUsername.toLowerCase(), new IrcUserInfo(ircUsername, rank, tag));
            }
        }
        this.userInfoCache.clear();
        this.userInfoCache.putAll(newCache);
    }

    private void handleInfoMessage(String message) {
        String infoContent = message.substring(5);
        if (infoContent.startsWith("当前在线人数:")) {
            ChatUtils.addChatMessage("§9[IRC] §e" + infoContent);
        } else {
            ChatUtils.addChatMessage("§9[IRC] §f" + infoContent);
        }
    }

    private void handleUserCheckResponse(String message) {
        Pattern successPattern = Pattern.compile("check: success '([^']*)' '([^']*)' '([^']*)'");
        Matcher successMatcher = successPattern.matcher(message);
        if (successMatcher.find()) {
            String gameUsername = successMatcher.group(1);
            String ircUsername = successMatcher.group(2);
            String rank = successMatcher.group(3);
            Consumer<String[]> callback = userCheckCallbacks.remove(gameUsername.toLowerCase());
            if (callback != null) callback.accept(new String[]{ircUsername, rank});
            return;
        }
        Pattern notFoundPattern = Pattern.compile("check: notfound '([^']*)'");
        Matcher notFoundMatcher = notFoundPattern.matcher(message);
        if (notFoundMatcher.find()) {
            String gameUsername = notFoundMatcher.group(1);
            Consumer<String[]> callback = userCheckCallbacks.remove(gameUsername.toLowerCase());
            if (callback != null) callback.accept(null);
        }
    }

    public void checkIfIrcUser(String gameUsername, Consumer<String[]> callback) {
        if (gameUsername == null || gameUsername.isEmpty() || callback == null) return;
        userCheckCallbacks.put(gameUsername.toLowerCase(), callback);
        String command = String.format("check: user '%s'", gameUsername);
        sendMessage(command);
    }

    private void handleChatMessage(String message) {
        Pattern pattern = Pattern.compile("chat: '(.+?)' '(.+?)' '(.+)'");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String displayTitle = matcher.group(1);
            String ircUsername = matcher.group(2);
            String chatContent = matcher.group(3);
            String formattedMessage = String.format("§9[IRC] §r[%s§r-%s§r]: §r%s", displayTitle, ircUsername, chatContent);
            ChatUtils.addChatMessage(formattedMessage);
        }
    }

    // [修改] 严谨的去重列表显示逻辑
    private void handleUserListMessage(String message) {
        // 原始消息格式: list:Count | User1 | User2...
        String[] parts = message.substring(5).split(" \\| ");

        if (parts.length > 0) {
            // 使用 Set 确保唯一性
            Set<String> uniqueKeys = new HashSet<>();
            List<String> displayLines = new ArrayList<>();

            for (int i = 1; i < parts.length; i++) {
                String rawLine = parts[i];
                if (rawLine == null || rawLine.trim().isEmpty()) continue;

                // 尝试提取核心IRC用户名作为去重依据
                // 格式: §r[%s§r-%s§r] §f%s
                String uniqueKey = rawLine;
                try {
                    int startMarker = rawLine.lastIndexOf("§r-");
                    int endMarker = rawLine.indexOf("§r]", startMarker);

                    if (startMarker != -1 && endMarker != -1) {
                        // 提取用户名部分
                        uniqueKey = rawLine.substring(startMarker + 3, endMarker);
                    }
                } catch (Exception e) {
                    uniqueKey = rawLine; // 提取失败则对比整行
                }

                if (uniqueKeys.add(uniqueKey)) {
                    displayLines.add(rawLine);
                }
            }

            ChatUtils.addChatMessage("§b当前IRC在线用户 (" + displayLines.size() + "):");
            for (String line : displayLines) {
                ChatUtils.addChatMessage("§7- " + line);
            }
        }
    }

    public void sendMessage(String plainTextMessage) {
        if (!connected || out == null) return;
        networkExecutor.submit(() -> {
            try {
                String encrypted = IrcSecurityUtils.encryptAES(plainTextMessage);
                out.println(encrypted);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public boolean isConnected() {
        return connected;
    }

    public void registerListener(IrcMessageListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregisterListener(IrcMessageListener listener) {
        listeners.remove(listener);
    }

    public interface IrcMessageListener {
        void onIrcMessage(String decryptedMessage);
    }

    public IrcUserInfo getIrcUserInfo(String gameUsername) {
        if (gameUsername == null || gameUsername.isEmpty()) return null;
        String lowerCaseUsername = gameUsername.toLowerCase();
        IrcUserInfo cachedInfo = userInfoCache.get(lowerCaseUsername);
        return cachedInfo;
    }
}
