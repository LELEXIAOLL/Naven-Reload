// 文件路径: com/heypixel/heypixelmod/obsoverlay/modules/impl/render/NameTags.java
package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventShader;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.HackerCheck;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.IRC;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.Teams;
import com.heypixel.heypixelmod.obsoverlay.utils.*;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        name = "NameTags",
        category = Category.RENDER,
        description = "Renders name tags"
)
public class NameTags extends Module {

   public FloatValue scale = ValueBuilder.create(this, "NameTags Size")
           .setDefaultFloatValue(0.3F)
           .setFloatStep(0.01F)
           .setMinFloatValue(0.1F)
           .setMaxFloatValue(1.0F)
           .build()
           .getFloatValue();

   private static final float PADDING = 6.0F;
   private static final float VERTICAL_PADDING = 2.5F;
   private static final float GAP = 2.0F;
   private static final float RADIUS = 3.0F;
   private static final Color BACKGROUND_COLOR = new Color(20, 20, 20, 120);

   private final Map<Entity, NameTagRenderData> entityRenderData = new ConcurrentHashMap<>();

   private IRC ircModule;
   private HackerCheck hackerCheckModule;
   private Teams teamsModule;

   @Override
   public void onEnable() {
      this.ircModule = (IRC) Naven.getInstance().getModuleManager().getModule(IRC.class);
      this.hackerCheckModule = (HackerCheck) Naven.getInstance().getModuleManager().getModule(HackerCheck.class);
      this.teamsModule = (Teams) Naven.getInstance().getModuleManager().getModule(Teams.class);
      super.onEnable();
   }

   @Override
   public void onDisable() {
      entityRenderData.clear();
      super.onDisable();
   }

   @EventTarget
   public void update(EventRender e) {
      entityRenderData.clear();
      if (mc.level == null) return;

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof Player player && entity != mc.player && !entity.isRemoved()) {
            double x = MathUtils.interpolate(e.getRenderPartialTicks(), entity.xo, entity.getX());
            double y = MathUtils.interpolate(e.getRenderPartialTicks(), entity.yo, entity.getY()) + entity.getBbHeight() + 0.5;
            double z = MathUtils.interpolate(e.getRenderPartialTicks(), entity.zo, entity.getZ());
            Vector2f projectedPos = ProjectionUtils.project(x, y, z, e.getRenderPartialTicks());

            if (projectedPos != null) {
               NameTagRenderData data = new NameTagRenderData(projectedPos);
               data.calculateDimensions(player);
               entityRenderData.put(entity, data);
            }
         }
      }
   }

   @EventTarget
   public void onShader(EventShader e) {
      if (e.getType() != EventType.BLUR || !this.isEnabled()) return;

      for (NameTagRenderData data : entityRenderData.values()) {
         if (data.isValid()) {
            RenderUtils.drawRoundedRect(e.getStack(), data.startX, data.startY, data.totalWidth, data.totalHeight, RADIUS, Integer.MIN_VALUE);
         }
      }
   }

   @EventTarget
   public void onRender(EventRender2D e) {
      if (!this.isEnabled()) return;

      for (NameTagRenderData data : entityRenderData.values()) {
         if (data.isValid()) {
            e.getStack().pushPose();

            float currentX = data.startX;
            float startY = data.startY;

            if (!data.prefix.isEmpty()) {
               RenderUtils.drawRoundedRect(e.getStack(), currentX, startY, data.prefixWidth, data.totalHeight, RADIUS, BACKGROUND_COLOR.getRGB());
               Fonts.harmony.render(e.getStack(), data.prefix, currentX + PADDING, startY + VERTICAL_PADDING, data.prefixColor, true, scale.getCurrentValue());
               currentX += data.prefixWidth + GAP;
            }

            RenderUtils.drawRoundedRect(e.getStack(), currentX, startY, data.healthWidth, data.totalHeight, RADIUS, BACKGROUND_COLOR.getRGB());
            Fonts.harmony.render(e.getStack(), data.healthText, currentX + PADDING, startY + VERTICAL_PADDING, data.healthColor, true, scale.getCurrentValue());
            currentX += data.healthWidth + GAP;

            RenderUtils.drawRoundedRect(e.getStack(), currentX, startY, data.nameWidth, data.totalHeight, RADIUS, BACKGROUND_COLOR.getRGB());
            Fonts.harmony.render(e.getStack(), data.nameText, currentX + PADDING, startY + VERTICAL_PADDING, Color.WHITE, true, scale.getCurrentValue());
            currentX += data.nameWidth + GAP;

            RenderUtils.drawRoundedRect(e.getStack(), currentX, startY, data.pingWidth, data.totalHeight, RADIUS, BACKGROUND_COLOR.getRGB());
            Fonts.harmony.render(e.getStack(), data.pingText, currentX + PADDING, startY + VERTICAL_PADDING, data.pingColor, true, scale.getCurrentValue());

            e.getStack().popPose();
         }
      }
   }

   private String getPrefix(Player player) {
      if (hackerCheckModule.isEnabled() && HackerCheck.isHacker(player)) return "Hacker";
      if (teamsModule.isEnabled() && Teams.isSameTeam(player)) return "Team";
      if (ircModule.isBmwIrcEnabled() && IRCManager.getInstance().isIrcUser(player.getName().getString())) return "BMWUser";
      if (FriendManager.isFriend(player)) return "Friend";
      return "";
   }

   private Color getHealthColor(float health) {
      if (health >= 16) return new Color(0, 255, 0);
      if (health >= 6) return new Color(255, 255, 0);
      return new Color(255, 0, 0);
   }

   private Color getPingColor(int ping) {
      if (ping <= 70) return new Color(0, 255, 0);
      if (ping <= 150) return new Color(255, 255, 0);
      if (ping <= 250) return new Color(255, 165, 0);
      return new Color(255, 0, 0);
   }

   private class NameTagRenderData {
      private final Vector2f projectedPos;
      float totalWidth = 0, totalHeight = 0;
      float startX = 0, startY = 0;
      float prefixWidth = 0, healthWidth = 0, nameWidth = 0, pingWidth = 0;
      String prefix, healthText, nameText, pingText;
      Color healthColor, pingColor, prefixColor;

      NameTagRenderData(Vector2f projectedPos) {
         this.projectedPos = projectedPos;
      }

      void calculateDimensions(Player player) {
         this.prefix = getPrefix(player);
         switch (this.prefix) {
            case "Hacker": this.prefixColor = Color.RED; break;
            case "Team": this.prefixColor = Color.GREEN; break;
            case "BMWUser": this.prefixColor = Color.YELLOW; break;
            case "Friend": this.prefixColor = new Color(0, 255, 255); break;
            default: this.prefixColor = Color.WHITE; break;
         }

         float health = player.getHealth() + player.getAbsorptionAmount();
         this.healthText = String.format("%.1f", health);
         this.healthColor = getHealthColor(player.getHealth());

         PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(player.getUUID());
         int ping = 0;

         if (playerInfo != null) {
            this.nameText = playerInfo.getTabListDisplayName() != null ? playerInfo.getTabListDisplayName().getString() : playerInfo.getProfile().getName();
            ping = playerInfo.getLatency();
         } else {
            this.nameText = player.getName().getString();
         }
         this.pingText = ping + "ms";
         this.pingColor = getPingColor(ping);

         float scaleValue = scale.getCurrentValue();
         float fontHeight = (float) Fonts.harmony.getHeight(true, scaleValue);
         this.totalHeight = fontHeight + VERTICAL_PADDING * 2;

         this.prefixWidth = prefix.isEmpty() ? 0 : (Fonts.harmony.getWidth(prefix, scaleValue) + PADDING * 2);
         this.healthWidth = Fonts.harmony.getWidth(healthText, scaleValue) + PADDING * 2;
         this.nameWidth = Fonts.harmony.getWidth(nameText, scaleValue) + PADDING * 2;
         this.pingWidth = Fonts.harmony.getWidth(pingText, scaleValue) + PADDING * 2;

         this.totalWidth = healthWidth + nameWidth + pingWidth + (GAP * 2);
         if (!prefix.isEmpty()) {
            this.totalWidth += prefixWidth + GAP;
         }

         this.startX = projectedPos.x - totalWidth / 2.0f;
         this.startY = projectedPos.y;
      }

      boolean isValid() {
         return this.projectedPos != null && this.totalWidth > 0;
      }
   }
}