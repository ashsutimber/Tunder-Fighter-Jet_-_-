package Controller.CoreController;

import Entity.Bullet.*;
import Entity.Plane.*;
import Entity.Buff.*;
import Entity.Buff.BuffDrop;
import Entity.Skill.*;
import Controller.Coreiterface.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Random;

/**
 * <h1>GameEngine — 游戏主循环引擎（游戏的心脏）</h1>
 *
 * <p>GameEngine 是整个游戏的核心控制器，以 <b>60 FPS 主循环</b> 驱动游戏的
 * 完整生命周期。它通过 Swing Timer 定时触发，每帧执行 update() + repaint()，
 * 协调所有子系统的运转。</p>
 *
 * <p>作为游戏的总指挥，GameEngine 管理所有实体列表（玩家、敌机、子弹、Buff球、
 * 爆炸效果）、处理碰撞检测、调度波次系统、驱动升级卡系统、激活技能效果。
 * 它也是 {@link GameEventListener} 的实现者，监听并响应关键游戏事件。</p>
 *
 * <h2>游戏生命周期（5 个阶段）</h2>
 * <ol>
 *   <li><b>标题画面（isTitleScreen）：</b>显示操作说明，按空格进入飞机选择</li>
 *   <li><b>飞机选择（isPlaneSelecting）：</b>从 4 种飞机中选择 1 架</li>
 *   <li><b>游戏进行：</b>7 轮波次战斗，含 Boss 战，敌机生成/射击/碰撞/升级</li>
 *   <li><b>升级卡选择（isCardSelecting）：</b>随机 3 选 1，游戏暂停</li>
 *   <li><b>结束画面：</b>GameOver（玩家死亡）或 GameWin（通关），按空格重开</li>
 * </ol>
 *
 * <h2>60 FPS 主循环流程</h2>
 * <pre>
 * Swing Timer (每 1/60 秒)
 *   └─ actionPerformed()
 *       └─ update()
 *           ├─ 1. 玩家移动 + 射击（含自动开火 J 键）
 *           ├─ 2. 敌机生成调度（按波次比例随机等级）
 *           ├─ 3. 敌机移动 + 射击（含 Boss 特殊弹幕独立计时）
 *           ├─ 4. 子弹位置更新 + 爆炸弹引爆检测
 *           ├─ 5. 碰撞检测 &amp; 伤害计算（穿甲 + 暴击一站式）
 *           ├─ 6. Buff 掉落球更新 &amp; 拾取判定
 *           ├─ 7. 清理死亡/出界实体
 *           ├─ 8. 波次切换判定（时间到 + 敌机清完 → 下一波 / Boss）
 *           ├─ 9. 升级检查 → 弹出升级卡（游戏暂停）
 *           ├─ 10. 技能效果更新 &amp; 过期 Buff 清理
 *           └─ 11. 游戏结束/通关检查
 *       └─ gamePanel.repaintGame()
 *           └─ paintComponent() → 渲染所有画面元素
 * </pre>
 *
 * <h2>波次系统（7 波）</h2>
 * <table border="1">
 *   <tr><th>波次</th><th>敌机生成比例</th><th>持续</th><th>备注</th></tr>
 *   <tr><td>Wave 1~3</td><td>Lv.1 100%</td><td>~30s</td><td>基础杂兵</td></tr>
 *   <tr><td>Wave 4</td><td>Lv.1(80%) : Lv.2(20%)</td><td>~30s</td><td>引入机动型</td></tr>
 *   <tr><td><b>Wave 5</b></td><td>Lv.1(60%):Lv.2(30%):Lv.3(10%)</td><td>—</td><td><b>Boss 1 战</b></td></tr>
 *   <tr><td>Wave 6</td><td>Lv.1(40%):Lv.2(30%):Lv.3(20%):Lv.4(10%)</td><td>~30s</td><td>引入王牌型</td></tr>
 *   <tr><td><b>Wave 7</b></td><td>Lv.1~4 混合</td><td>—</td><td><b>Boss 2 最终战</b></td></tr>
 * </table>
 *
 * <h2>碰撞检测规则</h2>
 * <ul>
 *   <li><b>玩家子弹 vs 敌机：</b>命中后子弹消失（非贯穿），伤害经穿甲+暴击计算</li>
 *   <li><b>敌机子弹 vs 玩家：</b>命中后子弹消失，触发 PLAYER_HIT 事件</li>
 *   <li><b>敌机机身 vs 玩家：</b>双方各扣 500 HP（非冲撞状态下）</li>
 *   <li><b>Boss 冲撞 vs 玩家：</b>玩家扣 1500 HP，Boss 自损 500 HP</li>
 * </ul>
 *
 * <h2>升级卡系统</h2>
 * <p>每次升级随机展示 3 张卡（从飞机卡池、武器卡池、及条件允许时的技能卡池中抽取）。
 * 选择后立即应用效果（属性修改、子弹变更、技能添加）。每 5 级获得 1 张额外技能卡。</p>
 *
 * @author 第7组
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see GamePanel   游戏界面面板（渲染输出）
 * @see InputControl 键盘输入处理
 * @see EventControl 事件监听/分发
 * @see Entity.Plane.PlayerPlane  玩家飞机
 * @see Entity.Plane.EnemyPlane   敌机
 */
public class GameEngine implements ActionListener, GameEventListener {

    // ── 核心组件 ──
    private GamePanel gamePanel;             // 游戏界面面板（渲染输出）
    private InputControl inputControl;       // 键盘输入控制器
    private EventControl eventControl;       // 事件监听/分发器

    // ── 实体列表 ──
    private PlayerPlane playerPlane;                              // 玩家飞机（唯一）
    private List<EnemyPlane> enemies = new ArrayList<>();         // 场景中所有敌机
    private List<Bullet> playerBullets = new ArrayList<>();       // 玩家发射的子弹
    private List<Bullet> enemyBullets = new ArrayList<>();        // 敌机发射的子弹

    // ── 游戏循环 ──
    private Timer gameTimer;                   // Swing 定时器，驱动主循环
    private static final int FPS = 60;         // 帧率
    private static final float DT = 1.0f / FPS; // 每帧时间增量 (delta time)

    // ── 游戏状态 ──
    private float gameTime = 0;              // 游戏已运行时间（秒）
    private int currentWave = 0;             // 当前波次 (1~7)
    private float waveTimer = 0;             // 当前波次已持续时间（秒）
    private int score = 0;                   // 玩家得分
    private boolean isGamePaused = false;    // 是否暂停（ESC 切换）
    private boolean isGameOver = false;      // 是否游戏结束
    private boolean isGameWin = false;       // 是否通关
    private boolean isCardSelecting = false; // 是否正在选升级卡（暂停游戏逻辑）
    private boolean isTitleScreen = true;    // 是否在标题画面
    private boolean isPlaneSelecting = false;// 是否在飞机选择界面

    // ── 升级卡系统 ──
    private List<UpgradeCard> currentCards = new ArrayList<>(); // 当前展示的升级卡（3张）
    private boolean hasPendingLevelUp = false;                  // 是否有待处理的升级
    private boolean hasPendingSkillCard = false;                // 是否有待处理的技能卡
    private int pendingLevelUps = 0;                            // 待处理升级次数

    // ── 敌机生成 ──
    private float enemySpawnTimer = 0;                               // 敌机生成计时器
    private static final float ENEMY_SPAWN_INTERVAL = 0.9f;         // 敌机生成间隔（秒）
    private int enemiesInWave = 0;                                   // 当前波次已生成敌机数
    private boolean bossSpawned = false;                             // Boss 是否已出场
    private boolean bossDefeated = false;                            // Boss 是否已被击败

    // ── 随机数 ──
    private Random random = new Random();                // 通用随机数生成器

    // ── Buff 掉落球 ──
    private List<BuffDrop> buffDrops = new ArrayList<>(); // 场景中的 Buff 掉落球

    // ── 爆炸效果 ──
    private List<Explosion> explosions = new ArrayList<>(); // 当前播放的爆炸效果列表

    // ═══════════════════════════════════════════════
    // 升级卡数据类
    // ═══════════════════════════════════════════════

    public enum CardType { SKILL, PLANE, WEAPON }

    public static class UpgradeCard {
        public String id;            // 卡牌编号 (S1~S9, P1~P9, W1~W16)
        public String name;          // 卡牌名称
        public String description;   // 效果描述
        public CardType type;        // 卡牌类型 (技能/飞机/武器)

        public UpgradeCard(String id, String name, String desc, CardType type) {
            this.id = id; this.name = name; this.description = desc; this.type = type;
        }

        @Override
        public String toString() { return name + ": " + description; }
    }

    // 所有升级卡（静态初始化，全局共享）
    private static List<UpgradeCard> allSkillCards = new ArrayList<>(); // 技能卡池 S1~S9
    private static List<UpgradeCard> allPlaneCards = new ArrayList<>(); // 飞机卡池 P1~P9
    private static List<UpgradeCard> allWeaponCards = new ArrayList<>();// 武器卡池 W1~W16

    static {
        // 技能卡 S1-S9
        allSkillCards.add(new UpgradeCard("S1", "雷霆瞬闪", "无敌闪避10s CD30s", CardType.SKILL));
        allSkillCards.add(new UpgradeCard("S2", "电磁护盾", "防御屏障1000HP CD30s", CardType.SKILL));
        allSkillCards.add(new UpgradeCard("S3", "聚变爆破", "全屏冲击波2000伤害 CD30s", CardType.SKILL));
        allSkillCards.add(new UpgradeCard("S4", "过载超频", "攻击+50% 速度+10% 间隔-50% 弹速+100% 10s CD30s", CardType.SKILL));
        allSkillCards.add(new UpgradeCard("S5", "雷霆裁决", "真实伤害1500 CD30s", CardType.SKILL));
        allSkillCards.add(new UpgradeCard("S6", "能量虹吸", "永久8%吸血（被动）", CardType.SKILL));
        allSkillCards.add(new UpgradeCard("S7", "星陨冲击", "陨石砸击最高血量敌机5000伤害 CD30s", CardType.SKILL));
        allSkillCards.add(new UpgradeCard("S8", "暗能爆发", "5s内攻击+300%移速-50% CD50s", CardType.SKILL));

        // 飞机卡 P1-P9
        allPlaneCards.add(new UpgradeCard("P1", "不灭血核", "HP+40% 护甲+50% 移速-50%", CardType.PLANE));
        allPlaneCards.add(new UpgradeCard("P2", "玄钢壁垒", "护甲+40% HP+8%", CardType.PLANE));
        allPlaneCards.add(new UpgradeCard("P3", "均衡星躯", "HP+10% 护甲+10% 移速+5%", CardType.PLANE));
        allPlaneCards.add(new UpgradeCard("P4", "流光驱动", "移速+20% HP+6%", CardType.PLANE));
        allPlaneCards.add(new UpgradeCard("P5", "过载核心", "HP+80% 护甲-80% 移速-20%", CardType.PLANE));
        allPlaneCards.add(new UpgradeCard("P6", "迅卫装甲", "移速+30% 护甲+35%", CardType.PLANE));
        allPlaneCards.add(new UpgradeCard("P7", "破空狂躯", "移速+100% HP-20% 护甲-30%", CardType.PLANE));
        allPlaneCards.add(new UpgradeCard("P8", "深渊韧体", "HP+24% 护甲+10%", CardType.PLANE));
        allPlaneCards.add(new UpgradeCard("P9", "破空巨盾", "HP+100% 护甲+100% 移速-80% 伤害+10%", CardType.PLANE));

        // 武器卡 W1-W16
        allWeaponCards.add(new UpgradeCard("W1", "锐击弹芯", "伤害+12% 暴击率+20(加法)", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W2", "狂怒爆破", "伤害+50% 弹速-10% 射击间隔+20%", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W3", "多重迅弹", "数量+1 弹速+18% 伤害-40% 间隔-50%", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W4", "暴击撕裂", "暴击率+80%(乘法) 伤害+20% 数量-1", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W5", "贯穿狂弹", "伤害+30% 暴击率-20%(乘法) 破甲+50%", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W6", "星轨散射", "数量+2 扇形散射 伤害-10% 间隔+50%", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W7", "暗能追猎", "子弹变更为导弹", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W8", "破甲弹幕", "破甲值+20(加法)", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W9", "湮灭重弹", "伤害+200% 数量变为1 暴击率+50%(乘法)", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W10", "裂变弹幕", "数量+1", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W11", "迅爆弹核", "暴击率+30(加法)", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W12", "破甲狂弹", "破甲值+100%", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W13", "暴雨弹潮", "数量+3 伤害-60% 暴击率-40%(乘法)", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W14", "瞬杀弹道", "伤害+500% 弹速-50% 数量变1 大小+100% 间隔+200%", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W15", "裁决轨迹", "暴击率+20% 伤害+20% 弹速-6%", CardType.WEAPON));
        allWeaponCards.add(new UpgradeCard("W16", "星陨弹幕", "数量+4 扇形 伤害-60% 暴击率-60%(乘法) 间隔-50%", CardType.WEAPON));
    }

    // ── 子弹数量追踪 ──
    private int bulletCount = 1;

    // ── 扇形散射 ──
    private boolean isSpreadPattern = false; // W6/W16 扇形散射模式
    private static final float SPREAD_ANGLE = 45.0f; // 散射角度 ±45°

    // ═══════════════════════════════════════════════
    // 构造方法
    // ═══════════════════════════════════════════════

    public GameEngine() {
        playerPlane = new PlayerPlane();
        playerPlane.setPosition(400, 500);

        eventControl = new EventControl();
        eventControl.registerListener(this);

        inputControl = new InputControl(playerPlane);
        inputControl.setEngine(this);

        gameTimer = new Timer(1000 / FPS, this);
    }

    // ═══════════════════════════════════════════════
    // 游戏启动
    // ═══════════════════════════════════════════════

    public void start() {
        gamePanel = new GamePanel(this);
        inputControl.registerKeyListeners(gamePanel.getFrame());
        gamePanel.getFrame().setVisible(true);
        gamePanel.getFrame().requestFocusInWindow();
        // 停留在标题画面直到玩家按空格
        isTitleScreen = true;
        gameTimer.start(); // 启动定时器以渲染标题画面动画
    }

    /**
     * 开始/重新开始游戏
     */
    /**
     * 选择飞机并开始游戏
     */
    public void selectPlane(int type) {
        isPlaneSelecting = false;
        isTitleScreen = false;
        playerPlane.configurePlane(type);
        startNewGame();
    }

    public void startNewGame() {
        // 保留飞机类型，重置属性
        playerPlane.reset();
        playerPlane.setPosition(400, 500);
        enemies.clear();
        playerBullets.clear();
        enemyBullets.clear();
        buffDrops.clear();
        explosions.clear();
        currentCards.clear();

        gameTime = 0;
        currentWave = 0;
        waveTimer = 0;
        score = 0;
        isGamePaused = false;
        isGameOver = false;
        isGameWin = false;
        isCardSelecting = false;
        isTitleScreen = false;
        hasPendingLevelUp = false;
        hasPendingSkillCard = false;
        pendingLevelUps = 0;
        enemiesInWave = 0;
        bossSpawned = false;
        bossDefeated = false;
        enemySpawnTimer = 0;
        bulletCount = 1;
        isSpreadPattern = false;

        startNextWave();
        gameTimer.start();
    }

    // ═══════════════════════════════════════════════
    // 游戏循环 (Timer ActionListener)
    // ═══════════════════════════════════════════════

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isGameOver || isGameWin) {
            gamePanel.repaintGame();
            return;
        }
        if (isTitleScreen || isPlaneSelecting) {
            gamePanel.repaintGame();
            return;
        }
        if (isGamePaused && !isCardSelecting) {
            gamePanel.repaintGame();
            return;
        }

        update();
        gamePanel.repaintGame();
    }

    // ═══════════════════════════════════════════════
    // 每帧更新
    // ═══════════════════════════════════════════════

    public void update() {
        if (isCardSelecting) return; // 选卡时暂停游戏

        gameTime += DT;
        waveTimer += DT;

        // 1. 玩家移动
        playerPlane.move(0, 0);

        // 2. 玩家射击 (space 手动 + J 自动开火)
        if (playerPlane.isAutoFire()) {
            playerPlane.onShoot(); // 自动开火：始终按下射击
        }
        Bullet pb = playerPlane.tryShoot(gameTime);
        if (pb != null) {
            int count = Math.max(1, bulletCount);
            if (isSpreadPattern && count > 1) {
                // 扇形散射：子弹在 ±45° 范围内均分发射
                float centerAngle = (float)(-Math.PI / 2); // 正上方
                float spreadRad = (float)Math.toRadians(SPREAD_ANGLE);
                float startAngle = centerAngle - spreadRad;
                float step = (2 * spreadRad) / (count - 1);
                for (int i = 0; i < count; i++) {
                    float angle = startAngle + i * step;
                    Bullet newBullet = createPlayerBulletCopy(playerPlane.getX(),
                                              playerPlane.getY() - playerPlane.getHeight() / 2);
                    newBullet.setDirection(angle, newBullet.getSpeed());
                    playerBullets.add(newBullet);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    float offsetX = (i - (count - 1) / 2.0f) * 8.0f;
                    Bullet newBullet = createPlayerBulletCopy(playerPlane.getX() + offsetX,
                                                              playerPlane.getY() - playerPlane.getHeight() / 2);
                    playerBullets.add(newBullet);
                }
            }
        }

        // 3. 更新玩家技能
        playerPlane.updateSkills(gameTime);

        // 4. 敌机生成
        if (!bossDefeated || currentWave < 10) {
            updateEnemySpawning();
        }

        // 5. 敌机更新
        for (EnemyPlane enemy : enemies) {
            if (!enemy.isAlive()) continue;
            enemy.move(0, 0);

            // Boss 特殊弹幕：每帧独立更新计时器
            if (enemy.isBoss()) {
                List<Bullet> specials = enemy.updateBossTimers(DT);
                enemyBullets.addAll(specials);
                // 冲撞触发：传入玩家位置以记录冲撞目标
                enemy.tryTriggerCharge(playerPlane.getX(), playerPlane.getY());
            }

            // Boss 冲撞：沿记录的玩家位置方向冲撞，检测碰撞
            if (enemy.isBoss() && enemy.isCharging()) {
                if (enemy.collidesWith(playerPlane)) {
                    playerPlane.takeDamage(enemy.getChargeDamage());
                    enemy.applyChargeSelfDamage();
                    enemy.stopChargeAndReturn(); // 撞到玩家后立刻原路返回
                }
            }

            // 敌机射击 (基础弹幕 + 受 atkSpeed 控制)
            List<Bullet> eb = enemy.tryShoot(gameTime);
            enemyBullets.addAll(eb);

            // 敌机技能更新
            enemy.updateSkills(gameTime);
            enemy.checkExpiredBuffs(gameTime);
        }

        // 6. 更新子弹 & 处理炸弹爆炸
        updateBullets(playerBullets);
        updateBulletsWithBombs(enemyBullets);

        // 7. 玩家 Buff 过期检查
        playerPlane.checkExpiredBuffs(gameTime);

        // 7.5. Buff 掉落球更新 & 拾取碰撞
        updateBuffDrops();

        // 8. 碰撞检测
        checkCollisions();

        // 9. 清理死亡实体
        cleanupDeadEntities();

        // 10. 波次切换判定
        checkWaveTransition();

        // 11. 检查升级
        if (hasPendingLevelUp && !isCardSelecting) {
            triggerUpgradeCards();
        }

        // 12. 更新爆炸效果
        for (Explosion exp : explosions) {
            exp.update(DT);
        }
        explosions.removeIf(exp -> exp.isFinished());

        // 13. 检查游戏结束
        if (playerPlane.isDead() && !isGameOver) {
            isGameOver = true;
            gameTimer.stop();
            eventControl.fireEvent(new GameEvent(GameEvent.Type.GAME_OVER, null));
        }
    }

    // ═══════════════════════════════════════════════
    // 子弹更新
    // ═══════════════════════════════════════════════

    private void updateBullets(List<Bullet> bullets) {
        for (Bullet b : bullets) {
            if (!b.isActive()) continue;
            b.update();
            if (b.isOutOfBound()) {
                b.setActive(false);
            }
        }
        bullets.removeIf(b -> !b.isActive());
    }

    /**
     * 更新敌机子弹，额外处理爆炸弹的引爆伤害
     */
    private void updateBulletsWithBombs(List<Bullet> bullets) {
        for (Bullet b : bullets) {
            if (!b.isActive()) continue;
            b.update();
            // 爆炸弹引爆：对玩家造成范围伤害
            if (b.isBomb() && b.hasDetonated()) {
                float dx = playerPlane.getX() - b.getX();
                float dy = playerPlane.getY() - b.getY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= b.getBombRadius()) {
                    playerPlane.takeDamage(b.getBombDamage());
                }
                // 爆炸视觉效果
                explosions.add(new Explosion(b.getX(), b.getY()));
                b.setActive(false);
            }
            if (b.isOutOfBound()) {
                b.setActive(false);
            }
        }
        bullets.removeIf(b -> !b.isActive());
    }

    // ═══════════════════════════════════════════════
    // 创建玩家子弹副本
    // ═══════════════════════════════════════════════

    private Bullet createPlayerBulletCopy(float x, float y) {
        Bullet template = playerPlane.getBullet();
        Bullet copy;
        if (template instanceof MissileBullet) {
            MissileBullet mb = new MissileBullet(x, y, true);
            copyStats(mb, template);
            mb.acquireTarget(enemies);
            copy = mb;
        } else if (template instanceof ArmorBullet) {
            ArmorBullet ab = new ArmorBullet(x, y, true);
            copyStats(ab, template);
            copy = ab;
        } else {
            NormalBullet nb = new NormalBullet(x, y, true);
            copyStats(nb, template);
            copy = nb;
        }
        return copy;
    }
    private MissileBullet createPlayerBulletCopy(Bullet src) {
        MissileBullet mb = new MissileBullet(src.getX(), src.getY(), true);
        copyStats(mb, src);
        return mb;
     }

    private void copyStats(Bullet dest, Bullet src) {
        dest.setAttack(src.getAttack());
        dest.setArmorDepth(src.getArmorDepth());
        dest.setCritRate(src.getCritRate());
        dest.setSpeed(src.getSpeed());
        dest.setBulletSize(src.getBulletSize());
    }

    // ═══════════════════════════════════════════════
    // 碰撞检测
    // ═══════════════════════════════════════════════

    private void checkCollisions() {
        // 玩家子弹 vs 敌机
        for (Bullet b : playerBullets) {
            if (!b.isActive()) continue;
            for (EnemyPlane enemy : enemies) {
                if (!enemy.isAlive()) continue;
                if (b.getBounds().intersects(enemy.getBounds())) {
                    int dmg = Bullet.calcFinalDamage(b, enemy.getArmor());
                    enemy.takeDamage(dmg);
                    playerPlane.lifesteal(dmg);
                    b.hit();

                    if (enemy.isDead()) {
                        onEnemyKilled(enemy);
                    }
                    break; // 子弹命中一个敌机后消失（非贯穿）
                }
            }
        }

        // 敌机子弹 vs 玩家
        for (Bullet b : enemyBullets) {
            if (!b.isActive()) continue;
            if (b.getBounds().intersects(playerPlane.getBounds())) {
                int dmg = Bullet.calcFinalDamage(b, playerPlane.getArmor());
                playerPlane.takeDamage(dmg);
                b.hit();
                eventControl.fireEvent(new GameEvent(GameEvent.Type.PLAYER_HIT, dmg));
            }
        }

        // 敌机机身 vs 玩家
        for (EnemyPlane enemy : enemies) {
            if (!enemy.isAlive() || enemy.isCharging() || enemy.isChargeWarning() || enemy.isReturning()) continue;
            if (enemy.collidesWith(playerPlane)) {
                playerPlane.takeDamage(500);
                enemy.takeDamage(500);
                if (enemy.isDead()) {
                    onEnemyKilled(enemy);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 敌机击杀处理
    // ═══════════════════════════════════════════════

    private void onEnemyKilled(EnemyPlane enemy) {
        score += enemy.getScoreValue();
        playerPlane.gainXp(enemy.getXpValue());

        // 爆炸效果
        explosions.add(new Explosion(enemy.getX(), enemy.getY()));

        // Buff 掉落
        if (Math.random() < enemy.getBuffDropRate()) {
            dropBuff(enemy.getX(), enemy.getY());
        }

        // 检查升级
        if (playerPlane.checkLevelUp()) {
            hasPendingLevelUp = true;
            eventControl.fireEvent(new GameEvent(GameEvent.Type.PLAYER_LEVEL_UP, playerPlane.getLevel()));
        }

        // Boss 检查
        if (enemy.isBoss()) {
            bossDefeated = true;
            if (currentWave == 7) {
                isGameWin = true;
                gameTimer.stop();
                eventControl.fireEvent(new GameEvent(GameEvent.Type.GAME_WIN, null));
            }
        }

        eventControl.fireEvent(new GameEvent(GameEvent.Type.ENEMY_DIED, enemy));
    }

    // ═══════════════════════════════════════════════
    // Buff 掉落
    // ═══════════════════════════════════════════════

    /**
     * 在指定位置生成一个 Buff 掉落球。
     * 掉落球会自动直线向下掉落，玩家触碰后拾取。
     */
    private void dropBuff(float x, float y) {
        double roll = Math.random();
        Buff buff;
        if (roll < 0.4) {
            buff = Buff.createHealBuff(gameTime);
        } else if (roll < 0.75) {
            buff = Buff.createShieldBuff(gameTime);
        } else {
            int fl = Math.min(3, playerPlane.getFireLevel() + 1);
            buff = Buff.createFireBuff(gameTime, fl);
        }
        BuffDrop drop = new BuffDrop(x, y, buff);
        buffDrops.add(drop);
    }

    /**
     * 每帧更新 Buff 掉落球：移动 + 拾取碰撞检测 + 清理出界球。
     */
    private void updateBuffDrops() {
        for (BuffDrop drop : buffDrops) {
            if (!drop.isActive()) continue;
            drop.update();
            // 检测与玩家飞机的碰撞
            if (drop.isActive() && drop.collidesWith(playerPlane)) {
                playerPlane.applyBuff(drop.getBuff());
                drop.setActive(false);
            }
        }
        buffDrops.removeIf(d -> !d.isActive());
    }

    // ═══════════════════════════════════════════════
    // 清理实体
    // ═══════════════════════════════════════════════

    private void cleanupDeadEntities() {
        // 清理移出屏幕的敌机
        enemies.removeIf(e -> !e.isAlive() || e.getY() > 700);
    }

    // ═══════════════════════════════════════════════
    // 敌机生成
    // ═══════════════════════════════════════════════

    private void updateEnemySpawning() {
        if (currentWave == 0) return;

        enemySpawnTimer += DT;
        if (enemySpawnTimer < ENEMY_SPAWN_INTERVAL) return;
        enemySpawnTimer = 0;

        // Boss 波次 (5 或 7)：先清完普通敌机再出Boss
        boolean isBossWave = (currentWave == 5 || currentWave == 7);
        if (isBossWave && !bossSpawned && enemiesInWave >= 8 && enemies.isEmpty()) {
            spawnBoss(currentWave == 5 ? 1 : 2);
            return;
        }

        if (isBossWave && bossSpawned) return;

        // 普通敌机生成
        if (enemiesInWave < 20) {
            EnemyPlane.EnemyLevel level = getRandomEnemyLevel();
            EnemyPlane enemy = EnemyPlane.createNormalEnemy(level);

            float spawnX = 40 + random.nextFloat() * (800 - 80);
            enemy.setPosition(spawnX, -40);
            enemy.setInitialX(spawnX);

            enemies.add(enemy);
            enemiesInWave++;
        }
    }

    /**
     * 根据当前波次获取随机敌机等级。
     * Wave 1~3: 仅 Level 1
     * Wave 4:   Level 1 : Level 2 = 4 : 1
     * Wave 5:   Level 1 : Level 2 : Level 3 = 6 : 3 : 1
     * Wave 6+:  沿用 Wave 5 比例，并逐渐引入 Level 4
     */
    private EnemyPlane.EnemyLevel getRandomEnemyLevel() {
        float roll = random.nextFloat();

        switch (currentWave) {
            case 1: case 2: case 3:
                // 仅 Level 1
                return EnemyPlane.EnemyLevel.LEVEL_1;

            case 4:
                // Level 1 : Level 2 = 4 : 1 → roll < 0.8 = Lv1, else Lv2
                if (roll < 0.8f) return EnemyPlane.EnemyLevel.LEVEL_1;
                return EnemyPlane.EnemyLevel.LEVEL_2;

            case 5:
                // Level 1 : Level 2 : Level 3 = 6 : 3 : 1 → thresholds 0.6, 0.9
                if (roll < 0.6f) return EnemyPlane.EnemyLevel.LEVEL_1;
                if (roll < 0.9f) return EnemyPlane.EnemyLevel.LEVEL_2;
                return EnemyPlane.EnemyLevel.LEVEL_3;

            default: // Wave 6+
                // 逐渐引入 Level 4, 比例: Lv1:Lv2:Lv3:Lv4 = 4:3:2:1
                if (roll < 0.4f) return EnemyPlane.EnemyLevel.LEVEL_1;
                if (roll < 0.7f) return EnemyPlane.EnemyLevel.LEVEL_2;
                if (roll < 0.9f) return EnemyPlane.EnemyLevel.LEVEL_3;
                return EnemyPlane.EnemyLevel.LEVEL_4;
        }
    }

    /**
     * 生成 Boss
     */
    private void spawnBoss(int bossLevel) {
        EnemyPlane boss = EnemyPlane.createBoss(bossLevel);
        boss.setPosition(400, -50);
        enemies.add(boss);
        bossSpawned = true;
        eventControl.fireEvent(new GameEvent(GameEvent.Type.BOSS_SPAWN, boss));
    }

    // ═══════════════════════════════════════════════
    // 波次管理
    // ═══════════════════════════════════════════════

    private void startNextWave() {
        currentWave++;
        waveTimer = 0;
        enemiesInWave = 0;
        bossSpawned = false;
        bossDefeated = false;
        enemySpawnTimer = 0;
        eventControl.fireEvent(new GameEvent(GameEvent.Type.WAVE_START, currentWave));
    }

    private void checkWaveTransition() {
        if (currentWave > 7) return;

        boolean isBossWave = (currentWave == 5 || currentWave == 7);

        if (isBossWave && bossDefeated) {
            // Boss 被击败，进入下一波
            startNextWave();
            return;
        }

        // 非Boss波次：时间到（~30秒）且敌机清完
        if (!isBossWave && waveTimer >= 30 && enemies.isEmpty() && enemiesInWave >= 10) {
            if (currentWave < 7) {
                eventControl.fireEvent(new GameEvent(GameEvent.Type.WAVE_CLEAR, currentWave));
                startNextWave();
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 升级卡系统
    // ═══════════════════════════════════════════════

    private void triggerUpgradeCards() {
        hasPendingLevelUp = false;
        isCardSelecting = true;
        isGamePaused = true;

        // 执行升级（基础属性成长）
        playerPlane.levelUp();

        // 随机抽取 3 张卡
        currentCards.clear();
        List<UpgradeCard> pool = new ArrayList<>();

        // 根据是否每5级添加技能卡
        boolean showSkillCard = (playerPlane.getLevel() % 5 == 0)
                                && playerPlane.getSkills().size() < playerPlane.getMaxSkillSlots();

        pool.addAll(allPlaneCards);
        pool.addAll(allWeaponCards);
        if (showSkillCard && playerPlane.getMaxSkillSlots() > 0) {
            // 排除已拥有的技能
            for (UpgradeCard sc : allSkillCards) {
                boolean alreadyOwned = false;
                for (Skill s : playerPlane.getSkills()) {
                    if (s.getName().equals(sc.name)) {
                        alreadyOwned = true;
                        break;
                    }
                }
                if (!alreadyOwned) pool.add(sc);
            }
        }

        // 随机选3张
        while (currentCards.size() < 3 && !pool.isEmpty()) {
            int idx = random.nextInt(pool.size());
            currentCards.add(pool.remove(idx));
        }

        if (!currentCards.isEmpty()) {
            eventControl.fireEvent(new GameEvent(GameEvent.Type.CARD_SELECT, currentCards));
        } else {
            resumeGame();
        }
    }

    /**
     * 玩家选择升级卡
     */
    public void selectCard(int index) {
        if (index < 0 || index >= currentCards.size()) return;

        UpgradeCard card = currentCards.get(index);
        applyCard(card);
        currentCards.clear();
        resumeGame();
    }

    /**
     * 应用升级卡效果
     */
    private void applyCard(UpgradeCard card) {
        Bullet bullet = playerPlane.getBullet();

        switch (card.id) {
            // ── 技能卡 ──
            case "S1": playerPlane.addSkill(Skill.createThunderDash()); break;
            case "S2": playerPlane.addSkill(Skill.createEMShield()); break;
            case "S3": playerPlane.addSkill(Skill.createFusionBlast()); break;
            case "S4": playerPlane.addSkill(Skill.createOverclock()); break;
            case "S5": playerPlane.addSkill(Skill.createThunderJudge()); break;
            case "S6": playerPlane.addSkill(Skill.createEnergySiphon()); break;
            case "S7": playerPlane.addSkill(Skill.createStarImpact()); break;
            case "S8": playerPlane.addSkill(Skill.createDarkBurst()); break;


            // ── 飞机卡 ──
            case "P1": // 不灭血核: HP+40% 护甲+50% 移速-50%
                playerPlane.setMaxHp((int)(playerPlane.getMaxHp() * 1.4f));
                playerPlane.setHp(playerPlane.getMaxHp());
                playerPlane.setArmor((int)(playerPlane.getArmor() * 1.5f));
                playerPlane.setMoveSpeed(playerPlane.getMoveSpeed() * 0.5f);
                break;
            case "P2": // 玄钢壁垒: 护甲+40% HP+8%
                playerPlane.setArmor((int)(playerPlane.getArmor() * 1.4f));
                playerPlane.setMaxHp((int)(playerPlane.getMaxHp() * 1.08f));
                playerPlane.setHp(playerPlane.getMaxHp());
                break;
            case "P3": // 均衡星躯: HP+10% 护甲+10% 移速+5%
                playerPlane.setMaxHp((int)(playerPlane.getMaxHp() * 1.1f));
                playerPlane.setHp(playerPlane.getMaxHp());
                playerPlane.setArmor((int)(playerPlane.getArmor() * 1.1f));
                playerPlane.setMoveSpeed(playerPlane.getMoveSpeed() * 1.05f);
                break;
            case "P4": // 流光驱动: 移速+20% HP+6%
                playerPlane.setMoveSpeed(playerPlane.getMoveSpeed() * 1.20f);
                playerPlane.setMaxHp((int)(playerPlane.getMaxHp() * 1.06f));
                playerPlane.setHp(playerPlane.getMaxHp());
                break;
            case "P5": // 过载核心: HP+80% 护甲-80% 移速-20%
                playerPlane.setMaxHp((int)(playerPlane.getMaxHp() * 1.8f));
                playerPlane.setHp(playerPlane.getMaxHp());
                playerPlane.setArmor((int)(playerPlane.getArmor() * 0.2f));
                playerPlane.setMoveSpeed(playerPlane.getMoveSpeed() * 0.8f);
                break;
            case "P6": // 迅卫装甲: 移速+30% 护甲+35%
                playerPlane.setMoveSpeed(playerPlane.getMoveSpeed() * 1.30f);
                playerPlane.setArmor((int)(playerPlane.getArmor() * 1.35f));
                break;
            case "P7": // 破空狂躯: 移速+100% HP-20% 护甲-30%
                playerPlane.setMoveSpeed(playerPlane.getMoveSpeed() * 2.0f);
                playerPlane.setMaxHp((int)(playerPlane.getMaxHp() * 0.8f));
                playerPlane.setHp(Math.min(playerPlane.getHp(), playerPlane.getMaxHp()));
                playerPlane.setArmor((int)(playerPlane.getArmor() * 0.7f));
                break;
            case "P8": // 深渊韧体: HP+24% 护甲+10%
                playerPlane.setMaxHp((int)(playerPlane.getMaxHp() * 1.24f));
                playerPlane.setHp(playerPlane.getMaxHp());
                playerPlane.setArmor((int)(playerPlane.getArmor() * 1.10f));
                break;
            case "P9": // 破空巨盾: HP+100% 护甲+100% 移速-80% 伤害+10%
                playerPlane.setMaxHp((int)(playerPlane.getMaxHp() * 2.0f));
                playerPlane.setHp(playerPlane.getMaxHp());
                playerPlane.setArmor((int)(playerPlane.getArmor() * 2.0f));
                playerPlane.setMoveSpeed(playerPlane.getMoveSpeed() * 0.2f);
                bullet.setAttack((int)(bullet.getAttack() * 1.10f));
                break;

            // ── 武器卡 ──
            case "W1": // 锐击弹芯: 伤害+12% 暴击率+20(加法)
                bullet.setAttack((int)(bullet.getAttack() * 1.12f));
                bullet.setCritRate(bullet.getCritRate() + 0.20f);
                break;
            case "W2": // 狂怒爆破: 伤害+50% 弹速-10% 间隔+20%
                bullet.setAttack((int)(bullet.getAttack() * 1.50f));
                bullet.setSpeed(bullet.getSpeed() * 0.90f);
                playerPlane.setAtkSpeed(playerPlane.getAtkSpeed() * 1.20f);
                break;
            case "W3": // 多重迅弹: 数量+1 弹速+18% 伤害-10% 间隔-50%
                bulletCount += 1;
                bullet.setSpeed(bullet.getSpeed() * 1.18f);
                bullet.setAttack((int)(bullet.getAttack() * 0.90f));
                playerPlane.setAtkSpeed(playerPlane.getAtkSpeed() * 0.50f);
                break;
            case "W4": // 暴击撕裂: 暴击率+80%(乘法) 伤害+20% 数量-1
                bullet.setCritRate(bullet.getCritRate() * 1.80f);
                bullet.setAttack((int)(bullet.getAttack() * 1.20f));
                if (bulletCount > 1) bulletCount -= 1;
                break;
            case "W5": // 贯穿狂弹: 伤害+30% 暴击率-20%(乘法) 破甲+50%
                bullet.setAttack((int)(bullet.getAttack() * 1.30f));
                bullet.setCritRate(bullet.getCritRate() * 0.80f);
                bullet.setArmorDepth((int)(bullet.getArmorDepth() * 1.50f));
                break;
            case "W6": // 星轨散射: 数量+2 扇形散射 伤害-10% 间隔+50%
                bulletCount += 2;
                bullet.setAttack((int)(bullet.getAttack() * 0.90f));
                playerPlane.setAtkSpeed(playerPlane.getAtkSpeed() * 1.50f);
                isSpreadPattern = true;
                break;
            case "W7": // 暗能追猎: 子弹变更为导弹
                MissileBullet missile = createPlayerBulletCopy(bullet);
                playerPlane.setBullet(missile);
                break;
            case "W8": // 破甲弹幕: 破甲值+20(加法)
                bullet.setArmorDepth(bullet.getArmorDepth() + 20);
                break;
            case "W9": // 湮灭重弹: 伤害+200% 数量变1 暴击率+50%(乘法)
                bullet.setAttack((int)(bullet.getAttack() * 3.0f));
                bulletCount = 1;
                bullet.setCritRate(bullet.getCritRate() * 1.50f);
                break;
            case "W10": // 裂变弹幕: 数量+1
                bulletCount += 1;
                break;
            case "W11": // 迅爆弹核: 暴击率+30(加法)
                bullet.setCritRate(bullet.getCritRate() + 0.30f);
                break;
            case "W12": // 破甲狂弹: 破甲值+100%
                bullet.setArmorDepth((int)(bullet.getArmorDepth() * 2.0f));
                break;
            case "W13": // 暴雨弹潮: 数量+3 伤害-20% 暴击率-20%(乘法)
                bulletCount += 3;
                bullet.setAttack((int)(bullet.getAttack() * 0.80f));
                bullet.setCritRate(bullet.getCritRate() * 0.80f);
                break;
            case "W14": // 瞬杀弹道: 伤害+500% 弹速-50% 数量变1 大小+100% 间隔+200%
                bullet.setAttack((int)(bullet.getAttack() * 6.0f));
                bullet.setSpeed(bullet.getSpeed() * 0.50f);
                bulletCount = 1;
                bullet.setBulletSize(bullet.getBulletSize() * 2.0f);
                playerPlane.setAtkSpeed(playerPlane.getAtkSpeed() * 3.0f);
                break;
            case "W15": // 裁决轨迹:暴击率+20%(加法) 伤害+20% 弹速-6%
                bullet.setCritRate(bullet.getCritRate() + 0.20f);
                bullet.setAttack((int)(bullet.getAttack() * 1.20f));
                bullet.setSpeed(bullet.getSpeed() * 0.94f);
                break;
            case "W16": // 星陨弹幕: 数量+4 扇形 伤害-30% 暴击率-30%(乘法) 间隔-50%
                bulletCount += 4;
                bullet.setAttack((int)(bullet.getAttack() * 0.70f));
                bullet.setCritRate(bullet.getCritRate() * 0.70f);
                playerPlane.setAtkSpeed(playerPlane.getAtkSpeed() * 0.50f);
                isSpreadPattern = true;
                break;
        }
    }

    // ═══════════════════════════════════════════════
    // 技能激活
    // ═══════════════════════════════════════════════

    public void activateSkill(int index) {
        if (index < 0 || index >= playerPlane.getSkills().size()) return;

        Skill skill = playerPlane.getSkills().get(index);
        if (!skill.canUse(gameTime)) return;

        int result = skill.activate(playerPlane, gameTime);

        // 处理特殊技能效果
        switch (skill.getType()) {
            case FUSION_BLAST:
                // 清屏：对所有敌机造成 2000 伤害
                for (EnemyPlane enemy : enemies) {
                    if (!enemy.isAlive()) continue;
                    int dmg = Bullet.calcFinalDamage(
                        new NormalBullet(0, 0, true) {{ setAttack(2000); setArmorDepth(20); }},
                        enemy.getArmor());
                    enemy.takeDamage(dmg);
                    if (enemy.isDead()) onEnemyKilled(enemy);
                }
                break;
            case THUNDER_JUDGE:
                // 真实伤害 1500 (无视护甲) — 对所有敌机
                for (EnemyPlane enemy : enemies) {
                    if (!enemy.isAlive()) continue;
                    enemy.takeDamage(1500);
                    if (enemy.isDead()) onEnemyKilled(enemy);
                }
                break;
            case STAR_IMPACT:
                // 陨石砸向血量最高的敌机
                EnemyPlane target = null;
                int maxHp = 0;
                for (EnemyPlane enemy : enemies) {
                    if (enemy.isAlive() && enemy.getHp() > maxHp) {
                        maxHp = enemy.getHp();
                        target = enemy;
                    }
                }
                if (target != null) {
                    target.takeDamage(5000);
                    if (target.isDead()) onEnemyKilled(target);
                }
                break;
        }
    }

    // ═══════════════════════════════════════════════
    // 暂停/恢复
    // ═══════════════════════════════════════════════

    public void togglePause() {
        if (isCardSelecting) return;
        if (isGamePaused) {
            resumeGame();
        } else {
            pauseGame();
        }
    }

    public void pauseGame() {
        isGamePaused = true;
    }

    public void resumeGame() {
        isGamePaused = false;
        isCardSelecting = false;
    }

    // ═══════════════════════════════════════════════
    // GameEventListener 实现
    // ═══════════════════════════════════════════════

    @Override
    public void onGameEvent(GameEvent event) {
        switch (event.getType()) {
            case GAME_OVER:
                isGameOver = true;
                gameTimer.stop();
                break;
            case GAME_WIN:
                isGameWin = true;
                gameTimer.stop();
                break;
        }
    }

    // ═══════════════════════════════════════════════
    // Getter / Setter
    // ═══════════════════════════════════════════════

    public void setGamePanel(GamePanel panel) { this.gamePanel = panel; }
    public void setInputControl(InputControl ic) { this.inputControl = ic; }
    public void setEventControl(EventControl ec) { this.eventControl = ec; }
    public EventControl getEventControl() { return eventControl; }

    public PlayerPlane getPlayerPlane() { return playerPlane; }
    public List<EnemyPlane> getEnemies() { return enemies; }
    public List<Bullet> getPlayerBullets() { return playerBullets; }
    public List<Bullet> getEnemyBullets() { return enemyBullets; }
    public List<Explosion> getExplosions() { return explosions; }
    public List<BuffDrop> getBuffDrops() { return buffDrops; }

    public int getScore() { return score; }
    public int getCurrentWave() { return currentWave; }
    public float getGameTime() { return gameTime; }
    public float getWaveTimer() { return waveTimer; }
    public boolean isGamePaused() { return isGamePaused; }
    public boolean isGameOver() { return isGameOver; }
    public boolean isGameWin() { return isGameWin; }
    public boolean isCardSelecting() { return isCardSelecting; }
    public boolean isTitleScreen() { return isTitleScreen; }
    public boolean isPlaneSelecting() { return isPlaneSelecting; }
    public List<UpgradeCard> getCurrentCards() { return currentCards; }
    public boolean isPaused() { return isGamePaused; }

    public void startGameLoop() { gameTimer.start(); }
    public void stopGameLoop() { gameTimer.stop(); }

    /**
     * 标题画面按空格开始游戏
     */
    public void onTitleScreenAction() {
        if (isTitleScreen) {
            // 进入飞机选择界面
            isTitleScreen = false;
            isPlaneSelecting = true;
        } else if (isGameOver || isGameWin) {
            isGameOver = false;
            isGameWin = false;
            isPlaneSelecting = true;
            gameTimer.start(); // 重启定时器以渲染飞机选择界面
        }
    }

    /**
     * 处理飞机选择鼠标点击
     */
    public void handlePlaneClick(int mouseX, int mouseY) {
        if (!isPlaneSelecting) return;

        int cardWidth = 160;
        int cardHeight = 280;
        int totalWidth = 4 * cardWidth + 3 * 15;
        int startX = (800 - totalWidth) / 2;
        int cardY = 180;

        for (int i = 0; i < 4; i++) {
            int cx = startX + i * (cardWidth + 15);
            if (mouseX >= cx && mouseX <= cx + cardWidth &&
                mouseY >= cardY && mouseY <= cardY + cardHeight) {
                selectPlane(i + 1);
                return;
            }
        }
    }

    /**
     * 处理升级卡选择鼠标点击
     */
    public void handleCardClick(int mouseX, int mouseY) {
        if (!isCardSelecting || currentCards.isEmpty()) return;

        // 三张卡片水平排列在屏幕中央
        int cardWidth = 200;
        int cardHeight = 300;
        int totalWidth = currentCards.size() * cardWidth + (currentCards.size() - 1) * 20;
        int startX = (800 - totalWidth) / 2;
        int cardY = 150;

        for (int i = 0; i < currentCards.size(); i++) {
            int cx = startX + i * (cardWidth + 20);
            if (mouseX >= cx && mouseX <= cx + cardWidth &&
                mouseY >= cardY && mouseY <= cardY + cardHeight) {
                selectCard(i);
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 爆炸效果内部类
    // ═══════════════════════════════════════════════

    public static class Explosion {
        private float x, y;
        private float timer = 0;
        private static final float DURATION = 0.5f;
        private float maxRadius = 40;

        public Explosion(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public void update(float dt) {
            timer += dt;
        }

        public boolean isFinished() { return timer >= DURATION; }
        public float getX() { return x; }
        public float getY() { return y; }
        public float getProgress() { return Math.min(1.0f, timer / DURATION); }
        public float getRadius() { return maxRadius * getProgress(); }
        public float getAlpha() { return 1.0f - getProgress(); }
    }
}
