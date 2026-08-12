package soys.soysmyloot.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.reward.RewardManager;

/**
 * PlaceholderAPI 扩展：提供进度与奖励状态占位符。
 * 标识符：soysmyloot
 *   %soysmyloot_damage_<怪物ID>%      该怪物累计伤害
 *   %soysmyloot_kills_<怪物ID>%       该怪物击杀数
 *   %soysmyloot_claimed_<奖励ID>%     是否已领取（是/否，可重复奖励恒为 -）
 *   %soysmyloot_claimable%            当前可领取奖励数量
 *   %soysmyloot_claimed_total%        累计领取次数
 */
public class PAPIExpansion extends PlaceholderExpansion {

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private final DataManager dataManager;
    private final RewardManager rewardManager;

    public PAPIExpansion(SOYSMyLoot plugin, ConfigManager config, DataManager dataManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.rewardManager = rewardManager;
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
        PlayerData data = dataManager.getData(player.getUniqueId());

        if (params.startsWith("damage_")) {
            return fmt(data.getDamage(params.substring("damage_".length())));
        }
        if (params.startsWith("kills_")) {
            return String.valueOf(data.getKills(params.substring("kills_".length())));
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
        return null;
    }

    private String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }
}
