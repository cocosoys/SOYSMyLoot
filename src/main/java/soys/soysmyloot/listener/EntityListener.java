package soys.soysmyloot.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.ScopeResolver;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.model.MonsterEntry;

/**
 * 伤害/击杀追踪监听器：记录玩家对配置怪物的累计伤害与击杀数。
 */
public class EntityListener implements Listener {

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private final DataManager dataManager;
    private final ScopeResolver scopeResolver;

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

        if (config.isDebug()) {
            plugin.getLogger().info("[追踪] " + player.getName() + " 对 " + monster.getId()
                    + " 造成 " + event.getFinalDamage() + " 点伤害（owner=" + owner + ", world=" + world + "）");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        MonsterEntry monster = config.getMonster(entity);
        if (monster == null) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        java.util.UUID owner = scopeResolver.resolveOwner(killer);
        String world = scopeResolver.resolveWorld(entity.getWorld().getName());
        dataManager.getData(owner).addKill(world, monster.getId(), 1);

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
