package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ModuleInfo(
        name = "JumpCircle",
        description = "Renders a circle effect when jumping",
        category = Category.RENDER
)
public class JumpCircle extends Module {
    
    private final BooleanValue filled = ValueBuilder.create(this, "Filled").setDefaultBooleanValue(true).build().getBooleanValue();
    private final BooleanValue outline = ValueBuilder.create(this, "Outline").setDefaultBooleanValue(true).build().getBooleanValue();
    private final FloatValue thickness = ValueBuilder.create(this, "Thickness")
            .setDefaultFloatValue(2.0F)
            .setMinFloatValue(0.1F)
            .setMaxFloatValue(5.0F)
            .setFloatStep(0.1F)
            .build()
            .getFloatValue();
    private final FloatValue duration = ValueBuilder.create(this, "Duration")
            .setDefaultFloatValue(1000.0F)
            .setMinFloatValue(100.0F)
            .setMaxFloatValue(5000.0F)
            .setFloatStep(50.0F)
            .build()
            .getFloatValue();
    private final FloatValue radius = ValueBuilder.create(this, "Radius")
            .setDefaultFloatValue(2.0F)
            .setMinFloatValue(0.5F)
            .setMaxFloatValue(10.0F)
            .setFloatStep(0.1F)
            .build()
            .getFloatValue();
    private final FloatValue growSpeed = ValueBuilder.create(this, "Grow Speed")
            .setDefaultFloatValue(1.0F)
            .setMinFloatValue(0.1F)
            .setMaxFloatValue(5.0F)
            .setFloatStep(0.1F)
            .build()
            .getFloatValue();
    
    private final List<Circle> circles = new ArrayList<>();
    private boolean lastOnGround = false;
    private Vec3 lastPos = Vec3.ZERO;
    
    @Override
    public void onEnable() {
        circles.clear();
        if (mc.player != null) {
            lastOnGround = mc.player.onGround();
            lastPos = mc.player.position();
        }
    }
    
    @EventTarget
    public void onMotion(EventMotion event) {
        if (mc.player == null) return;
        
        // Check if player just jumped
        if (!lastOnGround && mc.player.onGround()) {
            // Add new circle at player's position
            circles.add(new Circle(lastPos, System.currentTimeMillis()));
        }
        
        lastOnGround = mc.player.onGround();
        lastPos = mc.player.position();
    }
    
    @EventTarget
    public void onRender(EventRender event) {
        if (mc.player == null || circles.isEmpty()) return;
        
        long currentTime = System.currentTimeMillis();
        Iterator<Circle> iterator = circles.iterator();
        
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        
        while (iterator.hasNext()) {
            Circle circle = iterator.next();
            long timeDiff = currentTime - circle.creationTime;
            
            // Remove old circles
            if (timeDiff > duration.getCurrentValue()) {
                iterator.remove();
                continue;
            }
            
            // Calculate progress (0.0 to 1.0)
            float progress = timeDiff / duration.getCurrentValue();
            
            // Calculate current radius based on grow speed
            float currentRadius = radius.getCurrentValue() * (1.0F + progress * growSpeed.getCurrentValue());
            
            // Calculate alpha (fade out over time)
            float alpha = 1.0F - progress;
            
            // Render the circle
            renderCircle(event.getPMatrixStack(), circle.position, currentRadius, alpha, progress);
        }
        
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
    
    private void renderCircle(PoseStack poseStack, Vec3 pos, float radius, float alpha, float progress) {
        poseStack.pushPose();
        
        // Translate to circle position
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.translate(
                pos.x - cameraPos.x,
                pos.y - cameraPos.y,
                pos.z - cameraPos.z
        );
        
        // Rotate to face player
        poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
        
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        
        Matrix4f matrix = poseStack.last().pose();
        
        // Render filled circle
        if (filled.getCurrentValue()) {
            VertexConsumer filledConsumer = bufferSource.getBuffer(RenderType.gui());
            renderFilledCircle(matrix, filledConsumer, radius, alpha, progress);
        }
        
        // Render outline
        if (outline.getCurrentValue()) {
            VertexConsumer outlineConsumer = bufferSource.getBuffer(RenderType.debugLineStrip(1.0));
            renderOutline(matrix, outlineConsumer, radius, alpha, progress);
        }
        
        bufferSource.endBatch();
        poseStack.popPose();
    }
    
    private void renderFilledCircle(Matrix4f matrix, VertexConsumer consumer, float radius, float alpha, float progress) {
        float r = 0.0F;
        float g = 1.0F - progress;
        float b = progress;
        
        int segments = 32;
        
        // Render circle as triangle fan
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * (2 * Math.PI) / segments);
            float angle2 = (float) ((i + 1) * (2 * Math.PI) / segments);
            
            // Center point
            consumer.vertex(matrix, 0, 0, 0)
                    .color(r, g, b, alpha * 0.3F)
                    .endVertex();
            
            // First edge point
            float x1 = Mth.cos(angle1) * radius;
            float z1 = Mth.sin(angle1) * radius;
            consumer.vertex(matrix, x1, 0, z1)
                    .color(r, g, b, alpha * 0.3F)
                    .endVertex();
            
            // Second edge point
            float x2 = Mth.cos(angle2) * radius;
            float z2 = Mth.sin(angle2) * radius;
            consumer.vertex(matrix, x2, 0, z2)
                    .color(r, g, b, alpha * 0.3F)
                    .endVertex();
        }
    }
    
    private void renderOutline(Matrix4f matrix, VertexConsumer consumer, float radius, float alpha, float progress) {
        float r = 0.0F;
        float g = 1.0F - progress;
        float b = progress;
        
        RenderSystem.lineWidth(thickness.getCurrentValue());
        
        int segments = 32;
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * (2 * Math.PI) / segments);
            float x = Mth.cos(angle) * radius;
            float z = Mth.sin(angle) * radius;
            
            consumer.vertex(matrix, x, 0, z)
                    .color(r, g, b, alpha)
                    .endVertex();
        }
        
        // Close the circle by connecting last point to first
        float x = Mth.cos(0) * radius;
        float z = Mth.sin(0) * radius;
        consumer.vertex(matrix, x, 0, z)
                .color(r, g, b, alpha)
                .endVertex();
    }
    
    private static class Circle {
        final Vec3 position;
        final long creationTime;
        
        Circle(Vec3 position, long creationTime) {
            this.position = position;
            this.creationTime = creationTime;
        }
    }
}