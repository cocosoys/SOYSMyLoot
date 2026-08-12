package soys.soysmyloot.storage;

import soys.soysmyloot.data.PlayerData;

import java.util.Collection;
import java.util.UUID;

/**
 * 数据存储后端抽象。
 * <p>
 * 所有实现必须满足：
 * <ul>
 *   <li>方法可能在异步线程被调用，实现需自行保证线程安全；</li>
 *   <li>任何失败都以抛出异常的形式上报，由 {@link StorageManager} 统一降级处理；</li>
 *   <li>{@link #savePlayer(PlayerData)} 语义为 upsert，玩家不存在时插入，存在时整体覆盖。</li>
 * </ul>
 * 新增后端只需实现本接口并在 {@link StorageManager#buildStorage} 中注册。
 * </p>
 */
public interface DataStorage {

    /**
     * 后端类型。
     */
    StorageType getType();

    /**
     * 初始化连接 / 建表 / 创建数据文件。
     *
     * @throws Exception 初始化失败，该后端将被标记为不可用
     */
    void initialize() throws Exception;

    /**
     * 释放资源，关服或重载时调用。
     */
    void shutdown();

    /**
     * 后端当前是否可用。不可用的后端会被跳过而非导致插件崩溃。
     */
    boolean isAvailable();

    /**
     * 供 /myloot info 展示的简要描述，如文件路径或数据库地址。
     */
    String describe();

    // ================================================================
    //  读
    // ================================================================

    /**
     * 按 UUID 读取单名玩家的进度与领取记录。
     *
     * @return 玩家数据，不存在时返回 null
     */
    PlayerData loadPlayer(UUID playerUuid) throws Exception;

    /**
     * 读取全部玩家数据。仅用于迁移、同步与管理指令，常规流程不应调用。
     */
    Collection<PlayerData> loadAllPlayers() throws Exception;

    /**
     * 统计玩家总数。
     */
    int countPlayers() throws Exception;

    // ================================================================
    //  写
    // ================================================================

    /**
     * 保存（upsert）单名玩家的数据。
     */
    void savePlayer(PlayerData data) throws Exception;

    /**
     * 批量保存。实现应尽可能使用事务或单次落盘以提升性能。
     */
    void savePlayers(Collection<PlayerData> datas) throws Exception;

    /**
     * 删除一名玩家的全部数据。
     */
    void deletePlayer(UUID playerUuid) throws Exception;

    /**
     * 清空全部进度（伤害/击杀），保留领取记录。仅赛季重置（保留领取）时调用。
     */
    void clearProgress() throws Exception;

    /**
     * 清空全部数据（进度 + 领取）。仅由迁移覆盖流程调用。
     */
    void clear() throws Exception;

    /**
     * 排行榜聚合：按 owner 汇总 progress 表的累计伤害/击杀，整体排序后取前 limit 名。
     *
     * @param limit    返回数量上限
     * @param byDamage true 按伤害降序（伤害相同再按击杀），false 按击杀降序
     * @return 排行榜行（未回填 rank，由调用方排序后赋值）
     */
    java.util.List<LeaderboardRow> topPlayers(int limit, boolean byDamage) throws Exception;
}
