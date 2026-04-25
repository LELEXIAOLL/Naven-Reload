package com.heypixel.heypixelmod.obsoverlay.IRCModule;

import com.heypixel.heypixelmod.obsoverlay.utils.HWIDUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class RegisterScreen extends Screen implements IrcClientManager.IrcMessageListener {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("heypixel", "textures/background.png");
    private final Screen previousScreen;
    private EditBox usernameBox;
    private PasswordEditBox passwordBox;
    private EditBox qqBox;
    private EditBox captchaBox;
    private Button getCaptchaButton;
    private Button registerButton;
    private Button returnButton;
    private String status = "";

    public RegisterScreen(Screen previousScreen) {
        super(Component.literal("IRC 注册"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int inputWidth = 200;

        this.usernameBox = new EditBox(this.font, centerX - inputWidth / 2, centerY - 70, inputWidth, 20, Component.literal("用户名"));
        this.addRenderableWidget(usernameBox);
        this.passwordBox = new PasswordEditBox(this.font, centerX - inputWidth / 2, centerY - 45, inputWidth, 20, Component.literal("密码"));
        this.addRenderableWidget(passwordBox);
        this.qqBox = new EditBox(this.font, centerX - inputWidth / 2, centerY - 20, inputWidth, 20, Component.literal("QQ号"));
        this.addRenderableWidget(qqBox);
        this.captchaBox = new EditBox(this.font, centerX - inputWidth / 2, centerY + 5, inputWidth, 20, Component.literal("验证码"));
        this.addRenderableWidget(captchaBox);

        this.getCaptchaButton = Button.builder(Component.literal("获取验证码"), this::handleGetCaptcha)
                .bounds(centerX - inputWidth / 2, centerY + 35, inputWidth, 20)
                .build();
        this.addRenderableWidget(getCaptchaButton);
        this.registerButton = Button.builder(Component.literal("注册"), this::handleRegister)
                .bounds(centerX - inputWidth / 2, centerY + 60, inputWidth, 20)
                .build();
        this.addRenderableWidget(registerButton);
        this.returnButton = Button.builder(Component.literal("返回登录"), (button) -> {
            this.minecraft.setScreen(this.previousScreen);
        }).bounds(centerX - inputWidth / 2, centerY + 85, inputWidth, 20).build();
        this.addRenderableWidget(this.returnButton);
        IrcClientManager.INSTANCE.registerListener(this);
    }

    @Override
    public void removed() {
        IrcClientManager.INSTANCE.unregisterListener(this);
    }

    private void handleGetCaptcha(Button button) {
        final String qq = this.qqBox.getValue();
        if (qq.isEmpty() || !qq.matches("\\d{5,12}")) {
            this.status = "§c请输入有效的QQ号！";
            return;
        }

        this.status = "§e正在连接服务器...";
        this.getCaptchaButton.active = false;

        IrcClientManager.INSTANCE.connect(success -> {
            if (success) {
                this.status = "§e连接成功，正在请求验证码...";
                try {
                    String command = String.format("register: getcaptcha '%s'", qq);
                    IrcClientManager.INSTANCE.sendMessage(command);
                } catch (Exception e) {
                    this.status = "§c请求失败: " + e.getMessage();
                    this.getCaptchaButton.active = true;
                }
            } else {
                this.status = "§c无法连接到服务器，请检查网络。";
                this.getCaptchaButton.active = true;
            }
        });
    }

    private void handleRegister(Button button) {
        String username = this.usernameBox.getValue();
        String password = this.passwordBox.getValue();
        String qq = this.qqBox.getValue();
        String captcha = this.captchaBox.getValue();

        if (username.isEmpty() || password.isEmpty() || qq.isEmpty() || captcha.isEmpty()) {
            this.status = "§c所有字段都不能为空！";
            return;
        }

        // --- 新增：名称和密码合法性检测 ---
        if (!username.matches("^[a-zA-Z0-9]{3,16}$")) {
            this.status = "§c用户名不合法 (3-16位英文或数字)";
            return;
        }
        if (password.length() < 6 || password.length() > 20) {
            this.status = "§c密码长度不合法 (6-20位)";
            return;
        }
        // --- 检测结束 ---

        this.status = "§e正在注册...";
        try {
            String passwordHash = IrcSecurityUtils.sha256(password); // Typo correction: sha256
            String hwid = HWIDUtils.getHWID();
            String command = String.format("register: '%s' '%s' '%s' '%s' '%s'", username, passwordHash, qq, hwid, captcha);
            IrcClientManager.INSTANCE.sendMessage(command);
        } catch (Exception e) {
            this.status = "§c注册失败: " + e.getMessage();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackgroundTexture(graphics);
        graphics.drawCenteredString(this.font, "IRC 用户注册", this.width / 2, this.height / 2 - 100, 0xFFFFFF);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int inputWidth = 200;
        int boxStartX = centerX - inputWidth / 2;
        int labelPadding = 5;
        int verticalCenteringOffset = (20 - this.font.lineHeight) / 2;

        String userLabel = "用户名:";
        graphics.drawString(this.font, userLabel, boxStartX - this.font.width(userLabel) - labelPadding, centerY - 70 + verticalCenteringOffset, 0xFFFFFF, true);

        String passLabel = "密码:";
        graphics.drawString(this.font, passLabel, boxStartX - this.font.width(passLabel) - labelPadding, centerY - 45 + verticalCenteringOffset, 0xFFFFFF, true);

        String qqLabel = "QQ号:";
        graphics.drawString(this.font, qqLabel, boxStartX - this.font.width(qqLabel) - labelPadding, centerY - 20 + verticalCenteringOffset, 0xFFFFFF, true);

        String captchaLabel = "验证码:";
        graphics.drawString(this.font, captchaLabel, boxStartX - this.font.width(captchaLabel) - labelPadding, centerY + 5 + verticalCenteringOffset, 0xFFFFFF, true);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (!this.status.isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 + 115, 0xFFFFFF);
        }
        this.renderServerStatus(graphics);
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

    private void renderBackgroundTexture(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(BACKGROUND_TEXTURE, 0, 0, 0, 0, this.width, this.height, this.width, this.height);
        RenderSystem.disableBlend();
    }

    @Override
    public void onIrcMessage(String decryptedMessage) {
        if (decryptedMessage.startsWith("register:")) {
            this.getCaptchaButton.active = true;
            switch (decryptedMessage) {
                case "register: captcha_sent":
                    this.status = "§a验证码已发送，请检查邮箱！";
                    break;
                case "register: captcha_ratelimited":
                    this.status = "§c请求过于频繁，请1分钟后再试！";
                    break;
                case "register: success":
                    this.status = "§a注册成功！请返回登录。";
                    break;
                case "register: failed_captcha":
                    this.status = "§c注册失败：验证码错误或已过期。";
                    break;
                case "register: failed_userexists":
                    this.status = "§c注册失败：用户名或QQ号已被注册。";
                    break;
                case "register: failed_dberror":
                case "register: failed_format":
                    this.status = "§c注册失败：服务器内部错误。";
                    break;
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previousScreen);
    }
}