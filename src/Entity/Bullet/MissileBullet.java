package Entity.Bullet;

import java.awt.Color;
import Entity.Plane.EnemyPlane;
import java.util.List;

/**
 * <h1>导弹（MissileBullet）— 渐进追踪制导子弹</h1>
 *
 * <p>继承自 {@link Bullet}，具有制导追踪能力。发射后初速度为垂直向上，
 * 随后每帧通过渐进调整速度方向（vx, vy）逐步转向最近敌机，
 * 形成弧线追踪轨迹，而非瞬间改变飞行方向。</p>
 *
 * <h2>初始属性</h2>
 * <table border="1">
 *   <tr><th>属性</th><th>值</th><th>说明</th></tr>
 *   <tr><td>基础伤害</td><td>500</td><td>三种子弹中最高</td></tr>
 *   <tr><td>飞行速度</td><td>6.0 px/帧</td><td>比直线子弹慢（8.0），但可追踪</td></tr>
 *   <tr><td>穿甲值</td><td>15</td><td>介於普通（10）与穿甲（20）之间</td></tr>
 *   <tr><td>暴击率</td><td>20%</td><td>三种子弹中最高</td></tr>
 *   <tr><td>暴击伤害范围</td><td>200%~400%</td><td>与所有子弹相同</td></tr>
 *   <tr><td>子弹大小</td><td>7.0</td><td>三种子弹中最大</td></tr>
 *   <tr><td>弹道类型</td><td>TRACKING（追踪）</td><td>渐进弧线制导</td></tr>
 *   <tr><td>转向速率</td><td>1.5°/帧（~0.026 rad/帧）</td><td>限制单帧最大转向角度</td></tr>
 * </table>
 *
 * <h2>制导机制详解</h2>
 * <p>导弹每帧执行以下步骤：</p>
 * <ol>
 *   <li>从敌机列表中寻找距离最近的存活敌机</li>
 *   <li>计算当前速度方向（atan2(vy, vx)）与期望方向（指向目标）的角度差</li>
 *   <li>将角度差钳制到 {@code TURN_RATE} 范围内（防止瞬间转向）</li>
 *   <li>更新 vx/vy = (cos(newAngle) * speed, sin(newAngle) * speed)</li>
 *   <li>按新速度移动</li>
 * </ol>
 *
 * <h2>获取方式</h2>
 * <ul>
 *   <li>初始选择：选择飞机4（追猎）时初始装备</li>
 *   <li>升级卡 W7（暗能追猎）：子弹变更为导弹</li>
 * </ul>
 *
 * <h2>外观</h2>
 * <ul>
 *   <li>玩家导弹：洋红色 (Magenta)</li>
 *   <li>敌机导弹：粉红色 (255, 50, 150)</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see NormalBullet  普通子弹（直线、无追踪）
 * @see ArmorBullet   穿甲弹（直线、高穿甲）
 * @see EnemyPlane    敌机实体（追踪目标）
 */
public class MissileBullet extends Bullet {

    /** 敌机列表引用 — 导弹每帧从中寻找最近目标 */
    private List<EnemyPlane> enemiesRef;

    /** 最大转向速率（弧度/帧）：限制导弹每帧能调整的最大角度，
     *  防止瞬间转向，形成平滑的弧线追踪轨迹。
     *  TURN_RATE = 1.5° / 60FPS ≈ 0.026 rad/帧 */
    private static final float TURN_RATE = 1.5f / 60f;

    /**
     * 构造追踪导弹。
     *
     * <p>初始速度方向未设置（vx=vy=0），首次 update() 时
     * 若 vx/vy 仍为 0 则默认向上发射（vy = -speed）。</p>
     *
     * <p>颜色区分：</p>
     * <ul>
     *   <li>玩家：洋红色 (Magenta) — 鲜明易识别</li>
     *   <li>敌机：粉红色 (255, 50, 150) — 高威胁警示</li>
     * </ul>
     *
     * @param x              初始 X 坐标（中心点）
     * @param y              初始 Y 坐标（中心点）
     * @param isPlayerBullet true 表示由玩家发射
     */
    public MissileBullet(float x, float y, boolean isPlayerBullet) {
        this.x = x;
        this.y = y;
        this.attack = 500;           // 基础伤害（三种子弹中最高）
        this.speed = 6.0f;           // 飞行速度（略慢于直线子弹）
        this.armorDepth = 15;        // 穿甲深度（中等水平）
        this.critRate = 0.20f;       // 初始暴击率 20%（最高）
        this.critDamageMin = 2.0f;
        this.critDamageMax = 4.0f;
        this.bulletSize = 7.0f;      // 子弹碰撞大小（最大）
        this.isPlayerBullet = isPlayerBullet;
        this.trajectory = "TRACKING"; // 追踪弹道
        // 玩家洋红色 / 敌机粉红色
        this.color = isPlayerBullet ? Color.MAGENTA : new Color(255, 50, 150);
    }

    /**
     * 设置敌机列表引用（由 GameEngine 在创建子弹副本时调用）。
     * <p>导弹每帧从此列表中寻找最近目标进行追踪。</p>
     *
     * @param enemies 当前场景中所有存活敌机的列表（GameEngine.enemies）
     */
    public void acquireTarget(List<EnemyPlane> enemies) {
        this.enemiesRef = enemies;
    }

    /**
     * 从敌机列表中寻找距离最近的存活敌机（欧几里得距离平方最小）。
     * <p>距离平方比较避免开方运算，提高每帧寻敌性能。</p>
     *
     * @return 最近的存活敌机；若列表为空或无存活敌机则返回 null
     */
    private EnemyPlane findNearestEnemy() {
        if (enemiesRef == null || enemiesRef.isEmpty()) return null;

        EnemyPlane nearest = null;
        float minDistSq = Float.MAX_VALUE;

        for (EnemyPlane e : enemiesRef) {
            if (!e.isAlive()) continue;     // 跳过已死亡敌机

            float dx = e.getX() - x;
            float dy = e.getY() - y;
            float distSq = dx * dx + dy * dy; // 距离平方（避免 sqrt）

            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearest = e;
            }
        }
        return nearest;
    }

    /**
     * 每帧更新导弹位置 — 渐进追踪制导。
     *
     * <h3>制导算法流程</h3>
     * <ol>
     *   <li><b>初速度初始化：</b>若 vx=vy=0（首次更新），默认向上发射</li>
     *   <li><b>寻找目标：</b>遍历敌机列表，找距离最近的存活敌机</li>
     *   <li><b>计算角度差：</b>期望方向（指向目标）- 当前速度方向</li>
     *   <li><b>角度差归一化：</b>限定在 [-PI, PI] 范围内</li>
     *   <li><b>转向钳制：</b>单帧角度变化不超过 TURN_RATE</li>
     *   <li><b>更新方向：</b>新角度 = 当前角度 + 钳制后角度差</li>
     *   <li><b>移动：</b>按新速度分量移动</li>
     * </ol>
     *
     * <p>无目标时保持当前方向惯性飞行（不强制向上/下）。</p>
     */
    @Override
    public void update() {
        if (isPlayerBullet && enemiesRef != null) {
            // ── 初速度初始化 ──
            // 若 vx/vy 未被外部设置（如扇形散射），则默认向上发射
            if (vx == 0 && vy == 0) {
                vy = -speed;    // 初速度：正上方
            }

            // ── 渐进制导：每帧寻找最近敌机，逐步调整速度方向 ──
            EnemyPlane nearest = findNearestEnemy();
            if (nearest != null) {
                // 期望方向：从导弹当前位置指向目标位置
                float desiredAngle = (float) Math.atan2(nearest.getY() - y, nearest.getX() - x);

                // 当前速度方向
                float currentAngle = (float) Math.atan2(vy, vx);

                // 计算角度差并归一化到 [-PI, PI]
                float angleDiff = desiredAngle - currentAngle;
                while (angleDiff > Math.PI)  angleDiff -= (float)(2 * Math.PI);
                while (angleDiff < -Math.PI) angleDiff += (float)(2 * Math.PI);

                // 限制单帧最大转向角度（防止瞬间"蛇形"转向）
                if (angleDiff > TURN_RATE)       angleDiff = TURN_RATE;
                else if (angleDiff < -TURN_RATE) angleDiff = -TURN_RATE;

                // 更新速度方向（速度标量不变，仅改变方向）
                float newAngle = currentAngle + angleDiff;
                vx = (float) Math.cos(newAngle) * speed;
                vy = (float) Math.sin(newAngle) * speed;
            }

            // 按当前速度移动（有目标时已调整方向，无目标时保持惯性）
            x += vx;
            y += vy;
        } else if (isPlayerBullet) {
            // 无目标列表时：默认直线向上（兼容旧逻辑）
            y -= speed;
        } else {
            // 敌机导弹：向下飞行
            y += speed;
        }

        // 超出屏幕边界则标记为不活跃
        if (isOutOfBound()) {
            active = false;
        }
    }
}
