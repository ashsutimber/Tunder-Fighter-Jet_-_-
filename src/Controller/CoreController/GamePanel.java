package Controller.CoreController;

import Entity.Bullet.*;
import Entity.Plane.*;
import Entity.Skill.*;
import Entity.Buff.BuffDrop;
import Controller.Coreiterface.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * <h1>GamePanel — 游戏界面面板（JPanel 子类）</h1>
 *
 * <p>GamePanel 是整个游戏的视觉渲染核心，负责所有画面元素的绘制和
 * Swing 窗口管理。它不参与游戏逻辑计算（由 GameEngine 负责），
 * 仅从 GameEngine 读取状态并渲染到屏幕上。</p>
 *
 * <p>采用 <b>双缓冲（Double Buffering）</b> 技术消除画面闪烁：
 * {@code setDoubleBuffered(true)} 确保所有绘制先在离屏缓冲区完成，
 * 再一次性显示到屏幕。</p>
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li><b>窗口管理：</b>创建 JFrame（800×600, 标题"雷霆战机"），不可调整大小</li>
 *   <li><b>图片资源加载：</b>从 src/image/ 目录加载所有飞机图片并缓存</li>
 *   <li><b>多画面渲染：</b>支持 6 种游戏画面状态的切换</li>
 *   <li><b>实体绘制：</b>玩家/敌机飞机、子弹、Buff球、爆炸特效</li>
 *   <li><b>HUD 绘制：</b>HP条（含护盾）、分数、波次、等级/经验、技能冷却、Buff状态</li>
 *   <li><b>Boss 冲撞视觉：</b>红色虚线预警 + 十字标记 + 返回路径虚线</li>
 *   <li><b>鼠标交互：</b>飞机选择卡片点击、升级卡选择点击</li>
 * </ul>
 *
 * <h2>6 种游戏画面状态</h2>
 * <table border="1">
 *   <tr><th>状态</th><th>判定条件</th><th>渲染内容</th></tr>
 *   <tr><td>标题画面</td><td>{@code isTitleScreen}</td><td>游戏标题、操作说明、星空背景</td></tr>
 *   <tr><td>飞机选择</td><td>{@code isPlaneSelecting}</td><td>4 张飞机卡片（属性 + 图片）</td></tr>
 *   <tr><td>游戏主画面</td><td>以上皆否</td><td>完整游戏场景 + HUD</td></tr>
 *   <tr><td>升级卡选择</td><td>{@code isCardSelecting}</td><td>半透明遮罩 + 3 张升级卡片</td></tr>
 *   <tr><td>游戏结束</td><td>{@code isGameOver}</td><td>"游戏结束" + 最终得分</td></tr>
 *   <tr><td>通关</td><td>{@code isGameWin}</td><td>"恭喜通关" + 最终得分</td></tr>
 * </table>
 *
 * <h2>渲染流程</h2>
 * <pre>
 * GameEngine.actionPerformed() 每 1/60 秒
 *   └─ gamePanel.repaintGame()
 *       └─ repaint()
 *           └─ paintComponent(g)
 *               ├─ isTitleScreen    → drawTitleScreen()      标题画面
 *               ├─ isPlaneSelecting → drawPlaneSelection()   飞机选择
 *               └─ 游戏主画面：
 *                   ├─ drawBackground()      滚动星空背景
 *                   ├─ drawEnemies()         敌机 + Boss HP条 + 冲撞线
 *                   ├─ drawPlayer()          玩家 + 无敌/过载/暗能光环
 *                   ├─ drawBullets()         玩家子弹 + 敌机子弹(含爆炸弹)
 *                   ├─ drawExplosions()      爆炸特效(双圈渐隐)
 *                   ├─ drawBuffDrops()       Buff掉落球(发光+高光)
 *                   ├─ drawHUD()             HP/分数/波次/技能/Buff状态
 *                   ├─ drawCardSelection()   升级卡选择(可选)
 *                   ├─ drawGameOver/drawGameWin  结束画面(可选)
 *                   └─ drawPauseOverlay()    暂停遮罩(可选)
 * </pre>
 *
 * <h2>视觉特效</h2>
 * <ul>
 *   <li><b>爆炸特效：</b>双圈渐隐（外圈橙色半透明 + 内圈黄色）</li>
 *   <li><b>Buff球：</b>发光光晕 + 球体 + 高光点（立体感）</li>
 *   <li><b>Boss冲撞预警：</b>红色虚线 + 十字标记 + 箭头</li>
 *   <li><b>无敌状态：</b>白色闪烁光环</li>
 *   <li><b>过载超频：</b>橙色光环</li>
 *   <li><b>暗能爆发：</b>紫色光环</li>
 *   <li><b>HP条颜色：</b>绿色(&gt;50%) / 橙色(&gt;20%) / 红色(≤20%)</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see GameEngine              游戏引擎（数据源）
 * @see Entity.Plane.PlayerPlane 玩家飞机
 * @see Entity.Plane.EnemyPlane  敌机
 * @see Entity.Buff.BuffDrop     Buff 掉落球
 */
public class GamePanel extends JPanel implements MouseListener {

    /** 关联的游戏引擎 — 从此获取所有游戏状态数据（实体列表、HUD 信息、画面状态标志） */
    private GameEngine engine;

    /** Swing 顶层窗口 — 标题"雷霆战机 — Thunder Fighter Jet"，800×600 固定大小 */
    private JFrame frame;

    /** 随机数生成器 — 用于星星位置/亮度/速度的随机化 */
    private Random random = new Random();

    // ── 图片缓存（loadImages() 中加载，按索引获取） ──
    /** 玩家飞机图片数组（索引 0=雷霆, 1=疾风, 2=破甲, 3=追猎），48×48 缩放 */
    private Image[] playerImages = new Image[4];
    /** 普通敌机图片数组（索引对应等级 Lv.1~4），48×48 缩放 */
    private Image[] normalEnemyImages = new Image[4];
    /** Boss 图片数组（索引 0=Boss1, 1=Boss2），48×48 缩放 */
    private Image[] bossImages = new Image[2];

    // ── 背景星星（模拟深空效果） ──
    /** 星空背景星星列表 — 每帧向下滚动，出界后从顶部重新生成 */
    private List<Star> stars = new ArrayList<>();
    /** 星星总数（固定 100 颗，分布在 800×600 画面中） */
    private static final int STAR_COUNT = 100;

    // ── 窗口配置 ──
    /** 游戏窗口宽度（px），固定 800 */
    private static final int WINDOW_WIDTH = 800;
    /** 游戏窗口高度（px），固定 600 */
    private static final int WINDOW_HEIGHT = 600;

    // ═══════════════════════════════════════════════
    // 构造方法
    // ═══════════════════════════════════════════════

    public GamePanel(GameEngine engine) {
        this.engine = engine;
        engine.setGamePanel(this);

        setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        setDoubleBuffered(true);
        setBackground(Color.BLACK);
        addMouseListener(this);

        // 创建窗口
        frame = new JFrame("雷霆战机 — Thunder Fighter Jet");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(this);
        frame.pack();
        frame.setLocation(350, 150);

        // 加载图片
        loadImages();

        // 初始化星星
        for (int i = 0; i < STAR_COUNT; i++) {
            stars.add(new Star(random.nextFloat() * WINDOW_WIDTH,
                               random.nextFloat() * WINDOW_HEIGHT));
        }

        // 键盘监听 - 支持标题画面按空格 / 飞机选择按数字键
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (engine.isPlaneSelecting()) {
                    int key = e.getKeyCode();
                    if (key >= KeyEvent.VK_1 && key <= KeyEvent.VK_4) {
                        engine.selectPlane(key - KeyEvent.VK_1 + 1);
                    } else if (key == KeyEvent.VK_ESCAPE) {
                        // 返回标题画面
                        engine.selectPlane(1); // 默认选1
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    engine.onTitleScreenAction();
                }
            }
        });
    }

    // ═══════════════════════════════════════════════
    // 图片加载
    // ═══════════════════════════════════════════════

    private void loadImages() {
        try {
            // 尝试从 src/image/ 加载
            String basePath = "src/image/";
            playerImages[0] = loadImage(basePath + "playerJet1.png");
            playerImages[1] = loadImage(basePath + "playerJet2.png");
            playerImages[2] = loadImage(basePath + "playerJet3.png");
            playerImages[3] = loadImage(basePath + "playerJet4.png");

            normalEnemyImages[0] = loadImage(basePath + "normalAnemyJet1.png");
            normalEnemyImages[1] = loadImage(basePath + "normalAnemyJet2.png");
            normalEnemyImages[2] = loadImage(basePath + "normalAnemyJet3.png");
            normalEnemyImages[3] = loadImage(basePath + "normalAnemyJet4.png");

            bossImages[0] = loadImage(basePath + "BossJet1.png");
            bossImages[1] = loadImage(basePath + "BossJet2.png");
        } catch (Exception e) {
            System.err.println("Warning: Could not load some images: " + e.getMessage());
        }
    }

    private Image loadImage(String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    // 缩放到合适大小
                    return img.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
                }
            }
            // 尝试类路径
            InputStream is = getClass().getClassLoader().getResourceAsStream(path.replace("src/", ""));
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) return img.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
            }
        } catch (Exception e) {
            // 静默处理
        }
        return null;
    }

    // ═══════════════════════════════════════════════
    // 获取敌机对应图片
    // ═══════════════════════════════════════════════

    private Image getEnemyImage(EnemyPlane enemy) {
        switch (enemy.getEnemyLevel()) {
            case LEVEL_1: return normalEnemyImages[0];
            case LEVEL_2: return normalEnemyImages[1];
            case LEVEL_3: return normalEnemyImages[2];
            case LEVEL_4: return normalEnemyImages[3];
            case BOSS:
                return bossImages[enemy.getBossLevel() == 2 ? 1 : 0];
            default: return normalEnemyImages[0];
        }
    }

    private Image getPlayerImage() {
        // 根据玩家选择的飞机类型返回对应图片
        int type = engine.getPlayerPlane().getPlaneType(); // 1~4
        if (type >= 1 && type <= 4 && playerImages[type - 1] != null) {
            return playerImages[type - 1];
        }
        return playerImages[0]; // 兜底
    }

    // ═══════════════════════════════════════════════
    // 绘制
    // ═══════════════════════════════════════════════

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (engine.isTitleScreen()) {
            drawTitleScreen(g2d);
            return;
        }

        if (engine.isPlaneSelecting()) {
            drawPlaneSelection(g2d);
            return;
        }

        // 背景
        drawBackground(g2d);

        // 实体
        drawEnemies(g2d);
        drawPlayer(g2d);
        drawBullets(g2d);
        drawExplosions(g2d);
        drawBuffDrops(g2d);

        // HUD
        drawHUD(g2d);

        // 升级卡选择界面
        if (engine.isCardSelecting()) {
            drawCardSelection(g2d);
        }

        // 游戏结束/胜利
        if (engine.isGameOver()) {
            drawGameOver(g2d);
        } else if (engine.isGameWin()) {
            drawGameWin(g2d);
        }

        // 暂停
        if (engine.isPaused() && !engine.isCardSelecting()) {
            drawPauseOverlay(g2d);
        }

        Toolkit.getDefaultToolkit().sync();
    }

    // ═══════════════════════════════════════════════
    // 标题画面
    // ═══════════════════════════════════════════════

    private void drawTitleScreen(Graphics2D g) {
        // 深色背景
        g.setColor(new Color(5, 5, 20));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        // 星星
        for (Star s : stars) {
            s.y += s.speed;
            if (s.y > WINDOW_HEIGHT) { s.y = 0; s.x = random.nextFloat() * WINDOW_WIDTH; }
            g.setColor(new Color(255, 255, 255, (int)(s.brightness * 255)));
            int size = s.size;
            g.fillOval((int)s.x, (int)s.y, size, size);
        }

        // 标题
        g.setFont(new Font("SimHei", Font.BOLD, 48));
        g.setColor(new Color(255, 200, 50));
        String title = "雷霆战机";
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(title);
        g.drawString(title, (WINDOW_WIDTH - tw) / 2, 180);

        // 副标题
        g.setFont(new Font("SimHei", Font.ITALIC, 20));
        g.setColor(new Color(150, 200, 255));
        String sub = "Thunder Fighter Jet";
        fm = g.getFontMetrics();
        g.drawString(sub, (WINDOW_WIDTH - fm.stringWidth(sub)) / 2, 220);

        // 操作说明
        g.setFont(new Font("SimHei", Font.PLAIN, 16));
        g.setColor(Color.WHITE);
        String[] controls = {
            "操作说明:",
            "WASD / 方向键 — 移动",
            "空格键 / J — 射击",
            "1 / 2 / 3 — 使用技能",
            "ESC — 暂停"
        };
        int yPos = 300;
        for (String line : controls) {
            fm = g.getFontMetrics();
            g.drawString(line, (WINDOW_WIDTH - fm.stringWidth(line)) / 2, yPos);
            yPos += 28;
        }

        // 开始提示
        g.setFont(new Font("SimHei", Font.BOLD, 24));
        g.setColor(new Color(255, 255, 100));
        String start = "按 空格键 或 回车键 开始游戏";
        fm = g.getFontMetrics();
        int blinkAlpha = (int)((Math.sin(System.currentTimeMillis() * 0.004) + 1) * 127);
        g.setColor(new Color(255, 255, 100, Math.max(50, blinkAlpha)));
        g.drawString(start, (WINDOW_WIDTH - fm.stringWidth(start)) / 2, 460);
    }

    // ═══════════════════════════════════════════════
    // 飞机选择界面
    // ═══════════════════════════════════════════════

    private void drawPlaneSelection(Graphics2D g) {
        // 深色背景
        g.setColor(new Color(5, 5, 20));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        // 星星
        for (Star s : stars) {
            s.y += s.speed;
            if (s.y > WINDOW_HEIGHT) { s.y = 0; s.x = random.nextFloat() * WINDOW_WIDTH; }
            g.setColor(new Color(255, 255, 255, (int)(s.brightness * 255)));
            g.fillOval((int)s.x, (int)s.y, s.size, s.size);
        }

        // 标题
        g.setFont(new Font("SimHei", Font.BOLD, 30));
        g.setColor(new Color(255, 200, 50));
        String title = "选择你的战机";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (WINDOW_WIDTH - fm.stringWidth(title)) / 2, 55);

        // 飞机卡片
        String[] names = {"雷霆", "疾风", "破甲", "追猎"};
        String[] descs = {"均衡型", "高速型", "重甲型", "追踪型"};
        String[] bullets = {"普通弹", "普通弹", "穿甲弹", "追踪导弹"};
        int[] hps = {3000, 2500, 4000, 2800};
        int[] armors = {50, 30, 80, 40};
        float[] speeds = {5.0f, 7.0f, 3.5f, 5.5f};

        int cardWidth = 160;
        int cardHeight = 300;
        int totalWidth = 4 * cardWidth + 3 * 15;
        int startX = (WINDOW_WIDTH - totalWidth) / 2;
        int cardY = 180;

        for (int i = 0; i < 4; i++) {
            int cx = startX + i * (cardWidth + 15);

            // 卡片背景
            g.setColor(new Color(30, 30, 50));
            g.fillRoundRect(cx, cardY, cardWidth, cardHeight, 10, 10);
            g.setColor(new Color(100, 100, 150));
            g.drawRoundRect(cx, cardY, cardWidth, cardHeight, 10, 10);

            // 飞机图片
            Image img = playerImages[i];
            if (img != null) {
                g.drawImage(img, cx + (cardWidth - 64) / 2, cardY + 15, 64, 64, null);
            } else {
                g.setColor(Color.BLUE);
                g.fillRect(cx + (cardWidth - 48) / 2, cardY + 20, 48, 48);
            }

            // 飞机名称
            g.setFont(new Font("SimHei", Font.BOLD, 18));
            g.setColor(Color.WHITE);
            fm = g.getFontMetrics();
            g.drawString(names[i], cx + (cardWidth - fm.stringWidth(names[i])) / 2, cardY + 100);

            // 类型描述
            g.setFont(new Font("SimHei", Font.PLAIN, 12));
            g.setColor(new Color(150, 200, 255));
            fm = g.getFontMetrics();
            g.drawString(descs[i], cx + (cardWidth - fm.stringWidth(descs[i])) / 2, cardY + 120);

            // 属性
            g.setFont(new Font("SimHei", Font.PLAIN, 11));
            g.setColor(new Color(200, 200, 200));
            int ly = cardY + 145;
            g.drawString("生命值: " + hps[i], cx + 12, ly);
            g.drawString("护甲值: " + armors[i], cx + 12, ly + 20);
            g.drawString("移动速度: " + speeds[i] + " px/f", cx + 12, ly + 40);
            g.drawString("初始子弹: " + bullets[i], cx + 12, ly + 60);

            // 选择提示
            g.setFont(new Font("SimHei", Font.BOLD, 12));
            g.setColor(new Color(255, 200, 50));
            String hint = "按 " + (i + 1) + " 选择";
            fm = g.getFontMetrics();
            g.drawString(hint, cx + (cardWidth - fm.stringWidth(hint)) / 2, cardY + cardHeight - 15);
        }
    }

    // ═══════════════════════════════════════════════
    // 背景绘制
    // ═══════════════════════════════════════════════

    private void drawBackground(Graphics2D g) {
        // 深空背景
        g.setColor(new Color(5, 5, 25));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        // 滚动星星
        for (Star s : stars) {
            s.y += s.speed;
            if (s.y > WINDOW_HEIGHT) {
                s.y = -2;
                s.x = random.nextFloat() * WINDOW_WIDTH;
            }
            g.setColor(new Color(255, 255, 255, (int)(s.brightness * 180 + 75)));
            g.fillOval((int)s.x, (int)s.y, s.size, s.size);
        }
    }

    // ═══════════════════════════════════════════════
    // 玩家绘制
    // ═══════════════════════════════════════════════

    private void drawPlayer(Graphics2D g) {
        PlayerPlane p = engine.getPlayerPlane();
        if (!p.isAlive()) return;

        Image img = getPlayerImage();
        int w = p.getWidth();
        int h = p.getHeight();
        int drawX = (int)(p.getX() - w / 2);
        int drawY = (int)(p.getY() - h / 2);

        if (img != null) {
            g.drawImage(img, drawX, drawY, w, h, null);
        } else {
            // 默认绘制：蓝色三角形
            g.setColor(new Color(50, 150, 255));
            int[] xPts = {(int)p.getX(), (int)(p.getX() - w/2), (int)(p.getX() + w/2)};
            int[] yPts = {(int)(p.getY() - h/2), (int)(p.getY() + h/2), (int)(p.getY() + h/2)};
            g.fillPolygon(xPts, yPts, 3);
        }

        // 无敌效果：白色闪烁
        if (p.isInvincible()) {
            g.setColor(new Color(255, 255, 255, 80));
            g.drawOval(drawX - 5, drawY - 5, w + 10, h + 10);
        }

        // 过载超频效果
        if (p.isOverclockActive()) {
            g.setColor(new Color(255, 100, 0, 100));
            g.drawOval(drawX - 3, drawY - 3, w + 6, h + 6);
        }

        // 暗能爆发效果
        if (p.isDarkBurstActive()) {
            g.setColor(new Color(180, 0, 255, 80));
            g.drawOval(drawX - 8, drawY - 8, w + 16, h + 16);
        }
    }

    // ═══════════════════════════════════════════════
    // 敌机绘制
    // ═══════════════════════════════════════════════

    private void drawEnemies(Graphics2D g) {
        for (EnemyPlane enemy : engine.getEnemies()) {
            if (!enemy.isAlive()) continue;

            Image img = getEnemyImage(enemy);
            int w = enemy.getWidth();
            int h = enemy.getHeight();
            int drawX = (int)(enemy.getX() - w / 2);
            int drawY = (int)(enemy.getY() - h / 2);

            if (img != null) {
                g.drawImage(img, drawX, drawY, w, h, null);
            } else {
                // 默认绘制：红色三角形（敌机）/ 大红色六边形（Boss）
                if (enemy.isBoss()) {
                    g.setColor(new Color(220, 50, 50));
                    g.fillOval(drawX, drawY, w, h);
                } else {
                    g.setColor(new Color(255, 80, 80));
                    int[] xPts = {(int)enemy.getX(), (int)(enemy.getX() - w/2), (int)(enemy.getX() + w/2)};
                    int[] yPts = {(int)(enemy.getY() + h/2), (int)(enemy.getY() - h/2), (int)(enemy.getY() - h/2)};
                    g.fillPolygon(xPts, yPts, 3);
                }
            }

            // 普通敌机 HP 显示
            if (!enemy.isBoss()) {
                g.setFont(new Font("SimHei", Font.PLAIN, 9));
                g.setColor(new Color(255, 255, 255, 200));
                String hpStr = "HP:" + enemy.getHp() + "/" + enemy.getMaxHp();
                FontMetrics fm = g.getFontMetrics();
                int textW = fm.stringWidth(hpStr);
                g.drawString(hpStr, (int) enemy.getX() - textW / 2, drawY - 4);
            }

            // Boss HP 条
            if (enemy.isBoss()) {
                int barW = w + 20;
                int barH = 6;
                int barX = drawX - 10;
                int barY = drawY - 12;
                float hpPercent = (float)enemy.getHp() / enemy.getMaxHp();

                g.setColor(Color.DARK_GRAY);
                g.fillRect(barX, barY, barW, barH);
                Color hpColor = hpPercent > 0.5f ? Color.GREEN :
                                hpPercent > 0.2f ? Color.ORANGE : Color.RED;
                g.setColor(hpColor);
                g.fillRect(barX, barY, (int)(barW * hpPercent), barH);
                g.setColor(Color.WHITE);
                g.drawRect(barX, barY, barW, barH);

                // Boss 阶段标签
                g.setFont(new Font("SimHei", Font.BOLD, 10));
                g.setColor(Color.WHITE);
                g.drawString("Phase " + enemy.getBossPhase(), barX, barY - 2);
            }

            // 冲撞预警 / 冲撞中 / 返回中：绘制红色直虚线指向记录的玩家位置
            if (enemy.isChargeWarning()) {
                // 预警阶段：红色虚线指向记录的玩家位置
                drawChargeDashedLine(g, enemy, new Color(255, 50, 50, 200));
                // 预警文字
                g.setFont(new Font("SimHei", Font.BOLD, 14));
                g.setColor(new Color(255, 80, 80, 220));
                g.drawString("⚠ 冲撞预警", drawX + w / 2 - 30, drawY - 18);
            }
            if (enemy.isCharging()) {
                // 冲撞中：红色虚线 + 红色光圈
                drawChargeDashedLine(g, enemy, new Color(255, 0, 0, 220));
                g.setColor(new Color(255, 0, 0, 150));
                g.drawOval(drawX - 10, drawY - 10, w + 20, h + 20);
                g.setFont(new Font("SimHei", Font.BOLD, 12));
                g.drawString("冲撞!", drawX + w / 2 - 18, drawY - 18);
            }
            if (enemy.isReturning()) {
                // 返回中：从当前位置到原点的虚线
                drawReturnDashedLine(g, enemy, new Color(255, 100, 100, 150));
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 子弹绘制
    // ═══════════════════════════════════════════════

    private void drawBullets(Graphics2D g) {
        // 玩家子弹
        for (Bullet b : engine.getPlayerBullets()) {
            if (!b.isActive()) continue;
            g.setColor(b.getColor() != null ? b.getColor() : Color.CYAN);
            int size = (int)b.getBulletSize();
            g.fillOval((int)(b.getX() - size/2), (int)(b.getY() - size/2), size, size);
        }

        // 敌机子弹
        for (Bullet b : engine.getEnemyBullets()) {
            if (!b.isActive()) continue;
            int size = (int)b.getBulletSize();

            // 爆炸弹特殊渲染：待引爆时闪烁红光
            if (b.isBomb() && b.isBombArmed()) {
                float flash = (float)(Math.sin(System.currentTimeMillis() * 0.02) + 1) / 2;
                g.setColor(new Color(255, (int)(100 * flash), 0, 220));
                g.fillOval((int)(b.getX() - size), (int)(b.getY() - size), size * 2, size * 2);
                // 爆炸范围提示
                g.setColor(new Color(255, 50, 0, 60));
                g.drawOval((int)(b.getX() - b.getBombRadius()), (int)(b.getY() - b.getBombRadius()),
                          (int)(b.getBombRadius() * 2), (int)(b.getBombRadius() * 2));
            } else {
                g.setColor(b.getColor() != null ? b.getColor() : Color.RED);
                g.fillOval((int)(b.getX() - size/2), (int)(b.getY() - size/2), size, size);
            }

            // 巨型蓝弹光晕
            if (size >= 20) {
                g.setColor(new Color(50, 100, 255, 50));
                g.fillOval((int)(b.getX() - size), (int)(b.getY() - size), size * 2, size * 2);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 爆炸效果绘制
    // ═══════════════════════════════════════════════

    private void drawExplosions(Graphics2D g) {
        for (GameEngine.Explosion exp : engine.getExplosions()) {
            float progress = exp.getProgress();
            float radius = exp.getRadius();
            float alpha = exp.getAlpha();

            // 外圈
            g.setColor(new Color(255, 150, 0, (int)(alpha * 200)));
            g.fillOval((int)(exp.getX() - radius), (int)(exp.getY() - radius),
                       (int)(radius * 2), (int)(radius * 2));

            // 内圈
            g.setColor(new Color(255, 255, 100, (int)(alpha * 255)));
            g.fillOval((int)(exp.getX() - radius * 0.5f), (int)(exp.getY() - radius * 0.5f),
                       (int)radius, (int)radius);
        }
    }

    // ═══════════════════════════════════════════════
    // Buff 掉落球绘制
    // ═══════════════════════════════════════════════

    private void drawBuffDrops(Graphics2D g) {
        for (BuffDrop drop : engine.getBuffDrops()) {
            if (!drop.isActive()) continue;

            int r = (int) drop.getRadius();
            int drawX = (int) (drop.getX() - r);
            int drawY = (int) (drop.getY() - r);

            // 发光光晕
            g.setColor(new Color(drop.getColor().getRed(),
                                 drop.getColor().getGreen(),
                                 drop.getColor().getBlue(), 60));
            g.fillOval(drawX - 4, drawY - 4, r * 2 + 8, r * 2 + 8);

            // 球体主体
            g.setColor(drop.getColor());
            g.fillOval(drawX, drawY, r * 2, r * 2);

            // 高光（使球看起来有立体感）
            g.setColor(new Color(255, 255, 255, 150));
            g.fillOval(drawX + r / 3, drawY + r / 3, r / 2, r / 2);
        }
    }

    // ═══════════════════════════════════════════════
    // HUD 绘制
    // ═══════════════════════════════════════════════

    private void drawHUD(Graphics2D g) {
        PlayerPlane p = engine.getPlayerPlane();

        // ── 左上角：HP 条 ──
        int hudX = 15, hudY = 15;
        g.setFont(new Font("SimHei", Font.BOLD, 12));

        // HP 条
        int hpBarW = 200, hpBarH = 20;
        g.setColor(Color.DARK_GRAY);
        g.fillRect(hudX, hudY, hpBarW, hpBarH);
        float hpPercent = (float)p.getHp() / p.getMaxHp();
        Color hpColor = hpPercent > 0.5f ? Color.GREEN : hpPercent > 0.25f ? Color.ORANGE : Color.RED;
        g.setColor(hpColor);
        g.fillRect(hudX, hudY, (int)(hpBarW * hpPercent), hpBarH);

        // 护盾 HP（蓝色血条叠加）
        if (p.getShieldHp() > 0) {
            g.setColor(new Color(50, 150, 255, 180));
            int shieldW = (int)(hpBarW * (float)p.getShieldHp() / 300);
            g.fillRect(hudX, hudY - 4, Math.min(shieldW, hpBarW), 4);
        }

        g.setColor(Color.WHITE);
        g.drawRect(hudX, hudY, hpBarW, hpBarH);
        String hpText = "HP: " + p.getHp() + " / " + p.getMaxHp();
        g.drawString(hpText, hudX + 8, hudY + 15);

        // ── 自动开火指示 ──
        if (p.isAutoFire()) {
            g.setColor(new Color(255, 200, 0));
            g.setFont(new Font("SimHei", Font.BOLD, 11));
            g.drawString("⚡ AUTO FIRE ON", hudX + hpBarW + 10, hudY + 15);
        }

        // ── 右上角：分数/波次/等级 + 属性面板 ──
        int rx = 570, ry = 22;
        g.setColor(Color.WHITE);
        g.setFont(new Font("SimHei", Font.BOLD, 13));
        g.drawString("Score: " + engine.getScore(), rx, ry);
        g.drawString("Wave: " + engine.getCurrentWave() + " / 7", rx, ry + 18);
        g.drawString("Lv: " + p.getLevel(), rx, ry + 36);
        g.drawString("XP: " + p.getXp() + "/" + p.getXpForNextLevel(), rx, ry + 54);

        // 属性面板 (半透明背景)
        int statX = rx, statY = ry + 70;
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(statX - 4, statY - 4, 175, 105, 8, 8);
        g.setColor(new Color(100, 100, 100, 120));
        g.drawRoundRect(statX - 4, statY - 4, 175, 105, 8, 8);

        g.setFont(new Font("SimHei", Font.PLAIN, 11));
        int lineH = 18;
        Entity.Bullet.Bullet bl = p.getBullet();
        g.setColor(Color.WHITE);
        g.drawString("攻击力: " + bl.getAttack(), statX + 2, statY + lineH);
        g.drawString("暴击率: " + (int)(bl.getCritRate() * 100) + "%", statX + 2, statY + lineH * 2);
        g.drawString("子弹速度: " + (int)bl.getSpeed() + " px/f", statX + 2, statY + lineH * 3);
        g.drawString("护甲值: " + p.getArmor(), statX + 2, statY + lineH * 4);
        g.drawString("穿甲值: " + bl.getArmorDepth(), statX + 2, statY + lineH * 5);

        // 子弹类型
        String bulletType = bl instanceof Entity.Bullet.MissileBullet ? "导弹" :
                            bl instanceof Entity.Bullet.ArmorBullet ? "穿甲弹" : "普通弹";
        g.setColor(new Color(200, 200, 100));
        g.drawString("子弹: " + bulletType, statX + 2, statY + lineH * 5 + 14);

        // ── 左下角：技能栏 ──
        List<Skill> skills = p.getSkills();
        if (!skills.isEmpty()) {
            g.setFont(new Font("SimHei", Font.PLAIN, 11));
            int skillY = WINDOW_HEIGHT - 80;
            for (int i = 0; i < skills.size(); i++) {
                Skill s = skills.get(i);
                int sx = hudX + i * 130;
                g.setColor(new Color(30, 30, 60, 180));
                g.fillRect(sx, skillY, 125, 45);
                g.setColor(Color.WHITE);
                g.drawRect(sx, skillY, 125, 45);
                g.drawString((i+1) + ": " + s.getName(), sx + 5, skillY + 18);

                // CD 指示
                float cdRemain = s.getCooldownRemaining(engine.getGameTime());
                if (cdRemain > 0 && !s.isPassive()) {
                    g.setColor(Color.RED);
                    g.drawString("CD: " + (int)cdRemain + "s", sx + 5, skillY + 35);
                } else if (s.isPassive()) {
                    g.setColor(Color.GREEN);
                    g.drawString("被动", sx + 5, skillY + 35);
                } else {
                    g.setColor(Color.GREEN);
                    g.drawString("就绪", sx + 5, skillY + 35);
                }
            }
        }

        // ── 右下角：Buff 状态 ──
        if (p.getFireLevel() > 0) {
            g.setColor(Color.ORANGE);
            g.setFont(new Font("SimHei", Font.BOLD, 12));
            g.drawString("火力 Lv." + p.getFireLevel(), 650, WINDOW_HEIGHT - 30);
        }
    }

    // ═══════════════════════════════════════════════
    // 升级卡选择界面
    // ═══════════════════════════════════════════════

    private void drawCardSelection(Graphics2D g) {
        // 半透明遮罩
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        List<GameEngine.UpgradeCard> cards = engine.getCurrentCards();
        if (cards.isEmpty()) return;

        // 标题
        g.setFont(new Font("SimHei", Font.BOLD, 28));
        g.setColor(new Color(255, 220, 50));
        String title = "选择升级卡";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (WINDOW_WIDTH - fm.stringWidth(title)) / 2, 100);

        // 技能卡提示
        g.setFont(new Font("SimHei", Font.PLAIN, 14));
        g.setColor(Color.LIGHT_GRAY);
        String hint = "点击卡片选择";
        fm = g.getFontMetrics();
        g.drawString(hint, (WINDOW_WIDTH - fm.stringWidth(hint)) / 2, 125);

        // 卡片
        int cardWidth = 200;
        int cardHeight = 300;
        int totalWidth = cards.size() * cardWidth + (cards.size() - 1) * 20;
        int startX = (WINDOW_WIDTH - totalWidth) / 2;
        int cardY = 150;

        for (int i = 0; i < cards.size(); i++) {
            GameEngine.UpgradeCard card = cards.get(i);
            int cx = startX + i * (cardWidth + 20);

            // 卡片背景
            Color bgColor = new Color(40, 40, 40);
            switch (card.type) {
                case SKILL:  bgColor = new Color(50, 20, 80); break;
                case PLANE:  bgColor = new Color(20, 50, 20); break;
                case WEAPON: bgColor = new Color(80, 40, 20); break;
            }
            g.setColor(bgColor);
            g.fillRoundRect(cx, cardY, cardWidth, cardHeight, 12, 12);

            // 卡片边框
            g.setColor(Color.WHITE);
            g.drawRoundRect(cx, cardY, cardWidth, cardHeight, 12, 12);

            // 类型标签
            g.setFont(new Font("SimHei", Font.BOLD, 11));
            String typeLabel = card.type == GameEngine.CardType.SKILL ? "技能" :
                               card.type == GameEngine.CardType.PLANE ? "飞机" : "武器";
            g.setColor(card.type == GameEngine.CardType.SKILL ? new Color(200, 150, 255) :
                       card.type == GameEngine.CardType.PLANE ? new Color(150, 255, 150) :
                       new Color(255, 200, 150));
            g.drawString("[" + typeLabel + "]", cx + 10, cardY + 25);

            // 卡片名称
            g.setFont(new Font("SimHei", Font.BOLD, 18));
            g.setColor(Color.WHITE);
            g.drawString(card.name, cx + 10, cardY + 55);

            // 描述（多行）
            g.setFont(new Font("SimHei", Font.PLAIN, 12));
            g.setColor(new Color(200, 200, 200));
            String desc = card.description;
            int lineHeight = 18;
            int descY = cardY + 85;
            // 简单换行
            String[] words = desc.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (g.getFontMetrics().stringWidth(line + word) < cardWidth - 20) {
                    if (line.length() > 0) line.append(" ");
                    line.append(word);
                } else {
                    g.drawString(line.toString(), cx + 10, descY);
                    descY += lineHeight;
                    line = new StringBuilder(word);
                }
            }
            if (line.length() > 0) {
                g.drawString(line.toString(), cx + 10, descY);
            }

            // 底部：点击提示
            g.setFont(new Font("SimHei", Font.ITALIC, 10));
            g.setColor(Color.GRAY);
            g.drawString("点击选择 (" + (i+1) + ")", cx + 10, cardY + cardHeight - 15);
        }
    }

    // ═══════════════════════════════════════════════
    // 结束画面
    // ═══════════════════════════════════════════════

    private void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        g.setFont(new Font("SimHei", Font.BOLD, 48));
        g.setColor(Color.RED);
        String text = "游戏结束";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (WINDOW_WIDTH - fm.stringWidth(text)) / 2, 250);

        g.setFont(new Font("SimHei", Font.PLAIN, 20));
        g.setColor(Color.WHITE);
        String scoreText = "最终得分: " + engine.getScore();
        fm = g.getFontMetrics();
        g.drawString(scoreText, (WINDOW_WIDTH - fm.stringWidth(scoreText)) / 2, 300);

        g.setFont(new Font("SimHei", Font.PLAIN, 18));
        g.setColor(Color.LIGHT_GRAY);
        String restart = "按 空格键 重新开始";
        fm = g.getFontMetrics();
        g.drawString(restart, (WINDOW_WIDTH - fm.stringWidth(restart)) / 2, 370);
    }

    private void drawGameWin(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        g.setFont(new Font("SimHei", Font.BOLD, 48));
        g.setColor(new Color(255, 220, 50));
        String text = "恭喜通关!";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (WINDOW_WIDTH - fm.stringWidth(text)) / 2, 250);

        g.setFont(new Font("SimHei", Font.PLAIN, 20));
        g.setColor(Color.WHITE);
        String scoreText = "最终得分: " + engine.getScore();
        fm = g.getFontMetrics();
        g.drawString(scoreText, (WINDOW_WIDTH - fm.stringWidth(scoreText)) / 2, 300);

        g.setFont(new Font("SimHei", Font.PLAIN, 18));
        g.setColor(Color.LIGHT_GRAY);
        String restart = "按 空格键 重新开始";
        fm = g.getFontMetrics();
        g.drawString(restart, (WINDOW_WIDTH - fm.stringWidth(restart)) / 2, 370);
    }

    // ═══════════════════════════════════════════════
    // 暂停遮罩
    // ═══════════════════════════════════════════════

    private void drawPauseOverlay(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        g.setFont(new Font("SimHei", Font.BOLD, 36));
        g.setColor(Color.WHITE);
        String text = "暂停";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (WINDOW_WIDTH - fm.stringWidth(text)) / 2, 280);

        g.setFont(new Font("SimHei", Font.PLAIN, 16));
        g.setColor(Color.LIGHT_GRAY);
        String hint = "按 ESC 继续";
        fm = g.getFontMetrics();
        g.drawString(hint, (WINDOW_WIDTH - fm.stringWidth(hint)) / 2, 320);
    }

    // ═══════════════════════════════════════════════
    // MouseListener — 卡片选择
    // ═══════════════════════════════════════════════

    @Override
    public void mouseClicked(MouseEvent e) {
        if (engine.isPlaneSelecting()) {
            engine.handlePlaneClick(e.getX(), e.getY());
        } else if (engine.isCardSelecting()) {
            engine.handleCardClick(e.getX(), e.getY());
        }
    }

    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    // ═══════════════════════════════════════════════
    // 公共方法
    // ═══════════════════════════════════════════════

    public JFrame getFrame() { return frame; }
    public void repaintGame() { repaint(); }

    // ═══════════════════════════════════════════════
    // 冲撞虚线绘制
    // ═══════════════════════════════════════════════

    /**
     * 沿 Boss 冲撞方向绘制红色直虚线，指向记录的玩家位置
     */
    private void drawChargeDashedLine(Graphics2D g, EnemyPlane enemy, Color color) {
        float bx = enemy.getX();
        float by = enemy.getY();
        float tx = enemy.getChargeTargetX();
        float ty = enemy.getChargeTargetY();

        // 计算从 Boss 到记录玩家位置的方向
        float dx = tx - bx;
        float dy = ty - by;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001f) return;

        // 归一化方向
        float ndx = dx / dist;
        float ndy = dy / dist;

        // 线段从 Boss 延伸到记录玩家位置，并继续延伸到屏幕边界
        float extendLen = 800;
        float endX = bx + ndx * extendLen;
        float endY = by + ndy * extendLen;

        // 裁剪到屏幕边界
        if (endX < 0) { endY = by + ndy * (0 - bx) / ndx; endX = 0; }
        if (endX > 800) { endY = by + ndy * (800 - bx) / ndx; endX = 800; }
        if (endY < 0) { endX = bx + ndx * (0 - by) / ndy; endY = 0; }
        if (endY > 600) { endX = bx + ndx * (600 - by) / ndy; endY = 600; }

        // 绘制红色虚线
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                     0, new float[]{8, 6}, 0));
        g.setColor(color);
        g.drawLine((int) bx, (int) by, (int) endX, (int) endY);
        g.setStroke(oldStroke);

        // 在记录玩家位置处画十字标记
        if (tx >= 0 && tx <= 800 && ty >= 0 && ty <= 600) {
            int crossSize = 10;
            g.setColor(new Color(255, 50, 50, 180));
            g.drawLine((int) tx - crossSize, (int) ty, (int) tx + crossSize, (int) ty);
            g.drawLine((int) tx, (int) ty - crossSize, (int) tx, (int) ty + crossSize);
            // 小圆圈
            g.drawOval((int) tx - 6, (int) ty - 6, 12, 12);
        }

        // 在末端画箭头
        drawArrowHead(g, bx, by, endX, endY, color);
    }

    /**
     * 绘制返回路径虚线（从 Boss 当前位置到原始位置）
     */
    private void drawReturnDashedLine(Graphics2D g, EnemyPlane enemy, Color color) {
        float bx = enemy.getX();
        float by = enemy.getY();
        float ox = enemy.getChargeOriginX();
        float oy = enemy.getChargeOriginY();

        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                     0, new float[]{6, 4}, 0));
        g.setColor(color);
        g.drawLine((int) bx, (int) by, (int) ox, (int) oy);
        g.setStroke(oldStroke);
    }

    /**
     * 在虚线末端绘制小三角箭头
     */
    private void drawArrowHead(Graphics2D g, float x1, float y1, float x2, float y2, Color color) {
        float angle = (float) Math.atan2(y2 - y1, x2 - x1);
        float arrowSize = 8;
        float ax1 = x2 - arrowSize * (float) Math.cos(angle - Math.PI / 6);
        float ay1 = y2 - arrowSize * (float) Math.sin(angle - Math.PI / 6);
        float ax2 = x2 - arrowSize * (float) Math.cos(angle + Math.PI / 6);
        float ay2 = y2 - arrowSize * (float) Math.sin(angle + Math.PI / 6);

        g.setColor(color);
        g.fillPolygon(
            new int[]{(int) x2, (int) ax1, (int) ax2},
            new int[]{(int) y2, (int) ay1, (int) ay2},
            3
        );
    }

    // ═══════════════════════════════════════════════
    // 星星内部类
    // ═══════════════════════════════════════════════

    private static class Star {
        float x, y;
        float speed;
        int size;
        float brightness;

        Star(float x, float y) {
            this.x = x;
            this.y = y;
            this.speed = 0.5f + (float)Math.random() * 2.0f;
            this.size = 1 + (int)(Math.random() * 3);
            this.brightness = 0.3f + (float)Math.random() * 0.7f;
        }
    }
}
