package soys.soysmyloot.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.TableDataInfo;
import soys.soysmyloot.spring.entity.po.CopyHandPo;
import soys.soysmyloot.spring.entity.po.RewardGivePo;
import soys.soysmyloot.spring.entity.po.RewardItemsSavePo;
import soys.soysmyloot.spring.entity.po.RewardResetPo;
import soys.soysmyloot.spring.entity.po.RewardSavePo;
import soys.soysmyloot.spring.entity.po.RewardTogglePo;

/**
 * 礼包（奖励）管理业务接口。
 */
public interface RewardService {

    /** 分页查询礼包列表 */
    TableDataInfo list(Integer pageNum, Integer pageSize, String keyword, String stage);

    /** 礼包详情 */
    AjaxResult get(String id);

    /** 新建/编辑礼包（全量覆写 rewards.yml 对应节点后热重载） */
    AjaxResult save(RewardSavePo po);

    /** 删除礼包 */
    AjaxResult delete(String id);

    /** 启用/禁用 */
    AjaxResult toggle(RewardTogglePo po);

    /** 代发奖励（强制给在线/离线玩家发奖，不校验条件） */
    AjaxResult give(RewardGivePo po);

    /** 重置某玩家对某礼包的领取记录 */
    AjaxResult resetClaim(RewardResetPo po);

    /** 读取礼包物品栏 */
    AjaxResult items(String rewardId);

    /** 保存礼包物品栏 */
    AjaxResult saveItems(RewardItemsSavePo po);

    /** 从在线玩家背包拷贝物品 */
    AjaxResult copyHand(CopyHandPo po);

    /** 领取反馈枚举候选（Spigot 1.12.2 的 Sound / Particle 名称） */
    AjaxResult feedbackOptions();
}
