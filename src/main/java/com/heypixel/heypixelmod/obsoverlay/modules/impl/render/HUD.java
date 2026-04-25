package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.IRCModule.IrcClientManager;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.Version;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventShader;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleManager;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.SilenceFixMode;
import com.heypixel.heypixelmod.obsoverlay.ui.hudeditor.HUDEditor;
import com.heypixel.heypixelmod.obsoverlay.ui.lingdong.LingDong;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.heypixel.heypixelmod.obsoverlay.utils.StencilUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.StringUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.text.CustomTextRenderer;
import com.heypixel.heypixelmod.obsoverlay.values.Value;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector4f;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@ModuleInfo(
        name = "HUD",
        description = "Displays information on your screen",
        category = Category.RENDER
)
public class HUD extends Module {
   public static final int headerColor = new Color(150, 45, 45, 255).getRGB();
   public static final int bodyColor = new Color(0, 0, 0, 120).getRGB();
   public static final int backgroundColor = new Color(0, 0, 0, 40).getRGB();
   private static final ResourceLocation USER_ICON_RL = new ResourceLocation("heypixel", "textures/neverlose/avatar.png");
   private static final ResourceLocation PING_ICON_RL = new ResourceLocation("heypixel", "textures/neverlose/history.png");
   private static final ResourceLocation FPS_ICON_RL = new ResourceLocation("heypixel", "textures/neverlose/chart-line.png");
   private static final ResourceLocation TIME_ICON_RL = new ResourceLocation("heypixel", "textures/neverlose/time.png");
   private static final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
   private Color gradientColor1 = new Color(255, 90, 90);
   private Color gradientColor2 = new Color(90, 90, 255);
   public BooleanValue waterMark = ValueBuilder.create(this, "Water Mark").setDefaultBooleanValue(true).build().getBooleanValue();
   public BooleanValue betterTabBWFix = ValueBuilder.create(this, "BetterTab BWFix").setDefaultBooleanValue(true).build().getBooleanValue();
   public ModeValue waterMarkMode = ValueBuilder.create(this, "WaterMark Mode")
           .setVisibility(this.waterMark::getCurrentValue)
           .setDefaultModeIndex(0)
           .setModes("Naven", "LingDong", "NeverLose", "Mugen")
           .build()
           .getModeValue();
   public FloatValue watermarkSize = ValueBuilder.create(this, "Watermark Size")
           .setVisibility(this.waterMark::getCurrentValue)
           .setDefaultFloatValue(0.4F)
           .setFloatStep(0.01F)
           .setMinFloatValue(0.1F)
           .setMaxFloatValue(0.5F)
           .build()
           .getFloatValue();
   public FloatValue lingDongBgAlpha = ValueBuilder.create(this, "LingDong Alpha")
           .setVisibility(() -> this.waterMark.getCurrentValue() && this.waterMarkMode.isCurrentMode("LingDong"))
           .setDefaultFloatValue(55.0F)
           .setFloatStep(1.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .build()
           .getFloatValue();
   public BooleanValue watermarkBlur = ValueBuilder.create(this, "Watermark Blur")
           .setVisibility(this.waterMark::getCurrentValue)
           .setDefaultBooleanValue(true)
           .build()
           .getBooleanValue();
   public BooleanValue moduleToggleSound = ValueBuilder.create(this, "Module Toggle Sound").setDefaultBooleanValue(true).build().getBooleanValue();
   public BooleanValue bettertab = ValueBuilder.create(this, "BetterTab")
           .setVisibility(() -> waterMarkMode.isCurrentMode("LingDong"))
           .setDefaultBooleanValue(true)
           .build()
           .getBooleanValue();
   public BooleanValue notification = ValueBuilder.create(this, "Notification").setDefaultBooleanValue(true).build().getBooleanValue();
   public ModeValue notificationStyle = ValueBuilder.create(this, "Notification Style")
           .setVisibility(this.notification::getCurrentValue)
           .setDefaultModeIndex(0)
           .setModes("Naven", "SouthSide", "Reload", "LingDong")
           .build()
           .getModeValue();
   public BooleanValue moduleToggleAnimation = ValueBuilder.create(this, "Module Toggle Animation").setDefaultBooleanValue(true).build().getBooleanValue();
   public BooleanValue arrayList = ValueBuilder.create(this, "Array List").setDefaultBooleanValue(true).build().getBooleanValue();
   public ModeValue arrayListStyle = ValueBuilder.create(this, "ArrayList Style")
           .setVisibility(this.arrayList::getCurrentValue)
           .setDefaultModeIndex(0)
           .setModes("Naven", "Zen")
           .build()
           .getModeValue();
   public BooleanValue prettyModuleName = ValueBuilder.create(this, "Pretty Module Name")
           .setOnUpdate(value -> Module.update = true)
           .setVisibility(this.arrayList::getCurrentValue)
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();
   public BooleanValue hideRenderModules = ValueBuilder.create(this, "Hide Render Modules")
           .setOnUpdate(value -> Module.update = true)
           .setVisibility(this.arrayList::getCurrentValue)
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();
   public BooleanValue rainbow = ValueBuilder.create(this, "Rainbow")
           .setDefaultBooleanValue(true)
           .setVisibility(this.arrayList::getCurrentValue)
           .build()
           .getBooleanValue();
   public BooleanValue gradient = ValueBuilder.create(this, "Gradient")
           .setDefaultBooleanValue(false)
           .setVisibility(() -> this.arrayList.getCurrentValue())
           .setOnUpdate(new Consumer<Value>() {
              @Override
              public void accept(Value value) {
                 if (((BooleanValue) value).getCurrentValue()) {
                    rainbow.setCurrentValue(false);
                 }
              }
           })
           .build()
           .getBooleanValue();
   public FloatValue rainbowSpeed = ValueBuilder.create(this, "Rainbow Speed")
           .setVisibility(this.arrayList::getCurrentValue)
           .setMinFloatValue(1.0F)
           .setMaxFloatValue(20.0F)
           .setDefaultFloatValue(10.0F)
           .setFloatStep(0.1F)
           .build()
           .getFloatValue();
   public FloatValue rainbowOffset = ValueBuilder.create(this, "Rainbow Offset")
           .setVisibility(this.arrayList::getCurrentValue)
           .setMinFloatValue(1.0F)
           .setMaxFloatValue(20.0F)
           .setDefaultFloatValue(10.0F)
           .setFloatStep(0.1F)
           .build()
           .getFloatValue();
   public FloatValue gradientSpeed = ValueBuilder.create(this, "Gradient Speed")
           .setVisibility(() -> this.arrayList.getCurrentValue() && this.gradient.getCurrentValue())
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(30.0F)
           .setDefaultFloatValue(10.0F)
           .setFloatStep(0.1F)
           .build()
           .getFloatValue();
   public ModeValue gradientDirection = ValueBuilder.create(this, "Gradient Direction")
           .setVisibility(() -> this.arrayList.getCurrentValue() && this.gradient.getCurrentValue())
           .setDefaultModeIndex(0)
           .setModes("Horizontal", "Vertical")
           .build()
           .getModeValue();
   public FloatValue gradientColor1Red = ValueBuilder.create(this, "Gradient Color1 Red")
           .setVisibility(() -> this.arrayList.getCurrentValue() && this.gradient.getCurrentValue())
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .setDefaultFloatValue(255.0F)
           .setFloatStep(1.0F)
           .setOnUpdate(value -> updateGradientColors())
           .build()
           .getFloatValue();
   public FloatValue gradientColor1Green = ValueBuilder.create(this, "Gradient Color1 Green")
           .setVisibility(() -> this.arrayList.getCurrentValue() && this.gradient.getCurrentValue())
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .setDefaultFloatValue(90.0F)
           .setFloatStep(1.0F)
           .setOnUpdate(value -> updateGradientColors())
           .build()
           .getFloatValue();
   public FloatValue gradientColor1Blue = ValueBuilder.create(this, "Gradient Color1 Blue")
           .setVisibility(() -> this.arrayList.getCurrentValue() && this.gradient.getCurrentValue())
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .setDefaultFloatValue(90.0F)
           .setFloatStep(1.0F)
           .setOnUpdate(value -> updateGradientColors())
           .build()
           .getFloatValue();
   public FloatValue gradientColor2Red = ValueBuilder.create(this, "Gradient Color2 Red")
           .setVisibility(() -> this.arrayList.getCurrentValue() && this.gradient.getCurrentValue())
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .setDefaultFloatValue(90.0F)
           .setFloatStep(1.0F)
           .setOnUpdate(value -> updateGradientColors())
           .build()
           .getFloatValue();
   public FloatValue gradientColor2Green = ValueBuilder.create(this, "Gradient Color2 Green")
           .setVisibility(() -> this.arrayList.getCurrentValue() && this.gradient.getCurrentValue())
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .setDefaultFloatValue(90.0F)
           .setFloatStep(1.0F)
           .setOnUpdate(value -> updateGradientColors())
           .build()
           .getFloatValue();
   public FloatValue gradientColor2Blue = ValueBuilder.create(this, "Gradient Color2 Blue")
           .setVisibility(() -> this.arrayList.getCurrentValue() && this.gradient.getCurrentValue())
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .setDefaultFloatValue(255.0F)
           .setFloatStep(1.0F)
           .setOnUpdate(value -> updateGradientColors())
           .build()
           .getFloatValue();
   public ModeValue arrayListDirection = ValueBuilder.create(this, "ArrayList Direction")
           .setVisibility(this.arrayList::getCurrentValue)
           .setDefaultModeIndex(0)
           .setModes("Right", "Left")
           .build()
           .getModeValue();
   public FloatValue xOffset = ValueBuilder.create(this, "X Offset")
           .setVisibility(this.arrayList::getCurrentValue)
           .setMinFloatValue(-100.0F)
           .setMaxFloatValue(100.0F)
           .setDefaultFloatValue(1.0F)
           .setFloatStep(1.0F)
           .build()
           .getFloatValue();
   public FloatValue yOffset = ValueBuilder.create(this, "Y Offset")
           .setVisibility(this.arrayList::getCurrentValue)
           .setMinFloatValue(1.0F)
           .setMaxFloatValue(100.0F)
           .setDefaultFloatValue(1.0F)
           .setFloatStep(1.0F)
           .build()
           .getFloatValue();
   public FloatValue arrayListSize = ValueBuilder.create(this, "ArrayList Size")
           .setVisibility(this.arrayList::getCurrentValue)
           .setDefaultFloatValue(0.4F)
           .setFloatStep(0.01F)
           .setMinFloatValue(0.1F)
           .setMaxFloatValue(1.0F)
           .build()
           .getFloatValue();
   private ModuleManager moduleManager;
   private List<ModuleData> renderModuleData;
   private final StringBuilder stringBuilder = new StringBuilder();
   private String timeString = "";
   private long lastTimeUpdate = 0;
   private SilenceFixMode silenceFixModeModule;
   private String lastSilenceFixMode = "";
   float width;
   float watermarkHeight;
   List<Vector4f> blurMatrices = new ArrayList<>();
   List<ModuleToggleNotification> moduleToggleNotifications = new ArrayList<>();
   private long gradientStartTime = System.currentTimeMillis();

   @Override
   public void onEnable() {
      this.moduleManager = Naven.getInstance().getModuleManager();
      this.silenceFixModeModule = (SilenceFixMode) this.moduleManager.getModule(SilenceFixMode.class);
      Module.update = true;
      super.onEnable();
   }

   @EventTarget
   public void notification(EventRender2D e) {
      if (this.notification.getCurrentValue()) {
         Naven.getInstance().getNotificationManager().onRender(e);
      }
   }

   public String getModuleDisplayName(Module module) {
      if (module instanceof SilenceFixMode) {
         String currentMode = ((SilenceFixMode) module).silencemode.getCurrentMode();
         switch (currentMode) {
            case "SkyWarsPerformance": return "------------空岛高性能模式------------";
            case "SkyWars": return "------------空岛模式------------";
            case "BedWarsPerformance": return "------------起床高性能模式------------";
            case "BedWars": return "------------起床模式------------";
            case "PVPPerformance": return "------------竞技场高性能模式------------";
            case "PVP": return "------------竞技场模式------------";
            case "Dick": return "------------高玩模式------------";
            case "ChineseKongFu": return "------------中国功夫------------";
            default: return "SilenceFixMode";
         }
      }
      String name = this.prettyModuleName.getCurrentValue() ? module.getPrettyName() : module.getName();
      String suffix = module.getSuffix();
      if (suffix == null || suffix.isEmpty()) {
         return name;
      }
      stringBuilder.setLength(0);
      return stringBuilder.append(name).append(" §7").append(suffix).toString();
   }

   @EventTarget
   public void onModuleToggle(Module module) {
      if (this.moduleToggleAnimation.getCurrentValue()) {
         ModuleToggleNotification notification = new ModuleToggleNotification(module.getName(), module.isEnabled());
         this.moduleToggleNotifications.add(notification);
         notification.show();
         new Thread(() -> {
            try {
               Thread.sleep(3000);
               notification.hide();
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
         }).start();
      }
   }

   private void updateGradientColors() {
      this.gradientColor1 = new Color(
              (int)gradientColor1Red.getCurrentValue(),
              (int)gradientColor1Green.getCurrentValue(),
              (int)gradientColor1Blue.getCurrentValue()
      );
      this.gradientColor2 = new Color(
              (int)gradientColor2Red.getCurrentValue(),
              (int)gradientColor2Green.getCurrentValue(),
              (int)gradientColor2Blue.getCurrentValue()
      );
   }

   private int blendColors(Color color1, Color color2, float ratio) {
      ratio = Math.max(0.0f, Math.min(1.0f, ratio));
      int r = (int) (color1.getRed() * (1 - ratio) + color2.getRed() * ratio);
      int g = (int) (color1.getGreen() * (1 - ratio) + color2.getGreen() * ratio);
      int b = (int) (color1.getBlue() * (1 - ratio) + color2.getBlue() * ratio);
      return new Color(r, g, b).getRGB();
   }

   private int getFlowingGradientColor(float position) {
      long elapsed = System.currentTimeMillis() - gradientStartTime;
      float flowProgress = (float) ((elapsed % (2000 / gradientSpeed.getCurrentValue())) / 2000.0);
      float ratio = (position + flowProgress) % 1.0f;
      if (ratio > 0.5f) {
         ratio = 1.0f - ratio;
      }
      return blendColors(gradientColor1, gradientColor2, ratio * 2);
   }

   @EventTarget
   public void onShader(EventShader e) {
      if (this.notification.getCurrentValue() && e.getType() == EventType.SHADOW) {
         Naven.getInstance().getNotificationManager().onRenderShader(e);
      }
      if (this.waterMark.getCurrentValue() && waterMarkMode.isCurrentMode("Naven") && e.getType() == EventType.BLUR) {
         RenderUtils.drawRoundedRect(e.getStack(), 5.0F, 5.0F, this.width, this.watermarkHeight + 8.0F, 5.0F, Integer.MIN_VALUE);
      }
      if (this.arrayList.getCurrentValue() && e.getType() == EventType.BLUR) {
         for (Vector4f blurMatrix : this.blurMatrices) {
            RenderUtils.fillBound(e.getStack(), blurMatrix.x(), blurMatrix.y(), blurMatrix.z(), blurMatrix.w(), 1073741824);
         }
      }
      if (this.waterMark.getCurrentValue() && this.waterMarkMode.isCurrentMode("LingDong")) {
         if (e.getType() == EventType.BLUR) LingDong.renderShaderEffects(e.getStack(), "blur");
         if (e.getType() == EventType.SHADOW) LingDong.renderShaderEffects(e.getStack(), "shadow");
      }
   }

   private void renderMugenWatermark(EventRender2D e) {
      HUDEditor.HUDElement wmElement = HUDEditor.getInstance().getHUDElement("watermark");
      if (wmElement == null) return;
      e.getStack().pushPose();
      String text = "Naven-Reload";
      float scale = this.watermarkSize.getCurrentValue() * 2.0f;
      CustomTextRenderer font = Fonts.harmony;
      int lightBlue = new Color(100, 200, 255).getRGB();
      int white = Color.WHITE.getRGB();
      float textWidth = font.getWidth(text, scale);
      float fontHeight = (float) font.getHeight(true, scale);
      float x = (float) wmElement.x;
      float y = (float) wmElement.y;
      float paddingHorizontal = 6.0f;
      float paddingVertical = 4.0f;
      float totalW = textWidth + paddingHorizontal * 2;
      float totalH = fontHeight + paddingVertical * 2;
      wmElement.width = totalW;
      wmElement.height = totalH;
      long time = System.currentTimeMillis() % 4000L;
      int phase = (int) (time / 1000L);
      float rawProgress = (time % 1000L) / 1000.0f;
      float progress = 1.0f - (float) Math.pow(1.0f - rawProgress, 3);
      float rectX = x;
      float rectY = y;
      float rectW = 0;
      float rectH = 0;
      float lineThickness = 2.0f;

      switch (phase) {
         case 0:
            rectX = x;
            rectY = y;
            rectH = totalH;
            rectW = lineThickness + (totalW - lineThickness) * progress;
            break;
         case 1:
            rectX = x;
            rectW = totalW;
            float startH = totalH;
            float endH = lineThickness;
            rectH = startH - (startH - endH) * progress;
            rectY = y + (totalH - rectH);
            break;
         case 2:
            rectH = lineThickness;
            rectY = y + totalH - lineThickness;
            float startW = totalW;
            float endW = lineThickness;
            rectW = startW - (startW - endW) * progress;
            rectX = x;
            break;
         case 3:
            rectW = lineThickness;
            rectX = x;
            float startH2 = lineThickness;
            float endH2 = totalH;
            rectH = startH2 + (endH2 - startH2) * progress;
            rectY = y + totalH - rectH;
            break;
      }
      float textDrawX = x + paddingHorizontal;
      float textDrawY = y + paddingVertical;
      font.render(e.getStack(), text, textDrawX, textDrawY, new Color(lightBlue), true, scale);
      StencilUtils.write(false);
      RenderUtils.drawRectBound(e.getStack(), rectX, rectY, rectW, rectH, lightBlue);
      StencilUtils.erase(true);
      RenderUtils.drawRectBound(e.getStack(), rectX, rectY, rectW, rectH, lightBlue);
      font.render(e.getStack(), text, textDrawX, textDrawY, new Color(white), true, scale);
      StencilUtils.dispose();
      e.getStack().popPose();
   }

   private void renderNeverLoseWatermark(EventRender2D e) {
      GuiGraphics guiGraphics = e.getGuiGraphics();
      final Color BG_COLOR = new Color(0, 0, 0, 180);
      final Color ACCENT_COLOR = new Color(255, 255, 255);
      final float PADDING_HORIZONTAL = 8.0f;
      final float PADDING_VERTICAL = 4.0f;
      final float GAP = 10.0f;
      final float RADIUS = 8.0f;
      CustomTextRenderer font = Fonts.harmony;
      float sizeScale = this.watermarkSize.getCurrentValue();
      String clientName = "N-Re";
      String userName = IrcClientManager.INSTANCE.currentUser.ircUsername;
      PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(mc.player.getUUID());
      String ping = (playerInfo != null) ? playerInfo.getLatency() + "ms" : "0ms";
      String fps = mc.fpsString.split(" ")[0] + "fps";
      String time = new SimpleDateFormat("HH:mm").format(new Date());
      float fontHeight = (float) font.getHeight(true, sizeScale);
      int iconSize = (int) (fontHeight + 1);
      float totalHeight = (float)iconSize + PADDING_VERTICAL * 2;
      float navenWidth = font.getWidth(clientName, sizeScale) + PADDING_HORIZONTAL * 2;
      float iconGap = 4.0f;
      float infoGap = GAP / 2;
      float mainBoxWidth = (PADDING_HORIZONTAL * 2) +
              (iconSize + iconGap + font.getWidth(userName, sizeScale) + infoGap) +
              (iconSize + iconGap + font.getWidth(ping, sizeScale) + infoGap) +
              (iconSize + iconGap + font.getWidth(fps, sizeScale) + infoGap) +
              (iconSize + iconGap + font.getWidth(time, sizeScale));
      float totalWidth = navenWidth + GAP + mainBoxWidth;
      e.getStack().pushPose();
      float screenWidth = mc.getWindow().getGuiScaledWidth();
      float startX = (screenWidth - totalWidth) / 2.0f;
      float startY = 20.0f;
      RenderUtils.drawRoundedRect(e.getStack(), startX, startY, navenWidth, totalHeight, RADIUS, BG_COLOR.getRGB());
      font.render(e.getStack(), clientName, startX + PADDING_HORIZONTAL, startY + PADDING_VERTICAL + (totalHeight - PADDING_VERTICAL*2 - fontHeight)/2, ACCENT_COLOR, true, sizeScale);
      float mainBoxX = startX + navenWidth + GAP;
      RenderUtils.drawRoundedRect(e.getStack(), mainBoxX, startY, mainBoxWidth, totalHeight, RADIUS, BG_COLOR.getRGB());
      float currentX = mainBoxX + PADDING_HORIZONTAL;
      float textY = startY + PADDING_VERTICAL + (totalHeight - PADDING_VERTICAL*2 - fontHeight)/2;
      float iconY = startY + PADDING_VERTICAL;
      guiGraphics.blit(USER_ICON_RL, (int)currentX, (int)iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
      currentX += iconSize + iconGap;
      font.render(e.getStack(), userName, currentX, textY, Color.WHITE, true, sizeScale);
      currentX += font.getWidth(userName, sizeScale) + infoGap;
      guiGraphics.blit(PING_ICON_RL, (int)currentX, (int)iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
      currentX += iconSize + iconGap;
      font.render(e.getStack(), ping, currentX, textY, Color.WHITE, true, sizeScale);
      currentX += font.getWidth(ping, sizeScale) + infoGap;
      guiGraphics.blit(FPS_ICON_RL, (int)currentX, (int)iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
      currentX += iconSize + iconGap;
      font.render(e.getStack(), fps, currentX, textY, Color.WHITE, true, sizeScale);
      currentX += font.getWidth(fps, sizeScale) + infoGap;
      guiGraphics.blit(TIME_ICON_RL, (int)currentX, (int)iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
      currentX += iconSize + iconGap;
      font.render(e.getStack(), time, currentX, textY, Color.WHITE, true, sizeScale);
      e.getStack().popPose();
   }

   private void renderNavenWatermark(EventRender2D e) {
      HUDEditor.HUDElement wmElement = HUDEditor.getInstance().getHUDElement("watermark");
      if (wmElement == null) return;
      e.getStack().pushPose();
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastTimeUpdate > 1000) {
         lastTimeUpdate = currentTime;
         timeString = new SimpleDateFormat("HH:mm:ss").format(new Date());
      }
      stringBuilder.setLength(0);
      String renderusername = IrcClientManager.INSTANCE.currentUser.ircUsername;
      stringBuilder.append("Naven-Reload | ").append(Version.getVersion()).append(" | ").append(renderusername).append(" | ")
              .append(mc.fpsString.split(" ")[0]).append(" FPS | ")
              .append(timeString);
      String text = stringBuilder.toString();
      double newWatermarkSize = (double) this.watermarkSize.getCurrentValue() * 1.5;
      float width = Fonts.opensans.getWidth(text, newWatermarkSize) + 14.0F;
      float height = (float) Fonts.opensans.getHeight(true, newWatermarkSize) + 8.0F;
      this.width = width;
      this.watermarkHeight = height - 8.0F;
      float x = (float) wmElement.x;
      float y = (float) wmElement.y;
      wmElement.width = width;
      wmElement.height = height;
      StencilUtils.write(false);
      RenderUtils.drawRoundedRect(e.getStack(), x, y, width, height, 5.0F, Integer.MIN_VALUE);
      StencilUtils.erase(true);
      // 修改后的高度：红色头部减少为4.0F，黑色背景从+4.0F开始
      RenderUtils.fill(e.getStack(), x, y, x + width, y + 4.0F, headerColor);
      RenderUtils.fill(e.getStack(), x, y + 4.0F, x + width, y + height, bodyColor);
      Fonts.opensans.render(e.getStack(), text, x + 7.0, y + 5.0, Color.WHITE, true, newWatermarkSize);
      StencilUtils.dispose();
      e.getStack().popPose();
   }

   private void renderLingDongWatermark(EventRender2D e) {
      e.getStack().pushPose();
      String renderusername = IrcClientManager.INSTANCE.currentUser.ircUsername;
      LingDong.render(e.getGuiGraphics(), Fonts.opensans, Version.getVersion(), mc.fpsString.split(" ")[0], renderusername, this.watermarkSize.getCurrentValue(), this.moduleManager);
      e.getStack().popPose();
   }

   @EventTarget
   public void onRender(EventRender2D e) {
      CustomTextRenderer font = Fonts.opensans;
      if (this.moduleToggleAnimation.getCurrentValue()) {
         e.getStack().pushPose();
         for (ModuleToggleNotification notification : new ArrayList<>(this.moduleToggleNotifications)) {
            notification.update();
            if (notification.isExpired()) {
               this.moduleToggleNotifications.remove(notification);
               continue;
            }
            String text = notification.getModuleName() + " " + (notification.isEnabled() ? "Enabled" : "Disabled");
            float textWidth = font.getWidth(text, 0.5);
            float textHeight = (float) font.getHeight(true, 0.5);
            float centerX = (float)mc.getWindow().getGuiScaledWidth() / 2.0F;
            float centerY = 30.0F;
            float scale = notification.getScale();
            float alpha = notification.getAlpha();
            if (scale > 0.01f && alpha > 0.01f) {
               e.getStack().pushPose();
               e.getStack().translate(centerX, centerY, 0);
               e.getStack().scale(scale, scale, 1.0F);
               e.getStack().translate(-centerX, -centerY, 0);
               float rectWidth = textWidth + 20.0F;
               float rectHeight = textHeight + 10.0F;
               float rectX = centerX - rectWidth / 2.0F;
               float rectY = centerY - rectHeight / 2.0F;
               int bgColor = new Color(0, 0, 0, (int)(180 * alpha)).getRGB();
               int borderColor = new Color(255, 255, 255, (int)(100 * alpha)).getRGB();
               RenderUtils.drawRoundedRect(e.getStack(), rectX, rectY, rectWidth, rectHeight, 15.0F, bgColor);
               RenderUtils.drawRoundedRect(e.getStack(), rectX - 2.0F, rectY - 2.0F, rectWidth + 4.0F, rectHeight + 4.0F, 15.0F, borderColor);
               RenderUtils.drawRoundedRect(e.getStack(), rectX, rectY, rectWidth, rectHeight, 15.0F, bgColor);
               font.setAlpha(alpha);
               font.render(e.getStack(), text, rectX + 10.0F, rectY + 5.0F, Color.WHITE, true, 0.5);
               font.setAlpha(1.0F);
               e.getStack().popPose();
            }
         }
         e.getStack().popPose();
      }

      if (this.waterMark.getCurrentValue()) {
         switch (waterMarkMode.getCurrentMode()) {
            case "Naven": renderNavenWatermark(e); break;
            case "LingDong": renderLingDongWatermark(e); break;
            case "NeverLose": renderNeverLoseWatermark(e); break;
            case "Mugen": renderMugenWatermark(e); break;
         }
      }

      this.blurMatrices.clear();
      if (this.arrayList.getCurrentValue()) {
         if (arrayListStyle.isCurrentMode("Naven")) {
            renderNavenArrayList(e);
         } else if (arrayListStyle.isCurrentMode("Zen")) {
            renderZenArrayList(e);
         }
      }
   }

   private void prepareRenderData() {
      if (Module.update || this.renderModuleData == null) {
         List<Module> modules = new ArrayList<>(this.moduleManager.getModules());
         if (this.hideRenderModules.getCurrentValue()) {
            modules.removeIf(modulex -> modulex.getCategory() == Category.RENDER);
         }
         this.renderModuleData = modules.stream()
                 .map(module -> {
                    String displayName = this.getModuleDisplayName(module);
                    CustomTextRenderer currentFont = StringUtils.containChinese(displayName) ? Fonts.harmony : Fonts.opensans;
                    float width = currentFont.getWidth(displayName, this.arrayListSize.getCurrentValue());
                    return new ModuleData(module, displayName, width, currentFont);
                 })
                 .collect(Collectors.toList());
         this.renderModuleData.sort(Comparator.comparingDouble(ModuleData::getWidth).reversed());
         Module.update = false;
      }
   }
   private void renderNavenArrayList(EventRender2D e) {
      e.getStack().pushPose();
      prepareRenderData();
      if (this.renderModuleData == null || this.renderModuleData.isEmpty()) {
         e.getStack().popPose();
         return;
      }
      HUDEditor.HUDElement arraylistElement = HUDEditor.getInstance().getHUDElement("arraylist");
      if (arraylistElement == null) {
         e.getStack().popPose();
         return;
      }
      float maxWidth = this.renderModuleData.get(0).getWidth();
      float arrayListX = (float) arraylistElement.x;
      float arrayListY = (float) arraylistElement.y;
      float totalEnabledHeight = 0;
      for (ModuleData data : renderModuleData) {
         if (data.getModule().isEnabled()) {
            totalEnabledHeight += data.getFont().getHeight(true, arrayListSize.getCurrentValue());
         }
      }
      float currentYOffset = 0;
      int moduleIndex = 0;
      for (ModuleData data : this.renderModuleData) {
         Module module = data.getModule();
         SmoothAnimationTimer animation = module.getAnimation();
         animation.target = module.isEnabled() ? 100.0F : 0.0F;
         animation.update(true);
         if (animation.value > 0.0F) {
            String displayName = data.getDisplayName();
            CustomTextRenderer currentFont = data.getFont();
            double fontHeight = currentFont.getHeight(true, arrayListSize.getCurrentValue());
            float stringWidth = data.getWidth();
            float animatedHeight = (float) ((animation.value / 100.0F) * fontHeight);
            float innerX;
            if(arrayListDirection.isCurrentMode("Left")) {
               innerX = -stringWidth * (1.0F - animation.value / 100.0F);
            } else {
               innerX = maxWidth - stringWidth * (animation.value / 100.0F);
            }
            float currentY = arrayListY + currentYOffset;
            float pos = 0;
            if (gradientDirection.isCurrentMode("Horizontal")) {
               pos = (float)moduleIndex / renderModuleData.size();
            } else {
               pos = currentY / (arrayListY + totalEnabledHeight);
            }
            int bgColor = backgroundColor;
            if (gradient.getCurrentValue()) {
               Color gradColor = new Color(getFlowingGradientColor(pos), true);
               Color bgBase = new Color(backgroundColor, true);
               bgColor = blendColors(new Color(bgBase.getRed(), bgBase.getGreen(), bgBase.getBlue(), 100), new Color(gradColor.getRed(), gradColor.getGreen(), gradColor.getBlue(), 80), 0.3f);
            }
            RenderUtils.fillBound(e.getStack(), arrayListX + innerX, currentY + 2.0F, stringWidth + 3.0F, animatedHeight, bgColor);
            this.blurMatrices.add(new Vector4f(arrayListX + innerX, currentY + 2.0F, stringWidth + 3.0F, animatedHeight));
            int textColor = -1;
            if (this.rainbow.getCurrentValue() && !this.gradient.getCurrentValue()) {
               textColor = RenderUtils.getRainbowOpaque((int)(-currentY * this.rainbowOffset.getCurrentValue()), 1.0F, 1.0F, (21.0F - this.rainbowSpeed.getCurrentValue()) * 1000.0F);
            } else if (this.gradient.getCurrentValue()) {
               textColor = getFlowingGradientColor(pos);
            }
            currentFont.setAlpha(animation.value / 100.0F);
            currentFont.render(e.getStack(), displayName, (arrayListX + innerX + 1.5F), (currentY + 1.0F), new Color(textColor), true, arrayListSize.getCurrentValue());
            currentYOffset += animatedHeight;
            moduleIndex++;
         }
      }
      arraylistElement.width = maxWidth + 6.0f;
      arraylistElement.height = currentYOffset;
      Fonts.opensans.setAlpha(1.0F);
      Fonts.harmony.setAlpha(1.0F);
      e.getStack().popPose();
   }

   private void renderZenArrayList(EventRender2D e) {
      e.getStack().pushPose();
      prepareRenderData();
      if (this.renderModuleData == null || this.renderModuleData.isEmpty()) {
         e.getStack().popPose();
         return;
      }
      HUDEditor.HUDElement arraylistElement = HUDEditor.getInstance().getHUDElement("arraylist");
      if (arraylistElement == null) {
         e.getStack().popPose();
         return;
      }

      float arrayListX = (float) arraylistElement.x;
      float arrayListY = (float) arraylistElement.y;
      float baseScale = this.arrayListSize.getCurrentValue();
      float textScale = baseScale * 0.85f;
      float padding = 2.8f;
      float gap = 4.0f;

      float totalEnabledHeight = 0;
      float maxWidth = 0;

      for (ModuleData data : renderModuleData) {
         if (data.getModule().isEnabled()) {
            totalEnabledHeight += data.getFont().getHeight(true, textScale) + (padding * 2) + 2.0f;
         }
      }

      float currentYOffset = 0;
      int moduleIndex = 0;

      for (ModuleData data : this.renderModuleData) {
         Module module = data.getModule();
         SmoothAnimationTimer animation = module.getAnimation();
         animation.target = module.isEnabled() ? 100.0F : 0.0F;
         animation.update(true);

         if (animation.value > 0.0F) {
            String displayName = data.getDisplayName();
            CustomTextRenderer textFont = data.getFont();
            CustomTextRenderer iconFont = Fonts.icons;
            String iconText = module.getCategory().getIcon();
            float nameWidth = textFont.getWidth(displayName, textScale);
            float fontHeight = (float) textFont.getHeight(true, textScale);
            float contentHeight = fontHeight + (padding * 2);
            float moduleTotalHeight = contentHeight + 2.0f;
            float animatedHeight = (float) ((animation.value / 100.0F) * contentHeight);
            float animatedTotalHeight = (float) ((animation.value / 100.0F) * moduleTotalHeight);
            float iconSize = contentHeight;
            float textBoxWidth = nameWidth + (padding * 3);
            float rowTotalWidth = iconSize + gap + textBoxWidth;
            if (rowTotalWidth > maxWidth) maxWidth = rowTotalWidth;
            float iconBoxX;
            float textRectX;
            float currentY = arrayListY + currentYOffset;
            float slideOffset = (rowTotalWidth + 20) * (1.0F - animation.value / 100.0F);

            if(arrayListDirection.isCurrentMode("Left")) {
               iconBoxX = arrayListX - slideOffset;
               textRectX = iconBoxX + iconSize + gap;
            } else {
               float alignRightX = arrayListX + maxWidth;
               iconBoxX = alignRightX - iconSize + slideOffset;
               textRectX = iconBoxX - gap - textBoxWidth;
            }
            float pos = 0;
            if (gradientDirection.isCurrentMode("Horizontal")) {
               pos = (float)moduleIndex / renderModuleData.size();
            } else {
               pos = currentY / (arrayListY + totalEnabledHeight);
            }

            int bgColor = backgroundColor;
            if (gradient.getCurrentValue()) {
               Color gradColor = new Color(getFlowingGradientColor(pos), true);
               Color bgBase = new Color(backgroundColor, true);
               bgColor = blendColors(new Color(bgBase.getRed(), bgBase.getGreen(), bgBase.getBlue(), 100), new Color(gradColor.getRed(), gradColor.getGreen(), gradColor.getBlue(), 80), 0.3f);
            }

            int textColor = -1;
            if (this.rainbow.getCurrentValue() && !this.gradient.getCurrentValue()) {
               textColor = RenderUtils.getRainbowOpaque((int)(-currentY * this.rainbowOffset.getCurrentValue()), 1.0F, 1.0F, (21.0F - this.rainbowSpeed.getCurrentValue()) * 1000.0F);
            } else if (this.gradient.getCurrentValue()) {
               textColor = getFlowingGradientColor(pos);
            }
            RenderUtils.drawRoundedRect(e.getStack(), iconBoxX, currentY, iconSize, animatedHeight, 3.0f, bgColor);
            this.blurMatrices.add(new Vector4f(iconBoxX, currentY, iconSize, animatedHeight));
            RenderUtils.drawRoundedRect(e.getStack(), textRectX, currentY, textBoxWidth, animatedHeight, 3.0f, bgColor);
            this.blurMatrices.add(new Vector4f(textRectX, currentY, textBoxWidth, animatedHeight));
            iconFont.setAlpha(animation.value / 100.0F);
            textFont.setAlpha(animation.value / 100.0F);
            if (animatedHeight > fontHeight) {
               float iconWidth = iconFont.getWidth(iconText, textScale);
               float iconCenterY = currentY + (contentHeight - (float)iconFont.getHeight(true, textScale)) / 2.0f + 1.0f;
               float iconCenterX = iconBoxX + (iconSize - iconWidth) / 2.0f;
               iconFont.render(e.getStack(), iconText, iconCenterX, iconCenterY, new Color(textColor), true, textScale);
               float textCenterY = currentY + (contentHeight - fontHeight) / 2.0f + 1.0f;
               float nameTextX = textRectX + (textBoxWidth - nameWidth) / 2.0f;

               textFont.render(e.getStack(), displayName, nameTextX, textCenterY, new Color(textColor), true, textScale);
            }

            currentYOffset += animatedTotalHeight;
            moduleIndex++;
         }
      }

      arraylistElement.width = maxWidth + 10.0f;
      arraylistElement.height = currentYOffset;

      Fonts.opensans.setAlpha(1.0F);
      Fonts.harmony.setAlpha(1.0F);
      Fonts.icons.setAlpha(1.0F);
      e.getStack().popPose();
   }

   public static class ModuleToggleNotification {
      private final String moduleName;
      private final boolean enabled;
      private final long createTime;
      private final SmoothAnimationTimer scaleTimer;
      private final SmoothAnimationTimer alphaTimer;

      public ModuleToggleNotification(String moduleName, boolean enabled) {
         this.moduleName = moduleName;
         this.enabled = enabled;
         this.createTime = System.currentTimeMillis();
         this.scaleTimer = new SmoothAnimationTimer(0.0F);
         this.alphaTimer = new SmoothAnimationTimer(0.0F);
      }

      public void update() {
         scaleTimer.update(true);
         alphaTimer.update(true);
      }

      public boolean isExpired() {
         return System.currentTimeMillis() - createTime > 3000;
      }

      public float getScale() {
         return scaleTimer.value / 100.0F;
      }

      public float getAlpha() {
         return alphaTimer.value / 100.0F;
      }

      public String getModuleName() {
         return moduleName;
      }

      public boolean isEnabled() {
         return enabled;
      }

      public void show() {
         scaleTimer.target = 100.0F;
         alphaTimer.target = 100.0F;
      }

      public void hide() {
         scaleTimer.target = 0.0F;
         alphaTimer.target = 0.0F;
      }
   }

   private static class ModuleData {
      private final Module module;
      private final String displayName;
      private final float width;
      private final CustomTextRenderer font;

      public ModuleData(Module module, String displayName, float width, CustomTextRenderer font) {
         this.module = module;
         this.displayName = displayName;
         this.width = width;
         this.font = font;
      }
      public Module getModule() { return module; }
      public String getDisplayName() { return displayName; }
      public float getWidth() { return width; }
      public CustomTextRenderer getFont() { return font; }
   }
}