package com.heypixel.heypixelmod.obsoverlay.commands.impl;

import com.heypixel.heypixelmod.obsoverlay.commands.Command;
import com.heypixel.heypixelmod.obsoverlay.commands.CommandInfo;
import com.heypixel.heypixelmod.obsoverlay.files.FileManager;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;

import java.io.IOException;

@CommandInfo(
        name = "help",
        description = "Get help",
        aliases = {"h"}
)
public class CommandHelp extends Command {
    @Override
    public void onCommand(String[] args) {
        ChatUtils.addChatMessage("========Naven-Re========");
        ChatUtils.addChatMessage(".help .h 当前帮助列表");
        ChatUtils.addChatMessage(".config .cfg 配置文件系统");
        ChatUtils.addChatMessage(".bind .b 绑定按键");
        ChatUtils.addChatMessage(".language .lang 语言");
        ChatUtils.addChatMessage(".proxy .prox 代理设置");
        ChatUtils.addChatMessage(".toggle .t 功能切换");
        ChatUtils.addChatMessage(".sethiddenname .snp 设置名称保护NameProtect的名称(&用于颜色)");
        ChatUtils.addChatMessage(".irc list BMWIRC在线列表");
        ChatUtils.addChatMessage(".irc <消息> BMWIRC聊天");
        ChatUtils.addChatMessage(".rirc list Naven-ReloadIRC在线列表");
        ChatUtils.addChatMessage(".rirc c <消息> Naven-ReloadIRC聊天");
    }

    @Override
    public String[] onTab(String[] args) {
        return new String[0];
    }
}
