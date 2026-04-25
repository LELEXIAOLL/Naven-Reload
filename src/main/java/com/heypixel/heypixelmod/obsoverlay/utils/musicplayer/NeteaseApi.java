package com.heypixel.heypixelmod.obsoverlay.utils.musicplayer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NeteaseApi {
    private static final String API_BASE_URL = "自行搭建服务器https://neteasecloudmusicapienhanced.js.org/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36";

    // ... (保持原有的 LyricResult, QrKeyResult 等内部类不变) ...
    public static class LyricResult {
        public final boolean success;
        public final String lrc;
        public LyricResult(boolean success, String lrc) {
            this.success = success;
            this.lrc = lrc;
        }
    }

    public static class QrKeyResult {
        public final boolean success;
        public final String key;
        public QrKeyResult(boolean success, String key) { this.success = success; this.key = key; }
    }

    public static class QrCodeResult {
        public final boolean success;
        public final String base64Image;
        public QrCodeResult(boolean success, String base64Image) { this.success = success; this.base64Image = base64Image; }
    }

    public static class QrStatusResult {
        public final int code;
        public final String message;
        public final String cookie;
        public QrStatusResult(int code, String message, String cookie) { this.code = code; this.message = message; this.cookie = cookie; }
    }

    public static class LogoutResult {
        public final boolean success;
        public LogoutResult(boolean success) { this.success = success; }
    }

    public static class LoginStatusResult {
        public final boolean isLoggedIn;
        public final String nickname;
        public final String userId;
        public LoginStatusResult(boolean isLoggedIn, String nickname, String userId) {
            this.isLoggedIn = isLoggedIn; this.nickname = nickname; this.userId = userId;
        }
    }

    public static class UserDetailResult {
        public final boolean success;
        public final String avatarUrl;
        public UserDetailResult(boolean success, String avatarUrl) {
            this.success = success; this.avatarUrl = avatarUrl;
        }
    }

    public static class LikedSongsResult {
        public final boolean success;
        public final List<Likelist> songs;
        public LikedSongsResult(boolean success, List<Likelist> songs) {
            this.success = success; this.songs = songs;
        }
    }

    public static class SongUrlResult {
        public final boolean success;
        public final String url;
        public SongUrlResult(boolean success, String url) { this.success = success; this.url = url; }
    }

    public static class VipInfoResult {
        public final boolean isVip;
        public VipInfoResult(boolean isVip) {
            this.isVip = isVip;
        }
    }

    public static class UserPlaylistsResult {
        public final boolean success;
        public final List<PlaylistItem> playlists;
        public UserPlaylistsResult(boolean success, List<PlaylistItem> playlists) {
            this.success = success; this.playlists = playlists;
        }
    }

    public static VipInfoResult getVipInfo(String uid, String cookie) {
        try {
            String encodedCookie = URLEncoder.encode(cookie, StandardCharsets.UTF_8.toString());
            String endpoint = "/vip/info?uid=" + uid + "&cookie=" + encodedCookie;
            String jsonResponse = makeGetRequest(endpoint);
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (root.has("code") && root.get("code").getAsInt() == 200) {
                if (root.has("data") && root.get("data").isJsonObject()) {
                    JsonObject data = root.getAsJsonObject("data");
                    if (data.has("redVipLevel") && data.get("redVipLevel").getAsInt() > 0) {
                        return new VipInfoResult(true);
                    }
                }
            }
            return new VipInfoResult(false);
        } catch (Exception e) {
            e.printStackTrace();
            return new VipInfoResult(false);
        }
    }

    public static boolean followUser(String userIdToFollow, int t, String cookie) {
        try {
            String encodedCookie = URLEncoder.encode(cookie, StandardCharsets.UTF_8.toString());
            String endpoint = "/follow?id=" + userIdToFollow + "&t=" + t + "&cookie=" + encodedCookie;
            String jsonResponse = makeGetRequest(endpoint);
            String code = parseSimpleJson(jsonResponse, "code");
            return "200".equals(code);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static SongUrlResult getSongUrl(long songId, String cookie) {
        try {
            String encodedCookie = URLEncoder.encode(cookie, StandardCharsets.UTF_8.toString());
            // 加上 cookie 参数，并请求 exhigh (极高) 音质
            String endpoint = "/song/url/v1?id=" + songId + "&level=exhigh&cookie=" + encodedCookie;
            String jsonResponse = makeGetRequest(endpoint);

            // 解析 URL
            String urlKey = "\"url\":\"http";
            int urlStartIndex = jsonResponse.indexOf(urlKey);
            if (urlStartIndex != -1) {
                urlStartIndex += "\"url\":\"".length();
                int urlEndIndex = jsonResponse.indexOf('"', urlStartIndex);
                if (urlEndIndex != -1) {
                    String url = jsonResponse.substring(urlStartIndex, urlEndIndex);
                    if (!url.isEmpty()) {
                        // 很多时候返回的链接是 http 的，强制转为 https 有时更稳定，但这里先保持原样
                        return new SongUrlResult(true, url);
                    }
                }
            }
            return new SongUrlResult(false, null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SongUrlResult(false, null);
        }
    }

    public static LikedSongsResult fetchUserLikedSongs(String uid, String cookie) {
        try {
            String likelistUrl = "/likelist?uid=" + uid + "&cookie=" + URLEncoder.encode(cookie, StandardCharsets.UTF_8.toString());
            String likelistResponse = makeGetRequest(likelistUrl);
            String[] ids = parseJsonArray(likelistResponse, "ids");
            if (ids == null || ids.length == 0) {
                return new LikedSongsResult(true, new ArrayList<>());
            }
            String idsParam = String.join(",", ids);
            String detailUrl = "/song/detail?ids=" + idsParam;
            String detailResponse = makeGetRequest(detailUrl);
            List<Likelist> songList = parseSongDetails(detailResponse);
            return new LikedSongsResult(true, songList);
        } catch (Exception e) {
            e.printStackTrace();
            return new LikedSongsResult(false, null);
        }
    }

    // --- 新增：获取用户歌单列表 ---
    public static UserPlaylistsResult fetchUserPlaylists(String uid, String cookie) {
        try {
            String endpoint = "/user/playlist?uid=" + uid + "&cookie=" + URLEncoder.encode(cookie, StandardCharsets.UTF_8.toString());
            String jsonResponse = makeGetRequest(endpoint);

            List<PlaylistItem> playlists = new ArrayList<>();
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();

            if (root.has("playlist") && root.get("playlist").isJsonArray()) {
                com.google.gson.JsonArray playlistArray = root.getAsJsonArray("playlist");
                for (com.google.gson.JsonElement element : playlistArray) {
                    JsonObject plObj = element.getAsJsonObject();

                    long id = plObj.get("id").getAsLong();
                    String name = plObj.has("name") ? plObj.get("name").getAsString() : "未知歌单";
                    String coverUrl = plObj.has("coverImgUrl") ? plObj.get("coverImgUrl").getAsString() : null;
                    int trackCount = plObj.has("trackCount") ? plObj.get("trackCount").getAsInt() : 0;

                    String creatorName = "未知创建者";
                    if (plObj.has("creator") && plObj.get("creator").isJsonObject()) {
                        creatorName = plObj.getAsJsonObject("creator").get("nickname").getAsString();
                    }

                    playlists.add(new PlaylistItem(id, name, coverUrl, trackCount, creatorName));
                }
            }
            return new UserPlaylistsResult(true, playlists);
        } catch (Exception e) {
            e.printStackTrace();
            return new UserPlaylistsResult(false, null);
        }
    }

    // --- 新增：获取歌单内的所有歌曲 ---
    public static LikedSongsResult fetchPlaylistSongs(long playlistId, String cookie) {
        try {
            // 使用 /playlist/track/all 接口获取歌单所有歌曲
            String endpoint = "/playlist/track/all?id=" + playlistId + "&limit=1000&cookie=" + URLEncoder.encode(cookie, StandardCharsets.UTF_8.toString());
            String jsonResponse = makeGetRequest(endpoint);

            // 这个接口返回的结构与 song/detail 类似，包含 songs 数组，可以直接复用解析逻辑
            List<Likelist> songList = parseSongDetails(jsonResponse);
            return new LikedSongsResult(true, songList);
        } catch (Exception e) {
            e.printStackTrace();
            return new LikedSongsResult(false, null);
        }
    }

    // ... (保持 getQrKey, getLyric, logout, createQrCode, checkQrStatus, checkLoginStatus, getUserDetail, downloadImage 现有方法不变) ...
    public static QrKeyResult getQrKey() {
        try {
            String jsonResponse = makeGetRequest("/login/qr/key");
            String key = parseSimpleJson(jsonResponse, "unikey");
            return new QrKeyResult(key != null, key);
        } catch (Exception e) { e.printStackTrace(); return new QrKeyResult(false, null); }
    }

    public static LyricResult getLyric(long songId) {
        try {
            String endpoint = "/lyric?id=" + songId;
            String jsonResponse = makeGetRequest(endpoint);

            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();

            if (root.has("lrc") && root.get("lrc").isJsonObject()) {
                JsonObject lrcObject = root.getAsJsonObject("lrc");
                if (lrcObject.has("lyric") && lrcObject.get("lyric").isJsonPrimitive()) {
                    String lrcText = lrcObject.get("lyric").getAsString();
                    return new LyricResult(true, lrcText);
                }
            }

            if (root.has("nolyric") && root.get("nolyric").getAsBoolean()) {
                return new LyricResult(false, "[00:00.00]此歌曲为纯音乐\n");
            }

            return new LyricResult(false, "[00:00.00]暂无歌词\n");

        } catch (Exception e) {
            e.printStackTrace();
            return new LyricResult(false, "[00:00.00]歌词加载失败\n");
        }
    }

    public static LogoutResult logout(String cookie) {
        try {
            String postData = "cookie=" + URLEncoder.encode(cookie, StandardCharsets.UTF_8.toString());
            String jsonResponse = makePostRequest("/logout", postData);
            String code = parseSimpleJson(jsonResponse, "code");
            return new LogoutResult("200".equals(code));
        } catch (Exception e) {
            e.printStackTrace();
            return new LogoutResult(false);
        }
    }

    public static QrCodeResult createQrCode(String key) {
        try {
            String jsonResponse = makeGetRequest("/login/qr/create?key=" + key + "&qrimg=true");
            String base64 = parseSimpleJson(jsonResponse, "qrimg");
            if (base64 != null && base64.contains(",")) {
                base64 = base64.substring(base64.indexOf(',') + 1);
            }
            return new QrCodeResult(base64 != null, base64);
        } catch (Exception e) { e.printStackTrace(); return new QrCodeResult(false, null); }
    }

    public static QrStatusResult checkQrStatus(String key) {
        try {
            String endpoint = "/login/qr/check?key=" + key + "&timestamp=" + System.currentTimeMillis() + "&noCookie=true";
            String jsonResponse = makeGetRequest(endpoint);
            int code = Integer.parseInt(parseSimpleJson(jsonResponse, "code"));
            String message = parseSimpleJson(jsonResponse, "message");
            String cookie = parseSimpleJson(jsonResponse, "cookie");
            return new QrStatusResult(code, message, cookie);
        } catch (Exception e) { e.printStackTrace(); return new QrStatusResult(-1, "Request failed", null); }
    }

    public static LoginStatusResult checkLoginStatus(String cookie) {
        try {
            String postData = "cookie=" + URLEncoder.encode(cookie, StandardCharsets.UTF_8.toString());
            String jsonResponse = makePostRequest("/login/status", postData);
            String nickname = parseNestedJson(jsonResponse, "profile", "nickname");
            String userId = parseNestedJson(jsonResponse, "profile", "userId");
            boolean success = nickname != null && !nickname.isEmpty() && userId != null;
            return new LoginStatusResult(success, nickname, userId);
        } catch (Exception e) { e.printStackTrace(); return new LoginStatusResult(false, null, null); }
    }

    public static UserDetailResult getUserDetail(String uid) {
        try {
            String jsonResponse = makeGetRequest("/user/detail?uid=" + uid);
            String avatarUrl = parseNestedJson(jsonResponse, "profile", "avatarUrl");
            return new UserDetailResult(avatarUrl != null, avatarUrl);
        } catch (Exception e) { e.printStackTrace(); return new UserDetailResult(false, null); }
    }

    public static InputStream downloadImage(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        return connection.getInputStream();
    }

    // ... (保持私有辅助方法 makeGetRequest, makePostRequest, parseSimpleJson 等不变) ...
    private static String makeGetRequest(String endpoint) throws Exception {
        URL url = new URL(API_BASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", USER_AGENT);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }

    private static String makePostRequest(String endpoint, String postData) throws Exception {
        URL url = new URL(API_BASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Content-Length", Integer.toString(postData.getBytes().length));
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", USER_AGENT);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = postData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }

    private static String parseSimpleJson(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return null;

        startIndex += searchKey.length();
        while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
            startIndex++;
        }
        if (startIndex >= json.length()) return null;

        char firstChar = json.charAt(startIndex);
        int endIndex;

        if (firstChar == '"') {
            startIndex++;
            endIndex = json.indexOf('"', startIndex);
            if (endIndex == -1) return null;
            return json.substring(startIndex, endIndex);
        } else if (json.substring(startIndex).startsWith("null")) {
            return null;
        } else {
            endIndex = json.indexOf(',', startIndex);
            int braceEndIndex = json.indexOf('}', startIndex);
            if (endIndex == -1 || (braceEndIndex != -1 && braceEndIndex < endIndex)) {
                endIndex = braceEndIndex;
            }
            if (endIndex == -1) {
                braceEndIndex = json.lastIndexOf('}');
                if (braceEndIndex > startIndex) {
                    return json.substring(startIndex, braceEndIndex).trim();
                }
                return json.substring(startIndex).trim();
            }
            return json.substring(startIndex, endIndex).trim();
        }
    }

    private static String parseNestedJson(String json, String parentKey, String childKey) {
        String searchKey = "\"" + parentKey + "\":{";
        int parentIndex = json.indexOf(searchKey);
        if (parentIndex == -1) return null;

        String childSearchKey = "\"" + childKey + "\":\"";
        int childIndex = json.indexOf(childSearchKey, parentIndex);
        if (childIndex != -1) {
            childIndex += childSearchKey.length();
            int endIndex = json.indexOf("\"", childIndex);
            if (endIndex != -1) return json.substring(childIndex, endIndex);
        } else {
            childSearchKey = "\"" + childKey + "\":";
            childIndex = json.indexOf(childSearchKey, parentIndex);
            if (childIndex != -1) {
                childIndex += childSearchKey.length();
                int endIndex = json.indexOf(",", childIndex);
                if (endIndex == -1) endIndex = json.indexOf("}", childIndex);
                if (endIndex != -1) return json.substring(childIndex, endIndex).trim();
            }
        }
        return null;
    }

    private static String[] parseJsonArray(String json, String key) {
        String searchKey = "\"" + key + "\":[";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return null;
        startIndex += searchKey.length();
        int endIndex = json.indexOf("]", startIndex);
        if (endIndex == -1) return null;

        String arrayContent = json.substring(startIndex, endIndex);
        if (arrayContent.isEmpty()) return new String[0];
        return arrayContent.split(",");
    }

    // 修改可见性为public，以便fetchPlaylistSongs复用
    public static List<Likelist> parseSongDetails(String json) {
        List<Likelist> songs = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("songs") && root.get("songs").isJsonArray()) {
                com.google.gson.JsonArray songsArray = root.getAsJsonArray("songs");

                for (com.google.gson.JsonElement element : songsArray) {
                    JsonObject songObj = element.getAsJsonObject();

                    long id = songObj.get("id").getAsLong();
                    String name = songObj.has("name") ? songObj.get("name").getAsString() : "未知歌曲";
                    long duration = songObj.has("dt") ? songObj.get("dt").getAsLong() : 0;

                    String coverUrl = null;
                    if (songObj.has("al") && songObj.get("al").isJsonObject()) {
                        JsonObject alObj = songObj.getAsJsonObject("al");
                        if (alObj.has("picUrl")) {
                            coverUrl = alObj.get("picUrl").getAsString();
                        }
                    }

                    String artistName = "未知歌手";
                    if (songObj.has("ar") && songObj.get("ar").isJsonArray()) {
                        com.google.gson.JsonArray arArray = songObj.getAsJsonArray("ar");
                        List<String> artistNames = new ArrayList<>();
                        for (com.google.gson.JsonElement arElement : arArray) {
                            JsonObject arObj = arElement.getAsJsonObject();
                            if (arObj.has("name")) {
                                artistNames.add(arObj.get("name").getAsString());
                            }
                        }
                        if (!artistNames.isEmpty()) {
                            artistName = String.join(" / ", artistNames);
                        }
                    }

                    songs.add(new Likelist(id, name, artistName, coverUrl, duration));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return songs;
    }
}
