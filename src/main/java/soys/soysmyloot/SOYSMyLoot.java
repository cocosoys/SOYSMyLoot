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
import soys.soysmyloot.hook.LinkTeamHook;
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

        configManager = new ConfigManager(this);
        configManager.loadAll();

        storageManager = new StorageManager(this);
        try {
            storageManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "初始化存储后端失败，插件已禁用", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        messageManager = new MessageManager(this);
        dataManager = new DataManager(this, storageManager);

        scopeResolver = new ScopeResolver(configManager);
        rewardManager = new RewardManager(this, messageManager, dataManager, scopeResolver);
        leaderboardManager = new LeaderboardManager(this, storageManager, configManager);
        seasonManager = new SeasonManager(this, storageManager, dataManager, configManager, messageManager);
        onlineTimeTracker = new OnlineTimeTracker(this, dataManager);
        onlineTimeTracker.init();

        setupEconomy();
        setupPlayerPoints();

        getServer().getPluginManager().registerEvents(
                new EntityListener(this, configManager, dataManager, scopeResolver), this);
        getServer().getPluginManager().registerEvents(onlineTimeTracker, this);

        LootCommand cmd = new LootCommand(this, configManager, messageManager, dataManager, rewardManager,
                storageManager, scopeResolver, leaderboardManager, seasonManager);
        if (getCommand("myloot") != null) {
            getCommand("myloot").setExecutor(cmd);
            getCommand("myloot").setTabCompleter(cmd);
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
    }

    @Override
    public void onDisable() {
        if (leaderboardManager != null) {
            leaderboardManager.stop();
        }
        if (seasonManager != null) {
            seasonManager.stop();
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
}
