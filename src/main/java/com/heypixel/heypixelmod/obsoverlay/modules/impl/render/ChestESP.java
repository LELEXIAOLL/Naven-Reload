// 文件路径: com/heypixel/heypixelmod/obsoverlay/modules/impl/render/ChestESP.java
package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRespawn;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.BlockUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.ChunkUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

@ModuleInfo(
        name = "ChestESP",
        description = "Highlights chests",
        category = Category.RENDER
)
public class ChestESP extends Module {
   private static final float[] chestColor = new float[]{0.0F, 1.0F, 0.0F};
   private static final float[] openedChestColor = new float[]{1.0F, 0.0F, 0.0F};

   private final Set<BlockPos> openedChests = ConcurrentHashMap.newKeySet();
   private final List<ChestData> renderList = new CopyOnWriteArrayList<>();
   private static final long UPDATE_INTERVAL_MS = 1000L; // 每1000毫秒 (1秒) 更新一次
   private long lastUpdateTime = 0L;

   @Override
   public void onDisable() {
      this.openedChests.clear();
      this.renderList.clear();
   }

   @EventTarget
   public void onRespawn(EventRespawn e) {
      this.openedChests.clear();
      this.renderList.clear();
   }

   @EventTarget
   public void onPacket(EventPacket e) {
      if (e.getType() == EventType.RECEIVE && e.getPacket() instanceof ClientboundBlockEventPacket) {
         ClientboundBlockEventPacket packet = (ClientboundBlockEventPacket)e.getPacket();
         if ((packet.getBlock() == Blocks.CHEST || packet.getBlock() == Blocks.TRAPPED_CHEST) && packet.getB0() == 1 && packet.getB1() > 0) {
            this.openedChests.add(packet.getPos());
         }
      }
   }

   @EventTarget
   public void onTick(EventMotion e) {
      if (e.getType() != EventType.PRE) {
         return;
      }

      // --- 优化点 1 (续): 使用计时器控制更新频率 ---
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastUpdateTime > UPDATE_INTERVAL_MS) {
         lastUpdateTime = currentTime;
         updateChestList();
      }
   }

   private void updateChestList() {
      List<ChestData> newRenderList = ChunkUtils.getLoadedBlockEntities()
              .filter(be -> be instanceof ChestBlockEntity)
              .map(be -> (ChestBlockEntity) be)
              .map(this::createChestData)
              .filter(java.util.Objects::nonNull)
              .collect(Collectors.toList());

      this.renderList.clear();
      this.renderList.addAll(newRenderList);
   }

   private ChestData createChestData(ChestBlockEntity chestBE) {
      AABB box = this.getChestBox(chestBE);
      if (box != null) {
         boolean isOpened = this.openedChests.contains(chestBE.getBlockPos());
         return new ChestData(box, isOpened);
      }
      return null;
   }

   private AABB getChestBox(ChestBlockEntity chestBE) {
      BlockState state = chestBE.getBlockState();
      if (!state.hasProperty(ChestBlock.TYPE)) {
         return null;
      }

      ChestType chestType = state.getValue(ChestBlock.TYPE);
      if (chestType == ChestType.LEFT) {
         return null;
      }

      BlockPos pos = chestBE.getBlockPos();
      AABB box = BlockUtils.getBoundingBox(pos);
      if (chestType != ChestType.SINGLE) {
         BlockPos pos2 = pos.relative(ChestBlock.getConnectedDirection(state));
         if (mc.level != null && mc.level.getBlockState(pos2).getBlock() instanceof ChestBlock) {
            AABB box2 = BlockUtils.getBoundingBox(pos2);
            box = box.minmax(box2);
         }
      }

      return box;
   }

   @EventTarget
   public void onRender(EventRender e) {
      if (this.renderList.isEmpty()) {
         return;
      }

      PoseStack stack = e.getPMatrixStack();
      stack.pushPose();
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionShader);
      Tesselator tessellator = RenderSystem.renderThreadTesselator();
      BufferBuilder bufferBuilder = tessellator.getBuilder();

      for (ChestData data : this.renderList) {
         float[] color = data.isOpened ? openedChestColor : chestColor;
         RenderSystem.setShaderColor(color[0], color[1], color[2], 0.25F);
         RenderUtils.装女人(bufferBuilder, stack.last().pose(), data.boundingBox);
      }

      RenderSystem.disableBlend();
      RenderSystem.enableDepthTest();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      stack.popPose();
   }
   private static class ChestData {
      final AABB boundingBox;
      final boolean isOpened;

      ChestData(AABB boundingBox, boolean isOpened) {
         this.boundingBox = boundingBox;
         this.isOpened = isOpened;
      }
   }
}