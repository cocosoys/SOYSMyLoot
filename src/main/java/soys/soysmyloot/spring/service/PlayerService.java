package soys.soysmyloot.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import soys.soysmyloot.spring.entity.vo.MonthlyRewardVo;

import java.util.List;
import java.util.UUID;

/**
 * 玩家侧月卡服务：颁发一次性 token、查询月卡阶梯、执行领取。
 */
public interface PlayerService {

    /** 颁发一次性 token（游戏内命令调用，返回带 token 的完整 URL 段）。 */
    String issueToken(UUID uuid, String playerName);

    /** 校验 token，返回绑定的玩家 UUID；无效返回 null。 */
    UUID resolveToken(String token);

    /** 查询玩家可见的月卡阶梯列表（monthly_*）。 */
    List<MonthlyRewardVo> listMonthly(UUID uuid);

    /** 玩家领取月卡礼包。 */
    AjaxResult claim(String token, String rewardId);
}
