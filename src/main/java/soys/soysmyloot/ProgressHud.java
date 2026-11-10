package soys.soysmyloot;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.model.MonsterEntry;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 进度 HUD：定时向在线玩家展示当前（或进度最高的）追踪怪物的伤害/击杀进度。
 * <ul>
 *   <li>{@code hud.mode: actionbar} —— 通过 Spigot ActionBar 推送文本；</li>
 *   <li>{@code hud.mode: bossbar} —— 为每名玩家创建并复用一条 BossBar。</li>
 * </ul>
 * 刷新频率由 {@code hud.update-ticks} 控制；关闭或插件禁用时自动清理。
 */
public class ProgressHud implements Listener {

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private final DataManager dataManager;
    private final ScopeResolver scopeResolver;

    /** 玩家 UUID -> 最近一次造成伤害/击杀的怪物 ID（HUD 当前展示对象） */
    private final Map<UUID, String> currentMonster = new HashMap<>();
    /** 玩家 UUID -> BossBar（bossbar 模式复用） */
    private final Map<UUID, BossBar> bars = new HashMap<>();

    private boolean running;
    private int taskId = -1;

    public ProgressHud(SOYSMyLoot plugin, ConfigManager config, DataManager dataManager,
                       ScopeResolver scopeResolver) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.scopeResolver = scopeResolver;
    }

    /** 启动 HUD（配置关闭时安全跳过） */
    public void init() {
        if (!config.isHudEnabled()) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long period = Math.max(5, config.getHudUpdateTicks());
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period).getTaskId();
        running = true;
        plugin.getLogger().info("进度 HUD 已启用（模式: " + config.getHudMode() + "，刷新: " + period + " tick）");
    }

    /** 停止 HUD 并清理所有 BossBar */
    public void stop() {
        running = false;
        if (taskId >= 0) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
        currentMonster.clear();
    }

    /** 记录玩家当前交互的怪物（由 EntityListener 调用） */
    public void setCurrentMonster(Player player, String monsterId) {
        if (!running) {
            return;
        }
        if (monsterId == null || monsterId.isEmpty()) {
            currentMonster.remove(player.getUniqueId());
        } else {
            currentMonster.put(player.getUniqueId(), monsterId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
        currentMonster.remove(uuid);
    }

    // ================================================================
    //  内部逻辑
    // ================================================================

    private void tick() {
        if (!running) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    private void updatePlayer(Player player) {
        if (!player.isOnline()) {
            return;
        }
        String monsterId = currentMonster.get(player.getUniqueId());
        if (monsterId == null || config.getMonster(monsterId) == null) {
            // 无当前怪物时，展示进度最高的怪物（无任何进度则不展示）
            monsterId = findTopProgressMonster(player);
            if (monsterId == null) {
                clearHud(player);
                return;
            }
        }
        MonsterEntry monster = config.getMonster(monsterId);
        if (monster == null) {
            clearHud(player);
            return;
        }

        PlayerData data = dataManager.getData(scopeResolver.resolveOwner(player));
        String world = scopeResolver.resolveWorld(player.getWorld().getName());
        double damage = data.getDamage(world, monsterId);
        int kills = data.getKills(world, monsterId);

        // 从奖励条件中推导该怪物的展示目标（取最小的伤害/击杀条件 amount）
        double damageTarget = -1;
        double killsTarget = -1;
        for (RewardEntry reward : config.getRewards().values()) {
            for (RewardEntry.Condition c : reward.getConditions()) {
                if (!monsterId.equals(c.getMonsterId())) {
                    continue;
                }
                if (c.getType() == RewardEntry.ConditionType.DAMAGE
                        && (damageTarget < 0 || c.getAmount() < damageTarget)) {
                    damageTarget = c.getAmount();
                } else if (c.getType() == RewardEntry.ConditionType.KILLS
                        && (killsTarget < 0 || c.getAmount() < killsTarget)) {
                    killsTarget = c.getAmount();
                }
            }
        }

        double ratio = 0;
        if (damageTarget > 0) {
            ratio = damage / damageTarget;
        } else if (killsTarget > 0) {
            ratio = kills / killsTarget;
        }
        if (ratio > 1) {
            ratio = 1;
        }
        int percent = (int) Math.round(ratio * 100);

        StringBuilder text = new StringBuilder("&e" + monster.getDisplayName() + " &f伤害 &a"
                + fmt(damage));
        if (damageTarget > 0) {
            text.append("&7/&a").append(fmt(damageTarget));
        }
        text.append(" &f| &f击杀 &a").append(kills);
        if (killsTarget > 0) {
            text.append("&7/&a").append(fmt(killsTarget));
        }
        if (damageTarget > 0 || killsTarget > 0) {
            text.append(" &f").append(Text.progressBar(ratio, 10)).append(" &a").append(percent).append("%");
        }

        String colored = Text.color(text.toString());
        if ("bossbar".equals(config.getHudMode())) {
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), k ->
                    Bukkit.createBossBar(colored, BarColor.GREEN, BarStyle.SEGMENTED_10));
            bar.addPlayer(player);
            bar.setTitle(colored);
            bar.setProgress(Math.max(0.0, Math.min(1.0, ratio)));
            bar.setVisible(damageTarget > 0 || killsTarget > 0 || damage > 0 || kills > 0);
        } else {
            BossBar bar = bars.remove(player.getUniqueId());
            if (bar != null) {
                bar.removeAll();
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colored));
        }
    }

    private void clearHud(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        // actionbar 模式推送空串以清除残留
        if ("actionbar".equals(config.getHudMode()) && player.isOnline()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    /** 找出该玩家当前进度最高（伤害+击杀合计）的怪物 ID；无进度返回 null */
    private String findTopProgressMonster(Player player) {
        PlayerData data = dataManager.getData(scopeResolver.resolveOwner(player));
        String world = scopeResolver.resolveWorld(player.getWorld().getName());
        String best = null;
        double bestScore = 0;
        for (String monsterId : config.getMonsters().keySet()) {
            double score = data.getDamage(world, monsterId) + data.getKills(world, monsterId) * 100;
            if (score > bestScore) {
                bestScore = score;
                best = monsterId;
            }
        }
        return best;
    }

    private String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }
}
