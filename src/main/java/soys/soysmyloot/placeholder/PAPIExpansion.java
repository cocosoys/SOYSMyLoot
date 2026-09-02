package soys.soysmyloot.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.ScopeResolver;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.hook.LinkTeamHook;
import soys.soysmyloot.leaderboard.LeaderboardManager;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.reward.RewardManager;

import java.util.UUID;

/**
 * PlaceholderAPI 扩展：提供进度与奖励状态占位符。
 * 标识符：soysmyloot
 *   %soysmyloot_damage_<怪物ID>%      该怪物累计伤害
 *   %soysmyloot_kills_<怪物ID>%       该怪物击杀数
 *   %soysmyloot_claimed_<奖励ID>%     是否已领取（是/否，可重复奖励恒为 -）
 *   %soysmyloot_claimable%            当前可领取奖励数量
 *   %soysmyloot_claimed_total%        累计领取次数
 *   %soysmyloot_daily_<奖励ID>%       该奖励今日剩余可领取次数（- 表示无限制，? 表示未知）
 *   %soysmyloot_weekly_<奖励ID>%      该奖励本周剩余可领取次数
 *   %soysmyloot_total_damage%         本人/队伍跨世界累计伤害
 *   %soysmyloot_total_kills%          本人/队伍跨世界累计击杀
 *   %soysmyloot_rank%                 默认排序（配置）下的名次
 *   %soysmyloot_rank_damage%          按伤害的名次
 *   %soysmyloot_rank_kills%           按击杀的名次
 *   %soysmyloot_team%                 所属队伍名（无/非队伍模式为空）
 *   %soysmyloot_top_damage_<n>%       伤害榜第 n 名归属名
 *   %soysmyloot_top_kills_<n>%        击杀榜第 n 名归属名
 */
public class PAPIExpansion extends PlaceholderExpansion {

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private final DataManager dataManager;
    private final RewardManager rewardManager;
    private final ScopeResolver scopeResolver;
    private final LeaderboardManager leaderboardManager;

    public PAPIExpansion(SOYSMyLoot plugin, ConfigManager config, DataManager dataManager,
                         RewardManager rewardManager, ScopeResolver scopeResolver, LeaderboardManager leaderboardManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.rewardManager = rewardManager;
        this.scopeResolver = scopeResolver;
        this.leaderboardManager = leaderboardManager;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "soysmyloot";
    }

    @Override
    public String getAuthor() {
        return "Soys";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }
        UUID owner = scopeResolver.resolveOwner(player);
        String world = scopeResolver.resolveWorld(player.getWorld().getName());
        PlayerData data = dataManager.getData(owner);

        if (params.startsWith("damage_")) {
            return fmt(data.getDamage(world, params.substring("damage_".length())));
        }
        if (params.startsWith("kills_")) {
            return String.valueOf(data.getKills(world, params.substring("kills_".length())));
        }
        if (params.startsWith("claimed_")) {
            String rid = params.substring("claimed_".length());
            RewardEntry reward = config.getReward(rid);
            if (reward == null) {
                return "?";
            }
            if (reward.isRepeatable()) {
                return "-";
            }
            return data.getClaimCount(rid) > 0 ? "是" : "否";
        }
        if (params.equals("claimable")) {
            int count = 0;
            for (RewardEntry r : config.getRewards().values()) {
                if (rewardManager.canClaim(player, r)) {
                    count++;
                }
            }
            return String.valueOf(count);
        }
        if (params.equals("claimed_total")) {
            int count = 0;
            for (RewardEntry r : config.getRewards().values()) {
                count += data.getClaimCount(r.getId());
            }
            return String.valueOf(count);
        }

        // ---- 每日 / 每周剩余可领取次数 ----
        if (params.startsWith("daily_")) {
            String rid = params.substring("daily_".length());
            RewardEntry r = config.getReward(rid);
            if (r == null) {
                return "?";
            }
            if (r.getDailyLimit() <= 0) {
                return "-";
            }
            return String.valueOf(Math.max(0, r.getDailyLimit() - rewardManager.getDailyClaimed(player, r)));
        }
        if (params.startsWith("weekly_")) {
            String rid = params.substring("weekly_".length());
            RewardEntry r = config.getReward(rid);
            if (r == null) {
                return "?";
            }
            if (r.getWeeklyLimit() <= 0) {
                return "-";
            }
            return String.valueOf(Math.max(0, r.getWeeklyLimit() - rewardManager.getWeeklyClaimed(player, r)));
        }

        // ---- 跨世界累计 ----
        if (params.equals("total_damage")) {
            return fmt(data.getTotalDamage());
        }
        if (params.equals("total_kills")) {
            return String.valueOf(data.getTotalKills());
        }

        // ---- 排行榜名次 ----
        if (params.equals("rank")) {
            return String.valueOf(leaderboardManager.getRank(owner));
        }
        if (params.equals("rank_damage")) {
            return String.valueOf(leaderboardManager.getRank(owner, true));
        }
        if (params.equals("rank_kills")) {
            return String.valueOf(leaderboardManager.getRank(owner, false));
        }

        // ---- 队伍名 ----
        if (params.equals("team")) {
            if (!scopeResolver.isTeamMode()) {
                return "";
            }
            String name = LinkTeamHook.getTeamName(scopeResolver.resolveOwner(player));
            return name == null ? "" : name;
        }

        // ---- 榜单名次归属名 ----
        if (params.startsWith("top_damage_")) {
            return topName(params.substring("top_damage_".length()), true);
        }
        if (params.startsWith("top_kills_")) {
            return topName(params.substring("top_kills_".length()), false);
        }

        return null;
    }

    private String topName(String nStr, boolean byDamage) {
        int n = 0;
        try {
            n = Integer.parseInt(nStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (n < 1) {
            return null;
        }
        soys.soysmyloot.storage.LeaderboardRow row = leaderboardManager.getRow(n, byDamage);
        if (row == null) {
            return "-";
        }
        String teamName = LinkTeamHook.getTeamName(row.getOwner());
        if (teamName != null && !teamName.isEmpty()) {
            return teamName;
        }
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(row.getOwner());
        String name = op.getName();
        return name != null ? name : row.getOwner().toString();
    }

    private String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }
}
