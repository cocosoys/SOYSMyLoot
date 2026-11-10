package soys.soysmyloot.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.util.PageUtils;
import com.github.cocosoys.mc.soyshttpovermc.util.TableDataInfo;
import org.bukkit.Bukkit;
import soys.soysmyloot.SOYSMyLoot;
import soys.soysmyloot.data.DataManager;
import soys.soysmyloot.data.PlayerData;
import soys.soysmyloot.model.RewardEntry;
import soys.soysmyloot.spring.entity.vo.ClaimRecordVo;
import soys.soysmyloot.spring.service.ClaimService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 领取记录业务实现：遍历已缓存玩家的 claimCountMap，聚合为领取记录行。
 * 不引入新存储，仅读 DataManager 内存缓存。
 */
public class ClaimServiceImpl implements ClaimService {

    private final SOYSMyLoot plugin;

    public ClaimServiceImpl() {
        this.plugin = SOYSMyLoot.getPlugin(SOYSMyLoot.class);
    }

    @Override
    public TableDataInfo list(Integer pageNum, Integer pageSize, String playerNameFilter, String rewardIdFilter) {
        DataManager dm = plugin.getDataManager();
        List<ClaimRecordVo> rows = new ArrayList<>();
        if (dm != null) {
            String pFilter = playerNameFilter == null ? null : playerNameFilter.trim().toLowerCase();
            for (PlayerData pd : dm.getCachedPlayers()) {
                for (Map.Entry<String, Integer> e : pd.getClaimCountMap().entrySet()) {
                    String rewardId = e.getKey();
                    int count = e.getValue();
                    if (count <= 0) {
                        continue;
                    }
                    if (rewardIdFilter != null && !rewardIdFilter.isEmpty()
                            && !rewardId.equals(rewardIdFilter)) {
                        continue;
                    }
                    String playerName = Bukkit.getOfflinePlayer(pd.getUuid()).getName();
                    if (pFilter != null && !pFilter.isEmpty()
                            && (playerName == null || !playerName.toLowerCase().contains(pFilter))) {
                        continue;
                    }
                    ClaimRecordVo v = new ClaimRecordVo();
                    v.setPlayerUuid(pd.getUuid().toString());
                    v.setPlayerName(playerName);
                    v.setRewardId(rewardId);
                    RewardEntry r = plugin.getConfigManager().getReward(rewardId);
                    v.setRewardName(r == null ? rewardId : r.getName());
                    v.setLastClaimAt(pd.getLastClaim(rewardId));
                    v.setClaimCount(count);
                    rows.add(v);
                }
            }
        }
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return PageUtils.page(rows, pn, ps);
    }

}
