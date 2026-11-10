package soys.soysmyloot.spring.entity.vo;

import lombok.Data;

import java.util.List;

/**
 * 玩家侧月卡礼包展示行（Hypixel 风格页用）。
 */
@Data
public class MonthlyRewardVo {
    /** 礼包 ID（如 monthly_1） */
    private String id;
    /** 显示名 */
    private String name;
    /** 月卡等级（从 ID 后缀解析，1=最低） */
    private int level;
    /** 描述 */
    private String description;
    /** 物品预览（前 9 个） */
    private List<RewardItemVo> preview;
    /** 是否已领取 */
    private boolean claimed;
    /** 是否可领取（冷却/前置/上限检查通过） */
    private boolean canClaim;
    /** 冷却剩余毫秒（0 = 无冷却） */
    private long cooldownLeftMs;
    /** 玩家名（页头展示） */
    private String playerName;
    /** 本月已解锁到第几级（用于进度条） */
    private int unlockedLevel;
}
