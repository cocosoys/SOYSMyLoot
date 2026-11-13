package soys.soysmyloot.spring.erp;

import com.github.cocosoys.mc.mcerp.ErpMenus;
import com.github.cocosoys.mc.mcerp.McerpExpansion;
import com.github.cocosoys.mc.soyshttpovermc.api.SoysHttpOverMcApi;
import org.bukkit.plugin.Plugin;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.spring.controller.ClaimController;
import soys.soysmyloot.spring.controller.ErpConfigController;
import soys.soysmyloot.spring.controller.MonsterController;
import soys.soysmyloot.spring.controller.RewardController;
import soys.soysmyloot.spring.impl.ClaimServiceImpl;
import soys.soysmyloot.spring.impl.MonsterServiceImpl;
import soys.soysmyloot.spring.impl.MyLootConfigServiceImpl;
import soys.soysmyloot.spring.impl.RewardServiceImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * SOYSMyLoot 的 MCERP 模块扩展（identifier {@code SOYSMyLoot-ERP}）。
 *
 * <p>为什么要与 {@link soys.soysmyloot.spring.SOYSMyLootExpansion} 分开：</p>
 * <ul>
 *   <li>MCERP 判定"是否 ERP 模块"的唯一条件是 {@code obj instanceof McerpExpansion}，
 *       而负责页面托管与玩家端点的 {@code SOYSMyLootExpansion} 继承的是
 *       {@code SoysExpansion}（框架直系子类），永远进不了 ERP 索引；</li>
 *   <li>identifier 与插件名分开后，网关注册表 key、页面 tag、ERP 模块 id、控制台路由、
 *       pageDirRoot 目录段各自独立，不再由同一个字符串兼任六种身份；</li>
 *   <li>MCERP 未安装时主类不会实例化本类，因此 MCERP 只作为 softdepend。</li>
 * </ul>
 *
 * <p><b>页面归属</b>：dist 由 {@code SOYSMyLootExpansion} 按 owner 插件名托管在
 * {@code /web/plugins/SOYSMyLoot}；框架默认的 {@code pageRoot()} 取 identifier，会得到
 * {@code /web/plugins/SOYSMyLoot-ERP}（无任何插件托管），故覆写为 owner 插件名。</p>
 *
 * <p><b>菜单 URL</b>：默认 pageDirRoot = {@code pageRoot + "/" + NameSafe.path("SOYSMyLoot-ERP")}
 * = {@code /web/plugins/SOYSMyLoot/soysmylooterp}，与前端 wujieRoutes 顶层段一致。</p>
 */
public final class SOYSMyLootErpExpansion extends McerpExpansion {

    @Override
    public String getIdentifier() {
        return "SOYSMyLoot-ERP";
    }

    @Override
    public String getAuthor() {
        return "SOYS";
    }

    @Override
    protected String displayName() {
        return "战利品配置";
    }

    @Override
    protected String icon() {
        return "gift";
    }

    @Override
    protected int sortOrder() {
        return 30;
    }

    @Override
    protected String permission() {
        return "soysmyloot:erp:menu";
    }

    /** 页面 URL 前缀：覆盖为 owner 插件名，与页面托管路径（/web/plugins/SOYSMyLoot）一致。 */
    @Override
    public String pageRoot() {
        Plugin owner = getOwner();
        String name = owner == null ? "SOYSMyLoot" : owner.getName();
        SoysHttpOverMcApi api = getApi();
        String prefix = api == null ? null : api.getToolkit().pageFullPrefix(name);
        return prefix == null || prefix.isEmpty() ? "/web/plugins/" + name : prefix;
    }

    @Override
    protected ErpMenus menus() {
        return ErpMenus.create()
                .menu("reward", "礼包列表", "gift", RewardController.class)
                .perms("soysmyloot:erp:reward:list")
                .orderNum(10)
                .menu("monster", "怪物配置", "bug", MonsterController.class)
                .perms("soysmyloot:erp:monster:list")
                .orderNum(20)
                .menu("claim", "领取记录", "form", ClaimController.class)
                .perms("soysmyloot:erp:claim:list")
                .orderNum(30)
                // 插件配置目录：必须用带 lambda 的 4 参 dir（无 lambda 重载压栈不弹栈，
                // 会把后续菜单全嵌进目录）。叶子不写 component，由框架按 pageDirRoot 自动拼。
                .dir("config", "插件配置", "edit", d -> d
                        .menu("config", "核心配置", "input", ErpConfigController.class)
                        .perms("soysmyloot:erp:config:list")
                        .orderNum(10))
                .orderNum(40);
    }

    /**
     * 缓存的控制器实例（与注册/反注册共用同一来源，避免 reload 后重复登记）。
     */
    private volatile List<Object> cachedControllers;

    @Override
    protected List<Object> buildControllers() {
        List<Object> list = cachedControllers;
        if (list == null) {
            synchronized (this) {
                list = cachedControllers;
                if (list == null) {
                    list = new ArrayList<>();
                    list.add(new RewardController(new RewardServiceImpl()));
                    list.add(new MonsterController(new MonsterServiceImpl()));
                    list.add(new ClaimController(new ClaimServiceImpl()));
                    // 配置菜单控制器：构造注入 owner 实例，按 getDataFolder() 读写自己的 config.yml
                    list.add(new ErpConfigController(new MyLootConfigServiceImpl(
                            SOYSMyLoot.getPlugin(SOYSMyLoot.class))));
                    cachedControllers = list;
                }
            }
        }
        return list;
    }

    /** 不托管页面：dist 由 SOYSMyLootExpansion 统一托管，避免同目录重复登记与 tag 分裂。 */
    @Override
    protected String resourceRoot() {
        return null;
    }
}
