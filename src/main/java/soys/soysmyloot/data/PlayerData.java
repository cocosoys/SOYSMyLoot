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
}
