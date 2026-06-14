package Controller.Coreiterface;

import Controller.CoreController.GameEvent;
import Controller.CoreController.EventControl;

/**
 * <h1>游戏事件监听接口 — 观察者模式中的 Observer</h1>
 *
 * <p>所有需要响应游戏事件（敌机被击毁、Boss 出场、玩家升级、游戏结束等）的
 * 类都应实现本接口，并通过 {@link EventControl#registerListener(GameEventListener)}
 * 注册到事件控制器。</p>
 *
 * <p>当 {@link EventControl#fireEvent(GameEvent)} 被调用时，
 * 所有已注册监听器的 {@link #onGameEvent(GameEvent)} 方法将按
 * 注册顺序被依次调用（同步执行）。</p>
 *
 * <h2>使用示例</h2>
 * <pre>
 * public class MyHandler implements GameEventListener {
 *     public MyHandler(EventControl ec) {
 *         ec.registerListener(this);  // 注册监听
 *     }
 *
 *     &#64;Override
 *     public void onGameEvent(GameEvent event) {
 *         switch (event.getType()) {
 *             case ENEMY_DIED:
 *                 EnemyPlane killed = (EnemyPlane) event.getData();
 *                 updateScore(killed.getScoreValue());
 *                 break;
 *             case GAME_OVER:
 *                 showGameOverScreen();
 *                 break;
 *         }
 *     }
 * }
 * </pre>
 *
 * <h2>事件类型完整列表</h2>
 * <p>详见 {@link GameEvent.Type}，包括：</p>
 * <ul>
 *   <li>{@code PLAYER_HIT} — 玩家被击中</li>
 *   <li>{@code ENEMY_DIED} — 敌机被击毁</li>
 *   <li>{@code BOSS_PHASE_CHANGE} — Boss 切换阶段</li>
 *   <li>{@code BOSS_SPAWN} — Boss 出场</li>
 *   <li>{@code WAVE_START} — 新波次开始</li>
 *   <li>{@code WAVE_CLEAR} — 波次敌机全部清除</li>
 *   <li>{@code PLAYER_LEVEL_UP} — 玩家升级</li>
 *   <li>{@code CARD_SELECT} — 弹出升级卡选择</li>
 *   <li>{@code GAME_OVER} — 游戏结束</li>
 *   <li>{@code GAME_WIN} — 通关</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see EventControl  事件控制器（观察者模式中的 Subject）
 * @see GameEvent     事件数据载体
 */
public interface GameEventListener {

    /**
     * 当有游戏事件触发时被调用。
     *
     * <p>实现类应根据 {@link GameEvent#getType()} 判断事件类型，
     * 并从 {@link GameEvent#getData()} 中提取附加数据（如需要）。
     * 本方法在 {@link EventControl#fireEvent(GameEvent)} 中同步调用，
     * 应避免耗时操作以免影响游戏帧率。</p>
     *
     * @param event 触发的游戏事件（包含类型和附加数据）
     */
    void onGameEvent(GameEvent event);
}
