package Controller.Coreiterface;

import Entity.Plane.EnemyPlane;
import Entity.Bullet.Bullet;

/**
 * <h1>敌机行为监听接口 — 定义敌机 AI 的所有决策行为</h1>
 *
 * <p>本接口定义了敌机的 AI 行为回调，由游戏引擎（GameEngine）在每帧
 * 调用以驱动敌机的自主行动。与 {@link PlayerActionListener} 不同，
 * 敌机行为由 AI 逻辑决定而非键盘输入。</p>
 *
 * <p>调用链路：</p>
 * <pre>
 * GameEngine.update()
 *   └─ EnemyPlane.move()       ← 内部调用 onUpdatePosition 逻辑
 *   └─ EnemyPlane.tryShoot()   ← 内部调用 onDecideShoot 逻辑
 *   └─ EnemyPlane.takeDamage() → 死亡时调用 onDeath 逻辑
 * </pre>
 *
 * <h2>敌机 AI 设计</h2>
 * <p>当前实现中，敌机的 AI 逻辑直接内嵌在 {@link EnemyPlane} 类中
 * （如移动模式枚举 {@code MovePattern} 和射击决策），
 * 本接口定义了标准化的行为回调签名，便于未来扩展为独立的 AI 策略类。</p>
 *
 * <h2>支持的移动模式</h2>
 * <ul>
 *   <li><b>STRAIGHT</b> — 直线下降</li>
 *   <li><b>SINE</b> — 正弦波左右摇摆</li>
 *   <li><b>ZIGZAG</b> — 折线移动</li>
 *   <li><b>CHASE</b> — 追踪摇摆（Level 4）</li>
 *   <li><b>BOSS_SWAY</b> — Boss 周期性 X 轴冲刺</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see EnemyPlane  敌机实体（当前 AI 行为的主要承载类）
 * @see PlayerActionListener  玩家操作接口（对应的玩家接口）
 */
public interface EnemyActionListener {

    /**
     * AI 路径计算 — 每帧更新敌机位置。
     *
     * <p>不同移动模式的实现：</p>
     * <ul>
     *   <li>STRAIGHT: {@code y += moveSpeed}</li>
     *   <li>SINE:    {@code x = initialX + sin(timer) * amplitude; y += moveSpeed}</li>
     *   <li>ZIGZAG:  在正弦波正负半周期分别向左右平移</li>
     *   <li>CHASE:   横向追踪玩家 + 缓降</li>
     *   <li>BOSS_SWAY: 周期性 X 轴冲刺（5~10s 间隔）+ 慢速漂移</li>
     * </ul>
     *
     * @param plane 要更新位置的敌机实例
     */
    void onUpdatePosition(EnemyPlane plane);

    /**
     * AI 射击决策 — 判断是否发射子弹及子弹类型。
     *
     * <p>根据敌机等级发射不同弹幕：</p>
     * <ul>
     *   <li>Level 1: 单发直射（正下方）</li>
     *   <li>Level 2: 2 路扩散（±10°）</li>
     *   <li>Level 3: 3 路扇形（±15°）</li>
     *   <li>Level 4: 3 路 + 中心穿甲弹（伤害 ×1.5）</li>
     *   <li>Boss: 扇形弹幕 + 环形弹幕 + 旋转扫射 + 爆炸弹 + 巨型蓝弹</li>
     * </ul>
     *
     * <p>射击受 {@code atkSpeed} 冷却限制，由 {@link EnemyPlane#canShoot(float)} 检查。</p>
     *
     * @param plane 要决策射击的敌机实例
     */
    void onDecideShoot(EnemyPlane plane);

    /**
     * 敌机死亡处理 — 在被击毁时触发。
     *
     * <p>执行逻辑包括：</p>
     * <ul>
     *   <li>统计得分（scoreValue）</li>
     *   <li>发放经验值（xpValue）</li>
     *   <li>概率掉落 Buff 球（buffDropRate）</li>
     *   <li>播放爆炸特效</li>
     *   <li>触发相关游戏事件（ENEMY_DIED / BOSS_DEFEATED 等）</li>
     * </ul>
     *
     * @param plane 被击毁的敌机实例
     */
    void onDeath(EnemyPlane plane);
}
