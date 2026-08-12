package soys.soysmyloot.storage;

import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.storage.impl.MysqlStorage;
import soys.soysmyloot.storage.impl.SqlStorage;
import soys.soysmyloot.storage.impl.SqliteStorage;
import soys.soysmyloot.storage.impl.YamlStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * 存储协调器。
 * <p>
 * <b>主辅模型</b>：所有已启用的后端中优先级最高者（MYSQL &gt; SQLITE &gt; YAML）成为主存储，
 * 承担全部读操作；其余后端作为辅助存储，在写入时被镜像同步，充当热备份与降级方案。
 * </p>
 * <p>
 * <b>写入顺序</b>：所有异步写入被收敛到单线程执行器，保证同一名玩家的写操作严格有序，
 * 避免并发写导致的数据错乱。
 * </p>
 */
public class StorageManager {

    private final SOYSMyLoot plugin;

    /** 全部已成功初始化的后端 */
    private final Map<StorageType, DataStorage> storages = new EnumMap<>(StorageType.class);

    private DataStorage primary;
    private final List<DataStorage> secondaries = new ArrayList<>();

    /** 串行写入线程，保证写顺序 */
    private ExecutorService writeExecutor;

    public StorageManager(SOYSMyLoot plugin) {
        this.plugin = plugin;
    }

    // ================================================================
    //  生命周期
    // ================================================================

    /**
     * 构建并初始化所有启用的后端，选出主存储。
     *
     * @throws IllegalStateException 没有任何后端可用时抛出
     */
    public void initialize() {
        shutdownInternal(false);

        this.writeExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SOYSMyLoot-Storage");
            thread.setDaemon(true);
            return thread;
        });

        for (StorageType type : StorageType.values()) {
            if (!plugin.getConfigManager().isBackendEnabled(type.getId())) {
                continue;
            }
            DataStorage storage = buildStorage(type);
            if (storage == null) {
                continue;
            }
            try {
                storage.initialize();
                storages.put(type, storage);
                plugin.getLogger().info("已启用存储后端: " + type.getDisplayName()
                        + " (" + storage.describe() + ")");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "存储后端 " + type.getDisplayName() + " 初始化失败，已跳过: " + e.getMessage(), e);
            }
        }

        if (storages.isEmpty()) {
            throw new IllegalStateException(
                    "没有任何可用的存储后端，请检查 config.yml 中 storage.backends 的配置");
        }

        // 按优先级降序排序，最高者为主存储
        List<DataStorage> sorted = new ArrayList<>(storages.values());
        sorted.sort(Comparator.comparingInt((DataStorage s) -> s.getType().getPriority()).reversed());

        this.primary = sorted.get(0);
        this.secondaries.clear();
        for (int i = 1; i < sorted.size(); i++) {
            secondaries.add(sorted.get(i));
        }

        plugin.getLogger().info("主存储: " + primary.getType().getDisplayName()
                + (secondaries.isEmpty() ? "，无辅助存储" : "，辅助存储: " + describeSecondaries()));

        startKeepAliveTask();

        if (plugin.getConfigManager().isSyncOnStartup() && !secondaries.isEmpty()) {
            plugin.getLogger().info("正在执行启动时同步...");
            submit(() -> {
                try {
                    int count = syncToSecondaries();
                    plugin.getLogger().info("启动同步完成，已写入 " + count + " 名玩家数据");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "启动同步失败: " + e.getMessage(), e);
                }
            });
        }
    }

    /**
     * 关闭所有后端，等待写队列排空。
     */
    public void shutdown() {
        shutdownInternal(true);
    }

    private void shutdownInternal(boolean awaitWrites) {
        if (writeExecutor != null) {
            writeExecutor.shutdown();
            if (awaitWrites) {
                try {
                    if (!writeExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                        plugin.getLogger().warning("存储写入队列未能在 15 秒内排空，部分数据可能丢失");
                        writeExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    writeExecutor.shutdownNow();
                }
            }
            writeExecutor = null;
        }
        for (DataStorage storage : storages.values()) {
            try {
                storage.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warning("关闭存储后端 " + storage.getType().getId()
                        + " 时出错: " + e.getMessage());
            }
        }
        storages.clear();
        secondaries.clear();
        primary = null;
    }

    /**
     * 构建后端实例。新增后端类型时在此注册即可。
     */
    private DataStorage buildStorage(StorageType type) {
        switch (type) {
            case YAML:
                return new YamlStorage(plugin);
            case SQLITE:
                return new SqliteStorage(plugin);
            case MYSQL:
                return new MysqlStorage(plugin);
            default:
                return null;
        }
    }

    private void startKeepAliveTask() {
        DataStorage mysql = storages.get(StorageType.MYSQL);
        if (!(mysql instanceof MysqlStorage)) {
            return;
        }
        int seconds = ((MysqlStorage) mysql).getKeepAliveSeconds();
        if (seconds <= 0) {
            return;
        }
        long ticks = seconds * 20L;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                () -> ((SqlStorage) mysql).keepAlive(), ticks, ticks);
    }

    // ================================================================
    //  访问器
    // ================================================================

    public DataStorage getPrimary() {
        return primary;
    }

    public List<DataStorage> getSecondaries() {
        return Collections.unmodifiableList(secondaries);
    }

    public DataStorage getStorage(StorageType type) {
        return storages.get(type);
    }

    public Collection<DataStorage> getAllStorages() {
        return Collections.unmodifiableCollection(storages.values());
    }

    public boolean isEnabled(StorageType type) {
        return storages.containsKey(type);
    }

    private String describeSecondaries() {
        StringBuilder builder = new StringBuilder();
        for (DataStorage storage : secondaries) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(storage.getType().getDisplayName());
        }
        return builder.toString();
    }

    // ================================================================
    //  异步调度
    // ================================================================

    /**
     * 提交一个任务到串行写入线程。
     */
    public void submit(Runnable task) {
        ExecutorService executor = this.writeExecutor;
        if (executor == null || executor.isShutdown()) {
            // 关服阶段直接在当前线程执行，保证数据不丢
            task.run();
            return;
        }
        executor.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "存储任务执行异常: " + t.getMessage(), t);
            }
        });
    }

    /**
     * 回到主线程执行。
     */
    private void sync(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    // ================================================================
    //  读
    // ================================================================

    /**
     * 从主存储同步读取玩家数据。<b>可能阻塞，请勿在主线程调用 SQL 后端。</b>
     */
    public PlayerData loadPlayer(UUID playerUuid) throws Exception {
        return primary.loadPlayer(playerUuid);
    }

    /**
     * 异步读取玩家数据，结果回调在主线程执行。
     */
    public void loadPlayerAsync(UUID playerUuid, Consumer<PlayerData> callback) {
        submit(() -> {
            PlayerData data = null;
            try {
                data = primary.loadPlayer(playerUuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "读取玩家 " + playerUuid + " 失败: " + e.getMessage(), e);
            }
            PlayerData result = data;
            sync(() -> callback.accept(result));
        });
    }

    public Collection<PlayerData> loadAllPlayers() throws Exception {
        return primary.loadAllPlayers();
    }

    public int countPlayers() {
        try {
            return primary.countPlayers();
        } catch (Exception e) {
            return 0;
        }
    }

    // ================================================================
    //  写
    // ================================================================

    /**
     * 异步保存玩家数据：主存储写入后镜像到辅助存储。
     */
    public void savePlayerAsync(PlayerData data) {
        submit(() -> savePlayerBlocking(data));
    }

    /**
     * 同步保存玩家数据（阻塞当前线程），关服流程使用。
     */
    public void savePlayerBlocking(PlayerData data) {
        if (data == null) {
            return;
        }
        try {
            primary.savePlayer(data);
            data.clearDirty();
            debug("已保存玩家 " + data.getUuid() + " 到 " + primary.getType().getId());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "保存玩家 " + data.getUuid() + " 到主存储失败: " + e.getMessage(), e);
            return;
        }
        mirror(storage -> storage.savePlayer(data), "保存玩家 " + data.getUuid());
    }

    /**
     * 批量保存（关服 / 定时自动保存）。
     */
    public void savePlayersBlocking(Collection<PlayerData> datas) {
        if (datas == null || datas.isEmpty()) {
            return;
        }
        List<PlayerData> valid = new ArrayList<>();
        for (PlayerData data : datas) {
            if (data != null) {
                valid.add(data);
            }
        }
        if (valid.isEmpty()) {
            return;
        }
        try {
            primary.savePlayers(valid);
            for (PlayerData data : valid) {
                data.clearDirty();
            }
            debug("已批量保存 " + valid.size() + " 名玩家到 " + primary.getType().getId());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "批量保存到主存储失败: " + e.getMessage(), e);
            return;
        }
        mirror(storage -> storage.savePlayers(valid), "批量保存 " + valid.size() + " 名玩家");
    }

    /**
     * 异步删除玩家数据。
     */
    public void deletePlayerAsync(UUID playerUuid) {
        submit(() -> {
            try {
                primary.deletePlayer(playerUuid);
                debug("已从 " + primary.getType().getId() + " 删除玩家 " + playerUuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "从主存储删除玩家 " + playerUuid + " 失败: " + e.getMessage(), e);
                return;
            }
            mirror(storage -> storage.deletePlayer(playerUuid), "删除玩家 " + playerUuid);
        });
    }

    /**
     * 把一次写操作镜像到所有辅助存储。
     */
    private void mirror(StorageAction action, String description) {
        if (secondaries.isEmpty() || !plugin.getConfigManager().isMirrorEnabled()) {
            return;
        }
        Runnable task = () -> {
            for (DataStorage storage : secondaries) {
                if (!storage.isAvailable()) {
                    continue;
                }
                try {
                    action.execute(storage);
                } catch (Exception e) {
                    plugin.getLogger().warning("[镜像] " + description + " 写入 "
                            + storage.getType().getId() + " 失败: " + e.getMessage());
                }
            }
        };
        if (plugin.getConfigManager().isMirrorAsync()) {
            submit(task);
        } else {
            task.run();
        }
    }

    // ================================================================
    //  迁移与同步
    // ================================================================

    /**
     * 在两个后端之间迁移数据。
     *
     * @param from      来源后端
     * @param to        目标后端
     * @param overwrite true 表示先清空目标后端
     * @return 迁移的玩家数量
     * @throws Exception 任一步骤失败
     */
    public int migrate(StorageType from, StorageType to, boolean overwrite) throws Exception {
        DataStorage source = storages.get(from);
        DataStorage target = storages.get(to);
        if (source == null) {
            throw new IllegalStateException("来源后端 " + from.getId() + " 未启用");
        }
        if (target == null) {
            throw new IllegalStateException("目标后端 " + to.getId() + " 未启用");
        }
        Collection<PlayerData> players = source.loadAllPlayers();
        if (overwrite) {
            target.clear();
        }
        target.savePlayers(players);
        return players.size();
    }

    /**
     * 把主存储的全量数据覆盖同步到所有辅助存储。
     *
     * @return 同步的玩家数量
     */
    public int syncToSecondaries() throws Exception {
        if (secondaries.isEmpty()) {
            return 0;
        }
        Collection<PlayerData> players = primary.loadAllPlayers();
        for (DataStorage storage : secondaries) {
            if (!storage.isAvailable()) {
                continue;
            }
            try {
                storage.clear();
                storage.savePlayers(players);
            } catch (Exception e) {
                plugin.getLogger().warning("同步到 " + storage.getType().getId()
                        + " 失败: " + e.getMessage());
            }
        }
        return players.size();
    }

    private void debug(String message) {
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[存储] " + message);
        }
    }

    /**
     * 可抛异常的存储操作，用于镜像写入。
     */
    @FunctionalInterface
    private interface StorageAction {
        void execute(DataStorage storage) throws Exception;
    }
}
