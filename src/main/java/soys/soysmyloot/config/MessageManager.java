package soys.soysmyloot.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysmyloot.SOYSMyLoot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 语言文件管理器。
 * <p>负责从 {@code plugins/SOYSMyLoot/language/&lt;locale&gt;.yml} 加载语言文件，
 * 支持位置占位符 {@code {0}}、{@code {1}} ... 的替换。</p>
 * <p>语言文件采用平铺点路径键（如 {@code soysmyloot.command.reward.not-found}），
 * 与 SOYSHTTPOverMC 的 i18n 规范一致。颜色代码直接使用 § 符号，无需翻译。</p>
 */
public class MessageManager {

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private FileConfiguration lang;
    private String locale = "zh_cn";

    public MessageManager(SOYSMyLoot plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        this.locale = resolveLocale();

        File langDir = new File(plugin.getDataFolder(), "language");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        File file = new File(langDir, locale + ".yml");
        if (!file.exists()) {
            // 首次启动：从 jar 复制默认语言文件到磁盘
            plugin.saveResource("language/" + locale + ".yml", false);
        }

        // 加载磁盘文件
        this.lang = YamlConfiguration.loadConfiguration(file);

        // 叠加 jar 内置默认（仅当磁盘文件缺键时兜底）
        FileConfiguration defaults = loadInternalDefaults(locale);
        if (defaults != null) {
            for (String key : defaults.getKeys(true)) {
                if (!lang.contains(key, true) && !defaults.isConfigurationSection(key)) {
                    lang.set(key, defaults.get(key));
                }
            }
        }
    }

    /** 解析当前语言代码，默认 zh_cn */
    private String resolveLocale() {
        String loc = config != null ? config.getLocale() : null;
        if (loc == null || loc.trim().isEmpty()) {
            return "zh_cn";
        }
        return loc.trim().toLowerCase();
    }

    private FileConfiguration loadInternalDefaults(String loc) {
        String resourcePath = "language/" + loc + ".yml";
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                // 请求的语言 jar 内没有，回退到 zh_cn
                if (!"zh_cn".equals(loc)) {
                    return loadInternalDefaults("zh_cn");
                }
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    // ================================================================
    //  读取
    // ================================================================

    /**
     * 读取一条消息，按位置替换 {0} {1} ... 占位符。
     */
    public String get(String key, Object... args) {
        String raw;
        if (lang == null) {
            raw = null;
        } else {
            raw = lang.getString(key);
        }
        if (raw == null) {
            return "§c[missing: " + key + "]";
        }
        return format(raw, args);
    }

    public String get(String key) {
        return get(key, new Object[0]);
    }

    /**
     * 位置占位符替换：{0} → args[0]，{1} → args[1] ...
     */
    private String format(String raw, Object... args) {
        if (args == null || args.length == 0) {
            return raw;
        }
        String result = raw;
        for (int i = 0; i < args.length; i++) {
            String val = args[i] == null ? "" : String.valueOf(args[i]);
            result = result.replace("{" + i + "}", val);
        }
        return result;
    }

    // ================================================================
    //  发送
    // ================================================================

    public void send(CommandSender sender, String key) {
        send(sender, key, new Object[0]);
    }

    public void send(CommandSender sender, String key, Object... args) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(get(key, args));
    }

    // ================================================================
    //  属性
    // ================================================================

    /** 当前语言代码 */
    public String getLocale() {
        return locale;
    }
}
