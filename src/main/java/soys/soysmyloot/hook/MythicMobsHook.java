package soys.soysmyloot.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * MythicMobs 软依赖反射桥接。
 * <p>
 * 插件声明 {@code softdepend: [MythicMobs]}，但编译期并不依赖该插件，
 * 因此全部调用通过反射完成，避免找不到类导致编译/加载失败。
 * 提供：根据实体查询其 MythicMobs「内部名（Internal Name）」，用于
 * 「按内部名匹配自定义怪物」特性（monsters.yml 的 {@code mythic} 字段）。
 * </p>
 * <p>
 * 反射目标（仅在运行时存在，类名随版本变化，故用候选列表兜底）：
 *   {@code MobManager#getActiveMob(UUID)} -> Optional<ActiveMob>
 *   {@code ActiveMob#getType()}            -> MythicMob
 *   {@code MythicMob#getInternalName()}    -> String
 * 主类为插件实例本身（JavaPlugin），直接对其调用 {@code getMobManager()} 即可。
 * </p>
 */
public final class MythicMobsHook {

    /** 不同版本已知的 ActiveMob / MythicMob 类名（仅用于按名取方法，无编译依赖） */
    private static final String[] ACTIVE_MOB_CLASSES = {
            "io.lumine.xikage.mythicmobs.mobs.ActiveMob",
            "io.lumine.waywiders.mythicmobs.mobs.ActiveMob",
            "mythicmobs.mobs.ActiveMob"
    };
    private static final String[] MYTHIC_MOB_CLASSES = {
            "io.lumine.xikage.mythicmobs.mobs.MythicMob",
            "io.lumine.waywiders.mythicmobs.mobs.MythicMob",
            "mythicmobs.mobs.MythicMob"
    };

    private static boolean initialized = false;
    private static Plugin plugin;
    private static Object mobManager;
    private static Method getActiveMob;
    private static Method activeMobGetType;
    private static Method mythicGetInternalName;

    private MythicMobsHook() {
    }

    /** 初始化反射方法句柄。重复调用安全。 */
    public static void init() {
        initialized = true;
        plugin = null;
        mobManager = null;
        getActiveMob = null;
        activeMobGetType = null;
        mythicGetInternalName = null;

        Plugin p = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (p == null) {
            return;
        }
        try {
            // 主类即插件实例，直接取 MobManager
            Method getMobManager = p.getClass().getMethod("getMobManager");
            Object mm = getMobManager.invoke(p);
            if (mm == null) {
                return;
            }
            mobManager = mm;

            // getActiveMob 兼容 (UUID) 与 (Entity) 两种签名
            Method getActiveMobMethod;
            try {
                getActiveMobMethod = mm.getClass().getMethod("getActiveMob", UUID.class);
            } catch (NoSuchMethodException e) {
                getActiveMobMethod = mm.getClass().getMethod("getActiveMob", org.bukkit.entity.Entity.class);
            }
            getActiveMob = getActiveMobMethod;

            Class<?> activeMobClass = resolveClass(ACTIVE_MOB_CLASSES);
            Class<?> mythicMobClass = resolveClass(MYTHIC_MOB_CLASSES);
            if (activeMobClass == null || mythicMobClass == null) {
                return;
            }
            activeMobGetType = activeMobClass.getMethod("getType");
            mythicGetInternalName = mythicMobClass.getMethod("getInternalName");

            plugin = p;
        } catch (Throwable t) {
            // 方法签名不匹配或插件已变动：禁用桥接但不影响主插件运行
            plugin = null;
            mobManager = null;
            getActiveMob = null;
            activeMobGetType = null;
            mythicGetInternalName = null;
        }
    }

    private static Class<?> resolveClass(String[] candidates) {
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
                // 尝试下一个候选
            }
        }
        return null;
    }

    /** MythicMobs 是否可用（插件已加载且方法句柄就绪） */
    public static boolean isAvailable() {
        if (!initialized) {
            init();
        }
        return plugin != null && plugin.isEnabled() && mobManager != null
                && getActiveMob != null && activeMobGetType != null && mythicGetInternalName != null;
    }

    /**
     * 查询实体的 MythicMobs 内部名（Internal Name）。
     *
     * @return 内部名；非 MythicMob 实体或桥接不可用时返回 null
     */
    public static String getInternalName(LivingEntity entity) {
        if (!isAvailable() || entity == null) {
            return null;
        }
        try {
            Object opt;
            Class<?>[] params = getActiveMob.getParameterTypes();
            if (params.length == 1 && params[0] == UUID.class) {
                opt = getActiveMob.invoke(mobManager, entity.getUniqueId());
            } else {
                opt = getActiveMob.invoke(mobManager, entity);
            }
            if (opt == null) {
                return null;
            }
            Object activeMob;
            if (opt instanceof Optional) {
                Optional<?> o = (Optional<?>) opt;
                if (!o.isPresent()) {
                    return null;
                }
                activeMob = o.get();
            } else {
                // 旧版 API 直接返回 ActiveMob（可能为 null）
                activeMob = opt;
            }
            if (activeMob == null) {
                return null;
            }
            Object mythicMob = activeMobGetType.invoke(activeMob);
            if (mythicMob == null) {
                return null;
            }
            Object name = mythicGetInternalName.invoke(mythicMob);
            return name == null ? null : name.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
