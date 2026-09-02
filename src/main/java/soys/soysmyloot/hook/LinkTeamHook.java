package soys.soysmyloot.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SOYSLinkTeam 软依赖反射桥接。
 * <p>
 * 插件声明 {@code softdepend: [SOYSLinkTeam]}，但编译期并不依赖该插件，
 * 因此全部调用通过反射完成，避免找不到类导致编译/加载失败。
 * 提供：玩家所属队伍查询、队伍名索引查询，用于「队伍共享伤害」模式。
 * </p>
 * <p>
 * 反射目标（仅在运行时存在）：
 *   {@code soys.soyslinkteam.SOYSLinkTeam#getTeamManager()} -> TeamManager
 *   {@code TeamManager#getPlayerTeamId(UUID)} -> UUID | null
 *   {@code TeamManager#getTeamNameIndex()} -> Map<UUID, String>
 * </p>
 */
public final class LinkTeamHook {

    private static boolean initialized = false;
    private static Plugin plugin;
    private static Method getTeamManager;
    private static Method getPlayerTeamId;
    private static Method getTeamNameIndex;

    private LinkTeamHook() {
    }

    /** 初始化反射方法句柄。重复调用安全，仅在首次或插件重新加载时生效。 */
    public static void init() {
        initialized = true;
        plugin = Bukkit.getPluginManager().getPlugin("SOYSLinkTeam");
        getTeamManager = null;
        getPlayerTeamId = null;
        getTeamNameIndex = null;
        if (plugin == null) {
            return;
        }
        try {
            Class<?> owner = plugin.getClass();
            getTeamManager = owner.getMethod("getTeamManager");
            Object teamManager = getTeamManager.invoke(plugin);
            if (teamManager == null) {
                plugin = null;
                return;
            }
            Class<?> tmClass = teamManager.getClass();
            getPlayerTeamId = tmClass.getMethod("getPlayerTeamId", UUID.class);
            getTeamNameIndex = tmClass.getMethod("getTeamNameIndex");
        } catch (Throwable t) {
            // 方法签名不匹配或插件已变动：禁用桥接但不影响主插件运行
            plugin = null;
            getTeamManager = null;
            getPlayerTeamId = null;
            getTeamNameIndex = null;
        }
    }

    /** SOYSLinkTeam 是否可用（插件已加载且方法句柄就绪） */
    public static boolean isAvailable() {
        if (!initialized) {
            init();
        }
        return plugin != null && plugin.isEnabled() && getTeamManager != null;
    }

    /**
     * 查询玩家所属队伍 UUID。
     *
     * @return 队伍 UUID；不在队伍 / 桥接不可用时返回 null
     */
    public static UUID getPlayerTeamId(UUID playerId) {
        if (!isAvailable() || playerId == null) {
            return null;
        }
        try {
            Object teamManager = getTeamManager.invoke(plugin);
            if (teamManager == null) {
                return null;
            }
            Object result = getPlayerTeamId.invoke(teamManager, playerId);
            return result instanceof UUID ? (UUID) result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 队伍 UUID -> 队伍名 索引快照。
     */
    public static Map<UUID, String> getTeamNameIndex() {
        Map<UUID, String> map = new HashMap<>();
        if (!isAvailable()) {
            return map;
        }
        try {
            Object teamManager = getTeamManager.invoke(plugin);
            if (teamManager == null) {
                return map;
            }
            Object result = getTeamNameIndex.invoke(teamManager);
            if (result instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) result).entrySet()) {
                    if (entry.getKey() instanceof UUID) {
                        Object value = entry.getValue();
                        map.put((UUID) entry.getKey(), value == null ? "" : value.toString());
                    }
                }
            }
        } catch (Throwable t) {
            // 忽略，返回已收集的部分
        }
        return map;
    }

    /** 查询单个队伍名（不存在时返回 null） */
    public static String getTeamName(UUID teamId) {
        if (teamId == null) {
            return null;
        }
        return getTeamNameIndex().get(teamId);
    }
}
