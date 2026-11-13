package soys.soysmyloot.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import soys.soysmyloot.hook.MythicMobsHook;
import soys.soysmyloot.model.MonsterEntry;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.storage.StorageType;
import soys.soysmyloot.util.Text;
import soys.soysmyloot.util.YamlLineIndex;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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

    // ---- 区域限制（WorldGuard） ----
    private String regionMode = "all";
    private List<String> regionList = new ArrayList<>();

    // ---- 防作弊（伤害刷取检测） ----
    private boolean antiCheatEnabled;
    private double antiCheatMaxDamagePerHit;
    private double antiCheatMaxDps;

    // ---- 进度 HUD（BossBar / ActionBar） ----
    private boolean hudEnabled;
    private String hudMode;
    private int hudUpdateTicks;

    // ---- 排行榜 ----
    private int leaderboardLimit;
    private boolean leaderboardByDamage;
    private int leaderboardRefreshSeconds;

    // ---- 赛季重置 ----
    private boolean seasonAutoReset;
    private int seasonPeriodHours;
    private boolean seasonKeepClaims;

    // ---- 多语言 ----
    private String locale = "zh_cn";

    // ---- ERP 后台基地址（/monthly 指令拼月卡链接用） ----
    private String erpBaseUrl = "";

    // ---- monsters.yml / rewards.yml ----
    private final Map<String, MonsterEntry> monsters = new HashMap<>();
    private final Map<String, RewardEntry> rewards = new HashMap<>();

    // ---- 配置校验错误收集 ----
    private final List<String> configErrors = new ArrayList<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 加载全部配置，并在结束时执行引用完整性校验与错误汇总输出 */
    public void loadAll() {
        configErrors.clear();
        loadSettings();
        loadMonsters();
        loadRewards();
        validateReferences();
        reportConfigErrors();
    }

    // ============ 内部加载 ============

    private YamlConfiguration loadResource(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public void loadSettings() {
        this.rawConfig = loadResource("config.yml");
        debug = rawConfig.getBoolean("debug", false);
        autoSave = rawConfig.getInt("auto-save", 300);

        ConfigurationSection ts = rawConfig.getConfigurationSection("tracking");
        trackProjectile = ts != null ? ts.getBoolean("projectile", true) : true;
        trackIndirect = ts != null ? ts.getBoolean("indirect", false) : false;
        scopeId = ts != null ? ts.getString("scope", "player").toLowerCase() : "player";
        worldIsolation = ts != null ? ts.getBoolean("world-isolation", true) : true;

        // ---- 区域限制（tracking.regions） ----
        ConfigurationSection rg = ts != null ? ts.getConfigurationSection("regions") : null;
        regionMode = rg != null ? rg.getString("mode", "all").toLowerCase() : "all";
        if (!"all".equals(regionMode) && !"whitelist".equals(regionMode) && !"blacklist".equals(regionMode)) {
            configErrors.add("config.yml -> tracking.regions.mode 无效: " + regionMode + "（可用 all/whitelist/blacklist），已回退为 all");
            regionMode = "all";
        }
        regionList = rg != null ? rg.getStringList("list") : new ArrayList<>();
        if (regionList == null) {
            regionList = new ArrayList<>();
        }
        if (!"all".equals(regionMode) && regionList.isEmpty()) {
            configErrors.add("config.yml -> tracking.regions 配置了 " + regionMode
                    + " 但 list 为空，区域限制不生效（等同于 all）");
        }

        // ---- 防作弊 ----
        ConfigurationSection ac = rawConfig.getConfigurationSection("anti-cheat");
        antiCheatEnabled = ac != null ? ac.getBoolean("enabled", true) : true;
        antiCheatMaxDamagePerHit = ac != null ? ac.getDouble("max-damage-per-hit", 500) : 500;
        antiCheatMaxDps = ac != null ? ac.getDouble("max-dps", 3000) : 3000;
        if (antiCheatMaxDamagePerHit < 0) {
            antiCheatMaxDamagePerHit = 0;
        }
        if (antiCheatMaxDps < 0) {
            antiCheatMaxDps = 0;
        }

        // ---- 进度 HUD ----
        ConfigurationSection hd = rawConfig.getConfigurationSection("hud");
        hudEnabled = hd != null ? hd.getBoolean("enabled", false) : false;
        hudMode = hd != null ? hd.getString("mode", "actionbar").toLowerCase() : "actionbar";
        if (!"actionbar".equals(hudMode) && !"bossbar".equals(hudMode)) {
            configErrors.add("config.yml -> hud.mode 无效: " + hudMode + "（可用 actionbar/bossbar），已回退为 actionbar");
            hudMode = "actionbar";
        }
        hudUpdateTicks = hd != null ? hd.getInt("update-ticks", 20) : 20;
        if (hudUpdateTicks < 5) {
            hudUpdateTicks = 20;
        }

        ConfigurationSection lb = rawConfig.getConfigurationSection("leaderboard");
        leaderboardLimit = lb != null ? lb.getInt("limit", 10) : 10;
        String lbOrder = lb != null ? lb.getString("order", "damage").toLowerCase() : "damage";
        leaderboardByDamage = !"kills".equals(lbOrder);
        leaderboardRefreshSeconds = lb != null ? lb.getInt("refresh-seconds", 60) : 60;

        ConfigurationSection ss = rawConfig.getConfigurationSection("season");
        seasonAutoReset = ss != null ? ss.getBoolean("auto-reset", false) : false;
        seasonPeriodHours = ss != null ? ss.getInt("period-hours", 168) : 168;
        seasonKeepClaims = ss != null ? ss.getBoolean("keep-claims", true) : true;

        locale = rawConfig.getString("locale", "zh_cn");
        if (locale == null || locale.trim().isEmpty()) {
            locale = "zh_cn";
        }
        locale = locale.trim().toLowerCase();
    }

    public void loadMonsters() {
        monsters.clear();
        YamlConfiguration cfg = loadResource("monsters.yml");
        Map<String, Integer> lines = buildLineIndex("monsters.yml");
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
                configErrors.add(withLine(lines, "monsters." + id,
                        "monsters.yml -> 怪物 '" + id + "' 的 type 无效: " + typeStr + "，该怪物已被跳过"));
                continue;
            }
            String name = m.getString("name", "");
            String display = m.getString("display-name", id);
            String mythicId = m.getString("mythic", "");
            if (mythicId != null && !mythicId.isEmpty()) {
                if (!MythicMobsHook.isAvailable()) {
                    configErrors.add(withLine(lines, "monsters." + id,
                            "monsters.yml -> 怪物 '" + id + "' 配置了 mythic '" + mythicId
                                    + "' 但服务器未安装 MythicMobs，该怪物将永远无法匹配"));
                }
            }
            monsters.put(id, new MonsterEntry(id, type, name, display, mythicId == null ? "" : mythicId));
        }
    }

    public void loadRewards() {
        rewards.clear();
        YamlConfiguration cfg = loadResource("rewards.yml");
        Map<String, Integer> lines = buildLineIndex("rewards.yml");
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
                    String condPath = "rewards." + id + ".conditions." + ck;
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
                                configErrors.add(withLine(lines, condPath,
                                        "rewards.yml -> 奖励 '" + id + "' 的条件 '" + ck
                                                + "' material 无效: " + matStr + "，该条件已被跳过"));
                                continue;
                            }
                            itemData = c.getInt("data", -1);
                        } else if (ct == RewardEntry.ConditionType.TIME) {
                            startMin = parseTime(c.getString("start"));
                            endMin = parseTime(c.getString("end"));
                            if (startMin < 0 || endMin < 0) {
                                configErrors.add(withLine(lines, condPath,
                                        "rewards.yml -> 奖励 '" + id + "' 的条件 '" + ck
                                                + "' 缺少有效的 start/end 时间，该条件已被跳过"));
                                continue;
                            }
                            List<Integer> dayList = c.getIntegerList("days");
                            days = dayList.isEmpty() ? null : dayList;
                        } else if ((ct == RewardEntry.ConditionType.DAMAGE
                                || ct == RewardEntry.ConditionType.KILLS)
                                && amount <= 0) {
                            configErrors.add(withLine(lines, condPath,
                                    "rewards.yml -> 奖励 '" + id + "' 条件 '" + ck
                                            + "' 的 amount 必须 > 0，该条件已被跳过"));
                            continue;
                        }

                        conditions.add(new RewardEntry.Condition(ct, monsterId, amount,
                                itemMaterial, itemData, startMin, endMin, days));
                    } catch (IllegalArgumentException e) {
                        configErrors.add(withLine(lines, condPath,
                                "rewards.yml -> 奖励 '" + id + "' 的条件 '" + ck + "' type 无效，该条件已被跳过"));
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
                    String itemPath = "rewards." + id + ".rewards.items." + ik;
                    String matStr = i.getString("material", "STONE").toUpperCase();
                    org.bukkit.Material material;
                    try {
                        material = org.bukkit.Material.valueOf(matStr);
                    } catch (IllegalArgumentException e) {
                        configErrors.add(withLine(lines, itemPath,
                                "rewards.yml -> 奖励 '" + id + "' 的物品 '" + ik + "' material 无效: " + matStr + "，该物品已被跳过"));
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
            boolean enabled = r.getBoolean("enabled", true);
            if (partial && per <= 0) {
                // 未显式配置 per 时默认值=首个伤害/击杀条件 amount，此处仅提示
                configErrors.add(withLine(lines, "rewards." + id,
                        "rewards.yml -> 奖励 '" + id + "' 开启了 partial 但未设置有效的 per，将默认使用首个伤害/击杀条件 amount"));
            }

            // ---- V7 领取反馈：按礼包配置，未配置 feedback 子节 = 不播放 ----
            RewardEntry.Feedback feedback = null;
            ConfigurationSection fb = r.getConfigurationSection("feedback");
            if (fb != null) {
                feedback = new RewardEntry.Feedback(
                        fb.getBoolean("enabled", true),
                        fb.getString("claim-sound", ""),
                        (float) fb.getDouble("sound-volume", 1.0),
                        (float) fb.getDouble("sound-pitch", 1.0),
                        fb.getString("claim-particle", ""),
                        Math.max(0, fb.getInt("particle-count", 30)),
                        Math.max(0.0, fb.getDouble("particle-offset", 0.6)));
            }

            rewards.put(id, new RewardEntry(id, name, desc, conditions, repeatable, cooldown,
                    items, commands, money, points, messagesList,
                    stage, requires, dailyLimit, weeklyLimit, partial, per, consume, pool, poolCount,
                    enabled, feedback));
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

    // ============ 区域限制（WorldGuard） ============

    /** 区域限制模式：all（不限制）| whitelist（仅名单内区域）| blacklist（名单内区域除外） */
    public String getRegionMode() {
        return regionMode;
    }

    /** 区域 ID 名单（tracking.regions.list） */
    public List<String> getRegionList() {
        return regionList;
    }

    // ============ 防作弊 ============

    public boolean isAntiCheatEnabled() {
        return antiCheatEnabled;
    }

    /** 单次伤害计入上限（0 = 不限） */
    public double getAntiCheatMaxDamagePerHit() {
        return antiCheatMaxDamagePerHit;
    }

    /** 每秒累计伤害计入上限（0 = 不限） */
    public double getAntiCheatMaxDps() {
        return antiCheatMaxDps;
    }

    // ============ 进度 HUD ============

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    /** HUD 展示方式：actionbar | bossbar */
    public String getHudMode() {
        return hudMode;
    }

    /** HUD 刷新间隔（tick） */
    public int getHudUpdateTicks() {
        return hudUpdateTicks;
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

    /** 当前语言（Locale），默认 zh_CN */
    public String getLocale() {
        return locale;
    }

    /** ERP 后台月卡页面基地址（/monthly 指令拼链接用），未配置时返回空串 */
    public String getErpBaseUrl() {
        return erpBaseUrl;
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

    // ================================================================
    //  配置校验
    // ================================================================

    /**
     * 引用完整性校验：检查奖励配置中引用的怪物ID、前置奖励ID、随机池奖励ID是否存在。
     * 在 loadMonsters / loadRewards 之后调用，错误写入 configErrors。
     */
    private void validateReferences() {
        Map<String, Integer> lines = buildLineIndex("rewards.yml");
        for (Map.Entry<String, RewardEntry> entry : rewards.entrySet()) {
            String rewardId = entry.getKey();
            RewardEntry reward = entry.getValue();
            String rewardPath = "rewards." + rewardId;

            // 检查条件中引用的怪物ID
            for (RewardEntry.Condition c : reward.getConditions()) {
                if (c.getType() == RewardEntry.ConditionType.DAMAGE
                        || c.getType() == RewardEntry.ConditionType.KILLS) {
                    String mid = c.getMonsterId();
                    if (mid == null || mid.isEmpty()) {
                        configErrors.add(withLine(lines, rewardPath,
                                "rewards.yml -> 奖励 '" + rewardId + "' 的 " + c.getType()
                                        + " 条件缺少 monster 字段"));
                    } else if (!monsters.containsKey(mid)) {
                        configErrors.add(withLine(lines, rewardPath,
                                "rewards.yml -> 奖励 '" + rewardId + "' 引用了不存在的怪物 '" + mid + "'"));
                    }
                }
            }

            // 检查前置奖励 requires
            if (reward.getRequires() != null) {
                for (String reqId : reward.getRequires()) {
                    if (!rewards.containsKey(reqId)) {
                        configErrors.add(withLine(lines, rewardPath,
                                "rewards.yml -> 奖励 '" + rewardId + "' 的 requires 引用了不存在的奖励 '" + reqId + "'"));
                    }
                }
            }

            // 检查随机奖励池 random-pool
            if (reward.getPool() != null) {
                for (String poolId : reward.getPool()) {
                    if (!rewards.containsKey(poolId)) {
                        configErrors.add(withLine(lines, rewardPath,
                                "rewards.yml -> 奖励 '" + rewardId + "' 的 random-pool 引用了不存在的奖励 '" + poolId + "'"));
                    }
                }
            }
        }
    }

    /**
     * 将收集到的配置错误汇总输出到控制台。
     * 无错误时不输出；有错误时用 severe 级别逐条输出，便于服主定位问题。
     */
    private void reportConfigErrors() {
        if (configErrors.isEmpty()) {
            return;
        }
        plugin.getLogger().log(Level.SEVERE, "========== 配置校验发现 " + configErrors.size() + " 个问题 ==========");
        for (int i = 0; i < configErrors.size(); i++) {
            plugin.getLogger().log(Level.SEVERE, "[" + (i + 1) + "/" + configErrors.size() + "] " + configErrors.get(i));
        }
        plugin.getLogger().log(Level.SEVERE, "================================================================");
    }

    /** 获取本次加载收集到的配置错误列表（供外部查询，如 /myloot info 展示） */
    public List<String> getConfigErrors() {
        return new ArrayList<>(configErrors);
    }

    // ================================================================
    //  行号辅助
    // ================================================================

    /** 构建指定 YAML 文件的 路径->行号 索引（行号从 1 开始） */
    private Map<String, Integer> buildLineIndex(String fileName) {
        return YamlLineIndex.index(new File(plugin.getDataFolder(), fileName));
    }

    /** 为错误信息附加行号（行号未知时显示 ?） */
    private String withLine(Map<String, Integer> lines, String path, String message) {
        Integer ln = lines.get(path);
        return message + " (行 " + (ln == null ? "?" : ln) + ")";
    }
}
