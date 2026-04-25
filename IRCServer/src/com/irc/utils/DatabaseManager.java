package com.irc.utils;

import java.sql.*;
import java.util.ArrayList; // ✅ 新增
import java.util.List;      // ✅ 新增
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/n-re?useSSL=false&serverTimezone=UTC&autoReconnect=true&characterEncoding=utf8";
    private static final String DB_USER = "n-re";
    private static final String DB_PASS = "fEMjYT25tEnSmmZA";
    private static final String TABLE_NAME = "irc_users";

    private volatile Connection connection;
    private final ScheduledExecutorService keepAliveScheduler = Executors.newSingleThreadScheduledExecutor();

    public boolean connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }

            LogManager.info("正在连接到MySQL数据库 'n-re'...");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            LogManager.info("数据库连接成功！");
            return true;
        } catch (SQLException e) {
            LogManager.error("数据库连接失败: " + e.getMessage());
            return false;
        }
    }

    public void startKeepAlive() {
        LogManager.info("正在启动数据库自动保活/重连任务 (10秒/次)...");
        keepAliveScheduler.scheduleAtFixedRate(() -> {
            try {
                if (connection != null && !connection.isClosed() && connection.isValid(3)) {
                    try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
                        stmt.executeQuery();
                    }
                } else {
                    LogManager.warn("检测到数据库连接丢失，正在尝试自动重连...");
                    if (connect()) {
                        LogManager.info("自动重连成功！");
                    } else {
                        LogManager.error("自动重连失败，将在10秒后重试。");
                    }
                }
            } catch (Exception e) {
                LogManager.error("数据库保活任务发生异常: " + e.getMessage());
                connect();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public String[] getUserCredentials(String username) {
        if (connection == null) return null;

        String query = "SELECT `password`, `hwid`, `is_banned`, `rank`, `tag` FROM `" + TABLE_NAME + "` WHERE `username` = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String passwordHash = rs.getString("password");
                String hwid = rs.getString("hwid");
                String isBanned = String.valueOf(rs.getInt("is_banned"));
                String rank = rs.getString("rank");
                String tag = rs.getString("tag");
                return new String[]{passwordHash, hwid, isBanned, rank, tag};
            }
        } catch (SQLException e) {
            LogManager.error("查询用户 '" + username + "' 时出错: " + e.getMessage());
        }
        return null;
    }

    public String getQQByUsername(String username) {
        if (connection == null) return null;
        String query = "SELECT `qq` FROM `" + TABLE_NAME + "` WHERE `username` = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("qq");
            }
        } catch (SQLException e) {
            LogManager.error("查询用户QQ '" + username + "' 时出错: " + e.getMessage());
        }
        return null;
    }

    public boolean updateUserTag(String username, String tag) {
        if (connection == null) return false;
        String sql = "UPDATE `" + TABLE_NAME + "` SET `tag` = ? WHERE `username` = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tag);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LogManager.error("为用户 '" + username + "' 更新Tag时出错: " + e.getMessage());
            return false;
        }
    }

    public void updateLastLoginTime(String username) {
        if (connection == null) return;
        String sql = "UPDATE `" + TABLE_NAME + "` SET `updated_at` = NOW() WHERE `username` = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LogManager.error("为用户 '" + username + "' 更新最后登录时间时出错: " + e.getMessage());
        }
    }

    public boolean isHwidBanned(String hwid) {
        if (connection == null) return false;
        if (hwid == null || hwid.trim().isEmpty()) return false;
        String query = "SELECT `id` FROM `" + TABLE_NAME + "` WHERE `hwid` = ? AND `is_banned` = 1 LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, hwid);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            LogManager.error("检查HWID封禁状态时出错: " + e.getMessage());
            return false;
        }
    }

    public boolean doesUserExist(String username, String qq) {
        if (connection == null) return true;
        String query = "SELECT `id` FROM `" + TABLE_NAME + "` WHERE `username` = ? OR `qq` = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, qq);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            LogManager.error("检查用户存在性时出错: " + e.getMessage());
            return true;
        }
    }

    public boolean createUser(String username, String passwordHash, String qq, String hwid) {
        if (connection == null) return false;
        String sql = "INSERT INTO `" + TABLE_NAME + "` (`username`, `password`, `qq`, `hwid`, `rank`) VALUES (?, ?, ?, ?, 'FreeUser')";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            stmt.setString(3, qq);
            stmt.setString(4, hwid);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LogManager.error("创建用户 '" + username + "' 时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        if (keepAliveScheduler != null && !keepAliveScheduler.isShutdown()) {
            keepAliveScheduler.shutdownNow();
        }

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
            }
        }
    }

    // ✅ 修复：查询所有字段，并确保顺序与客户端 UserEntity 解析顺序一致
    public List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        if (connection == null) return users;

        // 这里的顺序必须对应客户端的 fields[0] 到 fields[9]
        String sql = "SELECT `id`, `username`, `password`, `hwid`, `is_banned`, `rank`, `created_at`, `updated_at`, `qq`, `tag` FROM `irc_users`";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String id = String.valueOf(rs.getInt("id"));
                String u = rs.getString("username");
                String p = rs.getString("password"); // 注意：这是 Hash
                String h = rs.getString("hwid");
                int b = rs.getInt("is_banned");
                String r = rs.getString("rank");

                // 处理时间可能为空的情况
                String c = rs.getString("created_at");
                if (c == null) c = "Unknown";
                else c = c.substring(0, c.length() - 2); // 去掉 .0

                String up = rs.getString("updated_at");
                if (up == null) up = "Unknown";
                else up = up.substring(0, up.length() - 2);

                String q = rs.getString("qq");
                String t = rs.getString("tag");
                if (t == null) t = "NULL";

                // 拼接字符串: id<|>user<|>pass<|>hwid<|>ban<|>rank<|>created<|>updated<|>qq<|>tag
                StringBuilder sb = new StringBuilder();
                sb.append(id).append("<|>");
                sb.append(u).append("<|>");
                sb.append(p).append("<|>");
                sb.append(h).append("<|>");
                sb.append(b).append("<|>");
                sb.append(r).append("<|>");
                sb.append(c).append("<|>");
                sb.append(up).append("<|>");
                sb.append(q).append("<|>");
                sb.append(t);

                users.add(sb.toString());
            }
        } catch (SQLException e) {
            LogManager.error("获取用户列表失败: " + e.getMessage());
        }
        return users;
    }

    public boolean updateUserField(String userId, String columnName, String newValue) {
        if (connection == null) return false;

        // 为了安全，简单的白名单检查，防止 SQL 注入修改非法列
        if (!isValidColumn(columnName)) {
            LogManager.warn("非法列名更新尝试: " + columnName);
            return false;
        }

        String sql = "UPDATE `irc_users` SET `" + columnName + "` = ? WHERE `id` = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            // 特殊处理：如果传过来的是 "NULL" 字符串且该列允许为空，可以处理成 SQL NULL，这里简化直接存字符串
            stmt.setString(1, newValue);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LogManager.error("更新用户字段失败: " + e.getMessage());
            return false;
        }
    }

    public boolean createUserFull(String u, String pHash, String qq, String hwid, String rank, String tag, int isBanned) {
        if (connection == null) return false;

        // 检查用户名是否存在
        if (doesUserExist(u, qq)) return false;

        String sql = "INSERT INTO `irc_users` (`username`, `password`, `qq`, `hwid`, `rank`, `tag`, `is_banned`, `created_at`, `updated_at`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, u);
            stmt.setString(2, pHash);
            stmt.setString(3, qq);
            stmt.setString(4, hwid);
            stmt.setString(5, rank);
            stmt.setString(6, tag);
            stmt.setInt(7, isBanned);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LogManager.error("创建用户失败: " + e.getMessage());
            return false;
        }
    }

    private boolean isValidColumn(String col) {
        return col.equals("username") || col.equals("password") || col.equals("hwid") ||
                col.equals("is_banned") || col.equals("rank") || col.equals("qq") || col.equals("tag");
    }

    public boolean deleteUser(String username) {
        if (connection == null) return false;
        String sql = "DELETE FROM `irc_users` WHERE `username` = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            LogManager.error("删除用户失败: " + e.getMessage());
            return false;
        }
    }
}