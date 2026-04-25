// Decompiled with: Procyon 0.6.0
// Class Version: 17
// Deobfuscated by Gemini
package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.combat.Aura;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@ModuleInfo(name = "Animations", description = "Customizes item animations and block animations", category = Category.RENDER)
public class Animations extends Module
{
    public BooleanValue onlyKillAura;
    public final ModeValue BlockMods;
    public final BooleanValue BlockOnlySword;
    public final BooleanValue KillauraAutoBlock;
    public final BooleanValue OverrideVanilla;
    public final BooleanValue ShowHUDItem;
    public final BooleanValue RenderOffhandShield;
    public final FloatValue BlockingX;
    public final FloatValue BlockingY;
    private boolean flip;
    public static boolean isBlocking;
    private final Minecraft mc;
    private float mainHandHeight;
    private float offHandHeight;
    private float oMainHandHeight;
    private float oOffHandHeight;
    private ItemStack mainHandItem;
    private ItemStack offHandItem;

    public Animations() {
        super();
        this.onlyKillAura = ValueBuilder.create(this, "Only KillAura").setDefaultBooleanValue(false).build().getBooleanValue();
        this.BlockMods = ValueBuilder.create(this, "Block Mods").setModes("None", "1.7", "Push").setDefaultModeIndex(1).build().getModeValue();
        this.BlockOnlySword = ValueBuilder.create(this, "Block Only Sword").setDefaultBooleanValue(true).build().getBooleanValue();
        this.KillauraAutoBlock = ValueBuilder.create(this, "Killaura Auto Block").setDefaultBooleanValue(true).build().getBooleanValue();
        this.OverrideVanilla = ValueBuilder.create(this, "Override Vanilla").setDefaultBooleanValue(true).build().getBooleanValue();
        this.ShowHUDItem = ValueBuilder.create(this, "Show HUD Item").setDefaultBooleanValue(true).build().getBooleanValue();
        this.RenderOffhandShield = ValueBuilder.create(this, "Render Offhand Shield").setDefaultBooleanValue(true).build().getBooleanValue();
        this.BlockingX = ValueBuilder.create(this, "Blocking-X").setDefaultFloatValue(0.56f).setMinFloatValue(-2.0f).setMaxFloatValue(2.0f).setFloatStep(0.01f).build().getFloatValue();
        this.BlockingY = ValueBuilder.create(this, "Blocking-Y").setDefaultFloatValue(-0.52f).setMinFloatValue(-2.0f).setMaxFloatValue(2.0f).setFloatStep(0.01f).build().getFloatValue();
        // m_91087_ -> getInstance
        this.mc = Minecraft.getInstance();
        this.mainHandHeight = 0.0f;
        this.offHandHeight = 0.0f;
        this.oMainHandHeight = 0.0f;
        this.oOffHandHeight = 0.0f;
        // f_41583_ -> EMPTY
        this.mainHandItem = ItemStack.EMPTY;
        this.offHandItem = ItemStack.EMPTY;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onRenderHand(final RenderHandEvent event) {
        if (!this.isEnabled() || !this.OverrideVanilla.getCurrentValue() || this.BlockMods.getCurrentMode().equals("None")) {
            return;
        }
        // m_41720_ -> getItem
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getItemStack().getItem() instanceof SwordItem)) {
            return;
        }
        boolean isOffhandUsing = false;
        // mc.f_91074_ -> mc.player
        // m_6117_ -> isUsingItem
        // m_7655_ -> getUsedItemHand
        if (this.mc.player.isUsingItem() && this.mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            // m_21206_ -> getOffhandItem
            final ItemStack offhandItem = this.mc.player.getOffhandItem();
            // m_41780_ -> getUseAnimation
            final UseAnim useAnim = offhandItem.getUseAnimation();
            if (useAnim != UseAnim.BLOCK) {
                isOffhandUsing = true;
            }
        }
        final boolean isKillauraBlocking = this.KillauraAutoBlock.getCurrentValue() && this.getAuraTarget() != null;
        if (this.onlyKillAura.getCurrentValue() && !isKillauraBlocking) {
            return;
        }
        if (isOffhandUsing && !isKillauraBlocking) {
            return;
        }
        // mc.f_91066_ -> mc.options
        // options.f_92095_ -> options.keyUse
        // keyUse.m_90857_ -> keyUse.isDown
        if (!this.mc.options.keyUse.isDown() && !isKillauraBlocking) {
            return;
        }
        event.setCanceled(true);
        this.renderArmWithItem(this.mc.player, event.getPartialTick(), event.getEquipProgress(), event.getHand(), event.getSwingProgress(), event.getItemStack(), event.getEquipProgress(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    @EventTarget
    public void onPacket(final EventPacket event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof ServerboundSwingPacket) {
            this.flip = !this.flip;
        }
    }

    @EventTarget
    public void onMotion(final EventMotion event) {
        if (event.getType() != EventType.PRE || this.mc.player == null) {
            return;
        }
        this.updateHandStates();
    }

    private void updateHandStates() {
        this.oMainHandHeight = this.mainHandHeight;
        this.oOffHandHeight = this.offHandHeight;
        final LocalPlayer localplayer = this.mc.player;
        // m_21205_ -> getMainHandItem
        final ItemStack itemstack = localplayer.getMainHandItem();
        // m_21206_ -> getOffhandItem
        final ItemStack itemstack2 = localplayer.getOffhandItem();
        final boolean isBlocking = this.isBlocking();
        if (isBlocking) {
            this.mainHandHeight = 1.0f;
            // m_41728_ -> isSameItem
            if (ItemStack.isSameItem(this.mainHandItem, itemstack)) {
                this.mainHandItem = itemstack;
            }
            if (ItemStack.isSameItem(this.offHandItem, itemstack2)) {
                this.offHandItem = itemstack2;
            }
            return;
        }
        // m_108637_ -> isSleeping
        if (localplayer.isSleeping()) {
            // m_14036_ -> clamp
            this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4f, 0.0f, 1.0f);
            this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4f, 0.0f, 1.0f);
        }
        else {
            // m_36403_ -> getAttackAnim
            final float f = localplayer.getAttackAnim(1.0f);
            // player.m_150109_ -> player.getInventory
            // inventory.f_35977_ -> inventory.selected
            final boolean flag = ForgeHooksClient.shouldCauseReequipAnimation(this.mainHandItem, itemstack, localplayer.getInventory().selected);
            final boolean flag2 = ForgeHooksClient.shouldCauseReequipAnimation(this.offHandItem, itemstack2, -1);
            if (!flag && this.mainHandItem != itemstack) {
                this.mainHandItem = itemstack;
            }
            if (!flag2 && this.offHandItem != itemstack2) {
                this.offHandItem = itemstack2;
            }
            final float targetMainHeight = flag ? 0.0f : (f * f * f);
            final float targetOffHeight = flag2 ? 0.0f : 1.0f;
            this.mainHandHeight += Mth.clamp(targetMainHeight - this.mainHandHeight, -0.2f, 0.2f);
            this.offHandHeight += Mth.clamp(targetOffHeight - this.offHandHeight, -0.2f, 0.2f);
        }
        if (this.mainHandHeight < 0.1f) {
            this.mainHandItem = itemstack;
        }
        if (this.offHandHeight < 0.1f) {
            this.offHandItem = itemstack2;
        }
    }

    private boolean isBlocking() {
        if (!this.isEnabled() || this.BlockMods.getCurrentMode().equals("None")) {
            return false;
        }
        final LocalPlayer player = this.mc.player;
        if (player == null) {
            return false;
        }
        final ItemStack mainHandItem = player.getMainHandItem();
        if (this.BlockOnlySword.getCurrentValue() && !(mainHandItem.getItem() instanceof SwordItem)) {
            return false;
        }
        boolean isOffhandUsing = false;
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            final ItemStack offhandItem = player.getOffhandItem();
            final UseAnim useAnim = offhandItem.getUseAnimation();
            if (useAnim != UseAnim.BLOCK) {
                isOffhandUsing = true;
            }
        }
        final boolean isKillauraBlocking = this.KillauraAutoBlock.getCurrentValue() && this.getAuraTarget() != null;
        if (this.onlyKillAura.getCurrentValue()) {
            return isKillauraBlocking;
        }
        return isKillauraBlocking || (!isOffhandUsing && this.mc.options.keyUse.isDown());
    }

    @EventTarget
    public void onRender(final EventRender event) {
        // mc.f_91073_ -> mc.level
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }
        if (this.ShowHUDItem.getCurrentValue()) {
            this.renderHUDItem(event);
        }
    }

    private void renderHUDItem(final EventRender event) {
        final ItemStack mainHandItem = this.mc.player.getMainHandItem();
        // m_41619_ -> isEmpty
        if (mainHandItem.isEmpty()) {
            return;
        }
        final PoseStack poseStack = new PoseStack();
        // mc.m_91269_ -> mc.renderBuffers
        // renderBuffers.m_110104_ -> renderBuffers.bufferSource
        final MultiBufferSource bufferSource = this.mc.renderBuffers().bufferSource();
        // mc.m_91296_ -> mc.getPartialTick
        final float partialTicks = this.mc.getPartialTick();
        final int packedLight = 15728880;
        // mc.m_91268_ -> mc.getWindow
        // window.m_85445_ -> window.getGuiScaledWidth
        final int screenWidth = this.mc.getWindow().getGuiScaledWidth();
        // window.m_85446_ -> window.getGuiScaledHeight
        final int screenHeight = this.mc.getWindow().getGuiScaledHeight();
        final float itemX = (float)(screenWidth - 100);
        final float itemY = (float)(screenHeight - 100);
        // poseStack.m_252880_ -> poseStack.translate
        poseStack.translate(itemX, itemY, 0.0f);
        // player.m_21324_ -> player.getAttackAnim
        final float swingProgress = this.mc.player.getAttackAnim(partialTicks);
        if (swingProgress > 0.0f) {
            // Mth.m_14031_ -> Mth.sin
            final float swingAngle = Mth.sin(swingProgress * swingProgress * 3.1415927f) * 10.0f;
            // poseStack.m_252781_ -> poseStack.mulPose
            // Axis.f_252403_ -> Axis.ZP
            // rotation.m_252977_ -> rotation.rotationDegrees
            poseStack.mulPose(Axis.ZP.rotationDegrees(swingAngle));
        }
        final float scale = 1.5f;
        // poseStack.m_85841_ -> poseStack.scale
        poseStack.scale(scale, scale, scale);
        this.renderItem(this.mc.player, mainHandItem, ItemDisplayContext.GUI, false, poseStack, bufferSource, packedLight);
    }

    private void renderArmWithItem(final AbstractClientPlayer player, final float partialTicks, final float equipProgress, final InteractionHand interactionHand, final float swingProgress, final ItemStack itemStack, final float equippedProg, final PoseStack poseStack, final MultiBufferSource multiBufferSource, final int light) {
        // player.m_150108_ -> player.isScoping
        if (!player.isScoping()) {
            final boolean flag = interactionHand == InteractionHand.MAIN_HAND;
            // player.m_5737_ -> player.getMainArm
            // humanoidArm.m_20828_ -> humanoidArm.getOpposite
            final HumanoidArm humanoidarm = flag ? player.getMainArm() : player.getMainArm().getOpposite();
            final Animations animations = this;
            // poseStack.m_85836_ -> poseStack.pushPose
            poseStack.pushPose();
            final boolean skipOffhandShield = !flag && player.getOffhandItem().getItem() instanceof ShieldItem && !this.RenderOffhandShield.getCurrentValue();
            if (!skipOffhandShield) {
                if (itemStack.isEmpty()) {
                    // player.m_20145_ -> player.isInvisible
                    if (flag && !player.isInvisible()) {
                        this.renderPlayerArm(poseStack, multiBufferSource, light, equippedProg, swingProgress, humanoidarm);
                    }
                }
                // itemStack.m_150930_ -> itemStack.is
                // Items.f_42573_ -> Items.FILLED_MAP
                else if (itemStack.is(Items.FILLED_MAP)) {
                    if (flag && this.offHandItem.isEmpty()) {
                        this.renderTwoHandedMap(poseStack, multiBufferSource, light, equipProgress, equippedProg, swingProgress);
                    }
                    else {
                        this.renderOneHandedMap(poseStack, multiBufferSource, light, equippedProg, humanoidarm, swingProgress, itemStack);
                    }
                }
                else {
                    // Items.f_42717_ -> Items.CROSSBOW
                    // CrossbowItem.m_40932_ -> CrossbowItem.isCharged
                    final boolean flag2 = itemStack.is(Items.CROSSBOW) && CrossbowItem.isCharged(itemStack);
                    final int i = (humanoidarm == HumanoidArm.RIGHT) ? 1 : -1;
                    if (itemStack.is(Items.CROSSBOW)) {
                        // player.m_6117_ -> player.isUsingItem
                        // player.m_21212_ -> player.getUseItemRemainingTicks
                        // player.m_7655_ -> player.getUsedItemHand
                        if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == interactionHand) {
                            this.applyItemArmTransform(poseStack, humanoidarm, equippedProg);
                            // poseStack.m_85837_ -> poseStack.translate
                            poseStack.translate(i * -0.4785682f, -0.0943870022892952, 0.05731530115008354);
                            // poseStack.m_252781_ -> poseStack.mulPose
                            // Axis.f_252529_ -> Axis.XP
                            // rotation.m_252961_ -> rotation.rotation
                            poseStack.mulPose(Axis.XP.rotation(-0.20830506f));
                            // Axis.f_252436_ -> Axis.YP
                            poseStack.mulPose(Axis.YP.rotation(i * 65.3f * 3.1415927f / 180.0f));
                            // Axis.f_252403_ -> Axis.ZP
                            poseStack.mulPose(Axis.ZP.rotation(i * -9.785f * 3.1415927f / 180.0f));
                            // itemStack.m_41779_ -> itemStack.getUseDuration
                            final float f6 = itemStack.getUseDuration() - (player.getUseItemRemainingTicks() - partialTicks + 1.0f);
                            // CrossbowItem.m_40939_ -> CrossbowItem.getChargeDuration
                            float f7 = f6 / CrossbowItem.getChargeDuration(itemStack);
                            f7 = Math.min(f7, 1.0f);
                            if (f7 > 0.1f) {
                                // Mth.m_14031_ -> Mth.sin
                                final float f8 = Mth.sin((f6 - 0.1f) * 1.3f);
                                final float f9 = f7 - 0.1f;
                                final float f10 = f8 * f9;
                                poseStack.translate(f10 * 0.0f, f10 * 0.004f, f10 * 0.0f);
                            }
                            poseStack.translate(f7 * 0.0f, f7 * 0.0f, f7 * 0.04f);
                            // poseStack.m_85841_ -> poseStack.scale
                            poseStack.scale(1.0f, 1.0f, 1.0f + f7 * 0.2f);
                            poseStack.mulPose(Axis.YP.rotation(i * -45.0f * 3.1415927f / 180.0f));
                        }
                        else {
                            // Mth.m_14116_ -> Mth.sqrt
                            final float f11 = -0.4f * Mth.sin(Mth.sqrt(swingProgress) * 3.1415927f);
                            final float f12 = 0.2f * Mth.sin(Mth.sqrt(swingProgress) * 6.2831855f);
                            final float f13 = -0.2f * Mth.sin(swingProgress * 3.1415927f);
                            poseStack.translate(i * f11, f12, f13);
                            this.applyItemArmTransform(poseStack, humanoidarm, equippedProg);
                            this.applyItemArmAttackTransform(poseStack, humanoidarm, swingProgress);
                            if (flag2 && swingProgress < 0.001f && flag) {
                                poseStack.translate(i * -0.641864f, 0.0, 0.0);
                                poseStack.mulPose(Axis.YP.rotation(i * 10.0f * 3.1415927f / 180.0f));
                            }
                        }
                        this.renderItem(player, itemStack, (i == 1) ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, i != 1, poseStack, multiBufferSource, light);
                    }
                    else {
                        final boolean flag3 = humanoidarm == HumanoidArm.RIGHT;
                        if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == interactionHand) {
                            switch (itemStack.getUseAnimation()) {
                                case NONE:
                                case BLOCK: {
                                    this.applyItemArmTransform(poseStack, humanoidarm, equippedProg);
                                    break;
                                }
                                case EAT:
                                case DRINK: {
                                    this.applyEatTransform(poseStack, partialTicks, humanoidarm, itemStack);
                                    this.applyItemArmTransform(poseStack, humanoidarm, equippedProg);
                                    break;
                                }
                                case BOW: {
                                    this.applyItemArmTransform(poseStack, humanoidarm, equippedProg);
                                    poseStack.translate(i * -0.2785682f, 0.18344399333000183, 0.15731529891490936);
                                    poseStack.mulPose(Axis.XP.rotation(-0.24321164f));
                                    poseStack.mulPose(Axis.YP.rotation(i * 35.3f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.ZP.rotation(i * -9.785f * 3.1415927f / 180.0f));
                                    final float f14 = itemStack.getUseDuration() - (player.getUseItemRemainingTicks() - partialTicks + 1.0f);
                                    float f15 = f14 / 20.0f;
                                    f15 = (f15 * f15 + f15 * 2.0f) / 3.0f;
                                    f15 = Math.min(f15, 1.0f);
                                    if (f15 > 0.1f) {
                                        final float f16 = Mth.sin((f14 - 0.1f) * 1.3f);
                                        final float f17 = f15 - 0.1f;
                                        final float f18 = f16 * f17;
                                        poseStack.translate(f18 * 0.0f, f18 * 0.004f, f18 * 0.0f);
                                    }
                                    poseStack.translate(f15 * 0.0f, f15 * 0.0f, f15 * 0.04f);
                                    poseStack.scale(1.0f, 1.0f, 1.0f + f15 * 0.2f);
                                    poseStack.mulPose(Axis.YP.rotation(i * -45.0f * 3.1415927f / 180.0f));
                                    break;
                                }
                                case SPEAR: {
                                    this.applyItemArmTransform(poseStack, humanoidarm, equippedProg);
                                    poseStack.translate(i * -0.5f, 0.699999988079071, 0.10000000149011612);
                                    poseStack.mulPose(Axis.XP.rotation(-0.9599311f));
                                    poseStack.mulPose(Axis.YP.rotation(i * 35.3f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.ZP.rotation(i * -9.785f * 3.1415927f / 180.0f));
                                    final float f19 = itemStack.getUseDuration() - (player.getUseItemRemainingTicks() - partialTicks + 1.0f);
                                    float f20 = f19 / 10.0f;
                                    f20 = Math.min(f20, 1.0f);
                                    if (f20 > 0.1f) {
                                        final float f21 = Mth.sin((f19 - 0.1f) * 1.3f);
                                        final float f22 = f20 - 0.1f;
                                        final float f23 = f21 * f22;
                                        poseStack.translate(f23 * 0.0f, f23 * 0.004f, f23 * 0.0f);
                                    }
                                    poseStack.translate(0.0, 0.0, f20 * 0.2f);
                                    poseStack.scale(1.0f, 1.0f, 1.0f + f20 * 0.2f);
                                    poseStack.mulPose(Axis.YP.rotation(i * -45.0f * 3.1415927f / 180.0f));
                                    break;
                                }
                            }
                        }
                        // Minecraft.m_91087_ -> Minecraft.getInstance
                        else if ((player.isUsingItem() || Minecraft.getInstance().options.keyUse.isDown() || (animations.KillauraAutoBlock.getCurrentValue() && this.getAuraTarget() != null)) && player.getMainHandItem().getItem() instanceof SwordItem && animations.BlockOnlySword.getCurrentValue() && !animations.BlockMods.getCurrentMode().equals("None")) {
                            final String lowerCase;
                            final String s = lowerCase = animations.BlockMods.getCurrentMode().toLowerCase();
                            switch (lowerCase) {
                                case "1.7": {
                                    poseStack.translate(i * this.BlockingX.getCurrentValue(), this.BlockingY.getCurrentValue(), -0.7200000286102295);
                                    final float f24 = Mth.sin(swingProgress * swingProgress * 3.1415927f);
                                    final float f25 = Mth.sin(Mth.sqrt(swingProgress) * 3.1415927f);
                                    poseStack.mulPose(Axis.YP.rotation(i * (45.0f + f24 * -20.0f) * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.ZP.rotation(i * f25 * -20.0f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.XP.rotation(f25 * -80.0f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.YP.rotation(i * -45.0f * 3.1415927f / 180.0f));
                                    poseStack.scale(0.9f, 0.9f, 0.9f);
                                    poseStack.translate(-0.2f, 0.126f, 0.2f);
                                    poseStack.mulPose(Axis.XP.rotation(-1.7845992f));
                                    poseStack.mulPose(Axis.YP.rotation(i * 15.0f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.ZP.rotation(i * 80.0f * 3.1415927f / 180.0f));
                                    break;
                                }
                                case "push": {
                                    poseStack.translate(i * this.BlockingX.getCurrentValue(), this.BlockingY.getCurrentValue(), -0.7200000286102295);
                                    poseStack.translate(i * -0.1414214f, 0.07999999821186066, 0.1414213925600052);
                                    poseStack.mulPose(Axis.XP.rotation(-1.7845992f));
                                    poseStack.mulPose(Axis.YP.rotation(i * 13.365f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.ZP.rotation(i * 78.05f * 3.1415927f / 180.0f));
                                    final float f26 = Mth.sin(swingProgress * swingProgress * 3.1415927f);
                                    final float f27 = Mth.sin(Mth.sqrt(swingProgress) * 3.1415927f);
                                    poseStack.mulPose(Axis.XP.rotation(f26 * -10.0f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.YP.rotation(f26 * -10.0f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.ZP.rotation(f26 * -10.0f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.XP.rotation(f27 * -10.0f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.YP.rotation(f27 * -10.0f * 3.1415927f / 180.0f));
                                    poseStack.mulPose(Axis.ZP.rotation(f27 * -10.0f * 3.1415927f / 180.0f));
                                    break;
                                }
                            }
                        }
                        // player.m_21209_ -> player.isPassenger
                        else if (player.isPassenger()) {
                            this.applyItemArmTransform(poseStack, humanoidarm, equippedProg);
                            poseStack.translate(i * -0.4f, 0.800000011920929, 0.30000001192092896);
                            poseStack.mulPose(Axis.YP.rotation(i * 65.0f * 3.1415927f / 180.0f));
                            poseStack.mulPose(Axis.ZP.rotation(i * -85.0f * 3.1415927f / 180.0f));
                        }
                        else {
                            this.applyItemArmTransform(poseStack, humanoidarm, equippedProg);
                            if (itemStack.getItem() instanceof SwordItem && (this.mc.options.keyUse.isDown() || (animations.KillauraAutoBlock.getCurrentValue() && this.getAuraTarget() != null && this.getAuraTarget() instanceof LivingEntity && this.getTargetHudEnabled()))) {
                                final String lowerCase2;
                                final String s = lowerCase2 = animations.BlockMods.getCurrentMode().toLowerCase();
                                switch (lowerCase2) {
                                    case "1.7": {
                                        poseStack.translate(i * 0.56f, -0.5199999809265137, -0.7200000286102295);
                                        final float f24 = Mth.sin(swingProgress * swingProgress * 3.1415927f);
                                        final float f25 = Mth.sin(Mth.sqrt(swingProgress) * 3.1415927f);
                                        poseStack.mulPose(Axis.YP.rotation(i * (45.0f + f24 * -20.0f) * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.ZP.rotation(i * f25 * -20.0f * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.XP.rotation(f25 * -80.0f * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.YP.rotation(i * -45.0f * 3.1415927f / 180.0f));
                                        poseStack.scale(0.9f, 0.9f, 0.9f);
                                        poseStack.translate(-0.2f, 0.126f, 0.2f);
                                        poseStack.mulPose(Axis.XP.rotation(-1.7845992f));
                                        poseStack.mulPose(Axis.YP.rotation(i * 15.0f * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.ZP.rotation(i * 80.0f * 3.1415927f / 180.0f));
                                        break;
                                    }
                                    case "Push": {
                                        poseStack.translate(i * 0.56f, -0.5199999809265137, -0.7200000286102295);
                                        poseStack.translate(i * -0.1414214f, 0.07999999821186066, 0.1414213925600052);
                                        poseStack.mulPose(Axis.XP.rotation(-1.7845992f));
                                        poseStack.mulPose(Axis.YP.rotation(i * 13.365f * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.ZP.rotation(i * 78.05f * 3.1415927f / 180.0f));
                                        final float f26 = Mth.sin(swingProgress * swingProgress * 3.1415927f);
                                        final float f27 = Mth.sin(Mth.sqrt(swingProgress) * 3.1415927f);
                                        poseStack.mulPose(Axis.XP.rotation(f26 * -10.0f * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.YP.rotation(f26 * -10.0f * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.ZP.rotation(f26 * -10.0f * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.XP.rotation(f27 * -10.0f * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.YP.rotation(f27 * -10.0f * 3.1415927f / 180.0f));
                                        poseStack.mulPose(Axis.ZP.rotation(f27 * -10.0f * 3.1415927f / 180.0f));
                                        break;
                                    }
                                    default: {
                                        this.applyItemArmAttackTransform(poseStack, humanoidarm, swingProgress);
                                        break;
                                    }
                                }
                            }
                            else {
                                this.applyItemArmAttackTransform(poseStack, humanoidarm, swingProgress);
                            }
                        }
                        this.renderItem(player, itemStack, flag3 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !flag3, poseStack, multiBufferSource, light);
                    }
                }
            }
            // poseStack.m_85849_ -> poseStack.popPose
            poseStack.popPose();
        }
    }

    private LivingEntity getAuraTarget() {
        final Aura aura = (Aura)Naven.getInstance().getModuleManager().getModule(Aura.class);
        if (aura != null && aura.isEnabled()) {
            try {
                final Field targetField = Aura.class.getDeclaredField("target");
                targetField.setAccessible(true);
                return (LivingEntity)targetField.get(null);
            }
            catch (final Exception e) {
                return null;
            }
        }
        return null;
    }

    private boolean getTargetHudEnabled() {
        final Aura aura = (Aura)Naven.getInstance().getModuleManager().getModule(Aura.class);
        if (aura != null && aura.isEnabled()) {
            try {
                final Field targetHudField = Aura.class.getDeclaredField("targetHud");
                targetHudField.setAccessible(true);
                final Object targetHudValue = targetHudField.get(aura);
                if (targetHudValue != null) {
                    final Method getCurrentValueMethod = targetHudValue.getClass().getMethod("getCurrentValue", (Class<?>[])new Class[0]);
                    return (boolean)getCurrentValueMethod.invoke(targetHudValue, new Object[0]);
                }
            }
            catch (final Exception e) {
                return false;
            }
        }
        return false;
    }

    private void renderPlayerArm(final PoseStack poseStack, final MultiBufferSource bufferSource, final int light, final float equippedProg, final float swingProgress, final HumanoidArm arm) {
        final boolean flag = arm == HumanoidArm.RIGHT;
        final float f = flag ? 1.0f : -1.0f;
        final float f2 = Mth.sqrt(swingProgress);
        final float f3 = -0.3f * Mth.sin(f2 * 3.1415927f);
        final float f4 = 0.4f * Mth.sin(f2 * 6.2831855f);
        final float f5 = -0.4f * Mth.sin(swingProgress * 3.1415927f);
        poseStack.translate(f * (0.644764f + f3), 0.644764f + f4, 0.644764f + f5);
        poseStack.mulPose(Axis.XP.rotation(-0.3f * Mth.sin(f2 * 6.2831855f)));
        poseStack.mulPose(Axis.YP.rotation(f * 0.4f * Mth.sin(f2 * 3.1415927f)));
        poseStack.mulPose(Axis.ZP.rotation(f * -0.4f * Mth.sin(swingProgress * 3.1415927f)));
        // Mth.m_14179_ -> Mth.lerp
        final float f6 = Mth.lerp(equippedProg, this.oMainHandHeight, this.mainHandHeight);
        final float f7 = Mth.lerp(equippedProg, this.oOffHandHeight, this.offHandHeight);
        this.renderItem(this.mc.player, flag ? this.mainHandItem : this.offHandItem, flag ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !flag, poseStack, bufferSource, light);
    }

    private void renderTwoHandedMap(final PoseStack poseStack, final MultiBufferSource bufferSource, final int light, final float equipProgress, final float equippedProg, final float swingProgress) {
        final float f = Mth.sqrt(swingProgress);
        final float f2 = -0.2f * Mth.sin(swingProgress * 3.1415927f);
        final float f3 = -0.4f * Mth.sin(f * 3.1415927f);
        poseStack.translate(0.0, -f2 / 2.0f, f3);
        final float f4 = Mth.lerp(equippedProg, this.oMainHandHeight, this.mainHandHeight);
        final float f5 = Mth.lerp(equippedProg, this.oOffHandHeight, this.offHandHeight);
        this.renderItem(this.mc.player, this.mainHandItem, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, poseStack, bufferSource, light);
        this.renderItem(this.mc.player, this.offHandItem, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, true, poseStack, bufferSource, light);
    }

    private void renderOneHandedMap(final PoseStack poseStack, final MultiBufferSource bufferSource, final int light, final float equippedProg, final HumanoidArm arm, final float swingProgress, final ItemStack item) {
        final float f = (arm == HumanoidArm.RIGHT) ? 1.0f : -1.0f;
        poseStack.translate(f * 0.125f, 0.0, 0.0);
        final float f2 = Mth.sqrt(swingProgress);
        final float f3 = -0.1f * Mth.sin(f2 * 3.1415927f);
        final float f4 = -0.3f * Mth.sin(f2 * 6.2831855f);
        final float f5 = -0.4f * Mth.sin(swingProgress * 3.1415927f);
        poseStack.translate(0.0, -f3 / 2.0f, f5);
        poseStack.mulPose(Axis.XP.rotation(f4 * 3.1415927f / 180.0f));
        poseStack.mulPose(Axis.YP.rotation(f * f2 * 3.1415927f / 180.0f));
        poseStack.mulPose(Axis.ZP.rotation(f * f3 * 3.1415927f / 180.0f));
        final float f6 = Mth.lerp(equippedProg, this.oMainHandHeight, this.mainHandHeight);
        final float f7 = Mth.lerp(equippedProg, this.oOffHandHeight, this.offHandHeight);
        this.renderItem(this.mc.player, item, (arm == HumanoidArm.RIGHT) ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, arm != HumanoidArm.RIGHT, poseStack, bufferSource, light);
    }

    private void applyItemArmTransform(final PoseStack poseStack, final HumanoidArm arm, final float equippedProg) {
        final int i = (arm == HumanoidArm.RIGHT) ? 1 : -1;
        final float f = Mth.lerp(equippedProg, this.oMainHandHeight, this.mainHandHeight);
        final float f2 = Mth.lerp(equippedProg, this.oOffHandHeight, this.offHandHeight);
        poseStack.translate(i * 0.56f, -0.52f + f * -0.6f, -0.7200000286102295);
    }

    private void applyItemArmAttackTransform(final PoseStack poseStack, final HumanoidArm arm, final float swingProgress) {
        final int i = (arm == HumanoidArm.RIGHT) ? 1 : -1;
        final float f = Mth.sin(swingProgress * swingProgress * 3.1415927f);
        final float f2 = Mth.sin(Mth.sqrt(swingProgress) * 3.1415927f);
        poseStack.translate(i * 0.56f, -0.5199999809265137, -0.7200000286102295);
        poseStack.mulPose(Axis.XP.rotation(-1.7845992f));
        poseStack.mulPose(Axis.YP.rotation(i * 13.365f * 3.1415927f / 180.0f));
        poseStack.mulPose(Axis.ZP.rotation(i * 78.05f * 3.1415927f / 180.0f));
        final float swingFactor = Mth.clamp(swingProgress, 0.0f, 1.0f);
        poseStack.mulPose(Axis.XP.rotation(f * -15.0f * swingFactor * 3.1415927f / 180.0f));
        poseStack.mulPose(Axis.YP.rotation(f2 * -15.0f * swingFactor * 3.1415927f / 180.0f));
        poseStack.mulPose(Axis.ZP.rotation(f2 * -70.0f * swingFactor * 3.1415927f / 180.0f));
    }

    private void applyEatTransform(final PoseStack poseStack, final float partialTicks, final HumanoidArm arm, final ItemStack item) {
        final float f = item.getUseDuration() - (this.mc.player.getUseItemRemainingTicks() - partialTicks + 1.0f);
        final float f2 = f / item.getUseDuration();
        if (f2 < 0.8f) {
            // Mth.m_14154_ -> Mth.abs
            // Mth.m_14089_ -> Mth.cos
            final float f3 = Mth.abs(Mth.cos(f / 4.0f * 3.1415927f) * 0.1f);
            poseStack.translate(0.0, f3, 0.0);
        }
        final float f4 = 1.0f - (float)Math.pow(1.0f - f2, 27.0);
        final int i = (arm == HumanoidArm.RIGHT) ? 1 : -1;
        poseStack.translate(f4 * 0.6f * i, f4 * -0.5f, f4 * 0.0f);
        poseStack.mulPose(Axis.YP.rotation(i * f4 * 90.0f * 3.1415927f / 180.0f));
        poseStack.mulPose(Axis.XP.rotation(f4 * 10.0f * 3.1415927f / 180.0f));
        poseStack.mulPose(Axis.ZP.rotation(i * f4 * 30.0f * 3.1415927f / 180.0f));
    }

    private void renderItem(final LivingEntity entity, final ItemStack stack, final ItemDisplayContext transformType, final boolean leftHand, final PoseStack poseStack, final MultiBufferSource buffer, final int light) {
        if (stack.isEmpty()) {
            return;
        }
        // mc.m_91291_ -> mc.getItemRenderer
        final ItemRenderer itemRenderer = this.mc.getItemRenderer();
        // itemRenderer.m_269491_ -> itemRenderer.renderStatic
        // entity.m_9236_ -> entity.level()
        itemRenderer.renderStatic(entity, stack, transformType, leftHand, poseStack, buffer, entity.level(), light, 0, 0);
    }

    static {
        Animations.isBlocking = false;
    }
}
