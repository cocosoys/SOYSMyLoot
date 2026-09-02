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
        /** HAS_ITEM 用：目标物品材质（可空） */
        private final Material itemMaterial;
        /** HAS_ITEM 用：目标物品耐久值（-1 表示任意） */
        private final int itemData;
        /** TIME 用：起始时间（当日分钟数），-1 表示未配置 */
        private final int startMinOfDay;
        /** TIME 用：结束时间（当日分钟数） */
        private final int endMinOfDay;
        /** TIME 用：允许的星期（1=周一 .. 7=周日），null/空表示每天 */
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

        public ConditionType getType() {
            return type;
        }

        public String getMonsterId() {
            return monsterId;
        }

        public double getAmount() {
            return amount;
        }

        public Material getItemMaterial() {
            return itemMaterial;
        }

        public int getItemData() {
            return itemData;
        }

        public int getStartMinOfDay() {
            return startMinOfDay;
        }

        public int getEndMinOfDay() {
            return endMinOfDay;
        }

        public List<Integer> getDays() {
            return days;
        }
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

        public Material getMaterial() {
            return material;
        }

        public int getAmount() {
            return amount;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }
    }

    private final String id;
    private final String name;
    private final String description;
    private final List<Condition> conditions;
    private final boolean repeatable;
    private final long cooldown;        // 冷却秒数
    private final List<ItemReward> items;
    private final List<String> commands;
    private final double money;
    private final double points;
    private final List<String> messages;

    // ---- V3 新增字段 ----
    /** 多阶段分组标识（如 铜/银/金），仅用于排序与展示，可空 */
    private final String stage;
    /** 前置奖励（需先领取的奖励 ID 列表），可空 */
    private final List<String> requires;
    /** 每日领取上限（0 = 不限） */
    private final int dailyLimit;
    /** 每周领取上限（0 = 不限） */
    private final int weeklyLimit;
    /** 部分领取：按进度比例发放奖励（按单位进度累进） */
    private final boolean partial;
    /** 部分领取的单位进度（默认 = 首个伤害/击杀条件的 amount） */
    private final double per;
    /** 部分领取是否消耗进度（默认 true） */
    private final boolean consume;
    /** 随机奖励池（奖励 ID 列表），可空 */
    private final List<String> pool;
    /** 随机奖励池每次抽取数量（默认 1） */
    private final int poolCount;

    public RewardEntry(String id, String name, String description, List<Condition> conditions,
                       boolean repeatable, long cooldown, List<ItemReward> items,
                       List<String> commands, double money, double points, List<String> messages,
                       String stage, List<String> requires, int dailyLimit, int weeklyLimit,
                       boolean partial, double per, boolean consume, List<String> pool, int poolCount) {
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
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public long getCooldown() {
        return cooldown;
    }

    public List<ItemReward> getItems() {
        return items;
    }

    public List<String> getCommands() {
        return commands;
    }

    public double getMoney() {
        return money;
    }

    public double getPoints() {
        return points;
    }

    public List<String> getMessages() {
        return messages;
    }

    public String getStage() {
        return stage;
    }

    public List<String> getRequires() {
        return requires;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public int getWeeklyLimit() {
        return weeklyLimit;
    }

    public boolean isPartial() {
        return partial;
    }

    public double getPer() {
        return per;
    }

    public boolean isConsume() {
        return consume;
    }

    public List<String> getPool() {
        return pool;
    }

    public int getPoolCount() {
        return poolCount;
    }
}
