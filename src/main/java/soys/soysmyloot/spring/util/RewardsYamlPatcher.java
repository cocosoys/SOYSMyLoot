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
 * rewards.yml 的「礼包节级」文本补丁写入器（V7）。
 *
 * <p>为什么不用 {@code YamlConfiguration.loadConfiguration()} + {@code save()}：
 * 那是全量写回，会丢掉 rewards.yml 里大量中文注释、行内注释、空行与键顺序，
 * 并把 conditions / rewards.items 从块列表改写成 c0 / i0 映射。本类改为文本级操作：</p>
 * <ol>
 *   <li>逐行解析缩进 / 键名 / 绝对路径，定位 {@code rewards.<id>} 节的行范围；</li>
 *   <li>节内已存在的标量键：<b>只替换冒号后的值片段</b>，缩进、键名、行内注释不变；</li>
 *   <li>节内已存在的列表键：flow 数组直接替换值；块列表整体替换（不改动其他节）；</li>
 *   <li>feedback 子节：逐键替换/补齐，或整块删除；新增时渲染带注释的完整块；</li>
 *   <li>缺失键：在所属祖先节末尾插入；其余行（含其他礼包、顶部注释、conditions/items 块）字节不变。</li>
 * </ol>
 *
 * <p>node 的 key 为相对 {@code rewards.<id>} 的路径（如 {@code name}、{@code rewards.money}、
 * {@code feedback}）；value 支持 String/Boolean/Number、List（渲染成块列表）、
 * Map（feedback 子节）、null（删除该键/子节）。未出现在 node 中的路径一律不动。</p>
 */
public final class RewardsYamlPatcher {

    private RewardsYamlPatcher() {
    }

    private static final Pattern KEY_LINE =
            Pattern.compile("^(\\s*)([^\\s#][^:]*?):(?:[ \\t]*(.*))?$");

    private static final Pattern NEEDS_QUOTE = Pattern.compile(
            "(?i)^(true|false|yes|no|on|off|null|~|-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?)$");

    /** 反馈子节的键顺序（与 rewards.yml 模板一致） */
    private static final String[] FEEDBACK_KEYS = {
            "enabled", "claim-sound", "sound-volume", "sound-pitch",
            "claim-particle", "particle-count", "particle-offset"
    };

    private static final class Row {
        final int indent;
        final String key;
        final String path;      // 绝对路径；块列表项为 null
        final String raw;
        final int colon;
        final boolean section;  // "key:" 后面没有标量值
        final boolean listItem;

        Row(int indent, String key, String path, String raw, int colon,
            boolean section, boolean listItem) {
            this.indent = indent;
            this.key = key;
            this.path = path;
            this.raw = raw;
            this.colon = colon;
            this.section = section;
            this.listItem = listItem;
        }
    }

    /** 一次编辑操作：把 [line, line + length) 替换为 newLines。 */
    private static final class Edit {
        final int line;
        final int length;
        final List<String> newLines;

        Edit(int line, int length, List<String> newLines) {
            this.line = line;
            this.length = length;
            this.newLines = newLines;
        }
    }

    // ============================================================
    //  公开 API
    // ============================================================

    /** 文件里是否已存在该礼包节（只读判断，供新增/编辑分流）。 */
    public static boolean contains(File file, String rewardId) throws IOException {
        List<String> lines = read(file);
        return findRow(parse(lines), "rewards." + rewardId) >= 0;
    }

    /**
     * 把 node 写入指定礼包节，保留文件其余内容。
     *
     * @return 实际改动/新增的行数之和，0 表示文件未变化
     */
    public static int write(File file, String rewardId, Map<String, Object> node) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("rewards.yml 不存在: " + (file == null ? "null" : file.getPath()));
        }
        if (rewardId == null || rewardId.trim().isEmpty()) {
            throw new IOException("礼包 ID 为空");
        }
        if (node == null) {
            throw new IOException("礼包配置为空");
        }

        String id = rewardId.trim();
        List<String> lines = read(file);
        List<Row> rows = parse(lines);
        String prefix = "rewards." + id;

        int rewardIdx = findRow(rows, prefix);
        if (rewardIdx < 0) {
            // 新增礼包：在 rewards 节末尾追加完整节（不动任何既有行）
            List<String> block = renderNewReward(id, node);
            int at = appendPosition(rows, lines);
            if (findRow(rows, "rewards") < 0) {
                lines.add("rewards:");
                at = lines.size();
            }
            lines.addAll(at, block);
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            return block.size();
        }

        int rewardIndent = rows.get(rewardIdx).indent;
        int end = sectionEnd(lines, rewardIdx, rewardIndent);

        // 节内已存在的键（相对路径 -> 行号）
        Map<String, Integer> keyRows = new LinkedHashMap<>();
        for (int i = rewardIdx + 1; i < end; i++) {
            Row r = rows.get(i);
            if (r == null || r.path == null || r.listItem) {
                continue;
            }
            if (r.path.startsWith(prefix + ".")) {
                keyRows.put(r.path.substring(prefix.length() + 1), i);
            }
        }

        List<Edit> edits = new ArrayList<>();
        // 插入点 -> 待插入行（同一插入点合并，保证顺序）
        Map<Integer, List<String>> insertions = new LinkedHashMap<>();
        int changes = 0;

        for (Map.Entry<String, Object> e : node.entrySet()) {
            String rel = e.getKey();
            Object value = e.getValue();
            Integer line = keyRows.get(rel);

            // ---- 删除 ----
            if (value == null) {
                if (line != null) {
                    int blockEnd = blockEnd(lines, line, rows.get(line).indent);
                    edits.add(new Edit(line, blockEnd - line, Collections.emptyList()));
                    changes++;
                }
                continue;
            }

            // ---- feedback 子节：单独处理（保留块内注释） ----
            if ("feedback".equals(rel)) {
                changes += planFeedback(lines, rows, line, prefix, value, end, edits, insertions);
                continue;
            }

            // ---- 列表：flow 数组替换值 / 块列表整体替换 ----
            if (value instanceof List) {
                List<String> rendered = renderList(rel, (List<?>) value,
                        line == null ? rewardIndent + 2 : rows.get(line).indent);
                if (line != null) {
                    Row r = rows.get(line);
                    int len = hasListItems(lines, line, r.indent)
                            ? blockEnd(lines, line, r.indent) - line : 1;
                    edits.add(new Edit(line, len, rendered));
                    changes++;
                } else {
                    queueInsert(insertions, lines, rows, keyRows, rel, rendered,
                            rewardIdx, rewardIndent, end, prefix);
                    changes++;
                }
                continue;
            }

            // ---- Map（非 feedback）：不支持，跳过 ----
            if (value instanceof Map) {
                continue;
            }

            // ---- 标量 ----
            String formatted = format(value);
            if (line != null) {
                String newRaw = replaceValue(rows.get(line).raw, rows.get(line).colon, formatted);
                if (!newRaw.equals(rows.get(line).raw)) {
                    edits.add(new Edit(line, 1, Collections.singletonList(newRaw)));
                    changes++;
                }
            } else {
                List<String> rendered = Collections.singletonList(
                        spaces(rewardIndent + 2 * depth(rel)) + lastKey(rel) + ": " + formatted);
                queueInsert(insertions, lines, rows, keyRows, rel, rendered,
                        rewardIdx, rewardIndent, end, prefix);
                changes++;
            }
        }

        // 合并插入操作
        for (Map.Entry<Integer, List<String>> en : insertions.entrySet()) {
            edits.add(new Edit(en.getKey(), 0, en.getValue()));
        }

        // 从后往前应用，避免行号漂移
        edits.sort((a, b) -> Integer.compare(b.line, a.line));
        for (Edit ed : edits) {
            for (int i = 0; i < ed.length; i++) {
                lines.remove(ed.line);
            }
            lines.addAll(ed.line, ed.newLines);
        }

        if (changes > 0) {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        }
        return changes;
    }

    /** 删除整个礼包节（含节内注释），保留文件其余内容。 */
    public static int delete(File file, String rewardId) throws IOException {
        if (rewardId == null || rewardId.trim().isEmpty()) {
            return 0;
        }
        List<String> lines = read(file);
        List<Row> rows = parse(lines);
        int idx = findRow(rows, "rewards." + rewardId.trim());
        if (idx < 0) {
            return 0;
        }
        int end = sectionEnd(lines, idx, rows.get(idx).indent);
        for (int i = end - 1; i >= idx; i--) {
            lines.remove(i);
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        return end - idx;
    }

    // ============================================================
    //  feedback 子节
    // ============================================================

    private static int planFeedback(List<String> lines, List<Row> rows, Integer line,
                                    String prefix, Object value, int sectionEndLine,
                                    List<Edit> edits, Map<Integer, List<String>> insertions) {
        if (!(value instanceof Map)) {
            // 非 Map 视为删除
            if (line != null) {
                int end = blockEnd(lines, line, rows.get(line).indent);
                edits.add(new Edit(line, end - line, Collections.emptyList()));
                return 1;
            }
            return 0;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> fb = (Map<String, Object>) value;

        if (line == null) {
            // 新增 feedback 块，插到礼包节末尾
            int indent = rows.get(findRewardLine(rows, prefix)).indent + 2;
            List<String> block = renderFeedbackBlock(fb, indent);
            insertions.computeIfAbsent(sectionEndLine, k -> new ArrayList<>()).addAll(block);
            return block.size();
        }

        // 已有 feedback 块：逐键替换/补齐，保留块内注释
        int fbIndent = rows.get(line).indent;
        int fbEnd = blockEnd(lines, line, fbIndent);
        Map<String, Integer> childRows = new LinkedHashMap<>();
        for (int i = line + 1; i < fbEnd; i++) {
            Row r = rows.get(i);
            if (r != null && r.path != null && !r.listItem
                    && r.path.startsWith(prefix + ".feedback.")) {
                childRows.put(r.path.substring((prefix + ".feedback.").length()), i);
            }
        }
        int changes = 0;
        List<String> missing = new ArrayList<>();
        for (String key : FEEDBACK_KEYS) {
            if (!fb.containsKey(key)) {
                continue;
            }
            Object v = fb.get(key);
            String formatted = v == null ? "''" : format(v);
            Integer cl = childRows.get(key);
            if (cl != null) {
                String newRaw = replaceValue(rows.get(cl).raw, rows.get(cl).colon, formatted);
                if (!newRaw.equals(rows.get(cl).raw)) {
                    edits.add(new Edit(cl, 1, Collections.singletonList(newRaw)));
                    changes++;
                }
            } else {
                missing.add(spaces(fbIndent + 2) + key + ": " + formatted);
                changes++;
            }
        }
        if (!missing.isEmpty()) {
            insertions.computeIfAbsent(fbEnd, k -> new ArrayList<>()).addAll(missing);
        }
        return changes;
    }

    private static List<String> renderFeedbackBlock(Map<String, Object> fb, int indent) {
        List<String> out = new ArrayList<>();
        String p = spaces(indent);
        out.add(p + "feedback:");
        // 说明注释放在块内，删除整个 feedback 节时可一并移除
        out.add(spaces(indent + 2) + "# 不配置本节 = 不播放；留空名称 = 不播放该项");
        for (String key : FEEDBACK_KEYS) {
            if (!fb.containsKey(key)) {
                continue;
            }
            Object v = fb.get(key);
            out.add(spaces(indent + 2) + key + ": " + (v == null ? "''" : format(v)));
        }
        return out;
    }

    // ============================================================
    //  行解析 / 定位
    // ============================================================

    private static List<String> read(File file) throws IOException {
        return new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
    }

    private static List<Row> parse(List<String> lines) {
        List<Row> rows = new ArrayList<>(lines.size());
        List<Integer> indentStack = new ArrayList<>();
        List<String> pathStack = new ArrayList<>();

        for (String raw : lines) {
            Matcher m = KEY_LINE.matcher(raw);
            if (!m.matches()) {
                rows.add(null); // 注释 / 空行
                continue;
            }
            int indent = m.group(1).length();
            String key = m.group(2).trim();
            boolean listItem = key.startsWith("-");
            if (listItem) {
                rows.add(new Row(indent, key, null, raw, raw.indexOf(':'), false, true));
                continue;
            }
            while (!indentStack.isEmpty() && indentStack.get(indentStack.size() - 1) >= indent) {
                indentStack.remove(indentStack.size() - 1);
                pathStack.remove(pathStack.size() - 1);
            }
            String parent = pathStack.isEmpty() ? "" : pathStack.get(pathStack.size() - 1);
            String path = parent.isEmpty() ? key : parent + "." + key;
            String rest = m.group(3) == null ? "" : m.group(3);
            boolean section = rest.trim().isEmpty() || rest.trim().startsWith("#");
            rows.add(new Row(indent, key, path, raw, raw.indexOf(':'), section, false));
            if (section) {
                indentStack.add(indent);
                pathStack.add(path);
            }
        }
        return rows;
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

    private static int findRewardLine(List<Row> rows, String prefix) {
        return Math.max(0, findRow(rows, prefix));
    }

    /** 节范围结束位置：最后一个「缩进更深的内容行」之后（不含尾部空行/注释）。 */
    private static int sectionEnd(List<String> lines, int startLine, int indent) {
        int i = startLine + 1;
        int last = startLine + 1;
        while (i < lines.size()) {
            String l = lines.get(i);
            if (l.trim().isEmpty() || l.trim().startsWith("#")) {
                i++;
                continue;
            }
            if (indentOf(l) > indent) {
                i++;
                last = i;
                continue;
            }
            break;
        }
        return last;
    }

    /** 键块范围（含其下更深缩进的所有行，用于整体替换/删除）。 */
    private static int blockEnd(List<String> lines, int line, int indent) {
        int i = line + 1;
        int last = line + 1;
        while (i < lines.size()) {
            String l = lines.get(i);
            if (l.trim().isEmpty() || l.trim().startsWith("#")) {
                i++;
                continue;
            }
            if (indentOf(l) > indent) {
                i++;
                last = i;
                continue;
            }
            break;
        }
        return last;
    }

    private static boolean hasListItems(List<String> lines, int line, int indent) {
        int end = blockEnd(lines, line, indent);
        for (int i = line + 1; i < end; i++) {
            if (lines.get(i).trim().startsWith("-")) {
                return true;
            }
        }
        return false;
    }

    /** 新增礼包时追加到 rewards 节末尾。 */
    private static int appendPosition(List<Row> rows, List<String> lines) {
        int idx = findRow(rows, "rewards");
        return idx < 0 ? lines.size() : sectionEnd(lines, idx, rows.get(idx).indent);
    }

    /** 为缺失键选择插入点：优先最长已存在祖先节末尾，否则礼包节末尾。 */
    private static void queueInsert(Map<Integer, List<String>> insertions,
                                    List<String> lines, List<Row> rows,
                                    Map<String, Integer> keyRows, String rel,
                                    List<String> rendered, int rewardIdx,
                                    int rewardIndent, int sectionEndLine, String prefix) {
        String[] segs = rel.split("\\.");
        int ancestorRows = 0;
        Integer ancestorLine = null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.length - 1; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(segs[i]);
            Integer l = keyRows.get(sb.toString());
            if (l != null && rows.get(l) != null && rows.get(l).section) {
                ancestorLine = l;
                ancestorRows = i + 1;
            }
        }
        int pos = ancestorLine == null ? sectionEndLine
                : blockEnd(lines, ancestorLine, rows.get(ancestorLine).indent);
        int indent = ancestorLine == null ? rewardIndent + 2
                : rows.get(ancestorLine).indent + 2;

        List<String> block = new ArrayList<>();
        // 缺少的中间节：先渲染出来
        for (int i = ancestorRows; i < segs.length - 1; i++) {
            block.add(spaces(indent + 2 * (i - ancestorRows)) + segs[i] + ":");
        }
        int keyIndent = indent + 2 * (segs.length - 1 - ancestorRows);
        // rendered 是带绝对缩进的行；整体平移到 keyIndent，保留块列表的相对缩进
        int base = rendered.isEmpty() ? 0 : indentOf(rendered.get(0));
        for (String l : rendered) {
            int relIndent = Math.max(0, indentOf(l) - base);
            block.add(spaces(keyIndent + relIndent) + l.trim());
        }
        insertions.computeIfAbsent(pos, k -> new ArrayList<>()).addAll(block);
    }

    // ============================================================
    //  渲染
    // ============================================================

    /** 新增礼包：渲染完整节（不经过 YamlConfiguration，保留手写风格）。 */
    private static List<String> renderNewReward(String id, Map<String, Object> node) {
        List<String> out = new ArrayList<>();
        out.add("  " + id + ":");
        for (Map.Entry<String, Object> e : node.entrySet()) {
            String rel = e.getKey();
            Object v = e.getValue();
            if (v == null || "feedback".equals(rel)) {
                continue;
            }
            if (v instanceof List) {
                out.addAll(renderList(rel, (List<?>) v, 4));
            } else if (!(v instanceof Map)) {
                String[] segs = rel.split("\\.");
                for (int i = 0; i < segs.length - 1; i++) {
                    out.add(spaces(4 + 2 * i) + segs[i] + ":");
                }
                out.add(spaces(4 + 2 * (segs.length - 1)) + lastKey(rel) + ": " + format(v));
            }
        }
        Object fb = node.get("feedback");
        if (fb instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) fb;
            out.addAll(renderFeedbackBlock(m, 4));
        }
        return out;
    }

    private static List<String> renderList(String rel, List<?> list, int indent) {
        String key = lastKey(rel);
        String p = spaces(indent);
        List<String> out = new ArrayList<>();
        if (list.isEmpty()) {
            out.add(p + key + ": []");
            return out;
        }
        out.add(p + key + ":");
        for (Object o : list) {
            if (o instanceof Map) {
                // 对象列表（如 rewards.items）：渲染成 "- material: DIAMOND" 形式
                boolean first = true;
                for (Map.Entry<?, ?> en : ((Map<?, ?>) o).entrySet()) {
                    if (en.getValue() == null) {
                        continue;
                    }
                    String line = en.getKey() + ": " + format(en.getValue());
                    out.add(spaces(indent + 2) + (first ? "- " : "  ") + line);
                    first = false;
                }
            } else {
                String f = o == null ? "''" : format(o);
                out.add(spaces(indent + 2) + "- " + f);
            }
        }
        return out;
    }

    private static String replaceValue(String raw, int colon, String formatted) {
        int p = colon + 1;
        while (p < raw.length() && (raw.charAt(p) == ' ' || raw.charAt(p) == '\t')) {
            p++;
        }
        String prefix = raw.substring(0, p);
        String rest = raw.substring(p);
        int commentAt = findInlineComment(rest);
        String comment = commentAt < 0 ? "" : rest.substring(commentAt);
        return prefix + formatted + comment;
    }

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
                sb.append(format(list.get(i)));
            }
            return sb.append(']').toString();
        }
        return quoteIfNeeded(String.valueOf(v));
    }

    private static String quoteIfNeeded(String s) {
        if (s.isEmpty()) {
            return "''";
        }
        boolean need = !s.equals(s.trim());
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
        return need ? "'" + s.replace("'", "''") + "'" : s;
    }

    private static int depth(String rel) {
        return rel.split("\\.").length;
    }

    private static String lastKey(String rel) {
        String[] segs = rel.split("\\.");
        return segs[segs.length - 1];
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
