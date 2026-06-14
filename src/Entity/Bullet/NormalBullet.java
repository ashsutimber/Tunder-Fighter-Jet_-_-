package Entity.Bullet;

import java.awt.Color;

/**
 * <h1>普通子弹（NormalBullet）— 基础直线子弹</h1>
 *
 * <p>继承自 {@link Bullet}，是最基础的子弹类型。直线飞行、零暴击率、
 * 低穿甲值。所有玩家初始装备普通子弹（飞机3除外），
 * 通过升级卡可以切换为其他子弹类型或增强属性。</p>
 *
 * <h2>初始属性</h2>
 * <table border="1">
 *   <tr><th>属性</th><th>值</th><th>说明</th></tr>
 *   <tr><td>基础伤害</td><td>300</td><td>三种子弹中最低</td></tr>
 *   <tr><td>飞行速度</td><td>8.0 px/帧</td><td>与穿甲弹相同</td></tr>
 *   <tr><td>穿甲值</td><td>10</td><td>仅能有效穿透低护甲目标</td></tr>
 *   <tr><td>暴击率</td><td>0%</td><td>初始无暴击能力</td></tr>
 *   <tr><td>暴击伤害范围</td><td>200%~400%</td><td>与所有子弹相同（因暴击率 0% 不生效）</td></tr>
 *   <tr><td>子弹大小</td><td>5.0</td><td>三种子弹中最小</td></tr>
 *   <tr><td>弹道类型</td><td>STRAIGHT（直线）</td><td>默认直线飞行</td></tr>
 * </table>
 *
 * <h2>特殊机制 — 爆炸弹模式</h2>
 * <p>敌机（Boss 2）的普通子弹可作为爆炸弹使用：{@code isBomb = true} 时，
 * 子弹先移动一段距离（bombTimer），随后停下待引爆（bombFuse），
 * 引爆后对爆炸范围内的玩家造成范围伤害（bombDamage）。</p>
 *
 * <h2>外观</h2>
 * <ul>
 *   <li>玩家普通子弹：青色 (Cyan)</li>
 *   <li>敌机普通子弹：浅红色 (255, 100, 100)</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see ArmorBullet    穿甲弹（高穿甲、有暴击）
 * @see MissileBullet  追踪导弹（追踪弹道、高暴击）
 * @see Bullet#calcFinalDamage(Bullet, int)  破甲+暴击伤害计算
 */
public class NormalBullet extends Bullet {

    /**
     * 构造普通子弹。
     *
     * <p>玩家普通子弹向上飞行（{@code vy = -speed}），
     * 敌机普通子弹向下飞行（{@code vy = +speed}）。</p>
     *
     * <p>颜色区分：</p>
     * <ul>
     *   <li>玩家：青色 (Cyan) — 明亮易识别</li>
     *   <li>敌机：浅红色 (255, 100, 100) — 危险警示色调</li>
     * </ul>
     *
     * @param x              初始 X 坐标（中心点）
     * @param y              初始 Y 坐标（中心点）
     * @param isPlayerBullet true 表示由玩家发射（向上），false 表示敌机发射（向下）
     */
    public NormalBullet(float x, float y, boolean isPlayerBullet) {
        this.x = x;
        this.y = y;
        this.attack = 300;           // 基础伤害
        this.speed = 8.0f;           // 飞行速度
        this.armorDepth = 10;        // 穿甲深度（三种子弹中最低）
        this.critRate = 0.0f;        // 初始无暴击
        this.critDamageMin = 2.0f;
        this.critDamageMax = 4.0f;
        this.bulletSize = 5.0f;      // 子弹碰撞大小（最小）
        this.isPlayerBullet = isPlayerBullet;
        this.trajectory = "STRAIGHT"; // 直线弹道
        // 玩家青色 / 敌机浅红色
        this.color = isPlayerBullet ? Color.CYAN : new Color(255, 100, 100);
    }

    /**
     * 每帧更新子弹位置/状态。
     *
     * <h3>爆炸弹模式</h3>
     * <p>当 {@code isBomb == true} 时（Boss 2 大型红弹）：</p>
     * <ol>
     *   <li><b>移动阶段：</b>子弹飞至预设位置（bombTimer 毫秒），期间沿设定方向慢速移动</li>
     *   <li><b>就位阶段：</b>到达位置后停下（vx=vy=0），设置 bombArmed=true</li>
     *   <li><b>引爆阶段：</b>就位后等待 bombFuse 秒后引爆，设置 hasDetonated=true</li>
     * </ol>
     *
     * <h3>普通弹道</h3>
     * <ol>
     *   <li>如果 vx/vy 被外部设置（如扇形散射），按自定义方向飞行</li>
     *   <li>否则按归属方向飞行：玩家子弹向上（y -= speed），敌机子弹向下（y += speed）</li>
     *   <li>检测边界：超出屏幕时标记 inactive</li>
     * </ol>
     */
    @Override
    public void update() {
        // 每帧累计存活时间（爆炸弹阶段计时用）
        bombLife += 1.0f / 60.0f;

        // ── 爆炸弹逻辑：移动 → 停下 → 待引爆 → 引爆 ──
        if (isBomb) {
            // 阶段1: 移动阶段 — 到达预设位置前
            if (!bombArmed && bombLife >= bombTimer) {
                // 到达位置，停下准备引爆
                bombArmed = true;
                vx = 0;
                vy = 0;     // 停止移动
            }
            // 阶段2: 待引爆 — 就位后倒计时
            if (bombArmed && bombLife >= bombTimer + bombFuse) {
                // 引信到期，引爆！
                hasDetonated = true;
                active = false;     // 爆炸后子弹消失
                return;
            }
            // 移动阶段：还未到达位置，继续按设定方向飞行
            if (!bombArmed) {
                if (vx != 0 || vy != 0) {
                    x += vx;
                    y += vy;        // 按自定义方向
                } else {
                    y += speed * 0.5f;  // 默认慢速向下
                }
            }
            return; // 爆炸弹模式结束（待引爆时不移动）
        }

        // ── 普通弹道逻辑 ──
        if (vx != 0 || vy != 0) {
            // 自定义方向优先（扇形散射/角度弹等）
            x += vx;
            y += vy;
        } else if (isPlayerBullet) {
            y -= speed;     // 玩家子弹：向上飞行
        } else {
            y += speed;     // 敌机子弹：向下飞行
        }

        // 超出屏幕边界则标记为不活跃（等待回收）
        if (isOutOfBound()) {
            active = false;
        }
    }
}
