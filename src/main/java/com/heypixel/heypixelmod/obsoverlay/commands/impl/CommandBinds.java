package com.heypixel.heypixelmod.obsoverlay.commands.impl;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.commands.Command;
import com.heypixel.heypixelmod.obsoverlay.commands.CommandInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Comparator;

@CommandInfo(
        name = "binds",
        description = "Lists all modules that have a key bind",
        aliases = {"keybinds"}
)
public class CommandBinds extends Command {
    @Override
    public void onCommand(String[] args) {
        if (args.length > 0) {
            ChatUtils.addChatMessage("Usage: .binds");
            return;
        }

        ChatUtils.addChatMessage("§7--- §6Key Binds §7---");

        boolean foundBinds = false;
        for (Module module : Naven.getInstance().getModuleManager().getModules()) {
            int key = module.getKey();

            // 检查按键是否为未绑定状态
            if (key != InputConstants.UNKNOWN.getValue()) {
                String keyName = InputConstants.getKey(key, 0).getDisplayName().getString();

                // 检查按键名称是否为 "0"
                if (!keyName.equals("key.keyboard.0")) {
                    foundBinds = true;
                    String moduleName = module.getName();
                    ChatUtils.addChatMessage("§7" + moduleName + "§f: §b" + keyName);
                }
            }
        }

        if (!foundBinds) {
            ChatUtils.addChatMessage("§c没有找到任何已绑定的按键。");
        }

        ChatUtils.addChatMessage("§7-----------------");
    }

    @Override
    public String[] onTab(String[] args) {
        return new String[0]; // .binds 命令没有子参数，所以返回空数组
    }
}