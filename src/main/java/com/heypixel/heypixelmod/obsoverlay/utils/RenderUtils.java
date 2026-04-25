package com.heypixel.heypixelmod.obsoverlay.utils;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import java.nio.ByteBuffer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class RenderUtils {
   private static final Minecraft mc = Minecraft.getInstance();
   private static final AABB DEFAULT_BOX = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

   public static int reAlpha(int color, float alpha) {
      int col = MathUtils.clamp((int)(alpha * 255.0F), 0, 255) << 24;
      col |= MathUtils.clamp(color >> 16 & 0xFF, 0, 255) << 16;
      col |= MathUtils.clamp(color >> 8 & 0xFF, 0, 255) << 8;
      return col | MathUtils.clamp(color & 0xFF, 0, 255);
   }

   public static void drawRotationalRing(PoseStack poseStack, float centerX, float centerY, float radius, float thickness, float arcAngle, float startAngle, int color) {
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);

      float outerRadius = radius;
      float innerRadius = radius - thickness;

      float r = (float)(color >> 16 & 255) / 255.0F;
      float g = (float)(color >> 8 & 255) / 255.0F;
      float b = (float)(color & 255) / 255.0F;
      float a = (float)(color >> 24 & 255) / 255.0F;

      BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
      bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

      // 将圆弧分为100段来绘制，以保证平滑
      int segments = 100;
      for (int i = 0; i <= segments; i++) {
         double angle = Math.toRadians(startAngle + (i / (double)segments) * arcAngle);
         float cos = (float) Math.cos(angle);
         float sin = (float) Math.sin(angle);

         bufferBuilder.vertex(centerX + cos * outerRadius, centerY + sin * outerRadius, 0).color(r, g, b, a).endVertex();
         bufferBuilder.vertex(centerX + cos * innerRadius, centerY + sin * innerRadius, 0).color(r, g, b, a).endVertex();
      }

      Tesselator.getInstance().end();
      RenderSystem.disableBlend();
   }

   public static void renderItemById(GuiGraphics graphics, String itemId, int x, int y, int size) {
      // 通过物品ID获取物品实例
      Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));

      // 如果物品不存在（返回空气），则不渲染
      if (item == net.minecraft.world.item.Items.AIR) {
         return;
      }

      // 创建一个 ItemStack 实例
      ItemStack stack = new ItemStack(item);

      // 使用 GuiGraphics 提供的渲染方法，并手动处理缩放
      PoseStack poseStack = graphics.pose();
      poseStack.pushPose();
      poseStack.translate(x, y, 0);
      // 缩放以达到指定的大小。物品默认大小为16x16
      poseStack.scale((float) size / 16.0F, (float) size / 16.0F, 1.0F);

      // 渲染物品和装饰（如耐久度条等）
      graphics.renderItem(stack, 0, 0);

      poseStack.popPose();
   }

   public static void drawTracer(PoseStack poseStack, float x, float y, float size, float widthDiv, float heightDiv, int color) {
      GL11.glEnable(3042);
      GL11.glBlendFunc(770, 771);
      GL11.glDisable(2929);
      GL11.glDepthMask(false);
      GL11.glEnable(2848);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Matrix4f matrix = poseStack.last().pose();
      float a = (float)(color >> 24 & 0xFF) / 255.0F;
      float r = (float)(color >> 16 & 0xFF) / 255.0F;
      float g = (float)(color >> 8 & 0xFF) / 255.0F;
      float b = (float)(color & 0xFF) / 255.0F;
      BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
      bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      bufferBuilder.vertex(matrix, x, y, 0.0F).color(r, g, b, a).endVertex();
      bufferBuilder.vertex(matrix, x - size / widthDiv, y + size, 0.0F).color(r, g, b, a).endVertex();
      bufferBuilder.vertex(matrix, x, y + size / heightDiv, 0.0F).color(r, g, b, a).endVertex();
      bufferBuilder.vertex(matrix, x + size / widthDiv, y + size, 0.0F).color(r, g, b, a).endVertex();
      bufferBuilder.vertex(matrix, x, y, 0.0F).color(r, g, b, a).endVertex();
      Tesselator.getInstance().end();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      GL11.glDisable(3042);
      GL11.glEnable(2929);
      GL11.glDepthMask(true);
      GL11.glDisable(2848);
   }

   public static void drawThickRectBorder(PoseStack poseStack, float x, float y, float width, float height, float thickness, int color) {
      // 上边框
      fill(poseStack, x, y, x + width, y + thickness, color);
      // 下边框
      fill(poseStack, x, y + height - thickness, x + width, y + height, color);
      // 左边框
      fill(poseStack, x, y + thickness, x + thickness, y + height - thickness, color);
      // 右边框
      fill(poseStack, x + width - thickness, y + thickness, x + width, y + height - thickness, color);
   }

   public static int getRainbowOpaque(int index, float saturation, float brightness, float speed) {
      float hue = (float)((System.currentTimeMillis() + (long)index) % (long)((int)speed)) / speed;
      return Color.HSBtoRGB(hue, saturation, brightness);
   }

   public static BlockPos getCameraBlockPos() {
      Camera camera = mc.getBlockEntityRenderDispatcher().camera;
      return camera.getBlockPosition();
   }

   public static void drawHealthRing(PoseStack poseStack, float centerX, float centerY,
                                     float radius, float thickness, float progress) {
      if (progress <= 0) return;

      Matrix4f matrix = poseStack.last().pose();
      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();

      // 设置渲染状态
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);

      // 纯白色不透明
      float r = 1.0f; // 红
      float g = 1.0f; // 绿
      float b = 1.0f; // 蓝
      float a = 1.0f; // 透明度

      // 计算圆弧长度
      float sweepAngle = progress * 360.0f;

      // 计算顶点数量
      int segments = (int) (Math.min(360, Math.max(36, sweepAngle)));
      float angleStep = sweepAngle / segments;

      // 起始角度（从顶部开始）
      float startAngle = -90.0f;

      // 使用POSITION_COLOR格式
      buffer.begin(Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

      for (int i = 0; i <= segments; i++) {
         float angle = (float) Math.toRadians(startAngle + i * angleStep);

         // 外圈点（带颜色）
         float outerX = centerX + (float)Math.cos(angle) * radius;
         float outerY = centerY + (float)Math.sin(angle) * radius;
         buffer.vertex(matrix, outerX, outerY, 0)
                 .color(r, g, b, a)
                 .endVertex();

         // 内圈点（带颜色）
         float innerX = centerX + (float)Math.cos(angle) * (radius - thickness);
         float innerY = centerY + (float)Math.sin(angle) * (radius - thickness);
         buffer.vertex(matrix, innerX, innerY, 0)
                 .color(r, g, b, a)
                 .endVertex();
      }

      tessellator.end();
      RenderSystem.disableBlend();
   }

   public static Vec3 getCameraPos() {
      Camera camera = mc.getBlockEntityRenderDispatcher().camera;
      return camera.getPosition();
   }

   public static RegionPos getCameraRegion() {
      return RegionPos.of(getCameraBlockPos());
   }

   public static void applyRegionalRenderOffset(PoseStack matrixStack) {
      applyRegionalRenderOffset(matrixStack, getCameraRegion());
   }

   public static void applyRegionalRenderOffset(PoseStack matrixStack, RegionPos region) {
      Vec3 offset = region.toVec3().subtract(getCameraPos());
      matrixStack.translate(offset.x, offset.y, offset.z);
   }

   public static void fill(PoseStack pPoseStack, float pMinX, float pMinY, float pMaxX, float pMaxY, int pColor) {
      innerFill(pPoseStack.last().pose(), pMinX, pMinY, pMaxX, pMaxY, pColor);
   }

   private static void innerFill(Matrix4f pMatrix, float pMinX, float pMinY, float pMaxX, float pMaxY, int pColor) {
      if (pMinX < pMaxX) {
         float i = pMinX;
         pMinX = pMaxX;
         pMaxX = i;
      }

      if (pMinY < pMaxY) {
         float j = pMinY;
         pMinY = pMaxY;
         pMaxY = j;
      }

      float f3 = (float)(pColor >> 24 & 0xFF) / 255.0F;
      float f = (float)(pColor >> 16 & 0xFF) / 255.0F;
      float f1 = (float)(pColor >> 8 & 0xFF) / 255.0F;
      float f2 = (float)(pColor & 0xFF) / 255.0F;
      BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      bufferbuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      bufferbuilder.vertex(pMatrix, pMinX, pMaxY, 0.0F).color(f, f1, f2, f3).endVertex();
      bufferbuilder.vertex(pMatrix, pMaxX, pMaxY, 0.0F).color(f, f1, f2, f3).endVertex();
      bufferbuilder.vertex(pMatrix, pMaxX, pMinY, 0.0F).color(f, f1, f2, f3).endVertex();
      bufferbuilder.vertex(pMatrix, pMinX, pMinY, 0.0F).color(f, f1, f2, f3).endVertex();
      Tesselator.getInstance().end();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   public static void drawRectBound(PoseStack poseStack, float x, float y, float width, float height, int color) {
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      Matrix4f matrix = poseStack.last().pose();
      float alpha = (float)(color >> 24 & 0xFF) / 255.0F;
      float red = (float)(color >> 16 & 0xFF) / 255.0F;
      float green = (float)(color >> 8 & 0xFF) / 255.0F;
      float blue = (float)(color & 0xFF) / 255.0F;
      buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      buffer.vertex(matrix, x, y + height, 0.0F).color(red, green, blue, alpha).endVertex();
      buffer.vertex(matrix, x + width, y + height, 0.0F).color(red, green, blue, alpha).endVertex();
      buffer.vertex(matrix, x + width, y, 0.0F).color(red, green, blue, alpha).endVertex();
      buffer.vertex(matrix, x, y, 0.0F).color(red, green, blue, alpha).endVertex();
      tesselator.end();
   }

   public static void drawCircle(PoseStack poseStack, float centerX, float centerY, float radius, int color) {
      drawCircle(poseStack, centerX, centerY, radius, color, 20);
   }

   public static void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();
      Matrix4f matrix = new Matrix4f();

      float a = (float)(color >> 24 & 0xFF) / 255.0F;
      float r = (float)(color >> 16 & 0xFF) / 255.0F;
      float g = (float)(color >> 8 & 0xFF) / 255.0F;
      float b = (float)(color & 0xFF) / 255.0F;

      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);

      buffer.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
      buffer.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
      buffer.vertex(matrix, x3, y3, 0).color(r, g, b, a).endVertex();

      tessellator.end();
      RenderSystem.disableBlend();
   }

   public static void drawCircle(PoseStack poseStack, float centerX, float centerY, float radius, int color, int segments) {
      if (radius <= 0) return;

      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();
      Matrix4f matrix = poseStack.last().pose();

      float a = (float)(color >> 24 & 0xFF) / 255.0F;
      float r = (float)(color >> 16 & 0xFF) / 255.0F;
      float g = (float)(color >> 8 & 0xFF) / 255.0F;
      float b = (float)(color & 0xFF) / 255.0F;

      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);

      buffer.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
      buffer.vertex(matrix, centerX, centerY, 0).color(r, g, b, a).endVertex();

      for (int i = 0; i <= segments; i++) {
         double angle = 2.0 * Math.PI * i / segments;
         float x = centerX + (float)(Math.cos(angle) * radius);
         float y = centerY + (float)(Math.sin(angle) * radius);
         buffer.vertex(matrix, x, y, 0).color(r, g, b, a).endVertex();
      }

      tessellator.end();
      RenderSystem.disableBlend();
   }

   public static void drawStencilRoundedRect(GuiGraphics graphics, float x, float y, float width, float height, float cornerRadius, int blurStrength, int color) {
      RenderSystem.assertOnRenderThread();
      int textureId = -1;

      try {
         // 1. 准备模板缓冲区
         StencilUtils.write(false);
         RenderUtils.drawRoundedRect(graphics.pose(), x, y, width, height, cornerRadius, 0xFFFFFFFF);
         StencilUtils.erase(true);

         // 2. 捕获屏幕区域
         Minecraft mc = Minecraft.getInstance();
         int windowWidth = mc.getWindow().getWidth();
         int windowHeight = mc.getWindow().getHeight();
         int scaledWidth = mc.getWindow().getGuiScaledWidth();
         int scaledHeight = mc.getWindow().getGuiScaledHeight();

         int pixelX = (int) (x * windowWidth / scaledWidth);
         int pixelY = (int) (y * windowHeight / scaledHeight);
         int pixelWidth = (int) (width * windowWidth / scaledWidth);
         int pixelHeight = (int) (height * windowHeight / scaledHeight);

         pixelX = Math.max(0, pixelX);
         pixelY = Math.max(0, pixelY);
         pixelWidth = Math.min(windowWidth - pixelX, pixelWidth);
         pixelHeight = Math.min(windowHeight - pixelY, pixelHeight);

         // 创建一个临时纹理
         textureId = TextureUtil.generateTextureId();
         RenderSystem.bindTexture(textureId);

         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, pixelWidth, pixelHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

         ByteBuffer buffer = ByteBuffer.allocateDirect(pixelWidth * pixelHeight * 4);
         GL11.glReadPixels(pixelX, windowHeight - pixelY - pixelHeight, pixelWidth, pixelHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
         GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, pixelWidth, pixelHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

         // 3. 绘制模糊纹理
         RenderSystem.setShader(GameRenderer::getPositionTexShader);
         RenderSystem.setShaderTexture(0, textureId);

         Matrix4f matrix = graphics.pose().last().pose();
         BufferBuilder builder = Tesselator.getInstance().getBuilder();
         builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         builder.vertex(matrix, x, y + height, 0).uv(0, 1).endVertex();
         builder.vertex(matrix, x + width, y + height, 0).uv(1, 1).endVertex();
         builder.vertex(matrix, x + width, y, 0).uv(1, 0).endVertex();
         builder.vertex(matrix, x, y, 0).uv(0, 0).endVertex();
         Tesselator.getInstance().end();

         // 4. 在模糊纹理之上绘制半透明覆盖层
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         RenderUtils.fillBound(graphics.pose(), x, y, width, height, color);

         StencilUtils.dispose();

      } finally {
         if (textureId != -1) {
            RenderSystem.deleteTexture(textureId);
         }
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   public static void drawFuzzyRoundedRectBackground(GuiGraphics graphics, float x, float y, float width, float height, float radius, int blurStrength, int color) {
      drawStencilRoundedRect(graphics, x, y, width, height, radius, blurStrength, color);
   }

   private static void color(BufferBuilder buffer, Matrix4f matrix, float x, float y, int color) {
      float alpha = (float)(color >> 24 & 0xFF) / 255.0F;
      float red = (float)(color >> 16 & 0xFF) / 255.0F;
      float green = (float)(color >> 8 & 0xFF) / 255.0F;
      float blue = (float)(color & 0xFF) / 255.0F;
      buffer.vertex(matrix, x, y, 0.0F).color(red, green, blue, alpha).endVertex();
   }

   public static void drawRoundedRect(PoseStack poseStack, float x, float y, float width, float height, float edgeRadius, int color) {
      if (color == 16777215) {
         color = ARGB32.color(255, 255, 255, 255);
      }

      if (edgeRadius < 0.0F) {
         edgeRadius = 0.0F;
      }

      if (edgeRadius > width / 2.0F) {
         edgeRadius = width / 2.0F;
      }

      if (edgeRadius > height / 2.0F) {
         edgeRadius = height / 2.0F;
      }

      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.lineWidth(1.0F);
      drawRectBound(poseStack, x + edgeRadius, y + edgeRadius, width - edgeRadius * 2.0F, height - edgeRadius * 2.0F, color);
      drawRectBound(poseStack, x + edgeRadius, y, width - edgeRadius * 2.0F, edgeRadius, color);
      drawRectBound(poseStack, x + edgeRadius, y + height - edgeRadius, width - edgeRadius * 2.0F, edgeRadius, color);
      drawRectBound(poseStack, x, y + edgeRadius, edgeRadius, height - edgeRadius * 2.0F, color);
      drawRectBound(poseStack, x + width - edgeRadius, y + edgeRadius, edgeRadius, height - edgeRadius * 2.0F, color);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      Matrix4f matrix = poseStack.last().pose();
      buffer.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
      float centerX = x + edgeRadius;
      float centerY = y + edgeRadius;
      int vertices = (int)Math.min(Math.max(edgeRadius, 10.0F), 90.0F);
      color(buffer, matrix, centerX, centerY, color);

      for (int i = 0; i <= vertices; i++) {
         double angleRadians = (Math.PI * 2) * (double)(i + 180) / (double)(vertices * 4);
         color(
            buffer,
            matrix,
            (float)((double)centerX + Math.sin(angleRadians) * (double)edgeRadius),
            (float)((double)centerY + Math.cos(angleRadians) * (double)edgeRadius),
            color
         );
      }

      tesselator.end();
      buffer.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
      centerX = x + width - edgeRadius;
      centerY = y + edgeRadius;
      color(buffer, matrix, centerX, centerY, color);

      for (int i = 0; i <= vertices; i++) {
         double angleRadians = (Math.PI * 2) * (double)(i + 90) / (double)(vertices * 4);
         color(
            buffer,
            matrix,
            (float)((double)centerX + Math.sin(angleRadians) * (double)edgeRadius),
            (float)((double)centerY + Math.cos(angleRadians) * (double)edgeRadius),
            color
         );
      }

      tesselator.end();
      buffer.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
      centerX = x + edgeRadius;
      centerY = y + height - edgeRadius;
      color(buffer, matrix, centerX, centerY, color);

      for (int i = 0; i <= vertices; i++) {
         double angleRadians = (Math.PI * 2) * (double)(i + 270) / (double)(vertices * 4);
         color(
            buffer,
            matrix,
            (float)((double)centerX + Math.sin(angleRadians) * (double)edgeRadius),
            (float)((double)centerY + Math.cos(angleRadians) * (double)edgeRadius),
            color
         );
      }

      tesselator.end();
      buffer.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
      centerX = x + width - edgeRadius;
      centerY = y + height - edgeRadius;
      color(buffer, matrix, centerX, centerY, color);

      for (int i = 0; i <= vertices; i++) {
         double angleRadians = (Math.PI * 2) * (double)i / (double)(vertices * 4);
         color(
            buffer,
            matrix,
            (float)((double)centerX + Math.sin(angleRadians) * (double)edgeRadius),
            (float)((double)centerY + Math.cos(angleRadians) * (double)edgeRadius),
            color
         );
      }

      tesselator.end();
      RenderSystem.disableBlend();
   }

   public static void drawSolidBox(PoseStack matrixStack) {
      drawSolidBox(DEFAULT_BOX, matrixStack);
   }

   public static void drawSolidBox(AABB bb, PoseStack matrixStack) {
      Tesselator tessellator = RenderSystem.renderThreadTesselator();
      BufferBuilder bufferBuilder = tessellator.getBuilder();
      Matrix4f matrix = matrixStack.last().pose();
      bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION);
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).endVertex();
      BufferUploader.drawWithShader(bufferBuilder.end());
   }

   public static void drawOutlinedBox(PoseStack matrixStack) {
      drawOutlinedBox(DEFAULT_BOX, matrixStack);
   }

   public static void drawOutlinedBox(AABB bb, PoseStack matrixStack) {
      Matrix4f matrix = matrixStack.last().pose();
      BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
      RenderSystem.setShader(GameRenderer::getPositionShader);
      bufferBuilder.begin(Mode.DEBUG_LINES, DefaultVertexFormat.POSITION);
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).endVertex();
      BufferUploader.drawWithShader(bufferBuilder.end());
   }

   public static void drawSolidBox(AABB bb, VertexBuffer vertexBuffer) {
      BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
      RenderSystem.setShader(GameRenderer::getPositionShader);
      bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION);
      drawSolidBox(bb, bufferBuilder);
      BufferUploader.reset();
      vertexBuffer.bind();
      RenderedBuffer buffer = bufferBuilder.end();
      vertexBuffer.upload(buffer);
      VertexBuffer.unbind();
   }

   public static void drawSolidBox(AABB bb, BufferBuilder bufferBuilder) {
      bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).endVertex();
   }

   public static void drawOutlinedBox(AABB bb, VertexBuffer vertexBuffer) {
      BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
      bufferBuilder.begin(Mode.DEBUG_LINES, DefaultVertexFormat.POSITION);
      drawOutlinedBox(bb, bufferBuilder);
      vertexBuffer.upload(bufferBuilder.end());
   }

   public static void drawOutlinedBox(AABB bb, BufferBuilder bufferBuilder) {
      bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).endVertex();
      bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).endVertex();
   }

   public static boolean isHovering(int mouseX, int mouseY, float xLeft, float yUp, float xRight, float yBottom) {
      return (float)mouseX > xLeft && (float)mouseX < xRight && (float)mouseY > yUp && (float)mouseY < yBottom;
   }

   public static boolean isHoveringBound(int mouseX, int mouseY, float xLeft, float yUp, float width, float height) {
      return (float)mouseX > xLeft && (float)mouseX < xLeft + width && (float)mouseY > yUp && (float)mouseY < yUp + height;
   }

   public static void fillBound(PoseStack stack, float left, float top, float width, float height, int color) {
      float right = left + width;
      float bottom = top + height;
      fill(stack, left, top, right, bottom, color);
   }

   public static void drawSolidBox(AABB bb, PoseStack matrixStack, BufferBuilder bufferBuilder) {
      Matrix4f matrix = matrixStack.last().pose();

      RenderSystem.setShader(GameRenderer::getPositionColorShader);

      // 设置为半透明白色，50%透明度
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

      bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

      // 绘制下平面
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();

      // 绘制上平面
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();

      // 绘制前平面
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();

      // 绘制后平面
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();

      // 绘制左平面
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();

      // 绘制右平面
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();

      BufferUploader.drawWithShader(bufferBuilder.end());
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // 恢复着色器颜色
   }

   public static void RenderLing(GuiGraphics graphics, float x, float y, float width, float height, int color, int blurStrength) {
      // 药丸的半径是其高度的一半，这样才能形成两侧的半圆。
      float radius = height / 2.0F;

      // 使用已有的方法绘制带有阴影的圆角矩形，当半径为高度的一半时，它就成了药丸形。
      // 您可以将 color 参数设置为 0xFF000000（不透明黑色），以满足“黑色”的要求。
      drawFuzzyRoundedRectBackground(graphics, x, y, width, height, radius, blurStrength, color);
   }

   // 在 RenderUtils.java 中添加这个方法
   public static void drawText(GuiGraphics graphics, String text, float x, float y, float scale, int color, boolean wrap) {
      PoseStack poseStack = graphics.pose();
      poseStack.pushPose();
      poseStack.translate(x, y, 0);
      poseStack.scale(scale, scale, 1.0F);

       graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);

       poseStack.popPose();
   }

   // 新增的 drawOutlinedBox 方法，简化绘制逻辑
   public static void drawOutlinedBox(AABB bb, PoseStack matrixStack, BufferBuilder bufferBuilder) {
      Matrix4f matrix = matrixStack.last().pose();

      bufferBuilder.begin(Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR); // 使用POSITION_COLOR以便应用着色器颜色

      // 绘制下平面
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();

      // 绘制上下连接线
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();

      // 绘制上平面
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
      bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();

      BufferUploader.drawWithShader(bufferBuilder.end());
   }

   public static void 装女人(BufferBuilder bufferBuilder, Matrix4f matrix, AABB box) {
      float minX = (float)(box.minX - mc.getEntityRenderDispatcher().camera.getPosition().x());
      float minY = (float)(box.minY - mc.getEntityRenderDispatcher().camera.getPosition().y());
      float minZ = (float)(box.minZ - mc.getEntityRenderDispatcher().camera.getPosition().z());
      float maxX = (float)(box.maxX - mc.getEntityRenderDispatcher().camera.getPosition().x());
      float maxY = (float)(box.maxY - mc.getEntityRenderDispatcher().camera.getPosition().y());
      float maxZ = (float)(box.maxZ - mc.getEntityRenderDispatcher().camera.getPosition().z());
      bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION);
      bufferBuilder.vertex(matrix, minX, minY, minZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, minY, minZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, minY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, minX, minY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, minX, maxY, minZ).endVertex();
      bufferBuilder.vertex(matrix, minX, maxY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, maxY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, maxY, minZ).endVertex();
      bufferBuilder.vertex(matrix, minX, minY, minZ).endVertex();
      bufferBuilder.vertex(matrix, minX, maxY, minZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, maxY, minZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, minY, minZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, minY, minZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, maxY, minZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, maxY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, minY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, minX, minY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, minY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, maxX, maxY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, minX, maxY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, minX, minY, minZ).endVertex();
      bufferBuilder.vertex(matrix, minX, minY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, minX, maxY, maxZ).endVertex();
      bufferBuilder.vertex(matrix, minX, maxY, minZ).endVertex();
      BufferUploader.drawWithShader(bufferBuilder.end());
   }
}
