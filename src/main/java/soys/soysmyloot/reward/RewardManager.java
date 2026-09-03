package soys.soysmyloot.reward;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.ScopeResolver;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.config.MessageManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 奖励管理器：条件校验、冷却判断与奖励发放。
 * <p>条件判定与领取记录均按 {@link ScopeResolver} 解析出的归属（玩家/队伍）与世界维度进行，
 * 从而与伤害追踪的维度保持一致。</p>
 * <p>支持的条件类型：DAMAGE / KILLS / LEVEL / MONEY / ONLINE / HAS_ITEM / TIME。</p>
 * <p>支持的领取模式：普通、部分领取（按进度比例累进发放并可选消耗进度）、
 * 每日/每周上限、随机奖励池、多阶段前置（requires）。</p>
 */
public class RewardManager {

    private final SOYSMyLoot plugin;
    private final MessageManager messageManager;
    private final DataManager dataManager;
    private final ScopeResolver scopeResolver;

    public RewardManager(SOYSMyLoot plugin, MessageManager messageManager, DataManager dataManager,
                         ScopeResolver scopeResolver) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.dataManager = dataManager;
        this.scopeResolver = scopeResolver;
    }

    // ================================================================
    //  条件判定
    // ================================================================

    /** 是否满足全部条件 */
    public boolean meetsConditions(Player player, RewardEntry reward) {
        PlayerData data = dataManager.getData(scopeResolver.resolveOwner(player));
        String world = scopeResolver.resolveWorld(player.getWorld().getName());
        for (RewardEntry.Condition c : reward.getConditions()) {
            if (!meets(player, data, world, c)) {
                return false;
            }
        }
        return true;
    }

    /** 单个条件是否满足 */
    private boolean meets(Player player, PlayerData data, String world, RewardEntry.Condition c) {
        switch (c.getType()) {
            case DAMAGE:
                return data.getDamage(world, c.getMonsterId()) >= c.getAmount();
            case KILLS:
                return data.getKills(world, c.getMonsterId()) >= c.getAmount();
            case LEVEL:
                return player.getLevel() >= c.getAmount();
            case MONEY:
                if (plugin.getEconomy() == null) {
                    return false;
                }
                return plugin.getEconomy().getBalance(player) >= c.getAmount();
            case ONLINE:
                // 累计在线时长（分钟），由 OnlineTimeTracker 自行统计并持久化，与版本无关
                return dataManager.getData(player.getUniqueId()).getOnlineMinutes() >= c.getAmount();
            case HAS_ITEM:
                return countItem(player, c.getItemMaterial(), c.getItemData()) >= c.getAmount();
            case TIME:
                return inTimeRange(c);
            default:
                return false;
        }
    }

    /** 除 DAMAGE/KILLS 外的条件是否全部满足（部分领取时用，进度由银行额度代替） */
    private boolean meetsNonProgress(Player player, RewardEntry reward) {
        PlayerData data = dataManager.getData(scopeResolver.resolveOwner(player));
        String world = scopeResolver.resolveWorld(player.getWorld().getName());
        for (RewardEntry.Condition c : reward.getConditions()) {
            if (c.getType() == RewardEntry.ConditionType.DAMAGE
                    || c.getType() == RewardEntry.ConditionType.KILLS) {
                continue;
            }
            if (!meets(player, data, world, c)) {
                return false;
            }
        }
        return true;
    }

    /** 统计玩家持有的指定物品数量（主背包 +  armor + 副手） */
    private int countItem(Player player, org.bukkit.Material material, int data) {
        if (material == null) {
            return 0;
        }
        List<ItemStack> all = new ArrayList<>();
        all.addAll(Arrays.asList(player.getInventory().getContents()));
        all.addAll(Arrays.asList(player.getInventory().getArmorContents()));
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null) {
            all.add(off);
        }
        int count = 0;
        for (ItemStack is : all) {
            if (is == null || is.getType() != material) {
                continue;
            }
            if (data >= 0 && is.getDurability() != data) {
                continue;
            }
            count += is.getAmount();
        }
        return count;
    }

    /** 当前服务器时间是否处于条件配置的时间段内（支持跨午夜与指定星期） */
    private boolean inTimeRange(RewardEntry.Condition c) {
        Calendar cal = Calendar.getInstance();
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        // 转换为 1=周一 .. 7=周日
        int day = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
        List<Integer> days = c.getDays();
        if (days != null && !days.isEmpty() && !days.contains(day)) {
            return false;
        }
        int s = c.getStartMinOfDay();
        int e = c.getEndMinOfDay();
        if (s <= e) {
            return nowMin >= s && nowMin <= e;
        }
        // 跨午夜，例如 22:00 - 02:00
        return nowMin >= s || nowMin <= e;
    }

    // ================================================================
    //  领取资格
    // ================================================================

    /** 是否已领取（仅针对不可重复奖励） */
    public boolean isClaimed(Player player, RewardEntry reward) {
        if (reward.isRepeatable()) {
            return false;
        }
        return dataManager.getData(scopeResolver.resolveOwner(player)).getClaimCount(reward.getId()) > 0;
    }

    /** 冷却剩余秒数（0 = 无冷却或已过） */
    public long getCooldownRemaining(Player player, RewardEntry reward) {
        if (reward.getCooldown() <= 0) {
            return 0;
        }
        long last = dataManager.getData(scopeResolver.resolveOwner(player)).getLastClaim(reward.getId());
        if (last <= 0) {
            return 0;
        }
        long passed = System.currentTimeMillis() / 1000 - last;
        if (passed >= reward.getCooldown()) {
            return 0;
        }
        return reward.getCooldown() - passed;
    }

    /** 当前周期内已领取次数（每日） */
    public int getDailyClaimed(Player player, RewardEntry reward) {
        return dataManager.getData(scopeResolver.resolveOwner(player)).getDailyClaimed(reward.getId());
    }

    /** 当前周期内已领取次数（每周） */
    public int getWeeklyClaimed(Player player, RewardEntry reward) {
        return dataManager.getData(scopeResolver.resolveOwner(player)).getWeeklyClaimed(reward.getId());
    }

    /** 是否可领取 */
    public boolean canClaim(Player player, RewardEntry reward) {
        if (getCooldownRemaining(player, reward) > 0) {
            return false;
        }
        if (reward.isPartial()) {
            // 部分领取：非进度类条件仍需满足；银行进度需 >= 一个单位
            if (!meetsNonProgress(player, reward)) {
                return false;
            }
            return bankedUnits(player, reward) > 0;
        }
        if (isClaimed(player, reward)) {
            return false;
        }
        if (reward.getDailyLimit() > 0 && getDailyClaimed(player, reward) >= reward.getDailyLimit()) {
            return false;
        }
        if (reward.getWeeklyLimit() > 0 && getWeeklyClaimed(player, reward) >= reward.getWeeklyLimit()) {
            return false;
        }
        if (reward.getRequires() != null && !reward.getRequires().isEmpty()) {
            for (String reqId : reward.getRequires()) {
                RewardEntry req = plugin.getConfigManager().getReward(reqId);
                if (req == null || !isClaimed(player, req)) {
                    return false;
                }
            }
        }
        return meetsConditions(player, reward);
    }

    /** 部分领取时，当前银行进度可兑换的单位数（floor(进度 / 单位)） */
    public int bankedUnits(Player player, RewardEntry reward) {
        if (!reward.isPartial()) {
            return 0;
        }
        RewardEntry.Condition metric = firstProgressCondition(reward);
        if (metric == null) {
            return 0;
        }
        PlayerData data = dataManager.getData(scopeResolver.resolveOwner(player));
        String world = scopeResolver.resolveWorld(player.getWorld().getName());
        double cur = metric.getType() == RewardEntry.ConditionType.KILLS
                ? data.getKills(world, metric.getMonsterId())
                : data.getDamage(world, metric.getMonsterId());
        double per = reward.getPer() > 0 ? reward.getPer() : metric.getAmount();
        if (per <= 0) {
            return 0;
        }
        return (int) Math.floor(cur / per);
    }

    /** 取首个伤害/击杀类条件作为部分领取的度量基准 */
    private RewardEntry.Condition firstProgressCondition(RewardEntry reward) {
        for (RewardEntry.Condition c : reward.getConditions()) {
            if (c.getType() == RewardEntry.ConditionType.DAMAGE
                    || c.getType() == RewardEntry.ConditionType.KILLS) {
                return c;
            }
        }
        return null;
    }

    // ================================================================
    //  发放
    // ================================================================

    /** 发放奖励并记录领取，返回是否成功 */
    public boolean claim(Player player, RewardEntry reward) {
        if (!canClaim(player, reward)) {
            return false;
        }
        // 领取记录落在「归属」维度（队伍模式下由队伍持有），物品/金钱/点券发给执行指令的个人
        PlayerData data = dataManager.getData(scopeResolver.resolveOwner(player));
        String world = scopeResolver.resolveWorld(player.getWorld().getName());

        int scale = 1;
        if (reward.isPartial()) {
            int units = bankedUnits(player, reward);
            if (units < 1) {
                return false;
            }
            scale = units;
            // 消耗进度（默认开启），使后续领取基于新的余额
            if (reward.isConsume()) {
                RewardEntry.Condition metric = firstProgressCondition(reward);
                if (metric != null) {
                    double per = reward.getPer() > 0 ? reward.getPer() : metric.getAmount();
                    double consumeAmt = per * units;
                    if (metric.getType() == RewardEntry.ConditionType.KILLS) {
                        data.addKill(world, metric.getMonsterId(), (int) consumeAmt);
                    } else {
                        data.addDamage(world, metric.getMonsterId(), -consumeAmt);
                    }
                }
            }
        }

        // 主体奖励（按单位数缩放）
        issueEffects(player, reward, scale);

        // 随机奖励池：抽取若干奖励模板并发放（不递归、不记录其领取）
        if (reward.getPool() != null && !reward.getPool().isEmpty()) {
            List<RewardEntry> poolEntries = new ArrayList<>();
            for (String id : reward.getPool()) {
                RewardEntry e = plugin.getConfigManager().getReward(id);
                if (e != null) {
                    poolEntries.add(e);
                }
            }
            Collections.shuffle(poolEntries);
            int n = Math.min(reward.getPoolCount(), poolEntries.size());
            for (int i = 0; i < n; i++) {
                issueEffects(player, poolEntries.get(i), 1);
            }
        }

        // 记录领取（归属维度）
        data.setClaimed(reward.getId(), System.currentTimeMillis() / 1000);
        long now = System.currentTimeMillis() / 1000;
        if (reward.getDailyLimit() > 0) {
            data.addDailyClaim(reward.getId(), now);
        }
        if (reward.getWeeklyLimit() > 0) {
            data.addWeeklyClaim(reward.getId(), now);
        }

        // 领取成功反馈（音效 + 粒子）
        playFeedback(player);

        return true;
    }

    /**
     * 管理员代发奖励：绕过条件校验、冷却、领取记录，直接发放奖励内容。
     * 用于 /myloot give 指令，不记录领取次数，不消耗进度。
     *
     * @param player 目标玩家
     * @param reward 奖励配置
     */
    public void adminGive(Player player, RewardEntry reward) {
        issueEffects(player, reward, 1);
        // 随机奖励池：同 claim 逻辑，抽取若干奖励模板并发放
        if (reward.getPool() != null && !reward.getPool().isEmpty()) {
            List<RewardEntry> poolEntries = new ArrayList<>();
            for (String id : reward.getPool()) {
                RewardEntry e = plugin.getConfigManager().getReward(id);
                if (e != null) {
                    poolEntries.add(e);
                }
            }
            Collections.shuffle(poolEntries);
            int n = Math.min(reward.getPoolCount(), poolEntries.size());
            for (int i = 0; i < n; i++) {
                issueEffects(player, poolEntries.get(i), 1);
            }
        }

        // 代发成功反馈（音效 + 粒子）
        playFeedback(player);
    }

    /**
     * 播放领取成功反馈（音效 + 粒子）。
     * 音效/粒子名称从 config.yml 的 feedback 节读取，无效名称在 debug 模式下输出警告。
     * 音效仅玩家本人可闻；粒子在玩家位置生成，周围玩家可见。
     */
    private void playFeedback(Player player) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isFeedbackEnabled()) {
            return;
        }

        // 播放音效
        String soundName = config.getFeedbackClaimSound();
        if (soundName != null && !soundName.trim().isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound,
                        config.getFeedbackSoundVolume(), config.getFeedbackSoundPitch());
            } catch (IllegalArgumentException e) {
                if (config.isDebug()) {
                    plugin.getLogger().warning("[反馈] 无效的音效名称: " + soundName);
                }
            }
        }

        // 播放粒子
        String particleName = config.getFeedbackClaimParticle();
        if (particleName != null && !particleName.trim().isEmpty()) {
            try {
                Particle particle = Particle.valueOf(particleName);
                Location loc = player.getLocation().add(0, 1.0, 0);
                double offset = config.getFeedbackParticleOffset();
                player.getWorld().spawnParticle(particle, loc,
                        config.getFeedbackParticleCount(), offset, offset, offset);
            } catch (IllegalArgumentException e) {
                if (config.isDebug()) {
                    plugin.getLogger().warning("[反馈] 无效的粒子名称: " + particleName);
                }
            }
        }
    }

    /** 把一份奖励模板按 scale 倍发放给玩家（不处理条件与领取记录） */
    private void issueEffects(Player player, RewardEntry reward, int scale) {
        // 物品
        for (RewardEntry.ItemReward ir : reward.getItems()) {
            int amount = Math.max(1, ir.getAmount()) * scale;
            ItemStack item = buildItem(ir, amount);
            java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        // 控制台指令
        if (!reward.getCommands().isEmpty()) {
            for (int i = 0; i < scale; i++) {
                for (String cmd : reward.getCommands()) {
                    String finalCmd = cmd.replace("%player%", player.getName());
                    Bukkit.getScheduler().runTask(plugin, () ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
                }
            }
        }

        // 金钱（需 Vault + 经济插件）
        if (reward.getMoney() > 0) {
            giveMoney(player, reward.getMoney() * scale);
        }

        // 点券（需 PlayerPoints）
        if (reward.getPoints() > 0) {
            givePoints(player, reward.getPoints() * scale);
        }

        // 消息
        for (String msg : reward.getMessages()) {
            player.sendMessage(Text.color(msg));
        }
    }

    private ItemStack buildItem(RewardEntry.ItemReward ir, int amount) {
        ItemStack item = new ItemStack(ir.getMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (ir.getName() != null && !ir.getName().isEmpty()) {
                meta.setDisplayName(Text.color(ir.getName()));
            }
            if (ir.getLore() != null && !ir.getLore().isEmpty()) {
                meta.setLore(ir.getLore().stream().map(Text::color).collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void giveMoney(Player player, double amount) {
        if (plugin.getEconomy() == null) {
            player.sendMessage(messageManager.get("warn-no-vault"));
            return;
        }
        plugin.getEconomy().depositPlayer(player, amount);
    }

    private void givePoints(Player player, double amount) {
        if (plugin.getPlayerPoints() == null) {
            player.sendMessage(messageManager.get("warn-no-playerpoints"));
            return;
        }
        plugin.getPlayerPoints().give(player.getUniqueId(), (int) amount);
    }
}
