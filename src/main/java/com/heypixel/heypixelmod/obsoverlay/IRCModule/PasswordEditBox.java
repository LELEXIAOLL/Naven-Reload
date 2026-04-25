package com.heypixel.heypixelmod.obsoverlay.IRCModule;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class PasswordEditBox extends EditBox {

    public PasswordEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    /**
     * 重写渲染方法，这是实现密码框的核心。
     */
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. 获取当前真实输入的文本
        String realText = this.getValue();

        // 2. 生成一个等长的、由星号组成的字符串
        String passwordText = "*".repeat(realText.length());

        // 3. 在渲染之前，暂时将输入框的“可见”文本设置为星号字符串
        this.setValue(passwordText);

        // 4. 调用父类的原始渲染方法，让它去绘制我们刚刚设置的星号
        super.renderWidget(graphics, mouseX, mouseY, partialTick);

        // 5. 关键：渲染完毕后，立刻将输入框的值恢复为真实的文本！
        // 这样可以确保 getValue() 等方法获取到的永远是用户输入的真实密码，
        // 而不是一串星号。
        this.setValue(realText);
    }
}