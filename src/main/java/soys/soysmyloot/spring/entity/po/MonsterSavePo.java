package soys.soysmyloot.spring.entity.po;

import lombok.Data;

/**
 * 怪物保存入参。
 */
@Data
public class MonsterSavePo {

    private String id;
    /** Bukkit EntityType 名（如 ZOMBIE） */
    private String type;
    /** 内部识别名（mythic 用，可空） */
    private String name;
    /** 显示名 */
    private String displayName;
    /** MythicMobs 内部 ID（可空） */
    private String mythic;
}
