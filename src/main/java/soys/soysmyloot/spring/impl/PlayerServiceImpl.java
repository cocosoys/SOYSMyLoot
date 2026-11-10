package soys.soysmyloot.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.spring.entity.vo.MonthlyRewardVo;
import soys.soysmyloot.spring.entity.vo.RewardItemVo;
import soys.soysmyloot.spring.service.PlayerService;
import soys.soysmyloot.spring.util.Sync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家侧月卡服务实现。
 *
 * <p>token：内存存储，10 分钟过期，领取成功后立即失效（一次性）。游戏内命令 {@code /monthly}
 * 调用 {@link #issueToken(UUID, String)} 拿到 token 后拼出 URL 发给玩家。</p>
 */
public class PlayerServiceImpl implements PlayerService {

    /** token 有效期（毫秒） */
    private static final long TTL_MS = 10 * 60 * 1000L;
    /** 月卡 ID 前缀约定 */
    private static final String MONTHLY_PREFIX = "monthly_";

    private static class TokenEntry {
        final UUID uuid;
        final String playerName;
        final long expireAt;

        TokenEntry(UUID uuid, String playerName, long expireAt) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.expireAt = expireAt;
        }
    }

    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();
    private final SOYSMyLoot plugin;

    public PlayerServiceImpl() {
        this.plugin = SOYSMyLoot.getPlugin(SOYSMyLoot.class);
    }

    @Override
    public String issueToken(UUID uuid, String playerName) {
        // 清理过期 token
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> e.getValue().expireAt < now);
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenEntry(uuid, playerName, now + TTL_MS));
        return token;
    }

    @Override
    public UUID resolveToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        TokenEntry e = tokens.get(token);
        if (e == null || e.expireAt < System.currentTimeMillis()) {
            tokens.remove(token);
            return null;
        }
        return e.uuid;
    }

    @Override
    public List<MonthlyRewardVo> listMonthly(UUID uuid) {
        ConfigManager cfg = plugin.getConfigManager();
        Player player = Bukkit.getPlayer(uuid);
        List<MonthlyRewardVo> out = new ArrayList<>();
        for (RewardEntry r : cfg.getRewards().values()) {
            if (r.getId() == null || !r.getId().startsWith(MONTHLY_PREFIX)) {
                continue;
            }
            if (!r.isEnabled()) {
                continue;
            }
            MonthlyRewardVo v = new MonthlyRewardVo();
            v.setId(r.getId());
            v.setName(r.getName());
            v.setDescription(r.getDescription());
            v.setLevel(parseLevel(r.getId()));
            v.setPreview(toPreview(r));
            if (player != null) {
                // isClaimed/canClaim/getCooldownRemaining 会读取玩家背包/等级/在线状态，必须在主线程执行
                final RewardEntry fr = r;
                v.setClaimed(Sync.onMain(plugin, () -> plugin.getRewardManager().isClaimed(player, fr)));
                v.setCanClaim(Sync.onMain(plugin, () -> plugin.getRewardManager().canClaim(player, fr)));
                long cd = Sync.onMain(plugin, () -> plugin.getRewardManager().getCooldownRemaining(player, fr));
                v.setCooldownLeftMs(cd * 1000L);
            } else {
                v.setClaimed(false);
                v.setCanClaim(false);
                v.setCooldownLeftMs(0);
            }
            out.add(v);
        }
        out.sort(Comparator.comparingInt(MonthlyRewardVo::getLevel));
        // 计算已解锁到第几级：连续已领取的最高等级
        int unlocked = 0;
        for (MonthlyRewardVo v : out) {
            if (v.isClaimed()) {
                unlocked = v.getLevel();
            } else {
                break;
            }
        }
        String playerName = player != null ? player.getName() : null;
        for (MonthlyRewardVo v : out) {
            v.setUnlockedLevel(unlocked);
            v.setPlayerName(playerName);
        }
        return out;
    }

    @Override
    public AjaxResult claim(String token, String rewardId) {
        UUID uuid = resolveToken(token);
        if (uuid == null) {
            return AjaxResult.error("链接已过期，请重新在游戏内输入 /monthly");
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return AjaxResult.error("你当前不在线，请先进入服务器再领取");
        }
        RewardEntry r = plugin.getConfigManager().getReward(rewardId);
        if (r == null) {
            return AjaxResult.error("礼包不存在: " + rewardId);
        }
        if (!r.isEnabled()) {
            return AjaxResult.error("该礼包已禁用");
        }
        // canClaim/claim 涉及背包/经济写入，必须在主线程执行（网关在独立线程池调用控制器）
        boolean ok = Sync.onMain(plugin, () -> {
            if (!plugin.getRewardManager().canClaim(player, r)) {
                return false;
            }
            return plugin.getRewardManager().claim(player, r);
        });
        if (ok) {
            // 一次性凭证：领取成功后立即失效，防止链接在有效期内被重复利用
            tokens.remove(token);
        }
        return ok ? AjaxResult.success() : AjaxResult.error("领取失败，请稍后再试");
    }

    private int parseLevel(String id) {
        try {
            return Integer.parseInt(id.substring(MONTHLY_PREFIX.length()));
        } catch (Throwable t) {
            return Integer.MAX_VALUE;
        }
    }

    private List<RewardItemVo> toPreview(RewardEntry r) {
        List<RewardItemVo> list = new ArrayList<>();
        if (r.getItems() == null) {
            return list;
        }
        int i = 0;
        int slot = 0;
        for (RewardEntry.ItemReward it : r.getItems()) {
            if (i++ >= 9) break;
            RewardItemVo vo = new RewardItemVo();
            vo.setSlot(slot++);
            vo.setMaterial(it.getMaterial() == null ? "" : it.getMaterial().name());
            vo.setAmount(it.getAmount());
            vo.setName(it.getName());
            vo.setLore(it.getLore());
            list.add(vo);
        }
        return list;
    }
}
