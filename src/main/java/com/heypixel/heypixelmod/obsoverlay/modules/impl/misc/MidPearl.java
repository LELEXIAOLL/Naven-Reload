package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMouseClick;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        name = "MidPearl",
        description = "Automatically throws ender pearl when pressing middle mouse button",
        category = Category.MISC
)
public class MidPearl extends Module {
    private final ModeValue mode = ValueBuilder.create(this, "Mode")
            .setModes("Fast Switch", "Fake Spoof")
            .setDefaultModeIndex(0)
            .build()
            .getModeValue();

    private final ModeValue keyMode = ValueBuilder.create(this, "Trigger Key")
            .setModes("Mid Button", "Mouse4", "Mouse5")
            .setDefaultModeIndex(0)
            .build()
            .getModeValue();

    private int originalSlot = -1;

    @EventTarget
    public void onMouseClick(EventMouseClick event) {

        int triggerKey = 2;
        if (keyMode.isCurrentMode("Mouse4")) {
            triggerKey = 3;
        } else if (keyMode.isCurrentMode("Mouse5")) {
            triggerKey = 4;
        }

        if (event.getKey() == triggerKey && !event.isState() && mc.player != null && mc.gameMode != null) {
            int pearlSlot = findPearlSlot();
            if (pearlSlot == -1) {
                ChatUtils.addChatMessage("§cPearl Not Found in hotbar!");
                return;
            }


            if (mode.isCurrentMode("Fast Switch")) {
                originalSlot = mc.player.getInventory().selected;
                mc.player.getInventory().selected = pearlSlot;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.getInventory().selected = originalSlot;
            } else if (mode.isCurrentMode("Fake Spoof")) {
                originalSlot = mc.player.getInventory().selected;

                ItemStack pearlStack = mc.player.getInventory().getItem(pearlSlot);
                mc.player.getInventory().selected = pearlSlot;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);

                mc.player.getInventory().selected = originalSlot;
            }
        }
    }

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.ENDER_PEARL && stack.getCount() > 0) {
                return i;
            }
        }
        return -1;
    }
}
