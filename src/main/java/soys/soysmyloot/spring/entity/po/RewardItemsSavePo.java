package soys.soysmyloot.spring.entity.po;

import lombok.Data;
import soys.soysmyloot.spring.entity.vo.RewardItemVo;

import java.util.List;

/**
 * 物品栏保存入参。
 */
@Data
public class RewardItemsSavePo {

    private String rewardId;
    private List<RewardItemVo> items;
}
