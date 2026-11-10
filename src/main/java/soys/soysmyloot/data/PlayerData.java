package soys.soysmyloot.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家/队伍数据（内存模型）：累计伤害、击杀数、奖励领取记录。
 * <p>
 * 进度（伤害/击杀）按「世界 + 怪物」复合维度记录，以支持多世界隔离；
 * 领取记录按奖励 ID 记录，不受世界隔离影响。队伍共享模式下，本对象同样用于
 * 承载队伍（以队伍 UUID 作为 owner）的共享进度。
 * </p>
 * <p>
 * 复合键格式：{@code world + SEP + monsterId}，其中 world 为空字符串表示「不按世界隔离」。
 * </p>
 * <p>
 * 线程模型：所有实例方法均加锁，主线程写入与存储线程读取通过 {@link #snapshot()}
 * 隔离；每次写操作递增 {@link #getVersion()}，保存完成后只有版本未变化才清除脏标记，
 * 避免异步落盘期间的新写入被误清除。
 * </p>
 */
public class PlayerData {

    /** 复合键分隔符（SOH，避免与怪物 ID 中的字符冲突，且不是 YAML 节点分隔符 '.'） */
    static final String SEP = "\u0001";

    private final UUID uuid;
    private final Map<String, Double> damageMap = new HashMap<>();
    private final Map<String, Integer> killMap = new HashMap<>();
    private final Map<String, Long> lastClaimMap = new HashMap<>();    // 奖励ID -> 上次领取时间(epoch秒)
    private final Map<String, Integer> claimCountMap = new HashMap<>(); // 奖励ID -> 已领取次数
    // ---- 累计在线时长（分钟），独立于进度/领取，按玩家自身 UUID 记录 ----
    private long onlineMinutes;
    // ---- 每日 / 每周领取计数（与周期起点一起用于跨周期自动重置）----
    private final Map<String, Integer> dailyClaimCountMap = new HashMap<>();
    private final Map<String, Long> dailyClaimStartMap = new HashMap<>();
    private final Map<String, Integer> weeklyClaimCountMap = new HashMap<>();
    private final Map<String, Long> weeklyClaimStartMap = new HashMap<>();
    private volatile boolean dirty = false;
    /** 写版本号：每次数据变更 +1，供异步保存判断期间是否有新写入 */
    private long version = 0;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    /** 当前写版本号 */
    public synchronized long getVersion() {
        return version;
    }

    /** 清除脏标记（仅用于加载完成的新对象） */
    public synchronized void clearDirty() {
        dirty = false;
    }

    /**
     * 保存成功后调用：仅当版本仍为保存时的版本才清除脏标记，
     * 避免保存期间产生的新写入被误标为已持久化。
     */
    public synchronized boolean clearDirtyIfVersion(long expectedVersion) {
        if (version == expectedVersion) {
            dirty = false;
            return true;
        }
        return false;
    }

    /** 生成一致性快照，供存储线程序列化，避免与主线程写入并发 */
    public synchronized PlayerData snapshot() {
        PlayerData copy = new PlayerData(uuid);
        copy.damageMap.putAll(damageMap);
        copy.killMap.putAll(killMap);
        copy.lastClaimMap.putAll(lastClaimMap);
        copy.claimCountMap.putAll(claimCountMap);
        copy.dailyClaimCountMap.putAll(dailyClaimCountMap);
        copy.dailyClaimStartMap.putAll(dailyClaimStartMap);
        copy.weeklyClaimCountMap.putAll(weeklyClaimCountMap);
        copy.weeklyClaimStartMap.putAll(weeklyClaimStartMap);
        copy.onlineMinutes = onlineMinutes;
        copy.version = version;
        return copy;
    }

    private void markDirty() {
        dirty = true;
        version++;
    }

    // ================================================================
    //  复合键工具
    // ================================================================

    /** 由世界与怪物 ID 构造复合键 */
    public static String keyOf(String world, String monsterId) {
        return (world == null ? "" : world) + SEP + (monsterId == null ? "" : monsterId);
    }

    /** 拆分复合键为世界与怪物 ID */
    public static String[] splitKey(String key) {
        int idx = key.indexOf(SEP);
        if (idx < 0) {
            return new String[]{"", key};
        }
        return new String[]{key.substring(0, idx), key.substring(idx + SEP.length())};
    }

    // ================================================================
    //  伤害 / 击杀（世界隔离）
    // ================================================================

    public synchronized double getDamage(String world, String monsterId) {
        return damageMap.getOrDefault(keyOf(world, monsterId), 0.0);
    }

    public synchronized int getKills(String world, String monsterId) {
        return killMap.getOrDefault(keyOf(world, monsterId), 0);
    }

    public synchronized void addDamage(String world, String monsterId, double amount) {
        String key = keyOf(world, monsterId);
        damageMap.put(key, damageMap.getOrDefault(key, 0.0) + amount);
        markDirty();
    }

    public synchronized void addKill(String world, String monsterId, int count) {
        String key = keyOf(world, monsterId);
        killMap.put(key, killMap.getOrDefault(key, 0) + count);
        markDirty();
    }

    /** 进度总量（跨所有世界与怪物），供排行榜聚合 */
    public synchronized double getTotalDamage() {
        double sum = 0;
        for (double v : damageMap.values()) {
            sum += v;
        }
        return sum;
    }

    public synchronized int getTotalKills() {
        int sum = 0;
        for (int v : killMap.values()) {
            sum += v;
        }
        return sum;
    }

    // ================================================================
    //  领取记录
    // ================================================================

    public synchronized long getLastClaim(String rewardId) {
        return lastClaimMap.getOrDefault(rewardId, 0L);
    }

    public synchronized int getClaimCount(String rewardId) {
        return claimCountMap.getOrDefault(rewardId, 0);
    }

    public synchronized void setClaimed(String rewardId, long epochSeconds) {
        lastClaimMap.put(rewardId, epochSeconds);
        claimCountMap.put(rewardId, getClaimCount(rewardId) + 1);
        markDirty();
    }

    // ================================================================
    //  供存储层加载使用
    // ================================================================

    public synchronized void setDamage(String world, String monsterId, double v) {
        damageMap.put(keyOf(world, monsterId), v);
    }

    public synchronized void setKill(String world, String monsterId, int v) {
        killMap.put(keyOf(world, monsterId), v);
    }

    public synchronized void setLastClaim(String rewardId, long v) {
        lastClaimMap.put(rewardId, v);
    }

    public synchronized void setClaimCount(String rewardId, int v) {
        claimCountMap.put(rewardId, v);
    }

    // ================================================================
    //  累计在线时长（分钟）
    // ================================================================

    public synchronized long getOnlineMinutes() {
        return onlineMinutes;
    }

    public synchronized void setOnlineMinutes(long v) {
        onlineMinutes = v;
        markDirty();
    }

    public synchronized void addOnlineMinutes(long v) {
        if (v > 0) {
            onlineMinutes += v;
            markDirty();
        }
    }

    // ================================================================
    //  每日 / 每周领取计数
    // ================================================================

    /** 当前周期内已领取次数（自动处理跨天/跨周重置） */
    public synchronized int getDailyClaimed(String rewardId) {
        long now = System.currentTimeMillis() / 1000;
        long todayStart = dayStartEpoch(now);
        if (dailyClaimStartMap.getOrDefault(rewardId, 0L) < todayStart) {
            dailyClaimCountMap.put(rewardId, 0);
            dailyClaimStartMap.put(rewardId, todayStart);
        }
        return dailyClaimCountMap.getOrDefault(rewardId, 0);
    }

    public synchronized int getWeeklyClaimed(String rewardId) {
        long now = System.currentTimeMillis() / 1000;
        long weekStart = weekStartEpoch(now);
        if (weeklyClaimStartMap.getOrDefault(rewardId, 0L) < weekStart) {
            weeklyClaimCountMap.put(rewardId, 0);
            weeklyClaimStartMap.put(rewardId, weekStart);
        }
        return weeklyClaimCountMap.getOrDefault(rewardId, 0);
    }

    /** 增加一次每日领取计数（必要时先重置周期） */
    public synchronized void addDailyClaim(String rewardId, long nowEpochSeconds) {
        long todayStart = dayStartEpoch(nowEpochSeconds);
        if (dailyClaimStartMap.getOrDefault(rewardId, 0L) < todayStart) {
            dailyClaimCountMap.put(rewardId, 0);
            dailyClaimStartMap.put(rewardId, todayStart);
        }
        dailyClaimCountMap.put(rewardId, dailyClaimCountMap.getOrDefault(rewardId, 0) + 1);
        markDirty();
    }

    public synchronized void addWeeklyClaim(String rewardId, long nowEpochSeconds) {
        long weekStart = weekStartEpoch(nowEpochSeconds);
        if (weeklyClaimStartMap.getOrDefault(rewardId, 0L) < weekStart) {
            weeklyClaimCountMap.put(rewardId, 0);
            weeklyClaimStartMap.put(rewardId, weekStart);
        }
        weeklyClaimCountMap.put(rewardId, weeklyClaimCountMap.getOrDefault(rewardId, 0) + 1);
        markDirty();
    }

    public synchronized void setDailyClaim(String rewardId, int count, long start) {
        dailyClaimCountMap.put(rewardId, count);
        dailyClaimStartMap.put(rewardId, start);
    }

    public synchronized void setWeeklyClaim(String rewardId, int count, long start) {
        weeklyClaimCountMap.put(rewardId, count);
        weeklyClaimStartMap.put(rewardId, start);
    }

    /** 当日 0 点（服务器本地时区）的 epoch 秒 */
    private static long dayStartEpoch(long nowSec) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(nowSec * 1000L);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis() / 1000L;
    }

    /** 本周一 0 点（服务器本地时区）的 epoch 秒 */
    private static long weekStartEpoch(long nowSec) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(nowSec * 1000L);
        int dow = cal.get(java.util.Calendar.DAY_OF_WEEK); // 1=周日 .. 7=周六
        int daysSinceMonday = (dow + 5) % 7;                // 周一=0
        cal.add(java.util.Calendar.DATE, -daysSinceMonday);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis() / 1000L;
    }

    // ================================================================
    //  重置
    // ================================================================

    /** 清空全部进度（伤害/击杀），保留领取记录。赛季重置（保留领取）时调用 */
    public synchronized void clearProgress() {
        damageMap.clear();
        killMap.clear();
        markDirty();
    }

    /** 清空全部数据（进度 + 领取）。赛季完全重置时调用 */
    public synchronized void clearAll() {
        damageMap.clear();
        killMap.clear();
        lastClaimMap.clear();
        claimCountMap.clear();
        dailyClaimCountMap.clear();
        dailyClaimStartMap.clear();
        weeklyClaimCountMap.clear();
        weeklyClaimStartMap.clear();
        markDirty();
    }

    public synchronized Map<String, Double> getDamageMap() {
        return damageMap;
    }

    public synchronized Map<String, Integer> getKillMap() {
        return killMap;
    }

    public synchronized Map<String, Long> getLastClaimMap() {
        return lastClaimMap;
    }

    public synchronized Map<String, Integer> getClaimCountMap() {
        return claimCountMap;
    }

    public synchronized Map<String, Integer> getDailyClaimCountMap() {
        return dailyClaimCountMap;
    }

    public synchronized Map<String, Long> getDailyClaimStartMap() {
        return dailyClaimStartMap;
    }

    public synchronized Map<String, Integer> getWeeklyClaimCountMap() {
        return weeklyClaimCountMap;
    }

    public synchronized Map<String, Long> getWeeklyClaimStartMap() {
        return weeklyClaimStartMap;
    }
}
