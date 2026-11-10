package soys.soysmyloot.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.PageUtils;
import com.github.cocosoys.mc.soyshttpovermc.util.TableDataInfo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.reward.RewardManager;
import soys.soysmyloot.spring.entity.po.CopyHandPo;
import soys.soysmyloot.spring.entity.po.RewardGivePo;
import soys.soysmyloot.spring.entity.po.RewardItemsSavePo;
import soys.soysmyloot.spring.entity.po.RewardResetPo;
import soys.soysmyloot.spring.entity.po.RewardSavePo;
import soys.soysmyloot.spring.entity.po.RewardTogglePo;
import soys.soysmyloot.spring.entity.vo.RewardConditionVo;
import soys.soysmyloot.spring.entity.vo.RewardDetailVo;
import soys.soysmyloot.spring.entity.vo.RewardItemVo;
import soys.soysmyloot.spring.entity.vo.RewardVo;
import soys.soysmyloot.spring.service.RewardService;
import soys.soysmyloot.spring.util.Sync;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 礼包管理业务实现：读走 ConfigManager 内存缓存，写直接覆写 rewards.yml 后热重载。
 * 不引入新存储，玩家领取记录经 DataManager 访问。
 */
public class RewardServiceImpl implements RewardService {

    private final SOYSMyLoot plugin;

    public RewardServiceImpl() {
        this.plugin = SOYSMyLoot.getPlugin(SOYSMyLoot.class);
    }

    private ConfigManager cfg() {
        return plugin.getConfigManager();
    }

    @Override
    public TableDataInfo list(Integer pageNum, Integer pageSize, String keyword, String stage) {
        Map<String, RewardEntry> all = cfg().getRewards();
        List<RewardVo> rows = new ArrayList<>();
        String kw = keyword == null ? null : keyword.trim().toLowerCase();
        for (RewardEntry r : all.values()) {
            if (stage != null && !stage.isEmpty() && !stage.equals(r.getStage())) {
                continue;
            }
            if (kw != null && !kw.isEmpty()) {
                String id = r.getId() == null ? "" : r.getId().toLowerCase();
                String name = r.getName() == null ? "" : r.getName().toLowerCase();
                if (!id.contains(kw) && !name.contains(kw)) {
                    continue;
                }
            }
            rows.add(toRow(r));
        }
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return PageUtils.page(rows, pn, ps);
    }

    private RewardVo toRow(RewardEntry r) {
        RewardVo v = new RewardVo();
        v.setId(r.getId());
        v.setName(r.getName());
        v.setDescription(r.getDescription());
        v.setStage(r.getStage());
        v.setEnabled(r.isEnabled());
        v.setRepeatable(r.isRepeatable());
        v.setCooldown(r.getCooldown());
        v.setDailyLimit(r.getDailyLimit());
        v.setWeeklyLimit(r.getWeeklyLimit());
        v.setPartial(r.isPartial());
        v.setItemCount(r.getItems() == null ? 0 : r.getItems().size());
        v.setClaimCount(sumClaims(r.getId()));
        return v;
    }

    /** 全服领取次数合计（仅统计已缓存玩家，避免全量加载所有玩家数据） */
    private long sumClaims(String rewardId) {
        long sum = 0;
        DataManager dataManager = plugin.getDataManager();
        if (dataManager == null) {
            return 0;
        }
        for (PlayerData pd : dataManager.getCachedPlayers()) {
            sum += pd.getClaimCount(rewardId);
        }
        return sum;
    }

    @Override
    public AjaxResult get(String id) {
        RewardEntry r = cfg().getReward(id);
        if (r == null) {
            return AjaxResult.error("礼包不存在: " + id);
        }
        return AjaxResult.success(toDetail(r));
    }

    private RewardDetailVo toDetail(RewardEntry r) {
        RewardDetailVo d = new RewardDetailVo();
        d.setId(r.getId());
        d.setName(r.getName());
        d.setDescription(r.getDescription());
        d.setStage(r.getStage());
        d.setEnabled(r.isEnabled());
        d.setRepeatable(r.isRepeatable());
        d.setCooldown(r.getCooldown());
        d.setDailyLimit(r.getDailyLimit());
        d.setWeeklyLimit(r.getWeeklyLimit());
        d.setPartial(r.isPartial());
        d.setPer(r.getPer());
        d.setConsume(r.isConsume());
        d.setRequires(r.getRequires());
        d.setPool(r.getPool());
        d.setPoolCount(r.getPoolCount());
        d.setCommands(r.getCommands());
        d.setMoney(r.getMoney());
        d.setPoints(r.getPoints());
        d.setMessages(r.getMessages());

        List<RewardConditionVo> conds = new ArrayList<>();
        if (r.getConditions() != null) {
            for (RewardEntry.Condition c : r.getConditions()) {
                RewardConditionVo cv = new RewardConditionVo();
                cv.setType(c.getType() == null ? null : c.getType().name());
                cv.setMonsterId(c.getMonsterId());
                cv.setAmount(c.getAmount());
                cv.setMaterial(c.getItemMaterial() == null ? null : c.getItemMaterial().name());
                cv.setData(c.getItemData());
                cv.setStartMinOfDay(c.getStartMinOfDay());
                cv.setEndMinOfDay(c.getEndMinOfDay());
                cv.setDays(c.getDays());
                conds.add(cv);
            }
        }
        d.setConditions(conds);

        List<RewardItemVo> items = new ArrayList<>();
        if (r.getItems() != null) {
            int slot = 0;
            for (RewardEntry.ItemReward it : r.getItems()) {
                RewardItemVo iv = new RewardItemVo();
                iv.setSlot(slot++);
                iv.setMaterial(it.getMaterial() == null ? null : it.getMaterial().name());
                iv.setAmount(it.getAmount());
                iv.setName(it.getName());
                iv.setLore(it.getLore());
                items.add(iv);
            }
        }
        d.setItems(items);
        return d;
    }

    @Override
    public AjaxResult save(RewardSavePo po) {
        if (po == null || po.getId() == null || po.getId().trim().isEmpty()) {
            return AjaxResult.error("id 不能为空");
        }
        try {
            YamlConfiguration yml = loadRewardsYml();
            String path = "rewards." + po.getId().trim();
            writeRewardNode(yml, path, po);
            saveRewardsYml(yml);
            cfg().loadRewards();
            return AjaxResult.success();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "保存礼包失败: " + po.getId(), e);
            return AjaxResult.error("保存失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            return AjaxResult.error("id 不能为空");
        }
        try {
            YamlConfiguration yml = loadRewardsYml();
            yml.set("rewards." + id, null);
            saveRewardsYml(yml);
            cfg().loadRewards();
            return AjaxResult.success();
        } catch (Exception e) {
            return AjaxResult.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult toggle(RewardTogglePo po) {
        if (po == null || po.getId() == null) {
            return AjaxResult.error("id 不能为空");
        }
        RewardEntry r = cfg().getReward(po.getId());
        if (r == null) {
            return AjaxResult.error("礼包不存在: " + po.getId());
        }
        RewardSavePo savePo = new RewardSavePo();
        // 基于当前完整详情覆写，仅改 enabled
        RewardDetailVo cur = toDetail(r);
        savePo.setId(cur.getId());
        savePo.setName(cur.getName());
        savePo.setDescription(cur.getDescription());
        savePo.setStage(cur.getStage());
        savePo.setEnabled(po.getEnabled() == null ? Boolean.TRUE : po.getEnabled());
        savePo.setRepeatable(cur.getRepeatable());
        savePo.setCooldown(cur.getCooldown());
        savePo.setDailyLimit(cur.getDailyLimit());
        savePo.setWeeklyLimit(cur.getWeeklyLimit());
        savePo.setPartial(cur.getPartial());
        savePo.setPer(cur.getPer());
        savePo.setConsume(cur.getConsume());
        savePo.setConditions(cur.getConditions());
        savePo.setRequires(cur.getRequires());
        savePo.setPool(cur.getPool());
        savePo.setPoolCount(cur.getPoolCount());
        savePo.setItems(cur.getItems());
        savePo.setCommands(cur.getCommands());
        savePo.setMoney(cur.getMoney());
        savePo.setPoints(cur.getPoints());
        savePo.setMessages(cur.getMessages());
        return save(savePo);
    }

    @Override
    public AjaxResult give(RewardGivePo po) {
        if (po == null || po.getPlayerName() == null || po.getRewardId() == null) {
            return AjaxResult.error("playerName 与 rewardId 不能为空");
        }
        RewardEntry reward = cfg().getReward(po.getRewardId());
        if (reward == null) {
            return AjaxResult.error("礼包不存在: " + po.getRewardId());
        }
        Player target = Bukkit.getPlayerExact(po.getPlayerName());
        if (target == null) {
            return AjaxResult.error("玩家不在线: " + po.getPlayerName());
        }
        try {
            // adminGive 涉及背包写入，必须在主线程执行（网关在独立线程池调用控制器）
            Sync.onMain(plugin, () -> plugin.getRewardManager().adminGive(target, reward));
            return AjaxResult.success();
        } catch (Throwable t) {
            return AjaxResult.error("代发失败: " + t.getMessage());
        }
    }

    @Override
    public AjaxResult resetClaim(RewardResetPo po) {
        if (po == null || po.getPlayerName() == null || po.getRewardId() == null) {
            return AjaxResult.error("playerName 与 rewardId 不能为空");
        }
        Player target = Bukkit.getPlayerExact(po.getPlayerName());
        if (target == null) {
            return AjaxResult.error("玩家不在线，无法重置: " + po.getPlayerName());
        }
        DataManager dm = plugin.getDataManager();
        if (dm == null) {
            return AjaxResult.error("数据管理器不可用");
        }
        PlayerData data = dm.getData(target.getUniqueId());
        data.setLastClaim(po.getRewardId(), 0L);
        data.setClaimCount(po.getRewardId(), 0);
        return AjaxResult.success();
    }

    @Override
    public AjaxResult items(String rewardId) {
        RewardEntry r = cfg().getReward(rewardId);
        if (r == null) {
            return AjaxResult.error("礼包不存在: " + rewardId);
        }
        List<RewardItemVo> list = new ArrayList<>();
        if (r.getItems() != null) {
            int slot = 0;
            for (RewardEntry.ItemReward it : r.getItems()) {
                RewardItemVo iv = new RewardItemVo();
                iv.setSlot(slot++);
                iv.setMaterial(it.getMaterial() == null ? null : it.getMaterial().name());
                iv.setAmount(it.getAmount());
                iv.setName(it.getName());
                iv.setLore(it.getLore());
                list.add(iv);
            }
        }
        return AjaxResult.success(list);
    }

    @Override
    public AjaxResult saveItems(RewardItemsSavePo po) {
        if (po == null || po.getRewardId() == null) {
            return AjaxResult.error("rewardId 不能为空");
        }
        RewardEntry r = cfg().getReward(po.getRewardId());
        if (r == null) {
            return AjaxResult.error("礼包不存在: " + po.getRewardId());
        }
        RewardSavePo savePo = new RewardSavePo();
        RewardDetailVo cur = toDetail(r);
        savePo.setId(cur.getId());
        savePo.setName(cur.getName());
        savePo.setDescription(cur.getDescription());
        savePo.setStage(cur.getStage());
        savePo.setEnabled(cur.getEnabled());
        savePo.setRepeatable(cur.getRepeatable());
        savePo.setCooldown(cur.getCooldown());
        savePo.setDailyLimit(cur.getDailyLimit());
        savePo.setWeeklyLimit(cur.getWeeklyLimit());
        savePo.setPartial(cur.getPartial());
        savePo.setPer(cur.getPer());
        savePo.setConsume(cur.getConsume());
        savePo.setConditions(cur.getConditions());
        savePo.setRequires(cur.getRequires());
        savePo.setPool(cur.getPool());
        savePo.setPoolCount(cur.getPoolCount());
        savePo.setItems(po.getItems());
        savePo.setCommands(cur.getCommands());
        savePo.setMoney(cur.getMoney());
        savePo.setPoints(cur.getPoints());
        savePo.setMessages(cur.getMessages());
        return save(savePo);
    }

    @Override
    public AjaxResult copyHand(CopyHandPo po) {
        if (po == null || po.getPlayerName() == null) {
            return AjaxResult.error("playerName 不能为空");
        }
        Player p = Bukkit.getPlayerExact(po.getPlayerName());
        if (p == null) {
            return AjaxResult.error("玩家不在线: " + po.getPlayerName());
        }
        PlayerInventory inv = p.getInventory();
        List<RewardItemVo> list = new ArrayList<>();
        int slot = 0;
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                slot++;
                continue;
            }
            RewardItemVo iv = new RewardItemVo();
            iv.setSlot(slot++);
            iv.setMaterial(stack.getType().name());
            iv.setAmount(stack.getAmount());
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                iv.setName(meta.getDisplayName());
                iv.setLore(meta.getLore());
            }
            list.add(iv);
        }
        return AjaxResult.success(list);
    }

    // ================================================================
    //  rewards.yml 读写
    // ================================================================

    private File rewardsFile() {
        return new File(plugin.getDataFolder(), "rewards.yml");
    }

    private YamlConfiguration loadRewardsYml() {
        File f = rewardsFile();
        if (!f.exists()) {
            plugin.saveResource("rewards.yml", false);
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    private void saveRewardsYml(YamlConfiguration yml) throws IOException {
        yml.save(rewardsFile());
    }

    private void writeRewardNode(YamlConfiguration yml, String path, RewardSavePo po) {
        yml.set(path + ".name", po.getName() == null ? po.getId() : po.getName());
        yml.set(path + ".description", po.getDescription() == null ? "" : po.getDescription());
        if (po.getStage() != null && !po.getStage().isEmpty()) {
            yml.set(path + ".stage", po.getStage());
        } else {
            yml.set(path + ".stage", null);
        }
        yml.set(path + ".enabled", po.getEnabled() == null || po.getEnabled());
        yml.set(path + ".repeatable", po.getRepeatable() != null && po.getRepeatable());
        yml.set(path + ".cooldown", po.getCooldown() == null ? 0L : po.getCooldown());
        yml.set(path + ".daily-limit", po.getDailyLimit() == null ? 0 : po.getDailyLimit());
        yml.set(path + ".weekly-limit", po.getWeeklyLimit() == null ? 0 : po.getWeeklyLimit());
        yml.set(path + ".partial", po.getPartial() != null && po.getPartial());
        if (po.getPer() != null) {
            yml.set(path + ".per", po.getPer());
        }
        yml.set(path + ".consume", po.getConsume() == null || po.getConsume());

        // 条件
        yml.set(path + ".conditions", null);
        if (po.getConditions() != null) {
            int i = 0;
            for (RewardConditionVo c : po.getConditions()) {
                String cp = path + ".conditions.c" + (i++);
                yml.set(cp + ".type", c.getType());
                if (c.getMonsterId() != null) yml.set(cp + ".monster", c.getMonsterId());
                if (c.getAmount() != null) yml.set(cp + ".amount", c.getAmount());
                if (c.getMaterial() != null) yml.set(cp + ".material", c.getMaterial());
                if (c.getData() != null) yml.set(cp + ".data", c.getData());
                if (c.getStartMinOfDay() != null) yml.set(cp + ".start", c.getStartMinOfDay());
                if (c.getEndMinOfDay() != null) yml.set(cp + ".end", c.getEndMinOfDay());
                if (c.getDays() != null) yml.set(cp + ".days", c.getDays());
            }
        }

        // 前置/随机池
        yml.set(path + ".requires", po.getRequires());
        yml.set(path + ".random-pool", po.getPool());
        yml.set(path + ".random-count", po.getPoolCount() == null ? 1 : po.getPoolCount());

        // 物品
        yml.set(path + ".rewards.items", null);
        if (po.getItems() != null) {
            int i = 0;
            for (RewardItemVo it : po.getItems()) {
                String ip = path + ".rewards.items.i" + (i++);
                yml.set(ip + ".material", it.getMaterial());
                yml.set(ip + ".amount", it.getAmount() == null ? 1 : it.getAmount());
                if (it.getName() != null) yml.set(ip + ".name", it.getName());
                if (it.getLore() != null) yml.set(ip + ".lore", it.getLore());
            }
        }

        yml.set(path + ".rewards.commands", po.getCommands());
        yml.set(path + ".rewards.money", po.getMoney() == null ? 0 : po.getMoney());
        yml.set(path + ".rewards.points", po.getPoints() == null ? 0 : po.getPoints());
        yml.set(path + ".rewards.messages", po.getMessages());
    }
}
