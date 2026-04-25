package com.heypixel.heypixelmod.obsoverlay.utils.renderer;

public class HealthBarAnimator {
    private float displayedHealth;
    private long lastUpdateTime;
    private final float animationSpeed;
    // --- 【新增代码块 1】---
    // 添加一个成员变量来存储目标值
    private float targetHealth;

    /**
     * Constructs a new HealthBarAnimator.
     * @param initialHealth The starting health value.
     * @param animationSpeed The speed of the animation. A higher value means faster transition.
     */
    public HealthBarAnimator(float initialHealth, float animationSpeed) {
        this.displayedHealth = initialHealth;
        this.targetHealth = initialHealth; // 初始化目标值
        this.lastUpdateTime = System.currentTimeMillis();
        this.animationSpeed = animationSpeed;
    }

    /**
     * Updates the displayed health value based on the target health and elapsed time.
     * 这个方法现在只负责计算动画，目标值由 setTargetHealth 或 update(float) 设置。
     */
    public void update() {
        long currentTime = System.currentTimeMillis();
        long deltaTime = currentTime - this.lastUpdateTime;
        this.lastUpdateTime = currentTime;

        if (Math.abs(this.targetHealth - this.displayedHealth) < 0.01f) {
            this.displayedHealth = this.targetHealth;
            return;
        }

        float change = (this.targetHealth - this.displayedHealth) * (deltaTime / 1000.0f) * this.animationSpeed;
        this.displayedHealth += change;
    }

    /**
     * Updates the target health and then calculates the animation step.
     * @param newTargetHealth The health value to animate towards.
     */
    public void update(float newTargetHealth) {
        // 先设置新的目标值
        this.targetHealth = newTargetHealth;
        // 然后调用无参数的 update 来执行动画计算
        update();
    }

    // --- 【新增代码块 2】---
    /**
     * 设置动画的目标值，但不立即执行动画计算。
     * @param targetHealth The new target value.
     */
    public void setTargetHealth(float targetHealth) {
        this.targetHealth = targetHealth;
    }

    /**
     * 获取当前动画的目标值。
     * @return The target health value.
     */
    public float getTargetHealth() {
        return this.targetHealth;
    }
    // --- 新增代码块 2 结束 ---

    /**
     * Resets the animator to a new immediate health value.
     * @param newHealth The new health value.
     */
    public void reset(float newHealth) {
        this.displayedHealth = newHealth;
        this.targetHealth = newHealth; // 重置时也要更新目标值
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Gets the current animated health value.
     * @return The smoothly interpolated health value.
     */
    public float getDisplayedHealth() {
        return displayedHealth;
    }
}