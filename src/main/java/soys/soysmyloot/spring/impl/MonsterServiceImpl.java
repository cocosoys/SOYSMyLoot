package soys.soysmyloot.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.PageUtils;
import com.github.cocosoys.mc.soyshttpovermc.util.TableDataInfo;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.model.MonsterEntry;
import soys.soysmyloot.spring.entity.po.MonsterSavePo;
import soys.soysmyloot.spring.entity.vo.MonsterVo;
import soys.soysmyloot.spring.service.MonsterService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 怪物配置业务实现：读 ConfigManager 内存，写 monsters.yml 后热重载。
 */
public class MonsterServiceImpl implements MonsterService {

    private final SOYSMyLoot plugin;

    public MonsterServiceImpl() {
        this.plugin = SOYSMyLoot.getPlugin(SOYSMyLoot.class);
    }

    @Override
    public TableDataInfo list(Integer pageNum, Integer pageSize, String keyword) {
        Map<String, MonsterEntry> all = plugin.getConfigManager().getMonsters();
        List<MonsterVo> rows = new ArrayList<>();
        String kw = keyword == null ? null : keyword.trim().toLowerCase();
        for (MonsterEntry m : all.values()) {
            if (kw != null && !kw.isEmpty()) {
                String id = m.getId() == null ? "" : m.getId().toLowerCase();
                String name = m.getDisplayName() == null ? "" : m.getDisplayName().toLowerCase();
                if (!id.contains(kw) && !name.contains(kw)) {
                    continue;
                }
            }
            MonsterVo v = new MonsterVo();
            v.setId(m.getId());
            v.setType(m.getType() == null ? null : m.getType().name());
            v.setDisplayName(m.getDisplayName());
            v.setMythic(m.getMythicId());
            rows.add(v);
        }
        return PageUtils.page(rows, pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
    }

    @Override
    public AjaxResult save(MonsterSavePo po) {
        if (po == null || po.getId() == null || po.getId().trim().isEmpty()) {
            return AjaxResult.error("id 不能为空");
        }
        try {
            File f = new File(plugin.getDataFolder(), "monsters.yml");
            if (!f.exists()) {
                plugin.saveResource("monsters.yml", false);
            }
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            String path = "monsters." + po.getId().trim();
            yml.set(path + ".type", po.getType() == null ? "ZOMBIE" : po.getType().toUpperCase());
            if (po.getName() != null && !po.getName().isEmpty()) {
                yml.set(path + ".name", po.getName());
            }
            yml.set(path + ".display-name", po.getDisplayName() == null ? po.getId() : po.getDisplayName());
            if (po.getMythic() != null && !po.getMythic().isEmpty()) {
                yml.set(path + ".mythic", po.getMythic());
            } else {
                yml.set(path + ".mythic", null);
            }
            yml.save(f);
            plugin.getConfigManager().loadMonsters();
            return AjaxResult.success();
        } catch (Exception e) {
            return AjaxResult.error("保存怪物失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            return AjaxResult.error("id 不能为空");
        }
        try {
            File f = new File(plugin.getDataFolder(), "monsters.yml");
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            yml.set("monsters." + id, null);
            yml.save(f);
            plugin.getConfigManager().loadMonsters();
            return AjaxResult.success();
        } catch (Exception e) {
            return AjaxResult.error("删除怪物失败: " + e.getMessage());
        }
    }
}
