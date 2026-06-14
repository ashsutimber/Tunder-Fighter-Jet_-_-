package Controller.CoreController;

import Controller.Coreiterface.GameEventListener;
import java.util.ArrayList;
import java.util.List;

/**
 * <h1>事件控制器（EventControl）— 观察者模式的事件分发中心</h1>
 *
 * <p>EventControl 实现了观察者（Observer）设计模式中的 Subject 角色，
 * 负责管理游戏事件监听器（{@link GameEventListener}）的注册/注销，
 * 以及游戏事件的触发与分发（{@link #fireEvent(GameEvent)}）。</p>
 *
 * <p>当游戏发生关键事件（敌机被击毁、Boss 出场、玩家升级、游戏结束等）时，
 * GameEngine 调用 {@link #fireEvent(GameEvent)} 通知所有已注册的监听器。
 * 各模块通过实现 {@link GameEventListener} 接口来接收感兴趣的事件。</p>
 *
 * <h2>设计意图</h2>
 * <p>通过事件驱动的方式解耦游戏各模块，避免 GameEngine 与 UI/音效/存档等
 * 模块之间的直接依赖。新模块只需注册为监听器即可响应事件，无需修改核心逻辑。</p>
 *
 * <h2>事件流</h2>
 * <pre>
 * GameEngine / EnemyPlane / ...
 *   │
 *   └─ fireEvent(new GameEvent(type, data))
 *       │
 *       └─ forEach listener:
 *           └─ listener.onGameEvent(event)
 *               ├─ HUD 更新分数
 *               ├─ 播放音效
 *               ├─ 显示特效
 *               └─ 记录统计
 * </pre>
 *
 * <h2>使用注意事项</h2>
 * <ul>
 *   <li>事件分发是<b>同步执行</b>的，监听器的 onGameEvent 方法中应避免耗时操作</li>
 *   <li>监听器按注册顺序被调用（FIFO）</li>
 *   <li>同一监听器不会重复注册（使用 contains 检查去重）</li>
 *   <li>在事件分发过程中不应修改 listeners 列表（ConcurrentModificationException）</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see GameEvent         事件数据载体
 * @see GameEventListener 事件监听接口
 * @see GameEngine        游戏引擎（主要的事件触发者）
 */
public class EventControl {

    /**
     * 已注册的事件监听器列表。
     * <p>使用 ArrayList 存储，支持按注册顺序同步遍历。
     * 通过 {@link #registerListener(GameEventListener)} 和
     * {@link #unregisterListener(GameEventListener)} 管理。</p>
     */
    private List<GameEventListener> listeners = new ArrayList<>();

    /**
     * 注册事件监听器（重复注册会被忽略）。
     *
     * <p>通常在构造函数或初始化方法中调用。
     * 注册后，监听器将接收所有 {@link #fireEvent(GameEvent)} 触发的事件。</p>
     *
     * @param listener 要注册的监听器实例
     */
    public void registerListener(GameEventListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);    // 去重
        }
    }

    /**
     * 注销事件监听器。
     *
     * <p>通常在对象销毁或不再需要接收事件时调用。
     * 注销后该监听器将不再接收任何事件通知。</p>
     *
     * @param listener 要注销的监听器实例
     */
    public void unregisterListener(GameEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * 触发事件 — 遍历所有已注册的监听器，依次调用其
     * {@link GameEventListener#onGameEvent(GameEvent)} 方法。
     *
     * <p><b>注意：</b>此方法是同步的，会在当前线程中依次调用所有监听器。
     * 监听器的 onGameEvent 实现应快速返回，避免阻塞游戏主循环（60 FPS）。</p>
     *
     * <p>不同类型的事件携带不同的 data 对象：</p>
     * <ul>
     *   <li>{@code PLAYER_HIT} → data = Integer（伤害值）</li>
     *   <li>{@code ENEMY_DIED} → data = EnemyPlane（被击毁的敌机）</li>
     *   <li>{@code BOSS_SPAWN} → data = EnemyPlane（Boss 敌机）</li>
     *   <li>{@code WAVE_START} / {@code WAVE_CLEAR} → data = Integer（波次编号）</li>
     *   <li>{@code CARD_SELECT} → data = List&lt;UpgradeCard&gt;（可选的升级卡列表）</li>
     *   <li>{@code GAME_OVER} / {@code GAME_WIN} → data = null</li>
     * </ul>
     *
     * @param event 要分发的游戏事件（包含类型和附加数据）
     */
    public void fireEvent(GameEvent event) {
        for (GameEventListener listener : listeners) {
            listener.onGameEvent(event);    // 同步通知
        }
    }
}
