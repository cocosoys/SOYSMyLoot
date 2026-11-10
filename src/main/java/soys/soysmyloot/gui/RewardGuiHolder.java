package soys.soysmyloot.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import soys.soysmyloot.model.RewardEntry;

import java.util.List;

/**
 * 奖励 GUI 的 InventoryHolder 标识：同时携带页码、总页数与当前页奖励切片，
 * 使点击事件可直接由 slot 反查奖励，无需依赖标题字符串解析。
 */
public class RewardGuiHolder implements InventoryHolder {

    private final int page;
    private final int totalPages;
    private final List<RewardEntry> slice;

    public RewardGuiHolder(int page, int totalPages, List<RewardEntry> slice) {
        this.page = page;
        this.totalPages = totalPages;
        this.slice = slice;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public List<RewardEntry> getSlice() {
        return slice;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
