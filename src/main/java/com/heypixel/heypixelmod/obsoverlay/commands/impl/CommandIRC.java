// 文件路径: com/heypixel/heypixelmod/obsoverlay/commands/impl/CommandIRC.java

package com.heypixel.heypixelmod.obsoverlay.commands.impl;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.commands.Command;
import com.heypixel.heypixelmod.obsoverlay.commands.CommandInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.IRC;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.IRCManager;

import java.util.ArrayList;
import java.util.List;

@CommandInfo(
        name = "irc",
        description = "Send a message to the IRC or manage users.",
        aliases = {}
)
public class CommandIRC extends Command {

    @Override
    public void onCommand(String[] args) {
        IRC ircModule = (IRC) Naven.getInstance().getModuleManager().getModule(IRC.class);
        if (ircModule == null || !ircModule.isEnabled() || !IRCManager.getInstance().isProcessRunning()) {
            ChatUtils.addChatMessage("§cIRC is not enabled or connected.");
            return;
        }

        if (args.length == 0) {
            ChatUtils.addChatMessage("§cUsage: .irc <message> | .irc look | .irc list");
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "look":
                List<String> visibleUsers = IRCManager.getInstance().getVisibleIrcUsers();
                if (visibleUsers.isEmpty()) {
                    ChatUtils.addChatMessage("§e[BMWIRC] §fNo BMWUsers found in your lobby.");
                } else {
                    ChatUtils.addChatMessage("§e[BMWIRC] §fVisible BMWUsers:");
                    for (String user : visibleUsers) {
                        ChatUtils.addChatMessage("§7- §a" + user);
                    }
                }
                break;

            // [新增] 处理 "list" 子命令
            case "list":
                IRCManager.getInstance().requestUserList();
                break;

            default:
                // 如果不是已知子命令，则视为发送消息
                String message = String.join(" ", args);
                IRCManager.getInstance().sendMessage(message);
                break;
        }
    }

    @Override
    public String[] onTab(String[] args) {
        if (args.length == 1) {
            String[] subCommands = {"look", "list"};
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