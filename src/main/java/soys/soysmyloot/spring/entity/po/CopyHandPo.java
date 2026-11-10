package soys.soysmyloot.spring.entity.po;

import lombok.Data;

/**
 * 从在线玩家背包拷贝物品入参。
 */
@Data
public class CopyHandPo {

    /** 目标在线玩家名 */
    private String playerName;
}
