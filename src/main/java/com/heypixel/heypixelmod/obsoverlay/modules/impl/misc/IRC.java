// 文件路径: com/heypixel/heypixelmod/obsoverlay/modules/impl/misc/IRC.java
package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventUpdate;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.IRCManager;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

@ModuleInfo(
        name = "IRC",
        description = "Chat with other Naven users.",
        category = Category.MISC
)
public class IRC extends Module {

    private final BooleanValue bmwIrc = ValueBuilder.create(this, "BMWIRC")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue debugMode = ValueBuilder.create(this, "Debug")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    // [修改] 移除了 userCreationAttempted 和 ticksSinceStart
    private int checkPlayerCounter = 0;

    public boolean isDebugEnabled() {
        return debugMode.getCurrentValue();
    }

    public boolean isBmwIrcEnabled() {
        return bmwIrc.getCurrentValue();
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        boolean isRunning = IRCManager.getInstance().isProcessRunning();

        // 管理进程启动/停止
        if (bmwIrc.getCurrentValue() && !isRunning) {
            IRCManager.getInstance().start();
            resetIrcState();
        } else if (!bmwIrc.getCurrentValue() && isRunning) {
            IRCManager.getInstance().stop();
        }

        // 运行时逻辑
        if (isEnabled() && isRunning) {
            // [修改] 删除了原来的 100 tick 延迟注册逻辑，现在由 Manager 在连接成功时自动触发

            // 定时检查周围玩家
            checkPlayerCounter++;
            if (checkPlayerCounter >= 20) {
                checkPlayerCounter = 0;
                checkForNearbyIrcUsers();
            }
        }
    }

    private void checkForNearbyIrcUsers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        for (Player player : mc.level.players()) {
            if (player.getUUID().equals(mc.player.getUUID())) {
                continue;
            }

            String playerName = player.getGameProfile().getName();
            if (IRCManager.getInstance().isIrcUser(playerName)) {
                IRCManager.getInstance().announceUser(playerName);
            }
        }
    }

    private void resetIrcState() {
        this.checkPlayerCounter = 0;
    }

    @Override
    public void onEnable() {
        resetIrcState();
    }

    @Override
    public void onDisable() {
        IRCManager.killAllBmwProcesses();
    }
}