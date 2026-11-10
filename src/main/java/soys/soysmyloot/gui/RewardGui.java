package soys.soysmyloot.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.ScopeResolver;
import soys.soysmyloot.config.ConfigManager;
import soys.soysmyloot.config.MessageManager;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.model.MonsterEntry;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.reward.RewardManager;
import soys.soysmyloot.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 奖励领取 GUI：分页箱子界面，展示全部奖励的条件进度与状态，左键点击尝试领取。
 * <p>布局：6 行 54 格，0~44 为奖励图标，底行 48/49/50 分别为上一页/关闭/下一页。</p>
 */
public final class RewardGui {

    private static final int SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 50;

    private final SOYSMyLoot plugin;
    private final ConfigManager config;
    private final MessageManager messageManager;
    private final DataManager dataManager;
    private final RewardManager rewardManager;
    private final ScopeResolver scopeResolver;

    public RewardGui(SOYSMyLoot plugin, ConfigManager config, MessageManager messageManager,
                     DataManager dataManager, RewardManager rewardManager, ScopeResolver scopeResolver) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
        this.dataManager = dataManager;
        this.rewardManager = rewardManager;
        this.scopeResolver = scopeResolver;
    }

    /** 打开指定页（页码越界时自动钳制） */
    public void open(Player player, int page) {
        List<RewardEntry> sorted = sortedRewards();
        if (sorted.isEmpty()) {
            messageManager.send(player, "soysmyloot.command.list.empty");
            return;
        }
        int totalPages = (sorted.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        int from = page * ITEMS_PER_PAGE;
        int to = Math.min(from + ITEMS_PER_PAGE, sorted.size());
        List<RewardEntry> slice = new ArrayList<>(sorted.subList(from, to));

        RewardGuiHolder holder = new RewardGuiHolder(page, totalPages, slice);
        String title = Text.color("&6战利品奖励 &7- 第 " + (page + 1) + "/" + totalPages + " 页");
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);

        for (int i = 0; i < slice.size(); i++) {
            inv.setItem(i, buildIcon(player, slice.get(i)));
        }
        inv.setItem(SLOT_PREV, buildNav(page > 0 ? Material.ARROW : Material.AIR,
                page > 0 ? "&e上一页" : " ", page > 0 ? "&7回到第 " + page + " 页" : null));
        inv.setItem(SLOT_CLOSE, buildNav(Material.BARRIER, "&c关闭", "&7关闭奖励界面"));
        inv.setItem(SLOT_NEXT, buildNav(page < totalPages - 1 ? Material.ARROW : Material.AIR,
                page < totalPages - 1 ? "&e下一页" : " ", page < totalPages - 1 ? "&7前往第 " + (page + 2) + " 页" : null));

        player.openInventory(inv);
    }

    /** 处理 GUI 内的点击（已由 GuiListener 取消事件后调用） */
    public void handleClick(Player player, RewardGuiHolder holder, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREV) {
            if (holder.getPage() > 0) {
                open(player, holder.getPage() - 1);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            if (holder.getPage() < holder.getTotalPages() - 1) {
                open(player, holder.getPage() + 1);
            }
            return;
        }
        if (slot < 0 || slot >= holder.getSlice().size()) {
            return;
        }
        RewardEntry reward = holder.getSlice().get(slot);
        if (!rewardManager.canClaim(player, reward)) {
            // 部分领取提示更精确的失败原因
            if (reward.isPartial() && rewardManager.bankedUnits(player, reward) < 1) {
                messageManager.send(player, "soysmyloot.command.reward.partial-not-enough");
            } else if (rewardManager.isClaimed(player, reward)) {
                messageManager.send(player, "soysmyloot.command.reward.claimed");
            } else if (rewardManager.getCooldownRemaining(player, reward) > 0) {
                messageManager.send(player, "soysmyloot.command.reward.cooldown",
                        rewardManager.getCooldownRemaining(player, reward));
            } else if (reward.getDailyLimit() > 0
                    && rewardManager.getDailyClaimed(player, reward) >= reward.getDailyLimit()) {
                messageManager.send(player, "soysmyloot.command.reward.daily-limit");
            } else if (reward.getWeeklyLimit() > 0
                    && rewardManager.getWeeklyClaimed(player, reward) >= reward.getWeeklyLimit()) {
                messageManager.send(player, "soysmyloot.command.reward.weekly-limit");
            } else {
                messageManager.send(player, "soysmyloot.command.reward.conditions-not-met");
            }
            return;
        }
        if (rewardManager.claim(player, reward)) {
            messageManager.send(player, "soysmyloot.command.reward.claimed-success",
                    Text.color(reward.getName()));
            // 刷新当前页，反映最新状态
            open(player, holder.getPage());
        } else {
            messageManager.send(player, "soysmyloot.command.reward.conditions-not-met");
        }
    }

    // ================================================================
    //  内部构建
    // ================================================================

    /** 奖励排序：多阶段（铜/银/金）优先，再按 ID，与 /myloot list 保持一致 */
    private List<RewardEntry> sortedRewards() {
        List<RewardEntry> list = new ArrayList<>(config.getRewards().values());
        list.sort((a, b) -> {
            int ra = stageRank(a.getStage());
            int rb = stageRank(b.getStage());
            if (ra != rb) {
                return Integer.compare(ra, rb);
            }
            return a.getId().compareToIgnoreCase(b.getId());
        });
        return list;
    }

    private int stageRank(String stage) {
        if (stage == null || stage.isEmpty()) {
            return 999;
        }
        String s = stage.toLowerCase();
        if (s.contains("bronze") || s.contains("铜")) {
            return 1;
        }
        if (s.contains("silver") || s.contains("银")) {
            return 2;
        }
        if (s.contains("gold") || s.contains("金")) {
            return 3;
        }
        return 50;
    }

    private ItemStack buildIcon(Player player, RewardEntry reward) {
        Material material = Material.BOOK;
        if (!reward.getItems().isEmpty()) {
            material = reward.getItems().get(0).getMaterial();
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(reward.getName()));
            meta.setLore(buildLore(player, reward));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> buildLore(Player player, RewardEntry reward) {
        List<String> lore = new ArrayList<>();
        if (reward.getDescription() != null && !reward.getDescription().isEmpty()) {
            lore.add(Text.color("&7" + reward.getDescription()));
            lore.add(Text.color("&8&m-------------------------"));
        }
        UUID owner = scopeResolver.resolveOwner(player);
        String world = scopeResolver.resolveWorld(player.getWorld().getName());
        PlayerData data = dataManager.getData(owner);
        if (reward.getConditions().isEmpty()) {
            lore.add(Text.color("&7条件: &a无条件"));
        } else {
            lore.add(Text.color("&7条件:"));
            for (RewardEntry.Condition c : reward.getConditions()) {
                lore.add(Text.color(conditionLine(player, data, world, c)));
            }
        }
        lore.add(Text.color("&8&m-------------------------"));
        lore.add(Text.color("&7状态: " + statusLine(player, data, world, reward)));
        if (reward.getDailyLimit() > 0) {
            int left = Math.max(0, reward.getDailyLimit() - rewardManager.getDailyClaimed(player, reward));
            lore.add(Text.color("&7今日剩余: &a" + left + "&7/&a" + reward.getDailyLimit()));
        }
        if (reward.getWeeklyLimit() > 0) {
            int left = Math.max(0, reward.getWeeklyLimit() - rewardManager.getWeeklyClaimed(player, reward));
            lore.add(Text.color("&7本周剩余: &a" + left + "&7/&a" + reward.getWeeklyLimit()));
        }
        if (reward.isPartial()) {
            lore.add(Text.color("&7可兑换: &a" + rewardManager.bankedUnits(player, reward) + " &7单位"));
        }
        if (reward.getRequires() != null && !reward.getRequires().isEmpty()) {
            lore.add(Text.color("&7前置: &e" + String.join("&7, &e", reward.getRequires())));
        }
        lore.add(Text.color("&e左键点击领取"));
        return lore;
    }

    /** 单个条件的展示行（含当前进度与迷你进度条） */
    private String conditionLine(Player player, PlayerData data, String world, RewardEntry.Condition c) {
        switch (c.getType()) {
            case DAMAGE: {
                MonsterEntry m = config.getMonster(c.getMonsterId());
                String name = m != null ? m.getDisplayName() : c.getMonsterId();
                double cur = data.getDamage(world, c.getMonsterId());
                return "&7- 伤害[" + name + "] &a" + fmt(cur) + "&7/&a" + fmt(c.getAmount())
                        + " &f" + Text.progressBar(c.getAmount() > 0 ? cur / c.getAmount() : 0, 8);
            }
            case KILLS: {
                MonsterEntry m = config.getMonster(c.getMonsterId());
                String name = m != null ? m.getDisplayName() : c.getMonsterId();
                int cur = data.getKills(world, c.getMonsterId());
                return "&7- 击杀[" + name + "] &a" + cur + "&7/&a" + (int) c.getAmount()
                        + " &f" + Text.progressBar(c.getAmount() > 0 ? cur / c.getAmount() : 0, 8);
            }
            case LEVEL:
                return "&7- 等级 &a" + player.getLevel() + "&7/&a" + (int) c.getAmount();
            case MONEY: {
                if (plugin.getEconomy() == null) {
                    return "&7- 金币 &c(未安装经济插件)";
                }
                double bal = plugin.getEconomy().getBalance(player);
                return "&7- 金币 &a" + fmt(bal) + "&7/&a" + fmt(c.getAmount())
                        + " &f" + Text.progressBar(c.getAmount() > 0 ? bal / c.getAmount() : 0, 8);
            }
            case ONLINE: {
                long minutes = data.getOnlineMinutes();
                return "&7- 在线时长 &a" + minutes + "&7/&a" + (int) c.getAmount() + " &7分钟";
            }
            case HAS_ITEM: {
                String mat = c.getItemMaterial() != null ? c.getItemMaterial().name() : "?";
                int count = countItem(player, c);
                return "&7- 持有[" + mat + "] &a" + count + "&7/&a" + (int) c.getAmount();
            }
            case TIME:
                return "&7- 时间段 &a" + fmtMinutes(c.getStartMinOfDay()) + "~" + fmtMinutes(c.getEndMinOfDay())
                        + " &a" + (rewardInTime(c) ? "✔" : "✘");
            default:
                return "&7- " + c.getType();
        }
    }

    private int countItem(Player player, RewardEntry.Condition c) {
        if (c.getItemMaterial() == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == c.getItemMaterial()) {
                if (c.getItemData() < 0 || is.getDurability() == c.getItemData()) {
                    count += is.getAmount();
                }
            }
        }
        for (ItemStack is : player.getInventory().getArmorContents()) {
            if (is != null && is.getType() == c.getItemMaterial()) {
                if (c.getItemData() < 0 || is.getDurability() == c.getItemData()) {
                    count += is.getAmount();
                }
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType() == c.getItemMaterial()) {
            if (c.getItemData() < 0 || off.getDurability() == c.getItemData()) {
                count += off.getAmount();
            }
        }
        return count;
    }

    private boolean rewardInTime(RewardEntry.Condition c) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
        int day = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
        List<Integer> days = c.getDays();
        if (days != null && !days.isEmpty() && !days.contains(day)) {
            return false;
        }
        int s = c.getStartMinOfDay();
        int e = c.getEndMinOfDay();
        if (s <= e) {
            return nowMin >= s && nowMin <= e;
        }
        return nowMin >= s || nowMin <= e;
    }

    private String statusLine(Player player, PlayerData data, String world, RewardEntry reward) {
        if (rewardManager.canClaim(player, reward)) {
            return "&a✔ 可领取";
        }
        if (rewardManager.isClaimed(player, reward)) {
            return "&c✘ 已领取";
        }
        long cd = rewardManager.getCooldownRemaining(player, reward);
        if (cd > 0) {
            return "&c✘ 冷却中（" + cd + "s）";
        }
        if (reward.getDailyLimit() > 0
                && rewardManager.getDailyClaimed(player, reward) >= reward.getDailyLimit()) {
            return "&c✘ 今日次数已满";
        }
        if (reward.getWeeklyLimit() > 0
                && rewardManager.getWeeklyClaimed(player, reward) >= reward.getWeeklyLimit()) {
            return "&c✘ 本周次数已满";
        }
        if (reward.isPartial()) {
            int units = rewardManager.bankedUnits(player, reward);
            if (units > 0) {
                return "&a✔ 可领取（" + units + " 单位）";
            }
            return "&7进度不足一个单位";
        }
        return "&7未达成";
    }

    private ItemStack buildNav(Material material, String name, String loreLine) {
        if (material == Material.AIR || name.trim().isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (loreLine != null) {
                meta.setLore(Collections.singletonList(Text.color(loreLine)));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String fmtMinutes(int minutes) {
        if (minutes < 0) {
            return "??:??";
        }
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }
}
