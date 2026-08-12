package soys.soysmyloot.storage;

import java.util.UUID;

/**
 * 排行榜单行：某 owner（玩家或队伍）的累计伤害与击杀数。
 * rank 由 {@link soys.soysmyloot.leaderboard.LeaderboardManager} 在排序后回填。
 */
public class LeaderboardRow {

    private final UUID owner;
    private final double totalDamage;
    private final int totalKills;
    private int rank;

    public LeaderboardRow(UUID owner, double totalDamage, int totalKills) {
        this.owner = owner;
        this.totalDamage = totalDamage;
        this.totalKills = totalKills;
        this.rank = 0;
    }

    public UUID getOwner() {
        return owner;
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }
}
