package com.heypixel.heypixelmod.obsoverlay.ui.targethud;

import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

public class TargetESP {
    private static final float[] targetColorRed = new float[]{0.78431374F, 0.0F, 0.0F, 0.23529412F};
    private static final float[] targetColorGreen = new float[]{0.0F, 0.78431374F, 0.0F, 0.23529412F};
    private static final ResourceLocation NITRO_TEXTURE = new ResourceLocation("heypixel", "textures/target/target1.png");
    private static final ResourceLocation RAINBOW_NITRO_TEXTURE = new ResourceLocation("heypixel", "textures/target/target2.png");
    private static final Random random = new Random();
    private static float rotationAngle = 0.0F;
    private static float currentRotationSpeed = 3.0F;
    private static float targetRotationSpeed = 3.0F;
    private static int rotationSpeedTickCounter = 0;
    private static int rotationDirectionTickCounter = 0;
    private static int rotationDirection = 1;
    private static float currentSizeMultiplier = 1.0F;
    private static float targetSizeMultiplier = 1.0F;
    private static int sizeTickCounter = 0;

    public static void render(EventRender e, List<Entity> targets, Entity mainTarget, String style) {
        switch (style) {
            case "Naven":
                renderNavenStyle(e, targets, mainTarget);
                break;
            case "Nitro":
                renderNitroStyle(e, targets, mainTarget, NITRO_TEXTURE);
                break;
            case "RainbowNitro":
                renderNitroStyle(e, targets, mainTarget, RAINBOW_NITRO_TEXTURE);
                break;
        }
    }

    private static void renderNavenStyle(EventRender e, List<Entity> targets, Entity mainTarget) {
        PoseStack stack = e.getPMatrixStack();
        float partialTicks = e.getRenderPartialTicks();
        stack.pushPose();
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        GL11.glEnable(2848);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderUtils.applyRegionalRenderOffset(stack);

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity) {
                float[] color = mainTarget == entity ? targetColorRed : targetColorGreen;
                stack.pushPose();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
                double motionX = entity.getX() - entity.xo;
                double motionY = entity.getY() - entity.yo;
                double motionZ = entity.getZ() - entity.zo;
                AABB boundingBox = entity.getBoundingBox()
                        .move(-motionX, -motionY, -motionZ)
                        .move(partialTicks * motionX, partialTicks * motionY, partialTicks * motionZ);
                RenderUtils.drawSolidBox(boundingBox, stack);
                stack.popPose();
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(3042);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(2848);
        stack.popPose();
    }

    // [修改点] 增加 ResourceLocation 参数，复用渲染逻辑
    private static void renderNitroStyle(EventRender e, List<Entity> targets, Entity mainTarget, ResourceLocation texture) {
        PoseStack stack = e.getPMatrixStack();
        float partialTicks = e.getRenderPartialTicks();
        net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        // 更新旋转逻辑
        rotationSpeedTickCounter++;
        if (rotationSpeedTickCounter >= 10) {
            rotationSpeedTickCounter = 0;
            targetRotationSpeed = 3.0F + random.nextFloat() * 3.0F;
        }
        rotationDirectionTickCounter++;
        if (rotationDirectionTickCounter >= 40 + random.nextInt(41)) {
            rotationDirectionTickCounter = 0;
            targetRotationSpeed = 0;
        }
        if (Math.abs(currentRotationSpeed) < 0.1F && targetRotationSpeed == 0) {
            rotationDirection *= -1;
            targetRotationSpeed = 3.0F + random.nextFloat() * 3.0F;
        }
        currentRotationSpeed = Mth.lerp(0.1F, currentRotationSpeed, targetRotationSpeed * rotationDirection);
        rotationAngle += currentRotationSpeed;

        // 更新大小逻辑
        sizeTickCounter++;
        if (sizeTickCounter >= 10) {
            sizeTickCounter = 0;
            targetSizeMultiplier = 1.0F + random.nextFloat() * 0.5F;
        }
        currentSizeMultiplier = Mth.lerp(0.1F, currentSizeMultiplier, targetSizeMultiplier);

        stack.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity)) continue;

            double entityX = entity.xo + (entity.getX() - entity.xo) * partialTicks;
            double entityY = entity.yo + (entity.getY() - entity.yo) * partialTicks + entity.getBbHeight() / 2.0;
            double entityZ = entity.zo + (entity.getZ() - entity.zo) * partialTicks;

            float distance = (float) cameraPos.distanceTo(new Vec3(entityX, entityY, entityZ));
            float baseSize = 1.0F + (distance / 20.0F);
            baseSize = Math.max(1.0F, Math.min(baseSize, 3.0F));
            float finalSize = baseSize * currentSizeMultiplier;

            stack.pushPose();
            stack.translate(entityX - cameraPos.x, entityY - cameraPos.y, entityZ - cameraPos.z);
            stack.mulPose(camera.rotation());
            stack.mulPose(Axis.ZP.rotationDegrees(rotationAngle));
            stack.scale(finalSize, finalSize, finalSize);

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            // [修改点] 使用传入的 texture
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tessellator.getBuilder();
            Matrix4f matrix = stack.last().pose();

            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferBuilder.vertex(matrix, -0.5F, -0.5F, 0.0F).uv(0.0F, 0.0F).endVertex();
            bufferBuilder.vertex(matrix, -0.5F, 0.5F, 0.0F).uv(0.0F, 1.0F).endVertex();
            bufferBuilder.vertex(matrix, 0.5F, 0.5F, 0.0F).uv(1.0F, 1.0F).endVertex();
            bufferBuilder.vertex(matrix, 0.5F, -0.5F, 0.0F).uv(1.0F, 0.0F).endVertex();
            tessellator.end();

            stack.popPose();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        stack.popPose();
    }
}