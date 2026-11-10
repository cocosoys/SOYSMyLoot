package soys.soysmyloot;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import soys.soysmyloot.command.LootCommand;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.config.MessageManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.gui.GuiListener;
import soys.soysmyloot.gui.RewardGui;
import soys.soysmyloot.hook.LinkTeamHook;
import soys.soysmyloot.hook.MythicMobsHook;
import soys.soysmyloot.hook.WorldGuardHook;
import soys.soysmyloot.leaderboard.LeaderboardManager;
import soys.soysmyloot.listener.EntityListener;
import soys.soysmyloot.placeholder.PAPIExpansion;
import soys.soysmyloot.reward.RewardManager;
import soys.soysmyloot.season.SeasonManager;
import soys.soysmyloot.storage.StorageManager;

import java.util.logging.Level;

/**
 * SOYSMyLoot —— 根据玩家对配置怪物造成的伤害/击杀发放奖励。
 * 强制依赖：PlaceholderAPI
 * 软依赖：Vault / PlayerPoints（金钱/点券奖励）、SOYSLinkTeam（队伍共享伤害）
 */
public final class SOYSMyLoot extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private StorageManager storageManager;
    private DataManager dataManager;
    private RewardManager rewardManager;
    private ScopeResolver scopeResolver;
    private LeaderboardManager leaderboardManager;
    private SeasonManager seasonManager;
    private OnlineTimeTracker onlineTimeTracker;
    private ProgressHud progressHud;
    private RewardGui rewardGui;

    private Economy economy;
    private PlayerPointsAPI playerPoints;

    @Override
    public void onEnable() {
        // 强制依赖检查（plugin.yml 已声明 depend，此处为防御性兜底）
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("未找到 PlaceholderAPI 依赖，SOYSMyLoot 无法启用！");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化队伍软依赖桥接（无 SOYSLinkTeam 时自动降级为不可用）
        LinkTeamHook.init();
        if (LinkTeamHook.isAvailable()) {
            getLogger().info("检测到 SOYSLinkTeam，队伍共享伤害模式可用（tracking.scope: team）。");
        }

        // 初始化 MythicMobs 软依赖桥接（无 MythicMobs 时自动降级）
        MythicMobsHook.init();
        if (MythicMobsHook.isAvailable()) {
            getLogger().info("检测到 MythicMobs，可按内部名匹配自定义怪物（monsters.yml 的 mythic 字段）。");
        }

        // 初始化 WorldGuard 软依赖桥接（无 WorldGuard 时区域限制自动失效）
        WorldGuardHook.init();
        if (WorldGuardHook.isAvailable()) {
            getLogger().info("检测到 WorldGuard，区域追踪限制可用（tracking.regions）。");
        }

        configManager = new ConfigManager(this);
        configManager.loadAll();

        // 配置了区域限制但未安装 WorldGuard 时给出明确提示（限制不生效）
        if (!WorldGuardHook.isAvailable()
                && !"all".equals(configManager.getRegionMode())
                && !configManager.getRegionList().isEmpty()) {
            getLogger().warning("tracking.regions 配置了区域限制但未安装 WorldGuard，该限制不生效。");
        }

        storageManager = new StorageManager(this);
        try {
            storageManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "初始化存储后端失败，插件已禁用", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        messageManager = new MessageManager(this, configManager);
        dataManager = new DataManager(this, storageManager);

        scopeResolver = new ScopeResolver(configManager);
        rewardManager = new RewardManager(this, messageManager, dataManager, scopeResolver);
        leaderboardManager = new LeaderboardManager(this, storageManager, configManager);
        seasonManager = new SeasonManager(this, storageManager, dataManager, configManager, messageManager);
        onlineTimeTracker = new OnlineTimeTracker(this, dataManager, scopeResolver);
        onlineTimeTracker.init();
        progressHud = new ProgressHud(this, configManager, dataManager, scopeResolver);
        progressHud.init();
        rewardGui = new RewardGui(this, configManager, messageManager, dataManager, rewardManager, scopeResolver);

        setupEconomy();
        setupPlayerPoints();

        EntityListener entityListener = new EntityListener(this, configManager, dataManager, scopeResolver);
        getServer().getPluginManager().registerEvents(entityListener, this);
        getServer().getPluginManager().registerEvents(onlineTimeTracker, this);
        getServer().getPluginManager().registerEvents(new GuiListener(rewardGui), this);

        // 已在线玩家（如插件热重载）异步预加载，避免首次伤害事件同步读库
        Bukkit.getOnlinePlayers().forEach(p -> dataManager.preload(p.getUniqueId()));
        // 周期性清理防作弊限流窗口，兜底队伍模式下的 key 残留
        Bukkit.getScheduler().runTaskTimer(this, entityListener::cleanupRateWindows, 1200L, 1200L);

        LootCommand cmd = new LootCommand(this, configManager, messageManager, dataManager, rewardManager,
                storageManager, scopeResolver, leaderboardManager, seasonManager, rewardGui);
        if (getCommand("myloot") != null) {
            getCommand("myloot").setExecutor(cmd);
            getCommand("myloot").setTabCompleter(cmd);
        }

        // 月卡访问链接指令（依赖 ERP 模块颁发的 token，ERP 未启用时指令内部会提示）
        if (getCommand("monthly") != null) {
            getCommand("monthly").setExecutor(new soys.soysmyloot.command.MonthlyCommand(
                    this, configManager, messageManager));
        }

        if (new PAPIExpansion(this, configManager, dataManager, rewardManager, scopeResolver, leaderboardManager).register()) {
            getLogger().info("PlaceholderAPI 扩展已注册。");
        }

        // 自动保存（脏数据经存储层异步队列落盘）
        int save = configManager.getAutoSave();
        if (save > 0) {
            Bukkit.getScheduler().runTaskTimer(this, dataManager::saveAll,
                    save * 20L, save * 20L);
        }

        // 在线时长每分钟结算一次
        Bukkit.getScheduler().runTaskTimer(this, onlineTimeTracker::flushTick, 1200L, 1200L);

        // 排行榜定时刷新
        leaderboardManager.start();
        // 赛季自动重置
        seasonManager.start();

        getLogger().info("SOYSMyLoot 已启用：加载 " + configManager.getMonsters().size()
                + " 个怪物，" + configManager.getRewards().size() + " 个奖励。");

        // 网关层扩展（独立于 MCERP）：dist 页面托管 + 玩家侧 token 端点
        if (Bukkit.getPluginManager().getPlugin("SOYSHTTPOverMC") != null) {
            try {
                soys.soysmyloot.spring.SOYSMyLootExpansion web = new soys.soysmyloot.spring.SOYSMyLootExpansion();
                if (!web.register()) {
                    getLogger().warning("SOYSMyLoot Web 扩展注册失败（identifier 冲突？）");
                } else {
                    getLogger().info("SOYSMyLoot Web 扩展已挂载（页面 /web/plugins/SOYSMyLoot"
                            + " + 玩家端点 /api/plugins/SOYSMyLoot/player/*）。");
                }
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "SOYSMyLoot Web 扩展挂载异常", t);
            }

            // ERP 模块（仅菜单 + /erp/* 管理端点）；MCERP 未安装时不注册，页面与玩家端点不受影响
            if (Bukkit.getPluginManager().getPlugin("MCERP") != null) {
                try {
                    soys.soysmyloot.spring.erp.SOYSMyLootErpExpansion erp =
                            new soys.soysmyloot.spring.erp.SOYSMyLootErpExpansion();
                    if (!erp.register()) {
                        getLogger().warning("SOYSMyLoot ERP 模块注册失败（identifier 冲突？）");
                    } else {
                        getLogger().info("SOYSMyLoot ERP 模块已挂载到 MCERP 中控后台（标识 SOYSMyLoot-ERP）。");
                    }
                } catch (Throwable t) {
                    getLogger().log(Level.WARNING, "SOYSMyLoot ERP 模块挂载异常（可忽略）", t);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (leaderboardManager != null) {
            leaderboardManager.stop();
        }
        if (seasonManager != null) {
            seasonManager.stop();
        }
        if (progressHud != null) {
            progressHud.stop();
        }
        if (onlineTimeTracker != null) {
            onlineTimeTracker.flushAll();
        }
        if (dataManager != null) {
            dataManager.saveAll();
        }
        if (storageManager != null) {
            storageManager.shutdown();
        }
        getLogger().info("SOYSMyLoot 已禁用。");
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            } else {
                getLogger().warning("Vault 已安装但未找到经济提供者，金钱奖励将被跳过。");
            }
        } else {
            getLogger().info("未安装 Vault，金钱奖励将被跳过。");
        }
    }

    private void setupPlayerPoints() {
        org.bukkit.plugin.Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (pp instanceof PlayerPoints) {
            try {
                playerPoints = ((PlayerPoints) pp).getAPI();
            } catch (Throwable t) {
                getLogger().warning("PlayerPoints 已安装但获取 API 失败，点券奖励将被跳过。");
            }
        } else {
            getLogger().info("未安装 PlayerPoints，点券奖励将被跳过。");
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public PlayerPointsAPI getPlayerPoints() {
        return playerPoints;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public ScopeResolver getScopeResolver() {
        return scopeResolver;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public SeasonManager getSeasonManager() {
        return seasonManager;
    }

    public ProgressHud getProgressHud() {
        return progressHud;
    }

    public RewardGui getRewardGui() {
        return rewardGui;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
