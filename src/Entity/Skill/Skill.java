package Entity.Skill;

import Entity.Plane.Plane;
import Entity.Plane.PlayerPlane;
import Entity.Bullet.Bullet;
import java.util.List;

/**
 * <h1>技能系统 — 主动/被动技能框架</h1>
 *
 * <p>Skill 定义了游戏中全部 9 种技能的通用框架，包括冷却机制（CD）、
 * 激活/更新生命周期、以及被动效果对子弹的修正。</p>
 *
 * <p>技能通过 {@code SkillType} 枚举区分类型，具体行为在
 * {@link #activate(Plane, float)} 中按类型分发。</p>
 *
 * <h2>技能列表</h2>
 * <table border="1">
 *   <tr><th>编号</th><th>名称</th><th>类型</th><th>效果</th><th>CD</th></tr>
 *   <tr><td>S1</td><td>雷霆瞬闪</td><td>主动</td><td>无敌闪避弹幕，持续 10s</td><td>30s</td></tr>
 *   <tr><td>S2</td><td>电磁护盾</td><td>主动</td><td>生成 1000 HP 防御屏障</td><td>30s</td></tr>
 *   <tr><td>S3</td><td>聚变爆破</td><td>主动</td><td>全屏清屏 2000 伤害</td><td>30s</td></tr>
 *   <tr><td>S4</td><td>过载超频</td><td>主动</td><td>攻击+50% 速度+10% 射速+50% 弹速+100%，10s</td><td>30s</td></tr>
 *   <tr><td>S5</td><td>雷霆裁决</td><td>主动</td><td>真实伤害 1500（无视护甲）</td><td>30s</td></tr>
 *   <tr><td>S6</td><td>能量虹吸</td><td>被动</td><td>永久 8% 吸血（造成伤害的 8% 回复 HP）</td><td>—</td></tr>
 *   <tr><td>S7</td><td>星陨冲击</td><td>主动</td><td>陨石砸血量最高敌机 5000 伤害</td><td>30s</td></tr>
 *   <tr><td>S8</td><td>暗能爆发</td><td>主动</td><td>5s 内攻击+300% 移速-50%</td><td>50s</td></tr>
 *   <tr><td>S9</td><td>裁决轨迹</td><td>被动</td><td>贯穿+暴击率+20%+伤害+20%+弹速-6%</td><td>—</td></tr>
 * </table>
 *
 * <h2>主动 vs 被动</h2>
 * <ul>
 *   <li><b>主动技能</b> (S1-S5, S7, S8)：有 CD，玩家按键触发，
 *       持续时间后自动结束（或一次性生效）</li>
 *   <li><b>被动技能</b> (S6)：无 CD，始终生效，
 * </ul>
 *
 * <h2>技能冷却机制</h2>
 * <p>冷却时间从 {@link #resetCooldown(float)} （即技能激活时刻）开始计算。
 * {@link #canUse(float)} 检查 (当前时间 - 上次使用时间) ≥ CD。
 * 被动技能始终 canUse=true。</p>
 *
 * @author 第7组（王家磊、周骏、张书豪、帅宇昕）
 * @version 1.2.0
 * @since 2026-03-01
 *
 * @see Plane  技能作用的载体
 * @see Entity.Buff.Buff  Buff 增益效果（临时增益，与技能互补）
 */
public class Skill {

    /**
     * 技能类型枚举 — 决定 activate/update 时的具体行为。
     */
    public enum SkillType {
        /** S1: 雷霆瞬闪 — 无敌闪避 10s, CD 30s */
        THUNDER_DASH,
        /** S2: 电磁护盾 — 屏障血量 1000, CD 30s */
        EM_SHIELD,
        /** S3: 聚变爆破 — 全屏清屏冲击波 2000 伤害（穿甲值20）, CD 30s */
        FUSION_BLAST,
        /** S4: 过载超频 — 攻击+50% 速度+10% 射速+50% 弹速+100%, 持续10s, CD 30s */
        OVERCLOCK,
        /** S5: 雷霆裁决 — 大范围真实伤害 1500（无视护甲）, CD 30s */
        THUNDER_JUDGE,
        /** S6: 能量虹吸 — 被动技能，永久 8% 吸血 */
        ENERGY_SIPHON,
        /** S7: 星陨冲击 — 召唤陨石砸血量最高敌机 5000 伤害（单体）, CD 30s */
        STAR_IMPACT,
        /** S8: 暗能爆发 — 5s 内攻击+300% 移速-50%, CD 50s */
        DARK_BURST,

    }

    // ── 基本属性 ──
    /** 技能显示名称（如"雷霆瞬闪"） */
    protected String name;

    /** 技能类型枚举值 */
    protected SkillType type;

    /** 冷却时间（秒），被动技能为 0 */
    protected float cooldown;

    /** 上次使用时的游戏时间（秒），初始 -999 确保首次可用 */
    protected float lastUsedTime = -999;

    /** 是否为被动技能（始终生效，无 CD） */
    protected boolean isPassive;

    /** 当前是否激活（主动技能生效期间为 true） */
    protected boolean isActive = false;

    /** 激活开始时间（用于持续时间计算） */
    protected float activeStartTime;

    /** 激活持续时间（秒），0 = 一次性技能（如清屏/裁决） */
    protected float activeDuration;

    // ── 被动效果数值 ──
    /** 被动伤害加成比例（如 0.20 = +20%） */
    protected float passiveDamageBonus = 0;

    /** 被动暴击率加成（加法，如 0.20 = +20%） */
    protected float passiveCritBonus = 0;

    /** 被动弹速修正（如 -0.06 = -6%） */
    protected float passiveSpeedMod = 0;

    /** 被动生命偷取比例（如 0.08 = 8%） */
    protected float passiveLifesteal = 0;

    /** 被动子弹贯穿（命中敌机后不消失，继续飞行） */
    protected boolean passivePiercing = false;

    /**
     * 构造一个技能。
     *
     * <p>构造时根据 {@code type} 自动设置被动效果数值和 activeDuration。
     * 推荐通过静态工厂方法（createXxx）创建，而不是直接调用此构造方法。</p>
     *
     * @param name      技能名称
     * @param type      技能类型枚举
     * @param cooldown  冷却时间（秒），被动技能传 0
     * @param isPassive 是否为被动技能
     */
    public Skill(String name, SkillType type, float cooldown, boolean isPassive) {
        this.name = name;
        this.type = type;
        this.cooldown = cooldown;
        this.isPassive = isPassive;

        // 根据类型设置被动效果数值和持续时间
        switch (type) {
            case ENERGY_SIPHON:
                this.passiveLifesteal = 0.08f;      // S6: 8% 吸血
                break;
            // 以下主动技能设置 duration
            case THUNDER_DASH:
                this.activeDuration = 10.0f;        // 无敌 10s
                break;
            case EM_SHIELD:
                this.activeDuration = 0;             // 一次性护盾
                break;
            case FUSION_BLAST:
                this.activeDuration = 0;             // 一次性清屏
                break;
            case OVERCLOCK:
                this.activeDuration = 10.0f;        // 增幅 10s
                break;
            case THUNDER_JUDGE:
                this.activeDuration = 0;             // 一次性伤害
                break;
            case STAR_IMPACT:
                this.activeDuration = 0;             // 一次性陨石
                break;
            case DARK_BURST:
                this.activeDuration = 5.0f;         // 爆发 5s
                break;
        }
    }

    // ═══════════════════════════════════════════════
    // 工厂方法 — 创建全部 8 种技能
    // ═══════════════════════════════════════════════

    /** 创建 S1 雷霆瞬闪：无敌闪避 10s，CD 30s */
    public static Skill createThunderDash() {
        return new Skill("雷霆瞬闪", SkillType.THUNDER_DASH, 30.0f, false);
    }

    /** 创建 S2 电磁护盾：屏障血量 1000，CD 30s */
    public static Skill createEMShield() {
        return new Skill("电磁护盾", SkillType.EM_SHIELD, 30.0f, false);
    }

    /** 创建 S3 聚变爆破：全屏清屏 2000 伤害，CD 30s */
    public static Skill createFusionBlast() {
        return new Skill("聚变爆破", SkillType.FUSION_BLAST, 30.0f, false);
    }

    /** 创建 S4 过载超频：全属性暴涨 10s，CD 30s */
    public static Skill createOverclock() {
        return new Skill("过载超频", SkillType.OVERCLOCK, 30.0f, false);
    }

    /** 创建 S5 雷霆裁决：真实伤害 1500，CD 30s */
    public static Skill createThunderJudge() {
        return new Skill("雷霆裁决", SkillType.THUNDER_JUDGE, 30.0f, false);
    }

    /** 创建 S6 能量虹吸：被动 8% 吸血 */
    public static Skill createEnergySiphon() {
        return new Skill("能量虹吸", SkillType.ENERGY_SIPHON, 0, true);
    }

    /** 创建 S7 星陨冲击：陨石砸最高血量敌机 5000 伤害，CD 30s */
    public static Skill createStarImpact() {
        return new Skill("星陨冲击", SkillType.STAR_IMPACT, 30.0f, false);
    }

    /** 创建 S8 暗能爆发：5s 攻击+300% 移速-50%，CD 50s */
    public static Skill createDarkBurst() {
        return new Skill("暗能爆发", SkillType.DARK_BURST, 50.0f, false);
    }



    // ═══════════════════════════════════════════════
    // 冷却判断
    // ═══════════════════════════════════════════════

    /**
     * 判断技能是否冷却完毕、可使用。
     * <p>被动技能始终返回 true。</p>
     *
     * @param currentTime 当前游戏时间（秒）
     * @return true 表示可以激活
     */
    public boolean canUse(float currentTime) {
        if (isPassive) return true;  // 被动技能无 CD
        return (currentTime - lastUsedTime) >= cooldown;
    }

    /**
     * 获取剩余冷却时间（秒）。
     * <p>被动技能始终返回 0。</p>
     *
     * @param currentTime 当前游戏时间（秒）
     * @return 剩余 CD 秒数（0 表示就绪）
     */
    public float getCooldownRemaining(float currentTime) {
        if (isPassive) return 0;
        float elapsed = currentTime - lastUsedTime;
        return Math.max(0, cooldown - elapsed);
    }

    /**
     * 重置冷却计时（技能激活时调用）。
     * <p>将 lastUsedTime 设置为当前游戏时间。</p>
     *
     * @param currentTime 当前游戏时间（秒）
     */
    public void resetCooldown(float currentTime) {
        this.lastUsedTime = currentTime;
    }

    // ═══════════════════════════════════════════════
    // 激活技能
    // ═══════════════════════════════════════════════

    /**
     * 激活技能，对 owner 生效。
     *
     * <p>实现流程：</p>
     * <ol>
     *   <li>检查 CD（{@link #canUse(float)}）</li>
     *   <li>根据 {@link SkillType} 执行具体效果</li>
     *   <li>重置冷却计时（{@link #resetCooldown(float)}）</li>
     *   <li>对于持续性技能，设置 isActive=true 和 activeStartTime</li>
     * </ol>
     *
     * <p><b>返回值含义：</b></p>
     * <ul>
     *   <li>1 — 技能成功激活（持续型/护盾型）</li>
     *   <li>2000 — FUSION_BLAST 的伤害值</li>
     *   <li>1500 — THUNDER_JUDGE 的伤害值</li>
     *   <li>5000 — STAR_IMPACT 的伤害值</li>
     *   <li>0 — CD 中，激活失败</li>
     * </ul>
     *
     * @param owner    技能拥有者（飞机实例）
     * @param gameTime 当前游戏时间（秒）
     * @return 技能效果数值（含义因类型而异），0 表示失败
     */
    public int activate(Plane owner, float gameTime) {
        if (!canUse(gameTime)) return 0;    // CD 中

        switch (type) {
            case THUNDER_DASH:
                // S1: 无敌闪避 10s
                owner.setInvincible(true);
                this.activeStartTime = gameTime;
                this.isActive = true;
                resetCooldown(gameTime);
                return 1;

            case EM_SHIELD:
                // S2: 生成屏障 1000 HP（与已有护盾叠加）
                owner.setShieldHp(owner.getShieldHp() + 1000);
                resetCooldown(gameTime);
                return 1;

            case FUSION_BLAST:
                // S3: 全屏清屏 2000 伤害（穿甲值20，由 GameEngine 执行伤害结算）
                resetCooldown(gameTime);
                return 2000;

            case OVERCLOCK:
                // S4: 过载超频 — 攻击+50% 速度+10% 射速+50% 弹速+100%，持续10s
                owner.setOverclockActive(true);
                this.activeStartTime = gameTime;
                this.isActive = true;
                resetCooldown(gameTime);
                return 1;

            case THUNDER_JUDGE:
                // S5: 真实伤害 1500（无视护甲，由 GameEngine 执行伤害结算）
                resetCooldown(gameTime);
                return 1500;

            case STAR_IMPACT:
                // S7: 陨石砸血量最高敌机 5000（由 GameEngine 寻找目标并执行伤害）
                resetCooldown(gameTime);
                return 5000;

            case DARK_BURST:
                // S8: 暗能爆发 — 5s 攻击+300% 移速-50%
                owner.setDarkBurstActive(true);
                this.activeStartTime = gameTime;
                this.isActive = true;
                resetCooldown(gameTime);
                return 1;
        }
        return 0;
    }

    /**
     * 更新主动技能的状态（检查持续时间是否过期）。
     *
     * <p>由 GameEngine 每帧调用。对于持续性技能（THUNDER_DASH/OVERCLOCK/DARK_BURST），
     * 当持续时间结束时自动移除对应效果（无敌/过载/暗能标志位）。</p>
     *
     * @param gameTime 当前游戏时间（秒）
     * @param owner    技能拥有者（用于恢复状态标志位）
     */
    public void update(float gameTime, Plane owner) {
        if (!isActive) return;                      // 非激活状态，跳过
        float elapsed = gameTime - activeStartTime;

        if (elapsed >= activeDuration) {            // 持续时间结束
            isActive = false;
            switch (type) {
                case THUNDER_DASH:
                    owner.setInvincible(false);     // 无敌结束
                    break;
                case OVERCLOCK:
                    owner.setOverclockActive(false);// 过载结束
                    break;
                case DARK_BURST:
                    owner.setDarkBurstActive(false);// 暗能结束
                    break;
                // 一次性技能（EM_SHIELD/FUSION_BLAST 等）不需要恢复
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 被动效果应用于子弹
    // ═══════════════════════════════════════════════

    /**
     * 将被动技能效果应用于子弹（每发子弹创建时调用）。
     *
     * <p>仅在技能为被动且已激活时生效。当前仅 S9（裁决轨迹）有此效果：</p>
     * <ul>
     *   <li>暴击率 +20%（加法：critRate + 0.20）</li>
     *   <li>伤害 +20%（乘法：attack × 1.20）</li>
     *   <li>弹速 -6%（乘法：speed × 0.94）</li>
     * </ul>
     *
     * @param bullet 要应用被动效果的子弹
     * @return true 表示子弹应具有贯穿效果（命中后不消失）
     */


    /**
     * 获取生命偷取比例（仅 S6 能量虹吸有效）。
     *
     * @return 生命偷取比例（如 0.08 = 8%，非吸血技能返回 0）
     */
    public float getLifesteal() {
        return passiveLifesteal;
    }

    // ═══════════════════════════════════════════════
    // Getter / Setter
    // ═══════════════════════════════════════════════

    /** @return 技能名称 */
    public String getName() { return name; }
    /** @return 技能类型枚举 */
    public SkillType getType() { return type; }
    /** @return 是否为被动技能 */
    public boolean isPassive() { return isPassive; }
    /** @return 冷却时间（秒） */
    public float getCooldown() { return cooldown; }
    /** @return 当前是否激活（持续型技能生效中） */
    public boolean isActive() { return isActive; }
    /** 设置激活状态 */
    public void setActive(boolean active) { this.isActive = active; }
    /** @return 激活持续时间（秒） */
    public float getActiveDuration() { return activeDuration; }
    /** @return 激活开始时间（游戏秒数） */
    public float getActiveStartTime() { return activeStartTime; }
    /** 设置激活开始时间 */
    public void setActiveStartTime(float t) { this.activeStartTime = t; }
}
