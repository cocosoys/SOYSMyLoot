package soys.soysmyloot.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.TableDataInfo;

/**
 * 领取记录查询业务接口。
 */
public interface ClaimService {

    /** 分页查询领取记录（聚合所有在线/已缓存玩家的 claimCountMap） */
    TableDataInfo list(Integer pageNum, Integer pageSize, String playerName, String rewardId);
}
