package com.heypixel.heypixelmod.obsoverlay.commands.impl;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.commands.Command;
import com.heypixel.heypixelmod.obsoverlay.commands.CommandInfo;
import com.heypixel.heypixelmod.obsoverlay.files.FileManager;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@CommandInfo(
        name = "config",
        description = "Manages client configurations.",
        aliases = {"cfg"}
)
public class CommandConfig extends Command {

   @Override
   public void onCommand(String[] args) {
      if (args.length == 0) {
         ChatUtils.addChatMessage("§7=======§6Config System§7=======");
         ChatUtils.addChatMessage("§7注意配置名不含后缀.cfg");
         ChatUtils.addChatMessage("§7父命令:.config / .cfg");
         ChatUtils.addChatMessage("§7.config open : 打开配置文件夹");
         ChatUtils.addChatMessage("§7.config list  : 查看可用配置列表");
         ChatUtils.addChatMessage("§7.config load <配置名称> : 加载配置");
         ChatUtils.addChatMessage("§7.config save <配置名称> : 保存配置");
         return;
      }

      String subCommand = args[0].toLowerCase();

      switch (subCommand) {
         case "open":
            try {
               // 打开正确的配置文件夹
               Runtime.getRuntime().exec("explorer " + FileManager.configFolder.getAbsolutePath());
            } catch (IOException var3) {
               ChatUtils.addChatMessage("§c无法打开配置文件文件夹。");
            }
            break;
         case "load":
            if (args.length < 2) {
               ChatUtils.addChatMessage("§c用法: .config load <配置名>");
               return;
            }
            // 直接传递文件名，不在命令中添加后缀
            Naven.getInstance().getFileManager().load(args[1]);
            break;
         case "save":
            if (args.length < 2) {
               ChatUtils.addChatMessage("§c用法: .config save <配置名>");
               return;
            }
            // 直接传递文件名，不在命令中添加后缀
            Naven.getInstance().getFileManager().save(args[1]);
            break;
         case "list":
            ChatUtils.addChatMessage("§7--- §6可用配置列表 §7---");
            try (Stream<Path> paths = Files.list(FileManager.configFolder.toPath())) {
               paths.filter(Files::isRegularFile)
                       .map(Path::getFileName)
                       .map(Path::toString)
                       .filter(name -> name.endsWith(".cfg"))
                       .forEach(name -> ChatUtils.addChatMessage("§7- §b" + name.replace(".cfg", "")));
            } catch (IOException e) {
               ChatUtils.addChatMessage("§c无法读取配置文件列表。");
            }
            ChatUtils.addChatMessage("§7--------------------");
            break;
         default:
            ChatUtils.addChatMessage("§c未知命令: " + subCommand + " ,请使用.config查看命令列表");
            break;
      }
   }

   @Override
   public String[] onTab(String[] args) {
      if (args.length == 1) {
         return new String[]{"open", "load", "save", "list"};
      }
      return new String[0];
   }
}