package soys.soysmyloot.model;

import org.bukkit.Material;

import java.util.List;

/**
 * 奖励配置项：对应 rewards.yml 中的单个奖励规则。
 */
public class RewardEntry {

    /** 条件类型 */
    public enum ConditionType {
        DAMAGE,  // 对该怪物累计造成的伤害
        KILLS    // 对该怪物的击杀数
    }

    /** 单个领取条件 */
    public static class Condition {
        private final ConditionType type;
        private final String monsterId;
        private final double amount;

        public Condition(ConditionType type, String monsterId, double amount) {
            this.type = type;
            this.monsterId = monsterId;
            this.amount = amount;
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

    public RewardEntry(String id, String name, String description, List<Condition> conditions,
                       boolean repeatable, long cooldown, List<ItemReward> items,
                       List<String> commands, double money, double points, List<String> messages) {
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
}
