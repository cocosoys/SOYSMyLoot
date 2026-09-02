package soys.soysmyloot.season;

import org.bukkit.Bukkit;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.config.MessageManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.storage.StorageManager;

import java.util.logging.Level;

/**
 * 赛季（自动重置）管理器。
 * <p>
 * 按配置周期（{@code season.period-hours}）自动清空进度，支持两种模式：
 *   - keep-claims=true（默认）：仅清空伤害/击杀进度，保留奖励领取记录，适合常规赛季轮换；
 *   - keep-claims=false：清空全部数据（含领取记录），适合彻底开新档。
 * 同时提供 {@link #resetProgress(boolean, boolean)} 供 /myloot reset 指令手动触发。
 * </p>
 */
public final class SeasonManager {

    private final SOYSMyLoot plugin;
    private final StorageManager storageManager;
    private final DataManager dataManager;
    private final ConfigManager config;
    private final MessageManager messageManager;

    private int taskId = -1;
    private long nextResetEpoch = 0;

    public SeasonManager(SOYSMyLoot plugin, StorageManager storageManager, DataManager dataManager,
                         ConfigManager config, MessageManager messageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.dataManager = dataManager;
        this.config = config;
        this.messageManager = messageManager;
    }

    /** 启动定时重置任务（异步）。返回下一次重置的 epoch 秒，未启用返回 0。 */
    public long start() {
        if (!config.isSeasonAutoReset()) {
            return 0;
        }
        long periodSeconds = config.getSeasonPeriodHours() * 3600L;
        if (periodSeconds <= 0) {
            return 0;
        }
        long ticks = periodSeconds * 20L;
        // 延迟一个周期后首次执行，避免启服即清
        taskId = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, () -> resetProgress(config.isSeasonKeepClaims(), true),
                        ticks, ticks)
                .getTaskId();
        this.nextResetEpoch = System.currentTimeMillis() / 1000 + periodSeconds;
        plugin.getLogger().info("[赛季] 自动重置已启用，周期 " + config.getSeasonPeriodHours()
                + " 小时，保留领取记录=" + config.isSeasonKeepClaims()
                + "，下次重置 " + nextResetEpoch);
        return nextResetEpoch;
    }

    /** 停止定时任务（关服时调用） */
    public void stop() {
        if (taskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    /** 下一次自动重置的 epoch 秒（未启用返回 0） */
    public long getNextResetEpoch() {
        return nextResetEpoch;
    }

    /**
     * 执行进度重置。
     *
     * @param keepClaims 是否保留领取记录（true=仅清进度，false=全清）
     * @param announce   是否向全服广播提示
     */
    public void resetProgress(boolean keepClaims, boolean announce) {
        try {
            if (keepClaims) {
                storageManager.clearProgressAll();
                dataManager.resetProgressAll(false);
            } else {
                storageManager.clearAll();
                dataManager.resetProgressAll(true);
            }
            if (announce) {
                messageManager.send(Bukkit.getConsoleSender(),
                        keepClaims ? "season-reset-keep" : "season-reset-full");
            }
            plugin.getLogger().info("[赛季] 进度已重置（keepClaims=" + keepClaims + "）");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[赛季] 进度重置失败", e);
        }
        // 重新安排下次重置时间（任务为循环任务，仅刷新展示用时间戳）
        if (taskId >= 0) {
            long periodSeconds = config.getSeasonPeriodHours() * 3600L;
            if (periodSeconds > 0) {
                this.nextResetEpoch = System.currentTimeMillis() / 1000 + periodSeconds;
            }
        }
    }
}
