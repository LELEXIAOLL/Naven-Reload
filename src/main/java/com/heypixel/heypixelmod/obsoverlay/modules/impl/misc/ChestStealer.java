package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Scaffold;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.InventoryUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.TickTimeHelper;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

@ModuleInfo(
        name = "ChestStealer",
        description = "Automatically steals items from chests",
        category = Category.MISC
)
public class ChestStealer extends Module {
   private static final TickTimeHelper timer = new TickTimeHelper();
   public final FloatValue csdelay = ValueBuilder.create(this, "Delay (Ticks)")
           .setDefaultFloatValue(3.0F)
           .setFloatStep(1.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(10.0F)
           .build()
           .getFloatValue();
   private final BooleanValue pickEnderChest = ValueBuilder.create(this, "Ender Chest").setDefaultBooleanValue(false).build().getBooleanValue();
   private final BooleanValue hideGUI = ValueBuilder.create(this, "Silent").setDefaultBooleanValue(true).build().getBooleanValue();
   private Screen lastTickScreen;

   // 新增渲染选项
   public BooleanValue CSRender = ValueBuilder.create(this, "Render")
           .setDefaultBooleanValue(false)
           .setVisibility(this.hideGUI::getCurrentValue)
           .build()
           .getBooleanValue();
   public ModeValue CSRenderMode = ValueBuilder.create(this, "RenderMode")
           .setVisibility(this.CSRender::getCurrentValue)
           .setDefaultModeIndex(0)
           .setModes("None", "Normal","SilenceFix","LingDong")
           .build()
           .getModeValue();

   // 在 ChestStealer 类中添加这些成员变量
   private long stealStartTime = 0; // 偷取开始时间
   private float innerRingAngle = 0; // 内圈角度
   private float middleRingAngle = 0; // 中圈角度
   private float outerRingAngle = 0; // 外圈角度

   // 在 ChestStealer 类中添加以下成员变量
   private boolean isWorking = false; // 跟踪当前是否正在工作
   private String currentItemID = ""; // 当前正在拿取的物品ID

   // 进度条参数
   private static final float PROGRESS_BAR_WIDTH = 150.0f;
   private static final float PROGRESS_BAR_HEIGHT = 8.0f;
   private static final float CORNER_RADIUS = 2.0f;
   private static final float PROGRESS_BAR_Y_OFFSET = 20.0f;
   private static final int BACKGROUND_COLOR = 0x80000000; // 半透明黑色背景
   private static final int PROGRESS_COLOR = 0xFFFFFFFF;   // 白色进度条

   // 进度跟踪变量
   public int totalItemsToSteal = 0;
   public int stolenItems = 0;
   private ChestMenu currentChestMenu = null;

   public static boolean isWorking() {
      return !timer.delay(3);
   }

   // 添加两个公有方法
   public boolean isCSWorking() {
      return this.isWorking;
   }

   public String getCSItemID() {
      return this.currentItemID;
   }


    //返回当前箱子内的物品列表。
   public List<ItemStack> getChestInventory() {
      if (this.currentChestMenu == null) {
         return Collections.emptyList();
      }
      return this.currentChestMenu.slots.stream()
              .filter(slot -> slot.container == this.currentChestMenu.getContainer())
              .map(slot -> slot.getItem())
              .collect(Collectors.toList());
   }

   /**
    * 返回当前箱子的物品槽位数量。
    *
    * @return 物品槽位数量。如果箱子未打开，则返回0。
    */
   public int getChestInventorySize() {
      if (this.currentChestMenu == null) {
         return 0;
      }
      return this.currentChestMenu.getContainer().getContainerSize();
   }


   @Override
   public void onEnable() {
      super.onEnable();
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
      resetProgressTracking();
   }

   // 重置进度跟踪
   private void resetProgressTracking() {
      totalItemsToSteal = 0;
      stolenItems = 0;
      currentChestMenu = null;
      stealStartTime = 0;
      this.isWorking = false;
      this.currentItemID = "";
   }

   private void onStealingStopped() {
      this.isWorking = false;
   }

   @SubscribeEvent
   public void onRenderGui(ScreenEvent.Render.Pre event) {
      if (!this.isEnabled() || !hideGUI.getCurrentValue()) return;

      Screen screen = event.getScreen();
      if (screen instanceof ContainerScreen container) {
         String chestTitle = container.getTitle().getString();
         String chest = Component.translatable("container.chest").getString();
         String largeChest = Component.translatable("container.chestDouble").getString();
         String enderChest = Component.translatable("container.enderchest").getString();

         if (chestTitle.equals(chest)
                 || chestTitle.equals(largeChest)
                 || chestTitle.equals("Chest")
                 || (this.pickEnderChest.getCurrentValue() && chestTitle.equals(enderChest))) {
            event.setCanceled(true); // 取消GUI渲染
         }
      }
   }

   // 在 onMotion 方法中更新工作状态
   @EventTarget(1)
   public void onMotion(EventMotion e) {
      if (e.getType() == EventType.PRE) {
         boolean wasWorking = this.isWorking; // 记录之前的工作状态
         this.isWorking = false; // 默认设为不工作

         Screen currentScreen = mc.screen;
         if (currentScreen instanceof ContainerScreen container) {
            ChestMenu menu = (ChestMenu)container.getMenu();

            // 如果是新打开的箱子，重置进度跟踪
            if (currentScreen != this.lastTickScreen) {
               resetProgressTracking();
               timer.reset();

               // 计算需要偷取的物品总数
               totalItemsToSteal = countUsefulItems(menu);
               currentChestMenu = menu;
               stolenItems = 0;

               // 设置偷取开始时间
               stealStartTime = System.currentTimeMillis();
               // 初始化随机角度
               innerRingAngle = (float)(Math.random() * 360);
               middleRingAngle = (float)(Math.random() * 360);
               outerRingAngle = (float)(Math.random() * 360);
            } else {
               String chestTitle = container.getTitle().getString();
               String chest = Component.translatable("container.chest").getString();
               String largeChest = Component.translatable("container.chestDouble").getString();
               String enderChest = Component.translatable("container.enderchest").getString();
               if (chestTitle.equals(chest)
                       || chestTitle.equals(largeChest)
                       || chestTitle.equals("Chest")
                       || (this.pickEnderChest.getCurrentValue() && chestTitle.equals(enderChest))) {

                  // 标记为正在工作
                  this.isWorking = true;

                  if (this.isChestEmpty(menu) && timer.delay(this.csdelay.getCurrentValue())) {
                     mc.player.closeContainer();
                     resetProgressTracking();
                  } else {
                     List<Integer> slots = IntStream.range(0, menu.getRowCount() * 9).boxed().collect(Collectors.toList());
                     Collections.shuffle(slots);

                     for (Integer pSlotId : slots) {
                        ItemStack stack = menu.getSlot(pSlotId).getItem();
                        if (isItemUseful(stack) && this.isBestItemInChest(menu, stack) && timer.delay(this.csdelay.getCurrentValue())) {
                           // 记录当前正在拿取的物品ID
                           this.currentItemID = stack.getItem().toString();

                           mc.gameMode.handleInventoryMouseClick(menu.containerId, pSlotId, 0, ClickType.QUICK_MOVE, mc.player);
                           timer.reset();
                           stolenItems++; // 增加已偷取物品计数
                           break;
                        }
                     }
                  }
               }
            }
         } else {
            // 箱子关闭时重置进度跟踪
            resetProgressTracking();
            stealStartTime = 0; // 重置偷取开始时间
         }

         // 如果之前在工作但现在不工作了，通知状态变化
         if (wasWorking && !this.isWorking) {
            onStealingStopped();
         }

         this.lastTickScreen = currentScreen;
      }
   }

   // 计算箱子中有用的物品数量
   private int countUsefulItems(ChestMenu menu) {
      int count = 0;
      for (int i = 0; i < menu.getRowCount() * 9; i++) {
         ItemStack stack = menu.getSlot(i).getItem();
         if (isItemUseful(stack) && this.isBestItemInChest(menu, stack)) {
            count++;
         }
      }
      return count;
   }

   @EventTarget
   public void onRender2D(EventRender2D event) {
      if (!this.isEnabled() || !CSRender.getCurrentValue()) {
         return;
      }

      if (CSRenderMode.isCurrentMode("Normal")) {
         renderNormalMode(event);
      }
      else if (CSRenderMode.isCurrentMode("SilenceFix")) {
         renderSilenceFixMode(event);
      }
   }


   private void renderNormalMode(EventRender2D event) {
      // 没有箱子打开或没有物品可偷取时不显示
      if (currentChestMenu == null || totalItemsToSteal <= 0) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;

      GuiGraphics guiGraphics = event.getGuiGraphics();
      int screenWidth = mc.getWindow().getGuiScaledWidth();
      int screenHeight = mc.getWindow().getGuiScaledHeight();

      float x = (screenWidth - PROGRESS_BAR_WIDTH) / 2.0f;
      float y = screenHeight / 2.0f + PROGRESS_BAR_Y_OFFSET;

      PoseStack poseStack = guiGraphics.pose();
      poseStack.pushPose();

      // 计算进度 (0.0 - 1.0)
      float progress = Math.min(1.0f, (float) stolenItems / (float) totalItemsToSteal);
      float progressWidth = PROGRESS_BAR_WIDTH * progress;

      // 绘制背景
      RenderUtils.drawRoundedRect(poseStack, x, y, PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT, CORNER_RADIUS, BACKGROUND_COLOR);

      // 绘制进度条
      if (progressWidth > 0) {
         RenderUtils.drawRoundedRect(poseStack, x, y, progressWidth, PROGRESS_BAR_HEIGHT, CORNER_RADIUS, PROGRESS_COLOR);
      }

      // 绘制文本
      String trackingText = "Stealing: " + stolenItems + "/" + totalItemsToSteal;
      float textScale = 0.35f;
      float textWidth = Fonts.harmony.getWidth(trackingText, textScale);
      float textX = (screenWidth - textWidth) / 2.0f;
      float textY = y - 12f; // 进度条上方

      Fonts.harmony.render(
              poseStack,
              trackingText,
              (double) textX,
              (double) textY,
              Color.WHITE,
              false,
              textScale
      );

      poseStack.popPose();
   }

   // 新增 SilenceFix 模式渲染方法
   private void renderSilenceFixMode(EventRender2D event) {
      // 没有开始偷取时不显示
      if (stealStartTime == 0) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;

      GuiGraphics guiGraphics = event.getGuiGraphics();
      int screenWidth = mc.getWindow().getGuiScaledWidth();
      int screenHeight = mc.getWindow().getGuiScaledHeight();
      float centerX = screenWidth / 2.0f;
      float centerY = screenHeight / 2.0f;

      PoseStack poseStack = guiGraphics.pose();
      poseStack.pushPose();

      // 计算经过的时间（秒）
      long currentTime = System.currentTimeMillis();
      float elapsedSeconds = (currentTime - stealStartTime) / 1000.0f;

      // 更新旋转角度（内圈1秒/圈，中圈1.5秒/圈，外圈2秒/圈）
      innerRingAngle = (innerRingAngle + 360 * elapsedSeconds) % 360;
      middleRingAngle = (middleRingAngle + 240 * elapsedSeconds) % 360; // 360/1.5=240
      outerRingAngle = (outerRingAngle + 180 * elapsedSeconds) % 360;   // 360/2=180

      // 重置开始时间
      stealStartTime = currentTime;

      // 绘制三个半圆环（180度弧）
      drawSemiRing(poseStack, centerX, centerY, 5.0f, 1.0f, innerRingAngle, 0xFFFFFFFF); // 内圈，半径20
      drawSemiRing(poseStack, centerX, centerY, 8.0f, 1.0f, middleRingAngle, 0xFFFFFFFF); // 中圈，半径30
      drawSemiRing(poseStack, centerX, centerY, 11.0f, 1.0f, outerRingAngle, 0xFFFFFFFF); // 外圈，半径40

      // 绘制文本
      String text = "Stealing...";
      float textScale = 0.3f; // 比 Normal 模式小一点
      float textWidth = Fonts.harmony.getWidth(text, textScale);
      float textX = (screenWidth - textWidth) / 2.0f;
      float textY = centerY + 15; // 在外圈下方

      Fonts.harmony.render(
              poseStack,
              text,
              (double) textX,
              (double) textY,
              Color.WHITE,
              false,
              textScale
      );

      poseStack.popPose();
   }

   // 新增方法：绘制半圆环
   private void drawSemiRing(PoseStack poseStack, float centerX, float centerY,
                             float radius, float thickness, float startAngle, int color) {
      Matrix4f matrix = poseStack.last().pose();
      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();

      // 设置渲染状态
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);

      float a = (float)(color >> 24 & 0xFF) / 255.0F;
      float r = (float)(color >> 16 & 0xFF) / 255.0F;
      float g = (float)(color >> 8 & 0xFF) / 255.0F;
      float b = (float)(color & 0xFF) / 255.0F;

      // 半圆环的弧度范围（180度）
      int segments = 60; // 60段足够平滑
      float sweepAngle = 180.0f; // 180度半圆
      float angleStep = sweepAngle / segments;

      // 使用TRIANGLE_STRIP绘制
      buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

      for (int i = 0; i <= segments; i++) {
         float angle = startAngle + i * angleStep;
         float rad = (float) Math.toRadians(angle);

         // 外点
         float x1 = centerX + (float) Math.cos(rad) * radius;
         float y1 = centerY + (float) Math.sin(rad) * radius;
         buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();

         // 内点
         float x2 = centerX + (float) Math.cos(rad) * (radius - thickness);
         float y2 = centerY + (float) Math.sin(rad) * (radius - thickness);
         buffer.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
      }

      tessellator.end();
      RenderSystem.disableBlend();
   }

   private boolean isBestItemInChest(ChestMenu menu, ItemStack stack) {
      if (!InventoryUtils.isGodItem(stack) && !InventoryUtils.isSharpnessAxe(stack)) {
         for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack checkStack = menu.getSlot(i).getItem();
            if (stack.getItem() instanceof ArmorItem && checkStack.getItem() instanceof ArmorItem) {
               ArmorItem item = (ArmorItem)stack.getItem();
               ArmorItem checkItem = (ArmorItem)checkStack.getItem();
               if (item.getEquipmentSlot() == checkItem.getEquipmentSlot() && InventoryUtils.getProtection(checkStack) > InventoryUtils.getProtection(stack)) {
                  return false;
               }
            } else if (stack.getItem() instanceof SwordItem && checkStack.getItem() instanceof SwordItem) {
               if (InventoryUtils.getSwordDamage(checkStack) > InventoryUtils.getSwordDamage(stack)) {
                  return false;
               }
            } else if (stack.getItem() instanceof PickaxeItem && checkStack.getItem() instanceof PickaxeItem) {
               if (InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
                  return false;
               }
            } else if (stack.getItem() instanceof AxeItem && checkStack.getItem() instanceof AxeItem) {
               if (InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
                  return false;
               }
            } else if (stack.getItem() instanceof ShovelItem
                    && checkStack.getItem() instanceof ShovelItem
                    && InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
               return false;
            }
         }

         return true;
      } else {
         return true;
      }
   }

   private boolean isChestEmpty(ChestMenu menu) {
      for (int i = 0; i < menu.getRowCount() * 9; i++) {
         ItemStack item = menu.getSlot(i).getItem();
         if (!item.isEmpty() && isItemUseful(item) && this.isBestItemInChest(menu, item)) {
            return false;
         }
      }

      return true;
   }

   public static boolean isItemUseful(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else if (InventoryUtils.isGodItem(stack) || InventoryUtils.isSharpnessAxe(stack)) {
         return true;
      } else if (stack.getItem() == Items.COBWEB) {
         return InventoryCleaner.shouldKeepCobweb() && InventoryUtils.getItemCount(Items.COBWEB) < 64;
      } else if (stack.getItem() instanceof ArmorItem) {
         ArmorItem item = (ArmorItem)stack.getItem();
         float protection = InventoryUtils.getProtection(stack);
         float bestArmor = InventoryUtils.getBestArmorScore(item.getEquipmentSlot());
         return !(protection <= bestArmor);
      } else if (stack.getItem() instanceof SwordItem) {
         float damage = InventoryUtils.getSwordDamage(stack);
         float bestDamage = InventoryUtils.getBestSwordDamage();
         return !(damage <= bestDamage);
      } else if (stack.getItem() instanceof PickaxeItem) {
         float score = InventoryUtils.getToolScore(stack);
         float bestScore = InventoryUtils.getBestPickaxeScore();
         return !(score <= bestScore);
      } else if (stack.getItem() instanceof AxeItem) {
         float score = InventoryUtils.getToolScore(stack);
         float bestScore = InventoryUtils.getBestAxeScore();
         return !(score <= bestScore);
      } else if (stack.getItem() instanceof ShovelItem) {
         float score = InventoryUtils.getToolScore(stack);
         float bestScore = InventoryUtils.getBestShovelScore();
         return !(score <= bestScore);
      } else if (stack.getItem() instanceof CrossbowItem) {
         float score = InventoryUtils.getCrossbowScore(stack);
         float bestScore = InventoryUtils.getBestCrossbowScore();
         return !(score <= bestScore);
      } else if (stack.getItem() instanceof BowItem && InventoryUtils.isPunchBow(stack)) {
         float score = InventoryUtils.getPunchBowScore(stack);
         float bestScore = InventoryUtils.getBestPunchBowScore();
         return !(score <= bestScore);
      } else if (stack.getItem() instanceof BowItem && InventoryUtils.isPowerBow(stack)) {
         float score = InventoryUtils.getPowerBowScore(stack);
         float bestScore = InventoryUtils.getBestPowerBowScore();
         return !(score <= bestScore);
      } else if (stack.getItem() == Items.COMPASS) {
         return !InventoryUtils.hasItem(stack.getItem());
      } else if (stack.getItem() == Items.WATER_BUCKET && InventoryUtils.getItemCount(Items.WATER_BUCKET) >= InventoryCleaner.getWaterBucketCount()) {
         return false;
      } else if (stack.getItem() == Items.LAVA_BUCKET && InventoryUtils.getItemCount(Items.LAVA_BUCKET) >= InventoryCleaner.getLavaBucketCount()) {
         return false;
      } else if (stack.getItem() instanceof BlockItem
              && Scaffold.isValidStack(stack)
              && InventoryUtils.getBlockCountInInventory() + stack.getCount() >= InventoryCleaner.getMaxBlockSize()) {
         return false;
      } else if (stack.getItem() == Items.ARROW && InventoryUtils.getItemCount(Items.ARROW) + stack.getCount() >= InventoryCleaner.getMaxArrowSize()) {
         return false;
      } else if (stack.getItem() instanceof FishingRodItem && !InventoryCleaner.shouldKeepFishingRod() && InventoryUtils.getItemCount(Items.FISHING_ROD) >= 1) {
            return false;
      } else if (stack.getItem() != Items.SNOWBALL && stack.getItem() != Items.EGG
              || InventoryUtils.getItemCount(Items.SNOWBALL) + InventoryUtils.getItemCount(Items.EGG) + stack.getCount() < InventoryCleaner.getMaxProjectileSize()
              && InventoryCleaner.shouldKeepProjectile()) {
         return stack.getItem() instanceof ItemNameBlockItem ? false : InventoryUtils.isCommonItemUseful(stack);
      } else {
         return false;
      }
   }
}