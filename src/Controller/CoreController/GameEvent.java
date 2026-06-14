package Controller.CoreController;

/**
 * <h1>游戏事件 — 观察者模式中的数据载体</h1>
 *
 * <p>GameEvent 封装游戏中发生的各类关键事件，由 {@link EventControl} 负责
 * 分发给所有已注册的 {@link Controller.Coreiterface.GameEventListener}。</p>
 *
 * <p>每个 GameEvent 包含：</p>
 * <ul>
 *   <li><b>类型（Type 枚举）</b> — 标识事件类别</li>
 *   <li><b>数据（Object data）</b> — 事件携带的附加信息（如被击毁的敌机引用、伤害值等）</li>
 * </ul>
 *
 * <h2>事件类型说明</h2>
 * <table border="1">
 *   <tr><th>枚举值</th><th>触发时机</th><th>data 内容</th></tr>
 *   <tr><td>{@code PLAYER_HIT}</td><td>玩家被敌机子弹命中</td><td>伤害值 (Integer)</td></tr>
 *   <tr><td>{@code ENEMY_DIED}</td><td>敌机被击毁</td><td>被击毁的敌机 (EnemyPlane)</td></tr>
 *   <tr><td>{@code BOSS_PHASE_CHANGE}</td><td>Boss 切换阶段</td><td>新阶段编号 (Integer)</td></tr>
 *   <tr><td>{@code BOSS_SPAWN}</td><td>Boss 出场</td><td>Boss 敌机 (EnemyPlane)</td></tr>
 *   <tr><td>{@code WAVE_START}</td><td>新波次开始</td><td>波次编号 (Integer)</td></tr>
 *   <tr><td>{@code WAVE_CLEAR}</td><td>当前波次敌机全部清除</td><td>波次编号 (Integer)</td></tr>
 *   <tr><td>{@code PLAYER_LEVEL_UP}</td><td>玩家升级</td><td>新等级 (Integer)</td></tr>
 *   <tr><td>{@code CARD_SELECT}</td><td>弹出升级卡选择</td><td>升级卡列表 (List&lt;UpgradeCard&gt;)</td></tr>
 *   <tr><td>{@code GAME_OVER}</td><td>游戏结束（玩家死亡）</td><td>null</td></tr>
 *   <tr><td>{@code GAME_WIN}</td><td>通关（击败最终 Boss）</td><td>null</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 触发事件
 * eventControl.fireEvent(new GameEvent(GameEvent.Type.ENEMY_DIED, killedEnemy));
 *
 * // 监听事件
 * eventControl.registerListener(event -&gt; {
 *     switch (event.getType()) {
 *         case GAME_OVER: handleGameOver(); break;
 *         // ...
 *     }
 * });
 * </pre>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see EventControl  事件控制器（观察者模式分发器）
 * @see Controller.Coreiterface.GameEventListener  事件监听接口
 */
public class GameEvent {

    /**
     * 游戏事件类型枚举。
     * 每个枚举值对应游戏中的一个关键事件，用于模块间解耦通信。
     */
    public enum Type {
        /** 玩家飞机被敌机子弹命中（data = Integer 伤害值） */
        PLAYER_HIT,
        /** 敌机被玩家子弹击毁（data = EnemyPlane） */
        ENEMY_DIED,
        /** Boss 进入新阶段（data = Integer 新阶段号 1-3） */
        BOSS_PHASE_CHANGE,
        /** Boss 敌机出场（data = EnemyPlane） */
        BOSS_SPAWN,
        /** 新一波敌机开始（data = Integer 波次编号） */
        WAVE_START,
        /** 当前波次所有敌机被清除（data = Integer 波次编号） */
        WAVE_CLEAR,
        /** 玩家升级（data = Integer 新等级） */
        PLAYER_LEVEL_UP,
        /** 弹出升级卡选择界面（data = List&lt;UpgradeCard&gt; 可选卡列表） */
        CARD_SELECT,
        /** 游戏结束—玩家死亡（data = null） */
        GAME_OVER,
        /** 通关—最终 Boss 被击败（data = null） */
        GAME_WIN
    }

    /** 事件类型（决定监听器如何处理） */
    private Type type;

    /** 事件附加数据（具体含义由 type 决定，可为 null） */
    private Object data;

    /**
     * 构造一个游戏事件。
     *
     * @param type 事件类型枚举值
     * @param data 事件携带的附加数据
     *        （如 {@code ENEMY_DIED} 时为被击毁的敌机引用，
     *         {@code PLAYER_HIT} 时为伤害值 Integer）
     */
    public GameEvent(Type type, Object data) {
        this.type = type;
        this.data = data;
    }

    /**
     * 获取事件类型。
     * @return 事件类型枚举值
     */
    public Type getType() { return this.type; }

    /**
     * 获取事件附加数据。
     * <p>调用方需要根据 {@link #getType()} 进行合理类型转换（instanceof / cast）。</p>
     * @return 事件数据对象（可能为 null）
     */
    public Object getData() { return this.data; }
}
