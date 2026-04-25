package com.heypixel.heypixelmod.obsoverlay.utils;

import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMoveInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class MoveUtils {
   private static final Minecraft mc = Minecraft.getInstance();

   private static float direction(float forward, float strafe) {
      float direction = mc.player.getYRot();
      boolean isMovingForward = forward > 0.0F;
      boolean isMovingBack = forward < 0.0F;
      boolean isMovingRight = strafe > 0.0F;
      boolean isMovingLeft = strafe < 0.0F;
      boolean isMovingSideways = isMovingRight || isMovingLeft;
      boolean isMovingStraight = isMovingForward || isMovingBack;
      if (forward != 0.0F || strafe != 0.0F) {
         if (isMovingBack && !isMovingSideways) {
            return direction + 180.0F;
         }

         if (isMovingForward && isMovingLeft) {
            return direction + 45.0F;
         }

         if (isMovingForward && isMovingRight) {
            return direction - 45.0F;
         }

         if (!isMovingStraight && isMovingLeft) {
            return direction + 90.0F;
         }

         if (!isMovingStraight && isMovingRight) {
            return direction - 90.0F;
         }

         if (isMovingBack && isMovingLeft) {
            return direction + 135.0F;
         }

         if (isMovingBack) {
            return direction - 135.0F;
         }
      }

      return direction;
   }

   public static float getDirection() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;

      if (player == null) {
         return 0.0f;
      }

      // 获取玩家当前的按键输入状态
      float moveForward = player.input.forwardImpulse; // 前后移动 (+1.0 for W, -1.0 for S)
      float moveStrafe = player.input.leftImpulse;    // 左右移动 (+1.0 for A, -1.0 for D)

      // 获取玩家当前的视角Yaw
      float yaw = player.getYRot();

      // 根据按键组合计算最终的移动方向Yaw
      if (moveForward != 0.0f) {
         if (moveStrafe > 0.0f) { // W + A
            yaw += (moveForward > 0.0f) ? -45.0f : 45.0f;
         } else if (moveStrafe < 0.0f) { // W + D
            yaw += (moveForward > 0.0f) ? 45.0f : -45.0f;
         }
         if (moveForward < 0.0f) { // S
            yaw += 180.0f;
         }
      } else {
         if (moveStrafe > 0.0f) { // A
            yaw -= 90.0f;
         } else if (moveStrafe < 0.0f) { // D
            yaw += 90.0f;
         }
      }
      return yaw;
   }

   public static void fixMovement(EventMoveInput event, float yaw) {
      float forward = event.getForward();
      float strafe = event.getStrafe();
      int angleUnit = 45;
      float angleTolerance = 22.5F;
      float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
      double angleDifference = (double)MathHelper.wrapDegrees(direction(forward, strafe) - yaw);
      double angleDistance = Math.abs(angleDifference);
      forward = 0.0F;
      strafe = 0.0F;
      if (angleDistance <= (double)((float)angleUnit + angleTolerance)) {
         forward++;
      } else if (angleDistance >= (double)(180.0F - (float)angleUnit - angleTolerance)) {
         forward--;
      }

      if (angleDifference >= (double)((float)angleUnit - angleTolerance) && angleDifference <= (double)(180.0F - (float)angleUnit + angleTolerance)) {
         strafe--;
      } else if (angleDifference <= (double)((float)(-angleUnit) + angleTolerance) && angleDifference >= (double)(-180.0F + (float)angleUnit - angleTolerance)) {
         strafe++;
      }

      forward *= directionFactor;
      strafe *= directionFactor;
      event.setForward(forward);
      event.setStrafe(strafe);
   }

   public static boolean isMoving() {
      return mc.player.input.leftImpulse != 0.0F
         || mc.player.input.forwardImpulse != 0.0F
         || mc.options.keyJump.isDown()
         || mc.options.keyLeft.isDown()
         || mc.options.keyRight.isDown()
         || mc.options.keyUp.isDown()
         || mc.options.keyDown.isDown();
   }
}
