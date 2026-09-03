package soys.soysmyloot.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.ScopeResolver;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.model.MonsterEntry;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 伤害/击杀追踪监听器：记录玩家对配置怪物的累计伤害与击杀数。
 * <p>
 * 直接伤害（EntityDamageByEntityEvent）：玩家近战或发射投射物造成的伤害，有明确伤害者。
 * 间接伤害（EntityDamageEvent）：火焰燃烧、掉落、溺水、中毒等无明确伤害者的伤害，
 * 通过「最近伤害者追溯」机制归因到最近一次对该实体造成直接伤害的玩家（需在有效时间窗口内）。
 * </p>
 */
public class EntityListener implements Listener {

    /** 间接伤害追溯的有效时间窗口（毫秒）：玩家最后一次直接伤害后，此时间内的间接伤害归因于该玩家 */
    private static final long INDIRECT_TRACE_WINDOW_MS = 15000L;

    /** 被视为「间接伤害」的伤害原因集合（无明确伤害者，需追溯最近伤害者） */
    private static final EnumSet<EntityDamageEvent.DamageCause> INDIRECT_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE_TICK,    // 火焰持续燃烧
            EntityDamageEvent.DamageCause.FIRE,         // 接触火焰
            EntityDamageEvent.DamageCause.LAVA,         // 岩浆
            EntityDamageEvent.DamageCause.FALL,         // 掉落
            EntityDamageEvent.DamageCause.DROWNING,     // 溺水
            EntityDamageEvent.DamageCause.SUFFOCATION,  // 窒息
            EntityDamageEvent.DamageCause.CRAMMING,     // 实体挤压
            EntityDamageEvent.DamageCause.CONTACT,      // 仙人掌等接触伤害
            EntityDamageEvent.DamageCause.POISON,       // 中毒
            EntityDamageEvent.DamageCause.WITHER,       // 凋零
            EntityDamageEvent.DamageCause.STARVATION,   // 饥饿
            EntityDamageEvent.DamageCause.MELTING,      // 融化（雪傀儡）
            EntityDamageEvent.DamageCause.DRAGON_BREATH // 龙息
    );

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private final DataManager dataManager;
    private final ScopeResolver scopeResolver;

    /** 实体 UUID -> 最近一次造成直接伤害的玩家 UUID（用于间接伤害追溯） */
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    /** 实体 UUID -> 最近一次直接伤害的时间戳（毫秒） */
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();

    public EntityListener(SOYSMyLoot plugin, ConfigManager config, DataManager dataManager,
                          ScopeResolver scopeResolver) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.scopeResolver = scopeResolver;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        LivingEntity entity = (LivingEntity) event.getEntity();

        Player player = getDamager(event.getDamager());
        if (player == null) {
            return;
        }
        // 投射物开关
        if (event.getDamager() instanceof Projectile && !config.isTrackProjectile()) {
            return;
        }

        MonsterEntry monster = config.getMonster(entity);
        if (monster == null) {
            return;
        }

        // 进度归属：队伍模式 -> 队伍 UUID；世界隔离 -> 实体所在世界
        java.util.UUID owner = scopeResolver.resolveOwner(player);
        String world = scopeResolver.resolveWorld(entity.getWorld().getName());
        dataManager.getData(owner).addDamage(world, monster.getId(), event.getFinalDamage());

        // 记录最近伤害者，供间接伤害追溯使用
        lastDamager.put(entity.getUniqueId(), player.getUniqueId());
        lastDamageTime.put(entity.getUniqueId(), System.currentTimeMillis());

        if (config.isDebug()) {
            plugin.getLogger().info("[追踪] " + player.getName() + " 对 " + monster.getId()
                    + " 造成 " + event.getFinalDamage() + " 点伤害（owner=" + owner + ", world=" + world + "）");
        }
    }

    /**
     * 间接伤害监听：处理无明确伤害者的伤害（火焰燃烧、掉落、溺水、中毒等）。
     * 通过「最近伤害者追溯」机制，将伤害归因到最近一次对该实体造成直接伤害的玩家。
     * 仅在 config.tracking.indirect = true 时生效。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        // 跳过有明确伤害者的情况（由 onDamage 处理），避免重复计算
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (!config.isTrackIndirect()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        if (!INDIRECT_CAUSES.contains(event.getCause())) {
            return;
        }
        LivingEntity entity = (LivingEntity) event.getEntity();
        MonsterEntry monster = config.getMonster(entity);
        if (monster == null) {
            return;
        }

        // 追溯最近伤害者
        UUID damagerUuid = lastDamager.get(entity.getUniqueId());
        Long traceTime = lastDamageTime.get(entity.getUniqueId());
        if (damagerUuid == null || traceTime == null) {
            return;
        }
        if (System.currentTimeMillis() - traceTime > INDIRECT_TRACE_WINDOW_MS) {
            // 超出追溯窗口，清理过期映射
            lastDamager.remove(entity.getUniqueId());
            lastDamageTime.remove(entity.getUniqueId());
            return;
        }

        Player player = Bukkit.getPlayer(damagerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        java.util.UUID owner = scopeResolver.resolveOwner(player);
        String world = scopeResolver.resolveWorld(entity.getWorld().getName());
        dataManager.getData(owner).addDamage(world, monster.getId(), event.getFinalDamage());

        if (config.isDebug()) {
            plugin.getLogger().info("[间接追踪] " + player.getName() + " 对 " + monster.getId()
                    + " 造成 " + event.getFinalDamage() + " 点间接伤害（cause=" + event.getCause()
                    + ", owner=" + owner + ", world=" + world + "）");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        MonsterEntry monster = config.getMonster(entity);
        if (monster == null) {
            // 非追踪怪物死亡时也清理追溯映射，避免内存泄漏
            lastDamager.remove(entity.getUniqueId());
            lastDamageTime.remove(entity.getUniqueId());
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            lastDamager.remove(entity.getUniqueId());
            lastDamageTime.remove(entity.getUniqueId());
            return;
        }

        java.util.UUID owner = scopeResolver.resolveOwner(killer);
        String world = scopeResolver.resolveWorld(entity.getWorld().getName());
        dataManager.getData(owner).addKill(world, monster.getId(), 1);

        // 实体死亡后清理追溯映射
        lastDamager.remove(entity.getUniqueId());
        lastDamageTime.remove(entity.getUniqueId());

        if (config.isDebug()) {
            plugin.getLogger().info("[追踪] " + killer.getName() + " 击杀 " + monster.getId()
                    + "（owner=" + owner + ", world=" + world + "）");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // 队伍模式下进度归属为队伍 UUID（被多名成员共享），不应在单人退出时卸载，
        // 否则可能与其他在线成员的在途写产生竞态。仅玩家专属数据在此卸载。
        java.util.UUID owner = scopeResolver.resolveOwner(event.getPlayer());
        if (owner.equals(event.getPlayer().getUniqueId())) {
            dataManager.unload(owner);
        }
    }

    /** 从伤害来源中提取玩家（兼容近战与投射物） */
    private Player getDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource source = ((Projectile) damager).getShooter();
            if (source instanceof Player) {
                return (Player) source;
            }
        }
        return null;
    }
}
