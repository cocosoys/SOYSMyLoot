package soys.soysmyloot.spring.entity;

/**
 * 「插件配置」菜单的唯一事实源：声明哪些配置文件可在 ERP 后台可视化编辑。
 *
 * <p>新增一个可编辑文件 = 本枚举加一项 + 前端加一个 .vue + 路由加一条绑定，
 * 菜单、端点、文件定位全部由本枚举派生，不需要改动框架代码。</p>
 *
 * <p>数据目录 = {@code plugins/SOYSMyLoot/}（owner 自己的 getDataFolder()，不写死别的插件名）。
 * 保存后会自动执行 {@code /myloot reload config} 等效热重载；但存储后端切换、
 * auto-save 定时周期等启动期创建的实例不会重建，仍需在保存后重启服务器。</p>
 *
 * <p>范围说明：{@code monsters.yml} / {@code rewards.yml} 已有「怪物配置」「礼包列表」
 * 专门管理页，语言包在 {@code language/} 且不适合通用 Map 编辑器，故均不纳入本枚举，
 * 避免两套入口写同一文件产生冲突。</p>
 */
public enum MyLootConfigFile {

    CONFIG("config", "核心配置", "config.yml",
            "调试模式、追踪归属、防作弊、进度 HUD、领取反馈、数据存储、排行榜、赛季、ERP 基地址");

    private final String id;
    private final String name;
    private final String file;
    private final String desc;

    MyLootConfigFile(String id, String name, String file, String desc) {
        this.id = id;
        this.name = name;
        this.file = file;
        this.desc = desc;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFile() {
        return file;
    }

    public String getDesc() {
        return desc;
    }

    /** 按 id 查找；未知 id 返回 null，由 Service 转成 AjaxResult.error。 */
    public static MyLootConfigFile byId(String id) {
        if (id == null) {
            return null;
        }
        for (MyLootConfigFile f : values()) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }
}
