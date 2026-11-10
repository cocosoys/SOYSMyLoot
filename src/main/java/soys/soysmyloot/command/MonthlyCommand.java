package soys.soysmyloot.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.spring.SOYSMyLootExpansion;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.config.MessageManager;
import soys.soysmyloot.util.Text;

/**
 * /monthly 指令：为当前玩家签发一次性访问 token，并拼出月卡页面 URL 发送给玩家。
 *
 * <p>token 由 {@code PlayerService.issueToken} 生成（10 分钟有效、一次性）；下发的是
 * <b>H5 页面地址</b> {@code /web/plugins/SOYSMyLoot/m?token=…}，页面再调用
 * 匿名端点 {@code /api/plugins/SOYSMyLoot/player/*} 换取数据。</p>
 *
 * <p>依赖网关层扩展（{@code SOYSMyLootExpansion}，只需 SOYSHTTPOverMC）；MCERP 是否安装
 * 不影响本指令。若扩展未注册，{@code PLAYER_SERVICE} 为 null，指令提示 Web 模块未启用。</p>
 */
public class MonthlyCommand implements CommandExecutor {

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private final MessageManager messageManager;

    public MonthlyCommand(SOYSMyLoot plugin, ConfigManager config, MessageManager messageManager) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messageManager.send(sender, "soysmyloot.command.common.player-only");
            return true;
        }
        Player player = (Player) sender;
        if (SOYSMyLootExpansion.PLAYER_SERVICE == null) {
            player.sendMessage(Text.color("&c[SOYSMyLoot] Web 模块未启用，无法生成月卡链接（需要 SOYSHTTPOverMC）。"));
            return true;
        }
        String token = SOYSMyLootExpansion.PLAYER_SERVICE.issueToken(player.getUniqueId(), player.getName());
        String path = "/web/plugins/SOYSMyLoot/m?token=" + token;
        String base = config.getErpBaseUrl();
        if (base == null || base.trim().isEmpty()) {
            player.sendMessage(Text.color("&e[SOYSMyLoot] 未配置 erp.base-url，请在 config.yml 设置后重发。"));
            player.sendMessage(Text.color("&7月卡页面路径：&f" + path));
            player.sendMessage(Text.color("&7你的访问 token：&f" + token));
            return true;
        }
        String url = base.replaceAll("/+$", "") + path;
        player.sendMessage(Text.color("&a[SOYSMyLoot] 月卡页面已生成（10 分钟内有效）："));
        player.sendMessage(Text.color("&f" + url));
        return true;
    }
}
