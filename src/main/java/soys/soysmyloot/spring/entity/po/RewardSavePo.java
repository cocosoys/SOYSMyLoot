package soys.soysmyloot.spring.entity.po;

import lombok.Data;
import soys.soysmyloot.spring.entity.vo.RewardConditionVo;
import soys.soysmyloot.spring.entity.vo.RewardItemVo;

import java.util.List;

/**
 * 礼包保存入参（新建/编辑共用，字段按端点取用）。
 */
@Data
public class RewardSavePo {

    private String id;
    private String name;
    private String description;
    private String stage;
    private Boolean enabled;
    private Boolean repeatable;
    private Long cooldown;
    private Integer dailyLimit;
    private Integer weeklyLimit;
    private Boolean partial;
    private Double per;
    private Boolean consume;

    private List<RewardConditionVo> conditions;
    private List<String> requires;
    private List<String> pool;
    private Integer poolCount;

    private List<RewardItemVo> items;
    private List<String> commands;
    private Double money;
    private Double points;
    private List<String> messages;
}
