package soys.soysmyloot.spring.entity.vo;

import lombok.Data;

import java.util.List;

/**
 * 物品栏格子 VO（mcmod 风格物品编辑器的单格数据）。
 */
@Data
public class RewardItemVo {

    /** 槽位索引（0-53），保存时由前端按数组顺序填充 */
    private Integer slot;

    /** 材质名（如 DIAMOND_SWORD） */
    private String material;

    /** 数量 */
    private Integer amount;

    /** 显示名（支持颜色码） */
    private String name;

    /** Lore 行 */
    private List<String> lore;
}
