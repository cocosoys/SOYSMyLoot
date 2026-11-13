package soys.soysmyloot.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;

import java.util.Map;

/**
 * 插件配置（ERP「插件配置」菜单）业务接口。
 */
public interface MyLootConfigService {

    /** 可编辑配置文件清单。 */
    AjaxResult list();

    /** 读取指定配置文件，返回 YAML -> Map。 */
    AjaxResult load(String fileId);

    /** 写回指定配置文件（保留注释），并触发热重载。 */
    AjaxResult save(String fileId, Map<String, Object> data);
}
