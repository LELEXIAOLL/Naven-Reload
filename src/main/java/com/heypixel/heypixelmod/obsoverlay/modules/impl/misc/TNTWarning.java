package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ProjectionUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.Vector2f;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.Colors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.*;

@ModuleInfo(
        name = "TNTWarning",
        description = "Displays countdown timer on primed TNT",
        category = Category.MISC
)
public class TNTWarning extends Module {

    @EventTarget
    public void onRender(EventRender2D event) {
        if (mc.level == null || mc.player == null) {
            return;
        }


        boolean tntNearby = false;
        Vec3 playerPos = mc.player.position();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt) {
                PrimedTnt tntEntity = (PrimedTnt) entity;
                int fuse = tntEntity.getFuse();
                Vec3 tntPos = tntEntity.position();
                double distance = playerPos.distanceTo(tntPos);

                if (distance <= 8.0) {
                    tntNearby = true;
                }
                tntPos = tntPos.add(0, tntEntity.getBoundingBox().getYsize() + 0.5, 0);
                Vector2f screenPos = ProjectionUtils.project(tntPos.x, tntPos.y, tntPos.z, 1.0F);
                if (screenPos.x != Float.MAX_VALUE && screenPos.y != Float.MAX_VALUE) {
                    String text = String.format("%.1f", fuse / 20.0f);
                    float x = screenPos.x - Fonts.harmony.getWidth(text, 0.5) / 2;
                    float y = screenPos.y;

                    Color color = Color.RED;
                    if (fuse > 40) {
                        color = Color.YELLOW;
                    } else if (fuse > 20) {
                        color = Color.ORANGE;
                    }

                    Fonts.harmony.render(event.getStack(), text, x, y, color, true, 0.5);
                }
            }
        }
        if (tntNearby) {
            drawScreenEdgeWarning(event);
        }
    }

    private void drawScreenEdgeWarning(EventRender2D event) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int gradientWidth = 30;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < gradientWidth; i++) {
            int alpha = (int)(255 * ((float)(gradientWidth - i) / gradientWidth) * 0.3f);
            int color = Colors.getColor(255, 0, 0, alpha);
            RenderUtils.drawRectBound(event.getStack(), 0, i, screenWidth, 1, color);
            RenderUtils.drawRectBound(event.getStack(), 0, screenHeight - i - 1, screenWidth, 1, color);
            RenderUtils.drawRectBound(event.getStack(), i, 0, 1, screenHeight, color);
            RenderUtils.drawRectBound(event.getStack(), screenWidth - i - 1, 0, 1, screenHeight, color);
        }

        RenderSystem.disableBlend();
    }
}