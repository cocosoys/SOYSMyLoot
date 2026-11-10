package soys.soysmyloot.spring.entity.vo;

import lombok.Data;

import java.util.List;

/**
 * 礼包列表行 VO（出参，对应 /erp/reward/list 的每行）。
 */
@Data
public class RewardVo {

    /** 礼包 ID */
    private String id;

    /** 显示名 */
    private String name;

    /** 描述 */
    private String description;

    /** 阶段分组（铜/银/金，可空） */
    private String stage;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否可重复领取 */
    private Boolean repeatable;

    /** 冷却秒数 */
    private Long cooldown;

    /** 每日上限（0=不限） */
    private Integer dailyLimit;

    /** 每周上限（0=不限） */
    private Integer weeklyLimit;

    /** 是否部分领取 */
    private Boolean partial;

    /** 物品数量 */
    private Integer itemCount;

    /** 全服领取次数合计 */
    private Long claimCount;
}
