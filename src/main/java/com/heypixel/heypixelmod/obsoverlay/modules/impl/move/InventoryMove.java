package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMoveInput;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.ui.ClickGUI;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;

@ModuleInfo(
        name = "InventoryMove",
        description = "Allows you to move while in GUI interfaces.",
        category = Category.MOVEMENT
)
public class InventoryMove extends Module {
    private final Minecraft minecraft = Minecraft.getInstance();

    public BooleanValue sneak = ValueBuilder.create(this, "Sneak")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    // --- 状态变量 ---
    // 用于记录在打开GUI之前，Sprint模块是否是开启的
    private boolean wasSprintEnabled = false;
    // 用于标记当前是否由InvMove在管理Sprint模块的状态
    private boolean isManagingSprint = false;

    @Override
    public void onEnable() {
        // 初始化状态
        this.isManagingSprint = false;
        this.wasSprintEnabled = false;
    }

    @Override
    public void onDisable() {
        // 当禁用InvMove时，必须确保恢复Sprint模块到它本来的状态
        if (isManagingSprint) {
            Sprint sprintModule = (Sprint) Naven.getInstance().getModuleManager().getModule(Sprint.class);
            if (sprintModule != null && wasSprintEnabled && !sprintModule.isEnabled()) {
                sprintModule.toggle(); // 使用 toggle() 更安全
            }
            isManagingSprint = false;
        }
    }

    @EventTarget
    public void onTick(EventRunTicks event) {
        if (event.getType() != EventType.PRE || mc.player == null) return;

        Sprint sprintModule = (Sprint) Naven.getInstance().getModuleManager().getModule(Sprint.class);
        if (sprintModule == null) return; // 如果找不到Sprint模块，则不执行任何操作

        boolean inGui = isMovementAllowed();

        // --- 核心逻辑：管理Sprint模块的开关 ---

        // 状态转换：刚刚进入GUI
        if (inGui && !isManagingSprint) {
            // 1. 记录下Sprint模块当前是否开启
            wasSprintEnabled = sprintModule.isEnabled();
            // 2. 如果是开启的，就将它关闭
            if (wasSprintEnabled) {
                sprintModule.toggle(); // 关闭Sprint模块
            }
            // 3. 标记现在由我们来管理Sprint
            isManagingSprint = true;
        }

        // 状态转换：刚刚退出GUI
        if (!inGui && isManagingSprint) {
            // 1. 检查在打开GUI之前，Sprint模块是否是开启的
            if (wasSprintEnabled) {
                sprintModule.toggle(); // 重新开启Sprint模块
            }
            // 2. 解除管理状态
            isManagingSprint = false;
        }

        // --- 原有的移动和视角调整逻辑 ---
        if (inGui) {
            this.adjustPlayerRotation();
        }
    }

    @EventTarget(1)
    public void handleMoveInput(EventMoveInput event) {
        if (!this.isMovementAllowed()) return;

        // 根据按键设置移动输入
        event.setForward(this.calculateForwardMovement());
        event.setStrafe(this.calculateStrafeMovement());
        event.setJump(this.isKeyActive(this.minecraft.options.keyJump));
        event.setSneak(this.sneak.getCurrentValue() && this.isKeyActive(this.minecraft.options.keyShift));
    }


    // --- Helper 方法 ---

    private boolean isKeyActive(KeyMapping keyMapping) {
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), keyMapping.getKey().getValue());
    }

    private boolean isKeyActive(int keyCode) {
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), keyCode);
    }

    private boolean isMovementAllowed() {
        Screen currentScreen = this.minecraft.screen;
        return this.minecraft.player != null && currentScreen != null && (this.isContainerScreen(currentScreen) || this.isClickGuiScreen(currentScreen));
    }

    private boolean isContainerScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen;
    }



    private boolean isClickGuiScreen(Screen screen) {
        String className = screen.getClass().getSimpleName();
        return screen instanceof ClickGUI || className.contains("ClickGUI") || className.contains("ClickGui");
    }

    private float calculateForwardMovement() {
        if (this.isKeyActive(this.minecraft.options.keyUp)) return 1.0F;
        return this.isKeyActive(this.minecraft.options.keyDown) ? -1.0F : 0.0F;
    }

    private float calculateStrafeMovement() {
        if (this.isKeyActive(this.minecraft.options.keyLeft)) return 1.0F;
        return this.isKeyActive(this.minecraft.options.keyRight) ? -1.0F : 0.0F;
    }

    private void adjustPlayerRotation() {
        LocalPlayer player = this.minecraft.player;
        if (this.isKeyActive(265)) player.setXRot(Math.max(player.getXRot() - 5.0F, -90.0F)); // Up
        if (this.isKeyActive(264)) player.setXRot(Math.min(player.getXRot() + 5.0F, 90.0F));  // Down
        if (this.isKeyActive(263)) player.setYRot(player.getYRot() - 5.0F);                       // Left
        if (this.isKeyActive(262)) player.setYRot(player.getYRot() + 5.0F);                       // Right
    }
}