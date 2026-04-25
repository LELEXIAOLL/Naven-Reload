package com.heypixel.heypixelmod.obsoverlay.commands.impl;

import com.heypixel.heypixelmod.obsoverlay.IRCModule.IrcClientManager;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.commands.Command;
import com.heypixel.heypixelmod.obsoverlay.commands.CommandInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@CommandInfo(
        name = "rirc",
        description = "Send a message to the IRC or manage users.",
        aliases = {}
)
public class CommandRIRC extends Command {

    @Override
    public void onCommand(String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reconnect")) {
            IrcClientManager.INSTANCE.manualReconnect();
            return;
        }

        if (!Naven.ircLoggedIn || !IrcClientManager.INSTANCE.isConnected()) {
            ChatUtils.addChatMessage("§c你尚未登录或连接到IRC服务器。");
            return;
        }

        if (args.length == 0) {
            sendHelpMessage();
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                // 列表去重逻辑已在 IrcClientManager.handleUserListMessage 中实现
                IrcClientManager.INSTANCE.sendMessage("get: list");
                break;

            case "c":
                if (args.length < 2) {
                    ChatUtils.addChatMessage("§c用法: .rirc c <消息>");
                    return;
                }
                // 拼接从第二个参数开始的所有内容
                StringJoiner messageBuilder = new StringJoiner(" ");
                for (int i = 1; i < args.length; i++) {
                    messageBuilder.add(args[i]);
                }
                String message = messageBuilder.toString();

                // 获取当前登录的IRC用户名
                String sender = IrcClientManager.INSTANCE.currentUser.ircUsername;
                if (sender == null || sender.isEmpty()) {
                    ChatUtils.addChatMessage("§c无法获取您的IRC用户名，请重新登录。");
                    return;
                }

                String chatCommand = String.format("chat: '%s' '%s'", sender, message);
                IrcClientManager.INSTANCE.sendMessage(chatCommand);
                break;

            default:
                sendHelpMessage();
                break;
        }
    }

    private void sendHelpMessage() {
        ChatUtils.addChatMessage("§b--- IRC 命令帮助 ---");
        ChatUtils.addChatMessage("§7.rirc list §f- 查看IRC在线玩家列表");
        ChatUtils.addChatMessage("§7.rirc c <消息> §f- 发送IRC聊天消息");
        ChatUtils.addChatMessage("§7.rirc reconnect §f- 使用上次凭据重连服务器");
    }

    @Override
    public String[] onTab(String[] args) {
        if (args.length == 1) {
            String[] subCommands = {"list", "c", "reconnect"};
            List<String> completions = new ArrayList<>();
            for (String cmd : subCommands) {
                if (cmd.startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
            return completions.toArray(new String[0]);
        }
        return new String[0];
    }
}