package com.heypixel.heypixelmod.obsoverlay.commands;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.commands.impl.*;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventClientChat;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import java.util.HashMap;
import java.util.Map;

public class CommandManager {
   public static final String PREFIX = ".";
   public final Map<String, Command> aliasMap = new HashMap<>();

   public CommandManager() {
      System.out.println("CommandManager初始化开始");
      try {
         this.initCommands();
         System.out.println("命令注册完成: " + aliasMap.keySet());
      } catch (Exception e) {
         System.err.println("命令初始化失败:");
         e.printStackTrace();
      }
      Naven.getInstance().getEventManager().register(this);
      System.out.println("已注册到事件系统");
   }

   private void initCommands() {
      this.registerCommand(new CommandBind());
      this.registerCommand(new CommandToggle());
      this.registerCommand(new CommandConfig());
      this.registerCommand(new CommandIRC());
      this.registerCommand(new CommandSetHideName());
      this.registerCommand(new CommandBinds());
      this.registerCommand(new CommandLanguage());
      this.registerCommand(new CommandHelp());
      this.registerCommand(new CommandRIRC());
      this.registerCommand(new CommandProxy());
   }

   private void registerCommand(Command command) {
      command.initCommand();
      this.aliasMap.put(command.getName().toLowerCase(), command);

      for (String alias : command.getAliases()) {
         this.aliasMap.put(alias.toLowerCase(), command);
      }
   }

   @EventTarget
   public void onChat(EventClientChat e) {
      System.out.println("[DEBUG] CommandManager收到事件，消息内容：" + e.getMessage());
      if (e.getMessage().startsWith(".")) {
         e.setCancelled(true);
         String chatMessage = e.getMessage().substring(".".length());
         String[] arguments = chatMessage.split(" ");
         if (arguments.length < 1) {
            ChatUtils.addChatMessage("Invalid command.");
            return;
         }

         String alias = arguments[0].toLowerCase();
         Command command = this.aliasMap.get(alias);
         if (command == null) {
            ChatUtils.addChatMessage("Invalid command. Please use .help");
            return;
         }

         String[] args = new String[arguments.length - 1];
         System.arraycopy(arguments, 1, args, 0, args.length);
         command.onCommand(args);
      }
   }
}
