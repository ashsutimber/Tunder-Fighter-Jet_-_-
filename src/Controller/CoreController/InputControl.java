package Controller.CoreController;

import Controller.Coreiterface.*;
import Entity.Plane.*;
import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;

/**
 * <h1>输入控制器（InputControl）— 键盘事件 → 游戏操作</h1>
 *
 * <p>InputControl 实现 {@link KeyListener} 接口，负责监听 JFrame 上的
 * 键盘事件，并将按键状态转化为对玩家飞机（{@link PlayerPlane}）的操作指令。
 * 它是玩家与游戏之间的桥梁。</p>
 *
 * <h2>键盘映射表</h2>
 * <table border="1">
 *   <tr><th>按键</th><th>操作</th><th>调用的方法</th></tr>
 *   <tr><td>{@code ↑ / W}</td><td>向上移动</td><td>{@link PlayerPlane#onMoveUp()}</td></tr>
 *   <tr><td>{@code ↓ / S}</td><td>向下移动</td><td>{@link PlayerPlane#onMoveDown()}</td></tr>
 *   <tr><td>{@code ← / A}</td><td>向左移动</td><td>{@link PlayerPlane#onMoveLeft()}</td></tr>
 *   <tr><td>{@code → / D}</td><td>向右移动</td><td>{@link PlayerPlane#onMoveRight()}</td></tr>
 *   <tr><td>{@code Space}</td><td>发射子弹</td><td>{@link PlayerPlane#onShoot()}</td></tr>
 *   <tr><td>{@code J}</td><td>切换自动开火</td><td>{@link PlayerPlane#toggleAutoFire()}</td></tr>
 *   <tr><td>{@code 1 / 2 / 3}</td><td>使用技能 1/2/3</td><td>{@code engine.activateSkill(0/1/2)}</td></tr>
 *   <tr><td>{@code ESC}</td><td>暂停/恢复游戏</td><td>{@code engine.togglePause()}</td></tr>
 * </table>
 *
 * <h2>按键防重复机制</h2>
 * <p>操作系统会对持续按下的键产生重复的 keyPressed 事件（key repeat）。
 * 为正确处理，使用 {@code HashSet<Integer> pressedKeys} 追踪当前按下的键集合：</p>
 * <ul>
 *   <li>{@code keyPressed} — 仅在键首次进入 pressedKeys 时处理，忽略重复事件</li>
 *   <li>{@code keyReleased} — 从 pressedKeys 中移除，调用对应的 onStop 方法</li>
 * </ul>
 *
 * <h2>调用链路</h2>
 * <pre>
 * 用户按键 → JFrame KeyEvent
 *   → InputControl.keyPressed()
 *     → PlayerPlane.onMoveUp/Down/Left/Right/Shoot()
 *     → GameEngine.activateSkill() / togglePause()
 * </pre>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see PlayerPlane  玩家飞机（接收操作指令）
 * @see GameEngine   游戏引擎（处理技能激活/暂停等）
 * @see PlayerActionListener  玩家操作监听接口
 */
public class InputControl implements KeyListener {

    /** 关联的玩家飞机 — 键盘输入转化为对此飞机的操作指令 */
    private PlayerPlane playerPlane;

    /** 关联的游戏引擎 — 处理技能激活（activateSkill）和暂停（togglePause） */
    private GameEngine engine;

    /**
     * 当前按下的键码集合 — 用于防重复触发。
     * <p>操作系统 key repeat 机制会反复触发 keyPressed，
     * 通过此集合确保每个按键只在第一次按下时处理一次。</p>
     */
    private final Set<Integer> pressedKeys = new HashSet<>();

    /**
     * 构造输入控制器。
     * @param player 关联的玩家飞机实例
     */
    public InputControl(PlayerPlane player) {
        this.playerPlane = player;
    }

    /**
     * 设置关联的游戏引擎引用（处理技能激活和暂停）。
     * <p>由于 GameEngine 在 InputControl 之后创建，需要在后续注入。</p>
     *
     * @param engine 游戏引擎实例
     */
    public void setEngine(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * 向 JFrame 注册键盘事件监听。
     *
     * <p>将 InputControl 自身添加为 JFrame 的 KeyListener，
     * 并确保窗口获得焦点以接收按键事件。</p>
     *
     * @param frame 游戏主窗口
     */
    public void registerKeyListeners(JFrame frame) {
        frame.addKeyListener(this);     // 注册自身为监听器
        frame.setFocusable(true);
        frame.requestFocusInWindow();   // 确保窗口获得焦点
    }

    /**
     * 处理按键按下事件。
     *
     * <p>通过 {@code pressedKeys} 防重复触发：
     * 相同按键已存在时直接返回，否则加入集合并执行对应操作。</p>
     *
     * @param e 按键事件
     */
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        // 防重复触发：已按下的键忽略 key repeat 事件
        if (pressedKeys.contains(code)) return;
        pressedKeys.add(code);

        switch (code) {
            // ── 移动：方向键 & WASD ──
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                playerPlane.onMoveUp();
                break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                playerPlane.onMoveDown();
                break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                playerPlane.onMoveLeft();
                break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                playerPlane.onMoveRight();
                break;

            // ── 射击：空格键 ──
            case KeyEvent.VK_SPACE:
                playerPlane.onShoot();
                break;

            // ── 自动开火：J 键 ──
            case KeyEvent.VK_J:
                playerPlane.toggleAutoFire();   // 切换开关
                break;

            // ── 技能：数字键 1/2/3 ──
            case KeyEvent.VK_1:
                playerPlane.onUseSkill(0);
                if (engine != null) engine.activateSkill(0);
                break;
            case KeyEvent.VK_2:
                playerPlane.onUseSkill(1);
                if (engine != null) engine.activateSkill(1);
                break;
            case KeyEvent.VK_3:
                playerPlane.onUseSkill(2);
                if (engine != null) engine.activateSkill(2);
                break;

            // ── 暂停/恢复：ESC 键 ──
            case KeyEvent.VK_ESCAPE:
                if (engine != null) engine.togglePause();
                break;
        }
    }

    /**
     * 处理按键释放事件。
     *
     * <p>从 pressedKeys 中移除键码，并调用对应的 onStop 方法
     * 通知 PlayerPlane 停止该方向移动或停止射击。</p>
     *
     * @param e 按键事件
     */
    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        pressedKeys.remove(code);   // 移出已按下集合

        switch (code) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                playerPlane.onStopMoveUp();
                break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                playerPlane.onStopMoveDown();
                break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                playerPlane.onStopMoveLeft();
                break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                playerPlane.onStopMoveRight();
                break;
            case KeyEvent.VK_SPACE:
                playerPlane.onStopShoot();  // 松开空格停止射击
                break;
            // J 键和技能键不需要 keyReleased 处理（toggle 型 / 一次性操作）
        }
    }

    /**
     * 按键键入事件 — 不使用。
     * <p>所有输入处理通过 keyPressed / keyReleased 完成。</p>
     */
    @Override
    public void keyTyped(KeyEvent e) {
        // keyTyped 不会为方向键和功能键触发，所有输入通过 keyPressed/Released 处理
    }

    // ── 兼容旧接口（委托到 keyPressed / keyReleased） ──

    /** @deprecated 使用 keyPressed(KeyEvent) 替代 */
    @Deprecated
    public void handleKeyPress(KeyEvent e) { keyPressed(e); }

    /** @deprecated 使用 keyReleased(KeyEvent) 替代 */
    @Deprecated
    public void handleKeyRelease(KeyEvent e) { keyReleased(e); }
}
