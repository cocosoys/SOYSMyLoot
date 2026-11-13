package soys.soysmyloot.spring.entity.vo;

import lombok.Data;
import soys.soysmyloot.model.RewardEntry;

/**
 * 礼包专属领取反馈（音效 + 粒子）传输对象。
 *
 * <p>对应 rewards.yml 中 {@code rewards.<id>.feedback} 子节。
 * enabled=false 或整个对象为 null 表示该礼包不播放领取反馈。</p>
 */
@Data
public class RewardFeedbackVo {

    private Boolean enabled;
    private String sound;
    private Double volume;
    private Double pitch;
    private String particle;
    private Integer count;
    private Double offset;

    public static RewardFeedbackVo of(RewardEntry.Feedback f) {
        if (f == null) {
            return null;
        }
        RewardFeedbackVo v = new RewardFeedbackVo();
        v.setEnabled(f.isEnabled());
        v.setSound(f.getSound());
        v.setVolume((double) f.getVolume());
        v.setPitch((double) f.getPitch());
        v.setParticle(f.getParticle());
        v.setCount(f.getCount());
        v.setOffset(f.getOffset());
        return v;
    }
}
