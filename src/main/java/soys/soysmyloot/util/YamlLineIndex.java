package soys.soysmyloot.util;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML 行号索引器。
 * <p>
 * 借助 SnakeYAML 的 {@code compose()} 直接解析节点树（不经 Bukkit 的 YamlConfiguration），
 * 遍历并记录「配置路径 → 行号(1 起始)」映射，供配置校验器在报错时标注具体行号。
 * 解析失败时返回空映射（行号信息为尽力而为，缺失时不阻断加载）。
 * </p>
 * <p>
 * 路径格式示例：{@code monsters.example_zombie}、{@code rewards.tier_gold.conditions.0}、
 * {@code rewards.tier_gold.rewards.items.1}。
 * </p>
 */
public final class YamlLineIndex {

    private YamlLineIndex() {
    }

    /** 解析指定文件，返回 路径->行号 映射（行号从 1 开始；缺失/解析失败返回空映射） */
    public static Map<String, Integer> index(File file) {
        Map<String, Integer> out = new HashMap<>();
        if (file == null || !file.exists()) {
            return out;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml();
            Node root = yaml.compose(reader);
            if (root instanceof MappingNode) {
                walk("", (MappingNode) root, out);
            }
        } catch (Exception e) {
            // 行号信息仅作辅助，解析失败不影响主流程
        }
        return out;
    }

    private static void walk(String prefix, MappingNode node, Map<String, Integer> out) {
        for (NodeTuple tuple : node.getValue()) {
            Node keyNode = tuple.getKeyNode();
            String key = asString(keyNode);
            if (key == null) {
                continue;
            }
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            // 用 key 所在行作为该配置项的行号（最贴近用户编辑位置）
            out.put(path, keyNode.getStartMark().getLine() + 1);

            Node valueNode = tuple.getValueNode();
            if (valueNode instanceof MappingNode) {
                walk(path, (MappingNode) valueNode, out);
            } else if (valueNode instanceof SequenceNode) {
                walkSequence(path, (SequenceNode) valueNode, out);
            }
        }
    }

    private static void walkSequence(String prefix, SequenceNode seq, Map<String, Integer> out) {
        int i = 0;
        for (Node item : seq.getValue()) {
            String p = prefix + "." + i;
            out.put(p, item.getStartMark().getLine() + 1);
            if (item instanceof MappingNode) {
                walk(p, (MappingNode) item, out);
            } else if (item instanceof SequenceNode) {
                walkSequence(p, (SequenceNode) item, out);
            }
            i++;
        }
    }

    private static String asString(Node node) {
        if (node instanceof ScalarNode) {
            return ((ScalarNode) node).getValue();
        }
        return null;
    }

    /** 仅供测试/调试：打印映射内容 */
    public static String dump(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            sb.append(e.getKey()).append(" -> ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }
}
