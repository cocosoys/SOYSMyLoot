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
    private boolean dirty = false;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /** 清除脏标记（保存成功后由存储层调用） */
    public void clearDirty() {
        dirty = false;
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

    public double getDamage(String world, String monsterId) {
        return damageMap.getOrDefault(keyOf(world, monsterId), 0.0);
    }

    public int getKills(String world, String monsterId) {
        return killMap.getOrDefault(keyOf(world, monsterId), 0);
    }

    public void addDamage(String world, String monsterId, double amount) {
        String key = keyOf(world, monsterId);
        damageMap.put(key, damageMap.getOrDefault(key, 0.0) + amount);
        dirty = true;
    }

    public void addKill(String world, String monsterId, int count) {
        String key = keyOf(world, monsterId);
        killMap.put(key, killMap.getOrDefault(key, 0) + count);
        dirty = true;
    }

    /** 进度总量（跨所有世界与怪物），供排行榜聚合 */
    public double getTotalDamage() {
        double sum = 0;
        for (double v : damageMap.values()) {
            sum += v;
        }
        return sum;
    }

    public int getTotalKills() {
        int sum = 0;
        for (int v : killMap.values()) {
            sum += v;
        }
        return sum;
    }

    // ================================================================
    //  领取记录
    // ================================================================

    public long getLastClaim(String rewardId) {
        return lastClaimMap.getOrDefault(rewardId, 0L);
    }

    public int getClaimCount(String rewardId) {
        return claimCountMap.getOrDefault(rewardId, 0);
    }

    public void setClaimed(String rewardId, long epochSeconds) {
        lastClaimMap.put(rewardId, epochSeconds);
        claimCountMap.put(rewardId, getClaimCount(rewardId) + 1);
        dirty = true;
    }

    // ================================================================
    //  供存储层加载使用
    // ================================================================

    public void setDamage(String world, String monsterId, double v) {
        damageMap.put(keyOf(world, monsterId), v);
    }

    public void setKill(String world, String monsterId, int v) {
        killMap.put(keyOf(world, monsterId), v);
    }

    public void setLastClaim(String rewardId, long v) {
        lastClaimMap.put(rewardId, v);
    }

    public void setClaimCount(String rewardId, int v) {
        claimCountMap.put(rewardId, v);
    }

    // ================================================================
    //  累计在线时长（分钟）
    // ================================================================

    public long getOnlineMinutes() {
        return onlineMinutes;
    }

    public void setOnlineMinutes(long v) {
        onlineMinutes = v;
        dirty = true;
    }

    public void addOnlineMinutes(long v) {
        if (v > 0) {
            onlineMinutes += v;
            dirty = true;
        }
    }

    // ================================================================
    //  每日 / 每周领取计数
    // ================================================================

    /** 当前周期内已领取次数（自动处理跨天/跨周重置） */
    public int getDailyClaimed(String rewardId) {
        long now = System.currentTimeMillis() / 1000;
        long todayStart = dayStartEpoch(now);
        if (dailyClaimStartMap.getOrDefault(rewardId, 0L) < todayStart) {
            dailyClaimCountMap.put(rewardId, 0);
            dailyClaimStartMap.put(rewardId, todayStart);
        }
        return dailyClaimCountMap.getOrDefault(rewardId, 0);
    }

    public int getWeeklyClaimed(String rewardId) {
        long now = System.currentTimeMillis() / 1000;
        long weekStart = weekStartEpoch(now);
        if (weeklyClaimStartMap.getOrDefault(rewardId, 0L) < weekStart) {
            weeklyClaimCountMap.put(rewardId, 0);
            weeklyClaimStartMap.put(rewardId, weekStart);
        }
        return weeklyClaimCountMap.getOrDefault(rewardId, 0);
    }

    /** 增加一次每日领取计数（必要时先重置周期） */
    public void addDailyClaim(String rewardId, long nowEpochSeconds) {
        long todayStart = dayStartEpoch(nowEpochSeconds);
        if (dailyClaimStartMap.getOrDefault(rewardId, 0L) < todayStart) {
            dailyClaimCountMap.put(rewardId, 0);
            dailyClaimStartMap.put(rewardId, todayStart);
        }
        dailyClaimCountMap.put(rewardId, dailyClaimCountMap.getOrDefault(rewardId, 0) + 1);
        dirty = true;
    }

    public void addWeeklyClaim(String rewardId, long nowEpochSeconds) {
        long weekStart = weekStartEpoch(nowEpochSeconds);
        if (weeklyClaimStartMap.getOrDefault(rewardId, 0L) < weekStart) {
            weeklyClaimCountMap.put(rewardId, 0);
            weeklyClaimStartMap.put(rewardId, weekStart);
        }
        weeklyClaimCountMap.put(rewardId, weeklyClaimCountMap.getOrDefault(rewardId, 0) + 1);
        dirty = true;
    }

    public void setDailyClaim(String rewardId, int count, long start) {
        dailyClaimCountMap.put(rewardId, count);
        dailyClaimStartMap.put(rewardId, start);
    }

    public void setWeeklyClaim(String rewardId, int count, long start) {
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
    public void clearProgress() {
        damageMap.clear();
        killMap.clear();
        dirty = true;
    }

    /** 清空全部数据（进度 + 领取）。赛季完全重置时调用 */
    public void clearAll() {
        damageMap.clear();
        killMap.clear();
        lastClaimMap.clear();
        claimCountMap.clear();
        dailyClaimCountMap.clear();
        dailyClaimStartMap.clear();
        weeklyClaimCountMap.clear();
        weeklyClaimStartMap.clear();
        dirty = true;
    }

    public Map<String, Double> getDamageMap() {
        return damageMap;
    }

    public Map<String, Integer> getKillMap() {
        return killMap;
    }

    public Map<String, Long> getLastClaimMap() {
        return lastClaimMap;
    }

    public Map<String, Integer> getClaimCountMap() {
        return claimCountMap;
    }

    public Map<String, Integer> getDailyClaimCountMap() {
        return dailyClaimCountMap;
    }

    public Map<String, Long> getDailyClaimStartMap() {
        return dailyClaimStartMap;
    }

    public Map<String, Integer> getWeeklyClaimCountMap() {
        return weeklyClaimCountMap;
    }

    public Map<String, Long> getWeeklyClaimStartMap() {
        return weeklyClaimStartMap;
    }
}
