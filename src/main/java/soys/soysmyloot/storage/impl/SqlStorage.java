package soys.soysmyloot.storage.impl;

import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.storage.DataStorage;
import soys.soysmyloot.storage.LeaderboardRow;
import soys.soysmyloot.storage.StorageType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SQL 存储后端的公共实现。
 * <p>
 * SQLite 与 MySQL 共用同一套表结构与 CRUD 逻辑，子类只需提供：
 * 驱动类名、JDBC URL、连接创建方式与建表语句方言。
 * </p>
 * <p>
 * 数据模型：每名玩家（或队伍）对应两张表
 *   - progress：对某个世界下某个怪物的累计伤害与击杀数（player_uuid, world, monster_id）
 *   - claims  ：对每个奖励的领取时间与次数（player_uuid, reward_id）
 * 保存时整体重写该玩家（或队伍）的两表数据，保证一致性。
 * </p>
 * <p>
 * 连接策略：维持单个长连接并在每次使用前做有效性探测，配合对象锁串行化访问。
 * 插件的写操作本身已经被 StorageManager 收敛到异步队列，无需引入连接池。
 * </p>
 */
public abstract class SqlStorage implements DataStorage {

    protected final SOYSMyLoot plugin;
    protected final Object lock = new Object();

    protected String tablePrefix = "mc_sml_";
    protected volatile boolean available = false;

    private Connection connection;

    protected SqlStorage(SOYSMyLoot plugin) {
        this.plugin = plugin;
    }

    // ================================================================
    //  子类需实现的方言部分
    // ================================================================

    /** JDBC 驱动类名 */
    protected abstract String getDriverClass();

    /** 创建一个全新的数据库连接 */
    protected abstract Connection createConnection() throws SQLException;

    /** 建表与建索引语句，按顺序执行 */
    protected abstract String[] getSchemaStatements();

    // ================================================================
    //  表名
    // ================================================================

    protected String progressTable() {
        return tablePrefix + "progress";
    }

    protected String claimsTable() {
        return tablePrefix + "claims";
    }

    protected String metaTable() {
        return tablePrefix + "meta";
    }

    // ================================================================
    //  生命周期
    // ================================================================

    @Override
    public void initialize() throws Exception {
        try {
            Class.forName(getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 JDBC 驱动 " + getDriverClass()
                    + "，请确认服务端已提供该驱动或手动放入 libraries 目录");
        }
        synchronized (lock) {
            connection = createConnection();
            try (Statement statement = connection.createStatement()) {
                for (String sql : getSchemaStatements()) {
                    statement.execute(sql);
                }
            }
            ensureWorldColumn();
            ensureClaimsColumns();
        }
        available = true;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            available = false;
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // 关闭失败无需处理
                }
                connection = null;
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * 获取一个可用连接，失效时自动重建。调用方必须持有 {@link #lock}。
     */
    protected Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // 旧连接关闭失败可忽略
                }
            }
            connection = createConnection();
        }
        return connection;
    }

    /**
     * 主动探测连接（保活任务使用）。
     */
    public void keepAlive() {
        synchronized (lock) {
            try {
                connection().isValid(3);
            } catch (SQLException e) {
                plugin.getLogger().warning("[" + getType().getId() + "] 保活探测失败: " + e.getMessage());
            }
        }
    }

    // ================================================================
    //  读
    // ================================================================

    @Override
    public PlayerData loadPlayer(UUID playerUuid) throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            PlayerData data = new PlayerData(playerUuid);

            String progressSql = "SELECT world, monster_id, damage, kills FROM " + progressTable() + " WHERE player_uuid = ?";
            try (PreparedStatement statement = conn.prepareStatement(progressSql)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String world = rs.getString("world");
                        String mid = rs.getString("monster_id");
                        data.setDamage(world, mid, rs.getDouble("damage"));
                        data.setKill(world, mid, rs.getInt("kills"));
                    }
                }
            }

            String claimsSql = "SELECT reward_id, last_claim, claim_count, daily_count, weekly_count, daily_start, weekly_start"
                    + " FROM " + claimsTable() + " WHERE player_uuid = ?";
            try (PreparedStatement statement = conn.prepareStatement(claimsSql)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String rid = rs.getString("reward_id");
                        data.setLastClaim(rid, rs.getLong("last_claim"));
                        data.setClaimCount(rid, rs.getInt("claim_count"));
                        data.setDailyClaim(rid, rs.getInt("daily_count"), rs.getLong("daily_start"));
                        data.setWeeklyClaim(rid, rs.getInt("weekly_count"), rs.getLong("weekly_start"));
                    }
                }
            }

            loadMeta(conn, playerUuid, data);

            data.clearDirty();
            return data;
        }
    }

    private void loadMeta(Connection conn, UUID playerUuid, PlayerData data) throws SQLException {
        String sql = "SELECT online_minutes FROM " + metaTable() + " WHERE player_uuid = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    data.setOnlineMinutes(rs.getLong("online_minutes"));
                }
            }
        }
    }

    @Override
    public Collection<PlayerData> loadAllPlayers() throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            Map<UUID, PlayerData> map = new HashMap<>();

            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM " + progressTable())) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("player_uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    PlayerData data = map.computeIfAbsent(uuid, PlayerData::new);
                    String world = rs.getString("world");
                    String mid = rs.getString("monster_id");
                    data.setDamage(world, mid, rs.getDouble("damage"));
                    data.setKill(world, mid, rs.getInt("kills"));
                }
            }

            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM " + claimsTable())) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("player_uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    PlayerData data = map.computeIfAbsent(uuid, PlayerData::new);
                    String rid = rs.getString("reward_id");
                    data.setLastClaim(rid, rs.getLong("last_claim"));
                    data.setClaimCount(rid, rs.getInt("claim_count"));
                    data.setDailyClaim(rid, rs.getInt("daily_count"), rs.getLong("daily_start"));
                    data.setWeeklyClaim(rid, rs.getInt("weekly_count"), rs.getLong("weekly_start"));
                }
            }

            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM " + metaTable())) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("player_uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    PlayerData data = map.computeIfAbsent(uuid, PlayerData::new);
                    data.setOnlineMinutes(rs.getLong("online_minutes"));
                }
            }

            for (PlayerData data : map.values()) {
                data.clearDirty();
            }
            return new ArrayList<>(map.values());
        }
    }

    @Override
    public int countPlayers() throws Exception {
        synchronized (lock) {
            String sql = "SELECT COUNT(DISTINCT player_uuid) FROM ("
                    + "SELECT player_uuid FROM " + progressTable()
                    + " UNION SELECT player_uuid FROM " + claimsTable() + ") t";
            try (Statement statement = connection().createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ================================================================
    //  写
    // ================================================================

    @Override
    public void savePlayer(PlayerData data) throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                writePlayer(conn, data);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void savePlayers(Collection<PlayerData> datas) throws Exception {
        if (datas.isEmpty()) {
            return;
        }
        synchronized (lock) {
            Connection conn = connection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (PlayerData data : datas) {
                    if (data != null) {
                        writePlayer(conn, data);
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void deletePlayer(UUID playerUuid) throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            try (PreparedStatement statement =
                         conn.prepareStatement("DELETE FROM " + progressTable() + " WHERE player_uuid = ?")) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement =
                         conn.prepareStatement("DELETE FROM " + claimsTable() + " WHERE player_uuid = ?")) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement =
                         conn.prepareStatement("DELETE FROM " + metaTable() + " WHERE player_uuid = ?")) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            }
        }
    }

    @Override
    public void clearProgress() throws Exception {
        synchronized (lock) {
            try (Statement statement = connection().createStatement()) {
                statement.executeUpdate("DELETE FROM " + progressTable());
            }
        }
    }

    @Override
    public void clear() throws Exception {
        synchronized (lock) {
            try (Statement statement = connection().createStatement()) {
                statement.executeUpdate("DELETE FROM " + claimsTable());
                statement.executeUpdate("DELETE FROM " + progressTable());
            }
        }
    }

    @Override
    public java.util.List<LeaderboardRow> topPlayers(int limit, boolean byDamage) throws Exception {
        synchronized (lock) {
            String order = byDamage
                    ? "SUM(damage) DESC, SUM(kills) DESC"
                    : "SUM(kills) DESC, SUM(damage) DESC";
            String sql = "SELECT player_uuid, COALESCE(SUM(damage),0) AS td, COALESCE(SUM(kills),0) AS tk"
                    + " FROM " + progressTable() + " GROUP BY player_uuid"
                    + " ORDER BY " + order + " LIMIT ?";
            java.util.List<LeaderboardRow> rows = new java.util.ArrayList<>();
            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                statement.setInt(1, Math.max(1, limit));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = parseUuid(rs.getString("player_uuid"));
                        if (uuid == null) {
                            continue;
                        }
                        rows.add(new LeaderboardRow(uuid, rs.getDouble("td"), rs.getInt("tk")));
                    }
                }
            }
            return rows;
        }
    }

    /**
     * 兼容从 V1 升级的旧表：若 progress 表缺少 world 列则尝试追加，避免数据丢失。
     * 新建的表已在建表语句中包含该列，此处 ALTER 会因列已存在而静默失败。
     */
    private void ensureWorldColumn() {
        String ddl = "ALTER TABLE " + progressTable() + " ADD COLUMN world " + worldColumnDdl();
        synchronized (lock) {
            try (Statement statement = connection().createStatement()) {
                statement.executeUpdate(ddl);
            } catch (SQLException ignored) {
                // 列已存在或方言不支持，忽略
            }
        }
    }

    /** 追加 world 列的方言定义（子类覆盖） */
    protected String worldColumnDdl() {
        return "TEXT NOT NULL DEFAULT ''";
    }

    /**
     * 兼容从旧表升级：若 claims 表缺少每日/每周列则尝试追加，避免数据丢失。
     * 新建的表已通过建表语句包含这些列，此处 ALTER 会因列已存在而静默失败。
     */
    private void ensureClaimsColumns() {
        String[] cols = {
                "daily_count INTEGER NOT NULL DEFAULT 0",
                "weekly_count INTEGER NOT NULL DEFAULT 0",
                "daily_start BIGINT NOT NULL DEFAULT 0",
                "weekly_start BIGINT NOT NULL DEFAULT 0"
        };
        synchronized (lock) {
            for (String col : cols) {
                try (Statement statement = connection().createStatement()) {
                    statement.executeUpdate("ALTER TABLE " + claimsTable() + " ADD COLUMN " + col);
                } catch (SQLException ignored) {
                    // 列已存在或方言不支持，忽略
                }
            }
        }
    }

    // ================================================================
    //  内部
    // ================================================================

    private void writePlayer(Connection conn, PlayerData data) throws SQLException {
        String uuid = data.getUuid().toString();

        // 整体重写 progress
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM " + progressTable() + " WHERE player_uuid = ?")) {
            del.setString(1, uuid);
            del.executeUpdate();
        }
        Set<String> monsterIds = new HashSet<>(data.getDamageMap().keySet());
        monsterIds.addAll(data.getKillMap().keySet());
        if (!monsterIds.isEmpty()) {
            String sql = "REPLACE INTO " + progressTable()
                    + " (player_uuid, world, monster_id, damage, kills) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                for (String composite : monsterIds) {
                    String[] parts = PlayerData.splitKey(composite);
                    String world = parts[0];
                    String mid = parts[1];
                    statement.setString(1, uuid);
                    statement.setString(2, world);
                    statement.setString(3, mid);
                    statement.setDouble(4, data.getDamage(world, mid));
                    statement.setInt(5, data.getKills(world, mid));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        // 整体重写 claims
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM " + claimsTable() + " WHERE player_uuid = ?")) {
            del.setString(1, uuid);
            del.executeUpdate();
        }
        Set<String> rewardIds = new HashSet<>(data.getLastClaimMap().keySet());
        rewardIds.addAll(data.getClaimCountMap().keySet());
        if (!rewardIds.isEmpty()) {
            String sql = "REPLACE INTO " + claimsTable()
                    + " (player_uuid, reward_id, last_claim, claim_count, daily_count, weekly_count, daily_start, weekly_start)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                for (String rid : rewardIds) {
                    statement.setString(1, uuid);
                    statement.setString(2, rid);
                    statement.setLong(3, data.getLastClaim(rid));
                    statement.setInt(4, data.getClaimCount(rid));
                    statement.setInt(5, data.getDailyClaimCountMap().getOrDefault(rid, 0));
                    statement.setInt(6, data.getWeeklyClaimCountMap().getOrDefault(rid, 0));
                    statement.setLong(7, data.getDailyClaimStartMap().getOrDefault(rid, 0L));
                    statement.setLong(8, data.getWeeklyClaimStartMap().getOrDefault(rid, 0L));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        // 在线时长
        try (PreparedStatement statement = conn.prepareStatement(
                "REPLACE INTO " + metaTable() + " (player_uuid, online_minutes) VALUES (?, ?)")) {
            statement.setString(1, uuid);
            statement.setLong(2, data.getOnlineMinutes());
            statement.executeUpdate();
        }
    }

    protected UUID parseUuid(String input) {
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
