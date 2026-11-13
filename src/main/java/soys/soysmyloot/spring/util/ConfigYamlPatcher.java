package soys.soysmyloot.spring.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 保留注释的 YAML 补丁写入器。
 *
 * <p>为什么不用 {@code ConfigUtil.toYaml(data)} + {@code PlatformYaml.save(...)}：
 *  Bukkit / SnakeYAML 序列化是「全量写回」，会把 config.yml 里大量中文注释、
 *  行内注释、空行、键顺序全部丢掉。本项目要求配置可视化编辑后注释必须原样保留，
 *  因此这里改为文本级键级补丁：</p>
 *  <ol>
 *    <li>逐行解析出「缩进 / 键名 / 完整路径 / 冒号位置」，用缩进栈还原层级；</li>
 *    <li>把提交上来的 Map 展平成 路径-&gt;值；</li>
 *    <li>对已存在的键，<b>只替换冒号之后的值片段</b>，缩进、键名、行内注释一律不动；</li>
 *    <li>对新增（或 live 旧配置缺失）的键，插入到所属节的末尾，缩进按层级生成；</li>
 *    <li>注释行、空行、未被提交的键全部原样保留（不删除任何用户自定义项）。</li>
 *  </ol>
 *
 * <p>适用范围：本项目 config.yml 这类「规整映射 + flow 数组/标量」配置。
 *  块标量（{@code |} / {@code &gt;}）、锚点、复杂块序列不在范围内（当前配置未使用）。</p>
 */
public final class ConfigYamlPatcher {

    private ConfigYamlPatcher() {
    }

    /** 匹配 "缩进 + 键: 值" 行；键名不含冒号，值里可以含冒号（如 JDBC URL）。 */
    private static final Pattern KEY_LINE =
            Pattern.compile("^(\\s*)([^\\s#][^:]*?):(?:[ \\t]*(.*))?$");

    /** YAML 关键字 / 数字样式：作为字符串时必须加引号，否则类型会被改变。 */
    private static final Pattern NEEDS_QUOTE = Pattern.compile(
            "(?i)^(true|false|yes|no|on|off|null|~|-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?)$");

    /** 行模型。key == null 表示注释行 / 空行 / 块序列项。 */
    private static final class Row {
        final int indent;
        final String key;
        final String path;
        final String raw;
        final int colon;
        final boolean section; // "key:" 后面没有标量值（子节 / 块序列起始）

        Row(int indent, String key, String path, String raw, int colon, boolean section) {
            this.indent = indent;
            this.key = key;
            this.path = path;
            this.raw = raw;
            this.colon = colon;
            this.section = section;
        }
    }

    /**
     * 删除一个顶层节及其内部所有行，并一并删除紧邻其上方的连续注释行。
     *
     * <p>用于配置迁移：V7 把全局 feedback 节改为按礼包配置后，需要把老版本
     * config.yml 里残留的整节（含标题注释）清掉，避免成为死配置。
     * 节与下一个节之间的空行会保留。</p>
     *
     * @return 删除的行数，0 表示该节不存在
     */
    public static int removeSection(File file, String section) throws IOException {
        if (file == null || !file.isFile() || section == null || section.isEmpty()) {
            return 0;
        }
        List<String> lines = new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        List<Row> rows = parse(lines);
        int idx = findRow(rows, section);
        if (idx < 0) {
            return 0;
        }
        int indent = rows.get(idx).indent;
        int end = idx + 1;
        for (int i = idx + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.trim().isEmpty() || l.trim().startsWith("#")) {
                continue;
            }
            if (indentOf(l) > indent) {
                end = i + 1;
                continue;
            }
            break;
        }
        // 向上回溯连续的注释行（遇到空行即停），把该节的标题注释一并清掉
        int start = idx;
        while (start > 0 && lines.get(start - 1).trim().startsWith("#")) {
            start--;
        }
        for (int i = end - 1; i >= start; i--) {
            lines.remove(i);
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        return end - start;
    }

    /**
     * 把 data 写回 file，保留注释。
     *
     * @return 实际发生改动的行数 / 新增键数之和，0 表示文件未变化
     */
    public static int write(File file, Map<String, Object> data) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("配置文件不存在: " + (file == null ? "null" : file.getPath()));
        }
        if (data == null) {
            throw new IOException("配置内容为空");
        }

        List<String> lines = new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        List<Row> rows = parse(lines);
        Map<String, Object> flat = new LinkedHashMap<>();
        flatten("", data, flat);

        int changes = 0;
        java.util.Set<String> handled = new java.util.HashSet<>();

        // ① 已存在的键：只替换值片段
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r == null || r.path == null) {
                continue;
            }
            if (r.section) {
                // 子节本身不改写值；若提交的 Map 里该节是空对象，视为已处理，
                // 避免步骤 ② 又插入一个同名重复节。
                if (flat.containsKey(r.path) && flat.get(r.path) instanceof Map) {
                    handled.add(r.path);
                }
                continue;
            }
            if (!flat.containsKey(r.path)) {
                continue;
            }
            Object value = flat.get(r.path);
            if (value instanceof Map) {
                handled.add(r.path);
                continue;
            }
            String formatted = format(value);
            if (formatted == null) {
                handled.add(r.path);
                continue;
            }
            String newLine = replaceValue(r.raw, r.colon, formatted);
            if (!newLine.equals(r.raw)) {
                lines.set(i, newLine);
                changes++;
            }
            handled.add(r.path);
        }

        // ② 缺失的键：插入到所属节末尾（或文件末尾追加新节）
        Map<Integer, List<String>> insertions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            if (handled.contains(e.getKey())) {
                continue;
            }
            int pos = findInsertPosition(rows, lines, e.getKey());
            List<String> block = renderNewKey(e.getKey(), e.getValue(), rows, lines, pos);
            if (block.isEmpty()) {
                continue;
            }
            insertions.computeIfAbsent(pos, k -> new ArrayList<>()).addAll(block);
            changes++;
        }
        if (!insertions.isEmpty()) {
            List<Integer> positions = new ArrayList<>(insertions.keySet());
            Collections.sort(positions, Collections.reverseOrder());
            for (Integer p : positions) {
                lines.addAll(p, insertions.get(p));
            }
        }

        if (changes > 0) {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        }
        return changes;
    }

    // ================= 解析 =================

    private static List<Row> parse(List<String> lines) {
        List<Row> rows = new ArrayList<>(lines.size());
        List<Integer> indentStack = new ArrayList<>();
        List<String> pathStack = new ArrayList<>();

        for (String raw : lines) {
            Matcher m = KEY_LINE.matcher(raw);
            if (!m.matches()) {
                rows.add(null);
                continue;
            }
            int indent = m.group(1).length();
            while (!indentStack.isEmpty() && indentStack.get(indentStack.size() - 1) >= indent) {
                indentStack.remove(indentStack.size() - 1);
                pathStack.remove(pathStack.size() - 1);
            }
            String parent = pathStack.isEmpty() ? "" : pathStack.get(pathStack.size() - 1);
            String key = m.group(2).trim();
            String path = parent.isEmpty() ? key : parent + "." + key;
            String rest = m.group(3) == null ? "" : m.group(3);
            boolean section = rest.trim().isEmpty();
            int colon = raw.indexOf(':');

            rows.add(new Row(indent, key, path, raw, colon, section));
            if (section) {
                indentStack.add(indent);
                pathStack.add(path);
            }
        }
        return rows;
    }

    private static void flatten(String prefix, Map<String, Object> map, Map<String, Object> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map && !((Map<?, ?>) v).isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) v;
                flatten(path, child, out);
            } else {
                out.put(path, v);
            }
        }
    }

    // ================= 值替换 =================

    private static String replaceValue(String raw, int colon, String formatted) {
        int p = colon + 1;
        while (p < raw.length() && (raw.charAt(p) == ' ' || raw.charAt(p) == '\t')) {
            p++;
        }
        String prefix = raw.substring(0, p);
        String rest = raw.substring(p);
        int commentAt = findInlineComment(rest);
        String comment = commentAt < 0 ? "" : rest.substring(commentAt);
        // 保留注释前的空白（comment 自身以空白开头），值直接顶到冒号后
        return prefix + formatted + comment;
    }

    /** 找到行内注释起点（含其前导空白）；找不到返回 -1。引号内的 # 不算注释。 */
    private static int findInlineComment(String rest) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble
                    && i > 0 && (rest.charAt(i - 1) == ' ' || rest.charAt(i - 1) == '\t')) {
                int s = i - 1;
                while (s >= 0 && (rest.charAt(s) == ' ' || rest.charAt(s) == '\t')) {
                    s--;
                }
                return s + 1;
            }
        }
        return -1;
    }

    // ================= 新增键插入 =================

    /**
     * 找到缺失键应插入的位置：最长已存在祖先节之后（节内所有行结束处）；
     * 若连顶层节都不存在，插到文件末尾。
     */
    private static int findInsertPosition(List<Row> rows, List<String> lines, String path) {
        String[] segs = path.split("\\.");
        int ancestorRow = -1;
        for (int i = 1; i < segs.length; i++) {
            int idx = findRow(rows, join(segs, i));
            if (idx >= 0) {
                ancestorRow = idx;
            } else {
                break;
            }
        }

        if (ancestorRow < 0) {
            return lines.size(); // 顶层新节 -> 文件末尾
        }
        int secIndent = rows.get(ancestorRow).indent;
        int end = ancestorRow + 1;
        while (end < lines.size()) {
            String l = lines.get(end);
            if (l.trim().isEmpty() || l.trim().startsWith("#")) {
                end++;
                continue;
            }
            if (indentOf(l) > secIndent) {
                end++;
                continue;
            }
            break;
        }
        return end;
    }

    private static int findRow(List<Row> rows, String path) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r != null && r.path != null && r.path.equals(path)) {
                return i;
            }
        }
        return -1;
    }

    private static String join(String[] segs, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(segs[i]);
        }
        return sb.toString();
    }

    /** 渲染缺失键的 YAML 块（相对祖先节的剩余键链）。 */
    private static List<String> renderNewKey(String path, Object value, List<Row> rows,
                                             List<String> lines, int insertPos) {
        String[] segs = path.split("\\.");
        String[] remaining = determineRemaining(rows, segs);
        if (remaining.length == 0) {
            return Collections.emptyList();
        }
        int baseIndent = baseIndentFor(rows, lines, insertPos, segs.length - remaining.length);
        List<String> out = new ArrayList<>();
        renderChain(remaining, 0, value, baseIndent, out);
        return out;
    }

    /** 从完整路径里切掉「已存在的最长祖先」，得到需要新建的键链。 */
    private static String[] determineRemaining(List<Row> rows, String[] segs) {
        int existing = 0;
        for (int i = 1; i < segs.length; i++) {
            if (findRow(rows, join(segs, i)) >= 0) {
                existing = i;
            } else {
                break;
            }
        }
        String[] out = new String[segs.length - existing];
        System.arraycopy(segs, existing, out, 0, out.length);
        return out;
    }

    private static int baseIndentFor(List<Row> rows, List<String> lines, int insertPos, int existingDepth) {
        if (existingDepth <= 0) {
            return 0;
        }
        // 插入点之前、缩进最浅且与祖先节同级子项一致的行，取它的缩进
        int best = 2 * existingDepth;
        for (int i = insertPos - 1; i >= 0 && i >= insertPos - 40; i--) {
            String l = lines.get(i);
            if (l.trim().isEmpty() || l.trim().startsWith("#")) {
                continue;
            }
            int ind = indentOf(l);
            if (ind > 0) {
                best = ind;
                break;
            }
        }
        return best;
    }

    private static void renderChain(String[] segs, int idx, Object value, int indent, List<String> out) {
        String pad = spaces(indent);
        if (idx < segs.length - 1) {
            out.add(pad + segs[idx] + ":");
            renderChain(segs, idx + 1, value, indent + 2, out);
            return;
        }
        String key = segs[idx];
        if (value instanceof Map) {
            out.add(pad + key + ":");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String f = format(e.getValue());
                out.add(spaces(indent + 2) + e.getKey() + ": " + (f == null ? "{}" : f));
            }
        } else if (value == null) {
            out.add(pad + key + ":");
        } else {
            String f = format(value);
            out.add(pad + key + ": " + (f == null ? "{}" : f));
        }
    }

    // ================= 值格式化 =================

    /** 返回 null 表示该值不应作为标量/flow 写回（如非空 Map）。 */
    private static String format(Object v) {
        if (v == null) {
            return "''";
        }
        if (v instanceof Boolean || v instanceof Number) {
            return String.valueOf(v);
        }
        if (v instanceof List) {
            List<?> list = (List<?>) v;
            if (list.isEmpty()) {
                return "[]";
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                String one = format(list.get(i));
                sb.append(one == null ? "''" : one);
            }
            return sb.append(']').toString();
        }
        if (v instanceof Map) {
            return null;
        }
        return quoteIfNeeded(String.valueOf(v));
    }

    private static String quoteIfNeeded(String s) {
        if (s.isEmpty()) {
            return "''";
        }
        boolean need = false;
        if (!s.equals(s.trim())) {
            need = true;
        }
        if (NEEDS_QUOTE.matcher(s).matches()) {
            need = true;
        }
        if (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            need = true;
        }
        for (int i = 0; i < s.length() && !need; i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"' || c == ',' || c == '[' || c == ']' || c == '{' || c == '}'
                    || c == '&' || c == '*' || c == '!' || c == '|' || c == '>' || c == '%'
                    || c == '@' || c == '`' || c == '#') {
                need = true;
                break;
            }
            if (c == ':' && i + 1 < s.length() && s.charAt(i + 1) == ' ') {
                need = true;
                break;
            }
        }
        if (!need) {
            return s;
        }
        return "'" + s.replace("'", "''") + "'";
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static String spaces(int n) {
        StringBuilder sb = new StringBuilder(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
