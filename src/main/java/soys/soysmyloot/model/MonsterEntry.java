package soys.soysmyloot.model;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

/**
 * 怪物配置项：对应 monsters.yml 中的单个怪物追踪规则。
 */
public class MonsterEntry {

    private final String id;
    private final EntityType type;
    private final String customName;   // 需要匹配的自定义名称（含 & 颜色代码，可能为空）
    private final String displayName;  // 仅用于展示的名称

    public MonsterEntry(String id, EntityType type, String customName, String displayName) {
        this.id = id;
        this.type = type;
        this.customName = customName;
        this.displayName = displayName;
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

    /**
     * 判断一个实体是否匹配本怪物规则。
     */
    public boolean matches(LivingEntity entity) {
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
