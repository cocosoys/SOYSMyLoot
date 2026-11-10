package soys.soysmyloot.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * 奖励 GUI 监听器：取消 GUI 内的一切点击/拖拽（防止物品被取出），
 * 并将点击位置转发给 {@link RewardGui#handleClick}。
 */
public class GuiListener implements Listener {

    private final RewardGui gui;

    public GuiListener(RewardGui gui) {
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof RewardGuiHolder)) {
            return;
        }
        // 一律取消：GUI 为纯展示+点击交互，不允许拿走/放入物品
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        // rawSlot 为 GUI 内槽位（0~53）；玩家背包区（>=54）不做处理
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= inv.getSize()) {
            return;
        }
        // shift 点击会尝试移动到别处，同样只按左/右键语义处理
        gui.handleClick(player, (RewardGuiHolder) inv.getHolder(), rawSlot);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof RewardGuiHolder) {
            event.setCancelled(true);
        }
    }
}
