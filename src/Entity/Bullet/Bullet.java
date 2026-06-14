package Entity.Bullet;

import java.awt.Rectangle;
import java.awt.Color;

/**
 * <h1>子弹抽象基类 — 所有子弹类型的通用模板</h1>
 *
 * <p>Bullet 定义了游戏中所有子弹（普通子弹、穿甲弹、导弹、Boss 特殊弹幕）的
 * 通用属性和行为框架。穿甲/暴击修正公式作为静态工具方法提供，供所有子类
 * 及 GameEngine 调用。</p>
 *
 * <h2>子弹类型对比</h2>
 * <table border="1">
 *   <tr><th>属性</th><th>NormalBullet</th><th>ArmorBullet</th><th>MissileBullet</th></tr>
 *   <tr><td>基础伤害</td><td>300</td><td>400</td><td>500</td></tr>
 *   <tr><td>飞行速度</td><td>8.0</td><td>8.0</td><td>6.0</td></tr>
 *   <tr><td>穿甲值</td><td>10</td><td>20</td><td>15</td></tr>
 *   <tr><td>暴击率</td><td>0%</td><td>10%</td><td>20%</td></tr>
 *   <tr><td>弹道</td><td>直线</td><td>直线</td><td>渐进追踪</td></tr>
 *   <tr><td>子弹大小</td><td>5.0</td><td>6.0</td><td>7.0</td></tr>
 * </table>
 *
 * <h2>破甲机制公式</h2>
 * <p>子弹穿甲值 (armorDepth) 与目标护甲值 (armor) 的比值（穿甲比）决定：
 * <ul>
 *   <li><b>伤害修正：</b>{@link #calcDamageModifier(int, int)} — 0.5× ~ 2.0×</li>
 *   <li><b>暴击率修正：</b>{@link #calcCritModifier(int, int)} — 0.2× ~ 2.0×</li>
 * </ul></p>
 *
 * <h2>暴击机制</h2>
 * <p>暴击率判定后，在 [{@code critDamageMin}, {@code critDamageMax}] 范围内
 * 随机选取一个倍率，最终伤害 = 基础伤害 × 暴击倍率。
 * 默认范围 2.0~4.0（即 200%~400% 伤害）。</p>
 *
 * <h2>爆炸弹机制</h2>
 * <p>Boss 2 的大型红弹具有爆炸属性：
 * <ol>
 *   <li>发射 → 移动 bombTimer 秒 → 停下</li>
 *   <li>就位 → 等待 bombFuse 秒 → 引爆</li>
 *   <li>引爆 → bombRadius 范围内造成 bombDamage 范围伤害</li>
 * </ol></p>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see NormalBullet   普通子弹
 * @see ArmorBullet    穿甲弹
 * @see MissileBullet  追踪导弹
 * @see Entity.Plane.Plane  飞机抽象基类（子弹的发射方）
 */
public abstract class Bullet {
    // ── 位置与尺寸 ──
    /** 子弹 X 坐标（中心点） */
    protected float x;

    /** 子弹 Y 坐标（中心点） */
    protected float y;

    /** 速度 X 分量（px/帧），vx≠0 或 vy≠0 时按自定义方向飞行 */
    protected float vx = 0;

    /** 速度 Y 分量（px/帧），默认由 update() 根据归属设置 */
    protected float vy = 0;

    /** 子弹碰撞检测半径（px） */
    protected float bulletSize;

    // ── 攻击属性 ──
    /** 基础伤害值（未经破甲/暴击修正） */
    protected int attack;

    /** 飞行速度标量（px/帧），用在 vx/vy 为 0 时作默认飞行速度 */
    protected float speed;

    /** 弹道轨迹类型（STRAIGHT 直线 / TRACKING 追踪），用于区分弹道逻辑 */
    protected String trajectory;

    /** 穿甲深度 — 数值越高，对高护甲目标越有效 */
    protected int armorDepth;

    // ── 暴击属性 ──
    /** 暴击率（0.0~1.0），如 0.2 = 20% */
    protected float critRate;

    /** 暴击伤害下限倍率（默认 2.0 = 200%） */
    protected float critDamageMin = 2.0f;

    /** 暴击伤害上限倍率（默认 4.0 = 400%） */
    protected float critDamageMax = 4.0f;

    // ── 归属 ──
    /** 是否由玩家发射（true=玩家子弹，false=敌机子弹） */
    protected boolean isPlayerBullet;

    /** 是否存活（命中目标或出界后置 false，等待回收） */
    protected boolean active = true;

    // ── 爆炸弹属性（Boss 2 大型红弹专用） ──
    /** 是否为爆炸弹 */
    protected boolean isBomb = false;

    /** 移动阶段持续时间（秒），到达后停下待引爆 */
    protected float bombTimer = 0;

    /** 就位后引爆延迟（秒） */
    protected float bombFuse = 1.0f;

    /** 爆炸伤害范围半径（px） */
    protected float bombRadius = 80;

    /** 爆炸范围伤害值 */
    protected int bombDamage = 1500;

    /** 是否已引爆 */
    protected boolean hasDetonated = false;

    /** 是否已就位待引爆（移动结束，引信点燃） */
    protected boolean bombArmed = false;

    /** 子弹已存活时间（秒），用于爆炸弹阶段计时 */
    protected float bombLife = 0;

    // ── 外观 ──
    /** 子弹绘制颜色（默认黄色，各子类覆盖） */
    protected Color color = Color.YELLOW;

    /** 空构造方法（供子类使用） */
    public Bullet() {}

    // ── 抽象方法 ──

    /**
     * 每帧更新子弹位置/状态。
     * <p>子类必须实现此方法，定义子弹的飞行逻辑和特殊行为
     * （直线飞行 / 追踪制导 / 爆炸弹阶段切换等）。</p>
     */
    public abstract void update();

    /**
     * 子弹命中目标时的处理逻辑。
     * <p>默认行为：标记子弹为不活跃（等待回收）。
     * 贯穿弹（被动技能"裁决轨迹"）可覆写此方法跳过 inactive 标记。</p>
     */
    public void hit() {
        active = false;     // 命中后消失
    }

    /**
     * 判断子弹是否超出屏幕边界（用于回收）。
     * <p>边界容差：上下 50px，左右 50px，防止子弹在边界突然消失。</p>
     *
     * @return true 表示已出界，应被回收
     */
    public boolean isOutOfBound() {
        // 超出屏幕各方向 50px 容差
        return y < -50 || y > 700 || x < -50 || x > 850;
    }

    // ── 碰撞检测 ──

    /**
     * 获取子弹的碰撞矩形（AABB）。
     * <p>以 (x, y) 为中心，bulletSize 为半径的正方形碰撞体。</p>
     *
     * @return 碰撞矩形（用于 intersects 检测）
     */
    public Rectangle getBounds() {
        return new Rectangle(
            (int)(x - bulletSize), (int)(y - bulletSize),
            (int)(bulletSize * 2), (int)(bulletSize * 2)
        );
    }

    // ═══════════════════════════════════════════════
    // 破甲机制 — 静态工具方法
    // ═══════════════════════════════════════════════

    /**
     * 计算伤害修正系数（基于穿甲比）。
     *
     * <p>穿甲比 = {@code 子弹穿甲值 / 敌机护甲值}</p>
     *
     * <table border="1">
     *   <tr><th>穿甲比范围</th><th>伤害修正</th><th>说明</th></tr>
     *   <tr><td>&gt; 2.0</td><td>2.0 (+100%)</td><td>完全压制</td></tr>
     *   <tr><td>1.6 ~ 2.0</td><td>1.6 (+60%)</td><td>强力穿透</td></tr>
     *   <tr><td>1.3 ~ 1.6</td><td>1.3 (+30%)</td><td>有效穿透</td></tr>
     *   <tr><td>1.0 ~ 1.3</td><td>1.15 (+15%)</td><td>轻微穿透</td></tr>
     *   <tr><td>0.8 ~ 1.0</td><td>0.9 (-10%)</td><td>轻微受阻</td></tr>
     *   <tr><td>0.7 ~ 0.8</td><td>0.8 (-20%)</td><td>中度受阻</td></tr>
     *   <tr><td>0.5 ~ 0.7</td><td>0.7 (-30%)</td><td>严重受阻</td></tr>
     *   <tr><td>&lt; 0.5</td><td>0.5 (-50%)</td><td>几乎无法穿透</td></tr>
     * </table>
     *
     * @param bulletArmorDepth 子弹穿甲值
     * @param enemyArmor       目标护甲值
     * @return 伤害倍率修正系数（最终伤害 = 基础伤害 × 此系数）
     */
    public static double calcDamageModifier(int bulletArmorDepth, int enemyArmor) {
        double ratio = (double) bulletArmorDepth / enemyArmor;

        if (ratio > 2.0)       return 2.0;    // 完全压制：伤害 +100%
        if (ratio > 1.6)       return 1.6;    // 强力穿透：伤害 +60%
        if (ratio > 1.3)       return 1.3;    // 有效穿透：伤害 +30%
        if (ratio > 1.0)       return 1.15;   // 轻微穿透：伤害 +15%
        if (ratio >= 0.8)      return 0.9;    // 轻微受阻：伤害 -10%
        if (ratio >= 0.7)      return 0.8;    // 中度受阻：伤害 -20%
        if (ratio >= 0.5)      return 0.7;    // 严重受阻：伤害 -30%
        return 0.5;                            // 几乎无法穿透：伤害 -50%
    }

    /**
     * 计算暴击率修正系数（基于穿甲比，乘法修正）。
     *
     * <p>最终暴击率 = 子弹暴击率 × 此系数（钳制在 [0, 1]）。</p>
     *
     * <table border="1">
     *   <tr><th>穿甲比范围</th><th>暴击率修正</th><th>说明</th></tr>
     *   <tr><td>&gt; 2.0</td><td>2.0 (+100%)</td><td>完全压制</td></tr>
     *   <tr><td>1.6 ~ 2.0</td><td>1.5 (+50%)</td><td>强力穿透</td></tr>
     *   <tr><td>1.3 ~ 1.6</td><td>1.3 (+30%)</td><td>有效穿透</td></tr>
     *   <tr><td>1.0 ~ 1.3</td><td>1.2 (+20%)</td><td>轻微穿透</td></tr>
     *   <tr><td>0.8 ~ 1.0</td><td>0.8 (-20%)</td><td>轻微受阻</td></tr>
     *   <tr><td>0.7 ~ 0.8</td><td>0.65 (-35%)</td><td>中度受阻</td></tr>
     *   <tr><td>0.5 ~ 0.7</td><td>0.4 (-60%)</td><td>严重受阻</td></tr>
     *   <tr><td>&lt; 0.5</td><td>0.2 (-80%)</td><td>几乎无法穿透</td></tr>
     * </table>
     *
     * <p><b>示例：</b>子弹暴击率 20%，系数 0.2（穿甲不足 -80%）
     * → 最终暴击率 = 20% × 0.2 = 4%。</p>
     *
     * @param bulletArmorDepth 子弹穿甲值
     * @param enemyArmor       目标护甲值
     * @return 暴击率倍率修正系数（乘法）
     */
    public static double calcCritModifier(int bulletArmorDepth, int enemyArmor) {
        double ratio = (double) bulletArmorDepth / enemyArmor;

        if (ratio > 2.0)       return 2.0;   // +100%
        if (ratio > 1.6)       return 1.5;   // +50%
        if (ratio > 1.3)       return 1.3;   // +30%
        if (ratio > 1.0)       return 1.2;   // +20%
        if (ratio >= 0.8)      return 0.8;   // -20%
        if (ratio >= 0.7)      return 0.65;  // -35%
        if (ratio >= 0.5)      return 0.4;   // -60%
        return 0.2;                           // -80%
    }

    // ═══════════════════════════════════════════════
    // 暴击判定
    // ═══════════════════════════════════════════════

    /**
     * 判定是否触发暴击，若触发则返回暴击伤害倍率。
     *
     * <p>暴击判定：{@code Math.random() < effectiveCritRate}</p>
     * <p>暴击倍率：{@code 1.0 + random(critDamageMin, critDamageMax)}</p>
     * <p>未暴击：返回 1.0（伤害不变）</p>
     *
     * <p><b>示例：</b>critDamageMin=2.0, critDamageMax=4.0,
     * 暴击时随机倍率在 [3.0, 5.0] 之间 → 最终伤害 300%~500%。</p>
     *
     * @param effectiveCritRate 经过破甲修正后的最终暴击率（0.0 ~ 1.0）
     * @return 伤害倍率（1.0 = 未暴击，> 1.0 = 暴击倍率）
     */
    public double rollCrit(double effectiveCritRate) {
        if (Math.random() < effectiveCritRate) {
            // 触发暴击：在 [critDamageMin, critDamageMax] 范围内随机
            // 最终倍率 = 1.0 + random(min, max)
            double range = critDamageMax - critDamageMin;
            return 1.0 + critDamageMin + Math.random() * range;
        }
        return 1.0;  // 未暴击
    }

    // ═══════════════════════════════════════════════
    // 一站式伤害计算（破甲 + 暴击）
    // ═══════════════════════════════════════════════

    /**
     * 一站式伤害计算：给定子弹与目标护甲，返回最终伤害值。
     *
     * <p>计算流程：</p>
     * <ol>
     *   <li>计算伤害修正系数（穿甲比 → 伤害倍率）</li>
     *   <li>计算暴击率修正系数（穿甲比 → 暴击率倍率）</li>
     *   <li>修正后暴击率 = clamp(bullet.critRate × critMod, 0, 1)</li>
     *   <li>暴击判定 → 暴击倍率（1.0 ~ 1.0+cirtDamageMax）</li>
     *   <li>最终伤害 = bullet.attack × dmgMod × critMultiplier</li>
     * </ol>
     *
     * <p><b>示例：</b>普通子弹(attack=300, armorDepth=10) vs Level 1 敌机(armor=10)
     * → 穿甲比=1.0 → dmgMod=0.9(-10%), critMod=0.8(-20%)
     * → 暴击率=0%×0.8=0% → 暴击倍率=1.0
     * → 最终伤害 = 300 × 0.9 × 1.0 = 270。</p>
     *
     * @param bullet     子弹实例（提供 attack, armorDepth, critRate）
     * @param enemyArmor 目标护甲值
     * @return 最终伤害值（已计入破甲修正和暴击判定）
     */
    public static int calcFinalDamage(Bullet bullet, int enemyArmor) {
        // 步骤1: 伤害修正（穿甲比 → 伤害倍率）
        double dmgMod = calcDamageModifier(bullet.armorDepth, enemyArmor);
        // 步骤2: 暴击率修正（穿甲比 → 暴击率倍率）
        double critMod = calcCritModifier(bullet.armorDepth, enemyArmor);
        // 步骤3: 修正后暴击率（钳制在 [0, 1]）
        double effectiveCritRate = Math.min(1.0, Math.max(0.0, bullet.critRate * critMod));
        // 步骤4: 暴击判定 → 暴击倍率
        double critMultiplier = bullet.rollCrit(effectiveCritRate);
        // 步骤5: 最终伤害
        return (int)(bullet.attack * dmgMod * critMultiplier);
    }

    // ═══════════════════════════════════════════════
    // Getter / Setter
    // ═══════════════════════════════════════════════

    /** @return 子弹 X 坐标 */
    public float getX() { return x; }
    /** @return 子弹 Y 坐标 */
    public float getY() { return y; }
    /** 设置子弹坐标 */
    public void setPosition(float x, float y) { this.x = x; this.y = y; }
    /** @return 速度 X 分量 */
    public float getVx() { return vx; }
    /** 设置速度 X 分量 */
    public void setVx(float vx) { this.vx = vx; }
    /** @return 速度 Y 分量 */
    public float getVy() { return vy; }
    /** 设置速度 Y 分量 */
    public void setVy(float vy) { this.vy = vy; }

    /**
     * 设置子弹运动方向（弧度制）和速度标量。
     * <p>同时设置 vx = cos(radians)*speed, vy = sin(radians)*speed 和 speed 字段。</p>
     *
     * @param radians 运动方向角度（弧度，0=正右, PI/2=正下, -PI/2=正上）
     * @param speed   速度标量（px/帧）
     */
    public void setDirection(float radians, float speed) {
        this.vx = (float)Math.cos(radians) * speed;
        this.vy = (float)Math.sin(radians) * speed;
        this.speed = speed;
    }

    /** @return 子弹是否存活 */
    public boolean isActive() { return active; }
    /** 设置子弹存活状态 */
    public void setActive(boolean active) { this.active = active; }
    /** @return 是否由玩家发射 */
    public boolean isPlayerBullet() { return isPlayerBullet; }
    /** @return 基础攻击力 */
    public int getAttack() { return attack; }
    /** 设置基础攻击力 */
    public void setAttack(int attack) { this.attack = attack; }
    /** @return 飞行速度标量 */
    public float getSpeed() { return speed; }
    /** 设置飞行速度标量 */
    public void setSpeed(float speed) { this.speed = speed; }
    /** @return 穿甲深度 */
    public int getArmorDepth() { return armorDepth; }
    /** 设置穿甲深度 */
    public void setArmorDepth(int armorDepth) { this.armorDepth = armorDepth; }
    /** @return 暴击率 (0.0~1.0) */
    public float getCritRate() { return critRate; }
    /** 设置暴击率 */
    public void setCritRate(float critRate) { this.critRate = critRate; }
    /** @return 子弹碰撞大小（半径） */
    public float getBulletSize() { return bulletSize; }
    /** 设置子弹碰撞大小 */
    public void setBulletSize(float bulletSize) { this.bulletSize = bulletSize; }
    /** @return 子弹绘制颜色 */
    public Color getColor() { return color; }
    /** 设置子弹颜色 */
    public void setColor(Color color) { this.color = color; }
    /** @return 弹道类型字符串 */
    public String getTrajectory() { return trajectory; }

    // ── 爆炸弹属性 getter/setter ──
    /** @return 是否为爆炸弹 */
    public boolean isBomb() { return isBomb; }
    /** 设置是否为爆炸弹 */
    public void setBomb(boolean bomb) { this.isBomb = bomb; }
    /** @return 移动阶段持续时间 */
    public float getBombTimer() { return bombTimer; }
    /** 设置移动阶段持续时间 */
    public void setBombTimer(float t) { this.bombTimer = t; }
    /** @return 引爆延迟 */
    public float getBombFuse() { return bombFuse; }
    /** 设置引爆延迟 */
    public void setBombFuse(float f) { this.bombFuse = f; }
    /** @return 爆炸范围半径 */
    public float getBombRadius() { return bombRadius; }
    /** 设置爆炸范围半径 */
    public void setBombRadius(float r) { this.bombRadius = r; }
    /** @return 爆炸范围伤害 */
    public int getBombDamage() { return bombDamage; }
    /** 设置爆炸范围伤害 */
    public void setBombDamage(int d) { this.bombDamage = d; }
    /** @return 是否已引爆 */
    public boolean hasDetonated() { return hasDetonated; }
    /** 设置是否已引爆 */
    public void setDetonated(boolean d) { this.hasDetonated = d; }
    /** @return 是否已就位待引爆 */
    public boolean isBombArmed() { return bombArmed; }
    /** 设置是否已就位 */
    public void setBombArmed(boolean a) { this.bombArmed = a; }
    /** @return 子弹已存活时间 */
    public float getBombLife() { return bombLife; }
}
