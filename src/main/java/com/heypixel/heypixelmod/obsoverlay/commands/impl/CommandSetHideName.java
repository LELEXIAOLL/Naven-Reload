package com.heypixel.heypixelmod.obsoverlay.commands.impl;

import com.heypixel.heypixelmod.obsoverlay.commands.Command;
import com.heypixel.heypixelmod.obsoverlay.commands.CommandInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.NameProtect;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;

@CommandInfo(
        name = "sethiddenname",
        description = "Set NameProtect name",
        aliases = {"snp"}
)
public class CommandSetHideName extends Command {
    @Override
    public void onCommand(String[] args) {
        if (args.length == 0) {
            ChatUtils.addChatMessage("Usage: .sethiddenname <name>");
            return;
        }
        
        String hiddenName = String.join(" ", args);
        NameProtect.setCustomHiddenName(hiddenName);
        ChatUtils.addChatMessage("Hidden name set to: " + hiddenName);
    }

    @Override
    public String[] onTab(String[] args) {
        return new String[0];
    }
}