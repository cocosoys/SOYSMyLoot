package soys.soysmyloot.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.platform.PlatformYaml;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.ConfigUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.spring.entity.MyLootConfigFile;
import soys.soysmyloot.spring.entity.vo.MyLootConfigFileVo;
import soys.soysmyloot.spring.entity.vo.MyLootConfigSaveVo;
import soys.soysmyloot.spring.service.MyLootConfigService;
import soys.soysmyloot.spring.util.ConfigYamlPatcher;
import soys.soysmyloot.spring.util.Sync;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 插件配置业务实现。
 *
 * <p>自包含：文件一律按 owner 插件自己的 {@code getDataFolder()} 解析，
 * 不写死别的插件名、不经过 PluginManager 查找。</p>
 *
 * <p>读写差异：读用平台的 {@code PlatformYaml.load + ConfigUtil.toMap}；
 * 写不用 {@code ConfigUtil.toYaml + PlatformYaml.save}（那会全量覆盖、丢掉全部中文注释），
 * 改用 {@link ConfigYamlPatcher} 做文本级键级补丁，只替换值、完整保留注释。</p>
 */
public class MyLootConfigServiceImpl implements MyLootConfigService {

    private final SOYSMyLoot plugin;

    public MyLootConfigServiceImpl(SOYSMyLoot plugin) {
        this.plugin = plugin;
    }

    @Override
    public AjaxResult list() {
        try {
            List<MyLootConfigFileVo> rows = new ArrayList<>();
            for (MyLootConfigFile f : MyLootConfigFile.values()) {
                rows.add(new MyLootConfigFileVo(f));
            }
            return AjaxResult.success(rows);
        } catch (Throwable t) {
            return AjaxResult.error("读取配置文件清单失败: " + t.getMessage());
        }
    }

    @Override
    public AjaxResult load(String fileId) {
        try {
            MyLootConfigFile f = requireFile(fileId);
            File file = resolve(f);
            if (!file.isFile()) {
                return AjaxResult.error("配置文件不存在，请先启动一次插件生成默认配置: " + file.getPath());
            }
            YamlConfiguration yaml = PlatformYaml.load(file);
            Map<String, Object> data = ConfigUtil.toMap(yaml);
            // V7：领取反馈已改为按礼包配置，旧的全局 feedback 节不再暴露给前端
            data.remove("feedback");
            return AjaxResult.success(data);
        } catch (Throwable t) {
            return AjaxResult.error("读取配置失败: " + t.getMessage());
        }
    }

    @Override
    public AjaxResult save(String fileId, Map<String, Object> data) {
        try {
            MyLootConfigFile f = requireFile(fileId);
            if (data == null) {
                return AjaxResult.error("配置内容为空");
            }
            File file = resolve(f);
            if (!file.isFile()) {
                return AjaxResult.error("配置文件不存在，请先启动一次插件生成默认配置: " + file.getPath());
            }

            // V7：清理老版本残留的全局 feedback 节（含其标题注释），一次性完成迁移
            data.remove("feedback");
            ConfigYamlPatcher.removeSection(file, "feedback");
            // 文本级补丁写回：注释、空行、行内注释、键顺序全部保留
            ConfigYamlPatcher.write(file, data);

            // 自动热重载（网关控制器跑在线程池，必须回 Bukkit 主线程）
            String reloadWarn = "";
            try {
                Sync.onMain(plugin, () -> {
                    plugin.getConfigManager().loadSettings();
                    plugin.getScopeResolver().reload();
                    plugin.getLeaderboardManager().reload();
                    plugin.getMessageManager().reload();
                    return null;
                });
            } catch (Throwable rt) {
                reloadWarn = "（热重载未完全成功: " + rt.getMessage() + "，建议执行 /myloot reload config）";
                plugin.getLogger().log(Level.WARNING, "配置保存后热重载失败", rt);
            }

            String tip = "已保存，注释完整保留，并已自动热重载" + reloadWarn
                    + "。注意：存储后端切换、auto-save 定时周期等启动期配置需重启服务器才生效。";
            return AjaxResult.success(MyLootConfigSaveVo.of(f.getFile(), tip));
        } catch (Throwable t) {
            return AjaxResult.error("保存配置失败: " + t.getMessage());
        }
    }

    private MyLootConfigFile requireFile(String fileId) {
        MyLootConfigFile f = MyLootConfigFile.byId(fileId);
        if (f == null) {
            throw new IllegalArgumentException("未知配置文件 id: " + fileId);
        }
        return f;
    }

    /** owner 自己的数据目录，不写死任何插件名。 */
    private File resolve(MyLootConfigFile f) {
        return new File(plugin.getDataFolder(), f.getFile());
    }
}
