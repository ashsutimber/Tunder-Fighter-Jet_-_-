package Entity.Plane;

import Controller.Coreiterface.EnemyActionListener;
import Entity.Buff.*;
import Entity.Bullet.*;
import Entity.Skill.*;
import java.util.Iterator;

/**
 * <h1>敌机（EnemyPlane）— AI 驱动的飞行单位</h1>
 *
 * <p>继承自 {@link Plane}，实现敌机的自主 AI 行为，包括多种移动模式、
 * 弹幕射击决策、Boss 多阶段切换和冲撞攻击。</p>
 *
 * <h2>敌机等级体系</h2>
 * <table border="1">
 *   <tr><th>等级</th><th>HP</th><th>护甲</th><th>移速</th><th>射速</th><th>弹幕</th><th>移动模式</th><th>经验</th></tr>
 *   <tr><td>Lv.1 杂兵</td><td>500</td><td>10</td><td>2.0</td><td>1.2s</td><td>单发直射</td><td>直线下降</td><td>250</td></tr>
 *   <tr><td>Lv.2 机动</td><td>1500</td><td>20</td><td>3.0</td><td>0.9s</td><td>2路扩散±10°</td><td>正弦/直线</td><td>680</td></tr>
 *   <tr><td>Lv.3 精英</td><td>3000</td><td>80</td><td>2.5</td><td>0.6s</td><td>3路扇形±15°</td><td>正弦/折线</td><td>1200</td></tr>
 *   <tr><td>Lv.4 王牌</td><td>5000</td><td>150</td><td>3.5</td><td>3.0s</td><td>3路+穿甲弹</td><td>追踪摇摆</td><td>2000</td></tr>
 *   <tr><td>Boss 1</td><td>300000</td><td>100</td><td>1.0</td><td>0.2s</td><td>扇形5发+环形+旋转</td><td>X轴冲刺</td><td>10000</td></tr>
 *   <tr><td>Boss 2</td><td>600000</td><td>200</td><td>0.5</td><td>0.6s</td><td>扇形8发+双旋转+红弹</td><td>X轴冲刺</td><td>10000</td></tr>
 * </table>
 *
 * <h2>Boss 多阶段系统</h2>
 * <p>Boss 具有 3 个阶段，根据 HP 百分比自动切换：</p>
 * <ul>
 *   <li><b>Phase 1:</b> HP 100% ~ 67% — 基础弹幕</li>
 *   <li><b>Phase 2:</b> HP 66% ~ 21% — 弹幕升级 + 冲撞攻击</li>
 *   <li><b>Phase 3:</b> HP ≤ 20% — 弹幕大幅升级，取消冲撞</li>
 * </ul>
 *
 * <h2>冲撞攻击机制</h2>
 * <ol>
 *   <li><b>预警阶段（2秒）：</b>Boss 记录玩家位置并静止，显示红色虚线指向该位置 + 十字标记</li>
 *   <li><b>冲撞阶段：</b>沿记录的方向以 15 px/帧高速冲出</li>
 *   <li><b>返回阶段：</b>冲撞结束或撞到玩家/边界后，以 10 px/帧移回原始位置</li>
 * </ol>
 *
 * <h2>波次敌机生成比例</h2>
 * <table border="1">
 *   <tr><th>波次</th><th>Lv.1</th><th>Lv.2</th><th>Lv.3</th><th>Lv.4</th><th>Boss</th></tr>
 *   <tr><td>1~3</td><td>100%</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
 *   <tr><td>4</td><td>80%</td><td>20%</td><td>—</td><td>—</td><td>—</td></tr>
 *   <tr><td>5</td><td>60%</td><td>30%</td><td>10%</td><td>—</td><td>Boss 1</td></tr>
 *   <tr><td>6~7</td><td>40%</td><td>30%</td><td>20%</td><td>10%</td><td>Boss 2</td></tr>
 * </table>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see Plane        飞机抽象基类
 * @see PlayerPlane  玩家飞机
 * @see EnemyActionListener  敌机 AI 行为接口
 */
public class EnemyPlane extends Plane {

    // ── 移动模式枚举 ──
    /** 敌机移动模式 — 决定 move() 中的路径行为 */
    public enum MovePattern {
        /** 直线下降：y += moveSpeed */
        STRAIGHT,
        /** 正弦波摇摆：x = initialX + sin(timer) * amplitude; y += moveSpeed */
        SINE,
        /** 折线移动：根据 sin 正负分别向左右平移 */
        ZIGZAG,
        /** 追踪摇摆（Lv.4）：横向追踪玩家 + 缓降 */
        CHASE,
        /** Boss X轴冲刺：周期性（5~10s）沿X方向高速冲刺 + 慢速漂移 */
        BOSS_SWAY
    }

    // ── 敌机等级枚举 ──
    /** 敌机等级 — 决定属性配置和 AI 行为复杂度 */
    public enum EnemyLevel {
        /** Lv.1 基础杂兵：HP 500, 护甲 10, 单发直射 */
        LEVEL_1,
        /** Lv.2 机动型：HP 1500, 护甲 20, 2路扩散 */
        LEVEL_2,
        /** Lv.3 精英型：HP 3000, 护甲 80, 3路扇形 */
        LEVEL_3,
        /** Lv.4 王牌型：HP 5000, 护甲 150, 3路+穿甲弹 */
        LEVEL_4,
        /** Boss 敌机：HP 300000/600000, 多阶段弹幕 */
        BOSS
    }

    // ── 基本属性 ──
    /** 敌机等级（LEVEL_1~4 或 BOSS） */
    private EnemyLevel enemyLevel;

    /** 当前移动模式 */
    private MovePattern movePattern;

    /** 击毁得分（累计到玩家总分） */
    private int scoreValue;

    /** 击杀经验值（发放给玩家） */
    private int xpValue;

    /** Buff 掉落概率（0.0~1.0），Boss 固定 1.0 */
    private float buffDropRate;

    // ── AI 状态 ──
    /** 移动相位计时器（正弦波/折线的相位累计） */
    private float moveTimer = 0;

    /** 射击冷却计时器（秒），达到 atkSpeed 后可射击 */
    private float shootTimer = 0;

    /** 初始 X 坐标（正弦波的中心线位置） */
    private float initialX;

    /** 正弦波振幅（px） */
    private float amplitude = 60;

    /** 正弦波频率（弧度/帧） */
    private float frequency = 0.03f;

    /** Boss 入场动画是否完成 */
    private boolean entered = false;

    /** Boss 入场动画计时器（秒） */
    private float entryTimer = 0;

    /** Boss 入场动画持续时间（3 秒） */
    private static final float ENTRY_DURATION = 3.0f;

    // ── Boss 阶段 ──
    /** Boss 当前阶段（0=非Boss, 1/2/3=Boss阶段），根据 HP 百分比自动切换 */
    private int bossPhase = 0;

    /** Boss 编号（1=雷霆先锋, 2=雷霆霸主） */
    private int bossLevel = 0;

    // ── Boss 弹幕计时器 ──
    /** Boss 1 环形弹幕计时器（每 2.5s 发射 30 发红色全周弹幕） */
    private float bossRingTimer = 0;

    /** Boss 1/2 旋转弹幕角度更新 */
    private float bossSweepTimer = 0;

    /** Boss 1 Phase 3 旋转弹幕 A 发射间隔计时（0.15s/发） */
    private float bossSweepFireTimerA = 0;

    /** Boss 1 Phase 3 旋转弹幕 B 发射间隔计时（0.15s/发） */
    private float bossSweepFireTimerB = 0;

    /** Boss 2 大型红弹计时器（Phase 1: 8s, Phase 2+: 5s） */
    private float bossBombTimer = 0;

    /** Boss 2 大型红弹连发计数（Phase 2+: 每次 3 连发） */
    private int bossBombBurstCount = 0;

    /** Boss 冲撞计时器（秒），达到间隔后触发冲撞预警 */
    private float bossChargeTimer = 0;

    /** Boss 2 Phase 3 巨型蓝弹计时器（每 20s） */
    private float bossGiantBlueTimer = 0;

    /** Boss 2 Phase 3 是否已回血（仅一次） */
    private boolean bossHealed = false;

    /** Boss 2 环形弹幕计时器（Phase 2+ 每 3~5s 随机） */
    private float boss2RingTimer = 0;

    /** Boss 2 环形弹幕间隔（3~5s 随机） */
    private float boss2RingInterval = 4.0f;

    /** Boss 2 Phase 3 环形弹幕剩余连发次数 */
    private int boss2RingBurstCount = 0;

    /** Boss 2 Phase 3 环形弹幕连发间隔计时（0.3s/次） */
    private float boss2RingBurstTimer = 0;

    // ── Boss 移动冲刺 ──
    /** Boss X 轴冲刺计时器（秒） */
    private float bossMoveDashTimer = 0;

    /** Boss 冲刺间隔（5~10s 随机） */
    private float bossDashInterval = 7.0f;

    /** Boss 当前冲刺 X 方向（+1 向右, -1 向左） */
    private float bossDashXDir = 0;

    /** Boss 是否正在 X 轴冲刺 */
    private boolean bossIsDashing = false;

    /** Boss 冲刺已持续时间（秒），上限 0.4s */
    private float bossDashDuration = 0;

    /** Boss 冲刺方向随机数生成器 */
    private java.util.Random dashRandom = new java.util.Random();

    // ── 双旋转弹幕角度（Boss 1 Phase 3 / Boss 2 全阶段） ──
    /** 旋转弹幕 A 当前角度（60° → -60° 往复） */
    private float bossSweepAngleA = 60;

    /** 旋转弹幕 B 当前角度（-60° → 60° 往复，与 A 方向相反） */
    private float bossSweepAngleB = -60;

    /** A 扫射方向（+1 正转, -1 反转） */
    private int bossSweepDirA = -1;

    /** B 扫射方向（与 A 相反） */
    private int bossSweepDirB = 1;

    // ── 冲撞攻击状态 ──
    /** 是否正在冲撞中（高速冲向记录的玩家位置） */
    private boolean isCharging = false;

    /** 是否在冲撞预警阶段（显示红色虚线 + 十字标记，2 秒） */
    private boolean isChargeWarning = false;

    /** 预警阶段倒计时（秒），从 2.0 倒到 0 */
    private float chargeWarningTimer = 0;

    /** 预警持续时间（秒） */
    private static final float CHARGE_WARNING_DURATION = 2.0f;

    /** 冲撞方向单位向量 X（指向记录的玩家位置，预警前确定后不再改变） */
    private float chargeDirX = 0;

    /** 冲撞方向单位向量 Y */
    private float chargeDirY = 0;

    /** 冲撞前记录的玩家 X 坐标（预警时固定，冲撞过程中不变） */
    private float chargeTargetX = 0;

    /** 冲撞前记录的玩家 Y 坐标 */
    private float chargeTargetY = 0;

    /** 冲撞速度 X 分量（px/帧），= chargeDirX × 15 */
    private float chargeSpeedX = 0;

    /** 冲撞速度 Y 分量（px/帧），= chargeDirY × 15 */
    private float chargeSpeedY = 0;

    /** 冲撞持续时间计时器（秒），上限 1.5s */
    private float chargeTimer = 0;

    /** 冲撞前原始 X 坐标（返回目标位置） */
    private float chargeOriginX = 0;

    /** 冲撞前原始 Y 坐标（Boss 默认 y≈120） */
    private float chargeOriginY = 120;

    /** 冲撞结束 X（预留，未使用） */
    private float chargeEndX = 0;

    /** 冲撞结束 Y（预留，未使用） */
    private float chargeEndY = 0;

    /** 是否正在返回原始位置 */
    private boolean isReturning = false;

    /** 返回速度（px/帧），默认 10.0 */
    private float returnSpeed = 10.0f;

    // ═══════════════════════════════════════════════
    // 构造方法
    // ═══════════════════════════════════════════════

    /** 空构造方法，由工厂方法填充属性 */
    public EnemyPlane() {
        super();
        this.initialX = 0;
    }

    // ═══════════════════════════════════════════════
    // 工厂方法
    // ═══════════════════════════════════════════════

    /**
     * 创建指定等级的普通敌机。
     *
     * <p>根据等级配置 HP、护甲、移速、射速、子弹类型、得分/经验值、
     * Buff掉落率和移动模式。具体参数见类的等级对比表。</p>
     *
     * @param level 敌机等级（LEVEL_1~4）
     * @return 配置完成的敌机实例
     */
    public static EnemyPlane createNormalEnemy(EnemyLevel level) {
        EnemyPlane e = new EnemyPlane();
        e.enemyLevel = level;
        e.isCharging = false;

        switch (level) {
            case LEVEL_1:
                // 基础杂兵：低血量、低护甲、最慢移速、单发直射
                e.maxHp = 500; e.hp = 500; e.armor = 10;
                e.moveSpeed = 2.0f; e.atkSpeed = 1.2f;
                e.bullet = new NormalBullet(0, 0, false);
                e.bullet.setAttack(350);       // Lv.1 子弹伤害
                e.bullet.setArmorDepth(30);     // Lv.1 子弹穿甲值
                e.scoreValue = 100; e.xpValue = 250;
                e.buffDropRate = 0.20f;        // 20% 概率掉落 Buff
                e.movePattern = MovePattern.STRAIGHT;
                e.width = 40; e.height = 40;
                break;

            case LEVEL_2:
                // 机动型：中血量、低护甲、较快移速、2路扩散弹幕
                e.maxHp = 1500; e.hp = 1500; e.armor = 20;
                e.moveSpeed = 3.0f; e.atkSpeed = 0.9f;
                e.bullet = new NormalBullet(0, 0, false);
                e.bullet.setAttack(500);
                e.bullet.setArmorDepth(50);
                e.scoreValue = 300; e.xpValue = 680;
                e.buffDropRate = 0.35f;
                // 随机选择正弦波或直线移动
                e.movePattern = Math.random() < 0.5 ? MovePattern.SINE : MovePattern.STRAIGHT;
                e.amplitude = 50; e.frequency = 0.04f;
                e.width = 44; e.height = 44;
                break;

            case LEVEL_3:
                // 精英型：高血量、中护甲、中移速、3路扇形弹幕
                e.maxHp = 3000; e.hp = 3000; e.armor = 80;
                e.moveSpeed = 2.5f; e.atkSpeed = 0.6f;
                e.bullet = new NormalBullet(0, 0, false);
                e.bullet.setAttack(1000);
                e.bullet.setArmorDepth(100);
                e.scoreValue = 600; e.xpValue = 1200;
                e.buffDropRate = 0.45f;
                // 随机选择正弦波或折线移动
                e.movePattern = Math.random() < 0.5 ? MovePattern.SINE : MovePattern.ZIGZAG;
                e.amplitude = 70; e.frequency = 0.05f;
                e.width = 48; e.height = 48;
                break;

            case LEVEL_4:
                // 王牌型：高血量、高护甲、高移速、3路+穿甲弹、追踪摇摆
                e.maxHp = 5000; e.hp = 5000; e.armor = 150;
                e.moveSpeed = 3.5f; e.atkSpeed = 3.0f;
                e.bullet = new NormalBullet(0, 0, false);
                e.bullet.setAttack(5000);
                e.bullet.setArmorDepth(100);
                e.scoreValue = 1000; e.xpValue = 2000;
                e.buffDropRate = 0.55f;
                e.movePattern = MovePattern.CHASE;
                e.width = 52; e.height = 52;
                break;

            default:
                break;  // BOSS 不在此方法处理
        }
        return e;
    }

    /**
     * 创建 Boss 敌机。
     *
     * <p>Boss 1（雷霆先锋）：HP 300000, 护甲 100, 扇形 5 发, 环形弹幕, Phase 3 双旋转扫射</p>
     * <p>Boss 2（雷霆霸主）：HP 600000, 护甲 200, 扇形 8 发, 双旋转扫射, 大型红弹, 巨型蓝弹</p>
     *
     * @param bossLevel Boss 编号（1=雷霆先锋, 2=雷霆霸主）
     * @return 配置完成的 Boss 敌机实例
     */
    public static EnemyPlane createBoss(int bossLevel) {
        EnemyPlane e = new EnemyPlane();
        e.enemyLevel = EnemyLevel.BOSS;
        e.bossLevel = bossLevel;
        e.bossPhase = 1;            // 初始 Phase 1
        e.movePattern = MovePattern.BOSS_SWAY;
        e.scoreValue = 5000;
        e.xpValue = 10000;
        e.buffDropRate = 1.0f;     // Boss 必掉 Buff

        if (bossLevel == 1) {
            // ── Boss 1: 雷霆先锋 ──
            e.maxHp = 300000; e.hp = 300000; e.armor = 100;
            e.moveSpeed = 1.0f; e.atkSpeed = 0.2f;   // 扇形弹幕 0.2s/发
            e.bullet = new NormalBullet(0, 0, false);
            e.bullet.setAttack(500);
            e.bullet.setArmorDepth(80);
            e.width = 80; e.height = 80;              // Boss 体积更大
        } else {
            // ── Boss 2: 雷霆霸主 ──
            e.maxHp = 600000; e.hp = 600000; e.armor = 200;
            e.moveSpeed = 0.5f; e.atkSpeed = 0.6f;
            e.bullet = new NormalBullet(0, 0, false);
            e.bullet.setAttack(1000);
            e.bullet.setArmorDepth(160);
            e.width = 90; e.height = 90;
        }
        return e;
    }

    // ═══════════════════════════════════════════════
    // Plane 抽象方法实现 — move()
    // ═══════════════════════════════════════════════

    /**
     * 敌机每帧移动 — 根据移动模式和当前状态更新位置。
     *
     * <h3>执行优先级（从高到低）</h3>
     * <ol>
     *   <li><b>Boss 入场动画：</b>从顶部缓入到 y≈120，小幅摇摆</li>
     *   <li><b>冲撞预警：</b>Boss 静止，倒计时结束后进入冲撞</li>
     *   <li><b>冲撞中：</b>按 chargeSpeedX/Y 高速移动</li>
     *   <li><b>返回原位：</b>冲撞结束后移回原始位置</li>
     *   <li><b>普通移动模式：</b>STRAIGHT / SINE / ZIGZAG / CHASE / BOSS_SWAY</li>
     * </ol>
     *
     * @param dx 未使用
     * @param dy 未使用
     */
    @Override
    public void move(float dx, float dy) {
        // ── 1. Boss 入场动画（首次出场时从顶部缓入） ──
        if (enemyLevel == EnemyLevel.BOSS && !entered) {
            entryTimer += 1.0f / 60.0f;          // 每帧 1/60 秒
            y += moveSpeed;                       // 缓入下降
            if (y >= 120) {
                y = 120;
                entered = true;                   // 入场完毕
            }
            x += Math.sin(entryTimer * 3) * 0.5f; // 小幅左右摇摆
            return;
        }

        // ── 2. 冲撞预警阶段：Boss 完全静止，倒计时 2 秒 ──
        if (isChargeWarning) {
            chargeWarningTimer -= 1.0f / 60.0f;
            if (chargeWarningTimer <= 0) {
                // 预警结束 → 进入冲撞
                isChargeWarning = false;
                isCharging = true;
                chargeTimer = 1.5f;                         // 最大冲撞持续时间
                chargeSpeedX = chargeDirX * 15.0f;          // 冲撞速度 15 px/帧
                chargeSpeedY = chargeDirY * 15.0f;
            }
            return;     // 预警期间不移动
        }

        // ── 3. 冲撞中：按预设方向高速移动 ──
        if (isCharging) {
            x += chargeSpeedX;
            y += chargeSpeedY;
            chargeTimer -= 1.0f / 60.0f;                    // 倒计时

            // 冲撞结束条件：超时 或 撞击屏幕边界
            boolean hitBoundary = (y > 650 || y < 50 || x < 0 || x > 800);
            if (chargeTimer <= 0 || hitBoundary) {
                isCharging = false;
                if (hitBoundary) {
                    applyChargeBoundaryDamage();             // 撞边界扣 800 HP
                }
                isReturning = true;                          // 进入返回阶段
            }
            return;
        }

        // ── 4. 返回原始位置：逐帧向原始坐标移动 ──
        if (isReturning) {
            float toOriginX = chargeOriginX - x;
            float toOriginY = chargeOriginY - y;
            float dist = (float) Math.sqrt(toOriginX * toOriginX + toOriginY * toOriginY);

            if (dist < returnSpeed) {
                // 足够接近 → 直接归位
                x = chargeOriginX;
                y = chargeOriginY;
                isReturning = false;
            } else {
                // 按固定速度移回原点
                float moveX = toOriginX / dist * returnSpeed;
                float moveY = toOriginY / dist * returnSpeed;
                x += moveX;
                y += moveY;
            }
            return;
        }

        // ── 5. 普通移动模式 ──
        switch (movePattern) {
            case STRAIGHT:
                // 直线下降
                y += moveSpeed;
                break;

            case SINE:
                // 正弦波左右摇摆 + 下降
                moveTimer += frequency;
                x = initialX + (float) Math.sin(moveTimer) * amplitude;
                y += moveSpeed;
                break;

            case ZIGZAG:
                // 折线：sin 峰值期快速平移，谷值期缓降
                moveTimer += frequency;
                if (Math.sin(moveTimer) > 0.7) {
                    x += moveSpeed * 1.5f;      // 正半周→向右
                } else if (Math.sin(moveTimer) < -0.7) {
                    x -= moveSpeed * 1.5f;      // 负半周→向左
                }
                y += moveSpeed * 0.7f;           // 缓降
                break;

            case CHASE:
                // 追踪摇摆（Lv.4 王牌型）：横向追踪 + 缓降
                moveTimer += 0.02f;
                x += Math.sin(moveTimer * 2) * moveSpeed * 0.8f;  // 左右摇摆
                y += moveSpeed * 0.5f;                              // 缓降
                break;

            case BOSS_SWAY:
                // Boss X 轴冲刺模式：间隔 5~10s 随机冲刺一次，其余时间慢速漂移
                if (enemyLevel != EnemyLevel.BOSS || !entered) break;

                bossMoveDashTimer += 1.0f / 60.0f;

                if (bossIsDashing) {
                    // 冲刺阶段：沿 X 方向高速移动（4 px/帧）
                    x += bossDashXDir * 4.0f;
                    bossDashDuration += 1.0f / 60.0f;

                    // 碰到边界或超过 0.4s 结束冲刺
                    if (x <= width / 2 + 10 || x >= 800 - width / 2 - 10
                        || bossDashDuration >= 0.4f) {
                        bossIsDashing = false;
                        bossMoveDashTimer = 0;
                        bossDashInterval = 5.0f + (float)Math.random() * 5.0f; // 5~10s

                        // 反弹方向
                        if (x <= width / 2 + 10) bossDashXDir = 1;
                        else if (x >= 800 - width / 2 - 10) bossDashXDir = -1;
                    }
                } else if (bossMoveDashTimer >= bossDashInterval) {
                    // 触发冲刺
                    bossIsDashing = true;
                    bossDashDuration = 0;
                    bossMoveDashTimer = 0;
                    if (bossDashXDir == 0) {
                        bossDashXDir = dashRandom.nextBoolean() ? 1 : -1;
                    }
                } else {
                    // 慢速漂移（等待下次冲刺）
                    float driftDir = bossDashXDir != 0 ? bossDashXDir : 1;
                    x += moveSpeed * driftDir * 0.3f;
                }
                break;
        }

        // ── 边界限制：防止敌机移出屏幕 ──
        if (x < width / 2) x = width / 2;
        if (x > 800 - width / 2) x = 800 - width / 2;
    }

    /** 射击由 GameEngine.tryShoot() 驱动 */
    @Override
    public void shoot() {
        // 射击由 GameEngine 每帧调用 tryShoot()
    }

    /**
     * AI 决定射击（由 GameEngine 每帧调用）。
     *
     * <p>射击冷却受 atkSpeed 控制，不同等级的弹幕模式：</p>
     * <ul>
     *   <li><b>Lv.1:</b> 单发直射（正下方）</li>
     *   <li><b>Lv.2:</b> 2 路扩散（±10°）</li>
     *   <li><b>Lv.3:</b> 3 路扇形（-15°/0°/+15°）</li>
     *   <li><b>Lv.4:</b> 3 路 + 中心穿甲弹（伤害 ×1.5）</li>
     *   <li><b>Boss:</b> 扇形弹幕（受 atkSpeed 控制）+ 特殊弹幕（独立计时）</li>
     * </ul>
     *
     * @param gameTime 当前游戏时间（秒）
     * @return 本帧发射的子弹列表（可能为空）
     */
    public java.util.List<Bullet> tryShoot(float gameTime) {
        java.util.List<Bullet> bullets = new java.util.ArrayList<>();

        // 冷却检查
        shootTimer += 1.0f / 60.0f;
        float effectiveAtkSpeed = getEffectiveAtkSpeed();
        if (shootTimer < effectiveAtkSpeed) return bullets;
        shootTimer = 0;     // 重置冷却

        // 冲撞中不射击
        if (isCharging) return bullets;

        switch (enemyLevel) {
            case LEVEL_1:
                // 单发直射（正下方，角度 90°）
                bullets.add(new NormalBullet(x, y + height / 2, false));
                break;

            case LEVEL_2:
                // 2 路扩散（±10°），左右略微偏移
                bullets.add(createAngledBullet(x - 6, y + height / 2, -10, false));
                bullets.add(createAngledBullet(x + 6, y + height / 2, 10, false));
                break;

            case LEVEL_3:
                // 3 路扇形（-15°/0°/+15°）
                bullets.add(createAngledBullet(x, y + height / 2, 0, false));     // 中心
                bullets.add(createAngledBullet(x - 8, y + height / 2, -15, false));  // 左
                bullets.add(createAngledBullet(x + 8, y + height / 2, 15, false));   // 右
                break;

            case LEVEL_4:
                // 3 路普通弹 + 中心穿甲弹（伤害 ×1.5）
                bullets.add(new NormalBullet(x, y + height / 2, false));
                bullets.add(createAngledBullet(x - 10, y + height / 2, -12, false));
                bullets.add(createAngledBullet(x + 10, y + height / 2, 12, false));
                // 穿甲弹中心位置，伤害 ×1.5
                ArmorBullet centerBullet = new ArmorBullet(x, y + height / 2, false);
                centerBullet.setAttack((int)(centerBullet.getAttack() * 1.5f));
                bullets.add(centerBullet);
                break;

            case BOSS:
                // Boss 基础扇形弹幕（受 atkSpeed 控制）
                bullets.addAll(bossBaseAttack());
                // 特殊弹幕由 updateBossTimers() 每帧独立处理
                break;
        }

        return bullets;
    }

    /**
     * 每帧更新 Boss 特殊弹幕计时器并返回本帧产生的特殊弹幕子弹。
     *
     * <p>此方法由 GameEngine 每帧调用，独立于基础射击冷却。
     * Boss 的特殊弹幕（环形、旋转扫射、大型红弹、巨型蓝弹等）
     * 都有各自的计时器，不受 atkSpeed 影响。</p>
     *
     * @param dt 帧时间增量（秒），等于 1/60
     * @return 本帧生成的特殊弹幕子弹列表
     */
    public java.util.List<Bullet> updateBossTimers(float dt) {
        java.util.List<Bullet> bullets = new java.util.ArrayList<>();
        if (enemyLevel != EnemyLevel.BOSS) return bullets;

        if (bossLevel == 1) {
            boss1Specials(bullets, dt);     // Boss 1 特殊弹幕
        } else {
            boss2Specials(bullets, dt);     // Boss 2 特殊弹幕
        }
        return bullets;
    }

    /**
     * Boss 基础扇形弹幕（受 shootTimer/atkSpeed 冷却控制）。
     *
     * <p>Boss 1: 全阶段扇形弹幕，-50°~50°，Phase 1/2 为 5 发，Phase 3 升级为 10 发</p>
     * <p>Boss 2: 全阶段扇形弹幕，Phase 1 为 8 发(-80°~80°)，Phase 2+ 为 12 发(-60°~60°)</p>
     */
    private java.util.List<Bullet> bossBaseAttack() {
        java.util.List<Bullet> bullets = new java.util.ArrayList<>();

        if (bossLevel == 1) {
            // Boss 1: 扇形弹幕角度 -50° ~ 50° (spreadAngle = 100°)
            int fanCount = bossPhase >= 3 ? 10 : 5;
            float spreadAngle = 100f;
            float angleStep = fanCount > 1 ? spreadAngle / (fanCount - 1) : 0;
            for (int i = 0; i < fanCount; i++) {
                float deg = 90f - spreadAngle / 2 + angleStep * i;
                double rad = Math.toRadians(deg);
                Bullet b = new NormalBullet(x, y + height / 2, false);
                b.setAttack(500); b.setArmorDepth(80);
                b.setColor(java.awt.Color.ORANGE);  // 扇形弹幕：橙色
                b.setDirection((float)rad, 4.0f);
                bullets.add(b);
            }
        } else {
            // Boss 2: 扇形弹幕
            int fanCount;
            float spreadAngle;
            if (bossPhase >= 2) {
                fanCount = 12;      // Phase 2+: 12 发
                spreadAngle = 120f; // -60° ~ 60°
            } else {
                fanCount = 8;       // Phase 1: 8 发
                spreadAngle = 160f; // -80° ~ 80°
            }
            float angleStep = spreadAngle / (fanCount - 1);
            for (int i = 0; i < fanCount; i++) {
                float deg = 90f - spreadAngle / 2 + angleStep * i;
                double rad = Math.toRadians(deg);
                Bullet fb = new NormalBullet(x, y + height / 2, false);
                fb.setAttack(500); fb.setArmorDepth(80);
                fb.setColor(java.awt.Color.ORANGE);
                fb.setDirection((float)rad, 4.0f);
                bullets.add(fb);
            }
        }
        return bullets;
    }

    // ═══════════════════════════════════════════════
    // Boss 1 特殊弹幕
    // ═══════════════════════════════════════════════

    /**
     * Boss 1（雷霆先锋）特殊弹幕 — 每帧独立计时。
     *
     * <p>包含：</p>
     * <ul>
     *   <li><b>全阶段：</b>环形弹幕（每 2.5s，30 发全周，红色）</li>
     *   <li><b>Phase 2:</b> 冲撞攻击（每 8s 触发，预警 2s）</li>
     *   <li><b>Phase 3:</b> 双旋转扫射 A+B（60°→-60° 往复，60°/s，蓝色，0.15s/发）</li>
     * </ul>
     */
    private void boss1Specials(java.util.List<Bullet> bullets, float dt) {
        // ── 环形弹幕：每 2.5s 发射 30 发全周红色子弹（每 12° 一发） ──
        bossRingTimer += dt;
        if (bossRingTimer >= 2.5f) {
            bossRingTimer -= 2.5f;
            for (int i = 0; i < 30; i++) {
                double rad = Math.toRadians(i * 12.0);  // 360° / 30 = 12°
                Bullet rb = createDirectionalBullet(x, y, (float)rad, 4.0f, 500, 80, java.awt.Color.RED);
                rb.setBulletSize(6.0f);
                bullets.add(rb);
            }
        }

        // ── Phase 2: 冲撞计时（仅 Phase 2，Phase 3 无冲撞） ──
        if (bossPhase == 2 && !isCharging && !isChargeWarning && !isReturning) {
            bossChargeTimer += dt;
        }

        // ── Phase 3: 双旋转扫射 ──
        if (bossPhase >= 3) {
            // 扫射 A: 60° → -60° 往复，旋转速度 60°/s
            bossSweepAngleA += bossSweepDirA * 60.0f * dt;
            if (bossSweepAngleA >= 60)  { bossSweepAngleA = 60;  bossSweepDirA = -1; }
            if (bossSweepAngleA <= -60) { bossSweepAngleA = -60; bossSweepDirA = 1; }

            bossSweepFireTimerA += dt;
            if (bossSweepFireTimerA >= 0.15f) {     // 0.15s/发
                bossSweepFireTimerA -= 0.15f;
                double rad = Math.toRadians(90 + bossSweepAngleA);
                Bullet sw = createDirectionalBullet(x + 10, y + height / 2, (float)rad,
                    4.0f, 800, 100, java.awt.Color.BLUE);
                sw.setBulletSize(7.0f);
                bullets.add(sw);
            }

            // 扫射 B: -60° → 60° 往复（与 A 方向相反）
            bossSweepAngleB += bossSweepDirB * 60.0f * dt;
            if (bossSweepAngleB >= 60)  { bossSweepAngleB = 60;  bossSweepDirB = -1; }
            if (bossSweepAngleB <= -60) { bossSweepAngleB = -60; bossSweepDirB = 1; }

            bossSweepFireTimerB += dt;
            if (bossSweepFireTimerB >= 0.15f) {
                bossSweepFireTimerB -= 0.15f;
                double rad = Math.toRadians(90 + bossSweepAngleB);
                Bullet sw = createDirectionalBullet(x - 10, y + height / 2, (float)rad,
                    4.0f, 800, 100, java.awt.Color.BLUE);
                sw.setBulletSize(7.0f);
                bullets.add(sw);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // Boss 2 特殊弹幕
    // ═══════════════════════════════════════════════

    /**
     * Boss 2（雷霆霸主）特殊弹幕 — 最复杂的弹幕系统。
     *
     * <p>包含：</p>
     * <ul>
     *   <li><b>双旋转扫射：</b>Phase 1(0.15s/发, 60°/s), Phase 2(0.1s/发, 120°/s), Phase 3(0.05s/发, 120°/s)</li>
     *   <li><b>大型红弹（爆炸弹）：</b>Phase 1(每 8s, 5 发), Phase 2+(每 5s, 5 发×3 连发)</li>
     *   <li><b>环形弹幕：</b>Phase 2(每 3~5s), Phase 3(每 3~5s ×3 连发, 间隔 0.3s)</li>
     *   <li><b>冲撞：</b>Phase 2(每 10s)</li>
     *   <li><b>巨型蓝弹：</b>Phase 3(每 20s, 伤害 3000, 穿甲 300)</li>
     *   <li><b>生命恢复：</b>Phase 3 首次进入时立即恢复 20% 最大 HP（一次性）</li>
     * </ul>
     */
    private void boss2Specials(java.util.List<Bullet> bullets, float dt) {
        // 旋转速度：Phase 1→1.0, Phase 2+→1.3
        float sweepSpeed = bossPhase >= 2 ? 1.3f : 1.0f;
        // 射击间隔：Phase 1→0.15s, Phase 2→0.1s, Phase 3→0.05s
        float sweepFireInterval = bossPhase >= 3 ? 0.05f : (bossPhase >= 2 ? 0.1f : 0.15f);
        int sweepDmg = bossPhase >= 2 ? 1500 : 1000;
        // Phase 2/3 旋转速度加倍：60°/s → 120°/s
        float rotMultiplier = (bossPhase >= 2) ? 120.0f : 60.0f;

        // ── 双旋转扫射角度更新（±60° 范围往复） ──
        bossSweepAngleA += bossSweepDirA * sweepSpeed * rotMultiplier * dt;
        bossSweepAngleB += bossSweepDirB * sweepSpeed * rotMultiplier * dt;
        if (bossSweepAngleA >= 60)  { bossSweepAngleA = 60;  bossSweepDirA = -1; }
        if (bossSweepAngleA <= -60) { bossSweepAngleA = -60; bossSweepDirA = 1; }
        if (bossSweepAngleB >= 60)  { bossSweepAngleB = 60;  bossSweepDirB = -1; }
        if (bossSweepAngleB <= -60) { bossSweepAngleB = -60; bossSweepDirB = 1; }

        // 双旋转扫射发射
        bossSweepFireTimerA += dt;
        if (bossSweepFireTimerA >= sweepFireInterval) {
            bossSweepFireTimerA -= sweepFireInterval;
            double radA = Math.toRadians(90 + bossSweepAngleA);
            double radB = Math.toRadians(90 + bossSweepAngleB);
            Bullet sa = createDirectionalBullet(x + 10, y + height / 2, (float)radA,
                4.0f, sweepDmg, 160, java.awt.Color.BLUE);
            Bullet sb = createDirectionalBullet(x - 10, y + height / 2, (float)radB,
                4.0f, sweepDmg, 160, java.awt.Color.BLUE);
            sa.setBulletSize(6.0f); sb.setBulletSize(6.0f);
            bullets.add(sa); bullets.add(sb);
        }

        // ── 大型红弹（爆炸弹） ──
        float bombInterval = bossPhase >= 2 ? 5.0f : 8.0f;
        int bombBursts = bossPhase >= 2 ? 3 : 1;    // Phase 2+ 每轮连发 3 次
        bossBombTimer += dt;
        if (bossBombTimer >= bombInterval) {
            bossBombTimer -= bombInterval;
            bossBombBurstCount = bombBursts;
        }
        if (bossBombBurstCount > 0) {
            // 发射 5 发，随机方向
            for (int i = 0; i < 5; i++) {
                double angle = Math.random() * Math.PI * 2;     // 随机 360° 方向
                NormalBullet bomb = new NormalBullet(x, y + height / 2, false);
                bomb.setBulletSize(15.0f);                       // 大型子弹
                bomb.setColor(java.awt.Color.RED);
                bomb.setDirection((float)angle, 3.0f);           // 慢速扩散
                bomb.setBomb(true);                              // 标记为爆炸弹
                bomb.setBombTimer(0.8f);                         // 移动 0.8s 后停下
                bomb.setBombFuse(1.0f);                          // 就位 1s 后引爆
                bomb.setBombRadius(80);                          // 爆炸半径 80px
                bomb.setBombDamage(1500);                        // 爆炸伤害 1500
                bullets.add(bomb);
            }
            bossBombBurstCount--;    // 连发计数 -1
        }

        // ── Phase 2+: 环形弹幕 ──
        if (bossPhase >= 2) {
            boss2RingTimer += dt;
            if (bossPhase >= 3) {
                // Phase 3: 环形弹幕 3 连发，每次间隔 0.3s
                if (boss2RingBurstCount > 0) {
                    boss2RingBurstTimer += dt;
                    if (boss2RingBurstTimer >= 0.3f) {
                        boss2RingBurstTimer -= 0.3f;
                        fireRingBarrage(bullets);
                        boss2RingBurstCount--;
                    }
                } else if (boss2RingTimer >= boss2RingInterval) {
                    // 触发新一轮 3 连发
                    boss2RingTimer = 0;
                    boss2RingBurstCount = 3;
                    boss2RingBurstTimer = 0;
                    boss2RingInterval = 3.0f + (float)Math.random() * 2.0f;
                }
            } else {
                // Phase 2: 普通环形弹幕，每 3~5s 一次
                if (boss2RingTimer >= boss2RingInterval) {
                    boss2RingTimer = 0;
                    boss2RingInterval = 3.0f + (float)Math.random() * 2.0f;
                    fireRingBarrage(bullets);
                }
            }
        }

        // ── Phase 2: 冲撞（Boss 2 仅 Phase 2 有） ──
        if (bossPhase == 2 && !isCharging && !isChargeWarning && !isReturning) {
            bossChargeTimer += dt;
        }

        // ── Phase 3: 巨型蓝弹（每 20s 一发，伤害 3000，穿甲 300） ──
        if (bossPhase >= 3) {
            bossGiantBlueTimer += dt;
            if (bossGiantBlueTimer >= 20.0f) {
                bossGiantBlueTimer -= 20.0f;
                Bullet giantBlue = new NormalBullet(x, y + height / 2, false);
                giantBlue.setAttack(3000);
                giantBlue.setArmorDepth(300);
                giantBlue.setBulletSize(20.0f);                  // 巨型尺寸
                giantBlue.setColor(java.awt.Color.BLUE);
                giantBlue.setDirection((float)Math.toRadians(90), 10.0f);
                bullets.add(giantBlue);
            }
        }
    }

    /**
     * 发射环形弹幕：360° 全周发射 30 发红色子弹（每 12° 一发）。
     */
    private void fireRingBarrage(java.util.List<Bullet> bullets) {
        for (int i = 0; i < 30; i++) {
            double rad = Math.toRadians(i * 12.0);
            Bullet rb = createDirectionalBullet(x, y, (float)rad, 4.0f, 500, 80, java.awt.Color.RED);
            rb.setBulletSize(6.0f);
            bullets.add(rb);
        }
    }

    /**
     * 尝试触发冲撞攻击（由 GameEngine 每帧调用）。
     *
     * <p>触发条件：不在冲撞/预警/返回状态，且冲撞计时器 ≥ 间隔阈值。
     * Boss 1 间隔 8s，Boss 2 间隔 10s。</p>
     *
     * @param playerX 玩家当前 X 坐标（用于记录冲撞目标）
     * @param playerY 玩家当前 Y 坐标（用于记录冲撞目标）
     */
    public void tryTriggerCharge(float playerX, float playerY) {
        float chargeInterval = (bossLevel == 2) ? 10.0f : 8.0f;
        if (!isCharging && !isChargeWarning && !isReturning
            && bossChargeTimer >= chargeInterval) {
            bossChargeTimer = 0;
            startCharge(playerX, playerY);
        }
    }

    /**
     * 创建指定角度的子弹（以正下方 90° 为基准，偏移 angleDeg 度）。
     */
    private Bullet createAngledBullet(float bx, float by, float angleDeg, boolean isPlayer) {
        NormalBullet b = new NormalBullet(bx, by, isPlayer);
        double rad = Math.toRadians(90 + angleDeg);  // 90° = 正下方
        b.setDirection((float)rad, b.getSpeed());
        return b;
    }

    /**
     * 创建指定方向向量的子弹（带完整属性参数）。
     */
    private Bullet createDirectionalBullet(float bx, float by, float rad, float spd,
                                            int dmg, int armor, java.awt.Color color) {
        NormalBullet b = new NormalBullet(bx, by, false);
        b.setAttack(dmg > 0 ? dmg : 500);
        b.setArmorDepth(armor > 0 ? armor : 80);
        b.setDirection(rad, spd);
        b.setColor(color);
        return b;
    }

    // ═══════════════════════════════════════════════
    // 冲撞攻击系统
    // ═══════════════════════════════════════════════

    /**
     * 开始冲撞流程 — 记录玩家位置，进入 2 秒预警阶段。
     *
     * <p>预警阶段 Boss 完全静止，显示红色虚线指向记录的玩家位置 + 十字标记。
     * 预警结束后沿固定方向以 15 px/帧高速冲出。</p>
     *
     * @param playerX 预警开始时的玩家 X 坐标
     * @param playerY 预警开始时的玩家 Y 坐标
     */
    private void startCharge(float playerX, float playerY) {
        // 保存原始位置（冲撞结束后返回此处）
        chargeOriginX = x;
        chargeOriginY = y;

        // 记录玩家位置（预警中不变，冲撞时沿此方向）
        chargeTargetX = playerX;
        chargeTargetY = playerY;

        // 计算指向记录位置的固定冲撞方向单位向量
        float dx = chargeTargetX - x;
        float dy = chargeTargetY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 0.001f) {
            chargeDirX = dx / dist;
            chargeDirY = dy / dist;
        } else {
            // 玩家与 Boss 重合时默认向下冲撞
            chargeDirX = 0;
            chargeDirY = 1;
        }

        // 进入预警阶段
        isChargeWarning = true;
        chargeWarningTimer = CHARGE_WARNING_DURATION;
        isCharging = false;
        isReturning = false;
    }

    /**
     * 设置/更新冲撞目标方向（由 GameEngine 或 TestBossVisual 调用）。
     *
     * <p>首次调用时初始化冲撞状态（记录原始位置、设置冲撞持续时间）。
     * 后续每帧可更新追踪方向（速度重新计算，不重置计时器）。</p>
     *
     * @param targetX 目标 X 坐标
     * @param targetY 目标 Y 坐标
     */
    public void startChargeToward(float targetX, float targetY) {
        if (!isCharging) {
            // 首次进入冲撞：保存原始位置并初始化
            chargeOriginX = x;
            chargeOriginY = y;
            isCharging = true;
            isReturning = false;
            chargeTimer = 1.5f;     // 最大冲撞 1.5s
        }
        // 每帧更新方向（不重置计时器）
        float dx = targetX - x;
        float dy = targetY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float speed = 15.0f;        // 冲撞速度
        if (dist > 0) {
            chargeSpeedX = dx / dist * speed;
            chargeSpeedY = dy / dist * speed;
        } else {
            chargeSpeedX = 0;
            chargeSpeedY = speed;
        }
    }

    /**
     * 检查并更新 Boss 阶段（根据 HP 百分比自动切换）。
     *
     * <p>阶段切换条件：</p>
     * <ul>
     *   <li>HP ≤ 20% → Phase 3（Boss 2 同时触发一次性 20% 回血）</li>
     *   <li>HP ≤ 66% → Phase 2</li>
     * </ul>
     */
    public void checkBossPhase() {
        if (enemyLevel != EnemyLevel.BOSS) return;

        float hpPercent = (float) hp / maxHp;
        int oldPhase = bossPhase;

        if (hpPercent <= 0.20f && bossPhase < 3) {
            bossPhase = 3;
            // Boss 2 Phase 3 首次进入时恢复 20% 最大 HP（仅一次）
            if (bossLevel == 2 && !bossHealed) {
                bossHealed = true;
                hp = Math.min(maxHp, hp + (int)(maxHp * 0.20f));
            }
        } else if (hpPercent <= 0.66f && bossPhase < 2) {
            bossPhase = 2;
        }

        // 阶段切换时触发事件（由外部监听）
        // if (oldPhase != bossPhase) { fireEvent(BOSS_PHASE_CHANGE); }
    }

    // ═══════════════════════════════════════════════
    // Plane 其余抽象方法实现
    // ═══════════════════════════════════════════════

    /**
     * 受到伤害 — 委托给基类的 applyDamage 并检查阶段切换。
     */
    @Override
    public void takeDamage(int damage) {
        applyDamage(damage);
        checkBossPhase();   // 受伤后检查是否触发阶段切换
    }

    /** 应用 Buff */
    @Override
    public void applyBuff(Buff buff) {
        buffs.add(buff);
        buff.apply(this);
    }

    /** 过期 Buff 由 checkExpiredBuffs(gameTime) 处理 */
    @Override
    public void removeExpiredBuffs() {
        // 由 checkExpiredBuffs(gameTime) 统一管理
    }

    /** @return true 如果 alive 标志为 false */
    @Override
    public boolean isDead() {
        return !alive;
    }

    // ═══════════════════════════════════════════════
    // Boss 碰撞伤害
    // ═══════════════════════════════════════════════

    /** @return Boss 冲撞对玩家造成的伤害（固定 1500） */
    public int getChargeDamage() {
        return 1500;
    }

    /**
     * Boss 冲撞自身伤害 — 撞到玩家时自身扣 500 HP。
     */
    public void applyChargeSelfDamage() {
        hp = Math.max(0, hp - 500);
        if (hp <= 0) alive = false;
        checkBossPhase();
    }

    /**
     * Boss 冲撞边界自身伤害 — 撞到屏幕边界时自身扣 800 HP。
     */
    public void applyChargeBoundaryDamage() {
        hp = Math.max(0, hp - 800);
        if (hp <= 0) alive = false;
        checkBossPhase();
    }

    /**
     * 冲撞命中后停止冲撞并开始移回原位（撞到玩家时调用）。
     */
    public void stopChargeAndReturn() {
        if (isCharging) {
            isCharging = false;
            isReturning = true;     // 触发返回阶段
        }
    }

    // ═══════════════════════════════════════════════
    // Getter / Setter
    // ═══════════════════════════════════════════════

    public EnemyLevel getEnemyLevel() { return enemyLevel; }
    public MovePattern getMovePattern() { return movePattern; }
    public void setMovePattern(MovePattern p) { this.movePattern = p; }
    public int getScoreValue() { return scoreValue; }
    public int getXpValue() { return xpValue; }
    public float getBuffDropRate() { return buffDropRate; }
    public int getBossPhase() { return bossPhase; }
    public int getBossLevel() { return bossLevel; }
    /** @return 是否为 Boss 敌机 */
    public boolean isBoss() { return enemyLevel == EnemyLevel.BOSS; }
    /** @return Boss 入场动画是否已完成 */
    public boolean hasEntered() { return entered; }
    /** @return 是否正在冲撞中 */
    public boolean isCharging() { return isCharging; }
    /** @return 是否在冲撞预警阶段 */
    public boolean isChargeWarning() { return isChargeWarning; }
    /** @return 是否正在返回原始位置 */
    public boolean isReturning() { return isReturning; }
    /** @return 冲撞方向单位向量 X */
    public float getChargeDirX() { return chargeDirX; }
    /** @return 冲撞方向单位向量 Y */
    public float getChargeDirY() { return chargeDirY; }
    /** @return 冲撞前原始 X 坐标 */
    public float getChargeOriginX() { return chargeOriginX; }
    /** @return 冲撞前原始 Y 坐标 */
    public float getChargeOriginY() { return chargeOriginY; }
    /** @return 冲撞目标 X 坐标（记录的玩家位置） */
    public float getChargeTargetX() { return chargeTargetX; }
    /** @return 冲撞目标 Y 坐标（记录的玩家位置） */
    public float getChargeTargetY() { return chargeTargetY; }
    /** 设置敌机初始位置（X 和 initialX） */
    public void setInitialX(float x) { this.initialX = x; this.x = x; }
    public float getMoveTimer() { return moveTimer; }
    public float getShootTimer() { return shootTimer; }
    public void setShootTimer(float t) { this.shootTimer = t; }
}
