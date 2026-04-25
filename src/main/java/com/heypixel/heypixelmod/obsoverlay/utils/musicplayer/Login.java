package com.heypixel.heypixelmod.obsoverlay.utils.musicplayer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Login {

    private static final String API_BASE_URL = "自行搭建nodejs服务器https://neteasecloudmusicapienhanced.js.org/";

    public static LoginResult loginWithPhoneAndPassword(String phone, String password) {
        try {
            // 1. 对参数进行URL编码，防止特殊字符导致URL格式错误
            String encodedPhone = URLEncoder.encode(phone, StandardCharsets.UTF_8.toString());
            String encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8.toString());

            // 2. 构造完整的请求URL
            String urlString = API_BASE_URL + "/login/cellphone?phone=" + encodedPhone + "&password=" + encodedPassword;
            URL url = new URL(urlString);

            // 3. 创建HTTP连接
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5秒连接超时
            connection.setReadTimeout(5000);    // 5秒读取超时

            // 4. 获取响应码
            int responseCode = connection.getResponseCode();

            // 5. 处理响应
            if (responseCode == HttpURLConnection.HTTP_OK) { // 200 OK
                // 读取响应体
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // 解析JSON以获取Cookie
                String jsonResponse = response.toString();
                String cookie = parseCookieFromJson(jsonResponse);

                if (cookie != null && !cookie.isEmpty()) {
                    return new LoginResult(true, cookie, "登录成功");
                } else {
                    // 响应中可能包含错误信息，例如 {"code":502,"msg":"密码错误"}
                    String errorMessage = parseMessageFromJson(jsonResponse);
                    return new LoginResult(false, null, "未能获取Cookie: " + errorMessage);
                }
            } else {
                // 处理非200的响应码
                return new LoginResult(false, null, "服务器错误, 响应码: " + responseCode);
            }
        } catch (Exception e) {
            // 处理网络异常等
            e.printStackTrace();
            return new LoginResult(false, null, "请求失败: " + e.getMessage());
        }
    }

    private static String parseCookieFromJson(String jsonResponse) {
        String cookieKey = "\"cookie\":\"";
        int startIndex = jsonResponse.indexOf(cookieKey);
        if (startIndex == -1) return null;

        startIndex += cookieKey.length();
        int endIndex = jsonResponse.indexOf("\"", startIndex);
        if (endIndex == -1) return null;

        return jsonResponse.substring(startIndex, endIndex);
    }

    private static String parseMessageFromJson(String jsonResponse) {
        String msgKey = "\"msg\":\"";
        int startIndex = jsonResponse.indexOf(msgKey);
        if (startIndex == -1) return "未知错误";

        startIndex += msgKey.length();
        int endIndex = jsonResponse.indexOf("\"", startIndex);
        if (endIndex == -1) return "未知错误";

        return jsonResponse.substring(startIndex, endIndex);
    }
}
