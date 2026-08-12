package soys.soysmyloot.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本处理工具。
 * <p>负责颜色代码翻译与 {@code {key}} 形式的占位符替换。</p>
 */
public final class Text {

    private Text() {
    }

    /**
     * 翻译 {@code &} 颜色代码。
     */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * 批量翻译颜色代码。
     */
    public static List<String> color(List<String> input) {
        List<String> result = new ArrayList<>();
        if (input == null) {
            return result;
        }
        for (String line : input) {
            result.add(color(line));
        }
        return result;
    }

    /**
     * 去除颜色代码。
     */
    public static String strip(String input) {
        return input == null ? "" : ChatColor.stripColor(color(input));
    }

    /**
     * 使用键值对替换 {@code {key}} 占位符。
     */
    public static String replace(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }

    /**
     * 先替换占位符，再转换颜色代码。
     */
    public static String format(String input, Map<String, String> placeholders) {
        return color(replace(input, placeholders));
    }

    /**
     * 发送消息，空字符串会被忽略。
     */
    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(color(message));
    }

    /**
     * 批量发送消息。
     */
    public static void send(CommandSender sender, List<String> messages) {
        if (sender == null || messages == null) {
            return;
        }
        for (String message : messages) {
            send(sender, message);
        }
    }

    /**
     * 拼接命令参数为一个字符串。
     */
    public static String join(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    /**
     * 安全地将字符串解析为 int，失败时返回默认值。
     */
    public static int parseInt(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }
}
