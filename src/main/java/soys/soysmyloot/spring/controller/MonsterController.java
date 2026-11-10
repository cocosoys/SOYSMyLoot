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
import soys.soysmyloot.spring.entity.po.MonsterSavePo;
import soys.soysmyloot.spring.service.MonsterService;

/**
 * 怪物配置端点：/api/plugins/SOYSMyLoot/erp/monster/*。
 */
@RequestMapping("/erp/monster")
public class MonsterController {

    private final MonsterService monsterService;

    public MonsterController(MonsterService monsterService) {
        this.monsterService = monsterService;
    }

    @ApiName("怪物列表")
    @ApiPermission("soysmyloot:erp:monster:list")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                              @RequestParam(name = "pageSize", required = false) Integer pageSize,
                              @RequestParam(name = "keyword", required = false) String keyword) {
        return monsterService.list(pageNum, pageSize, keyword);
    }

    @ApiName("保存怪物")
    @ApiPermission("soysmyloot:erp:monster:edit")
    @PostMapping("/save")
    public AjaxResult save(@RequestBody MonsterSavePo po) {
        return monsterService.save(po);
    }

    @ApiName("删除怪物")
    @ApiPermission("soysmyloot:erp:monster:edit")
    @PostMapping("/delete")
    public AjaxResult delete(@RequestParam(name = "id") String id) {
        return monsterService.delete(id);
    }
}
