package soys.soysmyloot.spring.entity.vo;

import lombok.Data;

import java.util.List;

/**
 * 礼包详情 VO（/erp/reward/get 返回）。
 */
@Data
public class RewardDetailVo {

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

    /** 领取反馈（音效 + 粒子）；null = 该礼包未配置反馈 */
    private RewardFeedbackVo feedback;
}
