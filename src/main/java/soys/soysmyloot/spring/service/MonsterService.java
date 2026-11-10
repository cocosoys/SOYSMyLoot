package soys.soysmyloot.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.TableDataInfo;
import soys.soysmyloot.spring.entity.po.MonsterSavePo;

/**
 * 怪物配置管理业务接口。
 */
public interface MonsterService {

    /** 分页查询怪物列表 */
    TableDataInfo list(Integer pageNum, Integer pageSize, String keyword);

    /** 新建/编辑怪物 */
    AjaxResult save(MonsterSavePo po);

    /** 删除怪物 */
    AjaxResult delete(String id);
}
