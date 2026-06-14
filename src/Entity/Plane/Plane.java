package Entity.Plane;

import Entity.Bullet.*;
import Entity.Buff.*;
import Entity.Skill.*;
import java.awt.Rectangle;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <h1>飞机抽象基类 — 所有飞行单位的通用模板</h1>
 *
 * <p>Plane 定义了游戏中所有飞行单位（玩家飞机、普通敌机、Boss 敌机）的
 * 通用属性和行为。作为抽象基类，它提供了生命、子弹、Buff、技能、成长等
 * 核心子系统的默认实现，子类只需覆写抽象方法即可。</p>
 *
 * <h2>核心子系统</h2>
 *
 * <h3>1. 生命系统</h3>
 * <ul>
 *   <li><b>HP（hp / maxHp）：</b>基础生命值，归零即死亡</li>
 *   <li><b>护盾（shieldHp）：</b>额外血量，优先于 HP 扣除</li>
 *   <li><b>护甲（armor）：</b>用于破甲计算，影响伤害和暴击率修正</li>
 *   <li><b>无敌状态（invincible）：</b>受击不掉血（雷霆瞬闪）</li>
 * </ul>
 *
 * <h3>2. 子弹系统</h3>
 * <p>Plane 持有子弹模板（{@code bullet}），射击时创建模板副本。
 * 攻击速度（{@code atkSpeed}）控制射击冷却。实际射击由
 * {@link #doShoot(float, float, float)} 创建子弹副本并应用技能/Buff 修正。</p>
 *
 * <h3>3. 增益系统</h3>
 * <p>通过 {@code buffs} 列表管理当前生效的 Buff。
 * {@link #checkExpiredBuffs(float)} 每帧检查并移除过期 Buff。</p>
 *
 * <h3>4. 技能系统</h3>
 * <p>技能槽上限 3 个（初始 0，每 5 级 +1）。
 * 被动技能在 {@link #createBulletCopy(float, float)} 中应用于子弹。
 * 主动技能由 GameEngine 通过 {@link Skill#activate(Plane, float)} 触发。</p>
 *
 * <h3>5. 成长系统</h3>
 * <p>等级提升通过 {@link #levelUp()} 实现基础属性成长（HP、子弹伤害、穿甲值各 +10%）。
 * 经验值通过击杀敌机获得，达标自动升级。</p>
 *
 * <h2>碰撞检测</h2>
 * <p>使用 AABB 矩形碰撞体（基于 x, y, width, height），
 * 支持飞机间碰撞（{@link #collidesWith(Plane)}）和飞机-子弹碰撞（{@link #collidesWith(Bullet)}）。</p>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see PlayerPlane  玩家飞机
 * @see EnemyPlane   敌机（含 Boss）
 * @see Bullet       子弹抽象基类
 * @see Buff         增益效果
 * @see Skill        技能系统
 */
public abstract class Plane {
    // ── 位置与尺寸 ──
    /** 飞机 X 坐标（中心点） */
    protected float x;

    /** 飞机 Y 坐标（中心点） */
    protected float y;

    /** 碰撞宽度（px） */
    protected int width = 48;

    /** 碰撞高度（px） */
    protected int height = 48;

    // ── 生命与护甲 ──
    /** 护甲值 — 用于破甲计算，降低受到的伤害和暴击率 */
    protected int armor;

    /** 当前生命值 */
    protected int hp;

    /** 最大生命值（升级和升级卡可改变） */
    protected int maxHp;

    /** 护盾虚拟血量 — 优先于 HP 扣除，到期/耗完后消失 */
    protected int shieldHp = 0;

    // ── 子弹系统 ──
    /** 子弹模板 — 射击时以此为模板创建副本（保留攻击力/穿甲/暴击属性） */
    protected Bullet bullet;

    /** 攻击速度（秒/发），即射击冷却时间 */
    protected float atkSpeed;

    /** 上次射击的游戏时间（秒），用于冷却判定 */
    protected float lastShootTime = 0;

    // ── 增益与技能 ──
    /** 当前生效的 Buff 列表（治疗/护盾/火力） */
    protected List<Buff> buffs = new ArrayList<>();

    /** 已装备的技能列表（数量 ≤ maxSkillSlots ≤ 3） */
    protected List<Skill> skills = new ArrayList<>();

    /** 最大技能槽数量（初始 0，每 5 级 +1，上限 3） */
    protected int maxSkillSlots = 0;

    /** 是否处于无敌状态（S1 雷霆瞬闪激活时） */
    protected boolean invincible = false;

    /** 过载超频是否激活（S4） */
    protected boolean overclockActive = false;

    /** 暗能爆发是否激活（S8） */
    protected boolean darkBurstActive = false;

    /** 火力 Buff 等级（0=无, 1~3，每级弹速/射速 +100%） */
    protected int fireLevel = 0;

    // ── 成长系统 ──
    /** 当前经验值 */
    protected int xp = 0;

    /** 当前等级（初始 1，每升一级基础属性 +10%） */
    protected int level = 1;

    // ── 移动 ──
    /** 基础移动速度（px/帧），可被 Buff/技能/升级卡修正 */
    protected float moveSpeed;

    // ── 外观 ──
    /** 当前显示的图片（由 GamePanel 根据飞机类型选择） */
    protected Image image;

    /** 序列帧数组（动画用，预留） */
    protected Image[] frames;

    /** 当前播放帧索引（动画用，预留） */
    protected int currentFrame = 0;

    /** 帧切换间隔（毫秒，动画用，预留） */
    protected long frameInterval = 100;

    /** 上次帧切换时间戳（动画用，预留） */
    protected long lastFrameTime = 0;

    /** 是否存活（isDead() 检查此标志） */
    protected boolean alive = true;

    // ═══════════════════════════════════════════════
    // 构造方法
    // ═══════════════════════════════════════════════

    /** 默认构造方法，初始化射击计时器为 0 */
    public Plane() {
        lastShootTime = 0;
    }

    // ═══════════════════════════════════════════════
    // 抽象方法（子类必须实现）
    // ═══════════════════════════════════════════════

    /**
     * 移动飞机位置。
     * @param dx X 轴位移增量（px）
     * @param dy Y 轴位移增量（px）
     */
    public abstract void move(float dx, float dy);

    /** 发射子弹（子类覆写以实现具体射击逻辑） */
    public abstract void shoot();

    /**
     * 受到伤害处理。
     * @param damage 原始伤害值（未计护甲/护盾修正，由 applyDamage 处理）
     */
    public abstract void takeDamage(int damage);

    /**
     * 应用增益效果。
     * @param buff 要应用的 Buff 实例
     */
    public abstract void applyBuff(Buff buff);

    /** 移除已过期的增益效果 */
    public abstract void removeExpiredBuffs();

    /**
     * 判断飞机是否已死亡。
     * @return true 表示已死亡
     */
    public abstract boolean isDead();

    // ═══════════════════════════════════════════════
    // 通用方法
    // ═══════════════════════════════════════════════

    /**
     * 判断是否可以射击（冷却完毕）。
     *
     * <p>检查 (当前时间 - 上次射击时间) ≥ 有效攻击速度。
     * 有效攻击速度受火力 Buff 和过载超频影响（见 {@link #getEffectiveAtkSpeed()}）。</p>
     *
     * @param gameTime 当前游戏时间（秒）
     * @return true 表示冷却完毕，可以射击
     */
    public boolean canShoot(float gameTime) {
        return (gameTime - lastShootTime) >= getEffectiveAtkSpeed();
    }

    /**
     * 获取当前的 <b>有效攻击速度</b>（秒/发），考虑了 Buff 和技能的修正。
     *
     * <p>修正规则：</p>
     * <ul>
     *   <li><b>火力 Buff (fireLevel > 0)：</b>速度 = atkSpeed / (1 + fireLevel)
     *       <br>Lv.1 → 速度翻倍（间隔减半），Lv.2 → 3×，Lv.3 → 4×</li>
     *   <li><b>过载超频 (overclockActive)：</b>速度 = speed × 0.5（间隔减半）</li>
     * </ul>
     *
     * @return 修正后的攻击间隔（秒/发），值越小射速越快
     */
    public float getEffectiveAtkSpeed() {
        float speed = atkSpeed;

        // 火力 Buff: 每级射速 +100%（间隔减半）
        if (fireLevel > 0) {
            speed = speed / (1 + fireLevel);
        }
        // 过载超频 (S4): 攻击间隔 -50%
        if (overclockActive) {
            speed = speed * 0.5f;
        }
        return speed;
    }

    /**
     * 执行射击 — 创建子弹副本并应用当前 Buff/技能修正。
     *
     * <p>修正流程：</p>
     * <ol>
     *   <li>检查射击冷却（{@link #canShoot(float)}）</li>
     *   <li>创建子弹模板副本（{@link #createBulletCopy(float, float)}）</li>
     *   <li>应用过载超频（S4）：攻击 +50%，弹速 +100%</li>
     *   <li>应用暗能爆发（S8）：攻击 +300%（×4）</li>
     * </ol>
     *
     * @param gameTime 当前游戏时间（秒）
     * @param bulletX  子弹生成 X 坐标
     * @param bulletY  子弹生成 Y 坐标
     * @return 创建的子弹副本；冷却中返回 null
     */
    public Bullet doShoot(float gameTime, float bulletX, float bulletY) {
        if (!canShoot(gameTime)) return null;   // 冷却中
        lastShootTime = gameTime;                // 更新射击时间

        Bullet b = createBulletCopy(bulletX, bulletY);

        // 过载超频 (S4): 攻击 +50%, 弹速 +100%
        if (overclockActive) {
            b.setAttack((int)(b.getAttack() * 1.5f));
            b.setSpeed(b.getSpeed() * 2.0f);
        }
        // 暗能爆发 (S8): 攻击 +300%（即 ×4）
        if (darkBurstActive) {
            b.setAttack((int)(b.getAttack() * 4.0f));
        }
        return b;
    }

    /**
     * 创建子弹的副本（根据当前子弹模板类型选择相应的子类构造方法）。
     *
     * <p>副本继承模板的攻击力、穿甲值、暴击率、速度、大小。
     * 注意：此方法在 Plane 基类中实现，GameEngine 有自己更复杂的实现
     * {@code createPlayerBulletCopy()} 用于处理导弹的 target acquisition。</p>
     *
     * @param bx 子弹生成 X 坐标
     * @param by 子弹生成 Y 坐标
     * @return 子弹副本（类型与模板相同）
     */
    protected Bullet createBulletCopy(float bx, float by) {
        Bullet copy;

        // 根据模板类型创建对应子类实例
        if (bullet instanceof NormalBullet) {
            NormalBullet nb = new NormalBullet(bx, by, this instanceof PlayerPlane);
            copy = nb;
        } else if (bullet instanceof ArmorBullet) {
            ArmorBullet ab = new ArmorBullet(bx, by, this instanceof PlayerPlane);
            copy = ab;
        } else if (bullet instanceof MissileBullet) {
            MissileBullet mb = new MissileBullet(bx, by, this instanceof PlayerPlane);
            copy = mb;
        } else {
            // 兜底：普通子弹
            copy = new NormalBullet(bx, by, this instanceof PlayerPlane);
        }

        // 复制模板的属性到副本
        copy.setArmorDepth(bullet.getArmorDepth());
        copy.setAttack(bullet.getAttack());
        copy.setCritRate(bullet.getCritRate());
        copy.setSpeed(bullet.getSpeed());
        copy.setBulletSize(bullet.getBulletSize());

        return copy;
    }

    /**
     * 受到伤害的通用处理（先扣护盾，再扣 HP）。
     *
     * <p>伤害结算顺序：</p>
     * <ol>
     *   <li>检查无敌状态 → 无视所有伤害</li>
     *   <li>先扣护盾 HP（shieldHp）</li>
     *   <li>护盾耗尽后扣基础 HP（hp）</li>
     *   <li>HP ≤ 0 时设 alive = false</li>
     * </ol>
     *
     * @param rawDamage 受到的原始伤害值
     */
    public void applyDamage(int rawDamage) {
        if (invincible) return;     // 无敌状态：完全免疫伤害

        int remaining = rawDamage;

        // 先扣护盾（蓝色虚拟血量）
        if (shieldHp > 0) {
            if (shieldHp >= remaining) {
                shieldHp -= remaining;  // 护盾足够吸收全部伤害
                remaining = 0;
            } else {
                remaining -= shieldHp;  // 护盾不够，剩余伤害穿透
                shieldHp = 0;
            }
        }

        // 护盾耗尽后扣 HP
        if (remaining > 0) {
            hp -= remaining;
            if (hp < 0) hp = 0;         // HP 不出现负数
        }

        // HP 归零 → 死亡
        if (hp <= 0) {
            alive = false;
        }
    }

    /**
     * 生命偷取（吸血）— 根据造成的伤害恢复 HP。
     *
     * <p>遍历所有技能，累加吸血比例（仅 S6 能量虹吸的 8%），
     * 回复量 = 造成伤害 × 总吸血比例，不超过最大 HP。</p>
     *
     * @param damageDealt 实际造成的伤害值
     */
    public void lifesteal(int damageDealt) {
        float totalLifesteal = 0;

        // 累加所有技能的吸血比例
        for (Skill s : skills) {
            totalLifesteal += s.getLifesteal();     // 非 S6 技能返回 0
        }

        if (totalLifesteal > 0) {
            int heal = (int)(damageDealt * totalLifesteal);
            hp = Math.min(maxHp, hp + heal);        // 不超过最大 HP
        }
    }

    /**
     * 添加技能到技能槽。
     *
     * <p>添加前检查：</p>
     * <ul>
     *   <li>技能槽是否已满（skills.size() < maxSkillSlots）</li>
     *   <li>被动技能添加后自动激活（setActive(true)）</li>
     * </ul>
     *
     * @param skill 要添加的技能实例
     * @return true 表示添加成功，false 表示技能槽已满
     */
    public boolean addSkill(Skill skill) {
        if (skills.size() < maxSkillSlots) {
            skills.add(skill);
            // 被动技能添加后立即激活
            if (skill.isPassive()) {
                skill.setActive(true);
            }
            return true;
        }
        return false;   // 技能槽已满
    }

    /**
     * 更新所有技能的过期状态（由 GameEngine 每帧调用）。
     *
     * <p>遍历所有技能，调用 {@link Skill#update(float, Plane)} 检查
     * 持续型主动技能（S1/S4/S8）是否过期需恢复状态。</p>
     *
     * @param gameTime 当前游戏时间（秒）
     */
    public void updateSkills(float gameTime) {
        for (Skill s : skills) {
            s.update(gameTime, this);
        }
    }

    /**
     * 检查并移除所有过期的 Buff。
     *
     * <p>遍历 buffs 列表，对每个 Buff 调用 {@link Buff#isExpired(float)}，
     * 过期则先调用 {@link Buff#remove(Plane)} 清理效果，再从列表中移除。</p>
     *
     * @param gameTime 当前游戏时间（秒）
     */
    public void checkExpiredBuffs(float gameTime) {
        Iterator<Buff> it = buffs.iterator();
        while (it.hasNext()) {
            Buff b = it.next();
            if (b.isExpired(gameTime)) {
                b.remove(this);     // 先清理效果（清护盾/降火力）
                it.remove();        // 再从列表中移除
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 碰撞检测
    // ═══════════════════════════════════════════════

    /**
     * 获取飞机的 AABB 碰撞矩形。
     * <p>以 (x, y) 为中心，width×height 的矩形。</p>
     *
     * @return 碰撞矩形（用于 intersects 检测）
     */
    public Rectangle getBounds() {
        return new Rectangle(
            (int)(x - width / 2), (int)(y - height / 2),
            width, height
        );
    }

    /**
     * 检测是否与另一架飞机发生碰撞（机身碰撞）。
     * @param other 另一架飞机
     * @return true 表示两架飞机的碰撞矩形相交
     */
    public boolean collidesWith(Plane other) {
        return getBounds().intersects(other.getBounds());
    }

    /**
     * 检测是否与子弹发生碰撞。
     * @param b 子弹实例
     * @return true 表示飞机的碰撞矩形与子弹碰撞矩形相交
     */
    public boolean collidesWith(Bullet b) {
        return getBounds().intersects(b.getBounds());
    }

    // ═══════════════════════════════════════════════
    // 经验值与升级
    // ═══════════════════════════════════════════════

    /**
     * 获得经验值。
     *
     * <p>累计经验，然后检查是否达到升级条件。
     * 注意：升级的实际执行（levelUp + 弹出升级卡）由 GameEngine 处理，
     * 这里仅累计 xp 和返回升级标志。</p>
     *
     * @param amount 获得的经验值
     * @return true 表示达到升级条件
     */
    public boolean gainXp(int amount) {
        xp += amount;
        return checkLevelUp();
    }

    /**
     * 检查是否达到升级条件。
     * @return true 表示当前经验值 ≥ 升级所需经验值
     */
    public boolean checkLevelUp() {
        int required = getXpForNextLevel();
        return xp >= required;
    }

    /**
     * 获取升级到下一级所需的 <b>累计经验值</b>。
     *
     * <p>经验表 = 原始累计经验表 + 每级额外增加 等级×300。
     * 累计额外 = 300 × level × (level + 1) / 2。</p>
     *
     * <h3>升级经验速查表</h3>
     * <table border="1">
     *   <tr><th>等级</th><th>升级所需 XP</th><th>累计 XP</th></tr>
     *   <tr><td>1 → 2</td><td>800</td><td>800</td></tr>
     *   <tr><td>2 → 3</td><td>1600</td><td>2400</td></tr>
     *   <tr><td>3 → 4</td><td>2700</td><td>5100</td></tr>
     *   <tr><td>...</td><td>...</td><td>...</td></tr>
     *   <tr><td>19 → 20</td><td>70700</td><td>368500</td></tr>
     * </table>
     *
     * @return 升级到下一级所需的累计经验值
     */
    public int getXpForNextLevel() {
        // 原始累计经验表（经验增量累计值）
        int[] baseTable = {0,
            500,    // 1→2: +500
            1500,   // 2→3: +1000  (累计: 500+1000)
            3300,   // 3→4: +1800
            5100,   // 4→5: +1800
            6900,   // 5→6: +1800
            8700,   // 6→7: +1800
            10500,  // 7→8: +1800
            13500,  // 8→9: +3000
            18500,  // 9→10: +5000
            25500,  // 10→11: +7000
            35500,  // 11→12: +10000
            48500,  // 12→13: +13000
            65500,  // 13→14: +17000
            87500,  // 14→15: +22000
            115500, // 15→16: +28000
            150500, // 16→17: +35000
            193500, // 17→18: +43000
            246500, // 18→19: +53000
            311500  // 19→20: +65000
        };

        int baseCumulative;
        if (level < baseTable.length) {
            baseCumulative = baseTable[level];
        } else {
            // 20 级以后每级递增 +80000（线性外推）
            baseCumulative = baseTable[baseTable.length - 1]
                           + (level - baseTable.length + 1) * 80000;
        }

        // 叠加：每升一级额外增加 等级×300
        // 累计额外 = 300 × level × (level + 1) / 2
        int extraCumulative = 300 * level * (level + 1) / 2;

        return baseCumulative + extraCumulative;
    }

    /**
     * 升级处理 — 基础属性成长 +10%（乘法叠加）。
     *
     * <p>升级效果：</p>
     * <ul>
     *   <li>等级 +1</li>
     *   <li>子弹伤害 +10%</li>
     *   <li>最大 HP +10%（升级满血）</li>
     *   <li>穿甲值 +10%</li>
     *   <li>护甲值 +10%（仅在 1~10 级）</li>
     *   <li>每 5 级获得 1 个技能槽（上限 3）</li>
     * </ul>
     *
     * <p>此基础成长在升级卡选择 <b>之前</b> 生效，与升级卡加成独立计算。</p>
     */
    public void levelUp() {
        level++;

        // 基础属性成长：子弹伤害/HP/穿甲值各 +10%（乘法）
        if (bullet != null) {
            bullet.setAttack((int)(bullet.getAttack() * 1.1f));
            bullet.setArmorDepth((int)(bullet.getArmorDepth() * 1.1f));
        }
        maxHp = (int)(maxHp * 1.1f);   // HP +10%
        hp = maxHp;                     // 升级满血

        // 护甲值仅在 1~10 级每级 +10%，11 级起不再成长
        if (level <= 10) {
            armor = (int)(armor * 1.1f);
        }

        // 每 5 级获得 1 个技能槽（上限 3）
        if (level % 5 == 0 && maxSkillSlots < 3) {
            maxSkillSlots++;
        }
    }

    // ═══════════════════════════════════════════════
    // Getter / Setter
    // ═══════════════════════════════════════════════

    // ── 位置与尺寸 ──
    public float getX() { return x; }
    public void setX(float x) { this.x = x; }
    public float getY() { return y; }
    public void setY(float y) { this.y = y; }
    public void setPosition(float x, float y) { this.x = x; this.y = y; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    // ── 生命与护甲 ──
    public int getArmor() { return armor; }
    public void setArmor(int armor) { this.armor = armor; }
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
    public int getMaxHp() { return maxHp; }
    /** 设置最大 HP（同时将当前 HP 设为最大值） */
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; this.hp = maxHp; }
    public int getShieldHp() { return shieldHp; }
    public void setShieldHp(int shieldHp) { this.shieldHp = shieldHp; }

    // ── 子弹系统 ──
    public Bullet getBullet() { return bullet; }
    public void setBullet(Bullet bullet) { this.bullet = bullet; }
    public float getAtkSpeed() { return atkSpeed; }
    public void setAtkSpeed(float atkSpeed) { this.atkSpeed = atkSpeed; }
    public float getLastShootTime() { return lastShootTime; }
    public void setLastShootTime(float t) { this.lastShootTime = t; }

    // ── Buff / 技能 ──
    public List<Buff> getBuffs() { return buffs; }
    public List<Skill> getSkills() { return skills; }
    public int getMaxSkillSlots() { return maxSkillSlots; }
    public void setMaxSkillSlots(int s) { this.maxSkillSlots = s; }

    // ── 状态标志 ──
    public boolean isInvincible() { return invincible; }
    public void setInvincible(boolean inv) { this.invincible = inv; }
    public boolean isOverclockActive() { return overclockActive; }
    public void setOverclockActive(boolean a) { this.overclockActive = a; }
    public boolean isDarkBurstActive() { return darkBurstActive; }
    public void setDarkBurstActive(boolean a) { this.darkBurstActive = a; }
    public int getFireLevel() { return fireLevel; }
    public void setFireLevel(int level) { this.fireLevel = level; }

    // ── 成长系统 ──
    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    // ── 移动 ──
    public float getMoveSpeed() { return moveSpeed; }
    public void setMoveSpeed(float s) { this.moveSpeed = s; }

    // ── 外观 ──
    public Image getImage() { return image; }
    public void setImage(Image img) { this.image = img; }

    // ── 状态 ──
    public boolean isAlive() { return alive; }
    public void setAlive(boolean a) { this.alive = a; }
}
