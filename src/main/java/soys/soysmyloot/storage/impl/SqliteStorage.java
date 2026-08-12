package soys.soysmyloot.storage.impl;

import org.bukkit.configuration.ConfigurationSection;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.storage.StorageType;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite 存储后端。
 * <p>单文件数据库，无需额外服务，适合需要 SQL 查询能力但不想部署 MySQL 的场景。</p>
 */
public class SqliteStorage extends SqlStorage {

    private File databaseFile;

    public SqliteStorage(SOYSMyLoot plugin) {
        super(plugin);
    }

    @Override
    public StorageType getType() {
        return StorageType.SQLITE;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfigManager().getBackendSection("sqlite");
        String path = section == null ? "data/players.db" : section.getString("file", "data/players.db");
        this.tablePrefix = section == null ? "mc_sml_" : section.getString("table-prefix", "mc_sml_");

        this.databaseFile = new File(plugin.getDataFolder(), path);
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建数据目录: " + parent.getAbsolutePath());
        }
        super.initialize();
    }

    @Override
    public String describe() {
        return databaseFile == null ? "未初始化" : databaseFile.getPath().replace('\\', '/');
    }

    @Override
    protected String getDriverClass() {
        return "org.sqlite.JDBC";
    }

    @Override
    protected Connection createConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databaseFile.getAbsolutePath());
        // 开启外键与 WAL，提升并发读性能
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    @Override
    protected String[] getSchemaStatements() {
        return new String[]{
                "CREATE TABLE IF NOT EXISTS " + progressTable() + " ("
                        + "player_uuid TEXT NOT NULL,"
                        + "world TEXT NOT NULL DEFAULT '',"
                        + "monster_id TEXT NOT NULL,"
                        + "damage REAL NOT NULL DEFAULT 0,"
                        + "kills INTEGER NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (player_uuid, world, monster_id)"
                        + ")",
                "CREATE TABLE IF NOT EXISTS " + claimsTable() + " ("
                        + "player_uuid TEXT NOT NULL,"
                        + "reward_id TEXT NOT NULL,"
                        + "last_claim INTEGER NOT NULL DEFAULT 0,"
                        + "claim_count INTEGER NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (player_uuid, reward_id)"
                        + ")",
                "CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "claims_uuid"
                        + " ON " + claimsTable() + " (player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "progress_uuid"
                        + " ON " + progressTable() + " (player_uuid)"
        };
    }
}
