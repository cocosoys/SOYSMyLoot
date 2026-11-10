package soys.soysmyloot.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPermission;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestParam;
import com.github.cocosoys.mc.soyshttpovermc.util.TableDataInfo;
import soys.soysmyloot.spring.service.ClaimService;

/**
 * 领取记录端点：/api/plugins/SOYSMyLoot/erp/claim/*。
 */
@RequestMapping("/erp/claim")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @ApiName("领取记录")
    @ApiPermission("soysmyloot:erp:claim:list")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                              @RequestParam(name = "pageSize", required = false) Integer pageSize,
                              @RequestParam(name = "playerName", required = false) String playerName,
                              @RequestParam(name = "rewardId", required = false) String rewardId) {
        return claimService.list(pageNum, pageSize, playerName, rewardId);
    }
}
