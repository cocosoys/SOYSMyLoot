package soys.soysmyloot.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPermission;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestParam;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import soys.soysmyloot.spring.service.MyLootConfigService;

import java.util.Map;

/**
 * 插件配置端点：/api/plugins/SOYSMyLoot/erp/config/*。
 *
 * <p>注解全部来自 {@code com.github.cocosoys.mc.soyshttpovermc.annotations}（平台自定义，
 * 不是 Spring 的）。权限节点沿用本项目既有的冒号命名风格，与其余 ERP 端点一致。</p>
 */
@RequestMapping("/erp/config")
public class ErpConfigController {

    private final MyLootConfigService configService;

    public ErpConfigController(MyLootConfigService configService) {
        this.configService = configService;
    }

    @ApiName("配置文件清单")
    @ApiPermission("soysmyloot:erp:config:list")
    @GetMapping("/files")
    public AjaxResult files() {
        return configService.list();
    }

    @ApiName("配置文件读取")
    @ApiPermission("soysmyloot:erp:config:query")
    @GetMapping("/load")
    public AjaxResult load(@RequestParam(name = "file", required = true) String file) {
        return configService.load(file);
    }

    @ApiName("配置文件保存")
    @ApiPermission("soysmyloot:erp:config:edit")
    @PostMapping("/save")
    public AjaxResult save(@RequestParam(name = "file", required = true) String file,
                           @RequestBody Map<String, Object> data) {
        return configService.save(file, data);
    }
}
