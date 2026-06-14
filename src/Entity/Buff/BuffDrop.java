package Entity.Buff;

import Entity.Plane.PlayerPlane;
import java.awt.Color;
import java.awt.Rectangle;

/**
 * <h1>Buff 掉落球 — 敌机击杀后的可拾取增益道具</h1>
 *
 * <p>敌机被击毁时，根据其 {@code buffDropRate} 掉落率概率生成一个 Buff 球。
 * Buff 球以随机速度（3~5 px/帧）自动直线向下掉落，被玩家飞机碰撞后拾取，
 * Buff 效果即刻应用到玩家。超出屏幕底部后自动消失回收。</p>
 *
 * <h2>掉落机制</h2>
 * <p>每次击杀敌机时通过 {@code Math.random() < buffDropRate} 判定是否掉落，
 * 再通过随机数决定 Buff 类型（40% 治疗 / 35% 护盾 / 25% 火力）。</p>
 *
 * <h2>视觉设计</h2>
 * <table border="1">
 *   <tr><th>Buff 类型</th><th>颜色</th><th>RGB</th><th>发光光晕</th></tr>
 *   <tr><td>治疗 (+800 HP)</td><td>🔴 红色</td><td>(255, 50, 50)</td><td>半透明红色</td></tr>
 *   <tr><td>护盾 (+300 护盾)</td><td>🔵 蓝色</td><td>(50, 100, 255)</td><td>半透明蓝色</td></tr>
 *   <tr><td>火力 (速度+100%)</td><td>🟡 黄色</td><td>(255, 220, 50)</td><td>半透明黄色</td></tr>
 * </table>
 *
 * <h2>碰撞检测</h2>
 * <p>使用 {@link Rectangle#intersects(Rectangle)} 检测 Buff 球与玩家飞机的碰撞。
 * 拾取后球标记为 inactive，在下一帧被清理。</p>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see Buff       关联的 Buff 效果数据
 * @see PlayerPlane 玩家飞机（拾取方）
 */
public class BuffDrop {

    /** Buff 球中心 X 坐标 */
    private float x;

    /** Buff 球中心 Y 坐标 */
    private float y;

    /** 下落速度（px/帧），范围 3~5，随机生成 */
    private float speed;

    /** 球的绘制/碰撞半径（px） */
    private float radius = 10.0f;

    /** 关联的 Buff 效果（拾取后通过 {@link Buff#apply} 生效） */
    private Buff buff;

    /** 球的颜色（根据 Buff 类型决定） */
    private Color color;

    /** 是否仍活跃（拾取或出界后设为 false） */
    private boolean active = true;

    /** 屏幕高度边界（超出此值标记为不活跃，留 20px 余量） */
    private static final float SCREEN_HEIGHT = 620;

    /**
     * 构造一个 Buff 掉落球。
     *
     * <p>下落速度在 3~5 px/帧之间随机，
     * 球的颜色根据 {@link Buff#getType()} 自动选择：</p>
     * <ul>
     *   <li>{@code HEAL} → 红色 (255, 50, 50)</li>
     *   <li>{@code SHIELD} → 蓝色 (50, 100, 255)</li>
     *   <li>{@code FIRE} → 黄色 (255, 220, 50)</li>
     * </ul>
     *
     * @param x    初始 X 坐标（通常为被击毁敌机的位置）
     * @param y    初始 Y 坐标
     * @param buff 关联的 Buff 对象（通过 {@link Buff} 工厂方法创建）
     */
    public BuffDrop(float x, float y, Buff buff) {
        this.x = x;
        this.y = y;
        this.buff = buff;

        // 随机下落速度：3~5 px/帧
        this.speed = 3.0f + (float) Math.random() * 2.0f;

        // 根据 Buff 类型确定球的颜色
        switch (buff.getType()) {
            case HEAL:
                this.color = new Color(255, 50, 50);    // 红色 — 治疗
                break;
            case SHIELD:
                this.color = new Color(50, 100, 255);    // 蓝色 — 护盾
                break;
            case FIRE:
                this.color = new Color(255, 220, 50);    // 黄色 — 火力
                break;
            default:
                this.color = Color.WHITE;                // 兜底白色
                break;
        }
    }

    // ═══════════════════════════════════════════════
    // 更新与碰撞
    // ═══════════════════════════════════════════════

    /**
     * 每帧更新 Buff 球位置（向下移动）。
     * <p>由 GameEngine 每帧调用。超出屏幕底部时自动标记为 inactive。</p>
     */
    public void update() {
        y += speed;                     // 直线向下移动
        if (y - radius > SCREEN_HEIGHT) {
            active = false;             // 超出屏幕，标记回收
        }
    }

    /**
     * 判断 Buff 球是否仍在屏幕内活跃（未被拾取且未出界）。
     * @return true 表示仍可被拾取
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 获取碰撞检测用的矩形边界。
     * <p>中心点向外扩展 radius 形成正方形碰撞体。</p>
     * @return 碰撞矩形（用于 {@link Rectangle#intersects(Rectangle)} 检测）
     */
    public Rectangle getBounds() {
        return new Rectangle(
            (int) (x - radius), (int) (y - radius),
            (int) (radius * 2), (int) (radius * 2)
        );
    }

    /**
     * 检测是否与玩家飞机碰撞（拾取判定）。
     * <p>使用 AABB 矩形相交检测。</p>
     *
     * @param player 玩家飞机实例
     * @return true 表示发生碰撞，应被拾取
     */
    public boolean collidesWith(PlayerPlane player) {
        return getBounds().intersects(player.getBounds());
    }

    // ═══════════════════════════════════════════════
    // Getter / Setter
    // ═══════════════════════════════════════════════

    /** @return Buff 球中心 X 坐标 */
    public float getX() { return x; }

    /** @return Buff 球中心 Y 坐标 */
    public float getY() { return y; }

    /** @return 球的绘制/碰撞半径 */
    public float getRadius() { return radius; }

    /** @return 球的绘制颜色（红/蓝/黄） */
    public Color getColor() { return color; }

    /** @return 关联的 Buff 效果数据 */
    public Buff getBuff() { return buff; }

    /** 设置球的活跃状态（拾取/出界时置 false） */
    public void setActive(boolean active) { this.active = active; }
}
