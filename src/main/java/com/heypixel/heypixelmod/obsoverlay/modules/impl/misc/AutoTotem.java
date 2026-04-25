package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationLevel;
import com.heypixel.heypixelmod.obsoverlay.utils.MoveUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

@ModuleInfo(
        name = "AutoTotem",
        category = Category.MISC,
        description = "Automatically re-totems when popped."
)
public class AutoTotem extends Module {
    private final ModeValue mode;
    private final FloatValue totemSlot;
    private final BooleanValue switchBack;
    private final BooleanValue legitMode;
    private final BooleanValue inventoryOnly;
    private final BooleanValue autoOpen;
    private final BooleanValue autoClose;
    private final FloatValue refillDelay;
    private final FloatValue openDelay;
    private final FloatValue closeDelay;
    private final FloatValue timeout;

    private long lastActionTime;
    private long lastMoveTime = -1;
    private boolean didAutoOpen;
    private int originalSlot = -1;
    private int noMoveTicks = 0;

    private boolean wasMovingTotem = false;
    private boolean isPopped = false;

    public AutoTotem() {
        mode = ValueBuilder.create(this, "Mode").setModes("Offhand", "Offhand & Totem Slot").build().getModeValue();
        totemSlot = ValueBuilder.create(this, "Totem Slot").setDefaultFloatValue(9.0f).setMinFloatValue(1.0f).setMaxFloatValue(9.0f).setFloatStep(1.0f).setVisibility(() -> mode.isCurrentMode("Offhand & Totem Slot")).build().getFloatValue();
        switchBack = ValueBuilder.create(this, "Switch Back").setDefaultBooleanValue(true).setVisibility(() -> mode.isCurrentMode("Offhand & Totem Slot")).build().getBooleanValue();
        legitMode = ValueBuilder.create(this, "Legit").setDefaultBooleanValue(true).build().getBooleanValue();
        inventoryOnly = ValueBuilder.create(this, "Inventory Only").setDefaultBooleanValue(true).build().getBooleanValue();
        autoOpen = ValueBuilder.create(this, "Auto Open Inventory").setDefaultBooleanValue(false).build().getBooleanValue();
        autoClose = ValueBuilder.create(this, "Auto Close").setDefaultBooleanValue(false).build().getBooleanValue();
        refillDelay = ValueBuilder.create(this, "Refill Delay (Ticks)").setDefaultFloatValue(1.0f).setMinFloatValue(0.0f).setMaxFloatValue(40.0f).setFloatStep(1.0f).build().getFloatValue();
        openDelay = ValueBuilder.create(this, "Open Delay (Ticks)").setDefaultFloatValue(0.0f).setMinFloatValue(0.0f).setMaxFloatValue(40.0f).setFloatStep(1.0f).setVisibility(() -> autoOpen.getCurrentValue()).build().getFloatValue();
        closeDelay = ValueBuilder.create(this, "Close Delay (Ticks)").setDefaultFloatValue(2.0f).setMinFloatValue(0.0f).setMaxFloatValue(40.0f).setFloatStep(1.0f).setVisibility(() -> autoClose.getCurrentValue()).build().getFloatValue();
        timeout = ValueBuilder.create(this, "Timeout (Seconds)").setDefaultFloatValue(3.0f).setMinFloatValue(1.0f).setMaxFloatValue(10.0f).setFloatStep(0.5f).build().getFloatValue();
    }

    @Override
    public void onEnable() {
        resetState();
    }
    @Override
    public void onDisable() { resetState(); }
    private void resetState() {
        lastActionTime = -1;
        lastMoveTime = -1;
        didAutoOpen = false;
        originalSlot = -1;
        noMoveTicks = 0;
        wasMovingTotem = false;
        isPopped = false;
    }

    @EventTarget
    public void onPacket(EventPacket e) {
        if (mc.player == null || mc.level == null) return;
        if (e.getPacket() instanceof ClientboundEntityEventPacket) {
            ClientboundEntityEventPacket packet = (ClientboundEntityEventPacket) e.getPacket();
            Entity entity = packet.getEntity(mc.level);

            if (entity != null && entity.equals(mc.player) && packet.getEventId() == 35) {
                lastActionTime = System.currentTimeMillis();

                if (mode.isCurrentMode("Offhand & Totem Slot")) {
                    int designatedSlot = (int) totemSlot.getCurrentValue() - 1;
                    if (mc.player.getInventory().getItem(designatedSlot).getItem() == Items.TOTEM_OF_UNDYING) {
                        if (originalSlot == -1) {
                            originalSlot = mc.player.getInventory().selected;
                        }
                        setSlot(designatedSlot);
                        isPopped = true;
                    }
                }
            }
        }
    }

    @EventTarget
    public void onMotion(EventMotion e) {
        if (e.getType() != EventType.PRE || mc.player == null) return;

        if (MoveUtils.isMoving()) {
            this.noMoveTicks = 0;
        } else {
            this.noMoveTicks++;
        }

        if (didAutoOpen && !(mc.screen instanceof InventoryScreen)) didAutoOpen = false;

        if (switchBack.getCurrentValue() && originalSlot != -1 && mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING && !wasMovingTotem && !isPopped) {
            setSlot(originalSlot);
            originalSlot = -1;
        }

        if (autoClose.getCurrentValue() && didAutoOpen && lastMoveTime != -1) {
            long closeDelayMillis = (long) (closeDelay.getCurrentValue() * 50.0f);
            if (System.currentTimeMillis() - lastMoveTime >= closeDelayMillis) {
                mc.player.closeContainer();
                didAutoOpen = false;
                lastMoveTime = -1;
            }
        }

        if (lastActionTime != -1) {
            long timeoutMillis = (long) (timeout.getCurrentValue() * 1000.0f);
            if (System.currentTimeMillis() - lastActionTime > timeoutMillis) {
                lastActionTime = -1;
            }
        }

        if (mode.isCurrentMode("Offhand & Totem Slot") && mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING && isLastAvailableTotemInHotbar()) {
            if (mc.screen instanceof InventoryScreen) {
                int designatedSlot = (int) totemSlot.getCurrentValue() - 1;
                moveTotem(designatedSlot, 40);
                lastActionTime = -1;
                return;
            } else if (lastActionTime != -1) {
                moveLastTotemToOffhand();
                lastActionTime = -1;
                return;
            }
        }

        if (legitMode.getCurrentValue() && !inventoryOnly.getCurrentValue() && !autoOpen.getCurrentValue() && !autoClose.getCurrentValue()) {

            boolean isMoving = MoveUtils.isMoving();
            boolean hasTotemInMainHand = mc.player.getMainHandItem().getItem() == Items.TOTEM_OF_UNDYING;
            boolean hasTotemInOffhand = mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING;
            if (isMoving && hasTotemInMainHand && !hasTotemInOffhand) {
                if (!wasMovingTotem) {
                    originalSlot = mc.player.getInventory().selected;
                    KeyMapping.click(mc.options.keySwapOffhand.getKey());
                    wasMovingTotem = true;
                }
            }

            else if (!isMoving && wasMovingTotem) {
                if (originalSlot != -1) {
                    setSlot(originalSlot);
                    originalSlot = -1;
                }
                wasMovingTotem = false;
            }
        }

        if (lastActionTime == -1 || !needsRefill()) {
            if (isPopped && mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
                isPopped = false;
            }
            return;
        }

        boolean needsInventoryAction = legitMode.getCurrentValue() || inventoryOnly.getCurrentValue();
        if (autoOpen.getCurrentValue() && !(mc.screen instanceof InventoryScreen) && needsInventoryAction) {
            long openDelayMillis = (long) (openDelay.getCurrentValue() * 50.0f);
            if (System.currentTimeMillis() - lastActionTime >= openDelayMillis) {
                mc.setScreen(new InventoryScreen(mc.player));
                didAutoOpen = true;
                return;
            }
        }

        boolean canRefill = !legitMode.getCurrentValue() || (mc.screen instanceof InventoryScreen) || (!inventoryOnly.getCurrentValue() && noMoveTicks > 2);
        if (!canRefill) return;

        long refillDelayMillis = (long) (refillDelay.getCurrentValue() * 50.0f);
        if (System.currentTimeMillis() - lastActionTime < refillDelayMillis) return;

        if (mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
            refillSlot(40);
            return;
        }

        if (mode.isCurrentMode("Offhand & Totem Slot") && countAvailableTotems() > 0) {
            int designatedSlot = (int) totemSlot.getCurrentValue() - 1;
            if (mc.player.getInventory().getItem(designatedSlot).getItem() != Items.TOTEM_OF_UNDYING) {
                refillSlot(designatedSlot);
            }
        }
    }

    private void setSlot(int slot) {
        if (slot >= 0 && slot < 9 && mc.player != null) {
            mc.player.getInventory().selected = slot;
        }
    }

    private void refillSlot(int slot) {
        int designatedTotemHotbarSlot = mode.isCurrentMode("Offhand & Totem Slot") ? (int) totemSlot.getCurrentValue() - 1 : -1;
        int totemInventorySlot = findItemSlot(Items.TOTEM_OF_UNDYING, designatedTotemHotbarSlot);

        if (totemInventorySlot != -1) {
            moveTotem(totemInventorySlot, slot);
        }
    }

    private boolean needsRefill() {
        if (isLastAvailableTotemInHotbar()) return false;

        boolean needsRefill = false;
        if (mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
            needsRefill = true;
        } else if (mode.isCurrentMode("Offhand & Totem Slot")) {
            if (countAvailableTotems() > 0) {
                int designatedSlot = (int) totemSlot.getCurrentValue() - 1;
                if (mc.player.getInventory().getItem(designatedSlot).getItem() != Items.TOTEM_OF_UNDYING) {
                    needsRefill = true;
                }
            }
        }

        if (!needsRefill) return false;

        int designatedTotemHotbarSlot = mode.isCurrentMode("Offhand & Totem Slot") ? (int) totemSlot.getCurrentValue() - 1 : -1;
        int sourceTotemSlot = findItemSlot(Items.TOTEM_OF_UNDYING, designatedTotemHotbarSlot);

        return sourceTotemSlot != -1;
    }

    private int countAvailableTotems() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; ++i) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                count++;
            }
        }
        return count;
    }

    private boolean isLastAvailableTotemInHotbar() {
        if (mc.player == null) return false;
        if (!mode.isCurrentMode("Offhand & Totem Slot")) return false;

        if (countAvailableTotems() == 1) {
            int designatedSlot = (int) totemSlot.getCurrentValue() - 1;
            return mc.player.getInventory().getItem(designatedSlot).getItem() == Items.TOTEM_OF_UNDYING;
        }
        return false;
    }

    private void moveTotem(int fromSlot, int toSlot) {
        if (mc.player == null || mc.gameMode == null) return;
        int containerFromSlot = fromSlot < 9 ? fromSlot + 36 : fromSlot;

        if (legitMode.getCurrentValue()) {
            int containerToSlot = (toSlot == 40) ? 45 : (toSlot < 9 ? toSlot + 36 : toSlot);
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, containerFromSlot, 0, ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, containerToSlot, 0, ClickType.PICKUP, mc.player);
            if (!mc.player.containerMenu.getCarried().isEmpty()) {
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, containerFromSlot, 0, ClickType.PICKUP, mc.player);
            }
        } else {
            int buttonId = (toSlot == 40) ? 40 : toSlot;
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, containerFromSlot, buttonId, ClickType.SWAP, mc.player);
        }
        lastMoveTime = System.currentTimeMillis();
    }

    private void moveLastTotemToOffhand() {
        if (mc.player == null) return;
        int designatedSlot = (int) totemSlot.getCurrentValue() - 1;

        setSlot(designatedSlot);

        KeyMapping.click(mc.options.keySwapOffhand.getKey());
    }

    private int findItemSlot(net.minecraft.world.item.Item item, int excludeSlot) {
        if (mc.player == null) return -1;
        for (int i = 9; i < 36; ++i) {
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        for (int i = 0; i < 9; ++i) {
            if (i == excludeSlot) continue;
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }
}
