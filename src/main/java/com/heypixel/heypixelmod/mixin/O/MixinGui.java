package com.heypixel.heypixelmod.mixin.O;

import com.google.common.collect.Lists;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRenderScoreboard;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventSetTitle;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.NoRender;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.Scoreboard;
import javax.annotation.Nullable;

import com.heypixel.heypixelmod.obsoverlay.ui.scoreboard.ScoreBoard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(
        value = {Gui.class},
        priority = 100
)
public class MixinGui {
   @Shadow @Nullable protected Component title;
   @Shadow protected int titleTime;
   @Shadow protected int titleFadeInTime;
   @Shadow protected int titleStayTime;
   @Shadow protected int titleFadeOutTime;
   @Shadow @Nullable protected Component subtitle;
   @Shadow @Final private Minecraft minecraft;

   // --- 已被新的注入取代，可以安全删除 ---
   /*
   @Inject(
      method = {"displayScoreboardSidebar"},
      at = {@At("HEAD")}
   )
   public void hookScoreboardHead(GuiGraphics pPoseStack, Objective pObjective, CallbackInfo ci) {
      // 这个方法的功能与 onRenderScoreboard_Head 冲突，并且依赖于已移除的 'down' 属性
   }

   @Inject(
      method = {"displayScoreboardSidebar"},
      at = {@At("RETURN")}
   )
   public void hookScoreboardReturn(GuiGraphics pPoseStack, Objective pObjective, CallbackInfo ci) {
      // 由于我们在HEAD取消了方法，RETURN注入点永远不会被调用，可以删除
   }
   */

   // --- 这些Redirect仍然有用，用于文字替换，保留它们 ---
   @Redirect(
           method = {"displayScoreboardSidebar"},
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"
           )
   )
   public int hookRenderScore(GuiGraphics instance, Font p_283343_, String p_281896_, int p_283569_, int p_283418_, int p_281560_, boolean p_282130_) {
      Scoreboard module = (Scoreboard)Naven.getInstance().getModuleManager().getModule(Scoreboard.class);
      return module.isEnabled() && module.hideScore.getCurrentValue() ? 0 : instance.drawString(p_283343_, p_281896_, p_283569_, p_283418_, p_281560_);
   }

   @Redirect(
           method = {"displayScoreboardSidebar"},
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"
           )
   )
   public MutableComponent hookScoreboardName(Team pPlayerTeam, Component pPlayerName) {
      MutableComponent mutableComponent = PlayerTeam.formatNameForTeam(pPlayerTeam, pPlayerName);
      EventRenderScoreboard event = new EventRenderScoreboard(mutableComponent);
      Naven.getInstance().getEventManager().call(event);
      return (MutableComponent)event.getComponent();
   }

   @Redirect(
           method = {"displayScoreboardSidebar"},
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;"
           )
   )
   public Component hookScoreboardTitle(Objective instance) {
      Component component = instance.getDisplayName();
      EventRenderScoreboard event = new EventRenderScoreboard(component);
      Naven.getInstance().getEventManager().call(event);
      return event.getComponent();
   }

   // --- 其他注入点保持不变 ---
   @Inject(method = {"setTitle"}, at = {@At("HEAD")}, cancellable = true)
   public void hookTitle(Component pTitle, CallbackInfo ci) { /* ... */ }

   @Inject(method = {"setSubtitle"}, at = {@At("RETURN")}, cancellable = true)
   public void hookSubtitle(Component pSubtitle, CallbackInfo ci) { /* ... */ }

   @Inject(method = {"renderEffects"}, at = {@At("HEAD")}, cancellable = true)
   public void hookRenderEffects(GuiGraphics pPoseStack, CallbackInfo ci) { /* ... */ }

   // 在你的 MixinGui.java 文件中，替换旧的 onRenderScoreboard_Head 方法

   @Inject(
           method = "displayScoreboardSidebar",
           at = @At("HEAD"),
           cancellable = true
   )
   private void onRenderScoreboard_Head(GuiGraphics guiGraphics, Objective objective, CallbackInfo ci) {
      Scoreboard scoreboardModule = (Scoreboard) Naven.getInstance().getModuleManager().getModule(Scoreboard.class);
      if (scoreboardModule == null || !scoreboardModule.isEnabled()) {
         if(scoreboardModule != null) {
            scoreboardModule.clearRenderData();
         }
         return;
      }

      ci.cancel();

      net.minecraft.world.scores.Scoreboard scoreboard = objective.getScoreboard();
      Collection<net.minecraft.world.scores.Score> collection = scoreboard.getPlayerScores(objective);

      List<net.minecraft.world.scores.Score> list = collection.stream()
              .filter(score -> score.getOwner() != null && !score.getOwner().startsWith("#"))
              .collect(Collectors.toList());

      List<net.minecraft.world.scores.Score> scores;
      if (list.size() > 15) {
         scores = Lists.newArrayList(com.google.common.collect.Iterables.skip(list, collection.size() - 15));
      } else {
         scores = list;
      }

      // --- 核心修正：创建 ScoreboardLine 列表 ---
      List<ScoreBoard.ScoreboardLine> scoreLines = Lists.newArrayListWithCapacity(scores.size());
      for (net.minecraft.world.scores.Score score : scores) {
         String ownerName = score.getOwner();
         PlayerTeam playerteam = scoreboard.getPlayersTeam(ownerName);
         Component ownerComponent = Component.literal(ownerName);

         // 获取带颜色的文本部分
         MutableComponent textComponent = PlayerTeam.formatNameForTeam(playerteam, ownerComponent);

         // 获取分数部分
         int scoreValue = score.getScore();

         // 创建并添加 ScoreboardLine 对象
         scoreLines.add(new ScoreBoard.ScoreboardLine(textComponent, scoreValue));
      }

      String title = objective.getDisplayName().getString();
      String footer = (this.minecraft.getCurrentServer() != null)
              ? this.minecraft.getCurrentServer().ip
              : "Singleplayer";

      ScoreBoard.renderCustomScoreboard(guiGraphics, title, scoreLines, footer);
   }
}