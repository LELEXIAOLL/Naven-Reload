package com.irc;

import com.irc.utils.DatabaseManager;
import com.irc.utils.LogManager;
import com.irc.utils.SecurityUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IRC {

    private static final int PORT = 6667;

    private static final Set<ClientHandler> clientHandlers = Collections.synchronizedSet(new HashSet<>());
    private static final Set<ClientHandler> panelHandlers = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, String> gameUsernames = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            SecurityUtils.init();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        DatabaseManager dbManager = new DatabaseManager();
        if (!dbManager.connect()) {
            return;
        }
        dbManager.startKeepAlive();
        startPeriodicBroadcast();
        startUserListBroadcast();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            LogManager.info("IRC 服务器已启动，监听端口: " + PORT);
            while (true) {
                ClientHandler clientHandler = new ClientHandler(serverSocket.accept(), dbManager);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            dbManager.disconnect();
        }
    }

    public static void addPanel(ClientHandler handler) {
        panelHandlers.add(handler);
        LogManager.info("面板已连接: " + handler.getAuthenticatedUsername());
    }

    public static void removePanel(ClientHandler handler) {
        panelHandlers.remove(handler);
    }

    public static void addClient(ClientHandler handler) {
        String username = handler.getAuthenticatedUsername();

        // 检查是否已有同名用户在线（用于决定是否广播上线消息）
        boolean alreadyOnline = false;
        synchronized (clientHandlers) {
            for (ClientHandler existing : clientHandlers) {
                if (existing != handler && username != null && username.equals(existing.getAuthenticatedUsername())) {
                    alreadyOnline = true;
                    break;
                }
            }
            clientHandlers.add(handler);
        }

        LogManager.info("游戏客户端已连接: " + username);
        // 只有当该用户之前不在线时才广播上线消息
        if (username != null && !alreadyOnline) {
            String displayTitle;
            String userTag = handler.getAuthenticatedTag();
            if (userTag != null && !userTag.trim().isEmpty()) {
                displayTitle = userTag.replace('&', '§');
            } else {
                displayTitle = mapRankToDisplay(handler.getAuthenticatedRank());
            }
            broadcastMessage(String.format("player_join: '%s' '%s'", displayTitle, username));
        }
    }

    public static void removeClient(ClientHandler handler) {
        String username = handler.getAuthenticatedUsername();
        boolean wasRemoved = clientHandlers.remove(handler);

        if (username != null && wasRemoved) {
            LogManager.info("客户端已断开: " + username);

            // 检查是否还有该用户的其他连接
            boolean stillOnline = false;
            synchronized (clientHandlers) {
                for (ClientHandler existing : clientHandlers) {
                    if (username.equals(existing.getAuthenticatedUsername())) {
                        stillOnline = true;
                        break;
                    }
                }
            }

            // 只有当该用户完全下线时才广播下线消息和移除游戏用户名
            if (!stillOnline) {
                gameUsernames.remove(username);
                String displayTitle;
                String userTag = handler.getAuthenticatedTag();
                if (userTag != null && !userTag.trim().isEmpty()) {
                    displayTitle = userTag.replace('&', '§');
                } else {
                    displayTitle = mapRankToDisplay(handler.getAuthenticatedRank());
                }
                broadcastMessage(String.format("player_quit: '%s' '%s'", displayTitle, username));
            }
        }
    }

    public static void broadcastMessage(String plainTextMessage) {
        Set<ClientHandler> handlersSnapshot;
        synchronized (clientHandlers) {
            handlersSnapshot = new HashSet<>(clientHandlers);
        }

        for (ClientHandler handler : handlersSnapshot) {
            try {
                handler.sendMessage(plainTextMessage);
            } catch (Exception e) {
            }
        }
    }

    public static void setGameUsername(String ircUsername, String gameUsername) {
        if (ircUsername != null) {
            gameUsernames.put(ircUsername, gameUsername);
        }
    }

    public static ClientHandler findUserByGameName(String gameUsername) {
        if (gameUsername == null || gameUsername.isEmpty()) return null;
        for (Map.Entry<String, String> entry : gameUsernames.entrySet()) {
            if (gameUsername.equalsIgnoreCase(entry.getValue())) {
                String ircUsername = entry.getKey();
                synchronized (clientHandlers) {
                    for (ClientHandler handler : clientHandlers) {
                        if (ircUsername.equals(handler.getAuthenticatedUsername())) {
                            return handler;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static String getUserListString() {
        StringJoiner listBuilder = new StringJoiner(" | ");
        Set<ClientHandler> currentClients;
        synchronized (clientHandlers) {
            currentClients = new HashSet<>(clientHandlers);
        }

        // 去重：相同用户名只显示一个
        Set<String> seenUsers = new HashSet<>();
        List<ClientHandler> uniqueClients = new ArrayList<>();
        for (ClientHandler handler : currentClients) {
            String ircUser = handler.getAuthenticatedUsername();
            if (ircUser != null && !seenUsers.contains(ircUser)) {
                seenUsers.add(ircUser);
                uniqueClients.add(handler);
            }
        }

        listBuilder.add(String.valueOf(uniqueClients.size()));
        for (ClientHandler handler : uniqueClients) {
            String ircUser = handler.getAuthenticatedUsername();
            String rank = handler.getAuthenticatedRank();
            String tag = handler.getAuthenticatedTag();

            String displayTitle;
            if (tag != null && !tag.trim().isEmpty()) {
                displayTitle = tag.replace('&', '§');
            } else {
                displayTitle = mapRankToDisplay(rank);
            }

            String gameUser = gameUsernames.getOrDefault(ircUser, "§8加载中...");
            listBuilder.add(String.format("§r[%s§r-%s§r] §f%s", displayTitle, ircUser, gameUser));
        }
        return "list:" + listBuilder.toString();
    }

    public static String mapRankToDisplay(String rank) {
        if (rank == null) return "§7未知";
        return switch (rank) {
            case "Admin"    -> "§c管理员";
            case "Dev"      -> "§b客户端作者";
            case "Beta"     -> "§e内部用户";
            case "FreeUser" -> "§a公益用户";
            default         -> "§7" + rank;
        };
    }

    // 获取去重后的用户数量
    private static int getUniqueUserCount() {
        Set<String> seenUsers = new HashSet<>();
        synchronized (clientHandlers) {
            for (ClientHandler handler : clientHandlers) {
                String ircUser = handler.getAuthenticatedUsername();
                if (ircUser != null) {
                    seenUsers.add(ircUser);
                }
            }
        }
        return seenUsers.size();
    }

    private static void startUserListBroadcast() {
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (clientHandlers.isEmpty()) return;
                StringJoiner payload = new StringJoiner(";");
                Set<ClientHandler> currentClients;
                synchronized (clientHandlers) {
                    currentClients = new HashSet<>(clientHandlers);
                }

                // 去重：相同用户名只广播一个
                Set<String> seenUsers = new HashSet<>();
                for (ClientHandler handler : currentClients) {
                    String ircUser = handler.getAuthenticatedUsername();
                    String rank = handler.getAuthenticatedRank();
                    String tag = handler.getAuthenticatedTag();
                    if (ircUser == null || rank == null) continue;
                    if (seenUsers.contains(ircUser)) continue;
                    seenUsers.add(ircUser);

                    String gameUser = gameUsernames.getOrDefault(ircUser, "加载中...");
                    String tagToSend = (tag == null) ? "NULL" : tag;
                    payload.add(String.format("%s,%s,%s,%s", gameUser, ircUser, rank, tagToSend));
                }
                if (payload.length() > 0) {
                    String message = "userlist_update:" + payload.toString();
                    broadcastMessage(message);
                }
            }
        }, 5000, 5000);
    }

    private static void startPeriodicBroadcast() {
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                int userCount = getUniqueUserCount();
                if (userCount > 0) {
                    broadcastMessage(String.format("info:当前在线人数: %d", userCount));
                }
            }
        }, 2 * 60 * 1000, 2 * 60 * 1000);
    }
}