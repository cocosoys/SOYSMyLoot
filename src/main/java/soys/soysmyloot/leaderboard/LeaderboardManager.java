package soys.soysmyloot.leaderboard;

import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.storage.LeaderboardRow;
import soys.soysmyloot.storage.StorageManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 排行榜管理器。
 * <p>
 * 按 owner（玩家或队伍 UUID）聚合 progress 表的累计伤害/击杀，整体排序后缓存。
 * 由于聚合需要扫描全表，刷新操作在异步线程执行，并定期（{@code leaderboard.refresh-seconds}）
 * 自动刷新；同时维护伤害榜与击杀榜两份快照，分别回填 rank 供占位符与指令使用。
 * </p>
 * <p>OWNER 解析见 {@link soys.soysmyloot.ScopeResolver}：team 模式下 owner 为队伍 UUID。</p>
 */
public final class LeaderboardManager {

    private final SOYSMyLoot plugin;
    private final StorageManager storageManager;
    private final ConfigManager config;

    private int refreshSeconds;
    private int limit;
    private boolean byDamage;

    private volatile List<LeaderboardRow> rowsDamage = new ArrayList<>();
    private volatile List<LeaderboardRow> rowsKills = new ArrayList<>();
    private final Map<UUID, Integer> rankDamage = new LinkedHashMap<>();
    private final Map<UUID, Integer> rankKills = new LinkedHashMap<>();

    private int taskId = -1;

    public LeaderboardManager(SOYSMyLoot plugin, StorageManager storageManager, ConfigManager config) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.config = config;
        reload();
    }

    /** 重新读取配置项 */
    public void reload() {
        this.refreshSeconds = config.getLeaderboardRefreshSeconds();
        this.limit = Math.max(1, config.getLeaderboardLimit());
        this.byDamage = config.isLeaderboardByDamage();
    }

    /** 启动定时刷新任务（异步），并立即刷新一次。 */
    public void start() {
        if (refreshSeconds <= 0) {
            return;
        }
        long ticks = refreshSeconds * 20L;
        taskId = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::refresh, 20L, ticks)
                .getTaskId();
        // 立即刷新一次，避免上线初期排行榜为空
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::refresh);
    }

    /** 停止刷新任务（关服时调用） */
    public void stop() {
        if (taskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    /** 主动刷新（读取主存储并重新排序、回填 rank） */
    public void refresh() {
        List<LeaderboardRow> dmg = storageManager.topPlayers(limit, true);
        List<LeaderboardRow> kil = storageManager.topPlayers(limit, false);
        Map<UUID, Integer> rd = new LinkedHashMap<>();
        Map<UUID, Integer> rk = new LinkedHashMap<>();
        int r = 1;
        for (LeaderboardRow row : dmg) {
            row.setRank(r);
            rd.put(row.getOwner(), r);
            r++;
        }
        r = 1;
        for (LeaderboardRow row : kil) {
            row.setRank(r);
            rk.put(row.getOwner(), r);
            r++;
        }
        this.rowsDamage = dmg;
        this.rowsKills = kil;
        this.rankDamage.clear();
        this.rankDamage.putAll(rd);
        this.rankKills.clear();
        this.rankKills.putAll(rk);
        plugin.getLogger().info("[排行榜] 已刷新：伤害榜 " + dmg.size() + " 名，击杀榜 " + kil.size() + " 名");
    }

    /** 伤害榜快照 */
    public List<LeaderboardRow> getRowsByDamage() {
        return rowsDamage;
    }

    /** 击杀榜快照 */
    public List<LeaderboardRow> getRowsByKills() {
        return rowsKills;
    }

    /**
     * 查询 owner 在指定排序下的名次。未上榜返回 0。
     */
    public int getRank(UUID owner, boolean byDamage) {
        Integer rank = (byDamage ? rankDamage : rankKills).get(owner);
        return rank == null ? 0 : rank;
    }

    /**
     * 查询 owner 在默认排序（配置决定）下的名次。
     */
    public int getRank(UUID owner) {
        return getRank(owner, byDamage);
    }

    /** 取指定排序下的第 n 名（1 起），越界返回 null */
    public LeaderboardRow getRow(int n, boolean byDamage) {
        List<LeaderboardRow> rows = byDamage ? rowsDamage : rowsKills;
        if (n < 1 || n > rows.size()) {
            return null;
        }
        return rows.get(n - 1);
    }
}
