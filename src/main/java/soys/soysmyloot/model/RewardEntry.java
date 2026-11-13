package soys.soysmyloot.model;

import org.bukkit.Material;

import java.util.List;

/**
 * 奖励配置项：对应 rewards.yml 中的单个奖励规则。
 */
public class RewardEntry {

    /** 条件类型 */
    public enum ConditionType {
        DAMAGE,   // 对该怪物累计造成的伤害
        KILLS,    // 对该怪物的击杀数
        LEVEL,    // 玩家等级 >= amount
        MONEY,    // 玩家余额 >= amount（需 Vault 及经济插件）
        ONLINE,   // 玩家累计在线时长 >= amount（分钟，基于 PLAY_ONE_MINUTE 统计）
        HAS_ITEM, // 玩家持有指定物品数量 >= amount
        TIME      // 当前服务器时间处于指定时间段内
    }

    /** 单个领取条件 */
    public static class Condition {
        private final ConditionType type;
        private final String monsterId;
        private final double amount;
        private final Material itemMaterial;
        private final int itemData;
        private final int startMinOfDay;
        private final int endMinOfDay;
        private final List<Integer> days;

        public Condition(ConditionType type, String monsterId, double amount,
                         Material itemMaterial, int itemData,
                         int startMinOfDay, int endMinOfDay, List<Integer> days) {
            this.type = type;
            this.monsterId = monsterId;
            this.amount = amount;
            this.itemMaterial = itemMaterial;
            this.itemData = itemData;
            this.startMinOfDay = startMinOfDay;
            this.endMinOfDay = endMinOfDay;
            this.days = days;
        }

        public ConditionType getType() { return type; }
        public String getMonsterId() { return monsterId; }
        public double getAmount() { return amount; }
        public Material getItemMaterial() { return itemMaterial; }
        public int getItemData() { return itemData; }
        public int getStartMinOfDay() { return startMinOfDay; }
        public int getEndMinOfDay() { return endMinOfDay; }
        public List<Integer> getDays() { return days; }
    }

    /** 物品奖励的原始数据（在领取时再构建 ItemStack，避免持有旧 ItemMeta） */
    public static class ItemReward {
        private final Material material;
        private final int amount;
        private final String name;
        private final List<String> lore;

        public ItemReward(Material material, int amount, String name, List<String> lore) {
            this.material = material;
            this.amount = amount;
            this.name = name;
            this.lore = lore;
        }

        public Material getMaterial() { return material; }
        public int getAmount() { return amount; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
    }

    /**
     * 领取成功反馈（音效 + 粒子），V7 起改为按礼包配置。
     *
     * <p>rewards.yml 中未配置 {@code feedback} 子节时该对象为 {@code null}，
     * 表示「不播放任何反馈」；字段含义与旧 config.yml 的全局 feedback 节一一对应。</p>
     */
    public static class Feedback {
        private final boolean enabled;
        private final String sound;
        private final float volume;
        private final float pitch;
        private final String particle;
        private final int count;
        private final double offset;

        public Feedback(boolean enabled, String sound, float volume, float pitch,
                        String particle, int count, double offset) {
            this.enabled = enabled;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.particle = particle;
            this.count = count;
            this.offset = offset;
        }

        public boolean isEnabled() { return enabled; }
        public String getSound() { return sound; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
        public String getParticle() { return particle; }
        public int getCount() { return count; }
        public double getOffset() { return offset; }
    }

    private final String id;
    private final String name;
    private final String description;
    private final List<Condition> conditions;
    private final boolean repeatable;
    private final long cooldown;
    private final List<ItemReward> items;
    private final List<String> commands;
    private final double money;
    private final double points;
    private final List<String> messages;

    // ---- V3 新增字段 ----
    private final String stage;
    private final List<String> requires;
    private final int dailyLimit;
    private final int weeklyLimit;
    private final boolean partial;
    private final double per;
    private final boolean consume;
    private final List<String> pool;
    private final int poolCount;

    /** ERP 启用开关（YAML 缺省 = true），false 时不在 GUI/列表中展示 */
    private final boolean enabled;

    // ---- V7 新增字段 ----
    /** 本礼包专属的领取反馈；null = 未配置 = 不播放 */
    private final Feedback feedback;

    public RewardEntry(String id, String name, String description, List<Condition> conditions,
                       boolean repeatable, long cooldown, List<ItemReward> items,
                       List<String> commands, double money, double points, List<String> messages,
                       String stage, List<String> requires, int dailyLimit, int weeklyLimit,
                       boolean partial, double per, boolean consume, List<String> pool, int poolCount) {
        this(id, name, description, conditions, repeatable, cooldown, items, commands, money, points,
                messages, stage, requires, dailyLimit, weeklyLimit, partial, per, consume, pool, poolCount, true);
    }

    public RewardEntry(String id, String name, String description, List<Condition> conditions,
                       boolean repeatable, long cooldown, List<ItemReward> items,
                       List<String> commands, double money, double points, List<String> messages,
                       String stage, List<String> requires, int dailyLimit, int weeklyLimit,
                       boolean partial, double per, boolean consume, List<String> pool, int poolCount,
                       boolean enabled) {
        this(id, name, description, conditions, repeatable, cooldown, items, commands, money, points,
                messages, stage, requires, dailyLimit, weeklyLimit, partial, per, consume, pool, poolCount,
                enabled, null);
    }

    public RewardEntry(String id, String name, String description, List<Condition> conditions,
                       boolean repeatable, long cooldown, List<ItemReward> items,
                       List<String> commands, double money, double points, List<String> messages,
                       String stage, List<String> requires, int dailyLimit, int weeklyLimit,
                       boolean partial, double per, boolean consume, List<String> pool, int poolCount,
                       boolean enabled, Feedback feedback) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.conditions = conditions;
        this.repeatable = repeatable;
        this.cooldown = cooldown;
        this.items = items;
        this.commands = commands;
        this.money = money;
        this.points = points;
        this.messages = messages;
        this.stage = stage;
        this.requires = requires;
        this.dailyLimit = dailyLimit;
        this.weeklyLimit = weeklyLimit;
        this.partial = partial;
        this.per = per;
        this.consume = consume;
        this.pool = pool;
        this.poolCount = poolCount;
        this.enabled = enabled;
        this.feedback = feedback;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<Condition> getConditions() { return conditions; }
    public boolean isRepeatable() { return repeatable; }
    public long getCooldown() { return cooldown; }
    public List<ItemReward> getItems() { return items; }
    public List<String> getCommands() { return commands; }
    public double getMoney() { return money; }
    public double getPoints() { return points; }
    public List<String> getMessages() { return messages; }
    public String getStage() { return stage; }
    public List<String> getRequires() { return requires; }
    public int getDailyLimit() { return dailyLimit; }
    public int getWeeklyLimit() { return weeklyLimit; }
    public boolean isPartial() { return partial; }
    public double getPer() { return per; }
    public boolean isConsume() { return consume; }
    public List<String> getPool() { return pool; }
    public int getPoolCount() { return poolCount; }
    public boolean isEnabled() { return enabled; }

    public Feedback getFeedback() { return feedback; }
}
