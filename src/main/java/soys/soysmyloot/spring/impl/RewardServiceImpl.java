package soys.soysmyloot.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.PageUtils;
import com.github.cocosoys.mc.soyshttpovermc.util.TableDataInfo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.spring.entity.po.CopyHandPo;
import soys.soysmyloot.spring.entity.po.RewardGivePo;
import soys.soysmyloot.spring.entity.po.RewardItemsSavePo;
import soys.soysmyloot.spring.entity.po.RewardResetPo;
import soys.soysmyloot.spring.entity.po.RewardSavePo;
import soys.soysmyloot.spring.entity.po.RewardTogglePo;
import soys.soysmyloot.spring.entity.vo.RewardConditionVo;
import soys.soysmyloot.spring.entity.vo.RewardDetailVo;
import soys.soysmyloot.spring.entity.vo.RewardFeedbackVo;
import soys.soysmyloot.spring.entity.vo.RewardItemVo;
import soys.soysmyloot.spring.entity.vo.RewardVo;
import soys.soysmyloot.spring.service.RewardService;
import soys.soysmyloot.spring.util.RewardsYamlPatcher;
import soys.soysmyloot.spring.util.Sync;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 礼包管理业务实现：读走 ConfigManager 内存缓存，写用 {@link RewardsYamlPatcher} 做节级文本补丁。
 *
 * <p>V7 起不再用 {@code YamlConfiguration.save()} 全量覆写 rewards.yml —— 那会丢掉全部中文注释、
 * 行内注释与块列表结构。现在只改动目标礼包节，文件其余字节不变。
 * 领取反馈（音效/粒子）也随礼包保存在 {@code rewards.<id>.feedback} 子节。</p>
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
        d.setFeedback(RewardFeedbackVo.of(r.getFeedback()));

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
        String invalid = validateFeedback(po.getFeedback());
        if (invalid != null) {
            return AjaxResult.error(invalid);
        }
        try {
            File file = ensureRewardsFile();
            RewardsYamlPatcher.write(file, po.getId().trim(), toNode(po));
            reloadOnMain();
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
            File file = ensureRewardsFile();
            RewardsYamlPatcher.delete(file, id.trim());
            reloadOnMain();
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
        // 基于当前完整详情覆写，仅改 enabled；feedback 必须透传，否则会被清空
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
        savePo.setFeedback(cur.getFeedback());
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
        savePo.setFeedback(cur.getFeedback());
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

    @Override
    public AjaxResult feedbackOptions() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<String> sounds = new ArrayList<>();
        for (Sound s : Sound.values()) {
            sounds.add(s.name());
        }
        List<String> particles = new ArrayList<>();
        for (Particle p : Particle.values()) {
            particles.add(p.name());
        }
        data.put("sounds", sounds);
        data.put("particles", particles);
        Map<String, Object> recommended = new LinkedHashMap<>();
        recommended.put("enabled", true);
        recommended.put("sound", "ENTITY_PLAYER_LEVELUP");
        recommended.put("volume", 1.0);
        recommended.put("pitch", 1.0);
        recommended.put("particle", "VILLAGER_HAPPY");
        recommended.put("count", 30);
        recommended.put("offset", 0.6);
        data.put("recommended", recommended);
        return AjaxResult.success(data);
    }

    // ================================================================
    //  rewards.yml 节级文本补丁
    // ================================================================

    private File rewardsFile() {
        return new File(plugin.getDataFolder(), "rewards.yml");
    }

    private File ensureRewardsFile() {
        File f = rewardsFile();
        if (!f.isFile()) {
            plugin.saveResource("rewards.yml", false);
        }
        return f;
    }

    /** 配置重载回主线程，避免与 Bukkit 主线程上的读取并发竞争。 */
    private void reloadOnMain() throws Exception {
        Sync.onMain(plugin, () -> {
            cfg().loadRewards();
            return null;
        });
    }

    /** 保存前校验 Sound / Particle 枚举名，避免写进无效值后只在 debug 日志里提示。 */
    private String validateFeedback(RewardFeedbackVo fb) {
        if (fb == null || !Boolean.TRUE.equals(fb.getEnabled())) {
            return null;
        }
        String s = fb.getSound();
        if (s != null && !s.trim().isEmpty()) {
            try {
                Sound.valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return "无效的音效名称: " + s;
            }
        }
        String p = fb.getParticle();
        if (p != null && !p.trim().isEmpty()) {
            try {
                Particle.valueOf(p.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return "无效的粒子名称: " + p;
            }
        }
        return null;
    }

    /**
     * 把保存入参转成「相对礼包节的路径 -> 值」。
     *
     * <p>刻意不写 conditions：条件结构复杂且由手工编辑 rewards.yml 维护；
     * rewards.items 仅在 saveItems 链路（po.items != null）时才写入，
     * 普通「配置」对话框提交时不会触碰原物品块。</p>
     */
    private Map<String, Object> toNode(RewardSavePo po) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("name", po.getName() == null ? po.getId() : po.getName());
        n.put("description", po.getDescription() == null ? "" : po.getDescription());
        n.put("stage", po.getStage() == null || po.getStage().isEmpty() ? null : po.getStage());
        n.put("enabled", po.getEnabled() == null || po.getEnabled());
        n.put("repeatable", po.getRepeatable() != null && po.getRepeatable());
        n.put("cooldown", po.getCooldown() == null ? 0L : po.getCooldown());
        n.put("daily-limit", po.getDailyLimit() == null ? 0 : po.getDailyLimit());
        n.put("weekly-limit", po.getWeeklyLimit() == null ? 0 : po.getWeeklyLimit());
        n.put("partial", po.getPartial() != null && po.getPartial());
        if (po.getPer() != null) {
            n.put("per", po.getPer());
        }
        n.put("consume", po.getConsume() == null || po.getConsume());
        n.put("random-count", po.getPoolCount() == null ? 1 : Math.max(1, po.getPoolCount()));
        n.put("requires", po.getRequires() == null ? Collections.emptyList() : po.getRequires());
        n.put("random-pool", po.getPool() == null ? Collections.emptyList() : po.getPool());
        n.put("rewards.commands", po.getCommands() == null ? Collections.emptyList() : po.getCommands());
        n.put("rewards.messages", po.getMessages() == null ? Collections.emptyList() : po.getMessages());
        n.put("rewards.money", po.getMoney() == null ? 0 : po.getMoney());
        n.put("rewards.points", po.getPoints() == null ? 0 : po.getPoints());

        if (po.getItems() != null) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (RewardItemVo it : po.getItems()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("material", it.getMaterial() == null ? "STONE" : it.getMaterial());
                m.put("amount", it.getAmount() == null ? 1 : it.getAmount());
                if (it.getName() != null && !it.getName().isEmpty()) {
                    m.put("name", it.getName());
                }
                if (it.getLore() != null && !it.getLore().isEmpty()) {
                    m.put("lore", it.getLore());
                }
                items.add(m);
            }
            n.put("rewards.items", items);
        }

        n.put("feedback", feedbackNode(po.getFeedback()));
        return n;
    }

    /** null = 删除该礼包的 feedback 子节（不播放任何反馈）。 */
    private Map<String, Object> feedbackNode(RewardFeedbackVo fb) {
        if (fb == null || !Boolean.TRUE.equals(fb.getEnabled())) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", true);
        m.put("claim-sound", fb.getSound() == null ? "" : fb.getSound().trim());
        m.put("sound-volume", fb.getVolume() == null ? 1.0 : fb.getVolume());
        m.put("sound-pitch", fb.getPitch() == null ? 1.0 : fb.getPitch());
        m.put("claim-particle", fb.getParticle() == null ? "" : fb.getParticle().trim());
        m.put("particle-count", fb.getCount() == null ? 30 : Math.max(0, fb.getCount()));
        m.put("particle-offset", fb.getOffset() == null ? 0.6 : Math.max(0.0, fb.getOffset()));
        return m;
    }
}
