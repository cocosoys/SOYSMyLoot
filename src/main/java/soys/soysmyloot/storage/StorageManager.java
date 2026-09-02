package soys.soysmyloot.storage;

import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.storage.LeaderboardRow;
import soys.soysmyloot.storage.impl.MysqlStorage;
import soys.soysmyloot.storage.impl.SqlStorage;
import soys.soysmyloot.storage.impl.SqliteStorage;
import soys.soysmyloot.storage.impl.YamlStorage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
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

    // ================================================================
    //  赛季与排行榜
    // ================================================================

    /**
     * 清空全部进度（保留领取记录）：主存储与所有可用的辅助存储都执行。
     *
     * @throws Exception 主存储清空失败时抛出
     */
    public void clearProgressAll() throws Exception {
        primary.clearProgress();
        for (DataStorage storage : secondaries) {
            if (!storage.isAvailable()) {
                continue;
            }
            try {
                storage.clearProgress();
            } catch (Exception e) {
                plugin.getLogger().warning("清空辅助存储 " + storage.getType().getId()
                        + " 进度失败: " + e.getMessage());
            }
        }
    }

    /**
     * 排行榜聚合：从主存储读取并排序后的前 limit 名（rank 尚未回填，由调用方赋值）。
     *
     * @param limit    返回数量上限
     * @param byDamage true 按伤害降序，false 按击杀降序
     */
    public List<LeaderboardRow> topPlayers(int limit, boolean byDamage) {
        try {
            return primary.topPlayers(limit, byDamage);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "读取排行榜失败: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 清空全部数据（进度 + 领取）：主存储与所有可用辅助存储都执行。仅完全重置时使用。
     */
    public void clearAll() throws Exception {
        primary.clear();
        for (DataStorage storage : secondaries) {
            if (!storage.isAvailable()) {
                continue;
            }
            try {
                storage.clear();
            } catch (Exception e) {
                plugin.getLogger().warning("清空辅助存储 " + storage.getType().getId()
                        + " 全部数据失败: " + e.getMessage());
            }
        }
    }

    // ================================================================
    //  导入 / 导出（备份与迁移）
    // ================================================================

    /**
     * 导出全部玩家数据到 YAML 文件（可移植备份格式，独立于后端实现）。
     *
     * @return 导出的玩家数量
     */
    public int exportAll(File file) throws Exception {
        Collection<PlayerData> all = primary.loadAllPlayers();
        YamlConfiguration out = new YamlConfiguration();
        for (PlayerData data : all) {
            String base = "players." + data.getUuid();
            for (Map.Entry<String, Double> entry : data.getDamageMap().entrySet()) {
                String[] parts = PlayerData.splitKey(entry.getKey());
                String worldKey = parts[0].isEmpty() ? "__global__" : parts[0];
                String path = base + ".progress." + worldKey + "." + parts[1];
                out.set(path + ".damage", entry.getValue());
                out.set(path + ".kills", data.getKills(parts[0], parts[1]));
            }
            java.util.Set<String> claimIds = new java.util.HashSet<>(data.getLastClaimMap().keySet());
            claimIds.addAll(data.getDailyClaimCountMap().keySet());
            claimIds.addAll(data.getWeeklyClaimCountMap().keySet());
            for (String rid : claimIds) {
                String path = base + ".claims." + rid;
                out.set(path + ".last-claim", data.getLastClaimMap().getOrDefault(rid, 0L));
                out.set(path + ".claim-count", data.getClaimCount(rid));
                out.set(path + ".daily-count", data.getDailyClaimCountMap().getOrDefault(rid, 0));
                out.set(path + ".daily-start", data.getDailyClaimStartMap().getOrDefault(rid, 0L));
                out.set(path + ".weekly-count", data.getWeeklyClaimCountMap().getOrDefault(rid, 0));
                out.set(path + ".weekly-start", data.getWeeklyClaimStartMap().getOrDefault(rid, 0L));
            }
            out.set(base + ".online-minutes", data.getOnlineMinutes());
        }
        out.save(file);
        return all.size();
    }

    /**
     * 从 YAML 备份文件导入数据：按 owner 逐条 upsert 覆盖（合并模式，不删除文件中没有的玩家）。
     *
     * @return 导入的玩家数量
     */
    public int importAll(File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalStateException("备份文件不存在: " + (file == null ? "null" : file.getPath()));
        }
        YamlConfiguration in = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = in.getConfigurationSection("players");
        if (root == null) {
            return 0;
        }
        List<PlayerData> toSave = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            UUID uuid = parseUuidSafe(key);
            if (uuid == null) {
                continue;
            }
            PlayerData data = new PlayerData(uuid);
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ConfigurationSection progress = section.getConfigurationSection("progress");
            if (progress != null) {
                for (String worldKey : progress.getKeys(false)) {
                    String world = "__global__".equals(worldKey) ? "" : worldKey;
                    ConfigurationSection ws = progress.getConfigurationSection(worldKey);
                    if (ws == null) {
                        continue;
                    }
                    for (String mid : ws.getKeys(false)) {
                        ConfigurationSection ps = ws.getConfigurationSection(mid);
                        if (ps == null) {
                            continue;
                        }
                        data.setDamage(world, mid, ps.getDouble("damage", 0));
                        data.setKill(world, mid, ps.getInt("kills", 0));
                    }
                }
            }
            ConfigurationSection claims = section.getConfigurationSection("claims");
            if (claims != null) {
                for (String rid : claims.getKeys(false)) {
                    ConfigurationSection cs = claims.getConfigurationSection(rid);
                    if (cs == null) {
                        continue;
                    }
                    data.setLastClaim(rid, cs.getLong("last-claim", 0));
                    data.setClaimCount(rid, cs.getInt("claim-count", 0));
                    data.setDailyClaim(rid, cs.getInt("daily-count", 0), cs.getLong("daily-start", 0));
                    data.setWeeklyClaim(rid, cs.getInt("weekly-count", 0), cs.getLong("weekly-start", 0));
                }
            }
            data.setOnlineMinutes(section.getLong("online-minutes", 0));
            data.clearDirty();
            toSave.add(data);
        }
        if (toSave.isEmpty()) {
            return 0;
        }
        primary.savePlayers(toSave);
        for (DataStorage storage : secondaries) {
            if (!storage.isAvailable()) {
                continue;
            }
            try {
                storage.savePlayers(toSave);
            } catch (Exception e) {
                plugin.getLogger().warning("导入镜像到 " + storage.getType().getId()
                        + " 失败: " + e.getMessage());
            }
        }
        return toSave.size();
    }

    private UUID parseUuidSafe(String input) {
        if (input == null) {
            return null;
        }
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
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
