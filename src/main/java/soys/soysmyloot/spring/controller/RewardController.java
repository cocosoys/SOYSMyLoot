package soys.soysmyloot.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPermission;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestParam;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.TableDataInfo;
import soys.soysmyloot.spring.entity.po.CopyHandPo;
import soys.soysmyloot.spring.entity.po.RewardGivePo;
import soys.soysmyloot.spring.entity.po.RewardItemsSavePo;
import soys.soysmyloot.spring.entity.po.RewardResetPo;
import soys.soysmyloot.spring.entity.po.RewardSavePo;
import soys.soysmyloot.spring.entity.po.RewardTogglePo;
import soys.soysmyloot.spring.service.RewardService;

/**
 * 礼包管理端点：路由 /api/plugins/SOYSMyLoot/erp/reward/*。
 * 仅声明接口并转发 {@link RewardService}，不含任何业务实现。
 */
@RequestMapping("/erp/reward")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @ApiName("礼包列表")
    @ApiPermission("soysmyloot:erp:reward:list")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                              @RequestParam(name = "pageSize", required = false) Integer pageSize,
                              @RequestParam(name = "keyword", required = false) String keyword,
                              @RequestParam(name = "stage", required = false) String stage) {
        return rewardService.list(pageNum, pageSize, keyword, stage);
    }

    @ApiName("礼包详情")
    @ApiPermission("soysmyloot:erp:reward:list")
    @GetMapping("/get")
    public AjaxResult get(@RequestParam(name = "id") String id) {
        return rewardService.get(id);
    }

    @ApiName("保存礼包")
    @ApiPermission("soysmyloot:erp:reward:edit")
    @PostMapping("/save")
    public AjaxResult save(@RequestBody RewardSavePo po) {
        return rewardService.save(po);
    }

    @ApiName("删除礼包")
    @ApiPermission("soysmyloot:erp:reward:edit")
    @PostMapping("/delete")
    public AjaxResult delete(@RequestParam(name = "id") String id) {
        return rewardService.delete(id);
    }

    @ApiName("启用/禁用礼包")
    @ApiPermission("soysmyloot:erp:reward:edit")
    @PostMapping("/toggle")
    public AjaxResult toggle(@RequestBody RewardTogglePo po) {
        return rewardService.toggle(po);
    }

    @ApiName("代发奖励")
    @ApiPermission("soysmyloot:erp:reward:give")
    @PostMapping("/give")
    public AjaxResult give(@RequestBody RewardGivePo po) {
        return rewardService.give(po);
    }

    @ApiName("重置领取")
    @ApiPermission("soysmyloot:erp:reward:reset")
    @PostMapping("/reset-claim")
    public AjaxResult resetClaim(@RequestBody RewardResetPo po) {
        return rewardService.resetClaim(po);
    }

    @ApiName("读取礼包物品栏")
    @ApiPermission("soysmyloot:erp:reward:list")
    @GetMapping("/items")
    public AjaxResult items(@RequestParam(name = "rewardId") String rewardId) {
        return rewardService.items(rewardId);
    }

    @ApiName("保存礼包物品栏")
    @ApiPermission("soysmyloot:erp:reward:edit")
    @PostMapping("/items/save")
    public AjaxResult saveItems(@RequestBody RewardItemsSavePo po) {
        return rewardService.saveItems(po);
    }

    @ApiName("从在线玩家背包拷贝物品")
    @ApiPermission("soysmyloot:erp:reward:edit")
    @PostMapping("/copy-hand")
    public AjaxResult copyHand(@RequestBody CopyHandPo po) {
        return rewardService.copyHand(po);
    }

    @ApiName("领取反馈枚举候选")
    @ApiPermission("soysmyloot:erp:reward:list")
    @GetMapping("/feedback-options")
    public AjaxResult feedbackOptions() {
        return rewardService.feedbackOptions();
    }
}
