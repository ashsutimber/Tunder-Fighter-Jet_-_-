package Entity.Bullet;

import java.awt.Color;

/**
 * <h1>穿甲弹（ArmorBullet）— 高穿甲直线子弹</h1>
 *
 * <p>继承自 {@link Bullet}，是一种具有较高初始穿甲值（20）的子弹类型。
 * 主要用于对抗高护甲敌机（Level 3/4 和 Boss），能够有效穿透护甲
 * 获得伤害和暴击率加成。</p>
 *
 * <h2>初始属性</h2>
 * <table border="1">
 *   <tr><th>属性</th><th>值</th><th>说明</th></tr>
 *   <tr><td>基础伤害</td><td>400</td><td>比普通子弹高 33%</td></tr>
 *   <tr><td>飞行速度</td><td>8.0 px/帧</td><td>与普通子弹相同</td></tr>
 *   <tr><td>穿甲值</td><td>20</td><td>普通子弹（10）的 2 倍</td></tr>
 *   <tr><td>暴击率</td><td>10%</td><td>初始即有暴击能力</td></tr>
 *   <tr><td>暴击伤害范围</td><td>200%~400%</td><td>与所有子弹相同</td></tr>
 *   <tr><td>子弹大小</td><td>6.0</td><td>比普通子弹（5.0）略大</td></tr>
 *   <tr><td>弹道类型</td><td>STRAIGHT（直线）</td><td>默认直线飞行</td></tr>
 * </table>
 *
 * <h2>获取方式</h2>
 * <ul>
 *   <li>初始选择：选择飞机3（破甲）时初始装备</li>
 *   <li>升级卡 W7（暗能追猎）：切换为导弹，不再是穿甲弹</li>
 * </ul>
 *
 * <h2>外观</h2>
 * <ul>
 *   <li>玩家穿甲弹：橙色 (255, 165, 0)</li>
 *   <li>敌机穿甲弹：红色 (255, 80, 80)</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see NormalBullet    普通子弹（低穿甲、零暴击）
 * @see MissileBullet   追踪导弹（追踪弹道、高暴击）
 * @see Bullet#calcFinalDamage(Bullet, int)  破甲+暴击伤害计算
 */
public class ArmorBullet extends Bullet {

    /**
     * 构造穿甲弹。
     *
     * <p>玩家穿甲弹向上飞行（{@code vy = -speed}），
     * 敌机穿甲弹向下飞行（{@code vy = +speed}）。</p>
     *
     * <p>颜色区分：</p>
     * <ul>
     *   <li>玩家：橙色 (255, 165, 0) — HUD 中易识别</li>
     *   <li>敌机：红色 (255, 80, 80) — 危险警示色</li>
     * </ul>
     *
     * @param x              初始 X 坐标（中心点）
     * @param y              初始 Y 坐标（中心点）
     * @param isPlayerBullet true 表示由玩家发射（向上），false 表示敌机发射（向下）
     */
    public ArmorBullet(float x, float y, boolean isPlayerBullet) {
        this.x = x;
        this.y = y;
        this.attack = 400;           // 基础伤害（比普通子弹高 33%）
        this.speed = 8.0f;           // 飞行速度
        this.armorDepth = 20;        // 穿甲深度（普通子弹的 2 倍）
        this.critRate = 0.10f;       // 初始暴击率 10%
        this.critDamageMin = 2.0f;   // 暴击伤害下限 200%
        this.critDamageMax = 4.0f;   // 暴击伤害上限 400%
        this.bulletSize = 6.0f;      // 子弹碰撞大小
        this.isPlayerBullet = isPlayerBullet;
        this.trajectory = "STRAIGHT"; // 直线弹道
        // 玩家橙色 / 敌机红色
        this.color = isPlayerBullet ? new Color(255, 165, 0) : new Color(255, 80, 80);
    }

    /**
     * 每帧更新子弹位置。
     *
     * <p>飞行逻辑：</p>
     * <ol>
     *   <li>如果 vx/vy 被外部设置（如扇形散射），按自定义方向飞行</li>
     *   <li>否则按归属方向飞行：玩家子弹向上（y -= speed），敌机子弹向下（y += speed）</li>
     *   <li>检测边界：超出屏幕时标记 inactive</li>
     * </ol>
     */
    @Override
    public void update() {
        // 自定义方向优先（vx/vy 非零时表示已设置特定飞行方向）
        if (vx != 0 || vy != 0) {
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
