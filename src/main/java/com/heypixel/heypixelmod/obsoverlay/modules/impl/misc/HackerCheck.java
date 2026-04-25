package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationLevel;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ModuleInfo(
        name = "HackerCheck",
        description = "Check all player cheats actions, but don't check you",
        category = Category.MISC
)
public class HackerCheck extends Module {
    private static final Notification freeusernoti = new Notification(NotificationLevel.WARNING, "FreeUser无法使用此功能!", 2000L);

    private final BooleanValue seedVLmessage = ValueBuilder.create(this, "SeedVLMessage")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();
    private final BooleanValue debug = ValueBuilder.create(this, "Debug")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();
    private static final String PREFIX = "§b[Naven§c-Re §4AC§b] ";
    private static final int MAX_VL = 3;

    private static final Set<UUID> hackers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    @EventTarget
    public void onMotion(EventMotion event) {
        if (mc.level == null || mc.player == null) {
            return;
        }

        Set<UUID> currentPlayers = mc.level.players().stream()
                .map(Player::getUUID)
                .collect(Collectors.toSet());
        playerDataMap.keySet().retainAll(currentPlayers);


        for (Player player : mc.level.players()) {
            if (player.equals(mc.player) || player.isCreative() || player.isSpectator() || mc.player.distanceToSqr(player) > 2048) {
                continue;
            }

            PlayerData data = playerDataMap.computeIfAbsent(player.getUUID(), u -> new PlayerData());

            data.resetTicks++;
            if (data.resetTicks > 2400 && data.vl > 0) {
                if(seedVLmessage.getCurrentValue()){ sendChatMessage(PREFIX + "§fThe VL of §a" + player.getName().getString() + "§f has been reset"); }
                data.vl = 0;
                data.resetTicks = 0;
                hackers.remove(player.getUUID());
            }

            if (data.antiKbCooldown > 0) data.antiKbCooldown--;
            if (data.noSlowBlockCooldown > 0) data.noSlowBlockCooldown--;
            data.inCobwebTicks = isPlayerInCobweb(player) ? data.inCobwebTicks + 1 : 0;
            data.usingItemTicks = player.isUsingItem() ? data.usingItemTicks + 1 : 0;


            if (!isHacker(player)) {
                checkViolations(player, data);
            }

            data.lastPosition = player.position();
            data.lastOnGround = player.onGround();
        }
    }

    private void checkViolations(Player player, PlayerData data) {
        // --- AntiKB Check ---
        if (data.antiKbCooldown <= 0) {
            if (player.hurtTime == 9 && data.lastOnGround && player.onGround()) {
                double horizontalSpeed = new Vec3(player.getDeltaMovement().x, 0, player.getDeltaMovement().z).length();

                if (debug.getCurrentValue()) {
                    sendChatMessage(PREFIX + "§e[Debug] §f" + player.getName().getString() + " took knockback with speed: " + String.format("%.4f", horizontalSpeed));
                }

                if (horizontalSpeed < 0.20) {
                    if (isAnotherPlayerNearby(player, 4.0)) {
                        increaseVl(player, data, 1, "AntiKB");
                        data.antiKbCooldown = 3;
                    }
                }
            }
        }

        // --- NoSlow (Block - Cobweb) Check ---
        if (data.noSlowBlockCooldown <= 0) {
            if (data.inCobwebTicks > 3) {
                double horizontalSpeed = new Vec3(player.getX() - data.lastPosition.x, 0, player.getZ() - data.lastPosition.z).length();

                if (debug.getCurrentValue()) {
                    sendChatMessage(PREFIX + "§e[Debug] §f" + player.getName().getString() + " in cobweb with speed: " + String.format("%.4f", horizontalSpeed));
                }

                if (horizontalSpeed > 0.06) {
                    increaseVl(player, data, 1, "NoSlow(Block)");
                    data.noSlowBlockCooldown = 3;
                }
            }
        }
    }

    private boolean isPlayerInCobweb(Player player) {
        BlockPos posFeet = player.blockPosition();
        return mc.level.getBlockState(posFeet).is(Blocks.COBWEB) || mc.level.getBlockState(posFeet.above()).is(Blocks.COBWEB);
    }
    private boolean isAnotherPlayerNearby(Player targetPlayer, double radius) {
        AABB searchArea = targetPlayer.getBoundingBox().inflate(radius);
        List<Player> nearbyPlayers = mc.level.getEntitiesOfClass(Player.class, searchArea,
                (otherPlayer) -> !otherPlayer.getUUID().equals(targetPlayer.getUUID()));
        return !nearbyPlayers.isEmpty();
    }

    private void increaseVl(Player player, PlayerData data, int amount, String checkName) {
        data.vl += amount;
        data.resetTicks = 0;
        if(seedVLmessage.getCurrentValue()){
            sendChatMessage(PREFIX + "§b" + player.getName().getString() + " §ftrigger check project §b" + checkName + "§f(§fVL: §c" + data.vl + "§f)");
        }

        if (data.vl >= MAX_VL) {
            if (hackers.add(player.getUUID())) {
                Naven.getInstance().getNotificationManager().addNotification(
                        new Notification(NotificationLevel.WARNING, "Detected " + checkName + " on " + player.getName().getString(), 3000L)
                );
                sendChatMessage(PREFIX + "§c" + player.getName().getString() + " §f is a hacker (§b" + checkName + "§f)");
            }
        }
    }

    private void sendChatMessage(String message) {
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(Component.nullToEmpty(message));
        }
    }

    public static boolean isHacker(Player player) {
        return hackers.contains(player.getUUID());
    }

    @Override
    public void onEnable() {
        if(Objects.equals(Naven.userRank, "FreeUser")){
            this.setEnabled(false);
            Naven.getInstance().getNotificationManager().addNotification(freeusernoti);
        }
        hackers.clear();
        playerDataMap.clear();
    }

    @Override
    public void onDisable() {
        hackers.clear();
        playerDataMap.clear();
    }

    private static class PlayerData {
        int vl = 0;
        int resetTicks = 0;
        int usingItemTicks = 0;
        int antiKbCooldown = 0;
        int noSlowBlockCooldown = 0;
        int inCobwebTicks = 0;
        Vec3 lastPosition = Vec3.ZERO;
        boolean lastOnGround = false;
    }
}