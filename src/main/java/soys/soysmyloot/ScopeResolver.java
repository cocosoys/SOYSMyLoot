package soys.soysmyloot;

import org.bukkit.entity.Player;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.hook.LinkTeamHook;

import java.util.UUID;

/**
 * 进度归属解析器。
 * <p>
 * 负责把「一次伤害/击杀事件」映射为存储维度：
 *   - 归属（owner）：player 模式 -> 玩家自身 UUID；team 模式 -> 队伍 UUID（不在队伍则回退玩家 UUID）。
 *     队伍模式下，同一队伍成员的伤害会累加到同一份队伍数据上，实现「公会/队伍共享伤害」。
 *   - 世界（world）：开启世界隔离 -> 实体所在世界名；关闭 -> 空串（跨世界汇总为一份进度）。
 * </p>
 * <p>配置项：{@code tracking.scope}（player | team）、{@code tracking.world-isolation}（bool）。</p>
 */
public final class ScopeResolver {

    /** 进度归属模式 */
    public enum Scope {
        PLAYER,
        TEAM
    }

    private final ConfigManager config;
    private Scope scope;
    private boolean worldIsolation;

    public ScopeResolver(ConfigManager config) {
        this.config = config;
        this.scope = parseScope(config.getScopeId());
        this.worldIsolation = config.isWorldIsolation();
    }

    /** 重新读取配置（/myloot reload 时调用） */
    public void reload() {
        this.scope = parseScope(config.getScopeId());
        this.worldIsolation = config.isWorldIsolation();
    }

    public Scope getScope() {
        return scope;
    }

    public boolean isWorldIsolation() {
        return worldIsolation;
    }

    public boolean isTeamMode() {
        return scope == Scope.TEAM;
    }

    /**
     * 解析进度归属 UUID。
     * team 模式下优先返回队伍 UUID；玩家不在任何队伍时回退到其自身 UUID，
     * 保证单人也能正常累计（加入队伍后新伤害自动并入队伍）。
     */
    public UUID resolveOwner(Player player) {
        if (scope == Scope.TEAM) {
            UUID teamId = LinkTeamHook.getPlayerTeamId(player.getUniqueId());
            if (teamId != null) {
                return teamId;
            }
        }
        return player.getUniqueId();
    }

    /**
     * 解析世界维度。关闭世界隔离时统一返回空串，使进度跨世界共享。
     */
    public String resolveWorld(String rawWorld) {
        return worldIsolation ? (rawWorld == null ? "" : rawWorld) : "";
    }

    private static Scope parseScope(String id) {
        return "team".equalsIgnoreCase(id) ? Scope.TEAM : Scope.PLAYER;
    }
}
