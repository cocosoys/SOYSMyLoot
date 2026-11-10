package soys.soysmyloot.spring;

import com.github.cocosoys.mc.soyshttpovermc.api.SoysExpansion;
import soys.soysmyloot.spring.controller.PlayerController;
import soys.soysmyloot.spring.impl.PlayerServiceImpl;
import soys.soysmyloot.spring.service.PlayerService;

import java.util.ArrayList;
import java.util.List;

/**
 * SOYSMyLoot 的网关层扩展（继承 {@link SoysExpansion}，不进入 MCERP 索引）。
 *
 * <p>只承担两件与 ERP 无关的职责：</p>
 * <ul>
 *   <li>页面托管：{@code /web/plugins/SOYSMyLoot}（resourceRoot=dist，页面 tag
 *       {@code expansion:SOYSMyLoot}）；</li>
 *   <li>玩家侧 token 端点：{@code /api/plugins/SOYSMyLoot/player/*}
 *       （类上标 {@code @Anonymous}，一次性 token 自校验，不依赖 ERP 登录态）。</li>
 * </ul>
 *
 * <p>管理后台的三组 {@code /erp/*} 端点与 MCERP 菜单由
 * {@link soys.soysmyloot.spring.erp.SOYSMyLootErpExpansion}（identifier {@code SOYSMyLoot-ERP}）
 * 单独登记。本类不写 ERP 注册表，因此未安装 MCERP 时页面与玩家端点照常可用。</p>
 *
 * <p>端点前缀由框架按 <b>owner 插件名</b>（SOYSMyLoot）自动拼，与 identifier 无关，
 * 故管理端点的 URL 仍是 {@code /api/plugins/SOYSMyLoot/erp/*}，前端 baseURL 无需改动。</p>
 */
public class SOYSMyLootExpansion extends SoysExpansion {

    /** 暴露给游戏内命令：颁发一次性访问 token（/monthly 命令用）。 */
    public static volatile PlayerService PLAYER_SERVICE;

    @Override
    public String getIdentifier() {
        return "SOYSMyLoot";
    }

    @Override
    public String getAuthor() {
        return "SOYS";
    }

    /**
     * 缓存的控制器实例。{@code buildControllers()} 在注册与反注册时可能被多次调用，
     * 必须复用同一批实例，否则反注册时移除的是不同对象，导致重复注册 / 泄漏
     * （这正是此前 /soyshttp reload 后端点重复注册的 B2 根因）。
     */
    private volatile List<Object> cachedControllers;

    @Override
    protected List<Object> buildControllers() {
        List<Object> list = cachedControllers;
        if (list == null) {
            synchronized (this) {
                list = cachedControllers;
                if (list == null) {
                    PlayerService playerService = new PlayerServiceImpl();
                    PLAYER_SERVICE = playerService;
                    list = new ArrayList<>();
                    list.add(new PlayerController(playerService));
                    cachedControllers = list;
                }
            }
        }
        return list;
    }

    @Override
    protected String resourceRoot() {
        return "dist";
    }
}
