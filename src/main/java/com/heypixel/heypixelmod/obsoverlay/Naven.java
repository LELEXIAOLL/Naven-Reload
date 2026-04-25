package com.heypixel.heypixelmod.obsoverlay;

import com.heypixel.heypixelmod.obfuscation.JNICObf;
import com.heypixel.heypixelmod.obsoverlay.IRCModule.IRCLoginScreen;
import com.heypixel.heypixelmod.obsoverlay.IRCModule.IrcClientManager;
import com.heypixel.heypixelmod.obsoverlay.commands.CommandManager;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventManager;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventShutdown;
import com.heypixel.heypixelmod.obsoverlay.files.FileManager;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleManager;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.AutoReport;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.ClickGUIModule;
import com.heypixel.heypixelmod.obsoverlay.ui.hudeditor.HUDEditor;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.*;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.MusicPlayerManager;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.PostProcessRenderer;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Shaders;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.values.HasValueManager;
import com.heypixel.heypixelmod.obsoverlay.values.ValueManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.awt.FontFormatException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@JNICObf
public class Naven {
   private static volatile boolean running = true;
   public static String TITLE = "Loading...";
   private static final String[] SENTENCES = {
           "那年的蝉鸣，比任何誓言都更响亮",
           "月光把我们的影子，拉成回不去的昨天",
           "时间是个沉默的贼，偷走我最珍贵的画面",
           "回忆总在某个不经意的瞬间，将我轻轻绊倒",
           "我们的友谊，是漫长黑夜里的不灭渔火",
           "离别时没说出口的话，沉在了岁月的河底",
           "有些告别轻得像关门声，却震碎了整片星空",
           "有些笑容，被时光打磨得愈发温暖",
           "回忆是珍珠，友情是宝石",
           "那个夏天的风铃，还在记忆里轻轻摇晃",
           "时间是个沉默的贼，偷走我最珍贵的画面"
   };
   private static String chosenSentence;
   private static long startTime;
   private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");
   public static final String CLIENT_NAME = "Naven-Modern";
   public static final String CLIENT_DISPLAY_NAME = "Naven";
   private static Naven instance;
   private final EventManager eventManager;
   private final EventWrapper eventWrapper;
   private final ValueManager valueManager;
   private final HasValueManager hasValueManager;
   private final RotationManager rotationManager;
   public final ModuleManager moduleManager;
   private final CommandManager commandManager;
   private final FileManager fileManager;
   private final NotificationManager notificationManager;
   public static float TICK_TIMER = 1.0F;
   private HUDEditor hudEditor;
   public static Queue<Runnable> skipTasks = new ConcurrentLinkedQueue<>();

   public static boolean ircLoggedIn = false;
   private boolean gameHasLoaded = false;
   public static String userRank = "Null";

   @SubscribeEvent
   public void onWorldJoin(EntityJoinLevelEvent event) {
      if (Minecraft.getInstance().player != null && event.getEntity() == Minecraft.getInstance().player) {
         boolean shouldExit = (!Naven.ircLoggedIn || "Null".equals(Naven.userRank))
                 && !IrcClientManager.INSTANCE.isAttemptingAutoReconnect();

         if (shouldExit) {
            System.exit(0);
         } else if (Naven.ircLoggedIn) {
            String gameUsername = Minecraft.getInstance().player.getName().getString();
            IrcClientManager.INSTANCE.currentUser.gameUsername = gameUsername;
            String updateCommand = String.format("update: name '%s'", gameUsername);
            IrcClientManager.INSTANCE.sendMessage(updateCommand);
         }
      }
   }

   private Naven() {
      startTime = System.currentTimeMillis();
      chosenSentence = SENTENCES[new Random().nextInt(SENTENCES.length)];
      instance = this;

      this.valueManager = new ValueManager();
      this.hasValueManager = new HasValueManager();
      this.eventManager = new EventManager();
      this.rotationManager = new RotationManager();
      this.moduleManager = new ModuleManager();
      this.commandManager = new CommandManager();
      this.hudEditor = new HUDEditor();
      MusicPlayerManager.setup();
      MusicPlayerManager.start();

      Shaders.init();
      PostProcessRenderer.init();

      try {
         Fonts.loadFonts();
      } catch (IOException | FontFormatException var2) {
         throw new RuntimeException(var2);
      }

      this.eventWrapper = new EventWrapper();
      this.fileManager = new FileManager();
      this.notificationManager = new NotificationManager();
      this.fileManager.load();
      this.moduleManager.getModule(ClickGUIModule.class).setEnabled(false);

      this.eventManager.register(getInstance());
      this.eventManager.register(this.eventWrapper);
      this.eventManager.register(new RotationManager());
      this.eventManager.register(new NetworkUtils());
      this.eventManager.register(new ServerUtils());
      this.eventManager.register(new EntityWatcher());
      MinecraftForge.EVENT_BUS.register(new EventWrapper());
      MinecraftForge.EVENT_BUS.register(this);
   }

   public static void modRegister() {
      try {
         new Naven();
      } catch (Exception var1) {
         var1.printStackTrace(System.err);
      }
   }

   private String formatDuration(long millis) {
      long hours = TimeUnit.MILLISECONDS.toHours(millis);
      long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
      long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
      return String.format("%02d:%02d:%02d", hours, minutes, seconds);
   }

   @SubscribeEvent
   public void onClientTick(ClientTickEvent event) {
      if (event.phase == TickEvent.Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc == null || mc.getWindow() == null) {
            return;
         }
         String version = Version.getVersion();
         String systemTime = DATE_FORMAT.format(new Date());
         long uptimeMillis = System.currentTimeMillis() - startTime;
         String uptime = formatDuration(uptimeMillis);
         String ircUsername = IrcClientManager.INSTANCE.currentUser.ircUsername;
         if (ircUsername == null) {
            ircUsername = "未登录";
         }

         String newTitle = String.format(
                 "Naven-Reload-%s User: %s Time：%s",
                 version,
                 ircUsername,
                 uptime
         );
         TITLE = newTitle;
         mc.getWindow().setTitle(TITLE);

         // --- 修正主菜单跳转逻辑 ---
         if (!ircLoggedIn && mc.screen instanceof TitleScreen && !IrcClientManager.INSTANCE.isAttemptingAutoReconnect()) {
            mc.setScreen(new IRCLoginScreen());
            return;
         }

         if (ircLoggedIn && mc.screen instanceof TitleScreen) {
            mc.setScreen(new MainUI());
         }

         // --- 游戏加载后执行一次的逻辑 ---
         if (!this.gameHasLoaded && mc.player != null) {
            if (Objects.equals(Naven.userRank, "FreeUser")) {
               this.moduleManager.getModule(AutoReport.class).setEnabled(true);
            }
            this.gameHasLoaded = true;
         }
      }
   }

   @EventTarget
   public void onShutdown(EventShutdown e) {
      this.fileManager.save();
      IRCManager.killAllBmwProcesses();
      MusicPlayerManager.stop();
      LogUtils.close();
   }

   @EventTarget(0)
   public void onEarlyTick(EventRunTicks e) {
      if (e.getType() == EventType.PRE) {
         TickTimeHelper.update();
      }
   }

   public static Naven getInstance() {
      return instance;
   }

   public EventManager getEventManager() {
      return this.eventManager;
   }

   public EventWrapper getEventWrapper() {
      return this.eventWrapper;
   }

   public ValueManager getValueManager() {
      return this.valueManager;
   }

   public HasValueManager getHasValueManager() {
      return this.hasValueManager;
   }

   public RotationManager getRotationManager() {
      return this.rotationManager;
   }

   public ModuleManager getModuleManager() {
      return this.moduleManager;
   }

   public CommandManager getCommandManager() {
      return this.commandManager;
   }

   public FileManager getFileManager() {
      return this.fileManager;
   }

   public NotificationManager getNotificationManager() {
      return this.notificationManager;
   }

   public HUDEditor getHudEditor() {
      return hudEditor;
   }
}