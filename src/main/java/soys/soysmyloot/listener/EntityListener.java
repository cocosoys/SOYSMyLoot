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

    public EntityListener(SOYSMyLoot plugin, ConfigManager config, DataManager dataManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
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

        dataManager.getData(player.getUniqueId()).addDamage(monster.getId(), event.getFinalDamage());

        if (config.isDebug()) {
            plugin.getLogger().info("[追踪] " + player.getName() + " 对 " + monster.getId()
                    + " 造成 " + event.getFinalDamage() + " 点伤害");
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

        dataManager.getData(killer.getUniqueId()).addKill(monster.getId(), 1);

        if (config.isDebug()) {
            plugin.getLogger().info("[追踪] " + killer.getName() + " 击杀 " + monster.getId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        dataManager.unload(event.getPlayer().getUniqueId());
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
