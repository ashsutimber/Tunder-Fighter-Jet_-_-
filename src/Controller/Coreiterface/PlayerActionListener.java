package Controller.Coreiterface;

import Entity.Plane.PlayerPlane;
import Controller.CoreController.InputControl;

/**
 * <h1>玩家操作监听接口 — 定义玩家所有可执行操作</h1>
 *
 * <p>本接口定义了玩家在游戏中的全部可执行动作，由 {@link InputControl}
 * 在接收到键盘输入后调用，通过 {@link PlayerPlane} 实现具体行为。</p>
 *
 * <p>调用链路：</p>
 * <pre>
 * 键盘输入 → InputControl (KeyListener) → PlayerActionListener → PlayerPlane
 * </pre>
 *
 * <h2>操作与按键映射</h2>
 * <table border="1">
 *   <tr><th>方法</th><th>按键</th><th>说明</th></tr>
 *   <tr><td>{@link #onMoveUp()}</td><td>{@code ↑ / W}</td><td>飞机向上移动</td></tr>
 *   <tr><td>{@link #onMoveDown()}</td><td>{@code ↓ / S}</td><td>飞机向下移动</td></tr>
 *   <tr><td>{@link #onMoveLeft()}</td><td>{@code ← / A}</td><td>飞机向左移动</td></tr>
 *   <tr><td>{@link #onMoveRight()}</td><td>{@code → / D}</td><td>飞机向右移动</td></tr>
 *   <tr><td>{@link #onShoot()}</td><td>{@code Space}</td><td>发射子弹</td></tr>
 *   <tr><td>{@link #onUseSkill(int)}</td><td>{@code 1 / 2 / 3}</td><td>使用对应技能槽位的技能</td></tr>
 * </table>
 *
 * <p><b>注意：</b>移动方向键支持同时按下多个以实现对角线移动。
 * InputControl 中使用 {@code HashSet<Integer>} 追踪已按下的键，
 * 防止操作系统按键重复事件（key repeat）导致的重复触发。
 * 释放对应方向键时需调用对应的 {@code onStopMoveXxx()} 方法。</p>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see InputControl  键盘输入控制器
 * @see PlayerPlane    玩家飞机（本接口的主要实现类）
 * @see EnemyActionListener  敌机 AI 行为接口（对应的 AI 接口）
 */
public interface PlayerActionListener {

    /**
     * 向上移动 — 对应 {@code ↑} 或 {@code W} 键按下。
     * 持续按住时每帧移动 {@code moveSpeed} px。
     */
    void onMoveUp();

    /**
     * 向下移动 — 对应 {@code ↓} 或 {@code S} 键按下。
     * 持续按住时每帧移动 {@code moveSpeed} px。
     */
    void onMoveDown();

    /**
     * 向左移动 — 对应 {@code ←} 或 {@code A} 键按下。
     * 持续按住时每帧移动 {@code moveSpeed} px。
     */
    void onMoveLeft();

    /**
     * 向右移动 — 对应 {@code →} 或 {@code D} 键按下。
     * 持续按住时每帧移动 {@code moveSpeed} px。
     */
    void onMoveRight();

    /**
     * 发射子弹 — 对应 {@code Space} 键按下。
     * <p>射击受攻击速度（atkSpeed）冷却限制，
     * 每次射击间隔必须 ≥ 当前有效攻击速度。</p>
     * <p>也可通过 {@code J} 键开启自动开火模式，在自动模式下
     * 此方法等效于持续按下状态。</p>
     */
    void onShoot();

    /**
     * 使用指定槽位的技能 — 对应数字键 {@code 1 / 2 / 3}。
     *
     * <p>技能激活的实际执行由 {@code GameEngine.activateSkill()} 处理，
     * 包括 CD 检查、伤害结算、Buff 应用等。本方法仅传递技能索引。</p>
     *
     * @param skillIndex 技能槽位索引（0-2，对应按键 1/2/3）
     *                   超出范围或槽位为空时静默忽略
     */
    void onUseSkill(int skillIndex);
}
