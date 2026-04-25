package com.irc;

import com.irc.handler.Login;
import com.irc.handler.LoginAttemptResult;
import com.irc.handler.Register;
import com.irc.service.CaptchaManager;
import com.irc.service.EmailService;
import com.irc.utils.*;
import com.irc.utils.PanelTokenManager; // 明确导入，防止找不到

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final DatabaseManager dbManager;
    private PrintWriter out;
    private static final CaptchaManager captchaManager = new CaptchaManager();
    private static final EmailService emailService = new EmailService();
    private static final Map<String, String> pendingPanelLogins = new ConcurrentHashMap<>();

    private String authenticatedUsername = null;
    private String authenticatedRank = null;
    private String authenticatedTag = null;

    private boolean isPanelSession = false;
    private PrivateKey panelPrivateKey = null;

    public ClientHandler(Socket socket, DatabaseManager dbManager) {
        this.clientSocket = socket;
        this.dbManager = dbManager;
    }

    @Override
    public void run() {
        try {
            clientSocket.setTcpNoDelay(true);
            clientSocket.setSendBufferSize(32768);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            this.out = out;

            String firstLine = in.readLine();
            if (firstLine == null) return;

            if (firstLine.equals("panel")) {
                handlePanelConnection(in, out);
            } else {
                handleClientMessage(firstLine);
                String encryptedLine;
                while ((encryptedLine = in.readLine()) != null) {
                    handleClientMessage(encryptedLine);
                }
            }
        } catch (IOException e) {
        } finally {
            if (isPanelSession) {
                IRC.removePanel(this);
            } else {
                IRC.removeClient(this);
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
            }
        }
    }

    public synchronized void sendPanelMessage(String msg) {
        if (out != null) {
            out.println(AdminSecurityUtils.encryptAES(msg));
            out.flush();
        }
    }

    private void handlePanelConnection(BufferedReader in, PrintWriter out) {
        this.isPanelSession = true;
        LogManager.info("检测到面板连接: " + clientSocket.getRemoteSocketAddress());

        try {
            KeyPair keyPair = AdminSecurityUtils.generateRSAKeyPair();
            this.panelPrivateKey = keyPair.getPrivate();

            String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            sendPanelMessage("k:" + publicKeyBase64);

            String line;
            while ((line = in.readLine()) != null) {
                try {
                    String decrypted = AdminSecurityUtils.decryptRSA(line, this.panelPrivateKey);
                    if (decrypted == null) continue;

                    if (decrypted.startsWith("captchauser:")) {
                        processPanelCaptcha(decrypted, out);
                    }
                    else if (decrypted.startsWith("panellogin:")) {
                        processPanelLogin(decrypted, out);
                    }
                    // 处理 Token 自动登录
                    else if (decrypted.startsWith("paneltoken:")) {
                        processPanelTokenLogin(decrypted, out);
                    }
                    // 处理用户列表请求 (如果 dbManager.getAllUsers() 未定义，这里会报错)
                    else if (decrypted.equals("req_userlist")) {
                        List<String> users = dbManager.getAllUsers();
                        StringBuilder sb = new StringBuilder("resp_userlist:");
                        for (int i = 0; i < users.size(); i++) {
                            sb.append(users.get(i));
                            if (i < users.size() - 1) sb.append(";<;>");
                        }
                        sendPanelMessage(sb.toString());
                    }

                    else if (decrypted.startsWith("req_update:")) {
                        String content = decrypted.substring("req_update:".length());
                        String[] parts = content.split("<\\|>"); // 使用 <|> 分割
                        if (parts.length == 3) {
                            String uid = parts[0];
                            String col = parts[1];
                            String val = parts[2];

                            boolean success = dbManager.updateUserField(uid, col, val);
                            if (success) {
                                LogManager.info("面板更新了用户(ID:" + uid + ") 的 " + col + " 为 " + val);
                                // 可选：发送回执，或者让客户端自己默认成功
                            }
                        }
                    }
                    // 处理删除用户
                    else if (decrypted.startsWith("req_deluser:")) {
                        String targetUser = decrypted.substring("req_deluser:".length());
                        boolean success = dbManager.deleteUser(targetUser);
                        if (success) {
                            sendPanelMessage("resp_deluser:success:" + targetUser);
                            LogManager.info("面板删除了用户: " + targetUser);
                        } else {
                            sendPanelMessage("resp_deluser:fail:" + targetUser);
                        }
                    }
                    else if (decrypted.startsWith("req_createuser:")) {
                        try {
                            String data = decrypted.substring("req_createuser:".length());
                            // 格式: u<|>p<|>qq<|>hwid<|>rank<|>tag<|>ban
                            String[] parts = data.split("<\\|>");
                            if (parts.length >= 7) {
                                String u = parts[0];
                                String p = parts[1];
                                String qq = parts[2];
                                String hwid = parts[3];
                                String rank = parts[4];
                                String tag = parts[5];
                                int ban = Integer.parseInt(parts[6]);

                                boolean success = dbManager.createUserFull(u, p, qq, hwid, rank, tag, ban);
                                if (success) {
                                    sendPanelMessage("resp_createuser:success");
                                    LogManager.info("面板创建了新用户: " + u);
                                } else {
                                    sendPanelMessage("resp_createuser:fail:User/QQ exists or DB error");
                                }
                            } else {
                                sendPanelMessage("resp_createuser:fail:Invalid Format");
                            }
                        } catch (Exception e) {
                            sendPanelMessage("resp_createuser:fail:" + e.getMessage());
                        }
                    }

                } catch (Exception e) {
                    sendPanelMessage("error:decrypt_failed");
                }
            }
        } catch (Exception e) {
            LogManager.error("面板会话异常: " + e.getMessage());
        }
    }

    private void processPanelTokenLogin(String decrypted, PrintWriter out) {
        try {
            String content = decrypted.substring("paneltoken:".length());
            String[] parts = content.split(",");
            if (parts.length != 2) {
                sendPanelMessage("error:token_invalid");
                return;
            }

            String username = parts[0];
            String token = parts[1];
            String currentIp = clientSocket.getInetAddress().getHostAddress();

            if (PanelTokenManager.validateToken(username, token, currentIp)) {
                this.authenticatedUsername = username;
                IRC.addPanel(this);
                LogManager.info("面板自动登录成功(Token): " + username);
                sendPanelMessage("successlogin:" + username + "," + token);
            } else {
                sendPanelMessage("error:token_invalid");
            }
        } catch (Exception e) {
            sendPanelMessage("error:server_error");
        }
    }

    private void processPanelCaptcha(String command, PrintWriter out) {
        String username = command.substring("captchauser:".length());
        String[] creds = dbManager.getUserCredentials(username);
        if (creds == null || !"Dev".equals(creds[3])) {
            sendPanelMessage("nocaptcha");
            return;
        }
        String qq = dbManager.getQQByUsername(username);
        if (qq == null) {
            sendPanelMessage("nocaptcha");
            return;
        }
        String code = captchaManager.generateAndStore(qq, clientSocket.getInetAddress().getHostAddress());
        if (code != null) {
            boolean sent = emailService.sendVerificationEmail(qq + "@qq.com", code);
            if (sent) {
                pendingPanelLogins.put(username, qq);
                sendPanelMessage("successcaptcha");
            } else {
                sendPanelMessage("nocaptcha");
            }
        } else {
            sendPanelMessage("nocaptcha");
        }
    }

    private void processPanelLogin(String decrypted, PrintWriter out) {
        try {
            String content = decrypted.substring("panellogin:".length());
            String[] parts = content.split(",");
            if (parts.length != 3) return;

            String username = parts[0];
            String passwordHash = parts[1];
            String captchaInput = parts[2];

            String[] creds = dbManager.getUserCredentials(username);
            if (creds == null || !"Dev".equals(creds[3]) || !creds[0].equalsIgnoreCase(passwordHash)) {
                sendPanelMessage("error:login_failed");
                return;
            }

            if (!pendingPanelLogins.containsKey(username)) {
                sendPanelMessage("error:captcha_expired");
                return;
            }

            String qq = pendingPanelLogins.get(username);
            if (captchaManager.verify(qq, captchaInput)) {
                pendingPanelLogins.remove(username);

                this.authenticatedUsername = username;
                IRC.addPanel(this);
                LogManager.info("面板管理员登录成功: " + username);

                String currentIp = clientSocket.getInetAddress().getHostAddress();
                String token = PanelTokenManager.createToken(username, currentIp);
                sendPanelMessage("successlogin:" + username + "," + token);
            } else {
                sendPanelMessage("error:captcha_expired");
            }
        } catch (Exception e) {
            sendPanelMessage("error:server_error");
        }
    }

    public void sendMessage(String plainText) throws Exception {
        if (out != null) {
            out.println(SecurityUtils.encryptAES(plainText));
        }
    }

    private void handleClientMessage(String encryptedLine) {
        try {
            String decryptedLine = SecurityUtils.decryptAES(encryptedLine);
            if (this.authenticatedUsername == null) {
                if (decryptedLine.startsWith("login:")) {
                    processLoginRequest(decryptedLine);
                } else if (decryptedLine.startsWith("register:")) {
                    processRegisterRequest(decryptedLine);
                } else {
                    sendMessage("error: unauthorized");
                }
            } else {
                if (decryptedLine.startsWith("update:")) {
                    processUpdateRequest(decryptedLine);
                } else if (decryptedLine.startsWith("get:")) {
                    processGetRequest(decryptedLine);
                } else if (decryptedLine.startsWith("chat:")) {
                    processChatRequest(decryptedLine);
                } else if (decryptedLine.startsWith("check:")) {
                    processCheckRequest(decryptedLine);
                } else if (decryptedLine.startsWith("settag:")) {
                    processSetTagRequest(decryptedLine);
                } else {
                    sendMessage("error: unknown_command");
                }
            }
        } catch (Exception e) {
        }
    }

    private void processLoginRequest(String decryptedLoginRequest) throws Exception {
        Pattern pattern = Pattern.compile("login: '([^']*)' '([^']*)' '([^']*)'");
        Matcher matcher = pattern.matcher(decryptedLoginRequest);
        if (matcher.find()) {
            String username = matcher.group(1);
            String password = matcher.group(2);
            String hwid = matcher.group(3);
            Login loginHandler = new Login(dbManager);
            LoginAttemptResult result = loginHandler.attempt(username, password, hwid);
            switch (result.getStatus()) {
                case SUCCESS:
                    this.authenticatedUsername = result.getUsername();
                    this.authenticatedRank = result.getRank();
                    this.authenticatedTag = result.getTag();
                    IRC.addClient(this);
                    String successMessage = String.format("login: success '%s' '%s'", result.getUsername(), result.getRank());
                    sendMessage(successMessage);
                    break;
                case USER_NOT_FOUND:
                case INVALID_PASSWORD:
                    sendMessage("login: faild");
                    break;
                case HWID_MISMATCH:
                    sendMessage("login faild to errorhwid");
                    break;
                case USER_BANNED:
                case HWID_BANNED:
                    sendMessage("use: ban");
                    break;
            }
        } else {
            sendMessage("login: faild_format");
        }
    }

    private void processRegisterRequest(String decryptedRequest) throws Exception {
        Pattern captchaPattern = Pattern.compile("register: getcaptcha '([^']*)'");
        Matcher captchaMatcher = captchaPattern.matcher(decryptedRequest);
        if (captchaMatcher.find()) {
            String qq = captchaMatcher.group(1);
            String ip = clientSocket.getRemoteSocketAddress().toString();
            Register registerHandler = new Register(dbManager, captchaManager, emailService);
            if (registerHandler.requestCaptcha(qq, ip)) {
                sendMessage("register: captcha_sent");
            } else {
                sendMessage("register: captcha_ratelimited");
            }
            return;
        }

        Pattern registerPattern = Pattern.compile("register: '([^']*)' '([^']*)' '([^']*)' '([^']*)' '([^']*)'");
        Matcher registerMatcher = registerPattern.matcher(decryptedRequest);
        if (registerMatcher.find()) {
            String username = registerMatcher.group(1);
            String passHash = registerMatcher.group(2);
            String qq = registerMatcher.group(3);
            String hwid = registerMatcher.group(4);
            String captcha = registerMatcher.group(5);
            Register registerHandler = new Register(dbManager, captchaManager, emailService);
            Register.RegistrationResult result = registerHandler.attemptRegistration(username, passHash, qq, hwid, captcha);
            switch (result) {
                case SUCCESS:
                    sendMessage("register: success");
                    break;
                case CAPTCHA_INVALID:
                    sendMessage("register: failed_captcha");
                    break;
                case USER_OR_QQ_EXISTS:
                    sendMessage("register: failed_userexists");
                    break;
                case DATABASE_ERROR:
                    sendMessage("register: failed_dberror");
                    break;
                case HWID_BANNED:
                    sendMessage("register: failed_hwidbanned");
                    break;
            }
            return;
        }
        sendMessage("register: failed_format");
    }

    private void processSetTagRequest(String decryptedRequest) throws Exception {
        Pattern pattern = Pattern.compile("settag: '([^']*)'");
        Matcher matcher = pattern.matcher(decryptedRequest);
        if (matcher.find()) {
            String tag = matcher.group(1);
            if (tag.length() > 32) {
                sendMessage("error: tag_too_long");
                return;
            }
            if (!tag.matches("^[a-zA-Z0-9&§_ ]*$")) {
                sendMessage("error: tag_invalid_chars");
                return;
            }
            if (dbManager.updateUserTag(this.authenticatedUsername, tag)) {
                this.authenticatedTag = tag;
                sendMessage("settag: success");
            } else {
                sendMessage("settag: failed");
            }
        } else {
            sendMessage("error: invalid_format");
        }
    }

    private void processUpdateRequest(String decryptedRequest) {
        String[] parts = decryptedRequest.split("'");
        if (parts.length == 2 && decryptedRequest.startsWith("update: name")) {
            IRC.setGameUsername(this.authenticatedUsername, parts[1]);
        }
    }

    private void processGetRequest(String decryptedRequest) throws Exception {
        if (decryptedRequest.equals("get: list")) {
            sendMessage(IRC.getUserListString());
        }
    }

    private void processCheckRequest(String decryptedRequest) throws Exception {
        Pattern pattern = Pattern.compile("check: user '([^']*)'");
        Matcher matcher = pattern.matcher(decryptedRequest);
        if (matcher.find()) {
            String gameUsername = matcher.group(1);
            ClientHandler foundUser = IRC.findUserByGameName(gameUsername);
            String response;
            if (foundUser != null) {
                String ircUsername = foundUser.getAuthenticatedUsername();
                String rank = foundUser.getAuthenticatedRank();
                response = String.format("check: success '%s' '%s' '%s'", gameUsername, ircUsername, rank);
            } else {
                response = String.format("check: notfound '%s'", gameUsername);
            }
            sendMessage(response);
        }
    }
    public void forceDisconnect() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
        }
    }

    private void processChatRequest(String decryptedRequest) {
        Pattern pattern = Pattern.compile("chat: '([^']*)' '([^']*)'");
        Matcher matcher = pattern.matcher(decryptedRequest);
        if (matcher.find()) {
            String sender = matcher.group(1);
            String message = matcher.group(2);
            if (!sender.equals(this.authenticatedUsername)) {
                return;
            }
            String displayTitle;
            if (this.authenticatedTag != null && !this.authenticatedTag.trim().isEmpty()) {
                displayTitle = this.authenticatedTag.replace('&', '§');
            } else {
                displayTitle = IRC.mapRankToDisplay(this.authenticatedRank);
            }
            LogManager.info("聊天<" + this.authenticatedUsername + ">: " + message);
            String broadcastMessage = String.format("chat: '%s' '%s' '%s'", displayTitle, this.authenticatedUsername, message);
            IRC.broadcastMessage(broadcastMessage);
        }
    }

    public String getAuthenticatedUsername() {
        return authenticatedUsername;
    }

    public String getAuthenticatedRank() {
        return authenticatedRank;
    }

    public String getAuthenticatedTag() {
        return authenticatedTag;
    }
}