package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationLevel;
import com.heypixel.heypixelmod.obsoverlay.utils.ChunkUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.ProjectionUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.TickTimeHelper;
import com.heypixel.heypixelmod.obsoverlay.utils.Vector2f;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInfo(
        name = "GhostHand",
        description = "Interact with entities and chests from a distance",
        category = Category.MISC
)
public class GhostHand extends Module {
    private final BooleanValue chests = ValueBuilder.create(this, "Chests").setDefaultBooleanValue(true).build().getBooleanValue();
    private final BooleanValue enderChests = ValueBuilder.create(this, "EnderChests").setDefaultBooleanValue(true).build().getBooleanValue();
    private final BooleanValue villagers = ValueBuilder.create(this, "Villagers").setDefaultBooleanValue(true).build().getBooleanValue();
    private final BooleanValue armorStands = ValueBuilder.create(this, "ArmorStands").setDefaultBooleanValue(true).build().getBooleanValue();
    private final BooleanValue namedEntities = ValueBuilder.create(this, "Named Entities").setDefaultBooleanValue(true).build().getBooleanValue();
    private final BooleanValue renderTags = ValueBuilder.create(this, "Render Tags").setDefaultBooleanValue(true).build().getBooleanValue();

    private final Minecraft mc = Minecraft.getInstance();
    private final TickTimeHelper timer = new TickTimeHelper();
    private final List<RenderInfo> renderList = new CopyOnWriteArrayList<>();

    @EventTarget
    public void onMotion(EventMotion event) {
        if (event.getType() != EventType.PRE) return;

        if (mc.options.keyUse.isDown() && timer.delay(5)) {
            if (mc.player == null || mc.level == null) return;
            double range = mc.gameMode.getPickRange();
            Vec3 eyePos = mc.player.getEyePosition(1.0F);
            Vec3 lookVec = mc.player.getViewVector(1.0F);
            Vec3 reachEnd = eyePos.add(lookVec.scale(range));
            findAndInteractWithTarget(eyePos, reachEnd);
        }
    }

    @EventTarget
    public void onRender(EventRender event) {
        renderList.clear();
        if (!renderTags.getCurrentValue() || mc.player == null || mc.level == null) return;

        double blockRenderRange = 6.0;
        double entityRenderRange = 8.0;
        float partialTicks = event.getRenderPartialTicks();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos playerPos = mc.player.blockPosition();

        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(entityRenderRange))) {
            if (isTargetEntity(entity)) {
                Vec3 renderPos = getEntityRenderPosition(entity, partialTicks);
                if (renderPos.distanceTo(cameraPos) <= entityRenderRange) {
                    Vector2f screenPos = ProjectionUtils.project(renderPos.x, renderPos.y, renderPos.z, partialTicks);
                    if (screenPos != null) {
                        renderList.add(new RenderInfo(screenPos, "[Interact]", Color.CYAN));
                    }
                }
            }
        }

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-6, -6, -6), playerPos.offset(6, 6, 6))) {
            BlockState state = mc.level.getBlockState(pos);
            boolean isTargetBlock = (chests.getCurrentValue() && state.getBlock() instanceof ChestBlock) ||
                    (enderChests.getCurrentValue() && state.getBlock() instanceof EnderChestBlock);

            if (isTargetBlock) {
                Vec3 topCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5);
                if (topCenter.distanceTo(cameraPos) <= blockRenderRange) {
                    Vector2f screenPos = ProjectionUtils.project(topCenter.x, topCenter.y, topCenter.z, partialTicks);
                    if (screenPos != null) {
                        if (state.getBlock() instanceof EnderChestBlock) {
                            renderList.add(new RenderInfo(screenPos, "[Ender Chest]", Color.MAGENTA));
                        } else {
                            renderList.add(new RenderInfo(screenPos, "[Chest]", Color.YELLOW));
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (!renderTags.getCurrentValue()) return;

        for (RenderInfo info : renderList) {
            Vector2f pos = info.screenPos;
            String tag = info.tag;
            Color color = info.color;
            Fonts.harmony.render(event.getStack(), tag, pos.x, pos.y, color, true, 0.4F);
        }
    }

    private Vec3 getEntityRenderPosition(Entity entity, float partialTicks) {
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTicks;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTicks;
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTicks;
        return new Vec3(x, y + entity.getBbHeight() / 2, z);
    }

    private void findAndInteractWithTarget(Vec3 eyePos, Vec3 reachEnd) {
        Entity closestEntity = null;
        BlockHitResult closestBlockHit = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(reachEnd.distanceTo(eyePos)))) {
            if (isTargetEntity(entity)) {
                Optional<Vec3> hitOpt = entity.getBoundingBox().inflate(0.1).clip(eyePos, reachEnd);
                if (hitOpt.isPresent()) {
                    double distSq = eyePos.distanceToSqr(hitOpt.get());
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closestEntity = entity;
                        closestBlockHit = null;
                    }
                }
            }
        }

        for (BlockEntity be : ChunkUtils.getLoadedBlockEntities().toList()) {
            BlockState state = be.getBlockState();
            boolean isTargetBlock = (chests.getCurrentValue() && state.getBlock() instanceof ChestBlock) ||
                    (enderChests.getCurrentValue() && state.getBlock() instanceof EnderChestBlock);
            if (isTargetBlock) {
                AABB box = getBlockBoundingBox(be);
                if (box != null) {
                    Optional<Vec3> hitOpt = box.clip(eyePos, reachEnd);
                    if (hitOpt.isPresent()) {
                        double distSq = eyePos.distanceToSqr(hitOpt.get());
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closestBlockHit = new BlockHitResult(hitOpt.get(), Direction.UP, be.getBlockPos(), false);
                            closestEntity = null;
                        }
                    }
                }
            }
        }

        if (closestEntity != null) {
            interactWithEntity(closestEntity);
            timer.reset();
        } else if (closestBlockHit != null) {
            interactWithBlock(closestBlockHit);
            timer.reset();
        }
    }

    private AABB getBlockBoundingBox(BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            BlockState state = be.getBlockState();
            if (!state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.LEFT) return null;
            BlockPos pos = be.getBlockPos();
            AABB box = new AABB(pos);
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                Direction connectedDir = ChestBlock.getConnectedDirection(state);
                if (connectedDir != null) box = box.minmax(new AABB(pos.relative(connectedDir)));
            }
            return box;
        }
        return new AABB(be.getBlockPos());
    }

    private boolean isTargetEntity(Entity entity) {
        if (villagers.getCurrentValue() && entity instanceof Villager) return true;
        if (armorStands.getCurrentValue() && entity instanceof ArmorStand) return true;
        if (namedEntities.getCurrentValue() && entity.hasCustomName()) {
            String name = entity.getDisplayName().getString().toUpperCase();
            return name.contains("SHOP") || name.contains("CLICK") || name.contains("UPGRADES") || name.contains("QUEST");
        }
        return false;
    }

    private void interactWithEntity(Entity entity) {
        mc.getConnection().send(ServerboundInteractPacket.createInteractionPacket(entity, mc.player.isShiftKeyDown(), InteractionHand.MAIN_HAND));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void interactWithBlock(BlockHitResult hitResult) {
        mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, 0));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static class RenderInfo {
        final Vector2f screenPos;
        final String tag;
        final Color color;

        RenderInfo(Vector2f screenPos, String tag, Color color) {
            this.screenPos = screenPos;
            this.tag = tag;
            this.color = color;
        }
    }
}