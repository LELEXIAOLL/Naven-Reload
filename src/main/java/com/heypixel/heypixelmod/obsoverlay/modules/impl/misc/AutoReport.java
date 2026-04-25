package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obfuscation.JNICObf;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.IRCModule.IrcClientManager;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.*;

@JNICObf
@ModuleInfo(
        name = "AutoReport",
        description = "自动举报玩家，但会豁免IRC用户。",
        category = Category.MISC
)
public class AutoReport extends Module {
    private static final List<String> TARGET_PLAYERS = Arrays.asList(
            "萌吖同学", "XYUNFENGX", "burningcake4", "Muchuanbei",
            "Si_Rui", "CuteGirlQiQi", "Melor_", "bi_yue_hu"
    );

    private boolean isWaitingForContainer = false;
    private int ticksWaited = 0;
    private String reportedPlayerName = null;
    private boolean autoprotocal = false;

    // --- 主要修改点 1: 新增延迟处理变量 ---
    private String playerToReportAfterDelay = null;
    private int reportDelayTicks = 0;

    public ModeValue Protocal = ValueBuilder.create(this, "Protocal")
            .setDefaultModeIndex(0)
            .setModes("Normal", "Veta")
            .build()
            .getModeValue();

    private void tryReportPlayer(String playerName) {
        if (playerName == null || playerName.isEmpty() || this.isWaitingForContainer || !IrcClientManager.INSTANCE.isConnected()) {
            return;
        }

        IrcClientManager.IrcUserInfo userInfo = IrcClientManager.INSTANCE.getIrcUserInfo(playerName);

        if (userInfo != null) {
            String rank = userInfo.rank();
            String ircUsername = userInfo.ircUsername();
            String displayRank;
            switch (rank) {
                case "Admin": displayRank = "§c管理员"; break;
                case "Dev": displayRank = "§b客户端作者"; break;
                case "Beta": displayRank = "§e内部用户"; break;
                case "FreeUser": displayRank = "§a公益用户"; break;
                default: displayRank = "§7" + rank; break;
            }

            String ircMessage = String.format("我无法举报 %s ，因为他是IRC用户[%s§r-%s§r]", playerName, displayRank, ircUsername);
            String sender = IrcClientManager.INSTANCE.currentUser.ircUsername;
            if (sender != null && !sender.isEmpty()) {
                String chatCommand = String.format("chat: '%s' '%s'", sender, ircMessage);
                IrcClientManager.INSTANCE.sendMessage(chatCommand);
            }

            ChatUtils.addChatMessage("§c[AutoReport] §e已取消举报，因为 §c" + playerName + " §e是IRC用户: " + displayRank);
        } else {
            this.reportedPlayerName = playerName;
            if (mc.player != null) {
                mc.player.connection.sendCommand("report " + this.reportedPlayerName);
            }
            this.isWaitingForContainer = true;
            this.ticksWaited = 0;
        }
    }

    @EventTarget
    public void onPacket(EventPacket event) {
        setSuffix(Protocal.getCurrentMode());

        if (event.getPacket() instanceof ClientboundSystemChatPacket packet) {
            String message = packet.content().getString();

            if (!autoprotocal) {
                autoprotocal = true; // 避免重复检测
                if (message.contains("布吉岛协议加载成功")) {
                    Protocal.setCurrentValue(1);
                    ChatUtils.addChatMessage("§c[AutoReport] §e检测到Veta协议，已自动切换Veta模式");
                } else {
                    Protocal.setCurrentValue(0);
                    ChatUtils.addChatMessage("§c[AutoReport] §e未检测到Veta协议，已自动切换Normal模式");
                }
            }

            if (mc.player == null || mc.level == null || !this.isEnabled()) {
                return;
            }

            // --- 主要修改点 2: 死亡后不再立即举报，而是启动延迟计时器 ---
            if (message.contains("你现在是观察者") && !this.isWaitingForContainer && this.playerToReportAfterDelay == null) {
                Optional<AbstractClientPlayer> nearestPlayer = mc.level.players().stream()
                        .filter(player -> !player.equals(mc.player) && player.isAlive() && mc.player.distanceToSqr(player) <= 100)
                        .min(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)));

                nearestPlayer.ifPresent(player -> {
                    this.playerToReportAfterDelay = player.getName().getString();
                    this.reportDelayTicks = 20; // 设置20 tick (1秒) 的延迟
                });
            }
        }
    }

    @EventTarget
    public void onMotion(EventMotion e) {
        if (e.getType() != EventType.PRE || !this.isEnabled()) {
            return;
        }

        // --- 主要修改点 3: 在 onMotion 中处理延迟计时 ---
        if (this.reportDelayTicks > 0) {
            this.reportDelayTicks--;
            if (this.reportDelayTicks == 0) {
                tryReportPlayer(this.playerToReportAfterDelay);
                this.playerToReportAfterDelay = null; // 重置
            }
        }

        if (!this.isWaitingForContainer && mc.level != null && mc.player != null && ticksWaited++ % 20 == 0) {
            for (AbstractClientPlayer onlinePlayer : mc.level.players()) {
                if (onlinePlayer.equals(mc.player)) continue;
                if (TARGET_PLAYERS.contains(onlinePlayer.getName().getString())) {
                    tryReportPlayer(onlinePlayer.getName().getString());
                    break;
                }
            }
        }

        if (!this.isWaitingForContainer) {
            return;
        }

        if (Protocal.isCurrentMode("Veta")) {
            resetState();
            return;
        }

        this.ticksWaited++;
        if (this.ticksWaited > 40) {
            ChatUtils.addChatMessage("§c[AutoReport] §e举报GUI超时，已重置状态。");
            resetState();
            return;
        }

        if (mc.screen instanceof ContainerScreen container) {
            AbstractContainerMenu menu = container.getMenu();
            int containerSlotCount = menu.slots.size() - 36;

            for (int i = 0; i < containerSlotCount; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (!stack.isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.PICKUP, mc.player);
                    ChatUtils.addChatMessage("§c[AutoReport] §e检测到死亡，已自动举报");
                    if(mc.player != null) mc.player.closeContainer();
                    resetState();
                    break;
                }
            }
        }
    }

    private void resetState() {
        this.isWaitingForContainer = false;
        this.reportedPlayerName = null;
        this.ticksWaited = 0;
        this.playerToReportAfterDelay = null;
        this.reportDelayTicks = 0;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.autoprotocal = false;
        resetState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetState();
        if (Naven.userRank != null && Naven.userRank.equals("FreeUser")) {
            ChatUtils.addChatMessage("§e和树友要继续和布吉岛玩家做朋友哦");
            this.setEnabled(true);
        }
    }
}