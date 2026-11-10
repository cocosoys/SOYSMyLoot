package soys.soysmyloot.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.Anonymous;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestParam;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import soys.soysmyloot.spring.entity.po.PlayerClaimPo;
import soys.soysmyloot.spring.service.PlayerService;

import java.util.UUID;

/**
 * 玩家侧月卡端点：/api/plugins/SOYSMyLoot/player/*
 *
 * <p>鉴权方式：一次性 token（游戏内 /monthly 命令颁发）。整类标 {@code @Anonymous}：
 * 网关认证门放行、不做权限判定，由 token 自行校验，因此不依赖 ERP 后台登录态，
 * 也不占用 {@code /erp/*} 管理命名空间。</p>
 *
 * <p>所有方法零业务逻辑，转发到 PlayerService。</p>
 */
@Anonymous
@RequestMapping("/player")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @ApiName("月卡列表")
    @GetMapping("/monthly-list")
    public AjaxResult monthlyList(@RequestParam(name = "token") String token) {
        UUID uuid = playerService.resolveToken(token);
        if (uuid == null) {
            return AjaxResult.error("链接已过期，请重新在游戏内输入 /monthly");
        }
        return AjaxResult.success(playerService.listMonthly(uuid));
    }

    @ApiName("领取月卡")
    @PostMapping("/claim")
    public AjaxResult claim(@RequestBody PlayerClaimPo po) {
        if (po == null) {
            return AjaxResult.error("请求体不能为空");
        }
        return playerService.claim(po.getToken(), po.getRewardId());
    }
}
