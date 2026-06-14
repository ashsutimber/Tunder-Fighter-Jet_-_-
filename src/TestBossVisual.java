import Entity.Plane.EnemyPlane;
import Entity.Bullet.Bullet;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * <h1>Boss 弹幕可视化测试工具（TestBossVisual）</h1>
 *
 * <p>独立的测试面板，用于逐阶段预览和验证 Boss 的弹幕效果。
 * 不依赖完整的游戏引擎，直接创建 Boss 实例并通过 Swing Timer 驱动，
 * 在独立窗口中展示 Boss 弹幕行为。</p>
 *
 * <h2>测试覆盖范围</h2>
 * <p>共覆盖 <b>6 个测试阶段</b>（2 个 Boss × 3 个阶段）：</p>
 * <table border="1">
 *   <tr><th>阶段索引</th><th>Boss</th><th>阶段</th><th>测试内容</th></tr>
 *   <tr><td>0</td><td>Boss 1</td><td>Phase 1</td><td>扇形 5 发 + 环形弹幕（每 2.5s）</td></tr>
 *   <tr><td>1</td><td>Boss 1</td><td>Phase 2</td><td>扇形 + 环形 + 冲撞（每 8s）</td></tr>
 *   <tr><td>2</td><td>Boss 1</td><td>Phase 3</td><td>扇形 10 发 + 环形 + 双旋转扫射（蓝色）</td></tr>
 *   <tr><td>3</td><td>Boss 2</td><td>Phase 1</td><td>双旋转扫射 + 大型红弹（每 8s 爆炸）</td></tr>
 *   <tr><td>4</td><td>Boss 2</td><td>Phase 2</td><td>双旋转升级 + 红弹 3 连发（每 5s）</td></tr>
 *   <tr><td>5</td><td>Boss 2</td><td>Phase 3</td><td>巨型蓝弹（每 20s）+ 扇形 + 红弹 + 回血</td></tr>
 * </table>
 *
 * <h2>编译与运行</h2>
 * <pre>
 * # 编译
 * javac -encoding UTF-8 -cp out TestBossVisual.java -d out
 *
 * # 运行
 * java -cp out TestBossVisual
 * </pre>
 *
 * <h2>操作说明</h2>
 * <ul>
 *   <li><b>"下一阶段"按钮：</b>切换到下一个测试阶段</li>
 *   <li><b>"暂停/继续"按钮：</b>暂停/恢复动画</li>
 *   <li><b>玩家位置：</b>固定模拟在 (400, 500)，绿色圆点标记</li>
 *   <li><b>图例：</b>右上角显示各弹幕颜色对应的子弹类型</li>
 * </ul>
 *
 * <h2>视觉图例</h2>
 * <ul>
 *   <li>🟠 橙色 — 扇形弹幕</li>
 *   <li>🔴 红色 — 环形弹幕 / 大型红弹</li>
 *   <li>🔵 蓝色 — 旋转扫射 / 巨型蓝弹</li>
 *   <li>🟢 青色 — 冲撞路径</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-05-01
 *
 * @see EnemyPlane  Boss 敌机（弹幕行为实现）
 * @see Bullet      子弹抽象基类
 */
public class TestBossVisual extends JPanel implements ActionListener {

    /** 窗口宽度（px） */
    private static final int W = 800;

    /** 窗口高度（px） */
    private static final int H = 600;

    /** 每帧时间增量（秒），1/60 */
    private static final float DT = 1.0f / 60.0f;

    // ── 测试阶段定义 ──
    /** 全部 6 个测试阶段的描述标签 */
    private static final String[] STAGES = {
        "Boss 1 — Phase 1: 扇形弹幕5发 + 环形弹幕(每5s)",
        "Boss 1 — Phase 2: 扇形 + 环形 + 冲撞(每8~12s)",
        "Boss 1 — Phase 3: 扇形10发 + 环形 + 冲撞 + 旋转扫射(蓝色)",
        "Boss 2 — Phase 1: 双旋转扫射 + 大型红弹(每8s爆炸)",
        "Boss 2 — Phase 2: 双旋转升级 + 红弹(每5s×3连发)",
        "Boss 2 — Phase 3: 巨型蓝弹(每20s) + 扇形 + 红弹 + 冲撞 + 回血",
    };

    /** 当前测试阶段索引（0~5） */
    private int currentStage = 0;

    /** 当前测试的 Boss 实例 */
    private EnemyPlane boss;

    /** 场景中所有子弹列表 */
    private List<Bullet> bullets = new ArrayList<>();

    /** 爆炸效果列表（可视化装饰） */
    private List<MiniExplosion> explosions = new ArrayList<>();

    /** 阶段计时器（秒），从 0 开始累计 */
    private float timer = 0;

    /** Swing 定时器（60 FPS 驱动循环） */
    private Timer gameTimer;

    /** 顶部信息标签：HP / 护甲 / Phase / 计时 / 子弹数 */
    private JLabel infoLabel;

    /** "下一阶段"按钮 */
    private JButton nextButton;

    /** 阶段切换提示是否已显示 */
    private boolean phaseTransitionShown = false;

    // ── 模拟玩家位置 ──
    /** 模拟玩家 X 坐标（固定在 400） */
    private float playerX = 400;

    /** 模拟玩家 Y 坐标（固定在 500） */
    private float playerY = 500;

    /**
     * 构造测试面板 — 初始化 Swing 组件和 Boss。
     */
    public TestBossVisual() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(5, 5, 25));     // 深空背景色
        setDoubleBuffered(true);                 // 双缓冲防闪烁
        gameTimer = new Timer(1000 / 60, this);  // 60 FPS
        setupBossForStage(0);                    // 初始：Boss 1 Phase 1
    }

    // ═══════════════════════════════════════════════
    // 按阶段设置 Boss
    // ═══════════════════════════════════════════════

    /**
     * 根据阶段索引创建并配置 Boss 实例。
     *
     * <p>阶段 0~2 为 Boss 1，阶段 3~5 为 Boss 2。
     * 通过设置不同 HP 百分比来模拟阶段切换：</p>
     * <ul>
     *   <li>Phase 1: 100% HP（不扣血）</li>
     *   <li>Phase 2: ~50% HP（扣一半血以触发 Phase 2）</li>
     *   <li>Phase 3: ~15% HP（扣到 15% 以触发 Phase 3）</li>
     * </ul>
     *
     * @param stage 阶段索引（0~5）
     */
    private void setupBossForStage(int stage) {
        bullets.clear();
        explosions.clear();
        timer = 0;
        phaseTransitionShown = false;

        // 阶段 0~2 = Boss 1, 阶段 3~5 = Boss 2
        int bossLevel = stage < 3 ? 1 : 2;
        boss = EnemyPlane.createBoss(bossLevel);
        boss.setPosition(400, 150);     // Boss 初始位置（屏幕上方中央）

        // 根据阶段设置对应血量百分比
        switch (stage) {
            case 0: // Boss1 Phase 1: 100% HP — 完整血量
                break;
            case 1: // Boss1 Phase 2: HP ≤ 66% → 设置到 ~50%
                boss.takeDamage(boss.getMaxHp() - (int)(boss.getMaxHp() * 0.50f));
                break;
            case 2: // Boss1 Phase 3: HP ≤ 20% → 设置到 ~15%
                boss.takeDamage(boss.getMaxHp() - (int)(boss.getMaxHp() * 0.15f));
                break;
            case 3: // Boss2 Phase 1: 100% HP
                break;
            case 4: // Boss2 Phase 2: ~50% HP
                boss.takeDamage(boss.getMaxHp() - (int)(boss.getMaxHp() * 0.50f));
                break;
            case 5: // Boss2 Phase 3: ~15% HP
                boss.takeDamage(boss.getMaxHp() - (int)(boss.getMaxHp() * 0.15f));
                break;
        }

        // 更新信息标签
        if (infoLabel != null) {
            infoLabel.setText(String.format("HP: %d/%d | 护甲: %d | Phase: %d | 计时: %.1fs",
                boss.getHp(), boss.getMaxHp(), boss.getArmor(), boss.getBossPhase(), timer));
        }
    }

    // ═══════════════════════════════════════════════
    // 测试循环（模拟 GameEngine 每帧更新）
    // ═══════════════════════════════════════════════

    /**
     * 每帧（1/60 秒）执行 — 更新 Boss 弹幕、子弹、爆炸效果。
     *
     * <p>模拟 GameEngine.update() 的 Boss 相关部分：</p>
     * <ol>
     *   <li>推进计时器</li>
     *   <li>更新 Boss 特殊弹幕（环形/旋转/红弹/蓝弹）</li>
     *   <li>更新 Boss 基础射击（扇形弹幕）</li>
     *   <li>处理冲撞攻击</li>
     *   <li>更新所有子弹位置</li>
     *   <li>清理出界/失效子弹</li>
     *   <li>更新爆炸效果</li>
     * </ol>
     *
     * @param e ActionEvent（来自 Swing Timer）
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        timer += DT;    // 阶段计时器推进

        // Boss 特殊弹幕（每帧独立计时：环形/旋转/红弹/蓝弹）
        List<Bullet> specials = boss.updateBossTimers(DT);
        bullets.addAll(specials);

        // Boss 基础射击（受 atkSpeed 控制：扇形弹幕）
        List<Bullet> base = boss.tryShoot(timer);
        bullets.addAll(base);

        // Boss 冲撞触发：传入模拟玩家位置
        boss.tryTriggerCharge(playerX, playerY);
        if (boss.isCharging()) {
            boss.startChargeToward(playerX, playerY);
        }

        // Boss 移动（入场动画 / 冲撞 / BOSS_SWAY）
        boss.move(0, 0);

        // 更新所有子弹位置
        for (Bullet b : bullets) {
            b.update();
        }
        // 清理出界/失效子弹
        bullets.removeIf(b -> !b.isActive() || b.isOutOfBound());

        // 更新爆炸效果（装饰用）
        for (MiniExplosion ex : explosions) ex.update(DT);
        explosions.removeIf(ex -> ex.finished);

        // 定时触发环形弹幕爆炸效果（装饰）
        if (timer > 0 && (int)(timer * 10) % 50 == 0 && timer - (int)timer < DT * 2) {
            explosions.add(new MiniExplosion(boss.getX(), boss.getY(), 30));
        }

        // 更新顶部信息标签
        infoLabel.setText(String.format("HP: %d/%d | 护甲: %d | Phase: %d | 计时: %.1fs | 子弹数: %d",
            boss.getHp(), boss.getMaxHp(), boss.getArmor(), boss.getBossPhase(), timer, bullets.size()));

        repaint();  // 触发重绘
    }

    // ═══════════════════════════════════════════════
    // 绘制
    // ═══════════════════════════════════════════════

    /**
     * 绘制测试画面 — 背景网格 + 玩家指示 + Boss + 子弹 + 爆炸 + 图例。
     *
     * <p>渲染元素：</p>
     * <ul>
     *   <li><b>背景网格：</b>40px 间距的深色网格线</li>
     *   <li><b>玩家指示：</b>绿色圆点 + "玩家"标签</li>
     *   <li><b>Boss：</b>本体（Boss1 红色 / Boss2 紫色） + HP条 + 阶段 + 冲撞状态</li>
     *   <li><b>子弹：</b>发光光晕 + 本体 + 爆炸弹特殊渲染</li>
     *   <li><b>爆炸效果：</b>渐隐橙色圆圈</li>
     *   <li><b>图例：</b>右上角色块 + 说明文字</li>
     * </ul>
     *
     * @param g Graphics 上下文
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // ── 背景网格（40px 间距，用于视觉参考） ──
        g2.setColor(new Color(20, 20, 40));
        for (int i = 0; i < W; i += 40) g2.drawLine(i, 0, i, H);
        for (int i = 0; i < H; i += 40) g2.drawLine(0, i, W, i);

        // ── 玩家位置指示器（绿色圆点 + 标签） ──
        g2.setColor(new Color(0, 255, 100, 80));
        g2.fillOval((int)playerX - 10, (int)playerY - 10, 20, 20);
        g2.setColor(Color.GREEN);
        g2.drawString("玩家", playerX - 12, playerY - 15);

        // ── Boss 绘制 ──
        drawBoss(g2);

        // ── 子弹绘制 ──
        for (Bullet b : bullets) {
            if (!b.isActive()) continue;
            int size = (int)b.getBulletSize();

            // 爆炸弹特殊渲染：闪烁红光 + 爆炸范围圈
            if (b.isBomb() && b.isBombArmed()) {
                float flash = (float)(Math.sin(System.currentTimeMillis() * 0.02) + 1) / 2;
                g2.setColor(new Color(255, (int)(100 * flash), 0, 220));
                g2.fillOval((int)(b.getX() - size), (int)(b.getY() - size), size * 2, size * 2);
                g2.setColor(new Color(255, 50, 0, 60));
                g2.drawOval((int)(b.getX() - b.getBombRadius()), (int)(b.getY() - b.getBombRadius()),
                           (int)(b.getBombRadius() * 2), (int)(b.getBombRadius() * 2));
            } else {
                // 发光光晕（半透明大圈）
                g2.setColor(new Color(b.getColor().getRed(), b.getColor().getGreen(),
                    b.getColor().getBlue(), 60));
                g2.fillOval((int)(b.getX() - size), (int)(b.getY() - size), size * 2, size * 2);
                // 子弹本体（实心圆）
                g2.setColor(b.getColor() != null ? b.getColor() : Color.RED);
                g2.fillOval((int)(b.getX() - size / 2), (int)(b.getY() - size / 2), size, size);
            }
        }

        // ── 爆炸效果（装饰用，渐隐橙色圆圈） ──
        for (MiniExplosion ex : explosions) {
            float alpha = 1 - ex.progress;  // 透明度随时间降低
            g2.setColor(new Color(255, 150, 50, (int)(alpha * 150)));
            g2.fillOval((int)(ex.x - ex.radius), (int)(ex.y - ex.radius),
                       (int)(ex.radius * 2), (int)(ex.radius * 2));
        }

        // ── 图例（右上角） ──
        g2.setFont(new Font("SimHei", Font.PLAIN, 12));
        int lx = 620, ly = 80;
        g2.setColor(Color.ORANGE);  g2.fillOval(lx, ly, 10, 10);
        g2.setColor(Color.WHITE);   g2.drawString("扇形弹幕", lx + 15, ly + 10);
        g2.setColor(Color.RED);     g2.fillOval(lx, ly + 20, 10, 10);
        g2.setColor(Color.WHITE);   g2.drawString("环形弹幕 / 红弹", lx + 15, ly + 30);
        g2.setColor(Color.BLUE);    g2.fillOval(lx, ly + 40, 10, 10);
        g2.setColor(Color.WHITE);   g2.drawString("旋转扫射 / 巨型蓝弹", lx + 15, ly + 50);
        g2.setColor(Color.CYAN);    g2.fillOval(lx, ly + 60, 10, 10);
        g2.setColor(Color.WHITE);   g2.drawString("冲撞路径", lx + 15, ly + 70);
    }

    /**
     * 绘制 Boss 本体及相关视觉元素。
     *
     * <p>包含：</p>
     * <ul>
     *   <li>Boss 本体（Boss1 红色圆形 / Boss2 紫色圆形）</li>
     *   <li>阶段标签（"Boss N  Phase M"）</li>
     *   <li>HP 条（绿/橙/红 三色根据血量百分比）</li>
     *   <li>冲撞状态（黄色光圈 + "!!冲撞中!!"）</li>
     *   <li>返回状态（绿色虚线 + "返回中..."）</li>
     * </ul>
     */
    private void drawBoss(Graphics2D g) {
        int bw = boss.getWidth(), bh = boss.getHeight();
        int bx = (int)(boss.getX() - bw / 2), by = (int)(boss.getY() - bh / 2);

        // Boss 本体颜色：Boss 1 红色 / Boss 2 紫色
        Color bossColor = boss.getBossLevel() == 1 ?
            new Color(220, 50, 50) : new Color(180, 30, 180);
        g.setColor(bossColor);
        g.fillOval(bx, by, bw, bh);

        // Boss 标记文字
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        String label = "Boss " + boss.getBossLevel() + "  Phase " + boss.getBossPhase();
        g.drawString(label, bx - 10, by - 8);

        // HP 条绘制
        int barW = bw + 20, barH = 6;
        float hpPct = (float)boss.getHp() / boss.getMaxHp();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(bx - 10, by - 16, barW, barH);
        // HP 颜色：绿(>50%) / 橙(>20%) / 红(≤20%)
        g.setColor(hpPct > 0.5f ? Color.GREEN : hpPct > 0.2f ? Color.ORANGE : Color.RED);
        g.fillRect(bx - 10, by - 16, (int)(barW * hpPct), barH);
        g.setColor(Color.WHITE);
        g.drawRect(bx - 10, by - 16, barW, barH);

        // 冲撞状态：黄色光圈 + 标签
        if (boss.isCharging()) {
            g.setColor(new Color(255, 255, 0, 150));
            g.drawOval(bx - 15, by - 15, bw + 30, bh + 30);
            g.drawString("!!冲撞中!!", bx - 20, by - 25);
        }

        // 返回原位状态：绿色虚线 + 标签
        if (boss.isReturning()) {
            float ox = boss.getChargeOriginX(), oy = boss.getChargeOriginY();
            g.setColor(new Color(100, 255, 100, 120));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{6, 4}, 0));
            g.drawLine((int)boss.getX(), (int)boss.getY(), (int)ox, (int)oy);
            g.setStroke(new BasicStroke(1)); // 恢复默认线宽
            g.setColor(new Color(100, 255, 100, 180));
            g.fillOval((int)ox - 8, (int)oy - 8, 16, 16);
            g.drawString("返回中...", bx - 15, by - 25);
        }
    }

    // ═══════════════════════════════════════════════
    // 阶段切换
    // ═══════════════════════════════════════════════

    /**
     * 切换到下一个测试阶段。
     * <p>所有 6 个阶段测试完毕后弹出完成提示对话框并退出。</p>
     */
    private void nextStage() {
        currentStage++;
        if (currentStage >= STAGES.length) {
            JOptionPane.showMessageDialog(this,
                "全部 6 个 Boss 阶段测试完成！\n\n" +
                "Boss 1: Phase 1 (扇形+环形) → Phase 2 (+冲撞) → Phase 3 (扇形10发+旋转扫射)\n" +
                "Boss 2: Phase 1 (双旋转+红弹) → Phase 2 (升级扫射+红弹3连发+冲撞) → Phase 3 (巨型蓝弹+扇形+回血)",
                "测试完成", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
            return;
        }
        setupBossForStage(currentStage);
        nextButton.setText("下一阶段 →");
        if (currentStage == STAGES.length - 1) {
            nextButton.setText("完成测试");
        }
        gameTimer.start();
    }

    // ═══════════════════════════════════════════════
    // 小型爆炸效果（内部类）
    // ═══════════════════════════════════════════════

    /**
     * 装饰用小型爆炸效果 — 随时间扩大并渐隐。
     */
    static class MiniExplosion {
        /** 爆炸中心 X */
        float x;
        /** 爆炸中心 Y */
        float y;
        /** 当前半径（随时间增大） */
        float radius;
        /** 动画进度（0→1），达 1 时标记 finished */
        float progress;
        /** 是否已完成 */
        boolean finished;

        /** 创建爆炸效果 */
        MiniExplosion(float x, float y, float r) { this.x = x; this.y = y; this.radius = r; }

        /**
         * 每帧更新：进度推进 + 半径扩大。
         * @param dt 帧时间增量
         */
        void update(float dt) {
            progress += dt * 2;     // 2 秒完成
            radius += dt * 20;      // 半径快速扩大
            if (progress >= 1) finished = true;
        }
    }

    // ═══════════════════════════════════════════════
    // Main
    // ═══════════════════════════════════════════════

    /**
     * 测试工具入口 — 创建 JFrame 并启动各阶段弹幕预览。
     *
     * <p>窗口布局：</p>
     * <ul>
     *   <li><b>顶部：</b>信息栏（阶段标题 + HP 详情 + 控制按钮）</li>
     *   <li><b>中央：</b>测试面板（Boss + 弹幕 + 图例）</li>
     * </ul>
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TestBossVisual panel = new TestBossVisual();

            // 创建主窗口
            JFrame frame = new JFrame("Boss 弹幕阶段测试");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            // ── 顶部信息栏 ──
            JPanel topPanel = new JPanel(new BorderLayout());
            topPanel.setBackground(Color.BLACK);

            // HP/Phase/计时信息
            panel.infoLabel = new JLabel("初始化中...");
            panel.infoLabel.setForeground(Color.WHITE);
            panel.infoLabel.setFont(new Font("SimHei", Font.PLAIN, 14));
            panel.infoLabel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
            topPanel.add(panel.infoLabel, BorderLayout.CENTER);

            // 阶段标题
            JLabel titleLabel = new JLabel(STAGES[0], SwingConstants.CENTER);
            titleLabel.setForeground(new Color(255, 200, 50));
            titleLabel.setFont(new Font("SimHei", Font.BOLD, 16));
            titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            titleLabel.setBackground(new Color(20, 20, 50));
            titleLabel.setOpaque(true);
            topPanel.add(titleLabel, BorderLayout.NORTH);

            // "下一阶段"按钮
            panel.nextButton = new JButton("下一阶段 →");
            panel.nextButton.setFont(new Font("SimHei", Font.BOLD, 14));
            panel.nextButton.setBackground(new Color(50, 150, 50));
            panel.nextButton.setForeground(Color.WHITE);
            panel.nextButton.setFocusPainted(false);
            panel.nextButton.addActionListener(ev -> {
                panel.gameTimer.stop();
                panel.nextStage();
                titleLabel.setText(STAGES[panel.currentStage]);
                titleLabel.repaint();
                panel.gameTimer.start();
            });
            topPanel.add(panel.nextButton, BorderLayout.EAST);

            // 暂停/继续按钮
            JButton pauseBtn = new JButton("暂停 / 继续");
            pauseBtn.setFont(new Font("SimHei", Font.PLAIN, 12));
            pauseBtn.addActionListener(ev -> {
                if (panel.gameTimer.isRunning()) {
                    panel.gameTimer.stop();
                    pauseBtn.setText("▶ 继续");
                } else {
                    panel.gameTimer.start();
                    pauseBtn.setText("⏸ 暂停");
                }
            });
            topPanel.add(pauseBtn, BorderLayout.WEST);

            // 组装窗口
            frame.setLayout(new BorderLayout());
            frame.add(topPanel, BorderLayout.NORTH);
            frame.add(panel, BorderLayout.CENTER);
            frame.pack();
            frame.setLocation(300, 100);
            frame.setVisible(true);

            // 启动第一个阶段的测试
            panel.setupBossForStage(0);
            panel.infoLabel.setText(String.format("HP: %d/%d | 护甲: %d | Phase: %d",
                panel.boss.getHp(), panel.boss.getMaxHp(), panel.boss.getArmor(),
                panel.boss.getBossPhase()));
            panel.gameTimer.start();
        });
    }
}
