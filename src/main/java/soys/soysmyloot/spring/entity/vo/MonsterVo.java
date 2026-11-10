package soys.soysmyloot.spring.entity.vo;

import lombok.Data;

/**
 * 怪物列表行 VO。
 */
@Data
public class MonsterVo {

    private String id;
    /** Bukkit EntityType 名 */
    private String type;
    /** 内部识别名（mythic 用） */
    private String name;
    /** 显示名 */
    private String displayName;
    /** MythicMobs 内部 ID（可空） */
    private String mythic;
}
