package com.heypixel.heypixelmod.obsoverlay.IRCModule;

import com.heypixel.heypixelmod.obsoverlay.MainUI;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.files.impl.UserFile;
import com.heypixel.heypixelmod.obsoverlay.utils.HWIDUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class IRCLoginScreen extends Screen implements IrcClientManager.IrcMessageListener {

    private static final ResourceLocation BACKGROUND_TEXTURE =new ResourceLocation("heypixel", "textures/background.png");

    private EditBox usernameBox;
    private PasswordEditBox passwordBox;
    private Button loginButton;
    private Button registerButton;
    private String status = "";
    private boolean isLoggingIn = false;
    private long loginStartTime = 0;
    private static final long LOGIN_TIMEOUT_MS = 10000;

    public IRCLoginScreen() {
        super(Component.literal("IRC 登录"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int inputWidth = 200;

        this.usernameBox = new EditBox(this.font, centerX - inputWidth / 2, centerY - 40, inputWidth, 20, Component.literal("用户名"));
        this.addRenderableWidget(this.usernameBox);

        this.passwordBox = new PasswordEditBox(this.font, centerX - inputWidth / 2, centerY - 15, inputWidth, 20, Component.literal("密码"));
        this.addRenderableWidget(this.passwordBox);

        // --- 新增代码：自动填充逻辑 ---
        String savedUser = UserFile.getStoredUsername();
        String savedPass = UserFile.getStoredPassword();

        if (savedUser != null && !savedUser.isEmpty()) {
            this.usernameBox.setValue(savedUser);
        }
        if (savedPass != null && !savedPass.isEmpty()) {
            this.passwordBox.setValue(savedPass);
        }

        this.loginButton = Button.builder(Component.literal("登陆"), this::handleLogin)
                .bounds(centerX - inputWidth / 2, centerY + 15, inputWidth, 20)
                .build();
        this.addRenderableWidget(this.loginButton);

        this.registerButton = Button.builder(Component.literal("注册"), (button) -> {
            this.minecraft.setScreen(new RegisterScreen(this));
        }).bounds(centerX - inputWidth / 2, centerY + 40, inputWidth, 20).build();
        this.addRenderableWidget(this.registerButton);

        IrcClientManager.INSTANCE.registerListener(this);

        if (!IrcClientManager.INSTANCE.isConnected()) {
            IrcClientManager.INSTANCE.connect();
        }
    }

    @Override
    public void removed() {
        IrcClientManager.INSTANCE.unregisterListener(this);
    }

    private void handleLogin(Button button) {
        if (isLoggingIn) return;
        String username = this.usernameBox.getValue();
        String password = this.passwordBox.getValue();

        if (username.isEmpty() || password.isEmpty()) {
            this.status = "§c用户名或密码不能为空！";
            return;
        }

        if (!username.matches("^[a-zA-Z0-9]+$")) {
            this.status = "§c用户名只能包含英文和数字！";
            return;
        }

        this.status = "§e正在登陆...";
        setInputsActive(false);
        isLoggingIn = true;
        loginStartTime = System.currentTimeMillis();
        try {
            String hwid = HWIDUtils.getHWID();
            String command = String.format("login: '%s' '%s' '%s'", username, password, hwid);
            IrcClientManager.INSTANCE.sendMessage(command);
        } catch (Exception e) {
            e.printStackTrace();
            this.status = "§c加密失败: " + e.getMessage();
            resetLoginState();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isLoggingIn && (System.currentTimeMillis() - loginStartTime > LOGIN_TIMEOUT_MS)) {
            this.status = "§c登陆失败：网络错误。";
            resetLoginState();
        }
    }

    private void resetLoginState() {
        isLoggingIn = false;
        loginStartTime = 0;
        setInputsActive(true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackgroundTexture(graphics);
        graphics.drawCenteredString(this.font, "IRC 登陆面板", this.width / 2, this.height / 2 - 80, 0xFFFFFF);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int inputWidth = 200;
        int boxStartX = centerX - inputWidth / 2;
        int labelPadding = 5;
        int verticalCenteringOffset = (20 - this.font.lineHeight) / 2;

        String userLabel = "用户名:";
        graphics.drawString(this.font, userLabel, boxStartX - this.font.width(userLabel) - labelPadding, centerY - 40 + verticalCenteringOffset, 0xFFFFFF, true);

        String passLabel = "密码:";
        graphics.drawString(this.font, passLabel, boxStartX - this.font.width(passLabel) - labelPadding, centerY - 15 + verticalCenteringOffset, 0xFFFFFF, true);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (!this.status.isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 + 70, 0xFFFFFF);
        }
        renderHwid(graphics, mouseX, mouseY);
        this.renderServerStatus(graphics);

        graphics.flush();
    }

    private void renderServerStatus(GuiGraphics graphics) {
        boolean isOnline = IrcClientManager.INSTANCE.isConnected();
        String statusText = isOnline ? "在线" : "离线";
        int statusColor = isOnline ? 0x55FF55 : 0xFF5555;

        String prefixText = "IRCServer: ";
        int prefixWidth = this.font.width(prefixText);

        graphics.drawString(this.font, prefixText, 5, this.height - this.font.lineHeight - 5, 0xFFFFFF, true);
        graphics.drawString(this.font, statusText, 5 + prefixWidth, this.height - this.font.lineHeight - 5, statusColor, true);
    }

    private void renderHwid(GuiGraphics graphics, int mouseX, int mouseY) {
        String hwidText = "HWID: " + HWIDUtils.getHWID();
        int textWidth = this.font.width(hwidText);
        int x = this.width - textWidth - 5;
        int y = this.height - this.font.lineHeight - 5;
        boolean isHovered = mouseX >= x && mouseX <= x + textWidth && mouseY >= y && mouseY <= y + this.font.lineHeight;
        int color = isHovered ? 0xFFFFFF00 : 0xFFAAAAAA;
        graphics.drawString(this.font, hwidText, x, y, color, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        String hwidText = "HWID: " + HWIDUtils.getHWID();
        int textWidth = this.font.width(hwidText);
        int x = this.width - textWidth - 5;
        int y = this.height - this.font.lineHeight - 5;
        if (button == 0 && mouseX >= x && mouseX <= x + textWidth && mouseY >= y && mouseY <= y + this.font.lineHeight) {
            this.minecraft.keyboardHandler.setClipboard(HWIDUtils.getHWID());
            this.status = ChatFormatting.GREEN + "HWID已复制到剪贴板";
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderBackgroundTexture(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(BACKGROUND_TEXTURE, 0, 0, 0, 0, this.width, this.height, this.width, this.height);
        RenderSystem.disableBlend();
    }

    private void setInputsActive(boolean active) {
        this.usernameBox.setEditable(active);
        this.passwordBox.setEditable(active);
        this.loginButton.active = active;
        this.registerButton.active = active;
    }

    @Override
    public void onIrcMessage(String decryptedMessage) {
        if (decryptedMessage.startsWith("login:") || decryptedMessage.startsWith("use:")) {
            isLoggingIn = false;
        }
        if (decryptedMessage.startsWith("login: success")) {
            String[] parts = decryptedMessage.split("'");
            if (parts.length >= 4) {
                String usernameToSave = this.usernameBox.getValue();
                String passwordToSave = this.passwordBox.getValue();
                IrcClientManager.INSTANCE.setLoginCredentials(usernameToSave, passwordToSave);
                UserFile.saveCredentials(usernameToSave, passwordToSave);

                String username = parts[1];
                this.status = "§a登陆成功! 欢迎, " + username;

                if (this.minecraft != null) {
                    this.minecraft.setScreen(new MainUI());
                }
            } else {
                this.status = "§c登陆失败: 客户端与服务器版本不兼容";
                resetLoginState();
            }
        } else if (decryptedMessage.equals("login: faild")) {
            this.status = "§c登陆失败: 用户名或密码错误";
            resetLoginState();
        } else if (decryptedMessage.equals("login faild to errorhwid")) {
            this.status = "§c登陆失败: HWID不匹配";
            resetLoginState();
        } else if (decryptedMessage.equals("use: ban")) {
            this.status = "§4登陆失败: 您的账户或设备已被封禁";
            resetLoginState();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}