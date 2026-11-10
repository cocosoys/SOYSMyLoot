package soys.soysmyloot.spring.entity.vo;

import lombok.Data;

import java.util.List;

/**
 * 领取条件 VO。
 */
@Data
public class RewardConditionVo {

    /** 条件类型：DAMAGE/KILLS/LEVEL/MONEY/ONLINE/HAS_ITEM/TIME */
    private String type;

    /** 关联怪物 ID（DAMAGE/KILLS 用） */
    private String monsterId;

    /** 阈值 */
    private Double amount;

    /** HAS_ITEM 目标物品材质（可空） */
    private String material;

    /** HAS_ITEM 目标物品 data（-1=任意） */
    private Integer data;

    /** TIME 起始分钟数（当日） */
    private Integer startMinOfDay;

    /** TIME 结束分钟数（当日） */
    private Integer endMinOfDay;

    /** TIME 允许星期（1=周一..7=周日），null=每天 */
    private List<Integer> days;
}
