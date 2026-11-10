package soys.soysmyloot.model;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import soys.soysmyloot.hook.MythicMobsHook;

/**
 * 怪物配置项：对应 monsters.yml 中的单个怪物追踪规则。
 */
public class MonsterEntry {

    private final String id;
    private final EntityType type;
    private final String customName;   // 需要匹配的自定义名称（含 & 颜色代码，可能为空）
    private final String displayName;  // 仅用于展示的名称
    private final String mythicId;     // MythicMobs 内部名（Internal Name）；非空时按内部名匹配

    public MonsterEntry(String id, EntityType type, String customName, String displayName) {
        this(id, type, customName, displayName, "");
    }

    public MonsterEntry(String id, EntityType type, String customName, String displayName, String mythicId) {
        this.id = id;
        this.type = type;
        this.customName = customName;
        this.displayName = displayName;
        this.mythicId = mythicId == null ? "" : mythicId;
    }

    public String getId() {
        return id;
    }

    public EntityType getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** MythicMobs 内部名（空串表示未配置） */
    public String getMythicId() {
        return mythicId;
    }

    public boolean hasMythic() {
        return mythicId != null && !mythicId.isEmpty();
    }

    /**
     * 判断一个实体是否匹配本怪物规则。
     * <ul>
     *   <li>若配置了 {@code mythic} 内部名：仅当该实体是对应内部名的 MythicMob 时匹配
     *       （忽略基础类型与显示名，内部名唯一标识一种怪物）。</li>
     *   <li>否则按 类型 + 自定义名称 匹配（自定义名称为空表示匹配该类型任意实体）。</li>
     * </ul>
     */
    public boolean matches(LivingEntity entity) {
        if (hasMythic()) {
            String internal = MythicMobsHook.getInternalName(entity);
            return internal != null && internal.equalsIgnoreCase(mythicId);
        }
        if (entity.getType() != this.type) {
            return false;
        }
        // 未配置名称限制时，只要类型匹配即可
        if (customName == null || customName.isEmpty()) {
            return true;
        }
        String entityName = entity.getCustomName();
        if (entityName == null) {
            return false;
        }
        // 同时比较“带颜色代码”与“去色后”两种形式，兼容实体名含/不含颜色码
        String cfgName = ChatColor.translateAlternateColorCodes('&', customName);
        if (entityName.equals(cfgName)) {
            return true;
        }
        return ChatColor.stripColor(entityName).equalsIgnoreCase(ChatColor.stripColor(cfgName));
    }
}
