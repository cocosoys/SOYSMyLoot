package soys.soysmyloot.spring.entity.po;

import lombok.Data;

/**
 * 玩家侧领取请求：一次性 token + 礼包 ID。
 */
@Data
public class PlayerClaimPo {
    private String token;
    private String rewardId;
}
