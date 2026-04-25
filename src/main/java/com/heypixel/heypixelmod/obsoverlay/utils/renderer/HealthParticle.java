package com.heypixel.heypixelmod.obsoverlay.utils.renderer;

import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector2f;

import java.awt.*;
import java.util.Random;

public class HealthParticle {
    private final Vector2f position;
    private final Vector2f velocity;
    private final float size;
    private final int color;
    private final long creationTime;
    private final long lifeTime;
    private float alpha;
    private boolean dead;

    private static final Random random = new Random();

    public HealthParticle(float x, float y) {
        this.position = new Vector2f(x, y);

        // 进一步增大速度范围，使粒子散开更远
        this.velocity = new Vector2f(
                (random.nextFloat() - 0.5f) * 10.0f, // 增大到 10.0f
                (random.nextFloat() - 0.5f) * 10.0f  // 增大到 10.0f
        );

        // 稍微增大粒子大小范围
        this.size = random.nextFloat() * 3.0f + 2.0f; // 大小在 2.0 到 5.0 之间
        this.color = new Color(0x66CCFF).getRGB();
        this.alpha = 1.0f;
        this.creationTime = System.currentTimeMillis();
        this.lifeTime = 600 + random.nextInt(400); // 稍微增加生命周期
        this.dead = false;
    }

    public void update() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - creationTime;

        if (elapsedTime > lifeTime) {
            this.dead = true;
            return;
        }

        float progress = (float) elapsedTime / lifeTime;

        float easedProgress = (float) (1.0 - Math.pow(1.0 - progress, 2));

        position.x += velocity.x * (1.0f - easedProgress);
        position.y += velocity.y * (1.0f - easedProgress);

        this.alpha = 1.0f - easedProgress;
    }

    public void render(GuiGraphics graphics) {
        if (dead) return;

        int particleColor = new Color(
                (color >> 16) & 0xFF,
                (color >> 8) & 0xFF,
                color & 0xFF,
                (int) (alpha * 255)
        ).getRGB();

        RenderUtils.drawRoundedRect(
                graphics.pose(),
                position.x,
                position.y,
                size,
                size,
                size / 2.0f,
                particleColor
        );
    }

    public boolean isDead() {
        return dead;
    }
}