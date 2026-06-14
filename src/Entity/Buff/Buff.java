package Entity.Buff;

import Entity.Plane.Plane;

/**
 * <h1>Buff — 游戏增益效果</h1>
 *
 * <p>Buff 定义了游戏中可获取的临时增益效果框架，具有持续时间和过期机制。
 * 所有 Buff 对象通过静态工厂方法创建，根据 {@link BuffType} 枚举决定
 * 在 {@link #apply(Plane)} 和 {@link #remove(Plane)} 中的具体行为。</p>
 *
 * <h2>Buff 类型</h2>
 * <table border="1">
 *   <tr><th>枚举值</th><th>效果</th><th>持续时间</th><th>掉落概率</th><th>Buff球颜色</th></tr>
 *   <tr><td>{@code HEAL}</td><td>立即回复 800 HP</td><td>即时生效（0s）</td><td>40%</td><td>🔴 红色</td></tr>
 *   <tr><td>{@code SHIELD}</td><td>获得 300 护盾虚拟血量（先扣盾后扣血）</td><td>10 秒</td><td>35%</td><td>🔵 蓝色</td></tr>
 *   <tr><td>{@code FIRE}</td><td>子弹速度 +100%，射速 +100%，最高 Lv.3</td><td>8 秒</td><td>25%</td><td>🟡 黄色</td></tr>
 * </table>
 *
 * <h2>生命週期</h2>
 * <pre>
 * 创建 (工厂方法) → apply() 生效 → update/checkExpired → isExpired() → remove() 移除
 * </pre>
 *
 * <p>{@code applied} 和 {@code removed} 标志位确保 apply/remove 不会重复执行。</p>
 *
 * <h2>与技能（Skill）的区别</h2>
 * <ul>
 *   <li><b>Buff</b> — 临时增益，持续时间短（8~10s），敌机击杀后概率掉落</li>
 *   <li><b>Skill</b> — 主动/被动技能，CD 较长（30~50s），通过升级卡获取</li>
 * </ul>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see BuffDrop   Buff 掉落球（场景中的可拾取实体）
 * @see Plane       飞机抽象基类（Buff 作用的载体）
 * @see Entity.Skill.Skill  技能系统（对应的长效机制）
 */
public class Buff {

    /**
     * Buff 类型枚举 — 决定 apply/remove 时的具体效果。
     */
    public enum BuffType {
        /** 治疗 Buff: 立即回复 800 HP（不超最大 HP） */
        HEAL,
        /** 护盾 Buff: 获得 300 虚拟护甲血量，持续 10s，到期消失 */
        SHIELD,
        /** 火力 Buff: 子弹速度与发射速度 +100%，持续 8s，最高 Lv.3 */
        FIRE
    }

    /** Buff 显示名称（如"治疗"、"护盾"、"火力强化 Lv.2"） */
    protected String name;

    /** 持续时间（秒），HEAL 类型为 0（即时生效，不过期） */
    protected float duration;

    /** 效果开始时的游戏时间（秒），用于判断过期 */
    protected float startTime;

    /** Buff 类型（决定 apply/remove 行为） */
    protected BuffType type;

    /** 是否已应用（防止 apply 重复执行） */
    protected boolean applied = false;

    /** 是否已移除（防止 remove 重复执行） */
    protected boolean removed = false;

    // ── 护盾 Buff 专属属性 ──
    /** 护盾当前剩余血量 */
    protected int shieldHp = 0;
    /** 护盾最大血量（固定 300） */
    protected int shieldHpMax = 0;

    // ── 火力 Buff 专属属性 ──
    /** 火力等级（1~3），每级提供子弹速度 & 射速 +100% */
    protected int fireLevel = 1;

    /**
     * 构造一个 Buff 对象（不建议直接使用，请使用静态工厂方法）。
     *
     * @param name      Buff 显示名称
     * @param type      Buff 类型枚举
     * @param duration  持续时间（秒），0 表示即时生效
     * @param startTime 游戏时间（秒）
     */
    public Buff(String name, BuffType type, float duration, float startTime) {
        this.name = name;
        this.type = type;
        this.duration = duration;
        this.startTime = startTime;
    }

    // ═══════════════════════════════════════════════
    // 静态工厂方法
    // ═══════════════════════════════════════════════

    /**
     * 创建治疗 Buff — 拾取后立即回复 800 HP。
     * <p>治疗量：800，不会超过最大 HP。</p>
     *
     * @param gameTime 当前游戏时间（秒）
     * @return 治疗 Buff 实例
     */
    public static Buff createHealBuff(float gameTime) {
        return new Buff("治疗", BuffType.HEAL, 0, gameTime);
    }

    /**
     * 创建护盾 Buff — 拾取后获得 300 护盾虚拟血量。
     * <p>护盾持续 10 秒，期间受到的伤害优先从护盾扣除，
     * 到期或护盾耗尽后消失。</p>
     *
     * @param gameTime 当前游戏时间（秒）
     * @return 护盾 Buff 实例
     */
    public static Buff createShieldBuff(float gameTime) {
        Buff b = new Buff("护盾", BuffType.SHIELD, 10.0f, gameTime);
        b.shieldHp = 300;       // 护盾最大血量
        b.shieldHpMax = 300;
        return b;
    }

    /**
     * 创建火力 Buff — 拾取后子弹速度和射速 +100%。
     * <p>火力最高 3 级，每级效果叠加（Lv.1: 弹速/射速 ×2, Lv.2: ×3, Lv.3: ×4）。</p>
     *
     * @param gameTime 当前游戏时间（秒）
     * @param level    火力等级（1~3）
     * @return 火力 Buff 实例
     */
    public static Buff createFireBuff(float gameTime, int level) {
        Buff b = new Buff("火力强化 Lv." + level, BuffType.FIRE, 8.0f, gameTime);
        b.fireLevel = level;
        return b;
    }

    // ═══════════════════════════════════════════════
    // 生命周期方法
    // ═══════════════════════════════════════════════

    /**
     * 对指定飞机应用 Buff 效果。
     *
     * <p>不同 Buff 类型的行为：</p>
     * <ul>
     *   <li><b>HEAL:</b> 增加 800 HP（不超过最大 HP）</li>
     *   <li><b>SHIELD:</b> 设置飞机的护盾 HP 为 300</li>
     *   <li><b>FIRE:</b> 设置飞机的火力等级（最高 3 级）</li>
     * </ul>
     *
     * <p>使用 {@code applied} 标志位确保只应用一次。</p>
     *
     * @param plane 要应用效果的目标飞机
     */
    public void apply(Plane plane) {
        if (applied) return;    // 防重复应用
        applied = true;

        switch (type) {
            case HEAL:
                // 回复 800 HP，不超过最大 HP
                int newHp = Math.min(plane.getHp() + 800, plane.getMaxHp());
                plane.setHp(newHp);
                break;
            case SHIELD:
                // 设置护盾血量
                plane.setShieldHp(shieldHp);
                break;
            case FIRE:
                // 设置火力等级（不超过 3）
                plane.setFireLevel(Math.min(fireLevel, 3));
                break;
        }
    }

    /**
     * 从指定飞机移除 Buff 效果（过期或正常结束时调用）。
     *
     * <p>不同 Buff 类型的移除行为：</p>
     * <ul>
     *   <li><b>HEAL:</b> 无需移除（即时生效无持久影响）</li>
     *   <li><b>SHIELD:</b> 清空护盾 HP 为 0</li>
     *   <li><b>FIRE:</b> 重置火力等级为 0</li>
     * </ul>
     *
     * <p>使用 {@code removed} 标志位确保只移除一次。</p>
     *
     * @param plane 要移除效果的目标飞机
     */
    public void remove(Plane plane) {
        if (removed) return;    // 防重复移除
        removed = true;

        switch (type) {
            case SHIELD:
                plane.setShieldHp(0);   // 清空护盾
                break;
            case FIRE:
                plane.setFireLevel(0);  // 重置火力等级
                break;
            // HEAL 无需额外移除操作
        }
    }

    /**
     * 判断 Buff 是否已过期。
     *
     * <p>过期条件：{@code duration > 0} 且当前时间 - 开始时间 ≥ 持续时间。
     * 治疗 Buff（duration=0）永不过期（即时生效后由系统清理）。</p>
     *
     * @param currentTime 当前游戏时间（秒）
     * @return true 表示已过期，应被移除
     */
    public boolean isExpired(float currentTime) {
        // duration <= 0 表示即时生效（如治疗），永不过期
        return duration > 0 && (currentTime - startTime) >= duration;
    }

    // ═══════════════════════════════════════════════
    // Getter / Setter
    // ═══════════════════════════════════════════════

    /** @return Buff 显示名称 */
    public String getName() { return name; }

    /** @return Buff 类型枚举值 */
    public BuffType getType() { return type; }

    /** @return 持续时间（秒），0 表示即时生效 */
    public float getDuration() { return duration; }

    /** @return Buff 开始时的游戏时间（秒） */
    public float getStartTime() { return startTime; }

    /** @return 护盾当前剩余血量（仅 SHIELD 类型有效） */
    public int getShieldHp() { return shieldHp; }

    /** 设置护盾血量（伤害扣除时使用） */
    public void setShieldHp(int hp) { this.shieldHp = hp; }

    /** @return 火力等级（仅 FIRE 类型有效，1~3） */
    public int getFireLevel() { return fireLevel; }
}
