package soys.soysmyloot.data;

import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.storage.StorageManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 数据缓存层：在内存中缓存玩家数据，按需从存储层加载，并统一触发持久化。
 * <p>
 * 本类是对 {@link StorageManager} 的薄门面：读操作走主存储（同步、会阻塞当前线程），
 * 写操作经存储层的串行异步队列落盘并镜像到辅助后端。对外 API 保持稳定，
 * 业务层（监听器 / 指令 / 奖励 / 占位符）无需感知底层存储细节。
 * </p>
 */
public class DataManager {

    private final SOYSMyLoot plugin;
    private final StorageManager storageManager;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataManager(SOYSMyLoot plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    /** 获取玩家数据（优先缓存，否则从主存储加载） */
    public PlayerData getData(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> {
            try {
                PlayerData data = storageManager.loadPlayer(uuid);
                return data != null ? data : new PlayerData(uuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "加载玩家数据失败: " + uuid, e);
                return new PlayerData(uuid);
            }
        });
    }

    /** 卸载指定玩家：若存在脏数据则先保存 */
    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null && data.isDirty()) {
            storageManager.savePlayerAsync(data);
        }
    }

    /** 保存指定玩家（若脏） */
    public void save(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null && data.isDirty()) {
            storageManager.savePlayerAsync(data);
        }
    }

    /** 保存所有脏数据 */
    public void saveAll() {
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                storageManager.savePlayerAsync(data);
            }
        }
    }
}
