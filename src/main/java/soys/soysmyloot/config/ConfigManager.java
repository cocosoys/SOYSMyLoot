package soys.soysmyloot.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import soys.soysmyloot.model.MonsterEntry;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.storage.StorageType;
import soys.soysmyloot.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置管理器：负责加载、重载全部配置文件（config / monsters / rewards / messages），
 * 并暴露存储后端的查询方法（供 storage 包使用）。
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    // ---- config.yml 原始对象（保留供 storage 后端读取 storage.backends.*）----
    private YamlConfiguration rawConfig;

    // ---- config.yml ----
    private boolean debug;
    private int autoSave;
    private boolean trackProjectile;
    private boolean trackIndirect;

    // ---- 进度归属 / 世界隔离 ----
    private String scopeId;
    private boolean worldIsolation;

    // ---- 排行榜 ----
    private int leaderboardLimit;
    private boolean leaderboardByDamage;
    private int leaderboardRefreshSeconds;

    // ---- 赛季重置 ----
    private boolean seasonAutoReset;
    private int seasonPeriodHours;
    private boolean seasonKeepClaims;

    // ---- monsters.yml / rewards.yml ----
    private final Map<String, MonsterEntry> monsters = new HashMap<>();
    private final Map<String, RewardEntry> rewards = new HashMap<>();

    // ---- messages.yml ----
    private YamlConfiguration messages;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 加载全部配置 */
    public void loadAll() {
        loadSettings();
        loadMonsters();
        loadRewards();
        loadMessages();
    }

    // ============ 内部加载 ============

    private YamlConfiguration loadResource(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void loadSettings() {
        this.rawConfig = loadResource("config.yml");
        debug = rawConfig.getBoolean("debug", false);
        autoSave = rawConfig.getInt("auto-save", 300);

        ConfigurationSection ts = rawConfig.getConfigurationSection("tracking");
        trackProjectile = ts != null ? ts.getBoolean("projectile", true) : true;
        trackIndirect = ts != null ? ts.getBoolean("indirect", false) : false;
        scopeId = ts != null ? ts.getString("scope", "player").toLowerCase() : "player";
        worldIsolation = ts != null ? ts.getBoolean("world-isolation", true) : true;

        ConfigurationSection lb = rawConfig.getConfigurationSection("leaderboard");
        leaderboardLimit = lb != null ? lb.getInt("limit", 10) : 10;
        String lbOrder = lb != null ? lb.getString("order", "damage").toLowerCase() : "damage";
        leaderboardByDamage = !"kills".equals(lbOrder);
        leaderboardRefreshSeconds = lb != null ? lb.getInt("refresh-seconds", 60) : 60;

        ConfigurationSection ss = rawConfig.getConfigurationSection("season");
        seasonAutoReset = ss != null ? ss.getBoolean("auto-reset", false) : false;
        seasonPeriodHours = ss != null ? ss.getInt("period-hours", 168) : 168;
        seasonKeepClaims = ss != null ? ss.getBoolean("keep-claims", true) : true;
    }

    private void loadMonsters() {
        monsters.clear();
        YamlConfiguration cfg = loadResource("monsters.yml");
        ConfigurationSection section = cfg.getConfigurationSection("monsters");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection m = section.getConfigurationSection(id);
            if (m == null) {
                continue;
            }
            String typeStr = m.getString("type", "ZOMBIE").toUpperCase();
            EntityType type;
            try {
                type = EntityType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("怪物 '" + id + "' 的 type 无效: " + typeStr + "，已跳过");
                continue;
            }
            String name = m.getString("name", "");
            String display = m.getString("display-name", id);
            monsters.put(id, new MonsterEntry(id, type, name, display));
        }
    }

    private void loadRewards() {
        rewards.clear();
        YamlConfiguration cfg = loadResource("rewards.yml");
        ConfigurationSection section = cfg.getConfigurationSection("rewards");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null) {
                continue;
            }
            String name = r.getString("name", id);
            String desc = r.getString("description", "");
            boolean repeatable = r.getBoolean("repeatable", false);
            long cooldown = r.getLong("cooldown", 0);

            // ---- 条件 ----
            List<RewardEntry.Condition> conditions = new ArrayList<>();
            ConfigurationSection condSection = r.getConfigurationSection("conditions");
            if (condSection != null) {
                for (String ck : condSection.getKeys(false)) {
                    ConfigurationSection c = condSection.getConfigurationSection(ck);
                    if (c == null) {
                        continue;
                    }
                    try {
                        RewardEntry.ConditionType ct = RewardEntry.ConditionType.valueOf(
                                c.getString("type", "DAMAGE").toUpperCase());
                        String monsterId = c.getString("monster", "");
                        double amount = c.getDouble("amount", 0);

                        Material itemMaterial = null;
                        int itemData = -1;
                        int startMin = -1;
                        int endMin = -1;
                        List<Integer> days = null;

                        if (ct == RewardEntry.ConditionType.HAS_ITEM) {
                            String matStr = c.getString("material", "").toUpperCase();
                            try {
                                itemMaterial = Material.valueOf(matStr);
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("奖励 '" + id + "' 的条件 '" + ck
                                        + "' material 无效: " + matStr + "，已跳过");
                                continue;
                            }
                            itemData = c.getInt("data", -1);
                        } else if (ct == RewardEntry.ConditionType.TIME) {
                            startMin = parseTime(c.getString("start"));
                            endMin = parseTime(c.getString("end"));
                            if (startMin < 0 || endMin < 0) {
                                plugin.getLogger().warning("奖励 '" + id + "' 的条件 '" + ck
                                        + "' 缺少有效的 start/end 时间，已跳过");
                                continue;
                            }
                            List<Integer> dayList = c.getIntegerList("days");
                            days = dayList.isEmpty() ? null : dayList;
                        }

                        conditions.add(new RewardEntry.Condition(ct, monsterId, amount,
                                itemMaterial, itemData, startMin, endMin, days));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("奖励 '" + id + "' 的条件 '" + ck + "' type 无效，已跳过");
                    }
                }
            }

            // ---- 物品 ----
            List<RewardEntry.ItemReward> items = new ArrayList<>();
            ConfigurationSection itemsSection = r.getConfigurationSection("rewards.items");
            if (itemsSection != null) {
                for (String ik : itemsSection.getKeys(false)) {
                    ConfigurationSection i = itemsSection.getConfigurationSection(ik);
                    if (i == null) {
                        continue;
                    }
                    String matStr = i.getString("material", "STONE").toUpperCase();
                    org.bukkit.Material material;
                    try {
                        material = org.bukkit.Material.valueOf(matStr);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("奖励 '" + id + "' 的物品 '" + ik + "' material 无效: " + matStr + "，已跳过");
                        continue;
                    }
                    int amount = i.getInt("amount", 1);
                    String itemName = i.getString("name", "");
                    List<String> lore = i.getStringList("lore");
                    items.add(new RewardEntry.ItemReward(material, amount, itemName, lore));
                }
            }

            // ---- 指令 / 金钱 / 点券 / 消息 ----
            List<String> commands = r.getStringList("rewards.commands");
            double money = r.getDouble("rewards.money", 0);
            double points = r.getDouble("rewards.points", 0);
            List<String> messagesList = r.getStringList("rewards.messages");

            // ---- V3 新增字段 ----
            String stage = r.getString("stage", null);
            List<String> requires = r.getStringList("requires");
            int dailyLimit = r.getInt("daily-limit", 0);
            int weeklyLimit = r.getInt("weekly-limit", 0);
            boolean partial = r.getBoolean("partial", false);
            double per = r.getDouble("per", 0);
            boolean consume = r.getBoolean("consume", true);
            List<String> pool = r.getStringList("random-pool");
            int poolCount = r.getInt("random-count", 1);
            if (poolCount < 1) {
                poolCount = 1;
            }

            rewards.put(id, new RewardEntry(id, name, desc, conditions, repeatable, cooldown,
                    items, commands, money, points, messagesList,
                    stage, requires, dailyLimit, weeklyLimit, partial, per, consume, pool, poolCount));
        }
    }

    /** 解析 "HH:mm" 或纯小时为当日分钟数；失败返回 -1 */
    private int parseTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return -1;
        }
        String v = value.trim();
        int hour;
        int minute = 0;
        int idx = v.indexOf(':');
        try {
            if (idx >= 0) {
                hour = Integer.parseInt(v.substring(0, idx));
                minute = Integer.parseInt(v.substring(idx + 1));
            } else {
                hour = Integer.parseInt(v);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return -1;
        }
        return hour * 60 + minute;
    }

    private void loadMessages() {
        messages = loadResource("messages.yml");
    }

    // ============ 通用 ============

    public boolean isDebug() {
        return debug;
    }

    public int getAutoSave() {
        return autoSave;
    }

    public boolean isTrackProjectile() {
        return trackProjectile;
    }

    public boolean isTrackIndirect() {
        return trackIndirect;
    }

    // ============ 进度归属 / 世界隔离 ============

    /** 进度归属模式 id：player | team（team 模式下队伍共享伤害） */
    public String getScopeId() {
        return scopeId;
    }

    /** 是否开启世界隔离（true=按世界分别统计进度，false=跨世界汇总） */
    public boolean isWorldIsolation() {
        return worldIsolation;
    }

    // ============ 排行榜 ============

    public int getLeaderboardLimit() {
        return leaderboardLimit;
    }

    /** 默认排序是否按伤害（false=按击杀） */
    public boolean isLeaderboardByDamage() {
        return leaderboardByDamage;
    }

    public int getLeaderboardRefreshSeconds() {
        return leaderboardRefreshSeconds;
    }

    // ============ 赛季重置 ============

    public boolean isSeasonAutoReset() {
        return seasonAutoReset;
    }

    /** 赛季周期（小时） */
    public int getSeasonPeriodHours() {
        return seasonPeriodHours;
    }

    /** 赛季重置是否保留领取记录 */
    public boolean isSeasonKeepClaims() {
        return seasonKeepClaims;
    }

    // ============ 存储后端 ============

    public boolean isBackendEnabled(String backendId) {
        return rawConfig.getBoolean("storage.backends." + backendId + ".enabled", false);
    }

    public ConfigurationSection getBackendSection(String backendId) {
        return rawConfig.getConfigurationSection("storage.backends." + backendId);
    }

    public boolean isMirrorEnabled() {
        return rawConfig.getBoolean("storage.mirror.enabled", true);
    }

    public boolean isMirrorAsync() {
        return rawConfig.getBoolean("storage.mirror.async", true);
    }

    public boolean isSyncOnStartup() {
        return rawConfig.getBoolean("storage.mirror.sync-on-startup", false);
    }

    /**
     * 按配置启用的后端计算主存储 id（优先级：mysql &gt; sqlite &gt; yaml）。
     * 用于 /myloot info 等信息展示。
     */
    public String getPrimaryBackendId() {
        String best = "sqlite";
        int bestPriority = -1;
        for (StorageType type : StorageType.values()) {
            if (isBackendEnabled(type.getId()) && type.getPriority() > bestPriority) {
                bestPriority = type.getPriority();
                best = type.getId();
            }
        }
        return best;
    }

    // ============ 怪物 / 奖励 ============

    public Map<String, MonsterEntry> getMonsters() {
        return monsters;
    }

    public MonsterEntry getMonster(String id) {
        return monsters.get(id);
    }

    /** 根据实体匹配并返回对应的怪物配置（无则 null） */
    public MonsterEntry getMonster(LivingEntity entity) {
        for (MonsterEntry m : monsters.values()) {
            if (m.matches(entity)) {
                return m;
            }
        }
        return null;
    }

    public Map<String, RewardEntry> getRewards() {
        return rewards;
    }

    public RewardEntry getReward(String id) {
        return rewards.get(id);
    }

    /** 获取消息并替换占位符（含 {prefix}） */
    public String msg(String key, Map<String, String> placeholders) {
        if (messages == null) {
            return key;
        }
        String raw = messages.getString(key, key);
        Map<String, String> all = new HashMap<>();
        all.put("prefix", messages.getString("prefix", ""));
        if (placeholders != null) {
            all.putAll(placeholders);
        }
        return Text.format(raw, all);
    }

    public String msg(String key) {
        return msg(key, null);
    }
}
