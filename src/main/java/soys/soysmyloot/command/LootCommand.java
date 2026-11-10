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
import soys.soysmyloot.gui.RewardGui;
import soys.soysmyloot.hook.LinkTeamHook;
import soys.soysmyloot.hook.MythicMobsHook;
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
import java.util.List;
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
    private final RewardGui rewardGui;

    public LootCommand(SOYSMyLoot plugin, ConfigManager config, MessageManager messageManager,
                       DataManager dataManager, RewardManager rewardManager, StorageManager storageManager,
                       ScopeResolver scopeResolver, LeaderboardManager leaderboardManager, SeasonManager seasonManager,
                       RewardGui rewardGui) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
        this.dataManager = dataManager;
        this.rewardManager = rewardManager;
        this.storageManager = storageManager;
        this.scopeResolver = scopeResolver;
        this.leaderboardManager = leaderboardManager;
        this.seasonManager = seasonManager;
        this.rewardGui = rewardGui;
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
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                doReload(sender, args);
                break;
            case "list":
                if (!sender.hasPermission("soysmyloot.use")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                sendList(sender);
                break;
            case "claim":
                if (!sender.hasPermission("soysmyloot.use")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    messageManager.send(sender, "soysmyloot.command.common.player-only");
                    return true;
                }
                if (args.length < 2) {
                    messageManager.send(sender, "soysmyloot.command.common.unknown-command");
                    return true;
                }
                claimReward((Player) sender, args[1]);
                break;
            case "progress":
                if (!sender.hasPermission("soysmyloot.use")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    messageManager.send(sender, "soysmyloot.command.common.player-only");
                    return true;
                }
                sendProgress((Player) sender, args.length >= 2 ? args[1] : null);
                break;
            case "info":
                sendInfo(sender);
                break;
            case "gui":
                if (!sender.hasPermission("soysmyloot.use")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    messageManager.send(sender, "soysmyloot.command.common.player-only");
                    return true;
                }
                rewardGui.open((Player) sender, 0);
                break;
            case "top":
                if (!sender.hasPermission("soysmyloot.use")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                sendTop(sender, args.length >= 2 ? args[1] : null);
                break;
            case "export":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                doExport(sender, args.length >= 2 ? args[1] : null);
                break;
            case "import":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                if (args.length < 2) {
                    messageManager.send(sender, "soysmyloot.command.common.unknown-command");
                    return true;
                }
                doImport(sender, args[1]);
                break;
            case "migrate":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                if (args.length < 3) {
                    messageManager.send(sender, "soysmyloot.command.common.unknown-command");
                    return true;
                }
                doMigrate(sender, args[1], args[2]);
                break;
            case "reset":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                doReset(sender, args);
                break;
            case "give":
                if (!sender.hasPermission("soysmyloot.admin")) {
                    messageManager.send(sender, "soysmyloot.command.common.no-permission");
                    return true;
                }
                doGive(sender, args);
                break;
            default:
                messageManager.send(sender, "soysmyloot.command.common.unknown-command");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        messageManager.send(sender, "soysmyloot.command.help.header");
        String[][] lines = {
                {"myloot", "显示本帮助"},
                {"myloot list", "列出全部奖励及状态"},
                {"myloot gui", "打开奖励领取界面（GUI）"},
                {"myloot claim <id>", "领取指定奖励"},
                {"myloot progress [怪物]", "查看当前进度"},
                {"myloot top [damage|kills]", "查看排行榜"},
                {"myloot info", "查看插件信息"},
                {"myloot export [文件]", "导出全部数据为备份"},
                {"myloot import <文件>", "从备份导入数据"},
                {"myloot migrate <from> <to>", "在存储后端间迁移数据"},
                {"myloot reset <keep|full>", "清空全服进度/全部数据（管理员）"},
                {"myloot reset <玩家> <keep|full>", "重置指定玩家进度（管理员）"},
                {"myloot give <玩家> <奖励>", "代发奖励给指定玩家（管理员）"},
                {"myloot reload [config|monsters|rewards|messages]", "重载配置（默认全部，可指定单文件）（管理员）"}
        };
        for (String[] line : lines) {
            messageManager.send(sender, "soysmyloot.command.help.line", line[0], line[1]);
        }
        messageManager.send(sender, "soysmyloot.command.help.footer");
    }

    private void sendList(CommandSender sender) {
        if (config.getRewards().isEmpty()) {
            messageManager.send(sender, "soysmyloot.command.list.empty");
            return;
        }
        messageManager.send(sender, "soysmyloot.command.list.header");
        for (RewardEntry reward : sortedRewards()) {
            String coloredName = Text.color(reward.getName());
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (rewardManager.canClaim(p, reward)) {
                    messageManager.send(sender, "soysmyloot.command.list.item-available", reward.getId(), coloredName);
                } else if (rewardManager.isClaimed(p, reward)) {
                    messageManager.send(sender, "soysmyloot.command.list.item-claimed", reward.getId(), coloredName);
                } else {
                    messageManager.send(sender, "soysmyloot.command.list.item-locked", reward.getId(), coloredName);
                }
            } else {
                sender.sendMessage(Text.color("&e" + reward.getId() + " &7- " + coloredName));
            }
        }
        messageManager.send(sender, "soysmyloot.command.list.footer");
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
            messageManager.send(player, "soysmyloot.command.reward.not-found", id);
            return;
        }
        if (!reward.isPartial() && rewardManager.isClaimed(player, reward)) {
            messageManager.send(player, "soysmyloot.command.reward.claimed");
            return;
        }
        long cd = rewardManager.getCooldownRemaining(player, reward);
        if (cd > 0) {
            messageManager.send(player, "soysmyloot.command.reward.cooldown", cd);
            return;
        }
        if (reward.getDailyLimit() > 0 && rewardManager.getDailyClaimed(player, reward) >= reward.getDailyLimit()) {
            messageManager.send(player, "soysmyloot.command.reward.daily-limit");
            return;
        }
        if (reward.getWeeklyLimit() > 0 && rewardManager.getWeeklyClaimed(player, reward) >= reward.getWeeklyLimit()) {
            messageManager.send(player, "soysmyloot.command.reward.weekly-limit");
            return;
        }
        if (!rewardManager.canClaim(player, reward)) {
            if (reward.isPartial() && rewardManager.bankedUnits(player, reward) < 1) {
                messageManager.send(player, "soysmyloot.command.reward.partial-not-enough");
            } else {
                messageManager.send(player, "soysmyloot.command.reward.conditions-not-met");
            }
            return;
        }
        if (rewardManager.claim(player, reward)) {
            messageManager.send(player, "soysmyloot.command.reward.claimed-success", Text.color(reward.getName()));
        }
    }

    private void sendProgress(Player player, String monsterId) {
        UUID owner = scopeResolver.resolveOwner(player);
        String world = scopeResolver.resolveWorld(player.getWorld().getName());
        PlayerData data = dataManager.getData(owner);
        messageManager.send(player, "soysmyloot.command.progress.header");
        if (monsterId != null) {
            MonsterEntry m = config.getMonster(monsterId);
            if (m == null) {
                messageManager.send(player, "soysmyloot.command.common.no-data");
                return;
            }
            messageManager.send(player, "soysmyloot.command.progress.item",
                    m.getDisplayName(),
                    fmt(data.getDamage(world, m.getId())),
                    data.getKills(world, m.getId()));
        } else {
            if (config.getMonsters().isEmpty()) {
                messageManager.send(player, "soysmyloot.command.common.no-data");
                return;
            }
            for (MonsterEntry m : config.getMonsters().values()) {
                messageManager.send(player, "soysmyloot.command.progress.item",
                        m.getDisplayName(),
                        fmt(data.getDamage(world, m.getId())),
                        data.getKills(world, m.getId()));
            }
        }
        messageManager.send(player, "soysmyloot.command.progress.footer");
    }

    private void sendInfo(CommandSender sender) {
        messageManager.send(sender, "soysmyloot.command.info.basic",
                plugin.getDescription().getVersion());

        messageManager.send(sender, "soysmyloot.command.info.database",
                config.getPrimaryBackendId(),
                config.getMonsters().size(),
                config.getRewards().size());

        // 进度归属 / 世界隔离 / 赛季信息
        sender.sendMessage(Text.color("&7进度归属: &e" + config.getScopeId()
                + " &7| 世界隔离: &e" + config.isWorldIsolation()
                + " &7| 排行榜: &e" + config.getLeaderboardLimit() + " 名"));
        messageManager.send(sender, "soysmyloot.command.info.locale", messageManager.getLocale());
        sender.sendMessage(Text.color("&7MythicMobs: "
                + (MythicMobsHook.isAvailable() ? "&a可用（可按内部名匹配自定义怪物）" : "&c未安装")));
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
                sender.sendMessage(Text.color("&7用法：&e/myloot top [damage|kills]"));
            }
        }

        List<LeaderboardRow> rows = byDamage ? leaderboardManager.getRowsByDamage() : leaderboardManager.getRowsByKills();
        messageManager.send(sender, byDamage
                ? "soysmyloot.command.leaderboard.header-damage"
                : "soysmyloot.command.leaderboard.header-kills");
        if (rows.isEmpty()) {
            messageManager.send(sender, "soysmyloot.command.leaderboard.empty");
            return;
        }

        java.util.Map<UUID, String> teamNames = LinkTeamHook.getTeamNameIndex();
        for (LeaderboardRow row : rows) {
            messageManager.send(sender, "soysmyloot.command.leaderboard.line",
                    row.getRank(),
                    resolveOwnerName(row.getOwner(), teamNames),
                    fmt(row.getTotalDamage()),
                    row.getTotalKills());
        }

        // 命令执行者自身的名次（仅玩家）
        if (sender instanceof Player) {
            Player p = (Player) sender;
            UUID owner = scopeResolver.resolveOwner(p);
            int rank = leaderboardManager.getRank(owner, byDamage);
            PlayerData data = dataManager.getData(owner);
            if (rank > 0) {
                messageManager.send(sender, byDamage
                        ? "soysmyloot.command.leaderboard.self-damage"
                        : "soysmyloot.command.leaderboard.self-kills",
                        rank,
                        fmt(data.getTotalDamage()),
                        data.getTotalKills());
            } else {
                messageManager.send(sender, "soysmyloot.command.leaderboard.unranked");
            }
        }
    }

    private String resolveOwnerName(UUID owner, java.util.Map<UUID, String> teamNames) {
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
            messageManager.send(sender, "soysmyloot.command.data.export-fail", "无法创建导出目录");
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
                messageManager.send(sender, "soysmyloot.command.data.export-success", count, file.getName());
            } catch (Exception e) {
                messageManager.send(sender, "soysmyloot.command.data.export-fail", e.getMessage());
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
                messageManager.send(sender, "soysmyloot.command.data.import-success", targetFile.getName(), count);
                leaderboardManager.refresh();
            } catch (Exception e) {
                messageManager.send(sender, "soysmyloot.command.data.import-fail", e.getMessage());
            }
        });
    }

    private void doMigrate(CommandSender sender, String fromArg, String toArg) {
        StorageType from = StorageType.fromId(fromArg);
        StorageType to = StorageType.fromId(toArg);
        if (from == null || to == null) {
            messageManager.send(sender, "soysmyloot.command.data.migrate-unknown", fromArg);
            return;
        }
        if (from == to) {
            messageManager.send(sender, "soysmyloot.command.data.migrate-fail", "来源与目标不能相同");
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int count = storageManager.migrate(from, to, true);
                messageManager.send(sender, "soysmyloot.command.data.migrate-success",
                        count, from.getId(), to.getId());
                leaderboardManager.refresh();
            } catch (Exception e) {
                messageManager.send(sender, "soysmyloot.command.data.migrate-fail", e.getMessage());
            }
        });
    }

    /**
     * 重载配置。
     * 用法：
     *   /myloot reload              —— 重载全部配置
     *   /myloot reload all          —— 重载全部配置
     *   /myloot reload config       —— 仅重载 config.yml（设置）
     *   /myloot reload monsters     —— 仅重载 monsters.yml
     *   /myloot reload rewards      —— 仅重载 rewards.yml
     *   /myloot reload messages     —— 仅重载 language/ 语言文件
     */
    private void doReload(CommandSender sender, String[] args) {
        String target = args.length >= 2 ? args[1].toLowerCase() : "all";

        switch (target) {
            case "all":
                config.loadAll();
                scopeResolver.reload();
                leaderboardManager.reload();
                messageManager.reload();
                refreshLeaderboardAsync();
                messageManager.send(sender, "soysmyloot.command.reload.success",
                        config.getMonsters().size(), config.getRewards().size());
                break;
            case "config":
                config.loadSettings();
                scopeResolver.reload();
                leaderboardManager.reload();
                messageManager.reload();
                refreshLeaderboardAsync();
                messageManager.send(sender, "soysmyloot.command.reload.single", "config.yml");
                break;
            case "monsters":
                config.loadMonsters();
                messageManager.send(sender, "soysmyloot.command.reload.single", "monsters.yml");
                break;
            case "rewards":
                config.loadRewards();
                messageManager.send(sender, "soysmyloot.command.reload.single", "rewards.yml");
                break;
            case "messages":
                messageManager.reload();
                messageManager.send(sender, "soysmyloot.command.reload.single", "language/");
                break;
            default:
                messageManager.send(sender, "soysmyloot.command.reload.usage");
                break;
        }
    }

    /** 异步刷新排行榜（封装重复逻辑） */
    private void refreshLeaderboardAsync() {
        if (plugin.getServer() != null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, leaderboardManager::refresh);
        }
    }

    private void doReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageManager.send(sender, "soysmyloot.command.data.reset-usage");
            return;
        }

        // 判断第二个参数是玩家名还是模式
        String firstArg = args[1];
        boolean isPlayerMode = !"keep".equalsIgnoreCase(firstArg) && !"full".equalsIgnoreCase(firstArg);

        if (isPlayerMode) {
            // 重置指定玩家：/myloot reset <玩家> <keep|full>
            if (args.length < 3) {
                messageManager.send(sender, "soysmyloot.command.data.reset-player-usage");
                return;
            }
            String modeArg = args[2];
            int keepClaims = parseResetMode(sender, modeArg);
            if (keepClaims == -1) {
                return;
            }
            boolean keep = keepClaims == 1;
            OfflinePlayer target = Bukkit.getOfflinePlayer(firstArg);
            if (target == null) {
                messageManager.send(sender, "soysmyloot.command.data.player-not-found", firstArg);
                return;
            }
            dataManager.resetPlayer(target.getUniqueId(), !keep);
            String playerName = target.getName() != null ? target.getName() : firstArg;
            messageManager.send(sender, keep
                    ? "soysmyloot.command.data.reset-player-progress"
                    : "soysmyloot.command.data.reset-player-full", playerName);
        } else {
            // 重置全服：/myloot reset <keep|full>
            boolean keepClaims = parseResetMode(sender, firstArg) == 1;
            seasonManager.resetProgress(keepClaims, true);
            messageManager.send(sender, keepClaims
                    ? "soysmyloot.command.data.reset-progress"
                    : "soysmyloot.command.data.reset-full");
        }
    }

    /**
     * 解析重置模式，返回 1=keep, 0=full, -1=无效（已发送错误消息）。
     */
    private int parseResetMode(CommandSender sender, String modeArg) {
        if ("keep".equalsIgnoreCase(modeArg)) {
            return 1;
        }
        if ("full".equalsIgnoreCase(modeArg)) {
            return 0;
        }
        messageManager.send(sender, "soysmyloot.command.data.reset-usage");
        return -1;
    }

    /**
     * 管理员代发奖励：绕过条件校验、冷却、领取记录，直接发放奖励内容。
     * 用法：/myloot give <玩家名> <奖励ID>
     */
    private void doGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageManager.send(sender, "soysmyloot.command.data.give-usage");
            return;
        }
        String playerName = args[1];
        String rewardId = args[2];

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            messageManager.send(sender, "soysmyloot.command.data.player-not-online", playerName);
            return;
        }
        RewardEntry reward = config.getReward(rewardId);
        if (reward == null) {
            messageManager.send(sender, "soysmyloot.command.reward.not-found", rewardId);
            return;
        }

        rewardManager.adminGive(target, reward);

        messageManager.send(sender, "soysmyloot.command.data.give-success",
                target.getName(), Text.color(reward.getName()), rewardId);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String[] subs = {"help", "reload", "list", "gui", "claim", "progress", "info", "top", "export", "import", "migrate", "reset", "give"};
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
            } else if (a0.equals("reload")) {
                for (String s : new String[]{"all", "config", "monsters", "rewards", "messages"}) {
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
                // 第二个参数可能是 keep/full，也可能是玩家名
                String input = args[1].toLowerCase();
                for (String s : new String[]{"keep", "full"}) {
                    if (s.startsWith(input)) {
                        result.add(s);
                    }
                }
                // 补全在线玩家名
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) {
                        result.add(p.getName());
                    }
                }
            } else if (a0.equals("give")) {
                // 补全在线玩家名
                String input = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) {
                        result.add(p.getName());
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
        } else if (args.length == 3) {
            String a0 = args[0].toLowerCase();
            if (a0.equals("migrate")) {
                for (StorageType t : StorageType.values()) {
                    if (t.getId().startsWith(args[2].toLowerCase())) {
                        result.add(t.getId());
                    }
                }
            } else if (a0.equals("give")) {
                // 补全奖励ID
                for (String id : config.getRewards().keySet()) {
                    if (id.startsWith(args[2].toLowerCase())) {
                        result.add(id);
                    }
                }
            } else if (a0.equals("reset")) {
                // 第三个参数是 keep/full
                for (String s : new String[]{"keep", "full"}) {
                    if (s.startsWith(args[2].toLowerCase())) {
                        result.add(s);
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
