package soys.soysmyloot.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * WorldGuard 软依赖反射桥接：查询某个位置所属的保护区域 ID。
 * <p>
 * 与 {@link LinkTeamHook} 保持一致，全程使用反射、零编译期依赖：
 * WorldGuard 未安装时所有方法安全降级（视为「不在任何区域内」）。
 * </p>
 * <p>兼容两条 API 路径：</p>
 * <ul>
 *   <li>WG 7.x：{@code WorldGuardPlugin.getRegionContainer().createQuery()}</li>
 *   <li>WG 6.x：{@code WorldGuardPlugin.getRegionManager(World)}</li>
 * </ul>
 */
public final class WorldGuardHook {

    private static boolean available;
    private static Object instance; // WorldGuardPlugin 实例

    private WorldGuardHook() {
    }

    /** 初始化桥接（在插件启用时调用一次） */
    public static void init() {
        available = false;
        instance = null;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (plugin == null) {
            return;
        }
        try {
            // WG6 常用静态入口：WorldGuardPlugin.inst()
            try {
                instance = plugin.getClass().getMethod("inst").invoke(null);
            } catch (NoSuchMethodException e) {
                instance = plugin;
            }
            // 探测 API 可用性（任一路径存在即可）
            probe();
            available = instance != null;
        } catch (Throwable t) {
            instance = null;
            available = false;
        }
    }

    private static void probe() {
        try {
            instance.getClass().getMethod("getRegionContainer");
            return;
        } catch (NoSuchMethodException ignored) {
        }
        try {
            instance.getClass().getMethod("getRegionManager", org.bukkit.World.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("无法识别的 WorldGuard API 版本", e);
        }
    }

    /** WorldGuard 是否可用 */
    public static boolean isAvailable() {
        return available && instance != null;
    }

    /**
     * 查询位置所属的所有区域 ID（含父区域，WorldGuard 的 getRegions 已展开继承）。
     *
     * @return 区域 ID 集合；WG 不可用、位置不在任何区域或反射失败时返回空集合
     */
    public static Set<String> getRegionIdsAt(Location location) {
        if (!isAvailable() || location == null || location.getWorld() == null) {
            return Collections.emptySet();
        }
        try {
            Object query;
            try {
                Object container = instance.getClass().getMethod("getRegionContainer").invoke(instance);
                query = container.getClass().getMethod("createQuery").invoke(container);
            } catch (NoSuchMethodException e) {
                // WG6：getRegionManager(World)
                query = instance.getClass()
                        .getMethod("getRegionManager", org.bukkit.World.class)
                        .invoke(instance, location.getWorld());
            }
            Object applicable = query.getClass()
                    .getMethod("getApplicableRegions", Location.class)
                    .invoke(query, location);
            Object regions = applicable.getClass().getMethod("getRegions").invoke(applicable);
            Set<String> ids = new HashSet<>();
            for (Object region : (Collection<?>) regions) {
                Object id = region.getClass().getMethod("getId").invoke(region);
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            }
            return ids;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    /**
     * 判断位置是否允许追踪伤害。
     *
     * @param mode    all（不限制）| whitelist（仅 list 中的区域）| blacklist（list 中的区域除外）
     * @param regions 区域 ID 列表（配置的 tracking.regions.list）
     * @return true = 允许追踪
     */
    public static boolean isTrackable(Location location, String mode, Collection<String> regions) {
        if (mode == null || mode.isEmpty() || "all".equals(mode)) {
            return true;
        }
        if (regions == null || regions.isEmpty()) {
            // 名单为空时不构成限制，等同于 all
            return true;
        }
        Set<String> at = getRegionIdsAt(location);
        boolean inside = false;
        for (String id : at) {
            if (regions.contains(id)) {
                inside = true;
                break;
            }
        }
        return "whitelist".equals(mode) ? inside : !inside;
    }
}
