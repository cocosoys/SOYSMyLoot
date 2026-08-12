package soys.soysmyloot.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.config.MessageManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.model.MonsterEntry;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.reward.RewardManager;
import soys.soysmyloot.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主指令处理器：myloot（别名 ml / loot）
 * 子命令：help / reload / list / claim / progress / info
 */
public class LootCommand implements CommandExecutor, TabCompleter {

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private final MessageManager messageManager;
    private final DataManager dataManager;
    private final RewardManager rewardManager;

    public LootCommand(SOYSMyLoot plugin, ConfigManager config, MessageManager messageManager,
                       DataManager dataManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
        this.dataManager = dataManager;
        this.rewardManager = rewardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(sender);
                break;
            case "reload":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "no-permission");
                    return true;
                }
                config.loadAll();
                Map<String, String> ph = new HashMap<>();
                ph.put("monsters", String.valueOf(config.getMonsters().size()));
                ph.put("rewards", String.valueOf(config.getRewards().size()));
                messageManager.send(sender, "config-reloaded", ph);
                break;
            case "list":
                if (!sender.hasPermission("soysmyloot.use")) {
                    messageManager.send(sender, "no-permission");
                    return true;
                }
                sendList(sender);
                break;
            case "claim":
                if (!sender.hasPermission("soysmyloot.use")) {
                    messageManager.send(sender, "no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    messageManager.send(sender, "player-only");
                    return true;
                }
                if (args.length < 2) {
                    messageManager.send(sender, "unknown-command");
                    return true;
                }
                claimReward((Player) sender, args[1]);
                break;
            case "progress":
                if (!sender.hasPermission("soysmyloot.use")) {
                    messageManager.send(sender, "no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    messageManager.send(sender, "player-only");
                    return true;
                }
                sendProgress((Player) sender, args.length >= 2 ? args[1] : null);
                break;
            case "info":
                sendInfo(sender);
                break;
            default:
                messageManager.send(sender, "unknown-command");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        messageManager.send(sender, "help-header");
        String[][] lines = {
                {"myloot", "显示本帮助"},
                {"myloot list", "列出全部奖励及状态"},
                {"myloot claim <id>", "领取指定奖励"},
                {"myloot progress [怪物]", "查看当前进度"},
                {"myloot info", "查看插件信息"},
                {"myloot reload", "重载配置（管理员）"}
        };
        for (String[] line : lines) {
            Map<String, String> ph = new HashMap<>();
            ph.put("command", line[0]);
            ph.put("description", line[1]);
            messageManager.send(sender, "help-line", ph);
        }
        messageManager.send(sender, "help-footer");
    }

    private void sendList(CommandSender sender) {
        if (config.getRewards().isEmpty()) {
            messageManager.send(sender, "list-empty");
            return;
        }
        messageManager.send(sender, "list-header");
        for (RewardEntry reward : config.getRewards().values()) {
            String coloredName = Text.color(reward.getName());
            Map<String, String> ph = new HashMap<>();
            ph.put("id", reward.getId());
            ph.put("name", coloredName);
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (rewardManager.canClaim(p, reward)) {
                    messageManager.send(sender, "list-item-available", ph);
                } else if (rewardManager.isClaimed(p, reward)) {
                    messageManager.send(sender, "list-item-claimed", ph);
                } else {
                    messageManager.send(sender, "list-item-locked", ph);
                }
            } else {
                sender.sendMessage(Text.color("&e" + reward.getId() + " &7- " + coloredName));
            }
        }
        messageManager.send(sender, "list-footer");
    }

    private void claimReward(Player player, String id) {
        RewardEntry reward = config.getReward(id);
        if (reward == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("id", id);
            messageManager.send(player, "reward-not-found", ph);
            return;
        }
        if (rewardManager.isClaimed(player, reward)) {
            messageManager.send(player, "reward-claimed");
            return;
        }
        long cd = rewardManager.getCooldownRemaining(player, reward);
        if (cd > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("time", String.valueOf(cd));
            messageManager.send(player, "reward-cooldown", ph);
            return;
        }
        if (!rewardManager.meetsConditions(player, reward)) {
            messageManager.send(player, "reward-conditions-not-met");
            return;
        }
        if (rewardManager.claim(player, reward)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("name", Text.color(reward.getName()));
            messageManager.send(player, "reward-claimed-success", ph);
        }
    }

    private void sendProgress(Player player, String monsterId) {
        PlayerData data = dataManager.getData(player.getUniqueId());
        messageManager.send(player, "progress-header");
        if (monsterId != null) {
            MonsterEntry m = config.getMonster(monsterId);
            if (m == null) {
                messageManager.send(player, "no-data");
                return;
            }
            messageManager.send(player, "progress-item", progressPlaceholders(m, data));
        } else {
            if (config.getMonsters().isEmpty()) {
                messageManager.send(player, "no-data");
                return;
            }
            for (MonsterEntry m : config.getMonsters().values()) {
                messageManager.send(player, "progress-item", progressPlaceholders(m, data));
            }
        }
        messageManager.send(player, "progress-footer");
    }

    private Map<String, String> progressPlaceholders(MonsterEntry m, PlayerData data) {
        Map<String, String> ph = new HashMap<>();
        ph.put("monster", m.getDisplayName());
        ph.put("damage", fmt(data.getDamage(m.getId())));
        ph.put("kills", String.valueOf(data.getKills(m.getId())));
        return ph;
    }

    private void sendInfo(CommandSender sender) {
        Map<String, String> ph = new HashMap<>();
        ph.put("version", plugin.getDescription().getVersion());
        messageManager.send(sender, "info", ph);

        Map<String, String> ph2 = new HashMap<>();
        ph2.put("db", config.getPrimaryBackendId());
        ph2.put("monsters", String.valueOf(config.getMonsters().size()));
        ph2.put("rewards", String.valueOf(config.getRewards().size()));
        messageManager.send(sender, "info-database", ph2);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String[] subs = {"help", "reload", "list", "claim", "progress", "info"};
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
            return result;
        }
        if (args.length == 2) {
            String a0 = args[0].toLowerCase();
            if (a0.equals("claim")) {
                for (String id : config.getRewards().keySet()) {
                    if (id.startsWith(args[1].toLowerCase())) {
                        result.add(id);
                    }
                }
            } else if (a0.equals("progress")) {
                for (MonsterEntry m : config.getMonsters().values()) {
                    if (m.getId().startsWith(args[1].toLowerCase())) {
                        result.add(m.getId());
                    }
                }
            }
        }
        return result;
    }

    private String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }
}
