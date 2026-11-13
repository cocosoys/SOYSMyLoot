package soys.soysmyloot.spring.entity.vo;

import lombok.Data;

/**
 * 配置保存结果 VO。
 *
 * <p><b>字段名 tip 不可改</b>：前端 {@code mixins/configPage.js} 消费的是
 * {@code res.data.tip}（保存成功后弹出该提示）。</p>
 */
@Data
public class MyLootConfigSaveVo {

    private String file;
    private String tip;

    public MyLootConfigSaveVo() {
    }

    public MyLootConfigSaveVo(String file, String tip) {
        this.file = file;
        this.tip = tip;
    }

    public static MyLootConfigSaveVo of(String file, String tip) {
        return new MyLootConfigSaveVo(file, tip);
    }
}
