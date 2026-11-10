package soys.soysmyloot.spring.entity.vo;

import lombok.Data;

/**
 * 领取记录行 VO。
 */
@Data
public class ClaimRecordVo {

    /** 玩家名 */
    private String playerName;

    /** 玩家 UUID */
    private String playerUuid;

    /** 礼包 ID */
    private String rewardId;

    /** 礼包名 */
    private String rewardName;

    /** 上次领取时间（epoch 秒） */
    private Long lastClaimAt;

    /** 累计领取次数 */
    private Integer claimCount;
}
