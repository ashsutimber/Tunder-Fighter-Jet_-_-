package Entity.Plane;

import Controller.Coreiterface.PlayerActionListener;
import Entity.Buff.*;
import Entity.Bullet.*;
import Entity.Skill.*;
import java.util.Iterator;

/**
 * <h1>玩家飞机（PlayerPlane）— 键盘驱动的可操控飞机</h1>
 *
 * <p>继承自 {@link Plane}，实现 {@link PlayerActionListener} 接口，
 * 是游戏中由玩家操控的核心实体。玩家的键盘输入通过
 * {@code InputControl → PlayerActionListener → PlayerPlane} 链路
 * 驱动飞机的移动、射击和技能释放。</p>
 *
 * <h2>四种飞机类型</h2>
 * <table border="1">
 *   <tr><th>编号</th><th>名称</th><th>类型</th><th>HP</th><th>护甲</th><th>移速</th><th>初始子弹</th></tr>
 *   <tr><td>1</td><td>雷霆</td><td>均衡型</td><td>3000</td><td>50</td><td>5.0</td><td>普通子弹</td></tr>
 *   <tr><td>2</td><td>疾风</td><td>高速型</td><td>2500</td><td>30</td><td>7.0</td><td>普通子弹</td></tr>
 *   <tr><td>3</td><td>破甲</td><td>重甲型</td><td>4000</td><td>80</td><td>3.5</td><td>穿甲弹</td></tr>
 *   <tr><td>4</td><td>追猎</td><td>追踪型</td><td>2800</td><td>40</td><td>5.5</td><td>追踪导弹</td></tr>
 * </table>
 *
 * <h2>输入状态管理</h2>
 * <p>移动方向通过 4 个布尔标志位追踪（movingUp/Down/Left/Right），
 * 支持多个方向同时按下实现对角线移动。
 * 对角线移动时速度矢量乘以 0.707（≈ 1/√2）进行归一化，
 * 避免对角线移速比直线移速快 ~41% 的问题。</p>
 *
 * <h2>自动开火</h2>
 * <p>按 J 键切换自动开火模式。自动模式下，GameEngine 每帧调用
 * {@link #tryShoot(float)} 尝试射击（无视 isShooting 标志）。
 * 非自动模式下，按住空格键（isShooting=true）持续射击。</p>
 *
 * <h2>Buff/技能效果修正</h2>
 * <ul>
 *   <li><b>过载超频 (S4)：</b>移速 +10%</li>
 *   <li><b>暗能爆发 (S8)：</b>移速 -50%</li>
 *   <li><b>火力 Buff：</b>弹速 +100% × fireLevel</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see Plane        飞机抽象基类
 * @see EnemyPlane   敌机（AI 控制方）
 * @see PlayerActionListener  玩家操作接口
 * @see Controller.CoreController.InputControl  键盘输入控制器
 */
public class PlayerPlane extends Plane implements PlayerActionListener {

    // ── 输入状态 ──
    /** W / ↑ 是否被按住（向上移动） */
    private boolean movingUp = false;
    /** S / ↓ 是否被按住（向下移动） */
    private boolean movingDown = false;
    /** A / ← 是否被按住（向左移动） */
    private boolean movingLeft = false;
    /** D / → 是否被按住（向右移动） */
    private boolean movingRight = false;
    /** 空格键是否被按住（射击中） */
    private boolean isShooting = false;
    /** 自动开火开关（J 键切换），开启后无视 isShooting 标志始终射击 */
    private boolean autoFire = false;

    // ── 飞机类型 ──
    /** 选择的飞机类型（1=雷霆, 2=疾风, 3=破甲, 4=追猎） */
    private int planeType = 1;

    // ═══════════════════════════════════════════════
    // 构造方法
    // ═══════════════════════════════════════════════

    /**
     * 构造玩家飞机，默认选择飞机1（雷霆·均衡型）。
     */
    public PlayerPlane() {
        super();
        configurePlane(1);      // 默认：飞机1（雷霆）
        this.level = 1;
        this.xp = 0;
        this.maxSkillSlots = 0;
    }

    /**
     * 根据飞机类型配置初始属性。
     *
     * <p>四种飞机的差异化参数：</p>
     * <ul>
     *   <li><b>1·雷霆（均衡型）：</b>HP 3000, 护甲 50, 移速 5.0, 普通子弹</li>
     *   <li><b>2·疾风（高速型）：</b>HP 2500, 护甲 30, 移速 7.0, 普通子弹</li>
     *   <li><b>3·破甲（重甲型）：</b>HP 4000, 护甲 80, 移速 3.5, 穿甲弹</li>
     *   <li><b>4·追猎（追踪型）：</b>HP 2800, 护甲 40, 移速 5.5, 追踪导弹</li>
     * </ul>
     *
     * <p>所有飞机初始射速为 0.5s/发，碰撞体积为 48×48。</p>
     *
     * @param type 飞机类型（1~4）
     */
    public void configurePlane(int type) {
        this.planeType = type;
        this.width = 48;
        this.height = 48;
        this.atkSpeed = 0.5f;   // 初始射速：0.5 秒/发

        switch (type) {
            case 1: // 雷霆 — 均衡型
                this.maxHp = 3000; this.hp = 3000;
                this.armor = 50;
                this.moveSpeed = 5.0f;
                this.bullet = new NormalBullet(0, 0, true);
                break;
            case 2: // 疾风 — 高速型（低血量高机动）
                this.maxHp = 2500; this.hp = 2500;
                this.armor = 30;
                this.moveSpeed = 7.0f;
                this.bullet = new NormalBullet(0, 0, true);
                break;
            case 3: // 破甲 — 重甲型（高血量低机动，初始穿甲弹）
                this.maxHp = 4000; this.hp = 4000;
                this.armor = 80;
                this.moveSpeed = 3.5f;
                this.bullet = new ArmorBullet(0, 0, true);
                break;
            case 4: // 追猎 — 追踪型（中血量中机动，初始导弹）
                this.maxHp = 2800; this.hp = 2800;
                this.armor = 40;
                this.moveSpeed = 5.5f;
                this.bullet = new MissileBullet(0, 0, true);
                break;
        }
    }

    /** @return 当前飞机类型（1~4） */
    public int getPlaneType() { return planeType; }

    // ═══════════════════════════════════════════════
    // PlayerActionListener 实现
    // ═══════════════════════════════════════════════

    /** 按下 W / ↑：开始向上移动 */
    @Override
    public void onMoveUp()    { movingUp = true; }

    /** 按下 S / ↓：开始向下移动 */
    @Override
    public void onMoveDown()  { movingDown = true; }

    /** 按下 A / ←：开始向左移动 */
    @Override
    public void onMoveLeft()  { movingLeft = true; }

    /** 按下 D / →：开始向右移动 */
    @Override
    public void onMoveRight() { movingRight = true; }

    /** 按下空格：开始射击 */
    @Override
    public void onShoot()     { isShooting = true; }

    // ── 释放按键（由 InputControl.keyReleased 调用） ──
    /** 释放 W / ↑：停止向上移动 */
    public void onStopMoveUp()    { movingUp = false; }
    /** 释放 S / ↓：停止向下移动 */
    public void onStopMoveDown()  { movingDown = false; }
    /** 释放 A / ←：停止向左移动 */
    public void onStopMoveLeft()  { movingLeft = false; }
    /** 释放 D / →：停止向右移动 */
    public void onStopMoveRight() { movingRight = false; }
    /** 释放空格：停止射击 */
    public void onStopShoot()     { isShooting = false; }

    /** 使用技能（由 InputControl 调用，实际激活由 GameEngine 处理） */
    @Override
    public void onUseSkill(int skillIndex) {
        // 技能的实际激活逻辑在 GameEngine.activateSkill() 中处理
        // 此方法仅声明接口，不在此处实现
    }

    // ═══════════════════════════════════════════════
    // Plane 抽象方法实现
    // ═══════════════════════════════════════════════

    /**
     * 每帧移动 — 根据当前按下的方向键更新飞机位置。
     *
     * <p>速度修正：</p>
     * <ul>
     *   <li>基础移速 = moveSpeed</li>
     *   <li>过载超频 (S4)：移速 × 1.10</li>
     *   <li>暗能爆发 (S8)：移速 × 0.50</li>
     * </ul>
     *
     * <p><b>对角线归一化：</b>同时按下两个方向时，
     * 各分量乘以 0.707（≈ 1/√2），确保对角线移速与直线移速一致。</p>
     *
     * <p>边界限制在 800×600 屏幕内（考虑飞机半宽/半高）。</p>
     *
     * @param dx 未使用（InputControl 的遗留参数）
     * @param dy 未使用（InputControl 的遗留参数）
     */
    @Override
    public void move(float dx, float dy) {
        // 计算有效移速（受 Buff/技能修正）
        float effectiveSpeed = moveSpeed;
        if (overclockActive) effectiveSpeed *= 1.10f;   // S4: 移速 +10%
        if (darkBurstActive) effectiveSpeed *= 0.50f;    // S8: 移速 -50%

        // 根据按键状态计算位移分量
        float mx = 0, my = 0;
        if (movingUp)    my = -effectiveSpeed;   // Y 轴上为负（屏幕坐标系）
        if (movingDown)  my = effectiveSpeed;
        if (movingLeft)  mx = -effectiveSpeed;
        if (movingRight) mx = effectiveSpeed;

        // 对角线归一化（避免 √2 倍速问题）
        if (mx != 0 && my != 0) {
            mx *= 0.707f;   // ≈ 1/√2
            my *= 0.707f;
        }

        // 应用位移
        x += mx;
        y += my;

        // 边界限制（800×600 窗口，考虑飞机半宽/半高）
        if (x < width / 2) x = width / 2;           // 左边界
        if (x > 800 - width / 2) x = 800 - width / 2; // 右边界
        if (y < height / 2) y = height / 2;         // 上边界
        if (y > 600 - height / 2) y = 600 - height / 2; // 下边界
    }

    /** 射击由 GameEngine 每帧驱动，此方法不直接使用 */
    @Override
    public void shoot() {
        // 射击由 GameEngine.tryShoot() 统一管理
    }

    /**
     * 执行射击（由 GameEngine 每帧调用）。
     *
     * <p>射击条件：isShooting=true（空格键按住）或 autoFire=true（J 键开启）。</p>
     *
     * <p>火力 Buff 效果在此应用：弹速 × (1 + fireLevel)。
     * 其他 Buff/技能效果（过载/暗能/被动）在 {@link Plane#doShoot} 中应用。</p>
     *
     * @param gameTime 当前游戏时间（秒）
     * @return 创建的子弹（用于加入 bullet list）；冷却中或未在射击则返回 null
     */
    public Bullet tryShoot(float gameTime) {
        if (!isShooting) return null;   // 未按住射击键且未自动开火

        // 调用基类的 doShoot 创建子弹副本并应用 Buff/技能修正
        Bullet b = doShoot(gameTime, x, y - height / 2);

        if (b != null) {
            // 火力 Buff 额外效果：弹速 +100% × fireLevel
            if (fireLevel > 0) {
                b.setSpeed(b.getSpeed() * (1 + fireLevel));
            }
        }
        return b;
    }

    /**
     * 受到伤害 — 委托给基类的 {@link Plane#applyDamage(int)} 处理。
     * <p>基类处理：无敌检查 → 护盾 → HP → 死亡判定。</p>
     */
    @Override
    public void takeDamage(int damage) {
        applyDamage(damage);
    }

    /**
     * 应用 Buff — 添加到 buffs 列表并立即生效。
     * @param buff 要应用的 Buff 实例
     */
    @Override
    public void applyBuff(Buff buff) {
        buffs.add(buff);
        buff.apply(this);   // 立即生效
    }

    /** 过期 Buff 由 checkExpiredBuffs(gameTime) 统一处理 */
    @Override
    public void removeExpiredBuffs() {
        // 委托给 checkExpiredBuffs(gameTime)
    }

    /**
     * 死亡判定。
     * @return true 如果 alive 标志为 false
     */
    @Override
    public boolean isDead() {
        return !alive;
    }

    // ═══════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════

    /**
     * 重置玩家飞机到初始状态（游戏重开或飞机重新选择时调用）。
     *
     * <p>重置内容：</p>
     * <ul>
     *   <li>保持飞机类型（planeType 不变），重新配置基础属性</li>
     *   <li>等级重置为 1，经验清零</li>
     *   <li>清空所有技能、Buff、状态标志</li>
     *   <li>护盾清零，无敌/过载/暗能标志置 false</li>
     *   <li>火力等级归零，子弹属性恢复默认</li>
     * </ul>
     */
    public void reset() {
        int savedType = this.planeType;
        configurePlane(savedType);  // 保持飞机类型，重置所有属性

        // 重置成长和状态
        this.level = 1;
        this.xp = 0;
        this.maxSkillSlots = 0;
        this.shieldHp = 0;
        this.invincible = false;
        this.overclockActive = false;
        this.darkBurstActive = false;
        this.fireLevel = 0;
        this.alive = true;
        this.autoFire = false;

        // 清空集合
        this.buffs.clear();
        this.skills.clear();
        this.lastShootTime = 0;
    }

    // ═══════════════════════════════════════════════
    // Getter / Setter
    // ═══════════════════════════════════════════════

    /** @return 空格键是否被按住 */
    public boolean isShooting() { return isShooting; }

    /** @return 自动开火是否开启 */
    public boolean isAutoFire() { return autoFire; }

    /** 设置自动开火状态 */
    public void setAutoFire(boolean on) { this.autoFire = on; }

    /** 切换自动开火状态（J 键） */
    public void toggleAutoFire() { this.autoFire = !this.autoFire; }

    public boolean isMovingUp() { return movingUp; }
    public boolean isMovingDown() { return movingDown; }
    public boolean isMovingLeft() { return movingLeft; }
    public boolean isMovingRight() { return movingRight; }
}
