package soys.soysmyloot.storage.impl;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.storage.DataStorage;
import soys.soysmyloot.storage.LeaderboardRow;
import soys.soysmyloot.storage.StorageType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * YAML 文件存储后端。
 * <p>
 * 零外部依赖，默认可作为辅助镜像或小型服主存储。所有玩家数据写在同一个
 * players.yml 中，通过整对象加锁保证并发安全。
 * </p>
 */
public class YamlStorage implements DataStorage {

    private static final String ROOT = "players";

    /** 进度节点中代表「不按世界隔离」的世界键（空世界在 YAML 中不易阅读，用哨兵替代） */
    private static final String GLOBAL_WORLD = "__global__";

    private final SOYSMyLoot plugin;
    private final Object lock = new Object();

    private File file;
    private YamlConfiguration config;
    private boolean available = false;
    private boolean backupOnSave = false;

    public YamlStorage(SOYSMyLoot plugin) {
        this.plugin = plugin;
    }

    @Override
    public StorageType getType() {
        return StorageType.YAML;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfigManager().getBackendSection("yaml");
        String path = section == null ? "data/players.yml" : section.getString("file", "data/players.yml");
        this.backupOnSave = section != null && section.getBoolean("backup-on-save", false);

        this.file = new File(plugin.getDataFolder(), path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建数据目录: " + parent.getAbsolutePath());
        }
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("无法创建数据文件: " + file.getAbsolutePath());
        }
        synchronized (lock) {
            this.config = YamlConfiguration.loadConfiguration(file);
            if (!config.isConfigurationSection(ROOT)) {
                config.createSection(ROOT);
            }
        }
        this.available = true;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            try {
                if (config != null && file != null) {
                    config.save(file);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[YAML] 关闭时保存失败: " + e.getMessage());
            }
            available = false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String describe() {
        return file == null ? "未初始化" : file.getPath().replace('\\', '/');
    }

    // ================================================================
    //  读
    // ================================================================

    @Override
    public PlayerData loadPlayer(UUID playerUuid) {
        synchronized (lock) {
            ConfigurationSection section = config.getConfigurationSection(ROOT + "." + playerUuid);
            return section == null ? null : deserialize(playerUuid, section);
        }
    }

    @Override
    public Collection<PlayerData> loadAllPlayers() {
        synchronized (lock) {
            Collection<PlayerData> players = new ArrayList<>();
            ConfigurationSection root = config.getConfigurationSection(ROOT);
            if (root == null) {
                return players;
            }
            for (String key : root.getKeys(false)) {
                UUID id = parseUuid(key);
                if (id == null) {
                    continue;
                }
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                PlayerData data = deserialize(id, section);
                if (data != null) {
                    players.add(data);
                }
            }
            return players;
        }
    }

    @Override
    public int countPlayers() {
        synchronized (lock) {
            ConfigurationSection root = config.getConfigurationSection(ROOT);
            return root == null ? 0 : root.getKeys(false).size();
        }
    }

    // ================================================================
    //  写
    // ================================================================

    @Override
    public void savePlayer(PlayerData data) throws Exception {
        synchronized (lock) {
            serialize(data);
            flush();
        }
    }

    @Override
    public void savePlayers(Collection<PlayerData> datas) throws Exception {
        synchronized (lock) {
            for (PlayerData data : datas) {
                if (data != null) {
                    serialize(data);
                }
            }
            flush();
        }
    }

    @Override
    public void deletePlayer(UUID playerUuid) throws Exception {
        synchronized (lock) {
            config.set(ROOT + "." + playerUuid, null);
            flush();
        }
    }

    @Override
    public void clear() throws Exception {
        synchronized (lock) {
            config.set(ROOT, null);
            config.createSection(ROOT);
            flush();
        }
    }

    @Override
    public void clearProgress() throws Exception {
        synchronized (lock) {
            ConfigurationSection root = config.getConfigurationSection(ROOT);
            if (root != null) {
                for (String key : root.getKeys(false)) {
                    config.set(ROOT + "." + key + ".progress", null);
                }
            }
            flush();
        }
    }

    @Override
    public List<LeaderboardRow> topPlayers(int limit, boolean byDamage) throws Exception {
        synchronized (lock) {
            Collection<PlayerData> players = loadAllPlayers();
            List<LeaderboardRow> rows = new ArrayList<>();
            for (PlayerData data : players) {
                rows.add(new LeaderboardRow(data.getUuid(), data.getTotalDamage(), data.getTotalKills()));
            }
            rows.sort(byDamage
                    ? Comparator.<LeaderboardRow>comparingDouble(LeaderboardRow::getTotalDamage).reversed()
                    .thenComparingInt(LeaderboardRow::getTotalKills).reversed()
                    : Comparator.<LeaderboardRow>comparingInt(LeaderboardRow::getTotalKills).reversed()
                    .thenComparingDouble(LeaderboardRow::getTotalDamage).reversed());
            int lim = Math.max(1, limit);
            return rows.subList(0, Math.min(lim, rows.size()));
        }
    }

    // ================================================================
    //  内部
    // ================================================================

    private void flush() throws IOException {
        if (backupOnSave && file.exists()) {
            File backup = new File(file.getParentFile(), file.getName() + ".bak");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("[YAML] 备份失败: " + e.getMessage());
            }
        }
        config.save(file);
    }

    private void serialize(PlayerData data) {
        String base = ROOT + "." + data.getUuid();

        // 整体重写 progress 节点，避免残留已无数据的怪物
        config.set(base + ".progress", null);
        for (Map.Entry<String, Double> entry : data.getDamageMap().entrySet()) {
            String composite = entry.getKey();
            String[] parts = PlayerData.splitKey(composite);
            String worldKey = parts[0].isEmpty() ? GLOBAL_WORLD : parts[0];
            String mid = parts[1];
            String path = base + ".progress." + worldKey + "." + mid;
            config.set(path + ".damage", entry.getValue());
            config.set(path + ".kills", data.getKills(parts[0], mid));
        }

        // 整体重写 claims 节点
        config.set(base + ".claims", null);
        java.util.Set<String> rewardIds = new java.util.HashSet<>(data.getLastClaimMap().keySet());
        rewardIds.addAll(data.getDailyClaimCountMap().keySet());
        rewardIds.addAll(data.getWeeklyClaimCountMap().keySet());
        for (String rid : rewardIds) {
            String path = base + ".claims." + rid;
            config.set(path + ".last-claim", data.getLastClaimMap().getOrDefault(rid, 0L));
            config.set(path + ".claim-count", data.getClaimCount(rid));
            config.set(path + ".daily-count", data.getDailyClaimCountMap().getOrDefault(rid, 0));
            config.set(path + ".daily-start", data.getDailyClaimStartMap().getOrDefault(rid, 0L));
            config.set(path + ".weekly-count", data.getWeeklyClaimCountMap().getOrDefault(rid, 0));
            config.set(path + ".weekly-start", data.getWeeklyClaimStartMap().getOrDefault(rid, 0L));
        }

        // 累计在线时长（分钟）
        config.set(base + ".online-minutes", data.getOnlineMinutes());
    }

    private PlayerData deserialize(UUID id, ConfigurationSection section) {
        PlayerData data = new PlayerData(id);

        ConfigurationSection progress = section.getConfigurationSection("progress");
        if (progress != null) {
            for (String worldKey : progress.getKeys(false)) {
                ConfigurationSection ws = progress.getConfigurationSection(worldKey);
                if (ws == null) {
                    continue;
                }
                String world = GLOBAL_WORLD.equals(worldKey) ? "" : worldKey;
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
        return data;
    }

    private UUID parseUuid(String input) {
        if (input == null) {
            return null;
        }
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
