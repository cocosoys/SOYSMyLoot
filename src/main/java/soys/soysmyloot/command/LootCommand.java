package soys.soysmyloot.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.ScopeResolver;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.config.MessageManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.hook.LinkTeamHook;
import soys.soysmyloot.leaderboard.LeaderboardManager;
import soys.soysmyloot.model.MonsterEntry;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.reward.RewardManager;
import soys.soysmyloot.season.SeasonManager;
import soys.soysmyloot.storage.LeaderboardRow;
import soys.soysmyloot.storage.StorageManager;
import soys.soysmyloot.storage.StorageType;
import soys.soysmyloot.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 主指令处理器：myloot（别名 ml / loot）
 * 子命令：help / reload / list / claim / progress / info
 *        top（排行榜） / export（导出） / import（导入） / migrate（迁移） / reset（重置）
 */
public class LootCommand implements CommandExecutor, TabCompleter {

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private final MessageManager messageManager;
    private final DataManager dataManager;
    private final RewardManager rewardManager;
    private final StorageManager storageManager;
    private final ScopeResolver scopeResolver;
    private final LeaderboardManager leaderboardManager;
    private final SeasonManager seasonManager;

    public LootCommand(SOYSMyLoot plugin, ConfigManager config, MessageManager messageManager,
                       DataManager dataManager, RewardManager rewardManager, StorageManager storageManager,
                       ScopeResolver scopeResolver, LeaderboardManager leaderboardManager, SeasonManager seasonManager) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
        this.dataManager = dataManager;
        this.rewardManager = rewardManager;
        this.storageManager = storageManager;
        this.scopeResolver = scopeResolver;
        this.leaderboardManager = leaderboardManager;
        this.seasonManager = seasonManager;
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
                scopeResolver.reload();
                leaderboardManager.reload();
                if (plugin.getServer() != null) {
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, leaderboardManager::refresh);
                }
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
            case "top":
                if (!sender.hasPermission("soysmyloot.use")) {
                    messageManager.send(sender, "no-permission");
                    return true;
                }
                sendTop(sender, args.length >= 2 ? args[1] : null);
                break;
            case "export":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "no-permission");
                    return true;
                }
                doExport(sender, args.length >= 2 ? args[1] : null);
                break;
            case "import":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    messageManager.send(sender, "unknown-command");
                    return true;
                }
                doImport(sender, args[1]);
                break;
            case "migrate":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    messageManager.send(sender, "unknown-command");
                    return true;
                }
                doMigrate(sender, args[1], args[2]);
                break;
            case "reset":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "no-permission");
                    return true;
                }
                doReset(sender, args.length >= 2 ? args[1] : null);
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
                {"myloot top [damage|kills]", "查看排行榜"},
                {"myloot info", "查看插件信息"},
                {"myloot export [文件]", "导出全部数据为备份"},
                {"myloot import <文件>", "从备份导入数据"},
                {"myloot migrate <from> <to>", "在存储后端间迁移数据"},
                {"myloot reset <keep|full>", "清空进度/全部数据（管理员）"},
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
        for (RewardEntry reward : sortedRewards()) {
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

    /** 奖励按多阶段分组（铜/银/金优先）再按 ID 排序，便于阶梯式展示 */
    private List<RewardEntry> sortedRewards() {
        List<RewardEntry> list = new ArrayList<>(config.getRewards().values());
        list.sort((a, b) -> {
            int ra = stageRank(a.getStage());
            int rb = stageRank(b.getStage());
            if (ra != rb) {
                return Integer.compare(ra, rb);
            }
            return a.getId().compareToIgnoreCase(b.getId());
        });
        return list;
    }

    private int stageRank(String stage) {
        if (stage == null || stage.isEmpty()) {
            return 999;
        }
        String s = stage.toLowerCase();
        if (s.contains("bronze") || s.contains("铜")) {
            return 1;
        }
        if (s.contains("silver") || s.contains("银")) {
            return 2;
        }
        if (s.contains("gold") || s.contains("金")) {
            return 3;
        }
        return 50;
    }

    private void claimReward(Player player, String id) {
        RewardEntry reward = config.getReward(id);
        if (reward == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("id", id);
            messageManager.send(player, "reward-not-found", ph);
            return;
        }
        if (!reward.isPartial() && rewardManager.isClaimed(player, reward)) {
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
        if (reward.getDailyLimit() > 0 && rewardManager.getDailyClaimed(player, reward) >= reward.getDailyLimit()) {
            messageManager.send(player, "reward-daily-limit");
            return;
        }
        if (reward.getWeeklyLimit() > 0 && rewardManager.getWeeklyClaimed(player, reward) >= reward.getWeeklyLimit()) {
            messageManager.send(player, "reward-weekly-limit");
            return;
        }
        if (!rewardManager.canClaim(player, reward)) {
            if (reward.isPartial() && rewardManager.bankedUnits(player, reward) < 1) {
                messageManager.send(player, "reward-partial-not-enough");
            } else {
                messageManager.send(player, "reward-conditions-not-met");
            }
            return;
        }
        if (rewardManager.claim(player, reward)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("name", Text.color(reward.getName()));
            messageManager.send(player, "reward-claimed-success", ph);
        }
    }

    private void sendProgress(Player player, String monsterId) {
        UUID owner = scopeResolver.resolveOwner(player);
        String world = scopeResolver.resolveWorld(player.getWorld().getName());
        PlayerData data = dataManager.getData(owner);
        messageManager.send(player, "progress-header");
        if (monsterId != null) {
            MonsterEntry m = config.getMonster(monsterId);
            if (m == null) {
                messageManager.send(player, "no-data");
                return;
            }
            messageManager.send(player, "progress-item", progressPlaceholders(m, data, world));
        } else {
            if (config.getMonsters().isEmpty()) {
                messageManager.send(player, "no-data");
                return;
            }
            for (MonsterEntry m : config.getMonsters().values()) {
                messageManager.send(player, "progress-item", progressPlaceholders(m, data, world));
            }
        }
        messageManager.send(player, "progress-footer");
    }

    private Map<String, String> progressPlaceholders(MonsterEntry m, PlayerData data, String world) {
        Map<String, String> ph = new HashMap<>();
        ph.put("monster", m.getDisplayName());
        ph.put("damage", fmt(data.getDamage(world, m.getId())));
        ph.put("kills", String.valueOf(data.getKills(world, m.getId())));
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

        // 进度归属 / 世界隔离 / 赛季信息
        sender.sendMessage(Text.color("&7进度归属: &e" + config.getScopeId()
                + " &7| 世界隔离: &e" + config.isWorldIsolation()
                + " &7| 排行榜: &e" + config.getLeaderboardLimit() + " 名"));
        if (config.isSeasonAutoReset()) {
            long next = seasonManager.getNextResetEpoch();
            sender.sendMessage(Text.color("&7赛季自动重置: &a开启 &7| 周期 &e" + config.getSeasonPeriodHours()
                    + "h &7| 下次重置: &e" + (next > 0 ? String.valueOf(next) : "未知")));
        } else {
            sender.sendMessage(Text.color("&7赛季自动重置: &c关闭"));
        }
    }

    // ================================================================
    //  排行榜
    // ================================================================

    private void sendTop(CommandSender sender, String modeArg) {
        boolean byDamage;
        if (modeArg != null && "kills".equalsIgnoreCase(modeArg)) {
            byDamage = false;
        } else {
            byDamage = config.isLeaderboardByDamage();
            if (modeArg != null && !"damage".equalsIgnoreCase(modeArg)) {
                // 非法参数，提示可用项
                sender.sendMessage(Text.color("&7用法：&e/myloot top [damage|kills]"));
            }
        }

        List<LeaderboardRow> rows = byDamage ? leaderboardManager.getRowsByDamage() : leaderboardManager.getRowsByKills();
        messageManager.send(sender, byDamage ? "leaderboard-header-damage" : "leaderboard-header-kills");
        if (rows.isEmpty()) {
            messageManager.send(sender, "leaderboard-empty");
            return;
        }

        Map<UUID, String> teamNames = LinkTeamHook.getTeamNameIndex();
        for (LeaderboardRow row : rows) {
            Map<String, String> ph = new HashMap<>();
            ph.put("rank", String.valueOf(row.getRank()));
            ph.put("owner", resolveOwnerName(row.getOwner(), teamNames));
            ph.put("damage", fmt(row.getTotalDamage()));
            ph.put("kills", String.valueOf(row.getTotalKills()));
            messageManager.send(sender, "leaderboard-line", ph);
        }

        // 命令执行者自身的名次（仅玩家）
        if (sender instanceof Player) {
            Player p = (Player) sender;
            UUID owner = scopeResolver.resolveOwner(p);
            int rank = leaderboardManager.getRank(owner, byDamage);
            PlayerData data = dataManager.getData(owner);
            if (rank > 0) {
                Map<String, String> ph = new HashMap<>();
                ph.put("rank", String.valueOf(rank));
                ph.put("damage", fmt(data.getTotalDamage()));
                ph.put("kills", String.valueOf(data.getTotalKills()));
                messageManager.send(sender, byDamage ? "leaderboard-self-damage" : "leaderboard-self-kills", ph);
            } else {
                messageManager.send(sender, "leaderboard-unranked");
            }
        }
    }

    private String resolveOwnerName(UUID owner, Map<UUID, String> teamNames) {
        String teamName = teamNames.get(owner);
        if (teamName != null && !teamName.isEmpty()) {
            return "&d[" + teamName + "&d]";
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        String name = op.getName();
        return name != null ? name : owner.toString();
    }

    // ================================================================
    //  导出 / 导入 / 迁移 / 重置
    // ================================================================

    private void doExport(CommandSender sender, String fileName) {
        File dir = new File(plugin.getDataFolder(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            messageManager.send(sender, "export-fail", ph("reason", "无法创建导出目录"));
            return;
        }
        if (fileName == null || fileName.isEmpty()) {
            fileName = "export-" + System.currentTimeMillis() + ".yml";
        }
        if (!fileName.endsWith(".yml")) {
            fileName += ".yml";
        }
        // 仅允许文件名（防止路径穿越）
        File file = new File(dir, new File(fileName).getName());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int count = storageManager.exportAll(file);
                messageManager.send(sender, "export-success",
                        ph2("count", String.valueOf(count), "file", file.getName()));
            } catch (Exception e) {
                messageManager.send(sender, "export-fail", ph("reason", e.getMessage()));
            }
        });
    }

    private void doImport(CommandSender sender, String fileName) {
        if (!fileName.endsWith(".yml")) {
            fileName += ".yml";
        }
        File dir = new File(plugin.getDataFolder(), "exports");
        File file = new File(dir, new File(fileName).getName());
        if (!file.exists()) {
            // 也允许直接指定数据目录下的文件
            File alt = new File(plugin.getDataFolder(), new File(fileName).getName());
            if (alt.exists()) {
                file = alt;
            }
        }
        final File targetFile = file;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int count = storageManager.importAll(targetFile);
                messageManager.send(sender, "import-success",
                        ph2("count", String.valueOf(count), "file", targetFile.getName()));
                leaderboardManager.refresh();
            } catch (Exception e) {
                messageManager.send(sender, "import-fail", ph("reason", e.getMessage()));
            }
        });
    }

    private void doMigrate(CommandSender sender, String fromArg, String toArg) {
        StorageType from = StorageType.fromId(fromArg);
        StorageType to = StorageType.fromId(toArg);
        if (from == null || to == null) {
            messageManager.send(sender, "migrate-unknown", ph("from", fromArg));
            return;
        }
        if (from == to) {
            messageManager.send(sender, "migrate-fail", ph("reason", "来源与目标不能相同"));
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int count = storageManager.migrate(from, to, true);
                messageManager.send(sender, "migrate-success",
                        ph3("count", String.valueOf(count), "from", from.getId(), "to", to.getId()));
                leaderboardManager.refresh();
            } catch (Exception e) {
                messageManager.send(sender, "migrate-fail", ph("reason", e.getMessage()));
            }
        });
    }

    private void doReset(CommandSender sender, String modeArg) {
        if (modeArg == null) {
            messageManager.send(sender, "reset-usage");
            return;
        }
        boolean keepClaims;
        if ("keep".equalsIgnoreCase(modeArg)) {
            keepClaims = true;
        } else if ("full".equalsIgnoreCase(modeArg)) {
            keepClaims = false;
        } else {
            messageManager.send(sender, "reset-usage");
            return;
        }
        seasonManager.resetProgress(keepClaims, true);
        messageManager.send(sender, keepClaims ? "reset-progress" : "reset-full");
    }

    private Map<String, String> ph(String k1, String v1) {
        Map<String, String> m = new HashMap<>();
        m.put(k1, v1);
        return m;
    }

    private Map<String, String> ph2(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private Map<String, String> ph3(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, String> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String[] subs = {"help", "reload", "list", "claim", "progress", "info", "top", "export", "import", "migrate", "reset"};
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
            } else if (a0.equals("top")) {
                for (String s : new String[]{"damage", "kills"}) {
                    if (s.startsWith(args[1].toLowerCase())) {
                        result.add(s);
                    }
                }
            } else if (a0.equals("migrate")) {
                for (StorageType t : StorageType.values()) {
                    if (t.getId().startsWith(args[1].toLowerCase())) {
                        result.add(t.getId());
                    }
                }
            } else if (a0.equals("reset")) {
                for (String s : new String[]{"keep", "full"}) {
                    if (s.startsWith(args[1].toLowerCase())) {
                        result.add(s);
                    }
                }
            } else if (a0.equals("import")) {
                File dir = new File(plugin.getDataFolder(), "exports");
                if (dir.exists()) {
                    File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().startsWith(args[1].toLowerCase())) {
                                result.add(f.getName());
                            }
                        }
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("migrate")) {
            for (StorageType t : StorageType.values()) {
                if (t.getId().startsWith(args[2].toLowerCase())) {
                    result.add(t.getId());
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
