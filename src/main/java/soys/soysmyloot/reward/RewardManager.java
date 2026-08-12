package soys.soysmyloot.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.config.MessageManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 奖励管理器：条件校验、冷却判断与奖励发放。
 */
public class RewardManager {

    private final SOYSMyLoot plugin;
    private final MessageManager messageManager;
    private final DataManager dataManager;

    public RewardManager(SOYSMyLoot plugin, MessageManager messageManager, DataManager dataManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.dataManager = dataManager;
    }

    /** 是否满足全部条件 */
    public boolean meetsConditions(Player player, RewardEntry reward) {
        PlayerData data = dataManager.getData(player.getUniqueId());
        for (RewardEntry.Condition c : reward.getConditions()) {
            double have;
            switch (c.getType()) {
                case DAMAGE:
                    have = data.getDamage(c.getMonsterId());
                    break;
                case KILLS:
                    have = data.getKills(c.getMonsterId());
                    break;
                default:
                    have = 0;
            }
            if (have < c.getAmount()) {
                return false;
            }
        }
        return true;
    }

    /** 是否已领取（仅针对不可重复奖励） */
    public boolean isClaimed(Player player, RewardEntry reward) {
        if (reward.isRepeatable()) {
            return false;
        }
        return dataManager.getData(player.getUniqueId()).getClaimCount(reward.getId()) > 0;
    }

    /** 冷却剩余秒数（0 = 无冷却或已过） */
    public long getCooldownRemaining(Player player, RewardEntry reward) {
        if (reward.getCooldown() <= 0) {
            return 0;
        }
        long last = dataManager.getData(player.getUniqueId()).getLastClaim(reward.getId());
        if (last <= 0) {
            return 0;
        }
        long passed = System.currentTimeMillis() / 1000 - last;
        if (passed >= reward.getCooldown()) {
            return 0;
        }
        return reward.getCooldown() - passed;
    }

    /** 是否可领取 */
    public boolean canClaim(Player player, RewardEntry reward) {
        if (isClaimed(player, reward)) {
            return false;
        }
        if (getCooldownRemaining(player, reward) > 0) {
            return false;
        }
        return meetsConditions(player, reward);
    }

    /** 发放奖励并记录领取，返回是否成功 */
    public boolean claim(Player player, RewardEntry reward) {
        if (!canClaim(player, reward)) {
            return false;
        }
        PlayerData data = dataManager.getData(player.getUniqueId());

        // 物品
        for (RewardEntry.ItemReward ir : reward.getItems()) {
            ItemStack item = buildItem(ir);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        // 控制台指令
        if (!reward.getCommands().isEmpty()) {
            for (String cmd : reward.getCommands()) {
                String finalCmd = cmd.replace("%player%", player.getName());
                Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
            }
        }

        // 金钱（需 Vault + 经济插件）
        if (reward.getMoney() > 0) {
            giveMoney(player, reward.getMoney());
        }

        // 点券（需 PlayerPoints）
        if (reward.getPoints() > 0) {
            givePoints(player, reward.getPoints());
        }

        // 消息
        for (String msg : reward.getMessages()) {
            player.sendMessage(Text.color(msg));
        }

        data.setClaimed(reward.getId(), System.currentTimeMillis() / 1000);
        return true;
    }

    private ItemStack buildItem(RewardEntry.ItemReward ir) {
        ItemStack item = new ItemStack(ir.getMaterial(), Math.max(1, ir.getAmount()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (ir.getName() != null && !ir.getName().isEmpty()) {
                meta.setDisplayName(Text.color(ir.getName()));
            }
            if (ir.getLore() != null && !ir.getLore().isEmpty()) {
                meta.setLore(ir.getLore().stream().map(Text::color).collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void giveMoney(Player player, double amount) {
        if (plugin.getEconomy() == null) {
            player.sendMessage(messageManager.get("warn-no-vault"));
            return;
        }
        plugin.getEconomy().depositPlayer(player, amount);
    }

    private void givePoints(Player player, double amount) {
        if (plugin.getPlayerPoints() == null) {
            player.sendMessage(messageManager.get("warn-no-playerpoints"));
            return;
        }
        plugin.getPlayerPoints().give(player.getUniqueId(), (int) amount);
    }
}
