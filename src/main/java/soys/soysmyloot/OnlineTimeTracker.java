package soys.soysmyloot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 累计在线时长追踪器。
 * <p>由于 Spigot 1.12.2 的 {@code Statistic.PLAY_ONE_MINUTE} 等常量不可用，
 * 这里自行以「分钟」为单位累计每位玩家的在线时长，并写入其个人 {@link PlayerData}
 * （按玩家自身 UUID，而非队伍归属），由存储层随其它数据一起持久化。</p>
 * <p>计数采用「每分钟结算一次」的滑动窗口，避免重复或遗漏；玩家退出 / 关服时做最终结算。</p>
 */
public class OnlineTimeTracker implements Listener {

    private final SOYSMyLoot plugin;
    private final DataManager dataManager;
    /** 玩家 UUID -> 上次结算时刻（epoch 毫秒） */
    private final Map<UUID, Long> lastTick = new ConcurrentHashMap<>();

    public OnlineTimeTracker(SOYSMyLoot plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    /** 插件启用时为已在线的玩家建立结算基准 */
    public void init() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            lastTick.put(p.getUniqueId(), now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lastTick.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flush(event.getPlayer());
    }

    /** 周期性结算：把距上次结算已满 1 分钟的在线时长累加进数据 */
    public void flushTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Long t = lastTick.get(p.getUniqueId());
            if (t == null) {
                lastTick.put(p.getUniqueId(), now);
                continue;
            }
            long delta = now - t;
            if (delta >= 60000) {
                long minutes = delta / 60000;
                accumulate(p, minutes);
                lastTick.put(p.getUniqueId(), now - (delta % 60000));
            }
        }
    }

    /** 结算并移除某玩家（退出时调用） */
    private void flush(Player player) {
        Long t = lastTick.remove(player.getUniqueId());
        if (t == null) {
            return;
        }
        long minutes = (System.currentTimeMillis() - t) / 60000;
        if (minutes > 0) {
            accumulate(player, minutes);
        }
    }

    /** 关服时为所有在线玩家做最终结算 */
    public void flushAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            flush(p);
        }
    }

    private void accumulate(Player player, long minutes) {
        PlayerData data = dataManager.getData(player.getUniqueId());
        data.addOnlineMinutes(minutes);
        dataManager.save(player.getUniqueId());
    }
}
